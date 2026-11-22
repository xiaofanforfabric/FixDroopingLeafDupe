#!/usr/bin/env python3
"""动态生成 CI 测试矩阵（PaperMC Fill API v3）。

从 https://fill.papermc.io/v3 拉取 paper / folia 在 [LOWER, UPPER] 区间内的所有稳定版本，
为每个版本选出最新的 STABLE 构建，并读取该版本要求的最低 Java 主版本。

输出写入 GITHUB_OUTPUT：
    matrix={"include":[{project,version,build,java,url,name}, ...]}
    count=<条目数>

注意：版本列表完全来自官方 API，未来出现的新版本（例如 26.x）只要 API 已发布，
会自动纳入测试，无需修改本脚本。
"""
import json
import os
import re
import sys
import time
import urllib.request

API = "https://fill.papermc.io/v3"
PROJECTS = ["paper", "folia"]
LOWER = (1, 17, 0)
UPPER = (26, 1, 2)


def parse_version(ver):
    """把 '1.17' / '1.20.5' 解析为 (major, minor, patch)；非稳定版（-pre/-rc 等）返回 None。"""
    parts = ver.split(".")
    if not all(re.fullmatch(r"\d+", p) for p in parts):
        return None
    nums = [int(p) for p in parts]
    while len(nums) < 3:
        nums.append(0)
    return tuple(nums[:3])


def http_json(url):
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "FixDroopingLeafDupe-CI"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.load(resp)
        except Exception as exc:  # noqa: BLE001
            print(f"WARN: GET {url} failed ({exc})", file=sys.stderr)
            time.sleep(2 * (attempt + 1))
    return None


def pick_build(builds):
    """优先选择 STABLE 且带 server:default 下载的构建，否则退化为任意可下载构建。"""
    fallback = None
    for build in builds:
        download = (build.get("downloads") or {}).get("server:default")
        if not download:
            continue
        if str(build.get("channel", "")).upper() == "STABLE":
            return build, download
        if fallback is None:
            fallback = (build, download)
    return fallback if fallback else (None, None)


def main():
    include = []
    for project in PROJECTS:
        data = http_json(f"{API}/projects/{project}/versions")
        if not data:
            print(f"WARN: no data for project {project}", file=sys.stderr)
            continue

        for entry in data.get("versions", []):
            info = entry.get("version", {})
            ver = info.get("id")
            v = parse_version(ver) if ver else None
            if v is None or not (LOWER <= v <= UPPER):
                continue

            java_min = ((info.get("java") or {}).get("version") or {}).get("minimum") or 17

            builds = http_json(f"{API}/projects/{project}/versions/{ver}/builds")
            if not isinstance(builds, list) or not builds:
                continue

            build, download = pick_build(builds)
            if build is None:
                continue

            include.append({
                "project": project,
                "version": ver,
                "build": build.get("id"),
                "java": int(java_min),
                "url": download.get("url"),
                "name": download.get("name"),
            })

    include.sort(key=lambda e: (e["project"], parse_version(e["version"])))
    count = len(include)

    if count == 0:
        # 保证矩阵合法（无条目时用占位项，test 作业会通过 count 判断跳过）
        matrix = {"include": [{"project": "none", "version": "none", "build": 0,
                               "java": 17, "url": "", "name": "none"}]}
    else:
        matrix = {"include": include}

    print(f"Prepared {count} matrix entries", file=sys.stderr)

    github_output = os.environ.get("GITHUB_OUTPUT")
    lines = [
        "matrix=" + json.dumps(matrix, separators=(",", ":")),
        f"count={count}",
    ]
    if github_output:
        with open(github_output, "a", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")
    else:
        print("\n".join(lines))


if __name__ == "__main__":
    main()

