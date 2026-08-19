#!/usr/bin/env bash
set -euo pipefail

./gradlew clean test build --no-daemon --console=plain

terralith_mods="$PWD/build/jitpack-terralith-mods"
mkdir -p "$terralith_mods"
curl -fsSL 'https://cdn.modrinth.com/data/8oi3bsk5/versions/OxfI2n80/Terralith_26.2_v2.6.4.jar' \
	-o "$terralith_mods/Terralith_26.2_v2.6.4.jar"
curl -fsSL 'https://cdn.modrinth.com/data/XaDC71GB/versions/V3XWhM8r/lithostitched-1.8.0%2Bbeta3-fabric-26.2.jar' \
	-o "$terralith_mods/lithostitched-1.8.0+beta3-fabric-26.2.jar"

seeds=(0 1 -1 8675309 -160353759327030922 9223372036854775807)
for seed in "${seeds[@]}"; do
	WALDSCHATTEN_SURVEY_SEED="$seed" \
		timeout 5m ./gradlew runHeadlessSurvey --no-daemon --console=plain
done
for seed in "${seeds[@]}"; do
	WALDSCHATTEN_SURVEY_SEED="$seed" WALDSCHATTEN_TERRALITH_MODS="$terralith_mods" \
		timeout 5m ./gradlew runHeadlessTerralithSurvey --no-daemon --console=plain
done

./gradlew publishToMavenLocal --no-daemon --console=plain
