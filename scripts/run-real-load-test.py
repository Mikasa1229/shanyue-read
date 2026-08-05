"""Small reproducible fallback runner when JMeter is not installed locally.

It uses a thread pool and persistent urllib connections to measure the same
read-only scenarios as the JMeter plan without touching application data.
"""
from __future__ import annotations

import argparse
import json
import math
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

ENDPOINTS = {
    "novels_size20": ("/api/novels?page=1&size=20", {200}),
    "novels_page2": ("/api/novels?page=2&size=20", {200}),
    "ranking": ("/api/reading/ranking?page=1&size=20", {200}),
    "book_sources": ("/api/book-sources?page=1&size=20", {200}),
    "book_source_search": ("/api/book-sources/search?keyword=%E5%89%91%E6%9D%A5", {200}),
    # The platform wraps business 404 in an HTTP 200 envelope with code=404.
    "missing_novel": ("/api/novels/1", {200}),
    "auth_probe": ("/api/auth", {401}),
    "hot_shelf_unauthorized": ("/api/bookshelf/hot?top=20", {401}),
    "agent_infrastructure_unauthorized": ("/api/agent/infrastructure", {401}),
}


def percentile(values: list[float], p: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * p
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return round(ordered[lower], 2)
    return round(ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower), 2)


def request(base_url: str, path: str, expected: set[int], timeout: float) -> dict:
    started = time.perf_counter()
    status = 0
    error = None
    try:
        req = urllib.request.Request(base_url.rstrip("/") + path, headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status = response.status
            response.read()
    except urllib.error.HTTPError as exc:
        status = exc.code
        try:
            exc.read()
        except Exception:
            pass
    except Exception as exc:  # connection reset/timeout are part of the result
        error = type(exc).__name__
    return {
        "latencyMs": round((time.perf_counter() - started) * 1000, 2),
        "status": status,
        "ok": status in expected,
        "error": error,
    }


def run_stage(base_url: str, endpoint_name: str, workers: int, duration: int, timeout: float) -> dict:
    path, expected = ENDPOINTS[endpoint_name]
    deadline = time.perf_counter() + duration
    results: list[dict] = []
    lock = threading.Lock()

    def worker() -> None:
        local: list[dict] = []
        while time.perf_counter() < deadline:
            local.append(request(base_url, path, expected, timeout))
        with lock:
            results.extend(local)

    started = datetime.now(timezone.utc).isoformat()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(worker) for _ in range(workers)]
        for future in futures:
            future.result()
    latencies = [float(item["latencyMs"]) for item in results]
    ok = sum(1 for item in results if item["ok"])
    count = len(results)
    return {
        "endpoint": endpoint_name,
        "path": path,
        "concurrency": workers,
        "durationSeconds": duration,
        "startedAt": started,
        "requests": count,
        "ok": ok,
        "errors": count - ok,
        "successRate": round(ok / count * 100, 4) if count else 0,
        "qps": round(count / duration, 2),
        "successQps": round(ok / duration, 2),
        "p50Ms": percentile(latencies, 0.50),
        "p95Ms": percentile(latencies, 0.95),
        "p99Ms": percentile(latencies, 0.99),
        "maxMs": round(max(latencies), 2) if latencies else None,
        "errorTypes": sorted({item["error"] for item in results if item["error"]}),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--duration", type=int, default=5)
    parser.add_argument("--stages", default="1,10,25,50,100,200,400")
    parser.add_argument("--output", default="logs/real-load-test.json")
    parser.add_argument("--endpoints", default=", ".join(ENDPOINTS.keys()))
    args = parser.parse_args()
    stages = [int(value) for value in args.stages.split(",") if value.strip()]
    selected = [value.strip() for value in args.endpoints.split(",") if value.strip()]
    unknown = [value for value in selected if value not in ENDPOINTS]
    if unknown:
        parser.error("unknown endpoints: " + ", ".join(unknown))
    all_results = []
    for endpoint in selected:
        for workers in stages:
            result = run_stage(args.base_url, endpoint, workers, args.duration, 10.0)
            all_results.append(result)
            print(json.dumps(result, ensure_ascii=False))
    report = {
        "tool": "python-threadpool-fallback",
        "baseUrl": args.base_url,
        "durationSecondsPerStage": args.duration,
        "stages": stages,
        "startedAt": datetime.now(timezone.utc).isoformat(),
        "results": all_results,
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"report={output}")


if __name__ == "__main__":
    main()
