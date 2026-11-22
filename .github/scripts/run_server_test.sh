#!/usr/bin/env bash
# 下载指定 Paper/Folia 构建，搭一个最小服务端，验证插件能被正常加载并启用。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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

# 预置插件配置：开启自动清理，缩短扫描间隔、放大每轮扫描数，便于功能测试
mkdir -p plugins/FixDroopingLeafDupe
cat > plugins/FixDroopingLeafDupe/config.yml <<'EOF'
cancel-fertilize: true
auto-clean:
  enabled: true
  interval-ticks: 40
  chunks-per-run: 512
  on-chunk-load: false
language: zh_cn
EOF

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

echo "== Starting server (timeout 300s) =="
: > "$LOG"
rm -f cmd.in
mkfifo cmd.in
timeout 300 java -Xms512M -Xmx1200M -Dfixdroopingleafdupe.debug=true -jar server.jar --nogui < cmd.in > "$LOG" 2>&1 &
SERVER_PID=$!
exec 3> cmd.in
send() { printf '%s\n' "$1" >&3 2>/dev/null || true; }

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

# ---- 功能测试（仅当插件已启用） ----
# 通过控制台在 forceload 的独立区块放置：一个畸形垂滴叶（只有上半、下方空气），
# 应被插件的自动清理清除；一个普通石头方块应被保留（防止“误删一切”回归）。
FUNC=0
if [ "$ENABLED" = "1" ]; then
  sleep 2
  send "forceload add 1000 1000"
  sleep 2
  # 畸形垂滴叶（只有上半、下方空气）——应被插件清除
  send "setblock 1000 100 1000 minecraft:small_dripleaf[half=upper]"
  # 对照：普通石头——不应被插件“误判清除”
  send "setblock 1002 100 1000 minecraft:stone"
  for _ in $(seq 1 30); do
    if grep -q '已清除畸形垂滴叶: world 1000,100,1000' "$LOG" 2>/dev/null; then
      break
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  if grep -q 'Changed the block at 1000, 100, 1000' "$LOG" 2>/dev/null \
     && grep -q '已清除畸形垂滴叶: world 1000,100,1000' "$LOG" 2>/dev/null \
     && ! grep -q '已清除畸形垂滴叶: world 1002,100,1000' "$LOG" 2>/dev/null; then
    FUNC=1
  fi
fi

# 停止服务端
send "stop"
sleep 3
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

if ! grep -q 'FixDroopingLeafDupe 已启用' "$LOG"; then
  echo "RESULT: FAIL - plugin did NOT enable on ${PROJECT} ${VERSION} (build ${BUILD})"
  exit 1
fi

if [ "$FUNC" != "1" ]; then
  echo "RESULT: FAIL - functional test failed on ${PROJECT} ${VERSION} (build ${BUILD})"
  echo "----- functional markers -----"
  grep -nE 'Changed the block|已清除畸形垂滴叶|forceload|setblock|Dispatching command' "$LOG" || true
  exit 1
fi

echo "RESULT: OK - plugin enabled & auto-clean functional on ${PROJECT} ${VERSION} (build ${BUILD})"
exit 0
