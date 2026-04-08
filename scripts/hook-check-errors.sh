#!/usr/bin/env bash
# UserPromptSubmit hook — 检测后端错误日志并注入 Claude 上下文
# 仅当日志包含 ERROR/Exception 时才输出内容

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
output=$("$SCRIPT_DIR/check-errors.sh" 80 2>/dev/null)

# 没有错误则静默退出，不注入任何内容
if ! echo "$output" | grep -qE 'ERROR|Exception|Caused by:'; then
  exit 0
fi

context="【后端错误日志自动注入 — $(date '+%H:%M:%S')】
以下是各服务最近的 ERROR 日志，请在分析问题时参考：

${output}"

# 用 python 输出合法 JSON，使用 buffer 读取并用 replace 处理无效字节，避免 surrogate 问题
_json_output() {
  local py_cmd='import json, sys; ctx = sys.stdin.buffer.read().decode("utf-8", "replace"); print(json.dumps({"hookSpecificOutput": {"hookEventName": "UserPromptSubmit", "additionalContext": ctx}}))'
  if command -v python &>/dev/null; then
    printf '%s' "$context" | python -c "$py_cmd"
  elif command -v python3 &>/dev/null; then
    printf '%s' "$context" | python3 -c "$py_cmd"
  fi
}

_json_output
