"""Resumable source crawler for a canonical book.

The novel service remains the source of truth: every request goes through its
content endpoint, which stores an immutable content version and publishes the
LightRAG indexing event. The crawler only reports metadata and never prints
chapter bodies or secrets.
"""

import argparse
import json
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def get_json(url, timeout=90):
    request = Request(url, headers={"User-Agent": "ReaderNovelCrawler/1.0"})
    with urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def fetch_one(args, chapter):
    source_id, book_url, canonical_id, base_url, retries = args
    chapter_url = chapter.get("chapterUrl")
    index = int(chapter.get("index", 0))
    query = urlencode({
        "chapterUrl": chapter_url,
        "bookUrl": book_url,
        "chapterIndex": index,
        "canonicalBookId": canonical_id,
    })
    url = f"{base_url}/api/book-sources/{source_id}/content?{query}"
    last_error = "unknown error"
    for attempt in range(retries + 1):
        try:
            payload = get_json(url)
            content = ((payload.get("data") or {}).get("content") or "").strip()
            if payload.get("code") == 200 and content:
                return index, True, len(content), ""
            last_error = f"unexpected response code={payload.get('code')} length={len(content)}"
        except (HTTPError, URLError, TimeoutError, ValueError) as exc:
            last_error = str(exc)[:240]
        if attempt < retries:
            time.sleep(min(8, 1.5 * (attempt + 1)))
    return index, False, 0, last_error


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8082")
    parser.add_argument("--source-id", required=True, type=int)
    parser.add_argument("--book-url", required=True)
    parser.add_argument("--canonical-id", required=True, type=int)
    parser.add_argument("--workers", default=8, type=int)
    parser.add_argument("--retries", default=3, type=int)
    args = parser.parse_args()

    chapters_url = f"{args.base_url}/api/book-sources/{args.source_id}/chapters?{urlencode({'bookUrl': args.book_url})}"
    chapters_payload = get_json(chapters_url)
    chapters = (chapters_payload.get("data") or [])
    if not chapters:
        raise SystemExit("No chapters returned by source service")
    print(f"chapters={len(chapters)} workers={args.workers}", flush=True)

    worker_args = (args.source_id, args.book_url, args.canonical_id, args.base_url, args.retries)
    succeeded = failed = completed = 0
    with ThreadPoolExecutor(max_workers=max(1, min(args.workers, 16))) as executor:
        futures = [executor.submit(fetch_one, worker_args, chapter) for chapter in chapters]
        for future in as_completed(futures):
            index, ok, length, error = future.result()
            completed += 1
            if ok:
                succeeded += 1
            else:
                failed += 1
                print(f"failed chapter={index} error={error}", flush=True)
            if completed % 25 == 0 or completed == len(chapters):
                print(f"progress={completed}/{len(chapters)} succeeded={succeeded} failed={failed}", flush=True)

    if failed:
        raise SystemExit(2)
    print(f"complete chapters={len(chapters)} succeeded={succeeded}", flush=True)


if __name__ == "__main__":
    main()
