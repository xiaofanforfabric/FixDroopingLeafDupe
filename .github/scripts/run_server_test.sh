#!/usr/bin/env bash
# 下载指定 Paper/Folia 构建，搭一个最小服务端，验证插件能被正常加载并启用。
set -euo pipefail

: "${PROJECT:?missing PROJECT}"
: "${VERSION:?missing VERSION}"
: "${SERVER_URL:?missing SERVER_URL}"
BUILD="${BUILD:-0}"

WORK="${RUNNER_TEMP:-/tmp}/srv-${PROJECT}-${VERSION}-${BUILD}"
LOG="$WORK/server.log"

echo "== Node: $(uname -a) =="
java -version 2>&1 || true

mkdir -p "$WORK/plugins"
cd "$WORK"

echo "== Downloading server: $SERVER_URL =="
curl -fsSL --retry 3 -o server.jar "$SERVER_URL"

# 放置插件
PLUGIN_JAR="$(find "$GITHUB_WORKSPACE/plugin" -maxdepth 1 -name '*.jar' | head -n1)"
if [ -z "$PLUGIN_JAR" ]; then
  echo "ERROR: plugin jar not found"; exit 1
fi
cp "$PLUGIN_JAR" plugins/
echo "Plugin: $(basename "$PLUGIN_JAR")"

# 最小化服务端配置
echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
online-mode=false
level-type=flat
generate-structures=false
spawn-protection=0
max-players=1
view-distance=3
simulation-distance=3
EOF

echo "== Starting server (timeout 240s) =="
: > "$LOG"
timeout 240 java -Xms512M -Xmx1200M -jar server.jar --nogui < /dev/null > "$LOG" 2>&1 &
SERVER_PID=$!

ENABLED=0
for _ in $(seq 1 200); do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    break
  fi
  if grep -q 'Done (' "$LOG" 2>/dev/null && grep -q 'FixDroopingLeafDupe 已启用' "$LOG" 2>/dev/null; then
    ENABLED=1
    break
  fi
  if grep -qiE 'UnsupportedClassVersionError|Could not find or load main class|Error occurred during initialization of VM' "$LOG" 2>/dev/null; then
    break
  fi
  sleep 1
done

# 停止服务端
kill "$SERVER_PID" 2>/dev/null || true
sleep 2
kill -9 "$SERVER_PID" 2>/dev/null || true
wait "$SERVER_PID" 2>/dev/null || true

echo "===== server.log (tail 250) ====="
tail -n 250 "$LOG" || true
echo "================================="

# 任一命中的「插件相关报错」即视为失败（插件出错通常会被服务端自动禁用而不会崩溃）
ERROR_REGEX='ERROR\].*\[FixDroopingLeafDupe\]|SEVERE\].*\[FixDroopingLeafDupe\]|\[FixDroopingLeafDupe\].*(Exception|Error|Throwable)|Error occurred while (enabling|disabling) FixDroopingLeafDupe|Could not load .*FixDroopingLeafDupe|Failed to load .*FixDroopingLeafDupe|org\.cn\.xiaofan\.fixdroopingleafdupe|NoClassDefFoundError|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|AbstractMethodError|IncompatibleClassChangeError|UnsupportedClassVersionError|LinkageError|VerifyError|IllegalAccessError|ExceptionInInitializerError|Exception in thread'

if grep -nE "$ERROR_REGEX" "$LOG" > "$WORK/errors.txt" 2>/dev/null; then
  echo "RESULT: FAIL - plugin-related error detected on ${PROJECT} ${VERSION} (build ${BUILD})"
  echo "----- matched lines -----"
  cat "$WORK/errors.txt"
  exit 1
fi

if grep -q 'FixDroopingLeafDupe 已启用' "$LOG"; then
  echo "RESULT: OK - plugin enabled on ${PROJECT} ${VERSION} (build ${BUILD})"
  exit 0
fi

echo "RESULT: FAIL - plugin did NOT enable on ${PROJECT} ${VERSION} (build ${BUILD})"
exit 1
