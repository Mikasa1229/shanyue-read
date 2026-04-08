# 善阅坊 —— 开发日志

> 记录项目每个阶段的工作内容、踩坑过程与解决思路。

---

## 目录

1. [项目初始化与基础模块](#1-项目初始化与基础模块)
2. [互动、打卡、小说服务](#2-互动打卡小说服务)
3. [运维脚本与编码问题](#3-运维脚本与编码问题)
4. [Nacos 启动失败修复](#4-nacos-启动失败修复)
5. [编译错误全量修复](#5-编译错误全量修复)
6. [单元测试体系建立](#6-单元测试体系建立)
7. [Nacos 配置自动化初始化](#7-nacos-配置自动化初始化)
8. [书源集成（legado 规则引擎）](#8-书源集成legado-规则引擎)

---

## 1. 项目初始化与基础模块

**提交**：`d84bd22`

### 工作内容

- 搭建 Spring Cloud Alibaba 微服务骨架（父 pom + 7 个子模块）
- 实现 `reader-user`：注册/登录/改密/个人信息，Sa-Token 鉴权
- 实现 `reader-comment`：根评论 + 嵌套回复，软删除级联
- 实现 `reader-gateway`：路由转发 + `AuthGlobalFilter` 鉴权过滤器
- Docker Compose 搭建本地中间件（PostgreSQL / Redis / RabbitMQ / Nacos）

### 技术选型说明

| 选项 | 选择 | 原因 |
|------|------|------|
| 鉴权 | Sa-Token | 比 Spring Security 配置简单，天然支持 Redis 共享 Session |
| ORM | MyBatis-Plus | Lambda 链式 API，减少手写 SQL |
| 消息队列 | RabbitMQ | 可靠投递 + 死信队列，适合计数更新等异步场景 |
| 配置中心 | Nacos | Spring Cloud Alibaba 生态，服务发现和配置合二为一 |

---

## 2. 互动、打卡、小说服务

**提交**：`1e7f20a` / `cbfc417` / `edb3a1c`

### 工作内容

- `reader-interaction`：点赞/收藏 Toggle 逻辑，Redis 状态缓存，MQ 异步更新计数
- `reader-checkin`：Redis Bitmap 快速检测当日打卡，DB 唯一键兜底幂等，连续天数算法
- `reader-novel`：小说 CRUD，Redis 二级缓存（10 分钟 TTL），浏览量防刷（1 分钟限频）

### 亮点：Redis Bitmap 打卡

Bitmap 是 Redis 的位数组结构，每个用户每月只占 `ceil(31/8) = 4 字节`，相比存储完整日期记录节省巨大空间。

```
key: checkin:bitmap:{userId}:{yyyyMM}
bit offset = dayOfMonth - 1  (0-indexed)

SETBIT checkin:bitmap:1001:202503 14 1   # 3月15日打卡
GETBIT checkin:bitmap:1001:202503 14     # 查询是否打卡 → 1
```

缓存过期后从 DB 重建 Bitmap，保证数据不丢失。

---

## 3. 运维脚本与编码问题

**提交**：`a042441` → `e3bcc76` → `f3fcdc8`

### 问题：Windows cmd 中文乱码

`.bat` 文件在中文 Windows 下以 GBK 编码解析。如果文件以 UTF-8 保存（无 BOM），中文字节会被 cmd 当作命令名执行，产生 `'姟务' 不是内部或外部命令` 报错。

#### 解决方案：bat 只做跳板，逻辑写在 PowerShell

```
start-middleware.bat
  └─→ powershell.exe -ExecutionPolicy Bypass -File start-middleware.ps1
```

`.ps1` 文件必须保存为 **UTF-8 with BOM**（文件头 `EF BB BF`）。
PowerShell 5（Windows 默认版本）以系统代码页（GBK）读取无 BOM 的文件；
有 BOM 时才识别为 UTF-8，中文才能正常显示。

#### 写入 BOM 的方法

```powershell
$utf8bom = [System.Text.UTF8Encoding]::new($true)   # true = emitBOM
[System.IO.File]::WriteAllText($path, $content, $utf8bom)
```

---

## 4. Nacos 启动失败修复

**提交**：`677caec` / `daca092`

### 问题 1：Nacos 依赖外部 MySQL 存储

Nacos standalone 模式默认使用内嵌 Derby，但 Docker 重启后 Derby 数据会丢失。
解决：在 docker-compose 中单独加一个 `nacos-mysql` 容器，Nacos 持久化到 MySQL。

### 问题 2：nacos-mysql-schema.sql 有重复 AUTO_INCREMENT

MySQL 8 对 `AUTO_INCREMENT` 关键字的重复声明报错，删除重复项即可。

### 问题 3：网关 `SaReactorSyncHolder.run()` 方法不存在

Sa-Token 1.38.0 的 WebFlux 模块将 API 从 `run(Runnable)` 改为 `setContext()` / `clearContext()`：

```java
// 旧 API（1.37.x）
SaReactorSyncHolder.run(exchange, () -> { ... });

// 新 API（1.38.0）
SaReactorSyncHolder.setContext(exchange);
try {
    // 业务逻辑
} finally {
    SaReactorSyncHolder.clearContext();
}
```

---

## 5. 编译错误全量修复

**对应 Git 当前未提交改动**

### 5.1 `NovelServiceImpl` — `page.convert()` 返回类型不兼容

MyBatis-Plus 的 `page.convert(fn)` 返回 `IPage<R>`（接口），而方法签名声明返回 `Page<NovelVO>`（具体类）。

```java
// 错误写法
Page<NovelVO> result = page.convert(this::toVO);  // IPage 无法赋值给 Page

// 正确写法：手动构造 Page 对象
Page<Novel> rawPage = page(new Page<>(dto.getPage(), dto.getSize()), wrapper);
Page<NovelVO> result = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
result.setRecords(rawPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
```

### 5.2 `RabbitMQConfig` — `Queue` Bean 注入歧义

当 Spring 容器中存在多个 `Queue` Bean 时，方法参数 `Queue queue` 无法自动确定注入哪一个，需要 `@Qualifier` 明确指定：

```java
@Bean
public Binding deadBinding(@Qualifier("deadQueue") Queue deadQueue, DirectExchange deadExchange) {
    return BindingBuilder.bind(deadQueue).to(deadExchange).with("dead.routing.key");
}
```

### 5.3 `RedisConfig` — Gateway 下 Bean 冲突

`reader-common` 的 `RedisConfig` 定义了 Servlet 风格的 `RedisTemplate<String, Object>`。
但 Gateway 是 WebFlux 响应式应用，Spring Boot 的 `RedisAutoConfiguration` 在 WebFlux 下提供另一套配置，两者冲突。

解决：加条件注解，让 `RedisConfig` 只在 Servlet 环境下加载：

```java
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RedisConfig { ... }
```

### 5.4 Nacos `spring.config.import` 必须显式声明

Spring Cloud 2023.x（对应 Spring Boot 3.x）强制要求 `spring.config.import`：

```yaml
spring:
  config:
    import: "optional:nacos:reader-user.yaml"  # optional: 表示 Nacos 无此文件时不报错
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
        file-extension: yaml
        group: reader                            # 有意义的分组名，而非 DEFAULT_GROUP
```

`optional:` 前缀：若 Nacos 中还没有该文件，服务正常启动，使用 application.yml 的默认值。
去掉 `optional:` 则文件不存在时启动报错——适合生产环境强制要求配置齐全的场景。

### 5.5 Nacos `namespace` 必须用 UUID，不能用名称

Nacos 内部用 UUID 标识命名空间，`namespace: reader` 指向一个不存在的 ID，服务注册/发现全部失败。

```yaml
# 错误
nacos:
  discovery:
    namespace: reader   # "reader" 不是有效的 namespace ID

# 正确：删除 namespace 字段，使用默认 public 命名空间
nacos:
  discovery:
    server-addr: localhost:8848
```

---

## 6. 单元测试体系建立

**详细说明见 [notes/unit-testing-mybatisplus.md](notes/unit-testing-mybatisplus.md)**

### 测试结果汇总

| 服务 | 测试类 | 用例数 |
|------|--------|-------|
| reader-gateway | `AuthGlobalFilterTest` | 8 |
| reader-user | `UserServiceTest` | 14 |
| reader-novel | `NovelServiceTest` | 11 |
| reader-comment | `CommentServiceTest` | 9 |
| reader-interaction | `InteractionServiceTest` | 7 |
| reader-checkin | `CheckinServiceTest` | 5 |
| **合计** | | **54** |

全部通过，0 失败，0 错误。

---

## 7. Nacos 配置自动化初始化

**详细说明见 [notes/docker-init-container.md](notes/docker-init-container.md)**

### 核心设计

在 `docker-compose.yml` 添加一个 **一次性 init 容器**，利用 Nacos REST API 自动写入配置，彻底避免手动操作 Nacos 控制台。

```yaml
init-nacos:
  image: alpine:3.19
  depends_on:
    nacos:
      condition: service_healthy   # 等 Nacos 健康检查通过再执行
  command: ["sh", "-c", "apk add -q --no-cache curl && sh /nacos-init.sh"]
  volumes:
    - ./nacos-init.sh:/nacos-init.sh:ro
  restart: "no"                    # 写完就退出，不重启
```

`nacos-init.sh` 用 `curl --data-urlencode` 写入 YAML，自动处理换行符和特殊字符的 URL 编码。

---

## 8. 书源集成（legado 规则引擎）

**提交**：`c0223b6`（含前序多次修复提交）

### 工作内容

- 集成 [legado](https://github.com/gedoor/legado) 阅读 APP 的书源格式，让用户可以从第三方网站搜索小说、获取章节列表、阅读正文
- 实现 `LegadoRuleEngine`：支持 CSS 简写、JSONPath、XPath、正则、链式、降级、模板等全套规则语法
- 实现 `HttpFetcher`：支持 GET/POST、GBK 编码、SSL 忽略、相对 URL 转绝对 URL、单引号 JSON 兼容
- 实现 `BookSourceModel`：兼容 legado 新旧两种书源格式（新格式嵌套对象 vs 旧格式平铺字段）
- 实现 `BookSourceServiceImpl`：导入/搜索/章节/正文完整流程
- 新增 REST 接口 `/api/book-sources/**`，Gateway 路由已配置
- 导入并验证多个书源，覆盖 CSS、JSONPath、POST 搜索等多种类型

### 书源仓库原理

项目内置 legado 格式书源仓库（`book_source/legado/sources/`），每个文件是一个 JSON 数组，每个元素描述一个网站的抓取规则：

```
book_source/legado/sources/
  71e56d4f.json   精选书源（19 个，已全部导入测试）
  b778fe6b.json   大全集（3911 个书源）
  2a1f129b.json, 3bb7b751.json ...  其他分类
```

书源 JSON 关键字段：

| 字段 | 说明 |
|------|------|
| `bookSourceUrl` | 网站根 URL，作为 baseUrl 拼接相对路径 |
| `searchUrl` | 搜索路径，`{{key}}` 替换关键词，末尾可附加 `,'method':'POST',...` |
| `ruleSearch` | 搜索结果解析规则（bookList/name/author/bookUrl/...） |
| `ruleToc` | 目录页解析规则（chapterList/chapterName/chapterUrl） |
| `ruleContent` | 正文页解析规则（content） |

新格式的 `ruleSearch` 是嵌套对象；旧格式使用 `searchList`/`searchName` 等平铺字段，`BookSourceModel.effectiveSearch*()` 自动选择有值的字段。

### LegadoRuleEngine 规则语法

| 类型 | 格式 | 示例 |
|------|------|------|
| JSONPath | `$.path` 或 `$..path` | `$.data.list` |
| XPath | `//tag/@attr` | `//meta[@property='og:title']/@content` |
| CSS 简写 | `class.name` / `tag.name` / `id.name` | `class.bookname` → `.bookname` |
| CSS 标准 | 直接写选择器 | `div.content p` |
| 属性提取 | `rule@attr` | `tag.a.0@href`，`class.box@html` |
| 属性+正则 | `rule@attr##pattern` | `class.article-content.0@html##广告.*` |
| 正则提取 | `##pattern` | `##第(\d+)章` |
| 正则替换 | `##pattern##replace` | `##\s+## ` |
| 链式 | `rule1@rule2@...` | `class.catalog@li@a` |
| 降级 | `rule1\|\|rule2` | `$.title\|\|class.title@text` |
| 模板 | `{{$.path}}` | `/novel/{{$.novelId}}` |

**`splitByAt` 白名单机制**：`@` 既可以是链式分割符（`class.catalog@li`），也可以是属性提取（`tag.a.0@href`）。通过 `KNOWN_ATTRS` 白名单区分：`href/text/html/src/title` 等在白名单内的不分割（走属性提取），`a/li/div/span` 等 HTML 标签名不在白名单内的才分割（走链式 CSS）。

### 踩坑记录

| 问题 | 根因 | 解决方案 |
|------|------|----------|
| `selectSingle` 方法缺失 | `extractList` 引用了未定义的方法 | 补充实现，支持 `.N` 索引定位容器元素 |
| 搜索结果 `name`/`bookUrl` 为 null | `splitByAt` 把 `tag.a.0@title` 错误分成两段，第二段 `title` 从纯文本提取属性失败 | 改用白名单机制，`@title` 整体作为属性提取器 |
| `Set.of()` 启动报错 | `KNOWN_ATTRS` 中 `src` 重复出现，`Set.of()` 不允许重复元素 | 删除多余的 `src` |
| JSONPath 书源 `bookUrl` 为空 | 规则是 URL 模板 `/novel/{{$.novelId}}`，不是 CSS/JSONPath 规则 | 新增 `resolveTemplate`，用 JSONPath 提取值后替换 `{{...}}` 占位符 |
| `@html##regex` 组合不生效 | `cssSingle` 只检查纯字母属性名，`html##\(广告\)` 含 `#` 不匹配 | 先截取 `##` 前的 `attrPart`，再判断是否为已知属性 |
| 23qb.com 正文为空 | `article-content` div 由 JavaScript 动态填充，服务端无法执行 JS | 属网站反爬限制，无法修复；建议换静态渲染书源 |
| `GET /api/book-sources/` 返回 500 | URL 末尾多一个 `/` 导致找不到路由 | 去掉末尾斜杠 |

### 已验证可用书源（截至 2026-03-18）

---

## 第 10 节：三小时开发总结（2026-04-09）

### 工作背景

本次迭代共约三小时，持续完善善阅坊阅读器的前后端功能。在上一轮已完成书架、阅读器、书源搜索的基础上，重点解决用户反馈的体验问题。

---

### 一、已完成功能

#### 1. 发现页搜索结果分页
- **现象**：书源聚合搜索可返回数十条结果，全部平铺展示，页面过长。
- **改动**：`HomeView.vue` 添加 `pagedResults`（每页 10 条）+ `totalPages`/`pageNums` computed，模板增加分页控件，新搜索时 `currentPage` 归 1。
- **结果**：顶部显示"共找到 X 本书，第 N/M 页"，页码超过 7 时自动折叠为省略号。

#### 2. 修复发现页"查看目录"不渲染
- **根因**：`v-else-if="searched"` 块与 `v-else-if="chapterBook.title"` 块平级，当 `searched=true` 时章节面板的条件永远不会被求值。
- **修复**：将章节面板移入 `searched` 块内部，用 `v-if="chapterBook.title"` / `v-else` 嵌套切换。

#### 3. 广场书评与书名绑定
- **需求**：书评发布时应与指定书名强关联，广场 feed 中书名要醒目展示。
- **改动**：
  - `SquareView.vue`：feed 卡片头部改为"推荐了"，书名以金色徽章（`feed-book-badge`）展示在正文上方；写书评弹窗书名输入行改为金色背景，视觉突出。
  - `ReaderView.vue`：书评弹窗改为固定显示当前书名（不可编辑），placeholder 变为"写下你对《书名》的感想…"。

#### 4. 收藏功能（独立于书架）
- **背景**：用户反映"加入书架"和"收藏"不是同一概念；个人中心也缺少收藏展示。
- **后端**（`reader-novel`）：
  - `V5__favorite.sql`：新建 `t_favorite_book` 表，字段含 `user_id`、`book_url`（联合唯一）、书名/作者/封面/书源等。
  - 新增 `FavoriteBook` 实体、`FavoriteBookMapper`、`FavoriteService`/`FavoriteServiceImpl`、`FavoriteController`（`/api/favorites`）。
  - 接口：POST 添加（幂等）、DELETE 取消、GET 分页查询、GET /check 检查状态。
  - 网关（`reader-gateway`）：路由追加 `/api/favorites/**`。
- **前端**：
  - 新增 `api/favorite.js`。
  - `HomeView.vue`：搜索结果"♥ 收藏"按钮改用 favorite API；书架按钮保留为加入书架（两者独立）。
  - `ReaderView.vue`：顶栏 ♡/♥ 改为调用 `apiAddFavorite`/`apiCheckFavorited`。
  - `ProfileView.vue`：我的收藏 tab 改用 `apiGetMyFavorites`，展示书源书籍卡片（书名/作者/封面），支持"去阅读"（跳转 HomeView 章节列表）和"取消收藏"。

#### 5. 阅读进度与书签
- **阅读进度**：
  - `V4__bookshelf_progress.sql`：`t_bookshelf_book` 添加 `last_chapter_index`（INT）、`total_chapters`（INT）。
  - `UpdateProgressDTO` 新增可选字段，`BookshelfServiceImpl.updateProgress` 按需 SET。
  - `ReaderView.vue`：主动加载章节后上报 `chapterIndex` 和 `totalChapters`（登录即上报，不再依赖是否在书架）。
  - `BookshelfView.vue`：有总章节数时展示进度条 + "第 N / M 章"。
- **书签**：
  - 纯前端 `localStorage`（key `reader_bookmarks_${bookUrl}`），最多 30 条。
  - 顶栏增加 🔖 按钮：已标记时变金色；再次点击删除；新增时自动打开书签面板。
  - 书签面板（侧抽屉）：显示章节名 + 时间，点击跳转，右侧 × 删除。

#### 6. 预加载级联修复
- **现象**：设置预载 N 章时，每一个预载完成的章节又触发下一级预载，导致整本书被连续加载。
- **修复**：`loadChapterContent(idx, isPreload=false)` 新增第二参数，`isPreload=true` 时跳过级联触发，只有主动加载才向后预载 N 章。

#### 7. MinIO 头像上传
- **背景**：头像之前写到本地文件系统，用户发现 MinIO bucket 未创建。
- **改动**（`reader-user`）：
  - `pom.xml` 添加 `io.minio:minio:8.5.7`。
  - `application.yml` 添加 `app.minio.*` 配置（endpoint/access-key/secret-key/bucket/public-url）。
  - `MinioProperties.java`：`@ConfigurationProperties(prefix="app.minio")` 属性绑定。
  - `MinioConfig.java`：Bean 初始化时自动创建 bucket（如不存在）并设置公开读取策略（S3 JSON policy）；MinIO 不可达时 WARN 降级，不阻断启动。
  - `UserController.java`：`uploadAvatar` 改用 `minioClient.putObject`，移除本地 `java.nio.file` 写入，返回 MinIO 公开 URL（`http://localhost:9000/reader-avatars/avatars/xxx.jpg`）。

#### 8. 阅读设置优化
- **设置面板背景修复**：teleport 到 body 后背景变为白色，通过 `bgStyle` computed 显式绑定 `:style="bgStyle"` 解决。
- **页面宽度选项**：窄(500px)/中(680px)/宽(900px)/全屏，存 localStorage，绑定 `.reader-content` 的 `max-width`。
- **上拉加载上一章**：`topTrigger` IntersectionObserver 监听顶部哨兵元素，触发 `loadPrevChapter` 插入 `loadedChunks` 头部。

---

### 二、遇到的困难与解决方式

| 困难 | 解决方式 |
|------|---------|
| 发现页章节面板永远不渲染 | `v-else-if` 链平级时后续分支被屏蔽；将章节面板嵌入 `searched` 块内部，用嵌套 `v-if/v-else` 切换 |
| 预加载导致全书连续加载 | 为 `loadChapterContent` 添加 `isPreload` 布尔参数，预加载调用不再递归触发下一轮预加载 |
| Jsoup `el.text()` 合并换行导致正文无段落 | 改用 `elementToText()`：先将 `<br>`/`</p>`/`</div>` 替换为 `\n`，再剥离 HTML 标签，最后按行过滤空行，拼 `<p>` |
| MinIO bucket 未创建且无公开读权限 | `MinioConfig.java` 在 Bean 初始化时检查 bucket 存在性并自动创建；用 S3 policy JSON 设置 `s3:GetObject` 公开读取 |
| MinIO URL 从错误 host 加载头像 | 头像上传返回绝对 URL（`http://localhost:9000/...`），浏览器直接访问 MinIO，无需 vite 代理转发 |
| 阅读器设置面板 teleport 后背景消失 | 设置面板背景通过 CSS `background: inherit` 无法穿透 teleport，改为 computed `bgStyle` 显式绑定颜色值 |
| 收藏与书架的业务边界 | 新建独立 `t_favorite_book` 表（V5 迁移），`/api/favorites` 与 `/api/bookshelf` 完全分离；书架=阅读进度，收藏=喜爱标记展示在个人主页 |

---

### 三、待优化事项

- MinIO 头像 URL 为 `localhost:9000`，生产环境需配置 nginx 反向代理或 CDN。
- 阅读进度目前写入 `t_bookshelf_book`，但 `updateProgress` 在书不在书架时会抛 `NOT_FOUND`；前端 `catch` 已忽略，但理想方案是自动入架或单独建阅读历史表。
- 书签仅存 `localStorage`，换设备后丢失；如需同步可扩展后端书签表。
- 广场书评目前以书名（字符串）关联，无法检索同名书的所有书评；书源书籍若有稳定唯一标识（bookUrl）可作为关联键。

| 书源 | 搜索 | 章节 | 正文 | 备注 |
|------|------|------|------|------|
| 铅笔小说（23qb.com） | ✅ | ✅ | ❌ | 正文 JS 动态加载 |
| 猫眼看书（jmlldsc.com） | ✅ | — | — | JSONPath + 模板 bookUrl |
| 酷我小说（kuwo.cn） | ✅ | — | — | JSON API 接口 |
| 阅友小说（suixkan.com） | ✅ | — | — | bookUrl 规则含 JS，返回 null |
