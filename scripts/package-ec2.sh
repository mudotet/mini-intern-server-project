#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
PLATFORM="${1:-linux/amd64}"
case "$PLATFORM" in linux/amd64|linux/arm64) ;; *) printf '%s\n' 'Supported platforms: linux/amd64 or linux/arm64' >&2; exit 1 ;; esac
./gradlew installDist
docker build --platform "$PLATFORM" -f docker/application/Dockerfile -t mini-game-server:ec2 .
docker save mini-game-server:ec2 | gzip > build/mini-game-server-ec2.tar.gz
printf '%s\n' 'Created build/mini-game-server-ec2.tar.gz. No AWS resources were changed.'
