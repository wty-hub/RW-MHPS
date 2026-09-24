#!/usr/bin/env bash
# 构建无头联调用的服务端镜像。宿主机需 Java 21。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
IMAGE="${RW_MHPS_IMAGE:-rwmhps-server:local}"

if [[ -z "${JAVA_HOME:-}" ]] && [[ -d /usr/lib/jvm/java-21-openjdk-amd64 ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"
fi

cd "$ROOT"
./gradlew :Server-All:jar :plugin:AllyRequest:jar
mkdir -p docker/dist/server-jar
cp Server-All/build/libs/Server-All.jar docker/dist/server-jar/Server-All.jar
docker build -f docker/Dockerfile.server -t "$IMAGE" .
echo "built $IMAGE"
