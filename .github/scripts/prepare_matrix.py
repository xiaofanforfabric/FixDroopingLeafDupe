#!/usr/bin/env python3
"""动态生成 CI 测试矩阵。

从 PaperMC API v2 拉取 paper / folia 在 [LOWER, UPPER] 区间内的所有稳定版本，
为每个版本选出最新构建，并推断运行所需的 Java 主版本。

输出写入 GITHUB_OUTPUT（键：matrix），格式为 GitHub Actions matrix 的 JSON：
    {"include": [{"project":..,"version":..,"build":..,"file":..,"java":..}, ...]}

注意：版本列表完全来自官方 API，因此未来出现的新版本（例如 26.1.2）只要 API 已发布，
会自动纳入测试，无需修改本脚本。
"""
import json
import os
import re
import sys
import time
import urllib.request

API = "https://api.papermc.io/v2"
PROJECTS = ["paper", "folia"]
LOWER = (1, 17, 0)
UPPER = (26, 1, 2)


def parse_version(ver: str):
    """把 '1.17' / '1.20.5' 解析为 (major, minor, patch)；非稳定版（-pre/-rc 等）返回 None。"""
    parts = ver.split(".")
    if not all(re.fullmatch(r"\d+", p) for p in parts):
        return None
    nums = [int(p) for p in parts]
    while len(nums) < 3:
        nums.append(0)
    return tuple(nums[:3])


def http_json(url: str):
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "FixDroopingLeafDupe-CI"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.load(resp)
        except Exception as exc:  # noqa: BLE001
            print(f"WARN: GET {url} failed ({exc})", file=sys.stderr)
            time.sleep(2 * (attempt + 1))
    return None


def java_for(v):
    major, minor, patch = v
    if v >= (1, 20, 5):
        return 21
    if v >= (1, 18, 0):
        return 17
    return 16  # 1.17.x


def main():
    include = []
    for project in PROJECTS:
        data = http_json(f"{API}/projects/{project}")
        if not data:
            print(f"WARN: no data for project {project}", file=sys.stderr)
            continue

        for ver in data.get("versions", []):
            v = parse_version(ver)
            if v is None or not (LOWER <= v <= UPPER):
                continue

            builds = http_json(f"{API}/projects/{project}/versions/{ver}/builds")
            if not builds or not builds.get("builds"):
                continue

            # 取最新的一个构建
            build = builds["builds"][-1]
            app = (build.get("downloads") or {}).get("application") or {}
            filename = app.get("name")
            if not filename:
                continue

            include.append({
                "project": project,
                "version": ver,
                "build": build["build"],
                "file": filename,
                "java": java_for(v),
            })

    include.sort(key=lambda e: (e["project"], parse_version(e["version"])))
    matrix = {"include": include}
    print(f"Prepared {len(include)} matrix entries", file=sys.stderr)

    line = "matrix=" + json.dumps(matrix, separators=(",", ":"))
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as fh:
            fh.write(line + "\n")
    else:
        print(line)


if __name__ == "__main__":
    main()
