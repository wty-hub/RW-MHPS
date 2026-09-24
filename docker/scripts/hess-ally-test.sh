#!/usr/bin/env bash
# AllyRequest 无头客户端联调：隔离临时服 + 两个 Hess 客户端。
# 开局后 bot1 `.jm 2`，bot2 `.y`。断言依据是服端日志（客户端 141 不可靠）：
#   [AllyRequest] 玩家 … 同意了 … 的结盟请求, 已加入其队伍
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
IMAGE="${RW_MHPS_IMAGE:-rwmhps-server:local}"
DATA_DIR="${RW_MHPS_DATA_DIR:-$ROOT/data}"
JAR="${HESS_CLIENT_JAR:-$ROOT/Server-All/build/libs/Server-All.jar}"
PLUGIN_JAR="${ALLY_PLUGIN_JAR:-$ROOT/plugin/AllyRequest/build/libs/AllyRequest.jar}"
HESS_LIBS="${HESS_CLIENT_LIBS:-$ROOT/Server-Core/libs}"
PORT="${ALLY_TEST_PORT:-25240}"
RUN_ID="${RANDOM}-$$"
CLIENT_TIMEOUT="${ALLY_CLIENT_TIMEOUT:-90}"
SERVER_NAME="rwmhps-ally-srv-$RUN_ID"

declare -a CONTAINERS=()
cleanup() {
  local c
  for c in ${CONTAINERS[@]+"${CONTAINERS[@]}"}; do docker rm -f "$c" >/dev/null 2>&1 || true; done
  docker rm -f "$SERVER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

log() { printf '==> %s\n' "$*"; }
fail() { printf 'error: %s\n' "$*" >&2; exit 1; }

[[ -f "$JAR" ]] || fail "missing $JAR（先 ./gradlew :Server-All:jar）"
[[ -f "$PLUGIN_JAR" ]] || fail "missing $PLUGIN_JAR（先 ./gradlew :plugin:AllyRequest:jar）"
[[ -f "$HESS_LIBS/game-lib.jar" ]] || fail "missing $HESS_LIBS/game-lib.jar"
[[ -d "$DATA_DIR/gameData" ]] || fail "missing $DATA_DIR/gameData"
docker image inspect "$IMAGE" >/dev/null 2>&1 || fail "missing image $IMAGE（先 ./docker/docker-build.sh）"

WORK="$(mktemp -d)"
mkdir -p "$WORK/plugins"
cp "$PLUGIN_JAR" "$WORK/plugins/AllyRequest.jar"
python3 - "$WORK" "$PORT" <<'PY'
import json, pathlib, sys
dest, port = pathlib.Path(sys.argv[1]), int(sys.argv[2])
(dest / "Config.json").write_text(json.dumps({
    "port": port,
    "webPort": 0,
    "defStartCommand": "start",
    "log": "DEBUG",
    "autoUpList": False,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(dest / "ConfigServer.json").write_text(json.dumps({
    "enableAllianceGameThreadSync": True,
    "autoStartMinPlayerSize": 2,
    "denySinglePlayerStart": False,
    "enableModTransfer": False,
    "oneAdmin": True,
    "maxPlayer": 10,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

log "start server port=$PORT"
docker run -d --network host \
  --name "$SERVER_NAME" \
  -e TZ="${TZ:-Asia/Shanghai}" \
  -v "$JAR:/app/Server-All.jar:ro" \
  -v "$WORK:/app/data" \
  -v "$DATA_DIR/gameData:/app/data/gameData:ro" \
  --entrypoint java \
  "$IMAGE" \
  -Xms512m -Xmx1024m \
  -Drwhps.eula.accepted=true \
  -Dfile.encoding=UTF-8 \
  -Djava.awt.headless=true \
  -jar /app/Server-All.jar
CONTAINERS+=("$SERVER_NAME")

log "wait for headless"
ok=0
for _ in $(seq 1 90); do
  if docker logs "$SERVER_NAME" 2>&1 | grep -q "无头服务器运行ID"; then
    ok=1
    break
  fi
  sleep 2
done
[[ "$ok" == "1" ]] || { docker logs "$SERVER_NAME" 2>&1 | tail -80; fail "server did not become ready"; }

export HESS_CLIENT_TARGET="127.0.0.1:$PORT"
export HESS_CLIENT_TIMEOUT="$CLIENT_TIMEOUT"
export HESS_CLIENT_JAR="$JAR"
export HESS_CLIENT_LIBS="$HESS_LIBS"
export RW_MHPS_DATA_DIR="$DATA_DIR"
export RW_MHPS_IMAGE="$IMAGE"

log "client bot1 .jm 2"
HESS_CLIENT_NAME=bot1 \
HESS_CLIENT_CHAT=".jm 2" \
HESS_CLIENT_CHAT_DELAY=25 \
HESS_CLIENT_CONTAINER="rwmhps-ally-a-$RUN_ID" \
  "$SCRIPT_DIR/hess-client.sh" &
pid_a=$!

sleep 8
log "client bot2 .y"
HESS_CLIENT_NAME=bot2 \
HESS_CLIENT_CHAT=".y" \
HESS_CLIENT_CHAT_DELAY=30 \
HESS_CLIENT_CONTAINER="rwmhps-ally-b-$RUN_ID" \
  "$SCRIPT_DIR/hess-client.sh" &
pid_b=$!

wait "$pid_a" || true
wait "$pid_b" || true

log "assert server log"
if docker logs "$SERVER_NAME" 2>&1 | grep -q "已加入其队伍"; then
  log "ally ok"
  exit 0
fi
docker logs "$SERVER_NAME" 2>&1 | tail -120
fail "server log missing AllyRequest accept line"
