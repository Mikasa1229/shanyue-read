#!/bin/sh
# Initialize valid UTF-8 YAML snippets in Nacos.
set -eu

NACOS_URL="http://nacos:8848/nacos/v1/cs/configs"
GROUP="reader"

put_config() {
  data_id="$1"
  content="$2"
  result=$(curl -sf -X POST "$NACOS_URL" \
    --data-urlencode "dataId=$data_id" \
    --data-urlencode "group=$GROUP" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content=$content")
  if [ "$result" = "true" ]; then
    echo "[OK] $data_id"
  else
    echo "[WARN] $data_id -> $result"
  fi
}

put_config "reader-gateway.yaml" '# Central gateway overrides.
logging:
  level:
    com.shanyuefang: debug
    org.springframework.cloud.gateway: info'

put_config "reader-user.yaml" '# Central user-service overrides.
logging:
  level:
    com.shanyuefang: debug'

put_config "reader-novel.yaml" '# Central novel-service overrides.
logging:
  level:
    com.shanyuefang: debug'

put_config "reader-comment.yaml" '# Central comment-service overrides.
logging:
  level:
    com.shanyuefang: debug'

put_config "reader-interaction.yaml" '# Central interaction-service overrides.
logging:
  level:
    com.shanyuefang: debug'

put_config "reader-checkin.yaml" '# Central check-in-service overrides.
logging:
  level:
    com.shanyuefang: debug'

echo "Nacos YAML initialization complete."
