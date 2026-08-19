#!/usr/bin/env bash
set -euo pipefail

./gradlew clean test build --no-daemon --console=plain

if ! command -v xvfb-run >/dev/null 2>&1; then
	echo "JitPack image has no xvfb-run; refusing to call the client verification complete." >&2
	exit 65
fi

timeout 12m xvfb-run -a ./gradlew runGametest --no-daemon --console=plain
./gradlew publishToMavenLocal --no-daemon --console=plain
