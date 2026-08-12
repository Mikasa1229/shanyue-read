# RAG 泛化修正说明

## 问题

《剑来》前 100 章可以作为真实语料和固定 Benchmark，但它不能成为生产逻辑的规则来源。此前审计发现两处不符合这一原则的运行时代码：线索过滤器包含该书的人名白名单，人物身份提示包含该书的具体称呼和姓名示例。这会把单一语料的经验倒灌到所有小说，属于错误的评测驱动开发。

## 修正

- 删除线索过滤器中的专有名词白名单。线索是否保留只由模型在逐字 evidence 上作出的“未解决且叙事重要”判断、结构化 schema 校验和数量上限决定。
- 将人物身份窗口的高分信号改为通用的命名/身份揭示语言，例如“本名”“化名”“身份揭晓”，不再包含某类作品的固定人物称呼。
- 保留实体、别名和双实体联合召回，但它们完全从当前作品已审核的 `KnowledgeGraphNode` 与 `KnowledgeEntityAlias` 动态解析；共同出现只扩展原文候选，不会创建关系边。
- 将 MCP SDK 验证脚本的检索词改为环境变量 `AGENT_MCP_SEARCH_QUERY`，默认值为通用示例词。

## 验证

新增 `KnowledgeServiceImplEvidenceRecallTest.pairAnchoringUsesRuntimeEntitiesAndAliasesRatherThanBookSpecificNames`，以虚构的“林默 / 城南来客 / 周青”验证别名解析和双实体原文候选。该测试不读取《剑来》资源。

移除特化代码后重新运行同一份《剑来》严格 Benchmark：关系召回 37.5%、检索 Recall@5 88.89%、MRR 0.6667、禁止关系 0、剧透越界 0。指标没有依赖已删除的人名白名单；但这些数字仍只说明该固定语料上的表现，不能外推为所有小说的质量。

运行时源代码扫描确认 `backend/reader-agent/src/main`、`frontend` 和 `scripts` 中没有《剑来》人物、地点或书名的专有匹配；测试资源、评测 JSON、文档和可显式传入的测试查询不在该约束范围内。
