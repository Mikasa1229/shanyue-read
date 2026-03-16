#!/bin/sh
# Nacos 配置初始化脚本
# 由 docker-compose 的 init-nacos 服务在 Nacos 健康检查通过后自动执行。
# 使用 curl --data-urlencode 写入多行 YAML，无需手动 URL 编码。

set -e

NACOS_URL="http://nacos:8848/nacos/v1/cs/configs"
GROUP="reader"

# 写入单条配置，结果为 "true" 表示成功
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

echo ""
echo "=== Nacos config init (group: $GROUP) ==="

# ── reader-gateway ──────────────────────────────────────────
put_config "reader-gateway.yaml" \
"# ===== reader-gateway 集中配置 =====
# 可在此覆盖路由、限流、跨域等网关配置，无需重新打包
logging:
  level:
    com.shanyuefang: debug
    org.springframework.cloud.gateway: info"

# ── reader-user ─────────────────────────────────────────────
put_config "reader-user.yaml" \
"# ===== reader-user 集中配置 =====
# 可在此覆盖数据库、缓存TTL、Sa-Token等配置，无需重新打包
# 示例（按需取消注释）：
# spring:
#   datasource:
#     url: jdbc:postgresql://prod-host:5432/db_user
logging:
  level:
    com.shanyuefang: debug"

# ── reader-novel ─────────────────────────────────────────────
put_config "reader-novel.yaml" \
"# ===== reader-novel 集中配置 =====
# 可在此覆盖小说服务的数据库、缓存、MQ 等配置
logging:
  level:
    com.shanyuefang: debug"

# ── reader-comment ───────────────────────────────────────────
put_config "reader-comment.yaml" \
"# ===== reader-comment 集中配置 =====
# 可在此覆盖点评服务的数据库、Feign 超时等配置
logging:
  level:
    com.shanyuefang: debug"

# ── reader-interaction ───────────────────────────────────────
put_config "reader-interaction.yaml" \
"# ===== reader-interaction 集中配置 =====
# 可在此覆盖互动服务的数据库、缓存TTL等配置
logging:
  level:
    com.shanyuefang: debug"

# ── reader-checkin ───────────────────────────────────────────
put_config "reader-checkin.yaml" \
"# ===== reader-checkin 集中配置 =====
# 可在此覆盖打卡服务的数据库、Bitmap TTL等配置
logging:
  level:
    com.shanyuefang: debug"

echo "=== Done ==="
echo ""
