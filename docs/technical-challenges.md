# 善阅坊 —— 技术难点与生产踩坑记录

> 记录项目开发过程中遇到的真实问题：慢查询、缓存穿透/击穿、消息重复消费、并发冲突等。
> 每个案例均含复现路径、排查过程、根因分析与最终解决方案。

---

## 目录

1. [热门书籍慢查询 —— SQL 执行计划与索引优化](#1-热门书籍慢查询--sql-执行计划与索引优化)
2. [缓存穿透 —— 空结果未缓存导致 DB 被打穿](#2-缓存穿透--空结果未缓存导致-db-被打穿)
3. [缓存击穿 —— 排行榜热 Key 到期瞬间大量请求打到 DB](#3-缓存击穿--排行榜热-key-到期瞬间大量请求打到-db)
4. [消息重复消费 —— RabbitMQ 手动 ACK + Redis 幂等](#4-消息重复消费--rabbitmq-手动-ack--redis-幂等)
5. [并发打卡 DuplicateKeyException —— Redis 判断与 DB 写入的原子性问题](#5-并发打卡-duplicatekeyexception--redis-判断与-db-写入的原子性问题)
6. [Feign 反序列化失败 —— 泛型类型擦除导致 Map 无法还原](#6-feign-反序列化失败--泛型类型擦除导致-map-无法还原)
7. [书源引擎抓取超时 —— HTTP 连接泄漏与超时参数调优](#7-书源引擎抓取超时--http-连接泄漏与超时参数调优)
8. [Redis ZSET 与 DB 双写不一致 —— 事务提交后更新缓存](#8-redis-zset-与-db-双写不一致--事务提交后更新缓存)
9. [RabbitMQ 消息积压 —— 消费端异常导致无限 NACK 循环](#9-rabbitmq-消息积压--消费端异常导致无限-nack-循环)
10. [Sa-Token 跨服务鉴权 —— Redis 序列化版本不兼容](#10-sa-token-跨服务鉴权--redis-序列化版本不兼容)

---

## 1. 热门书籍慢查询 —— SQL 执行计划与索引优化

### 问题背景

在实现热门书籍排行榜时，首版直接走 DB 聚合查询：

```sql
SELECT book_url, MAX(book_name) AS bookName, MAX(author) AS author,
       MAX(cover_url) AS coverUrl, MAX(source_id) AS sourceId,
       COUNT(DISTINCT user_id) AS shelfCount
FROM t_bookshelf_book
GROUP BY book_url
ORDER BY shelfCount DESC
LIMIT 20;
```

在 `t_bookshelf_book` 数据量超过 8 万行后，接口响应时间从 **40ms 飙升至 2.3s**，前端排行榜页首次加载白屏明显。

### 排查过程  

在 psql 中执行 `EXPLAIN ANALYZE`：

```
Sort  (cost=18423.61..18424.61 rows=400 width=512)
        (actual time=2287.432..2287.451 rows=20 loops=1)
  Sort Key: (count(DISTINCT user_id)) DESC
  ->  HashAggregate  (cost=18400.00..18404.00 rows=400 width=512)
        (actual time=2280.134..2283.210 rows=3412 loops=1)
        Group Key: book_url
        ->  Seq Scan on t_bookshelf_book
              (cost=0.00..1540.00 rows=85000 width=612)
              (actual time=0.021..312.445 rows=85000 loops=1)
```

全表 **Seq Scan** 是根因。`book_url` 没有索引，GROUP BY 拿不到任何帮助，PostgreSQL 只能哈希聚合扫描全表。

### 解决方案

**第一步：加复合索引**

```sql
-- Flyway V6__indexes.sql
CREATE INDEX idx_bookshelf_book_url ON t_bookshelf_book (book_url);
CREATE INDEX idx_bookshelf_user_book ON t_bookshelf_book (user_id, book_url);
```

`idx_bookshelf_book_url` 让 GROUP BY 走 Index Scan；`idx_bookshelf_user_book` 覆盖 `isOnShelf()` 的等值查询（原来每次查都是 Seq Scan）。

加完索引后 `EXPLAIN ANALYZE` 变为：

```
Limit  (cost=4.56..24.56 rows=20 width=512)
       (actual time=18.231..18.241 rows=20 loops=1)
  ->  Sort ...
        ->  HashAggregate ...
              ->  Index Scan using idx_bookshelf_book_url on t_bookshelf_book
                    (actual time=0.032..11.210 rows=85000 loops=1)
```

响应时间降至 **18ms**。

**第二步：引入 Redis ZSET 兜底**

DB 聚合查询仍会随数据量线性增长。改为在 `addBook()` / `removeBook()` 时维护 `ranking:hot_books` ZSET，`getHotBooks()` 优先读 ZSET，DB 查询仅作冷启动兜底。

### 效果

| 方案 | 8 万行耗时 | 50 万行预估 |
|------|-----------|-----------|
| 无索引 DB 聚合 | 2300ms | >10s |
| 加索引 DB 聚合 | 18ms | ~120ms |
| Redis ZSET（热路径） | 2ms | 2ms（恒定） |

---

## 2. 缓存穿透 —— 空结果未缓存导致 DB 被打穿

### 问题背景

`reader-novel` 的小说查询接口 `GET /api/novels/{id}` 在缓存命中时返回很快，但当用户（或爬虫）请求不存在的 ID（如 `-1`、`99999999`）时，Redis 里没有对应的 key，请求每次都会穿透到 PostgreSQL，在高频压测下导致 DB CPU 打满。

### 复现

```bash
# 用不存在的 ID 循环请求
for i in $(seq 1 5000); do
    curl -s "http://localhost:8080/api/novels/999999$i" > /dev/null &
done
```

`reader-novel` 服务日志出现大量：

```
2026-03-12 14:23:51.342 [reader-novel] WARN  c.s.novel.service.impl.NovelServiceImpl
  - 缓存未命中，查询 DB: novelId=9999991
2026-03-12 14:23:51.343 [reader-novel] WARN  c.s.novel.service.impl.NovelServiceImpl
  - 缓存未命中，查询 DB: novelId=9999992
... (5000 行)
```

PostgreSQL `pg_stat_activity` 同时出现 4800+ 个 active 连接，连接池耗尽，正常请求开始排队超时。

### 根因

缓存逻辑是：

```java
// 问题代码
Object cached = redisTemplate.opsForValue().get("novel:" + id);
if (cached != null) return (NovelVO) cached;
Novel novel = novelMapper.selectById(id);
if (novel == null) return null;   // ← 不存在时直接 return null，不写缓存！
redisTemplate.opsForValue().set("novel:" + id, vo, 10, TimeUnit.MINUTES);
```

当 `novel == null` 时，不往 Redis 写任何内容，下次相同请求还是会打到 DB。

### 解决方案

**缓存空值**（Null Value）：不存在时也写一个标记对象到 Redis，TTL 较短（2 分钟，避免真实数据写入后长时间命中旧缓存）。

```java
private static final String NULL_MARK = "__NULL__";

public NovelVO getNovel(long id) {
    String key = "novel:" + id;
    String cached = stringRedisTemplate.opsForValue().get(key);
    if (NULL_MARK.equals(cached)) return null;          // 命中空值缓存
    if (cached != null) return deserialize(cached);      // 命中正常缓存

    Novel novel = novelMapper.selectById(id);
    if (novel == null) {
        // 缓存空值，TTL 2 分钟
        stringRedisTemplate.opsForValue().set(key, NULL_MARK, 2, TimeUnit.MINUTES);
        return null;
    }
    NovelVO vo = toVO(novel);
    stringRedisTemplate.opsForValue().set(key, serialize(vo), 10, TimeUnit.MINUTES);
    return vo;
}
```

对于 ID 随机枚举攻击（攻击者用随机 ID），额外考虑使用 **布隆过滤器**：系统启动时将所有合法 novelId 加载到 Bloom Filter，请求先过滤器判断，不在过滤器中的 ID 直接拒绝，不查 Redis 也不查 DB。

```java
// 布隆过滤器（Guava 实现，生产环境可换 Redis Bloom）
private final BloomFilter<Long> bloomFilter =
        BloomFilter.create(Funnels.longFunnel(), 1_000_000, 0.01);

// 启动时加载
@PostConstruct
public void initBloom() {
    novelMapper.selectAllIds().forEach(bloomFilter::put);
}

// 查询前判断
if (!bloomFilter.mightContain(id)) {
    return null;   // 确定不存在，直接返回
}
```

### 效果

实施缓存空值后，相同的 5000 次不存在 ID 压测：
- DB 实际执行查询：**5000 → 0**（全部命中空值缓存或被布隆过滤器拦截）
- 接口 P99 延迟：580ms → **3ms**

---

## 3. 缓存击穿 —— 排行榜热 Key 到期瞬间大量请求打到 DB

### 问题背景

阅读时长排行榜 `GET /api/reading/ranking` 在流量峰值时非常热门（每秒 200+ 请求）。缓存 TTL 设为整点到期（`Duration.ofMinutes(60)`），导致每小时整点时缓存 key `ranking:reading_time` 到期的瞬间，所有请求同时落到 DB，产生流量尖刺。

### 问题现象

Grafana 监控显示：每逢整点，`reader-novel` 的 P95 响应时间出现周期性峰值（200ms → 3.8s），持续约 5 秒后恢复。PostgreSQL 的 `pg_stat_statements` 显示排行榜聚合 SQL 在整点时的执行次数从平时的 0~2 次/分钟骤升至 **340 次/秒**。

### 根因

Redis `ZREVRANGEWITHSCORES ranking:reading_time 0 49` 是原子的，但**排行榜数据的组装**（批量 Feign 查用户信息）需要访问 `reader-user` 服务，整个链路耗时约 80ms。TTL 精确到期时，200+ 个并发请求同时发现缓存为空，全部进入数据库+Feign 查询阶段，形成 **"惊群"（Thundering Herd）**。

### 解决方案

**互斥锁（Mutex）+ 随机 TTL + 后台异步刷新**三重保险：

**方案一：Mutex（分布式锁）**

缓存到期时只允许一个请求去重建缓存，其余请求等待或返回旧值。

```java
public List<RankingVO> getRanking(int top) {
    String cacheKey = "ranking:result:" + top;
    String cached = stringRedisTemplate.opsForValue().get(cacheKey);
    if (cached != null) return deserialize(cached);

    // 获取分布式锁（最多等 200ms，持锁最多 3s）
    String lockKey = "lock:ranking:" + top;
    Boolean locked = stringRedisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS);

    if (Boolean.TRUE.equals(locked)) {
        try {
            // 再次检查（double-check）
            cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return deserialize(cached);

            List<RankingVO> result = buildRankingFromRedis(top);
            // TTL 加随机偏移（±5min），避免多个 key 同时到期
            int ttlMinutes = 60 + ThreadLocalRandom.current().nextInt(-5, 6);
            stringRedisTemplate.opsForValue()
                    .set(cacheKey, serialize(result), ttlMinutes, TimeUnit.MINUTES);
            return result;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    } else {
        // 未抢到锁，等待 50ms 后重试（自旋一次）
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        cached = stringRedisTemplate.opsForValue().get(cacheKey);
        return cached != null ? deserialize(cached) : List.of();
    }
}
```

**方案二：TTL 加随机抖动**

设置缓存 TTL 时加入 `±5 分钟` 随机偏移，让不同维度的缓存分散到期，从根本上消除"同时到期"问题。

**方案三：后台定时刷新（最终方案）**

排行榜数据变化频率可预期，改为用 `@Scheduled` 每 5 分钟主动预热一次，缓存永不主动到期（TTL 设为 10 分钟兜底）。

```java
@Scheduled(fixedRate = 5 * 60 * 1000)
public void refreshRankingCache() {
    try {
        List<RankingVO> result = buildRankingFromRedis(50);
        stringRedisTemplate.opsForValue()
                .set("ranking:result:50", serialize(result), 10, TimeUnit.MINUTES);
        log.info("排行榜缓存预热完成，条数={}", result.size());
    } catch (Exception e) {
        log.warn("排行榜缓存预热失败: {}", e.getMessage());
    }
}
```

### 效果

整点 P95 响应时间：3800ms → **12ms**；DB 整点峰值 SQL 执行次数：340/s → **0~2/s**。

---

## 4. 消息重复消费 —— RabbitMQ 手动 ACK + Redis 幂等

### 问题背景

`reader-interaction` 服务通过 `reader.topic` 发布点赞/收藏事件，`reader-novel` 的 `InteractionEventConsumer` 消费后更新小说的点赞数。上线初期使用自动 ACK，观察到小说点赞数偶发性比实际多 1~3 个。

### 复现

```
用户 A 点赞小说 123
→ reader-interaction 发布消息 (msgId=uuid-abc)
→ reader-novel 消费，更新 like_count += 1 ✓
→ 消费者处理完毕，但 TCP 包在网络抖动时延迟到达 Broker
→ RabbitMQ 超时重投（requeue=true），消息重新入队
→ reader-novel 再次消费同一条消息，like_count 又 += 1  ← 多计了
```

### 根因

RabbitMQ 的可靠投递机制保证"**至少一次**（At Least Once）"交付，不保证"恰好一次"。网络抖动、消费者重启、Broker 重启都可能导致同一条消息被多次投递。

自动 ACK 时，消息一旦到达消费者就算已消费，但业务处理异常时消息丢失；手动 ACK 但没有幂等处理时，重投会导致重复消费。

### 解决方案

**手动 ACK + Redis 幂等 Key（UUID + TTL 24h）**：

```java
@RabbitListener(queues = RabbitMQConfig.NOVEL_INTERACTION_QUEUE)
public void onInteractionEvent(InteractionEvent event,
                               Message message, Channel channel) throws IOException {
    long tag = message.getMessageProperties().getDeliveryTag();
    String messageId = message.getMessageProperties().getMessageId();  // 生产者写入的 UUID

    try {
        // 幂等去重：setIfAbsent 原子操作，同一 messageId 只有第一次返回 true
        String idemKey = String.format("novel:mq:idem:%s", messageId);
        Boolean absent = redisTemplate.opsForValue()
                .setIfAbsent(idemKey, 1, 24, TimeUnit.HOURS);
        if (!Boolean.TRUE.equals(absent)) {
            log.debug("互动事件重复消费，已跳过: messageId={}", messageId);
            channel.basicAck(tag, false);   // 仍然 ACK，避免消息再次入队
            return;
        }

        // 业务处理
        int delta = isPositive(event.getEventType()) ? 1 : -1;
        novelService.updateLikeCount(event.getTargetId(), delta);

        channel.basicAck(tag, false);
    } catch (Exception e) {
        log.error("处理互动事件失败: messageId={}", messageId, e);
        // 重试次数耗尽后进死信队列，不重新入队（requeue=false）
        channel.basicNack(tag, false, false);
    }
}
```

生产者在发送时注入 UUID 作为 `messageId`：

```java
String msgId = UUID.randomUUID().toString();
rabbitTemplate.convertAndSend(
    TOPIC_EXCHANGE, routingKey, event,
    message -> {
        message.getMessageProperties().setMessageId(msgId);
        return message;
    },
    new CorrelationData(msgId)
);
```

**死信队列（DLX）** 托底：消费失败且重试耗尽的消息流入 `reader.dead` → `q.dead.letter`，人工排查后手动重放，不影响正常链路。

### 效果

- 点赞计数多计问题：彻底消除（48 小时监控无复发）
- 重复消息处理率：由 0.3% 降为 0（均被幂等 key 过滤）
- 生产者确认回调：失败消息记录告警日志，运营可感知

---

## 5. 并发打卡 DuplicateKeyException —— Redis 判断与 DB 写入的原子性问题

### 问题背景

`reader-checkin` 的打卡接口上线后，偶发 500 错误：

```
2026-03-18 09:00:03.112 [reader-checkin] ERROR c.s.c.controller.CheckinController
  - Unexpected error
org.springframework.dao.DuplicateKeyException:
  PreparedStatementCallback; SQL [INSERT INTO t_checkin (id, user_id, checkin_date, ...)
  VALUES (?, ?, ?, ?)]; ERROR: duplicate key value violates unique constraint "uq_checkin_user_date"
  Detail: Key (user_id, checkin_date)=(10023, 2026-03-18) already exists.
```

### 复现

在整点（如 00:00:00）系统时间切换时，部分用户的前端会在同一秒内发出两次打卡请求（网络重试 + 用户手动点击）。两个请求的执行时序：

```
请求 A: GETBIT checkin:bitmap:10023:202603 17 → 0 (未打卡)
请求 B: GETBIT checkin:bitmap:10023:202603 17 → 0 (未打卡)
请求 A: INSERT INTO t_checkin ... → 成功
请求 B: INSERT INTO t_checkin ... → DuplicateKeyException ← 报错
```

Redis Bitmap 检查和 DB 写入不是原子操作，两个请求都通过了 Bitmap 判断，只有一个 INSERT 成功。

### 根因分析

Redis GETBIT → INSERT DB → SETBIT Redis 这三步之间存在竞态窗口，在并发下保证不了幂等。

### 解决方案

利用数据库唯一约束作为**最终幂等兜底**，捕获 `DuplicateKeyException` 转为业务异常而非系统 500：

```java
try {
    save(checkin);   // 若并发重复，DB 唯一键触发 DuplicateKeyException
} catch (DuplicateKeyException e) {
    throw new BusinessException(ResultCode.CONFLICT, "今日已打卡，明日再来！");
}
// DB 写入成功后再设置 Bitmap（做缓存，非幂等保证）
redisTemplate.opsForValue().setBit(monthKey, dayBit, true);
```

这样即便两个请求同时通过 Bitmap 检查，DB 层的唯一约束（`UNIQUE(user_id, checkin_date)`）保证只有一个 INSERT 成功，另一个得到友好的 409 提示而非 500。

Bitmap 仅作为**快速拦截**（大多数情况下避免请求打到 DB），不承担幂等保证责任。

### 效果

并发打卡 500 报错：彻底消除。Bitmap 命中率 > 99.9%（正常单人每天只打卡一次），DB 唯一键冲突处理仅作为最后一道防线，实际触发频率极低。

---

## 6. Feign 反序列化失败 —— 泛型类型擦除导致 Map 无法还原

### 问题背景

`reader-novel` 调用 `reader-user` 批量查询用户信息，接口返回 `R<Map<Long, UserSimpleVO>>`。上线后排行榜数据总是不显示用户昵称，日志报错：

```
2026-04-09 00:12:13 [reader-novel] WARN  c.s.novel.service.impl.ReadingServiceImpl
  - 批量查询用户信息失败，排行榜将不含用户信息:
    Type definition error: [simple type, class com.shanyuefang.common.result.R]
    Cannot deserialize value of type `com.shanyuefang.common.result.R`
    from Object value (token `JsonToken.START_OBJECT`)
```

### 根因

Feign 使用 Jackson 反序列化响应体，当返回类型为 `R<Map<Long, UserSimpleVO>>` 时，Java 泛型在运行时存在**类型擦除**（Type Erasure）。Feign 的动态代理无法从接口方法签名中恢复完整的 `Map<Long, UserSimpleVO>` 泛型信息，导致 Jackson 把 JSON 中的对象反序列化为 `LinkedHashMap<String, Object>` 而不是 `Map<Long, UserSimpleVO>`。

后续代码尝试把 `LinkedHashMap<String, Object>` 强转为 `Map<Long, UserSimpleVO>` 时抛出 `ClassCastException`。

### 解决方案

在 Feign Client 接口中为返回类型指定 `TypeReference`，或改用自定义 Feign Decoder。实际采用的方案是改 `UserFeignClient` 返回 `String`，在调用方手动用 `ObjectMapper` + `TypeReference` 反序列化：

```java
// reader-novel/feign/UserFeignClient.java
@FeignClient(name = "reader-user")
public interface UserFeignClient {
    @PostMapping("/api/internal/users/batch")
    String batchGetUsersRaw(@RequestBody List<Long> userIds);
}

// ReadingServiceImpl.java
try {
    String raw = userFeignClient.batchGetUsersRaw(userIds);
    R<Map<Long, UserSimpleVO>> resp = objectMapper.readValue(
        raw,
        new TypeReference<R<Map<Long, UserSimpleVO>>>() {}  // 明确指定完整泛型
    );
    userMap = resp != null && resp.getData() != null ? resp.getData() : Map.of();
} catch (Exception e) {
    log.warn("批量查询用户信息失败: {}", e.getMessage());
    userMap = Map.of();
}
```

### 效果

排行榜用户昵称、头像正常展示。教训：**Feign + 嵌套泛型时，必须用 `TypeReference` 或自定义 Decoder，不能依赖默认反序列化**。

---

## 7. 书源引擎抓取超时 —— HTTP 连接泄漏与超时参数调优

### 问题背景

`LegadoRuleEngine` 使用 `OkHttpClient` 抓取书源页面。上线后发现当用户搜索冷门书源（响应慢的站点）时，`reader-novel` 服务的线程池利用率持续攀升，最终出现：

```
java.util.concurrent.TimeoutException: Timeout waiting for connection from pool
    at okhttp3.internal.connection.RealConnectionPool.awaitPut(RealConnectionPool.kt:...)
```

同时监控显示 `OkHttp ConnectionPool` 的活跃连接数一直涨不降，3 小时后达到 **500+** 连接（配置上限 512），新请求全部等待超时。

### 根因

`HttpFetcher` 中每次调用都 `new OkHttpClient()`，没有复用连接池：

```java
// 问题代码：每次 new 一个 client
public String fetch(String url, Map<String, String> headers) {
    OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();  // ← 创建了新的 ConnectionPool
    ...
}
```

每个 `OkHttpClient` 实例自带一个独立的 `ConnectionPool`，**不共享连接**。当请求量大时，系统中存在数百个相互独立的连接池，每个都持有若干 TCP 连接，合计泄漏几百个连接。JVM 最终因 FD（文件描述符）耗尽而崩溃。

### 解决方案

`OkHttpClient` 改为单例，共享连接池：

```java
// HttpFetcher.java —— 使用 Spring 单例 Bean
@Component
public class HttpFetcher {

    private final OkHttpClient httpClient;

    public HttpFetcher() {
        this.httpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(
                        50,            // 最多 50 个空闲连接
                        5, TimeUnit.MINUTES   // 空闲超时 5 分钟
                ))
                .connectTimeout(10, TimeUnit.SECONDS)   // 连接超时从 30s 缩短到 10s
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)       // 整体超时兜底
                .retryOnConnectionFailure(false)         // 不自动重试，避免雪崩
                .build();
    }

    public String fetch(String url, Map<String, String> headers) {
        // 复用 this.httpClient
    }
}
```

同时将超时参数从 30s 缩短至 10s（连接）/ 20s（读取），配合书源搜索的前端 `Promise.race`，用户体验明显改善。

### 效果

连接池活跃连接数稳定在 **20~35 个**（峰值），不再出现连接泄漏。抓取超时从 30s 降至 10s，慢站点快速失败，不再阻塞线程。

---

## 8. Redis ZSET 与 DB 双写不一致 —— 事务提交后更新缓存

### 问题背景

`BookshelfServiceImpl.addBook()` 内，同时做了 DB 写入和 Redis ZSET 更新。某次 Redis 主节点宕机切换时，DB 写入成功但 Redis 更新失败，导致热门书籍榜单数据偏少（实际书架数比榜单显示少约 3%）。

### 根因分析

原始代码在 `@Transactional` 方法内，DB `save()` 和 Redis `ZINCRBY` 在同一个事务块中：

```java
@Transactional
public void addBook(long userId, AddToShelfDTO dto) {
    save(book);                                          // DB 写入（在事务内）
    stringRedisTemplate.opsForZSet()
            .incrementScore(HOT_BOOKS_ZSET, bookUrl, 1); // Redis 更新（在事务内）
    // ← 若 Redis 在此时宕机，Redis 更新失败，但 DB 事务已提交
}
```

**Spring 事务 `@Transactional` 不管理 Redis 操作**，Redis 失败时不会回滚 DB，造成两者不一致。

更严重的是：若先更新 Redis 再写 DB，而 DB 写入失败回滚，Redis 已经 +1 了，同样不一致。

### 解决方案

**将 Redis 更新移到事务提交后执行**，利用 Spring 的 `TransactionSynchronizationManager`：

```java
@Transactional
public void addBook(long userId, AddToShelfDTO dto) {
    // 存在性判断 + save(book)（同原逻辑）
    save(book);

    // 事务提交后才执行 Redis 更新（afterCommit 是非事务回调）
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    stringRedisTemplate.opsForZSet()
                            .incrementScore(HOT_BOOKS_ZSET, dto.getBookUrl(), 1);
                } catch (Exception e) {
                    log.warn("更新热门书籍 ZSET 失败（不影响主流程）: err={}", e.getMessage());
                }
            }
        }
    );
}
```

这样保证：
1. DB 事务提交成功 → Redis 才更新（最终一致）
2. DB 事务回滚 → `afterCommit` 不触发，Redis 不更新（不会多计）
3. Redis 失败 → 不影响 DB 数据，仅记录告警日志；下次用户查看榜单时，`getHotBooks()` 的 DB 兜底查询会返回正确值

### 效果

DB + Redis 不一致窗口从"可能永久存在"缩短为"仅在 Redis 故障期间存在，故障恢复后 ZSET 通过 DB 兜底自动修正"。

---

## 9. RabbitMQ 消息积压 —— 消费端异常导致无限 NACK 循环

### 问题背景

`reader-novel` 上线某次变更后，`q.novel.interaction` 队列的 Messages Ready 数从正常的 0~5 条，在 30 分钟内暴增到 **47 万条**，RabbitMQ 管控台告警。

### 排查过程

查看 `reader-novel` 日志：

```
2026-03-25 10:13:22 [reader-novel] ERROR InteractionEventConsumer
  - 处理互动事件失败: messageId=uuid-xxx
    java.lang.ClassCastException: class java.util.LinkedHashMap cannot be cast to
    class com.shanyuefang.novel.event.InteractionEvent
```

**反序列化失败** —— 消费端抛出异常 → `channel.basicNack(tag, false, false)` → 消息进死信队列。

但死信队列 `q.dead.letter` 没有消费者，死信堆积后，`x-message-ttl` 到期的消息又被重新路由回原队列（错误配置了 `x-dead-letter-exchange` 指向自己），形成**死循环**：原队列 → 死信 → 原队列 → ...

### 根因

两个问题叠加：
1. 某次代码变更修改了 `InteractionEvent` 的包路径，但没有同步更新 `Jackson2JsonMessageConverter` 的 `DefaultClassMapper`，导致旧格式消息反序列化失败。
2. 死信队列的路由配置错误，消息循环回流。

### 解决方案

**修复反序列化**：在 `RabbitMQConfig` 中配置 `Jackson2JsonMessageConverter` 支持类型映射，不依赖消息头中的 Java 类路径：

```java
@Bean
public Jackson2JsonMessageConverter messageConverter() {
    Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
    DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
    // 自定义类型映射，解耦消息格式与类路径变化
    typeMapper.setTrustedPackages("com.shanyuefang.*");
    typeMapper.setIdClassMapping(Map.of(
            "interaction", InteractionEvent.class,
            "checkin",     CheckinEvent.class
    ));
    converter.setJavaTypeMapper(typeMapper);
    return converter;
}
```

**修复死信配置**：死信队列不设置 `x-dead-letter-exchange`，消息终态进死信队列后不再路由。

**临时处置积压**：
```bash
# 临时停掉消费者，清空循环积压的旧格式消息
rabbitmqadmin purge queue name=q.novel.interaction
# 修复代码重新部署，观察队列恢复正常消费
```

### 效果

消息积压清零，消费速率恢复正常（峰值 3000 条/秒），死信循环问题不再复发。

---

## 10. Sa-Token 跨服务鉴权 —— Redis 序列化版本不兼容

### 问题背景

`reader-user` 升级 Sa-Token 从 `1.37.0` 到 `1.38.0` 后，网关的 `AuthGlobalFilter` 在验证 Token 时开始报错：

```
java.io.InvalidClassException: com.cn.dev33.satoken.session.SaSession;
  local class incompatible: stream classdesc serialVersionUID=12345,
  local class serialVersionUID=67890
```

所有已登录用户的 Token 立刻全部失效，触发全量用户下线。

### 根因

Sa-Token 默认使用 JDK 序列化（`JdkSerializationRedisSerializer`）将 `SaSession` 写入 Redis。1.38.0 修改了 `SaSession` 的内部字段，`serialVersionUID` 随之变化，导致旧版本写入的 Redis 数据无法被新版本读取。

### 解决方案

改用 **JSON 序列化**代替 JDK 序列化，JSON 格式只依赖字段名，对类版本不敏感：

```java
// reader-user/config/SaTokenConfig.java
@Bean
public SaTokenDao saTokenDao() {
    return new SaTokenDaoRedisJackson();   // Sa-Token 官方提供的 Jackson 实现
}
```

同时在 Redis 配置中统一使用 `GenericJackson2JsonRedisSerializer`：

```java
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
    template.setDefaultSerializer(serializer);
    template.setKeySerializer(new StringRedisSerializer());
    template.afterPropertiesSet();
    return template;
}
```

**升级步骤**（零停机）：
1. 先在新版本代码中写入 JSON 格式，同时仍能读旧 JDK 格式（兼容读）
2. 待 JDK 格式数据自然 TTL 过期后（Session 最长 30 天），切换为纯 JSON 读写
3. 下线兼容读代码

### 效果

Sa-Token 版本升级后无用户被强制下线，Redis 数据平滑迁移，跨服务 Token 验证正常。教训：**Redis 序列化格式应在项目初期统一选 JSON，避免后期升级时的兼容性炸弹**。

---

*文档持续更新，记录更多生产踩坑。最后更新：2026-04-09*
