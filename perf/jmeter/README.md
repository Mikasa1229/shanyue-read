# JMeter 容器化压测

本目录提供固定版本 Apache JMeter 5.6.3 的 Docker CLI 运行方式，适合单实例 Demo 的接口压测。镜像在构建时从 Apache 下载 JMeter，测试计划和结果目录通过卷挂载。

## 构建并运行

先确保 Reader 网关在宿主机 `8080` 端口运行：

```powershell
docker compose -f perf/jmeter/docker-compose.yml build
docker compose -f perf/jmeter/docker-compose.yml run --rm `
  -e JVM_ARGS="-Xms512m -Xmx512m" `
  jmeter
```

容器通过 `host.docker.internal` 访问宿主机服务，结果写入 `logs/jmeter/`。

## 单档测试

```powershell
docker compose -f perf/jmeter/docker-compose.yml run --rm `
  -e JVM_ARGS="-Xms512m -Xmx512m" `
  jmeter -n -t /tests/reader-api-step-load.jmx `
  -JBASE_HOST=host.docker.internal -JBASE_PORT=8080 `
  -JTHREADS=100 -JRAMP_SECONDS=10 -JDURATION=120 `
  -l /tests/output/reader-api-100.jtl `
  -e -o /tests/output/report-100
```

## 接口矩阵

`reader-api-endpoints.csv` 当前覆盖：

- 小说列表第一页和第二页；
- 阅读排行榜；
- 书源分页列表；
- 书源关键词搜索（剑来）；
- 不存在小说详情（HTTP 200，业务响应码为 404）；
- 未登录鉴权（预期 401）；
- 未登录热门书架（预期 401）；
- 未登录 Agent 基础设施信息（预期 401）。

CSV 的第二列是期望 HTTP 状态码，JMeter 响应断言会将 `401` 和业务错误包装后的 `200` 按预期计为成功。不存在小说详情的业务码需要额外用 JSON 断言校验。

## 注意事项

- 该计划是只读/未登录矩阵，不包含注册、登录、收藏、阅读进度、积分、模型调用和图谱重建；
- Agent SSE 应使用测试用户和 Mock 模型单独测试，不能把真实 DeepSeek 作为极限压测依赖；
- 每次正式测试只改变一个变量，例如并发数或持续时间；
- 结果应该结合 JVM、Docker、PostgreSQL、Redis 和 Hikari 指标分析。
