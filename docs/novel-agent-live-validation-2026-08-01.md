# Novel Agent Live Validation Record

Date: 2026-08-01 (Asia/Shanghai)

This record intentionally excludes provider keys, access tokens, model output text, and novel body text.

## Deployment

- Docker dependencies: PostgreSQL, Redis, RabbitMQ, Nacos, Elasticsearch, MinIO, Milvus, Neo4j, Prometheus, Jaeger, and OTLP Collector healthy.
- Standard local stack: Gateway `8080`, user `8081`, novel `8082`, comment `8083`, interaction `8084`, check-in `8085`, Agent `8086`, frontend `3000`.
- `scripts/check-status.ps1`: all checks passed.
- Deployment boundary check on the standard Agent `8086`: health `UP`, Flyway `26,27,28,29,30`, direct browser API `404`.

## Corpus and projections

- Canonical work: `358679512818388992`.
- Source content ledger: `1279/1279 READY`, chapter range `0..1278`, no missing or duplicate chapter indexes.
- Agent index ledger: `1279/1279 COMPLETED`; chunks `20472` across `1279` chapters.
- RabbitMQ index/delete queues and DLQs: all zero.
- PostgreSQL graph authority: `26893` nodes and `399953` edges.
- Neo4j LightRAG projection: `26893` nodes and `399953` edges after bounded re-projection.
- Milvus: a live evidence-chunk re-projection succeeded using the versioned 256-dimension collection; no dimension mismatch was logged.

## Protocol and product smoke

- `scripts/validate-agent-mcp.ps1`: initialization, `notifications/initialized`, `ping`, tool schema, allowlist, argument rejection, and server-side spoiler boundary all passed.
- Independent `@modelcontextprotocol/sdk@1.12.1` Streamable HTTP client: five read-only tools discovered and `book.search` completed.
- `scripts/validate-agent-e2e.ps1`: registration, login, Gateway identity propagation, exactly three starter credits, Agent session, LightRAG retrieval, and SSE completed. SSE included `recommendations`, `graph`, and `done` events.
- Optional dependency fault injection: pausing `reader-milvus` and pausing `reader-neo4j` separately both preserved `meta`, `delta`, `recommendations`, `graph`, and `done` SSE events. The Milvus run increased `reader_agent_vector_recall_total{outcome="fallback"}` and completed through PostgreSQL/Elasticsearch fallback; both containers were unpaused in `finally` blocks and returned healthy.
- Latest Milvus pause run against the rebuilt Agent also returned a complete Gateway SSE stream; the post-run Prometheus scrape showed `reader_agent_vector_recall_total{outcome="fallback"}=2` and the container was restored in `finally`.
- The targeted graph fault helper `scripts/validate-agent-graph-fault.ps1` forced a visible character seed, paused `reader-neo4j`, and completed SSE with `reader_agent_graph_recall_total{outcome="fallback"}` increasing by `2`; the container was restored in `finally`.
- The real DeepSeek usage row recorded non-zero prompt/output estimates because the provider response exposed an empty `Usage` object; the new zero-value guard marked it `ESTIMATED` rather than falsely claiming `PROVIDER`. Bounded community/evidence components remained non-zero. Citation metadata resolved to the indexed canonical work and stayed at the chapter-zero boundary used by the test account.

## Build gates

- Full backend Maven test reactor: passed.
- Agent focused Maven tests: passed.
- Frontend `npm run build`: passed.
- Docker Compose configuration validation: passed.
- `git diff --check`: passed.
- Validation script defaults now target the launcher-standard Agent port `8086`; stale `8096` defaults were removed from the repeatable deployment, MCP, reranker-load, and external-validation commands.
- Full backend regression after the latest Reranker HTTP-contract test: all nine Maven modules passed; frontend production build and Compose validation passed again.
- Final regression after the optional-vector/graph timeout/cooldown implementation: all nine Maven modules passed (`150` tests total, `74` in `reader-agent`), frontend production build, Compose validation, and `git diff --check` passed.
- Full backend regression after adding the original fixture gate and 50-request Reranker fixture load: all nine Maven modules passed again.
- The Reranker contract regression now verifies bearer authentication, request fields, provider ordering, and the success path without logging candidate text. A real third-party endpoint is still intentionally not configured.
- Independent `@modelcontextprotocol/sdk@1.12.1` validation was rerun from an isolated temporary npm project against `8086`: Streamable HTTP connected, five read-only tools were discovered, and `book.search` returned one content block; no dependency was added to the repository.
- The same contract fixture now completes 50 local HTTP-provider requests, records 50 `success` outcomes, and asserts a bounded p95-style maximum latency; this validates the adapter and metrics path but is not a substitute for an external provider load result.
- Reranker parsing now rejects duplicate or out-of-range provider indexes and falls back to deterministic local ordering, preventing malformed external responses from duplicating or dropping evidence silently.
- The copyright-cleared `original-synthetic` fixture at `backend/reader-agent/src/test/resources/agent-original-fixture.json` contains ten chapters, two evidence-distinguished same-name characters, an open/resolved clue chain, and causal events; its deterministic graph-quality gate passes.
- Milvus optional evidence recall now uses `AGENT_MILVUS_OPERATION_TIMEOUT_MILLIS` (default `1500`) and `AGENT_MILVUS_FAILURE_COOLDOWN_SECONDS` (default `30`); timeout/connection failures are observable and do not block model generation.
- Neo4j optional LightRAG recall now uses `AGENT_NEO4J_OPERATION_TIMEOUT_MILLIS` (default `1500`) and `AGENT_NEO4J_FAILURE_COOLDOWN_SECONDS` (default `30`); graph claims fail closed while relational graph evidence remains available.
- Optional Reranker calls now use `AGENT_RERANKER_OPERATION_TIMEOUT_MILLIS` (default `3000`) and `AGENT_RERANKER_FAILURE_COOLDOWN_SECONDS` (default `30`); provider errors fall back to deterministic local ordering without repeating the timeout on every candidate request.
- The latest Reranker timeout/cooldown implementation is deployed in the standard `8086` Agent process; focused tests and the full `150`-test backend regression pass against that build.
- The external readiness preflight passes the local deployment, Gateway boundary, and `original-synthetic` fixture checks, and explicitly reports `pending_external_reranker_credentials` for the intentionally empty provider configuration.
- The ten-chapter `original-synthetic` fixture remains the required copyright-cleared quality baseline; its graph extraction, same-name identity separation, LightRAG boundary, and five-case DeepSeek answer gate are complete. Additional licensed corpora are optional follow-up coverage.
- The LightRAG local graph budget is now explicit at 36 edges in both the answer path and read-only graph tool path; focused graph tests and the deployed Agent health/SSE checks pass after the correction.
- MCP graph responses use the same 36-edge cap; the MCP lifecycle and spoiler-boundary verifier pass against the rebuilt Agent.

## Real-model fixture run

- A temporary canonical work was indexed from the copyright-cleared fixture using explicit UTF-8 requests; all `10/10` chapter calls returned HTTP `200`.
- This historical DeepSeek fixture run produced `24` PostgreSQL nodes, `27` edges, one deterministic clue, and `20` LightRAG community cards under the review policy that existed on 2026-08-01. The current build no longer creates visible regex/rule claims; explicit user builds publish only LLM claims that pass strict evidence/type/alias gates, while administrators can subsequently set `PENDING` or `REJECTED`.
- Runtime identity keys kept the two same-name characters separate: `character:黎青:城东的黎青` and `character:黎青:城西的黎青`. The extractor now expands shortened model hints from verbatim evidence before persistence.
- A real DeepSeek answer request against the fixture completed through the Gateway SSE path with the required `meta`, `recommendations`, `graph`, and `done` events. The recorded prompt was bounded to LightRAG community/evidence/tool components; no whole-book or whole-graph context was sent.
- Authenticated insight smoke against the indexed canonical work returned HTTP `200` for graph, clues, timeline, reading map, spoiler-safe capsule, similar-book DNA, reader link, reading plan, shelf groups, and preferences endpoints at reading chapter `0`.
- DeepSeek omitted positive usage counters for this run, so answer rows are explicitly marked `ESTIMATED`; this is not presented as provider-reported token data. The graph extraction call count and bounded answer context are retained as runtime evidence, while a direct GraphRAG comparison is intentionally not claimed because that architecture is not implemented.
- The server-side answer release gate was exercised with five real DeepSeek cases on the original fixture (citation, graph, clue, spoiler refusal, and tool-security). Evaluation run `358909459994513408` persisted `5/5 PASSED`; temporary users, sessions, messages, and fixture projections were removed afterward, while the aggregate evaluation record was retained.

## Remaining external gate

- A real third-party Reranker endpoint is not configured. The original fixture's deterministic and real DeepSeek graph/runtime gates now pass; external provider ordering/50-request load and authorized answer-quality scoring still require the inputs described in the external runbook.

## Follow-up verification (2026-08-01 17:20 Asia/Shanghai)

- The complete Maven reactor regression passed again: all nine backend modules and `150` tests passed, including `74` Agent tests.
- `npm run build` passed with `137` transformed frontend modules; Docker Compose configuration and `git diff --check` also passed.
- A fresh authenticated Gateway flow passed registration, login, the three starter credits, session creation, LightRAG SSE, and the required `meta`, `recommendations`, `graph`, and `done` events.
- Authenticated smoke requests for graph, clues, timeline, reading map, spoiler-safe capsule, similar-book DNA, reading plan, shelf groups, and preferences all returned HTTP `200` for the indexed 《剑来》 work at chapter `0`.
- `validate-agent-external-readiness.ps1` still reports only `pending_external_reranker_credentials`; no local fixture or deterministic fallback result is being presented as a third-party provider result.
- `scripts/validate-novel-corpus.ps1` passed for 《剑来》: `1279/1279` contiguous source chapters (`0..1278`) are `READY`, with `1279/1279` Agent documents and completed jobs and `20472` chunks.
- The persisted `RERANKER` model route is covered by an HTTP contract test: a stable cohort selects the gray-routed model while bearer credentials and candidate content remain bounded to the provider request; disabled/unavailable routes still use the configured environment model or deterministic local fallback.
- After the Reranker-route change, the latest full reactor regression passed `151` tests total, including `75` Agent tests; the rebuilt Agent JAR is deployed on standard port `8086` and the Gateway SSE smoke still passes.
- A clean rebuild caught and fixed the infrastructure metadata map arity issue introduced by the extra active-Reranker fields. The authenticated `/api/agent/infrastructure` smoke now returns `200` with `embeddingModelVersion=hash-embedding-v1`, `rerankerModel=rerank-v3.5`, and the active cohort model; no stale incremental class remains deployed.
- On the cleanly rebuilt deployment, both Neo4j and Milvus pause fault injections again completed the full SSE event contract and increased their respective fallback counters; the MCP JSON-RPC lifecycle/allowlist verifier also passed with the internal token.
- The embedding adapter regression now covers OpenAI-compatible batched `/v1/embeddings` responses, configured dimension validation, and cooldown fallback. The local deployment intentionally remains on `hash-embedding-v1` until a reviewed semantic provider and matching versioned Milvus collection are configured.
- Authenticated `/api/agent/infrastructure` metadata now reports the embedding provider, model, dimension, and version together with the active Reranker route, making provider/collection drift visible without exposing credentials.
- Final post-change reactor regression passed all nine modules with `79` Agent tests; deployment boundary, authenticated SSE, corpus, MCP, frontend build, and Compose checks remained green. The external readiness script still reports only `pending_external_reranker_credentials`.
- The LightRAG usage observability pass added the administrator-only `usage-breakdown` endpoint and Agent control-room view. A trusted local smoke with a temporary administrator role returned HTTP 200 with the six privacy-safe section keys and 40 redacted recent rows; the temporary role was removed immediately afterward. Direct browser-facing access remained `404`.
- After packaging the updated JAR (the prior running process had an older artifact), the standard `8086` deployment check, authenticated SSE E2E, MCP lifecycle verifier, service status, Compose config, frontend build and `剑来` corpus gate passed. External readiness intentionally remains `pending_external_reranker_credentials`.
- `AgentAdminControllerTest` now covers the usage-breakdown redaction and 90-day clamp; the latest Agent-focused regression has `80` passing tests. The complete Maven reactor, frontend build and PowerShell parser gate remain green.
## Scheme A post-change validation (2026-08-01 19:54)

- `LocalEvidenceRerankerTest` now contains three tests, including 50 repeated rankings; focused Agent tests (`ConfiguredRerankerServiceTest` and `LocalEvidenceRerankerTest`) passed 10/10.
- Full backend reactor regression passed all 83 discovered tests. The Agent JAR was rebuilt and redeployed on `8086`; health is `UP`, direct browser-facing access is `404`, and Flyway V26--V30 are successful.
- `scripts/validate-agent-local-reranker.ps1` passed with `repeatedRuns=50` and `externalProviderRequired=false`.
- `scripts/validate-agent-external-readiness.ps1 -SkipProviderProbe` passed with `status=local_ready_external_optional`, `localReranker=true`, and `externalRerankerConfigured=false`.
- Frontend production build, Docker Compose configuration validation, PowerShell syntax parsing, and `git diff --check` passed. Gateway/SSE E2E was not rerun because Gateway and business services were stopped in the local environment; this is an environment-state gap, not a Reranker test failure.
