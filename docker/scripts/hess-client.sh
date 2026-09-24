#!/usr/bin/env bash
# Hess 无头客户端 sidecar：host 网络连 127.0.0.1:<游戏口>。
# 必须挂原始 game-lib.jar（HESS_CLIENT_LIBS）覆盖服端 ASM 缓存里被 stub 的 j.d/j.e。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
IMAGE="${RW_MHPS_IMAGE:-rwmhps-server:local}"

log() { printf '==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || die "need docker"

DATA_DIR="${RW_MHPS_DATA_DIR:-$ROOT/data}"
[[ -d "$DATA_DIR/gameData" ]] || die "missing $DATA_DIR/gameData"

TARGET="${HESS_CLIENT_TARGET:-127.0.0.1:5123}"
NAME="${HESS_CLIENT_NAME:-bot1}"
CHAT="${HESS_CLIENT_CHAT-}"
CHAT_DELAY="${HESS_CLIENT_CHAT_DELAY:-0}"
CHAT2="${HESS_CLIENT_CHAT2:-}"
CHAT2_DELAY="${HESS_CLIENT_CHAT2_DELAY:-0}"
TIMEOUT="${HESS_CLIENT_TIMEOUT:-60}"
CONTAINER="${HESS_CLIENT_CONTAINER:-rwmhps-hess-client}"
JAR="${HESS_CLIENT_JAR:-$ROOT/Server-All/build/libs/Server-All.jar}"
HESS_LIBS="${HESS_CLIENT_LIBS:-$ROOT/Server-Core/libs}"

[[ -f "$JAR" ]] || die "missing $JAR（先 ./gradlew :Server-All:jar）"
[[ -f "$HESS_LIBS/game-lib.jar" ]] || die "missing $HESS_LIBS/game-lib.jar"

CLIENT_DATA="${HESS_CLIENT_DATA_DIR:-}"
CLEAN_DATA=0
if [[ -z "$CLIENT_DATA" ]]; then
  CLIENT_DATA="$(mktemp -d)"
  CLEAN_DATA=1
fi
mkdir -p "$CLIENT_DATA/plugins" "$CLIENT_DATA/log"
python3 - "$CLIENT_DATA" <<'PY'
import json, pathlib, sys
dest = pathlib.Path(sys.argv[1])
(dest / "Config.json").write_text(json.dumps({
    "port": 0,
    "webPort": 0,
    "defStartCommand": "start",
    "log": "DEBUG",
    "autoUpList": False,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(dest / "ConfigServer.json").write_text("{}\n", encoding="utf-8")
PY

CHAT_ARGS=()
if [[ -n "$CHAT" ]]; then
  CHAT_ARGS+=(--chat "$CHAT")
  if [[ "$CHAT_DELAY" != "0" ]]; then
    CHAT_ARGS+=(--chat-delay "$CHAT_DELAY")
  fi
fi
if [[ -n "$CHAT2" ]]; then
  CHAT_ARGS+=(--chat2 "$CHAT2")
  if [[ "$CHAT2_DELAY" != "0" ]]; then
    CHAT_ARGS+=(--chat2-delay "$CHAT2_DELAY")
  fi
fi
EXTRA_ARGS=()
if [[ "${HESS_CLIENT_START:-}" == "1" ]]; then
  EXTRA_ARGS+=(--start)
fi

cleanup() {
  if [[ "$CLEAN_DATA" == "1" ]]; then
    rm -rf "$CLIENT_DATA"
  fi
}
trap cleanup EXIT

log "hess-client $NAME -> $TARGET"
docker run --rm --network host \
  --name "$CONTAINER" \
  -e TZ="${TZ:-Asia/Shanghai}" \
  -e HESS_CLIENT_LIBS=/app/hess-libs \
  -v "$JAR:/app/Server-All.jar:ro" \
  -v "$CLIENT_DATA:/app/data" \
  -v "$DATA_DIR/gameData:/app/data/gameData:ro" \
  -v "$HESS_LIBS:/app/hess-libs:ro" \
  --entrypoint java \
  "$IMAGE" \
  -Xms256m -Xmx512m \
  -Drwhps.eula.accepted=true \
  -Dfile.encoding=UTF-8 \
  -Djava.awt.headless=true \
  -jar /app/Server-All.jar \
  --hess-client "$TARGET" \
  --name "$NAME" \
  ${CHAT_ARGS[@]+"${CHAT_ARGS[@]}"} \
  --timeout "$TIMEOUT" \
  ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}
