#!/usr/bin/env python3
"""Import and full-chain validate Legado sources against the running novel API."""
from __future__ import annotations

import argparse
import json
import threading
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from urllib.parse import quote
from urllib.request import Request, urlopen

BOOKS = ("剑来", "斗罗大陆", "西游记", "十日终焉", "龙族", "吞噬星空", "我的青春恋爱物语果然有问题", "关于我转生变成史莱姆这档事")


def call(url, method="GET", payload=None, timeout=18):
    data = None
    headers = {"Accept": "application/json", "User-Agent": "Reader-source-validator/1.0"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    with urlopen(Request(url, method=method, data=data, headers=headers), timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def get_sources(api):
    response = call(f"{api}?page=1&size=10000")
    return response.get("data", {}).get("records", [])


def valid_hit(book, row):
    name = str(row.get("name") or "")
    author = str(row.get("author") or "")
    if book == "龙族":
        return name in {"龙族（全集）", "龙族全集", "龙族I：火之晨曦", "龙族II：悼亡者之瞳", "龙族III：黑月之潮", "龙族IV：奥丁之渊", "龙族V：悼亡者的归来"} and (not author or "江南" in author)
    return name == book


def probe(api, source, min_length, stop_after_first_pass):
    result = {"id": str(source["id"]), "name": source.get("sourceName", ""), "url": source.get("sourceUrl", ""), "books": {}}
    for book in BOOKS:
        item = {"search": False, "toc": False, "content": 0, "status": "搜索"}
        try:
            rows = call(f"{api}/{source['id']}/search?keyword={quote(book)}&page=1").get("data") or []
            hit = next((row for row in rows if valid_hit(book, row) and row.get("bookUrl")), None)
            if not hit:
                result["books"][book] = item
                continue
            item["search"] = True
            chapters = call(f"{api}/{source['id']}/chapters?bookUrl={quote(hit['bookUrl'], safe='')}").get("data") or []
            first = next((chapter for chapter in chapters if chapter.get("chapterUrl")), None)
            if not first:
                item["status"] = "目录"
                result["books"][book] = item
                continue
            item["toc"] = True
            body = call(f"{api}/{source['id']}/content?chapterUrl={quote(first['chapterUrl'], safe='')}")
            item["content"] = len(str(body.get("data", {}).get("content") or ""))
            item["status"] = "通过" if item["content"] >= min_length else "正文"
        except Exception as exc:
            item["status"] = f"请求异常: {type(exc).__name__}"
        result["books"][book] = item
        if stop_after_first_pass and item["status"] == "通过":
            break
    result["passedBooks"] = [book for book, value in result["books"].items() if value["status"] == "通过"]
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", default="http://localhost:8082/api/book-sources")
    parser.add_argument("--normalized", default="artifacts/legado-normalized-sources.json")
    parser.add_argument("--report", default="docs/legado书源全量校验报告-2026-08-14.md")
    parser.add_argument("--passing-source-output", default="book_source/legado-passing-source-rules.json", help="export importable rules for sources passing at least one book")
    parser.add_argument("--import-candidates", action="store_true", help="import normalized sources before probing")
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--min-content", type=int, default=200)
    parser.add_argument("--limit", type=int, default=0, help="only probe first N sources; 0 means all")
    parser.add_argument("--activate-passing-only", action="store_true", help="temporarily enable each imported source and keep only sources passing at least one book")
    parser.add_argument("--stop-after-first-pass", action="store_true", help="stop testing a source after its first complete success")
    parser.add_argument("--checkpoint", default="artifacts/legado-scan-checkpoint.jsonl", help="append each completed source here; enables safe resume")
    parser.add_argument("--resume", action="store_true", help="skip source IDs already present in the checkpoint")
    args = parser.parse_args()

    if args.import_candidates:
        sources = json.loads(Path(args.normalized).read_text(encoding="utf-8"))
        for start in range(0, len(sources), 50):
            call(f"{args.api}/import/json", method="POST", payload={"json": json.dumps(sources[start:start + 50], ensure_ascii=False)})
            print(f"imported {min(start + 50, len(sources))}/{len(sources)}", flush=True)
    sources = get_sources(args.api)
    normalized_urls = None
    if args.activate_passing_only:
        normalized_urls = {str(item.get("bookSourceUrl", "")).rstrip("/") for item in json.loads(Path(args.normalized).read_text(encoding="utf-8"))}
        sources = [source for source in sources if str(source.get("sourceUrl", "")).rstrip("/") in normalized_urls]
    completed = {}
    checkpoint = Path(args.checkpoint)
    if args.resume and checkpoint.exists():
        for line in checkpoint.read_text(encoding="utf-8").splitlines():
            try:
                value = json.loads(line)
                completed[value["id"]] = value
            except (KeyError, json.JSONDecodeError):
                continue
        sources = [source for source in sources if str(source["id"]) not in completed]
    if args.limit:
        sources = sources[:args.limit]
    print(f"probing {len(sources)} sources against {len(BOOKS)} books", flush=True)
    results = list(completed.values())
    write_lock = threading.Lock()
    def activate_and_probe(source):
        was_enabled = bool(source.get("enabled"))
        if args.activate_passing_only and not was_enabled:
            call(f"{args.api}/{source['id']}/status", method="PUT")
        try:
            value = probe(args.api, source, args.min_content, args.stop_after_first_pass)
            if args.activate_passing_only and not value["passedBooks"]:
                call(f"{args.api}/{source['id']}/status", method="PUT")
            return value
        except Exception:
            if args.activate_passing_only and not was_enabled:
                try:
                    call(f"{args.api}/{source['id']}/status", method="PUT")
                except Exception:
                    pass
            raise

    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
        futures = [pool.submit(activate_and_probe, source) for source in sources]
        for index, future in enumerate(as_completed(futures), 1):
            value = future.result()
            results.append(value)
            with write_lock:
                checkpoint.parent.mkdir(parents=True, exist_ok=True)
                with checkpoint.open("a", encoding="utf-8") as handle:
                    handle.write(json.dumps(value, ensure_ascii=False) + "\n")
            print(f"probed {index}/{len(futures)}", flush=True)
    results.sort(key=lambda value: value["name"])
    passing = [value for value in results if value["passedBooks"]]
    lines = ["# Legado 书源全量校验报告", "", f"生成时间：{time.strftime('%Y-%m-%d %H:%M:%S')}；扫描源数量：{len(results)}；至少一本完整通过：{len(passing)}。", "", "验收链路：搜索命中 → 目录有章节 → 首章正文不少于 200 字。", "", "## 源汇总", "", "| 书源 | 地址 | 通过书目 |", "|---|---|---|"]
    for value in passing:
        lines.append(f"| {value['name'].replace('|', '/')} | {value['url']} | {', '.join(value['passedBooks'])} |")
    lines += ["", "## 全量明细", "", "| 书源 | 书目 | 搜索 | 目录 | 正文长度 | 状态 |", "|---|---|---:|---:|---:|---|"]
    for value in results:
        for book in BOOKS:
            item = value["books"].get(book, {})
            lines.append(f"| {value['name'].replace('|', '/')} | {book} | {int(item.get('search', False))} | {int(item.get('toc', False))} | {item.get('content', 0)} | {item.get('status', '未测试')} |")
    report = Path(args.report)
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    normalized_by_url = {
        str(item.get("bookSourceUrl", "")).rstrip("/"): item
        for item in json.loads(Path(args.normalized).read_text(encoding="utf-8"))
    }
    passing_rules = [
        normalized_by_url[value["url"].rstrip("/")]
        for value in passing
        if value["url"].rstrip("/") in normalized_by_url
    ]
    rule_output = Path(args.passing_source_output)
    rule_output.parent.mkdir(parents=True, exist_ok=True)
    rule_output.write_text(json.dumps(passing_rules, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"report={report} passing={len(passing)} rules={rule_output}", flush=True)


if __name__ == "__main__":
    main()
