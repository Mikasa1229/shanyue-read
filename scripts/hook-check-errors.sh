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

# 用 jq 或 python 输出合法 JSON（Windows Store 的 python3 stub 在非交互 shell 中不可用，优先 python）
_json_output() {
  python -c "
import json, sys
ctx = sys.stdin.read()
print(json.dumps({'hookSpecificOutput': {'hookEventName': 'UserPromptSubmit', 'additionalContext': ctx}}))
" <<< "$context"
}

if command -v jq &>/dev/null; then
  jq -n --arg ctx "$context" \
    '{"hookSpecificOutput":{"hookEventName":"UserPromptSubmit","additionalContext":$ctx}}'
elif command -v python &>/dev/null; then
  _json_output
elif command -v python3 &>/dev/null; then
  python3 -c "
import json, sys
ctx = sys.stdin.read()
print(json.dumps({'hookSpecificOutput': {'hookEventName': 'UserPromptSubmit', 'additionalContext': ctx}}))
" <<< "$context"
fi
