#!/usr/bin/env bash
set -euo pipefail

export GRADLE_USER_HOME="$PWD/.jitpack-gradle-home"

./gradlew clean test build --no-daemon --console=plain

# Everything the surveys stage beside us: the terrain stack the empire server runs, the
# hard dependency, and enchanted-forest — the other mod that rewrites the same parameter
# list. A pre-set WALDSCHATTEN_DEP_MODS (a local folder, e.g. a not-yet-released jar)
# is honoured as-is; otherwise the released versions are fetched.
if [ -z "${WALDSCHATTEN_DEP_MODS:-}" ]; then
	dep_mods="$PWD/build/jitpack-dep-mods"
	mkdir -p "$dep_mods"
	curl -fsSL 'https://cdn.modrinth.com/data/8oi3bsk5/versions/OxfI2n80/Terralith_26.2_v2.6.4.jar' \
		-o "$dep_mods/Terralith_26.2_v2.6.4.jar"
	curl -fsSL 'https://cdn.modrinth.com/data/XaDC71GB/versions/V3XWhM8r/lithostitched-1.8.0%2Bbeta3-fabric-26.2.jar' \
		-o "$dep_mods/lithostitched-1.8.0+beta3-fabric-26.2.jar"
	curl -fsSL 'https://github.com/lilkuzco-dev/empire-worldgen/releases/download/v0.2.0/empire_worldgen-0.2.0.jar' \
		-o "$dep_mods/empire_worldgen-0.2.0.jar"
	curl -fsSL 'https://github.com/lilkuzco-dev/vibranium/releases/download/v1.8.1/vibranium-1.8.1.jar' \
		-o "$dep_mods/vibranium-1.8.1.jar"
	curl -fsSL 'https://github.com/lilkuzco-dev/enchanted-forest/releases/download/v0.1.11/enchanted-forest-0.1.11.jar' \
		-o "$dep_mods/enchanted-forest-0.1.11.jar"
	export WALDSCHATTEN_DEP_MODS="$dep_mods"
fi

seeds=(0 1 -1 8675309 -160353759327030922 9223372036854775807)
for seed in "${seeds[@]}"; do
	WALDSCHATTEN_SURVEY_SEED="$seed" \
		timeout 5m ./gradlew runHeadlessSurvey --no-daemon --console=plain
	grep -F "WALDSCHATTEN_HEADLESS PASS seed=$seed " \
		build/run-headless-survey/logs/latest.log >/dev/null
done
for seed in "${seeds[@]}"; do
	WALDSCHATTEN_SURVEY_SEED="$seed" \
		timeout 5m ./gradlew runHeadlessTerralithSurvey --no-daemon --console=plain
	grep -F "WALDSCHATTEN_HEADLESS PASS seed=$seed " \
		build/run-headless-terralith/logs/latest.log >/dev/null
done

./gradlew publishToMavenLocal --no-daemon --console=plain
