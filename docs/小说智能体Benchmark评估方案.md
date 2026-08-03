# 小说智能体 Benchmark 评估方案

这份文档把“功能能调用”和“回答质量好”拆成两套门禁。Benchmark 使用原创合成语料或已获得授权的语料，不把小说正文、API Key、用户标识写入报告。

## 一、三层 Benchmark

| 层级 | 目标 | 是否消耗模型额度 | 运行入口 |
| --- | --- | --- | --- |
| 离线契约 Benchmark | 验证 LightRAG 阅读边界、引用来源、工具安全、提示词拒答、预算和降级契约 | 否 | `scripts/validate-agent-benchmark.ps1` |
| 《剑来》真实语料 Benchmark | 使用已索引的 `canonicalBookId=358679512818388992`，覆盖章节回顾、人物图谱、伏笔、访谈、分岔地图、推荐和剧透拦截 | 默认否；真实路由时消耗 | `scripts/validate-agent-jianlai-benchmark.ps1` |
| 在线运行 Benchmark | 验证 Gateway/SSE、真实 PostgreSQL、Milvus、Neo4j、ES、Reranker 的延迟、错误率和故障降级 | 默认否；真实路由时会消耗 | 综合脚本的 `-RunLive` 和专项脚本 |
| 答案质量 Benchmark | 在授权 fixture 或《剑来》已授权测试题上评估事实正确性、引用 Precision/Recall、剧透拦截、图谱 F1、推荐可追溯性和中文表达 | 是 | 管理端 `answer-suite` + 人工/LLM 评审 |

## 二、指标和计算方法

- **事实准确率**：答案中的可验证事实与 gold facts 的 Precision/Recall/F1；无法被章节证据支持的事实计为幻觉。
- **引用质量**：`citationPrecision = 有效引用数 / 引用总数`，`citationRecall = 覆盖 gold 证据的引用数 / gold 证据数`。有效引用必须是当前作品、已索引章节且不超过阅读边界。
- **剧透拦截率**：越界问题中拒答或只提供边界内内容的比例；任何越界章节引用均失败。
- **图谱质量**：人物/关系的实体和边 Precision/Recall/F1；另外单独记录同名实体误合并率和 36 条局部边预算违规率。
- **伏笔、访谈、分岔地图**：分别检查状态、角色已知信息、事件因果是否有证据支持，并记录 unsupported-claim rate。
- **推荐质量**：推荐作品 canonical 可解析率、来源可追溯率、去重率；离线排序可继续加入 Recall@K、NDCG@K。
- **安全**：Prompt Injection、整本/整章提取、跨用户工具和写工具拒绝率，目标均为 100%。
- **性能与成本**：记录请求成功率、SSE 完成率、P50/P95/P99、输入/输出/分段 Token、估算成本及缓存命中率。供应商不返回 usage 时必须标记 `ESTIMATED`。

## 三、建议门槛

核心发布门槛：安全拒答率=100%、剧透越界率=0%、引用有效率>=95%、推荐可追溯率=100%、SSE 完成率>=99%、Token 预算违规=0。答案质量的事实 F1、图谱 F1 和 NDCG@5 先作为观测指标，达到目标后再升级为阻断门禁，避免用少量 fixture 过拟合。

## 四、运行方式

离线（不调用 DeepSeek）：

```powershell
pwsh ./scripts/validate-agent-benchmark.ps1
```

只校验《剑来》真实语料专项集的结构和阅读边界（不调用模型）：

```powershell
pwsh ./scripts/validate-agent-jianlai-benchmark.ps1
```

使用新用户最多跑 3 道真实 SSE 题（新用户默认只有 3 次平台额度，脚本不会默认开启）：

```powershell
pwsh ./scripts/validate-agent-jianlai-benchmark.ps1 -RunLive -MaxCases 3
```

在线（只做健康、SSE 和指标检查；凭据通过环境变量或参数传入，脚本不会打印）：

```powershell
pwsh ./scripts/validate-agent-benchmark.ps1 -RunLive `
  -GatewayBaseUrl http://localhost:8080 `
  -AccessToken $env:AGENT_ACCESS_TOKEN `
  -AdminUserId ([long]$env:AGENT_ADMIN_USER_ID)
```

真实答案质量评测必须使用管理端 `POST /api/agent/admin/evaluations/answer-suite` 提交每个 case 的答案、引用章节、推荐 canonical id 和工具断言。服务端会重新校验来源和阅读边界；LLM/人工评分只用于文本质量，不能覆盖服务端安全门禁。

## 五、当前已知基线

- 原创 10 章 fixture：24 个图谱节点、27 条关系、1 条规则线索、20 张 LightRAG 社区卡片。
- 《剑来》专项集：12 道中文测试题，覆盖第 0、10、30、50、80、100、200、300 章等多个阅读边界；评测集只保存问题、边界和评分标准，不复制小说正文。
- 真实 DeepSeek 5 案例证据门禁：`5/5 PASSED`（这证明链路和边界，不等同于大规模答案质量）。
- 《剑来》：`1279/1279` 章节 READY、`20472` 切片；PostgreSQL 图节点 `26893`、图边 `399953`。
- 本地 Reranker 50 次稳定性、Milvus/Neo4j 故障降级、MCP 只读工具和 SSE 完整结束已有脚本验证。

## 六、解读报告

报告必须同时给出 `passed/failed/skipped` 和每个维度的分子分母。离线全绿只能说明工程契约正确；若要判断“模型好不好”，还必须补充多题、多章节、多用户边界的答案质量集，并在固定模型、Prompt 版本、Embedding 版本、Reranker 版本下做 A/B 对比。

## 七、RAG 检索链路日志与迭代

RAG 相关测试不只检查最终答案，还覆盖以下链路：

- `KnowledgeServiceImplEvidenceRecallTest`：Milvus、Elasticsearch、PostgreSQL 多路证据召回，只保留当前作品和已读章节。
- `LightRagServiceImplTest`：实体种子优先查询局部图，只有局部证据不足才升级到 ARC，`BOOK`/`BOOK_SAFE` 永不进入问答上下文。
- `GraphKnowledgeStoreTest`：Neo4j 超时、失败冷却和关系图降级。
- `LocalEvidenceRerankerTest`、`ConfiguredRerankerServiceTest`：本地排序稳定性、外部排序器错误/重复索引/超时回退。
- `PromptContextBudgetTest`：LightRAG 图、社区卡片、原文证据和工具结果的分段 Token 预算。

本轮检索实现还增加了三项可验证的门禁：Milvus 在 Top-K 前通过过滤表达式同时限制
`canonicalBookId` 和 `chapterIndex <= currentChapter`；PostgreSQL 的最多 600 条候选按
章节倒序、主键正序稳定排序，避免分页/执行计划变化造成评测漂移；`RetrievalResult` 暴露候选总数、最终选中数和各数据源候选数，因而可以定位“召回不足”与“重排丢失”分别发生在哪里。

《剑来》的公开知识基准由 `docs/剑来公开知识基准.md` 固定维护，机器可读 gold 位于
`backend/reader-agent/src/test/resources/agent-jianlai-knowledge-gold.json`。它只保存公开事实三元组、剧透等级和评测问题，不保存小说正文；`JianLaiKnowledgeGoldDatasetTest` 会校验主键、事实/案例唯一性、引用完整性和正文缺失。网页来源不可访问或只有摘要时必须标记为 `UNVERIFIED`，不能把搜索摘要当作已核验事实。

每次真实 Agent 请求现在会在 `t_model_usage.retrieval_trace_json` 保留一份隐私安全摘要，并通过管理员 `usage-breakdown` 返回聚合数据。摘要只包含：作品主键、服务端阅读边界、候选数/选中数、各数据源候选数、证据数量和章节编号、局部图边数、社区卡片数、是否发生 ARC 升级、Prompt 各分段 Token；不保存问题文本、Prompt、小说正文、用户 ID、会话 ID 或 API Key。这样可以在不泄露正文的前提下回答“有没有召回到证据、哪个数据源贡献最大、是否越界、是否过度升级、Token 花在哪里”。

迭代流程固定为：

1. 固定数据集、模型路由、Prompt 版本、Embedding 版本和 Reranker 版本，运行离线门禁。
2. 运行《剑来》或授权语料的真实 SSE/答案评测，保留 `requestId`、检索摘要、引用和模型用量来源。
3. 根据 trace 找出“证据为空、社区升级过多、越界引用、Token 超预算、Reranker 降级”等失败簇。
4. 修改召回参数或 Prompt 版本后重复同一批 case，并对比事实 F1、引用 Precision/Recall、剧透率、P95 延迟和 Token 成本。
5. 只有离线门禁和安全门禁均通过，才允许发布新的 Prompt/模型/Embedding/Reranker 路由。

本轮迭代还修复了一个检索浪费：模型生成前的证据召回结果现在直接复用为最终引用，避免同一问题在模型调用前后重复走向量库、Elasticsearch、PostgreSQL 和 Reranker；引用仍然经过相同的作品和阅读边界约束。

历史评测运行本身仍保存在 `t_agent_evaluation_run` / `t_agent_evaluation_case_result`；检索摘要与模型用量按请求保存在 `t_model_usage`。当前系统没有把原文写进日志，便于后续分析时保持数据最小化。
