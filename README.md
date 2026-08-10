# 善阅坊 Reader

善阅坊是一个面向中文网络小说阅读场景的全栈项目，包含书源管理、书库与书架、在线阅读、阅读进度、排行榜、评论互动、签到积分，以及独立的小说 AI Agent。

Agent 同时提供右下角悬浮入口和独立工作台，支持中文对话、自然语言找书、书架作品引用、无剧透剧情回忆、人物关系图、伏笔雷达、角色访谈、阅读分岔地图、相似书 DNA 和自定义 OpenAI-compatible 模型。

## 技术架构

- 前端：Vue 3、Vite、Pinia、Vue Router、Cytoscape.js。
- 后端：Java 17、Spring Boot 3、Spring Cloud、Spring AI、MyBatis-Plus、Sa-Token。
- 微服务：Gateway、User、Novel、Comment、Interaction、Check-in、Agent。
- 数据与中间件：PostgreSQL、Redis、RabbitMQ、Nacos、MinIO、Elasticsearch、Milvus、Neo4j。
- 可观测性：Prometheus、OpenTelemetry、Jaeger。
- 本地环境：Docker Compose + PowerShell 启动脚本。

## 小说 Agent

项目只采用 LightRAG 作为图谱问答架构，不保留传统 GraphRAG 的全书图遍历或全书社区报告拼接：

1. 根据作品和用户已读章节确定服务端防剧透边界。
2. 从问题中的实体或可信别名出发，查询有界局部图。
3. 使用 Milvus 语义召回、Elasticsearch 关键词召回、PostgreSQL 权威片段和 LightRAG 图 evidence 组成候选。
4. 通过本地混合 Reranker 或可选外部 Reranker 精排。
5. 在统一 Token 预算内生成带章节依据的中文回答。

PostgreSQL 保存权威图节点、关系、证据和审核状态；Neo4j 是可重建的局部关系查询投影。模型抽取结果必须经过节点类型、逐字 evidence、关系端点类型和可信别名门禁。事件与伏笔可以基于已有正文索引增量重算，不需要重新向量化整本小说。

《剑来》前 100 章仅作为一份固定的专项评测语料，而不是运行时规则来源。最新严格评测为：关系召回 37.5%、检索 Recall@5 88.89%、MRR 0.6667、防剧透越界词命中 0；生产代码通过已审实体和别名驱动候选召回，不编码该书的人物、地点或章节。完整过程见 [《剑来》前 100 章 RAG 五轮迭代报告](docs/剑来前100章RAG五轮迭代报告.md)。

## 目录结构

```text
Reader/
├─ frontend/                 Vue 前端
├─ backend/                  Spring Cloud 微服务
│  ├─ reader-gateway/
│  ├─ reader-user/
│  ├─ reader-novel/
│  ├─ reader-comment/
│  ├─ reader-interaction/
│  ├─ reader-checkin/
│  └─ reader-agent/
├─ docker/                   本地中间件 Compose 配置
├─ scripts/                  启动、验证、Benchmark 和压测脚本
├─ docs/                     架构、验收、面试与实验报告
└─ artifacts/                本地生成的结构化评估结果
```

## 本地启动

### 环境要求

- JDK 17
- Maven 3.9+
- Node.js 20+
- Docker Desktop（支持 Docker Compose）
- PowerShell 7 推荐；Windows PowerShell 也可用于日常启动脚本

### 配置

复制 `.env.example` 为 `.env`，为本地环境填写随机的内部令牌和加密密钥。需要调用平台模型或构建模型图谱时，再配置 OpenAI-compatible 模型地址与 Key。

```powershell
Copy-Item .env.example .env
```

不要提交 `.env`、API Key、访问令牌或用户自定义模型密钥。

### 启动服务

```powershell
pwsh -NoProfile -File scripts/start-all.ps1
```

也可以分别启动：

```powershell
pwsh -NoProfile -File scripts/start-middleware.ps1
pwsh -NoProfile -File scripts/start-backend.ps1
pwsh -NoProfile -File scripts/start-frontend.ps1
```

默认访问地址：

- 前端：`http://localhost:3000`
- API Gateway：`http://localhost:8080`
- Agent 服务健康检查：`http://localhost:8086/actuator/health`
- Nacos：`http://localhost:8848/nacos`
- RabbitMQ：`http://localhost:15672`
- MinIO：`http://localhost:9001`
- Prometheus：`http://localhost:9090`
- Jaeger：`http://localhost:16686`

检查或停止全部服务：

```powershell
pwsh -NoProfile -File scripts/check-status.ps1
pwsh -NoProfile -File scripts/stop-all.ps1
```

## 构建与测试

后端：

```powershell
Set-Location backend
mvn test
```

前端：

```powershell
Set-Location frontend
npm install
npm run build
```

运行《剑来》前 100 章结构与检索评估：

```powershell
pwsh -NoProfile -File scripts/evaluate-jianlai-rag-quality.ps1 `
  -Round round-5-final `
  -EvaluateRetrieval
```

评估脚本只保存结构化指标和短失败样例，不输出 API Key，也不会额外复制整本小说正文。

## 主要文档

- [小说智能体架构计划](docs/novel-agent-architecture-plan.md)
- [小说智能体工作内容详解](docs/小说智能体工作内容详解.md)
- [Benchmark 评估方案](docs/小说智能体Benchmark评估方案.md)
- [前 100 章五轮迭代报告](docs/剑来前100章RAG五轮迭代报告.md)
- [部署与外部验证手册](docs/novel-agent-external-validation-runbook.md)
- [接口压测报告详细说明](docs/接口压测报告详细说明.md)

## 当前边界

本项目主要用于学习、作品展示和求职。当前 Agent 已具备完整工程链路，但抽取质量仍有可迭代空间：前 100 章人物连边率和跨章事件数量偏低，部分保守别名需要人工审核，伏笔还需要更完整的跨窗口回收状态判断。报告中的指标应按固定数据、模型、Prompt、Embedding 和 Reranker 版本解读，不能把一次测试结果视为所有小说的普遍质量。
