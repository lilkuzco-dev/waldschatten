#!/usr/bin/env bash
set -euo pipefail

export GRADLE_USER_HOME="$PWD/.jitpack-gradle-home"

./gradlew clean test build --no-daemon --console=plain

terralith_mods="$PWD/build/jitpack-terralith-mods"
mkdir -p "$terralith_mods"
curl -fsSL 'https://cdn.modrinth.com/data/8oi3bsk5/versions/OxfI2n80/Terralith_26.2_v2.6.4.jar' \
	-o "$terralith_mods/Terralith_26.2_v2.6.4.jar"
curl -fsSL 'https://cdn.modrinth.com/data/XaDC71GB/versions/V3XWhM8r/lithostitched-1.8.0%2Bbeta3-fabric-26.2.jar' \
	-o "$terralith_mods/lithostitched-1.8.0+beta3-fabric-26.2.jar"
curl -fsSL 'https://github.com/lilkuzco-dev/empire-worldgen/releases/download/v0.1.0/empire_worldgen-0.1.0.jar' \
	-o "$terralith_mods/empire_worldgen-0.1.0.jar"

seeds=(0 -7237218149412987956)
for seed in "${seeds[@]}"; do
	WALDSCHATTEN_SURVEY_SEED="$seed" \
		timeout 5m ./gradlew runHeadlessSurvey --no-daemon --console=plain
	grep -F "WALDSCHATTEN_HEADLESS PASS seed=$seed " \
		build/run-headless-survey/logs/latest.log >/dev/null
done
for seed in "${seeds[@]}"; do
	WALDSCHATTEN_SURVEY_SEED="$seed" WALDSCHATTEN_TERRALITH_MODS="$terralith_mods" \
		timeout 5m ./gradlew runHeadlessTerralithSurvey --no-daemon --console=plain
	grep -F "WALDSCHATTEN_HEADLESS PASS seed=$seed " \
		build/run-headless-terralith/logs/latest.log >/dev/null
done

./gradlew publishToMavenLocal --no-daemon --console=plain
