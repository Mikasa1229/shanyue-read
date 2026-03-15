# 善阅坊 —— 小说阅读分享平台 项目设计文档

> 版本：v2.1 | 日期：2026-03-15 | 架构：Spring Cloud Alibaba 微服务

---

## 一、项目概述

### 1.1 项目背景
善阅坊是一个面向书迷的小说阅读与分享社区，支持小说发布管理、点评互动、点赞收藏、阅读打卡等核心功能，采用微服务架构保证系统高可用与水平扩展能力。

### 1.2 核心功能模块
| 模块 | 功能描述 |
|------|----------|
| 用户服务 | 注册、登录、JWT/Sa-Token 鉴权、个人信息 |
| 小说服务 | 小说增删改查、全文搜索、分类筛选 |
| 点评服务 | 提交点评、删除点评、点评分页列表 |
| 互动服务 | 点赞收藏、取消操作、状态查询 |
| 打卡服务 | 每日打卡、打卡记录、连续天数统计 |

### 1.3 非功能需求
| 指标 | 目标 |
|------|------|
| 接口响应 | P99 < 200ms |
| 并发支持 | 单服务 QPS > 5000 |
| 可用性 | 99.9%（三九）|
| 数据一致性 | 强一致（本地事务）+ 最终一致（跨服务） |
| 可观测性 | 全链路追踪 + 指标监控 + 日志聚合 |

---

## 二、微服务架构设计

### 2.1 整体架构图
```
┌─────────────────────────────────────────────────────────────────┐
│                         客户端层                                  │
│           Web (Vue 3)  /  iOS  /  Android                        │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTPS
┌───────────────────────────▼─────────────────────────────────────┐
│                      CDN / Nginx                                  │
│              静态资源加速 + 负载均衡 + SSL 终止                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│               API Gateway (Spring Cloud Gateway)                  │
│    路由转发 | 鉴权过滤 | 限流熔断 | 请求日志 | 灰度发布              │
└──────┬──────────┬──────────┬──────────┬──────────┬──────────────┘
       │          │          │          │          │
  ┌────▼───┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐
  │用户服务 │ │小说服务 │ │点评服务 │ │互动服务 │ │打卡服务 │
  │:8081   │ │:8082   │ │:8083   │ │:8084   │ │:8085   │
  └────┬───┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘
       │          │          │          │          │
┌──────▼──────────▼──────────▼──────────▼──────────▼──────────────┐
│                       基础设施层                                   │
│  Nacos（注册中心+配置中心）| Seata（分布式事务）| Sentinel（限流熔断）│
│  MySQL 8.0 | Redis 7.x | Kafka | Elasticsearch | MinIO           │
│  Prometheus + Grafana | SkyWalking | ELK Stack                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 服务拆分原则
按**领域驱动设计（DDD）** 划分服务边界，每个服务拥有独立数据库（Database per Service）：

| 服务名 | 端口 | 数据库 | 职责 |
|--------|------|--------|------|
| `reader-gateway` | 8080 | - | API 网关，统一入口 |
| `reader-user` | 8081 | `db_user` | 用户注册/登录/鉴权 |
| `reader-novel` | 8082 | `db_novel` | 小说 CRUD + ES 搜索 |
| `reader-comment` | 8083 | `db_comment` | 点评管理 |
| `reader-interaction` | 8084 | `db_interaction` | 点赞收藏 |
| `reader-checkin` | 8085 | `db_checkin` | 阅读打卡 |

---

## 三、技术选型（大厂主流栈）

### 3.1 完整技术栈
| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **基础框架** | Spring Boot | 3.2.x | 主框架 |
| **微服务** | Spring Cloud Alibaba | 2023.0.x | 微服务全家桶 |
| **服务注册/配置** | Nacos | 2.3.x | 服务发现 + 动态配置 |
| **API 网关** | Spring Cloud Gateway | 4.x | 路由 + 鉴权 + 限流 |
| **服务调用** | OpenFeign | 4.x | 声明式 HTTP 客户端 |
| **熔断限流** | Sentinel | 1.8.x | 阿里开源，大厂标准 |
| **分布式事务** | Seata | 2.x | AT 模式，跨服务事务 |
| **ORM** | MyBatis-Plus | 3.5.x | CRUD 增强 + 乐观锁 |
| **主数据库** | PostgreSQL | 16.x | 主存储，支持 JSON/全文索引 |
| **缓存 L1** | Caffeine | 3.x | 本地缓存，纳秒级 |
| **缓存 L2** | Redis | 7.x | 分布式缓存 + 分布式锁（Redisson）|
| **消息队列** | RabbitMQ | 3.13.x | 可靠投递，死信队列，延迟消息 |
| **全文搜索** | Elasticsearch | 8.x | 小说标题/简介搜索 |
| **对象存储** | MinIO | RELEASE.2024 | 封面图/用户头像存储 |
| **鉴权** | Sa-Token | 1.38.x | 国内大厂首选，支持 Redis 共享会话 |
| **链路追踪** | SkyWalking | 9.x | 全链路 APM |
| **指标监控** | Prometheus + Grafana | - | 服务指标可视化 |
| **日志聚合** | ELK（ES + Logstash + Kibana）| 8.x | 统一日志管理 |
| **CDC 同步** | Debezium | 2.6.x | PostgreSQL WAL → 缓存失效（替代 Canal）|
| **容器化** | Docker + K8s | - | 容器编排 |
| **接口文档** | Knife4j | 4.x | Swagger3 增强 |
| **测试** | JUnit 5 + Mockito + Testcontainers | - | 单元 + 集成测试 |
| **构建** | Maven | 3.9.x | 多模块管理 |
| **前端** | Vue 3 + Vite + Pinia + Element Plus | - | SPA |

### 3.2 技术选型说明
| 技术 | 选型理由 |
|------|----------|
| PostgreSQL 16 | 原生支持 JSONB、全文索引、WAL CDC；相比 MySQL 对复杂查询更友好 |
| RabbitMQ 3.13 | 消息可靠性强，支持死信队列、延迟插件、消息确认机制；场景匹配点赞/打卡异步写 |
| Debezium | 监听 PostgreSQL WAL 日志实现 CDC，替代 Canal（Canal 仅支持 MySQL）|
| Caffeine + Redis | L1 本地缓存减少 Redis 网络 IO，L2 Redis 保证分布式一致性 |
| Sa-Token | 比 Spring Security + JWT 更轻量，内置 Redis 会话共享，适合微服务鉴权 |
| Sentinel | 接口限流 + 熔断降级，防止服务雪崩，大厂标配 |
| Seata AT | 跨服务写操作（如删除小说级联清理）保证分布式事务一致性 |
| SkyWalking | 微服务链路追踪，自动采集 Spring Boot / Feign / PostgreSQL / Redis 调用链 |

---

## 四、项目结构

```
reader/
├── docs/                          # 设计文档
├── reader-common/                 # 公共模块（被其他服务依赖）
│   ├── common-core/               # 通用工具：Result、Exception、常量
│   └── common-feign/              # Feign 客户端接口定义
├── reader-gateway/                # API 网关服务（:8080）
├── reader-user/                   # 用户服务（:8081）
├── reader-novel/                  # 小说服务（:8082）
├── reader-comment/                # 点评服务（:8083）
├── reader-interaction/            # 互动服务（:8084）
├── reader-checkin/                # 打卡服务（:8085）
├── docker/                        # Docker & K8s 配置
│   ├── docker-compose.yml         # 本地开发环境
│   └── k8s/                       # K8s 部署描述文件
└── pom.xml                        # 父 POM，统一版本管理
```

每个业务服务内部结构（以 `reader-novel` 为例）：
```
reader-novel/src/main/java/com/shanyuefang/novel/
├── controller/        # REST 接口层
├── service/           # 业务逻辑层
│   └── impl/
├── mapper/            # MyBatis-Plus Mapper
├── domain/
│   ├── entity/        # 数据库实体
│   ├── dto/           # 请求 DTO（含参数校验注解）
│   └── vo/            # 响应 VO
├── event/             # Kafka 事件发送/消费
├── cache/             # 二级缓存管理
└── NovelApplication.java
```

---

## 五、数据库设计

> 每个微服务独立数据库（PostgreSQL Schema 隔离），通过服务调用而非 JOIN 获取跨库数据。

### 5.1 `db_user` — 用户表
```sql
CREATE TABLE t_user (
    id          BIGINT       PRIMARY KEY,                   -- 雪花算法 ID（应用层生成）
    username    VARCHAR(32)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,                      -- BCrypt 加密
    nickname    VARCHAR(64)  NOT NULL,
    avatar      VARCHAR(256),                               -- MinIO 对象存储路径
    status      SMALLINT     NOT NULL DEFAULT 1,            -- 1正常 0封禁
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_user_username ON t_user(username);
```

### 5.2 `db_novel` — 小说表
```sql
CREATE TABLE t_novel (
    id          BIGINT       PRIMARY KEY,
    title       VARCHAR(128) NOT NULL,
    author_id   BIGINT       NOT NULL,
    cover       VARCHAR(256),                               -- MinIO 路径
    description TEXT,
    category    VARCHAR(32),
    tags        JSONB        DEFAULT '[]',                  -- PostgreSQL 原生 JSONB
    status      SMALLINT     NOT NULL DEFAULT 1,            -- 1连载 2完结
    like_count  INT          NOT NULL DEFAULT 0,
    fav_count   INT          NOT NULL DEFAULT 0,
    view_count  INT          NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,            -- 乐观锁
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_novel_author_id   ON t_novel(author_id);
CREATE INDEX idx_novel_category    ON t_novel(category, status) WHERE deleted = FALSE;
CREATE INDEX idx_novel_created_at  ON t_novel(created_at DESC);
CREATE INDEX idx_novel_tags        ON t_novel USING GIN(tags);  -- JSONB 索引
```

> **ES 同步**：Debezium 监听 PostgreSQL WAL（`t_novel` 的 INSERT/UPDATE/DELETE）→ 推送至 RabbitMQ → ES Sink Consumer 同步至 `novel_index`，支持中文分词全文检索。

### 5.3 `db_comment` — 点评表
```sql
CREATE TABLE t_comment (
    id          BIGINT       PRIMARY KEY,
    novel_id    BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    parent_id   BIGINT,                                     -- 回复楼层，NULL 为根评论
    root_id     BIGINT,                                     -- 根评论 ID
    content     VARCHAR(1000) NOT NULL,
    like_count  INT          NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,            -- 1正常 0审核中
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_comment_novel_root ON t_comment(novel_id, root_id);
CREATE INDEX idx_comment_user_id    ON t_comment(user_id);
```

### 5.4 `db_interaction` — 点赞收藏表
```sql
CREATE TABLE t_interaction (
    id          BIGINT      PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    target_id   BIGINT      NOT NULL,
    target_type SMALLINT    NOT NULL,                       -- 1小说 2点评
    action      SMALLINT    NOT NULL,                       -- 1点赞 2收藏
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_target_action UNIQUE (user_id, target_id, target_type, action)
);

CREATE INDEX idx_interaction_target ON t_interaction(target_id, target_type, action);
```

### 5.5 `db_checkin` — 打卡表
```sql
CREATE TABLE t_checkin (
    id           BIGINT      PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    novel_id     BIGINT      NOT NULL,
    checkin_date DATE        NOT NULL,
    note         VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_novel_date UNIQUE (user_id, novel_id, checkin_date)
);

CREATE INDEX idx_checkin_user_date ON t_checkin(user_id, checkin_date DESC);
```

---

## 六、接口设计

### 6.1 统一响应结构
```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "traceId": "abc123xyz",
  "timestamp": 1710000000000
}
```
> `traceId` 由 SkyWalking 自动注入，便于日志关联。

### 6.2 错误码规范
| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 参数校验失败 |
| 401 | 未登录 / Token 失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 业务冲突（如重复打卡） |
| 429 | 限流（Sentinel 触发） |
| 500 | 服务内部错误 |
| 503 | 服务不可用（熔断降级） |

### 6.3 小说服务接口
| 方法 | 路径 | 描述 | 鉴权 |
|------|------|------|------|
| POST | `/api/novels` | 新增小说 | 是 |
| PUT | `/api/novels/{id}` | 编辑小说（乐观锁） | 是（仅作者） |
| DELETE | `/api/novels/{id}` | 逻辑删除 | 是（仅作者） |
| GET | `/api/novels/{id}` | 详情（二级缓存） | 否 |
| GET | `/api/novels` | 分页列表（游标分页） | 否 |
| GET | `/api/novels/search` | ES 全文搜索 | 否 |

### 6.4 点评服务接口
| 方法 | 路径 | 描述 | 鉴权 |
|------|------|------|------|
| POST | `/api/novels/{novelId}/comments` | 提交点评 | 是 |
| DELETE | `/api/comments/{id}` | 删除点评 | 是（仅本人） |
| GET | `/api/novels/{novelId}/comments` | 点评分页 | 否 |

### 6.5 互动服务接口
| 方法 | 路径 | 描述 | 鉴权 |
|------|------|------|------|
| POST | `/api/interactions/like` | 点赞/取消（幂等） | 是 |
| POST | `/api/interactions/favorite` | 收藏/取消（幂等） | 是 |
| GET | `/api/interactions/status` | 批量查询状态 | 是 |
| GET | `/api/users/favorites` | 我的收藏列表 | 是 |

### 6.6 打卡服务接口
| 方法 | 路径 | 描述 | 鉴权 |
|------|------|------|------|
| POST | `/api/checkins` | 今日打卡（幂等）| 是 |
| GET | `/api/checkins` | 月度打卡记录 | 是 |
| GET | `/api/checkins/streak` | 连续打卡天数 | 是 |

---

## 七、核心技术方案

### 7.1 鉴权方案（Sa-Token + Redis）
```
客户端登录 → 用户服务颁发 Token → 存入 Redis（Key: satoken:login:{userId}）
         → 客户端携带 Token 请求 Gateway
         → Gateway 过滤器调用 Sa-Token 校验
         → 校验通过后透传用户信息 Header（X-User-Id、X-User-Name）
         → 下游服务从 Header 直接获取，无需再次查 Redis
```

### 7.2 二级缓存方案
```
请求 → Caffeine L1（命中，< 1ms）→ 直接返回
     → Caffeine L1（未命中）→ Redis L2（命中，< 5ms）→ 回填 L1 → 返回
     → Redis L2（未命中）→ 查 PostgreSQL → 回填 L2 + L1 → 返回

缓存失效：Debezium 监听 PostgreSQL WAL → 推送 RabbitMQ → 各服务消费 → 删除 L1 + L2
```

| 缓存 Key | 内容 | Caffeine TTL | Redis TTL |
|----------|------|-------------|-----------|
| `novel:detail:{id}` | 小说详情 | 5 分钟 | 30 分钟 |
| `novel:list:{cursor}:{size}:{category}` | 列表 | 1 分钟 | 5 分钟 |
| `interaction:status:{userId}:{targetId}:{type}` | 互动状态 | 2 分钟 | 1 小时 |
| `checkin:streak:{userId}` | 连续打卡 | - | 1 天 |
| `checkin:bitmap:{userId}:{yearMonth}` | 月打卡 Bitmap | - | 32 天 |

**防穿透**：空结果缓存 null（TTL 60s）+ 布隆过滤器（Guava BloomFilter）

**防击穿**：Redisson `tryLock` 加分布式锁，只允许一个线程回源 DB

**防雪崩**：TTL 加随机抖动 ±10%

### 7.3 RabbitMQ 消息设计

**Exchange 规划：**
```
reader.topic (Topic Exchange)
├── routing key: novel.view.*          → Queue: q.novel.view.count      (浏览计数异步更新)
├── routing key: interaction.like.*    → Queue: q.interaction.like      (点赞计数异步更新)
├── routing key: interaction.favorite.*→ Queue: q.interaction.favorite  (收藏计数异步更新)
└── routing key: cache.invalidate.*    → Queue: q.cache.invalidate      (Debezium CDC 缓存失效)

reader.dead (Direct Exchange)          → Queue: q.dead.letter           (死信统一处理)
```

| Exchange | Routing Key | 生产者 | 消费者 | 作用 |
|----------|-------------|--------|--------|------|
| `reader.topic` | `novel.view.{novelId}` | 小说服务 | 小说服务 | 浏览计数异步写 |
| `reader.topic` | `interaction.like.{targetId}` | 互动服务 | 小说/点评服务 | 点赞计数异步写 |
| `reader.topic` | `interaction.favorite.{novelId}` | 互动服务 | 小说服务 | 收藏计数异步写 |
| `reader.topic` | `cache.invalidate.novel` | Debezium Adapter | 小说服务 | WAL 驱动缓存失效 |

**可靠性保障：**
- 生产者：`publisher-confirm` + `publisher-returns`，发送失败写 DB 重试表
- 消费者：手动 ACK（`acknowledge-mode: manual`），业务异常 NACK + 死信队列兜底
- 幂等消费：消息携带 `messageId`（雪花 ID），消费前 `SET NX` Redis 幂等 Key，TTL = 重试窗口 × 2

### 7.4 分布式事务（Seata AT 模式）
**场景**：用户删除小说 → 需同时删除 `db_novel`（小说服务）+ `db_comment`（点评服务）+ `db_interaction`（互动服务）

```
@GlobalTransactional
deleteNovel(id) {
    novelService.delete(id)          // 本地事务
    commentFeign.deleteByNovel(id)   // 远程调用，Seata 管理
    interactionFeign.deleteByNovel(id)
}
```

> 点赞/收藏等高频操作**不使用 Seata**，改用 Kafka 最终一致性 + 补偿机制，避免性能损耗。

### 7.5 限流熔断（Sentinel）
| 规则 | 接口 | 阈值 | 策略 |
|------|------|------|------|
| QPS 限流 | `POST /api/checkins` | 1000 QPS | 快速失败 |
| QPS 限流 | `POST /api/interactions/like` | 2000 QPS | 排队等待 |
| 熔断降级 | 小说详情接口 | 慢调用比例 > 50% | 熔断 10s，返回缓存兜底 |
| 热点限流 | `GET /api/novels/{id}` | 热点 ID 单独限流 | Sentinel 热点规则 |

### 7.6 参数校验 & 异常处理
```
DTO 层：@NotBlank / @Size / @Min / @Pattern（JSR-380）
Controller 层：@Validated 触发校验
全局处理：
  @RestControllerAdvice GlobalExceptionHandler
  ├── BusinessException        → 业务错误码，不打印堆栈
  ├── ConstraintViolationException → 400，返回字段级错误信息
  ├── FeignException           → 502，远程服务调用失败
  ├── SentinelBlockException   → 429，触发限流
  └── Exception                → 500，打印堆栈 + SkyWalking 告警
```

### 7.7 事务控制要点
- 所有 `@Transactional` 明确声明 `rollbackFor = Exception.class`
- 事务方法**不要过大**，避免长事务持锁（DB 锁 < 50ms）
- 计数更新使用 SQL 原子操作：`UPDATE t_novel SET like_count = like_count + 1 WHERE id = ?`
- 打卡去重：先查 Redis，命中则直接返回"已打卡"，未命中再走 DB `INSERT IGNORE`

---

## 八、可观测性体系

### 8.1 链路追踪（SkyWalking）
- 探针自动注入 Spring Boot / OpenFeign / MySQL / Redis / Kafka
- 每个请求生成 `traceId`，贯穿所有服务调用链
- 慢接口（> 200ms）自动上报告警

### 8.2 指标监控（Prometheus + Grafana）
| 监控维度 | 指标 |
|----------|------|
| JVM | 堆内存、GC 次数/耗时、线程数 |
| HTTP | QPS、响应时间分布（P50/P95/P99）、错误率 |
| MySQL | 连接池使用率、慢查询数 |
| Redis | 命中率、内存使用、连接数 |
| Kafka | 消费 Lag、生产/消费 TPS |
| Sentinel | 限流次数、熔断次数 |

### 8.3 日志规范（ELK）
- 统一 JSON 格式输出，字段：`timestamp / level / service / traceId / userId / message`
- Logstash 采集 → Elasticsearch 存储 → Kibana 查询
- ERROR 日志触发钉钉/企微告警

---

## 九、部署架构

### 9.1 本地开发（Docker Compose）
```yaml
# docker/docker-compose.yml 启动所有中间件
services: mysql, redis, kafka, zookeeper, nacos, elasticsearch, minio, skywalking-oap
```

### 9.2 生产环境（Kubernetes）
```
Namespace: reader-prod
├── Deployment: reader-gateway     (2 副本)
├── Deployment: reader-user        (2 副本)
├── Deployment: reader-novel       (3 副本)
├── Deployment: reader-comment     (2 副本)
├── Deployment: reader-interaction (3 副本)
└── Deployment: reader-checkin     (2 副本)
```
- 配置通过 Nacos Config 动态下发，无需重启服务
- 服务使用 K8s 存活探针（`/actuator/health`）自动重启

---

## 十、慢查询优化方案

| 场景 | 问题 | 优化方案 |
|------|------|----------|
| 小说列表 | `LIMIT offset, size` 深翻页 | 游标分页（`WHERE id < lastId LIMIT size`）+ 覆盖索引 |
| 点评列表 | 多表 JOIN 获取用户信息 | 点评查出后，批量调用用户服务获取昵称（Feign 批量接口）|
| 打卡历史 | 按月扫描大量记录 | Redis Bitmap 存储当月打卡（位图，O(1) 查询），DB 只做持久化 |
| 连续打卡 | 逐日遍历计算 | Redis 有序集合存打卡日期，`ZREVRANGE` 取最近 N 天快速计算 |
| 搜索小说 | MySQL LIKE `%关键词%` 全表扫 | 迁移至 Elasticsearch，支持分词 + 评分排序 |
| 热点小说 | Redis 单 Key 高频访问 | Caffeine L1 本地缓存拦截，减少 Redis 压力 |

---

## 十一、开发计划

### 阶段一：基础设施（第 1 周）
- [ ] Maven 多模块父 POM，统一依赖版本
- [ ] Docker Compose 启动本地中间件
- [ ] common-core：Result、BusinessException、雪花 ID
- [ ] Nacos 服务注册 + 配置中心接入
- [ ] Gateway：路由配置 + Sa-Token 鉴权过滤器

### 阶段二：用户 & 小说服务（第 2 周）
- [ ] 用户服务：注册/登录/Sa-Token 颁发
- [ ] 小说服务：CRUD + 二级缓存 + Debezium 缓存失效
- [ ] ES 索引初始化 + Debezium → RabbitMQ → ES Sink 同步配置

### 阶段三：互动 & 打卡服务（第 3 周）
- [ ] 点评服务：提交/删除/分页
- [ ] 互动服务：点赞收藏（Kafka 异步计数 + 幂等）
- [ ] 打卡服务：Redis Bitmap + 连续天数计算

### 阶段四：健壮性（第 4 周）
- [ ] Sentinel 限流熔断规则配置
- [ ] Seata 分布式事务接入（删除小说场景）
- [ ] 全局异常处理完善 + 参数校验覆盖
- [ ] SkyWalking + Prometheus + ELK 接入

### 阶段五：测试与调优（第 5 周）
- [ ] 单元测试（覆盖率 > 70%）+ Testcontainers 集成测试
- [ ] JMeter 压测：点赞/打卡接口 1000 QPS
- [ ] EXPLAIN 慢查询分析 + 索引优化
- [ ] 缓存一致性验证（Debezium + RabbitMQ 链路）

---

*文档持续更新，最新版本以 Git 仓库为准。*
