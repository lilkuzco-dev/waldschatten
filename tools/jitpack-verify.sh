#!/usr/bin/env bash
set -euo pipefail

./gradlew clean test build --no-daemon --console=plain

if ! command -v xvfb-run >/dev/null 2>&1; then
	echo "JitPack image has no xvfb-run; refusing to call the client verification complete." >&2
	exit 65
fi

mkdir -p build/lwjgl-natives build/tmp
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dorg.lwjgl.system.SharedLibraryExtractPath=$PWD/build/lwjgl-natives -Djava.io.tmpdir=$PWD/build/tmp"

timeout 12m xvfb-run -a ./gradlew runGametest --no-daemon --console=plain

terralith_mods="$PWD/build/jitpack-terralith-mods"
mkdir -p "$terralith_mods"
curl -fsSL 'https://cdn.modrinth.com/data/8oi3bsk5/versions/OxfI2n80/Terralith_26.2_v2.6.4.jar' \
	-o "$terralith_mods/Terralith_26.2_v2.6.4.jar"
curl -fsSL 'https://cdn.modrinth.com/data/XaDC71GB/versions/V3XWhM8r/lithostitched-1.8.0%2Bbeta3-fabric-26.2.jar' \
	-o "$terralith_mods/lithostitched-1.8.0+beta3-fabric-26.2.jar"
WALDSCHATTEN_TERRALITH_MODS="$terralith_mods" \
	timeout 12m xvfb-run -a ./gradlew runTerralithSurvey --no-daemon --console=plain

./gradlew publishToMavenLocal --no-daemon --console=plain
