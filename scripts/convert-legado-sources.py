#!/usr/bin/env python3
"""Convert Legado source files into the subset understood by Reader.

The original rule objects are intentionally retained. Reader maps the Legado
field names to BookSourceModel and evaluates the ruleSearch/ruleToc/ruleContent
objects at runtime, so dropping those objects would make a source unusable.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

JS_RE = re.compile(r"(?:<js>|@js:)", re.I)


def records(path: Path):
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError):
        return []
    if isinstance(value, dict):
        value = [value]
    return [item for item in value if isinstance(item, dict)]


def has_js(source: dict) -> bool:
    # A JavaScript cover or book-detail field does not prevent the Reader
    # runtime from searching and reading. Filter only JS in the mandatory
    # search, table-of-contents, and content route fields.
    search = source.get("ruleSearch") or {}
    toc = source.get("ruleToc") or {}
    content = source.get("ruleContent") or {}
    required = (
        source.get("searchUrl", ""), search.get("bookList", ""), search.get("name", ""),
        search.get("bookUrl", ""), toc.get("chapterList", ""), toc.get("chapterUrl", ""),
        content.get("content", ""),
    )
    for value in required:
        if JS_RE.search(str(value)):
            return True
    return False


def normalize(source: dict, enabled: bool) -> dict:
    # Keep exactly the names consumed by BookSourceModel plus the original rule
    # objects. Unknown Legado metadata is not needed by the server evaluator.
    keys = (
        "bookSourceName", "bookSourceUrl", "bookSourceType", "bookSourceGroup",
        "enabled", "header", "bookSourceCharset", "searchUrl", "ruleSearch",
        "ruleBookInfo", "ruleToc", "ruleContent",
    )
    result = {key: source[key] for key in keys if key in source}
    result["bookSourceType"] = 0
    result["enabled"] = enabled
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", default="book_source/legado/sources")
    parser.add_argument("--output", default="artifacts/legado-normalized-sources.json")
    parser.add_argument("--include-js", action="store_true", help="include sources requiring JavaScript (server will not execute them)")
    parser.add_argument("--disabled", action="store_true", help="write candidates as disabled; the scanner enables only sources that pass")
    args = parser.parse_args()

    source_dir = Path(args.source_dir)
    candidates = {}
    stats = {"files": 0, "records": 0, "novel": 0, "searchable": 0, "javascript": 0, "deduplicated": 0}
    for path in sorted(source_dir.glob("*.json")):
        stats["files"] += 1
        for source in records(path):
            stats["records"] += 1
            if source.get("bookSourceType", 0) not in (None, 0):
                continue
            stats["novel"] += 1
            if not source.get("bookSourceUrl") or not source.get("searchUrl"):
                continue
            stats["searchable"] += 1
            if has_js(source):
                stats["javascript"] += 1
                if not args.include_js:
                    continue
            # Database identity is source URL. Prefer the last source with the
            # same URL because Legado bundles often contain stale duplicates.
            candidates[source["bookSourceUrl"].rstrip("/")] = normalize(source, enabled=not args.disabled)

    stats["deduplicated"] = len(candidates)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(list(candidates.values()), ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False))
    print(f"normalized={len(candidates)} output={output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
