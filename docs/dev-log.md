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
