# 单元测试：MyBatis-Plus ServiceImpl 的测试模式

> 本文记录在不启动 Spring 容器的前提下，对 `ServiceImpl<M, T>` 进行纯单元测试所遇到的问题与最终解决方案。

---

## 目录

1. [为什么不用 Spring Boot Test？](#1-为什么不用-spring-boot-test)
2. [问题一：insert / updateById 的重载歧义](#2-问题一insert--updatebyid-的重载歧义)
3. [问题二：lambdaQuery() 抛 MybatisPlusException](#3-问题二lambdaquery-抛-mybatisplusexception)
4. [完整测试模板](#4-完整测试模板)
5. [WebFlux 过滤器测试：Sa-Token 静态方法的 Mock](#5-webflux-过滤器测试sa-token-静态方法的-mock)

---

## 1. 为什么不用 Spring Boot Test？

`@SpringBootTest` 会启动完整的 Spring 应用上下文：加载所有 Bean、连接数据库、连接 Redis……

对于**业务逻辑单元测试**，这有几个问题：

| 问题 | 影响 |
|------|------|
| 启动慢（数秒到数分钟） | 反馈周期太长，阻断 TDD 节奏 |
| 依赖外部基础设施 | 本地没开 Docker 时测试无法运行 |
| 测试范围过大 | 一旦失败难以定位是业务逻辑还是配置/网络问题 |

使用 `@ExtendWith(MockitoExtension.class)` 配合 Mockito，可以在**毫秒级**完成纯逻辑测试，且无任何外部依赖。

---

## 2. 问题一：insert / updateById 的重载歧义

### 问题背景

MyBatis-Plus 3.5.7 给 `BaseMapper` 新增了批量方法：

```java
// 已有（3.5.7 之前）
int insert(T entity);
int updateById(@Param("et") T entity);

// 3.5.7 新增
int insert(@Param("list") Collection<T> entityList);
int updateById(@Param("list") Collection<T> entityList);
```

### Mockito 的报错

当两个重载都存在时，Mockito 的泛型类型擦除参数匹配器会引发歧义：

```java
// 编译 / 运行时报错：
// ambiguous method call: both insert(T) and insert(Collection<T>) match
when(userMapper.insert(any())).thenReturn(1);
```

`any()` 匹配 `Object`，而 `T` 和 `Collection<T>` 都可以视为 `Object`，Mockito 无法确定要 stub 哪个重载。

### 解决方案

使用**带类型参数**的匹配器，明确告诉 Mockito 要匹配哪个重载：

```java
// 正确写法
when(userMapper.insert(any(User.class))).thenReturn(1);
when(userMapper.updateById(any(User.class))).thenReturn(1);

// 或者带条件断言
when(novelMapper.insert(argThat((Novel n) -> n.getTitle().equals("测试小说")))).thenReturn(1);
```

**原理**：`any(User.class)` 在字节码层面等价于 `isA(User.class)`，它明确要求参数类型为 `User`，而 `Collection<User>` 不是 `User`，Mockito 从而可以唯一确定目标重载。

---

## 3. 问题二：lambdaQuery() 抛 MybatisPlusException

### 问题背景

`ServiceImpl.lambdaQuery()` 是 MBP 提供的链式查询 API，用法如下：

```java
Novel novel = lambdaQuery()
    .eq(Novel::getId, id)
    .one();
```

在单元测试中，即便已经通过 `@InjectMocks` 或手动 `new` 出 Service，注入了 Mapper mock，调用 `lambdaQuery()` 仍然会抛出：

```
MybatisPlusException: Unable to get MybatisMapperProxy!
```

### 根本原因：调用链追踪

```
ServiceImpl.lambdaQuery()
  └─ getEntityClass()              // 获取泛型 T 的实际类型
       └─ getMapperClass()         // 通过 Mapper 类型反射泛型
            └─ MybatisUtils.getMybatisMapperProxy(baseMapper)
                 └─ 尝试从 Mapper 的 MyBatis 代理对象中提取信息
                      └─ 失败：Mockito mock 不是 MyBatis 的 MapperProxy，没有 `h` 字段
```

关键在于 `MybatisUtils.getMybatisMapperProxy()` 会通过 Java 反射获取动态代理对象的 `InvocationHandler`，而 Mockito 生成的 mock 对象的 `InvocationHandler` 是 Mockito 自己的，不包含 MyBatis 需要的元数据。

### 解决方案

直接设置 `ServiceImpl` 的 `entityClass` 字段，绕过整个反射链：

```java
@BeforeEach
void setUp() {
    novelService = new NovelServiceImpl(redisTemplate);
    ReflectionTestUtils.setField(novelService, "baseMapper", novelMapper);
    // 关键：短路掉 MybatisUtils.getMybatisMapperProxy() 的调用
    ReflectionTestUtils.setField(novelService, "entityClass", Novel.class);
}
```

**原理**：`ServiceImpl` 继承自 `AbstractService`，其中有一个懒加载字段 `entityClass`：

```java
// MBP 源码（简化）
protected Class<T> entityClass;

protected Class<T> getEntityClass() {
    if (entityClass == null) {
        // 从 Mapper 反射推导 → 这里会炸
        this.entityClass = ReflectHelper.getSuperClassGenericType(...);
    }
    return entityClass;
}
```

用 `ReflectionTestUtils.setField` 直接给 `entityClass` 赋值后，`getEntityClass()` 的 null 检查通过，不再走反射分支，MyBatis 代理查找也就不会被触发。

---

## 4. 完整测试模板

以下是适用于所有 MBP ServiceImpl 的标准测试模板：

```java
@ExtendWith(MockitoExtension.class)
class NovelServiceTest {

    @Mock
    private NovelMapper novelMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private NovelServiceImpl novelService;

    @BeforeEach
    void setUp() {
        // 1. 手动构造，传入需要的构造参数
        novelService = new NovelServiceImpl(redisTemplate);

        // 2. 注入 Mapper mock（绕过 @Autowired）
        ReflectionTestUtils.setField(novelService, "baseMapper", novelMapper);

        // 3. 设置 entityClass（绕过 MybatisMapperProxy 反射链）
        ReflectionTestUtils.setField(novelService, "entityClass", Novel.class);

        // 4. 为 Redis 的链式调用设置 lenient stub
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void createNovel_success() {
        // Arrange
        when(novelMapper.insert(any(Novel.class))).thenReturn(1);
        CreateNovelDTO dto = new CreateNovelDTO();
        dto.setTitle("测试小说");
        dto.setAuthorId(1L);

        // Act
        novelService.createNovel(dto);

        // Assert
        verify(novelMapper).insert(argThat((Novel n) ->
            "测试小说".equals(n.getTitle()) && Long.valueOf(1L).equals(n.getAuthorId())
        ));
    }

    @Test
    void getDetail_cacheHit() {
        // Arrange：Redis 缓存命中，不查 DB
        when(valueOps.get("novel:1")).thenReturn("{\"id\":1,\"title\":\"缓存小说\"}");

        // Act
        NovelVO vo = novelService.getDetail(1L);

        // Assert：验证命中缓存，没有查询数据库
        assertEquals("缓存小说", vo.getTitle());
        verify(novelMapper, never()).selectById(any());
    }
}
```

### 关键约定

| 场景 | 写法 |
|------|------|
| Mock 单条实体方法 | `any(Entity.class)` |
| Mock 带条件判断 | `argThat((Entity e) -> ...)` |
| 验证方法未被调用 | `verify(mapper, never()).method(...)` |
| 绕过 lambdaQuery | `setField(service, "entityClass", Entity.class)` |
| Redis 链式调用 | `lenient().when(template.opsForValue()).thenReturn(ops)` |

---

## 5. WebFlux 过滤器测试：Sa-Token 静态方法的 Mock

Gateway 使用 Spring WebFlux，过滤器返回 `Mono<Void>`，测试需要额外处理。

### 依赖

```xml
<!-- reactor-test 提供 StepVerifier -->
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 构造 WebFlux 请求上下文

```java
// 构造 POST /api/novels 请求
MockServerHttpRequest request = MockServerHttpRequest
    .post("/api/novels")
    .header("Authorization", "Bearer " + token)
    .build();
ServerWebExchange exchange = MockServerWebExchange.from(request);
```

### Mock Sa-Token 静态方法

Sa-Token 使用大量静态方法（`StpUtil.checkLogin()`、`StpUtil.getLoginIdAsLong()` 等），Mockito 5 的 `MockedStatic` 可以拦截：

```java
@Test
void validToken_injectsUserId() {
    try (MockedStatic<SaReactorSyncHolder> holderMock = mockStatic(SaReactorSyncHolder.class);
         MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {

        // 模拟 Sa-Token 验证通过，返回用户 ID
        stpMock.when(StpUtil::checkLogin).thenAnswer(inv -> null);  // 不抛异常 = 验证通过
        stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(42L);

        // 执行过滤器
        GatewayFilterChain chain = exchange -> Mono.empty();
        Mono<Void> result = filter.filter(exchange, chain);

        // 使用 StepVerifier 订阅响应式流，验证无异常完成
        StepVerifier.create(result)
            .verifyComplete();

        // 验证下游请求携带了 X-User-Id 头
        assertEquals("42",
            exchange.getRequest().getHeaders().getFirst("X-User-Id"));
    }
}
```

### 验证 401 响应

```java
@Test
void noToken_returns401() {
    MockServerHttpRequest request = MockServerHttpRequest.post("/api/novels").build();
    ServerWebExchange exchange = MockServerWebExchange.from(request);

    Mono<Void> result = filter.filter(exchange, mock(GatewayFilterChain.class));
    StepVerifier.create(result).verifyComplete();  // filter 写完响应后 complete

    assertEquals(HttpStatus.UNAUTHORIZED,
        exchange.getResponse().getStatusCode());
}
```

**要点**：WebFlux 的响应式过滤器在拦截请求时，通过 `exchange.getResponse().setStatusCode()` 和 `setComplete()` 来短路请求，不调用 `chain.filter()`。`StepVerifier.create(result).verifyComplete()` 验证整个 Mono 正常结束（无异常），然后再断言 response 的状态码。
