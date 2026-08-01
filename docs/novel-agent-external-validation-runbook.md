# Novel Agent External Validation Runbook

This runbook covers the final acceptance checks that require a real provider, a third-party MCP client, or copyright-cleared content. They cannot be truthfully proven by a source-only build.

## Preconditions

- Start the Docker dependencies with `docker compose -f docker/docker-compose.yml up -d`.
- Set `AGENT_INTERNAL_TOKEN`, `AGENT_GATEWAY_TOKEN`, `AGENT_ENCRYPTION_KEY`, and `DEEPSEEK_API_KEY` only in local secrets or deployment secrets. `AGENT_GATEWAY_TOKEN` must match in Gateway and Agent so direct Agent-port requests cannot forge user identity. Do not put them in source control.
- `scripts/start-backend.ps1` generates a transient value only for a local multi-process launch when `AGENT_GATEWAY_TOKEN` is absent; deployments must set and rotate their own value instead.
- Enable the intended optional stores: `AGENT_MILVUS_ENABLED=true`, `AGENT_ELASTICSEARCH_ENABLED=true`, and `AGENT_NEO4J_ENABLED=true`.
- For the single-node local Elasticsearch Compose profile, Agent-created indexes use `number_of_replicas: 0`; production replica counts must instead match the number of eligible data nodes.
- After deploying a new Agent process with a non-empty, matching `AGENT_GATEWAY_TOKEN`, run `./scripts/validate-agent-deployment.ps1`. It checks Agent health, Flyway V26--V30, and that an unauthenticated direct browser API request receives `404`; it deliberately does not read or print secrets.
- For local process replacement after a code/config change, build the Agent JAR and run `./scripts/restart-agent.ps1`; it loads `.env` without printing values, starts the service with a hidden window, and waits for `/actuator/health` `UP`.
- Use an original or licensed fixture work with at least ten numbered chapters, two identically named characters distinguished by explicit text, open/resolved clues, and a causal event chain.
- When posting Chinese fixture chapters from PowerShell, send `UTF-8` bytes with `Content-Type: application/json; charset=utf-8`; the Windows default code page can turn evidence into replacement characters and invalidate graph-quality results.

## Third-party MCP Interoperability

Use any MCP SDK client that supports JSON-RPC `2024-11-05` over HTTP. Send `X-Agent-Internal-Token` and the requesting `X-User-Id` only from a trusted internal runner.

For repeatable transport and boundary checks against a deployed instance, first run the repository helper (it does not print the token or novel body):

```powershell
$env:AGENT_INTERNAL_TOKEN = '<deployment secret>'
.\scripts\validate-agent-mcp.ps1 -UserId <test-user-id> -CanonicalBookId <fixture-work-id>
```

The helper validates initialization, discovered JSON Schema, required-argument rejection, write-tool rejection, and—when a fixture ID is provided—the server-clamped graph boundary. Still run at least one independent MCP SDK client afterward: the helper proves the HTTP/JSON-RPC contract, while an SDK proves client-library interoperability.

1. Call `initialize` with `jsonrpc: "2.0"`, then send `notifications/initialized` and `ping`; verify a protocol version, server name, tools capability, and lifecycle compatibility.
2. Call `tools/list`; validate every `inputSchema` as JSON Schema, including `required` fields and `additionalProperties: false`.
3. Call `bookshelf.list`, `book.search`, `book.detail`, `reading.state`, and `knowledge_graph.query`.
4. Assert unknown tools, missing required arguments, invalid IDs, and a missing user ID are rejected.
5. Call `knowledge_graph.query` with a chapter higher than the recorded progress; assert returned evidence never exceeds the private server-side boundary.

Evidence to retain: SDK name/version, raw request/response with tokens removed, and the test user/work IDs. Do not retain API keys or novel full text.

## Optional dependency fault injection

The repository has closed the local dependency-failure gate. For a repeatable local check, pause one optional container at a time, run the standard Gateway E2E, inspect the safe Prometheus counter, and always unpause in `finally`:

```powershell
docker pause reader-milvus
try { .\scripts\validate-agent-e2e.ps1 -CurrentChapter 0 }
finally { docker unpause reader-milvus }
```

The SSE must still contain `delta` and `done`; Milvus failure must increase `reader_agent_vector_recall_total{outcome="fallback"}`. For Neo4j, use the seed-aware helper so the actual local LightRAG path is exercised:

```powershell
.\scripts\validate-agent-graph-fault.ps1 -Dependency reader-neo4j
```

The helper requires an approved visible character, verifies `reader_agent_graph_recall_total{outcome="fallback"}`, and confirms graph queries fail closed while PostgreSQL graph data and normal Agent completion remain available. This local evidence does not replace a production network-partition or third-party-provider test.

## Optional External Reranker Integration and Load Test

Before running the optional 50-request test, run the readiness check. It never prints a provider key or response body. With no provider variables it passes the local core gate with `local_ready_external_optional`; after setting provider variables it additionally probes the real HTTP contract:

```powershell
.\scripts\validate-agent-external-readiness.ps1 `
  -AgentBaseUrl http://localhost:8086
```

The check also verifies Agent health, the Gateway direct-access `404` boundary, and the `original-synthetic` ten-chapter fixture. Pass `-SkipProviderProbe` only when the provider is reachable exclusively from the deployment network and will be probed during deployment.

1. Configure `AGENT_RERANKER_ENABLED=true`, `AGENT_RERANKER_BASE_URL`, `AGENT_RERANKER_API_KEY`, and optional model/path values. The persisted admin route key `RERANKER` can override the model and apply a stable percentage rollout without changing the provider secret. Tune `AGENT_RERANKER_OPERATION_TIMEOUT_MILLIS` and `AGENT_RERANKER_FAILURE_COOLDOWN_SECONDS` only when the provider's SLO requires different bounds.
2. Index the fixture chapters. Make one keyword-heavy and one semantic retrieval request, then verify the configured provider is called and returned ordering is used.
3. Run at least 50 requests at the expected portfolio concurrency against Agent retrieval endpoints. Measure p50/p95 latency, error rate, fallback count, and provider rate-limit responses through Prometheus. Query `reader_agent_reranker_requests_total{outcome="success|fallback|disabled"}` to distinguish external ordering, degraded local ordering, and intentionally disabled operation.
4. Disable the reranker temporarily. Verify deterministic local rerank continues to return bounded, cited results and normal reading remains available.

The repository helper executes the load portion through the normal Gateway session/SSE route, so user identity, credits, reading-boundary clipping, and per-conversation concurrency remain active. It creates one short-lived session per request and prints only aggregate latency, success/error counts, and reranker counter deltas:

```powershell
.\scripts\validate-agent-reranker-load.ps1 `
  -GatewayBaseUrl http://localhost:8080 `
  -AgentMetricsBaseUrl http://localhost:8086 `
  -UserId <test-user-id> `
  -AccessToken '<test-user-access-token>' `
  -CanonicalBookId <fixture-work-id> `
  -CurrentChapter <verified-reader-chapter> `
  -Requests 50 -Concurrency 5
```

Run it once with the configured Reranker and once with it disabled. Retain the JSON report and the Prometheus scrape, but never retain the access token, Reranker key, or full novel text. A configured run should increment `success`; an induced provider outage should increment `fallback`; the disabled run should increment `disabled` while all requests remain bounded and complete.

Pass criteria: zero out-of-bound citations, no uncaught provider failures, and an explicit fallback signal for every reranker outage.

## Model Answer Quality Evaluation

1. Create answer cases from the original/licensed fixture using the categories in `backend/reader-agent/src/test/resources/agent-eval-cases.json`.
2. Run the actual selected provider and prompt version for each case. Record answer text, reading boundary, the `canonicalBookId` that owns each citation, returned citation chapter indices, canonical recommendation IDs, and tool scope/write evidence. The answer-suite gate resolves citation chapters against that work's indexed chunks and resolves recommendation IDs through the `reader-novel` canonical-work source of truth; fabricated IDs cannot pass, while a valid but not-yet-indexed discovery candidate remains valid.
3. Submit the result to `POST /api/agent/admin/evaluations/answer-suite` as an administrator.
4. Inspect the persisted run and individual case evidence in the Agent admin page. A release candidate must have no failed spoiler, citation, graph/clue, or tool-security case.

The evaluator must not invent citation IDs or recommendation book IDs. The service persists submitted evidence and deterministically rejects missing or out-of-bound evidence.

## LightRAG Token-Efficiency Gate

Use the same original/licensed fixture and question set for a local-entity case, an unknown-entity case, and a broad thematic case.

1. For a question naming a visible character, verify the persisted model-usage row has a non-zero `graph_tokens` or `community_tokens` from `CHAPTER`/`GRAPH`, and that it does not contain a `BOOK` community in the prompt trace. The query must not mark an escalation when local graph/cards are available.
2. For a question with no visible entity and no low-level match, verify the response can use a bounded `ARC` escalation. `BOOK` and `BOOK_SAFE` remain forbidden in both cases.
3. Compare provider-reported `input_tokens` for the LightRAG path against the previous all-community baseline on the identical prompt version and fixture. Retain average and p95 input tokens alongside citation correctness; a token reduction only passes if citations and spoiler boundaries remain correct.
4. Inspect the privacy-safe `system_tokens`, `history_tokens`, `graph_tokens`, `community_tokens`, `evidence_tokens`, and `tool_tokens` columns. Their sum is an explainable composition estimate when the provider does not report token usage; prompt text must not be retained.

The administrator can retrieve a redacted aggregate and the last 50 redacted rows through `GET /api/agent/admin/usage-breakdown?days=7`. The response contains only section token counts, usage-source/status values and timestamps; it deliberately omits prompt text, user IDs, session IDs, request IDs and provider keys. Use this endpoint when attaching LightRAG token-efficiency evidence to an evaluation report. The repository helper `scripts/validate-agent-lightrag-budget.ps1` checks the six section keys and redaction boundary without printing the access token.

## Graph Quality Fixture

1. Index the fixture work once with rule extraction, then once with model extraction enabled.
2. Verify same-name characters with distinct evidence-backed `identityHint` values become separate identity keys; ambiguous aliases must not merge them.
3. Verify reading-map causal edges have source evidence and do not label a single shared name as causality.
4. At reading chapter N, query graph, clues, capsule, character interview, and map with N+1 or greater in the URL/body. All returned chapter evidence must remain at or below N.

## Report Template

Record: deployment commit, provider/model/version, fixture license/source, test date, request count, p50/p95, fallback/error counts, failed case IDs, and remediation. Keep secrets and full copyrighted text out of the report.
## Scheme A: Local Reranker Core Gate

The core gate does not require a third-party Reranker. Run:

```powershell
.\scripts\validate-agent-local-reranker.ps1
```

This executes the dependency-free `LocalEvidenceReranker` regression, including 50 repeated rankings. It verifies deterministic ordering, lexical/BM25 relevance, evidence-quality and cross-source signals, bounded result counts, and duplicate-free output. It must pass before packaging the Agent service.

The OpenAI-compatible provider flow described below is optional enhancement evidence. If no provider URL and key are configured, `validate-agent-external-readiness.ps1` must report `passed=true`, `status=local_ready_external_optional`, `localReranker=true`, and `externalRerankerConfigured=false`. A provider probe or provider load result must never be represented by the local fixture, DeepSeek Chat, or the deterministic fallback.
