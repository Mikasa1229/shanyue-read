#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────
# check-errors.sh  — 汇总各服务最新 ERROR 日志，供 Claude 分析
# 用法：bash scripts/check-errors.sh [行数，默认50]
# ─────────────────────────────────────────────────────────────────

LINES=${1:-50}
ROOT="$(cd "$(dirname "$0")/.." && pwd)/backend"
FOUND=0

echo "====== 后端错误日志汇总 (最近 ${LINES} 行/服务) ======"
echo "时间：$(date '+%Y-%m-%d %H:%M:%S')"
echo ""

for svc_dir in "$ROOT"/reader-*/; do
  log="$svc_dir/logs/warn.log"
  svc=$(basename "$svc_dir")
  if [ -f "$log" ] && [ -s "$log" ]; then
    FOUND=1
    echo "─── $svc ─────────────────────────────────────────"
    tail -n "$LINES" "$log"
    echo ""
  fi
done

if [ "$FOUND" -eq 0 ]; then
  echo "（暂无错误日志，所有服务运行正常或尚未写入日志）"
fi

echo "======================================================"
