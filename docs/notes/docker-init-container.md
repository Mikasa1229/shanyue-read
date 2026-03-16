# Docker Init Container 模式：Nacos 配置自动初始化

> 本文记录如何使用 docker-compose 的"一次性 init 容器"模式，在 Nacos 启动后自动写入配置，彻底消除手动操作 Nacos 控制台的需要。

---

## 目录

1. [背景：为什么需要自动初始化？](#1-背景为什么需要自动初始化)
2. [依赖顺序问题与 condition: service_healthy](#2-依赖顺序问题与-condition-service_healthy)
3. [Shell 脚本：curl --data-urlencode 处理多行 YAML](#3-shell-脚本curl---data-urlencode-处理多行-yaml)
4. [一次性容器：restart: "no"](#4-一次性容器restart-no)
5. [完整配置说明](#5-完整配置说明)
6. [与手动初始化对比](#6-与手动初始化对比)

---

## 1. 背景：为什么需要自动初始化？

Nacos 作为配置中心，服务启动时会从 Nacos 拉取配置。如果 Nacos 里没有对应的配置文件，服务会启动失败（或使用本地默认值，但缺少动态配置能力）。

传统做法：每次搭建开发环境，都需要：
1. 打开浏览器，访问 `http://localhost:8848/nacos`
2. 登录（nacos/nacos）
3. 手动在"配置管理"中新建 6 个 YAML 文件
4. 逐个粘贴内容、选择分组、保存

这个流程**繁琐且容易出错**，而且每次重建 Docker 环境都要重复一次（因为 Nacos 的配置存在 MySQL 中，如果清理了 volume 就会消失）。

---

## 2. 依赖顺序问题与 condition: service_healthy

### 朴素的 depends_on 不够用

```yaml
# 错误做法：只保证容器启动，不保证服务就绪
init-nacos:
  depends_on:
    - nacos
```

`depends_on` 默认只等待容器**进入 running 状态**，不等待容器内的服务**实际就绪**。Nacos 从 JVM 启动到 HTTP 接口可用需要约 10–30 秒，如果 init 脚本在 Nacos 还没准备好时就发请求，会得到连接拒绝。

### condition: service_healthy

```yaml
init-nacos:
  depends_on:
    nacos:
      condition: service_healthy  # 等待健康检查通过
```

这要求 Nacos 服务配置了 `healthcheck`：

```yaml
nacos:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/actuator/health"]
    interval: 15s
    timeout: 10s
    retries: 10
```

docker-compose 会每隔 15 秒执行一次健康检查命令，直到命令返回 0（成功）才将容器状态设为 `healthy`。`init-nacos` 服务会**等到 Nacos 变为 healthy 状态**后才启动，确保 HTTP 请求不会失败。

### 健康检查的传递性

```
nacos-mysql (healthy) → nacos (healthy) → init-nacos (starts)
```

Nacos 本身也依赖 MySQL，所以形成了三级等待链，完全由 docker-compose 的 condition 机制自动管理。

---

## 3. Shell 脚本：curl --data-urlencode 处理多行 YAML

### 问题：YAML 内容包含特殊字符

Nacos REST API 接受 `application/x-www-form-urlencoded` 格式的 POST 请求。YAML 内容中包含大量需要 URL 编码的字符：

| 字符 | URL 编码 | 出现原因 |
|------|---------|---------|
| `\n` | `%0A` | YAML 换行 |
| `:` | `%3A` | YAML 键值对分隔符 |
| `#` | `%23` | YAML 注释 |
| 空格 | `+` | 缩进 |

手动拼接 URL 编码极其麻烦，而且容易出错。

### curl --data-urlencode 自动编码

`curl` 的 `--data-urlencode` 选项会自动对参数值进行 URL 编码：

```sh
curl -X POST "http://nacos:8848/nacos/v1/cs/configs" \
  --data-urlencode "dataId=reader-user.yaml" \
  --data-urlencode "group=reader" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content=logging:
  level:
    com.shanyuefang: debug"
```

`content` 的值（多行 YAML）会被 curl 自动编码为正确的 URL 格式，无需手动处理。

### 函数封装

```sh
put_config() {
  DATA_ID="$1"
  CONTENT="$2"
  result=$(curl -sf -X POST "$NACOS_URL" \
    --data-urlencode "dataId=$DATA_ID" \
    --data-urlencode "group=$GROUP"    \
    --data-urlencode "type=yaml"       \
    --data-urlencode "content=$CONTENT")
  if [ "$result" = "true" ]; then
    echo "  [OK] $DATA_ID"
  else
    echo "  [WARN] $DATA_ID -> $result"
  fi
}
```

`-s`（silent）去掉进度条，`-f`（fail on HTTP error）让非 2xx 响应也触发错误退出。Nacos API 成功时返回字符串 `"true"`，失败时返回错误信息，通过字符串比较判断结果。

### Shell Here-String 传递多行内容

```sh
put_config "reader-user.yaml" \
"# ===== reader-user =====
logging:
  level:
    com.shanyuefang: debug"
```

Shell 的引号包裹保留换行符，`put_config` 收到的 `$2` 是真实的多行字符串，再传给 `--data-urlencode`，编码正确。

---

## 4. 一次性容器：restart: "no"

```yaml
init-nacos:
  restart: "no"
```

docker-compose 的默认重启策略是 `no`，但显式写出来更清晰：init 容器完成工作后**直接退出**，不会被重启。

Nacos 配置已经持久化到 MySQL，重建 Nacos 容器后数据不会丢失，因此 init 容器**只需要在 MySQL volume 被清空时重新运行**即可（每次 `docker compose down -v && docker compose up` 会触发）。

---

## 5. 完整配置说明

```yaml
# docker-compose.yml
init-nacos:
  image: alpine:3.19                    # 轻量镜像，只需要 curl
  container_name: reader-nacos-init
  depends_on:
    nacos:
      condition: service_healthy        # 等 Nacos 健康检查通过
  volumes:
    - ./nacos-init.sh:/nacos-init.sh:ro # 只读挂载脚本
  command: ["sh", "-c", "apk add -q --no-cache curl && sh /nacos-init.sh"]
  restart: "no"                         # 完成后退出，不重启
```

`command` 分两步：
1. `apk add -q --no-cache curl`：安装 curl（alpine 默认不含）
2. `sh /nacos-init.sh`：执行初始化脚本

选择 `alpine:3.19` 而非 `ubuntu` 或 `debian` 是因为 alpine 镜像极小（~5MB），`apk add curl` 快速。`--no-cache` 避免写入 apk 缓存文件，进一步减小容器层大小。

---

## 6. 与手动初始化对比

| 方式 | 优点 | 缺点 |
|------|------|------|
| 手动 Nacos 控制台 | 直观，可即时看到效果 | 每次重建都要重复；容易漏掉配置项 |
| PowerShell 脚本手动调用 | 一键完成；可在 CI 复用 | 需要记得在每次重建后执行；依赖本地 PS 环境 |
| docker-compose init 容器 | **完全自动**；与基础设施生命周期绑定；跨平台 | 需要 Docker 网络内的服务名（`nacos:8848`）而非 localhost |

**最终选择 init 容器**，原因：
- 和 `docker compose up -d` 完全绑定，一条命令完成所有基础设施初始化
- 跨平台（Linux/Mac/Windows 都只需 Docker）
- 脚本是 Shell，不依赖宿主机的 PowerShell / Python 环境
- 幂等：如果配置已存在，Nacos 会更新（`PUT` 语义），不会报错

### 补充：nacos-init.ps1 保留的意义

`scripts/nacos-init.ps1` 作为**独立工具**保留，用于：
- Nacos 配置被意外修改后，快速重置到初始状态
- 调试时单独测试配置内容是否正确

它不再被 `start-middleware.ps1` 自动调用，避免重复写入。
