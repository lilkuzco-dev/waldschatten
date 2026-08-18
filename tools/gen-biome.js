#!/usr/bin/env node
// Generates data/waldschatten/worldgen/biome/waldschatten.json.
//
// Two things are DERIVED rather than transcribed, and both for the same reason — a
// hand-copied constant is a constant that drifts:
//
//   colours   from tools/palette.js, which is vanilla forest run through one shift
//   features  the non-vegetal decoration steps are lifted verbatim from vanilla
//             dark_forest, so an ore vanilla adds in 26.3 arrives here too instead of
//             being silently absent from one biome in the world
//
// Only the vegetation step, the spawns, the climate and the attributes are ours.
// Usage: node tools/gen-biome.js
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { execFileSync } = require("node:child_process");
const { palette, hex } = require("./palette.js");

const SERVER_JAR = path.join(os.homedir(), ".gradle/caches/fabric-loom/26.2/minecraft-extracted_server.jar");
const OUT = path.join(__dirname, "..", "src/main/resources/data/waldschatten/worldgen/biome/waldschatten.json");

// GenerationStep.Decoration ordinals. Index 9 is the one we replace wholesale.
const VEGETAL_DECORATION = 9;

function vanillaBiome(name) {
	const raw = execFileSync("unzip", ["-p", SERVER_JAR, `data/minecraft/worldgen/biome/${name}.json`], { maxBuffer: 1 << 26 });
	return JSON.parse(raw.toString());
}

/** Our canopy floor. Everything the player actually walks through. */
const VEGETATION = [
	"minecraft:glow_lichen",
	// The floor is laid before anything stands on it: podzol and coarse dirt over the
	// grass, so a dark canopy is not left standing on cheerful green.
	"waldschatten:forest_floor",
	// Then the trees: the canopy has to exist before anything decorates the floor under it.
	"waldschatten:twisted_forest_vegetation",
	"waldschatten:fallen_twisted_log",
	"waldschatten:patch_gnarled_roots",
	"waldschatten:patch_thorn_vine",
	"waldschatten:patch_witch_hazel",
	"waldschatten:patch_nightshade",
	"waldschatten:patch_mandrake",
	"waldschatten:mushroom_ring",
	"waldschatten:forest_floor_cobweb",
	"waldschatten:scattered_cairn_stone",
	"waldschatten:ashen_soil_patch",
	// ---------------------------------------------------------------------------
	// VANILLA FEATURES BELOW: their ORDER RELATIVE TO EACH OTHER IS NOT OURS TO CHOOSE.
	//
	// Minecraft demands one globally consistent ordering of every feature across every
	// biome in a decoration step, and throws "Feature order cycle found" mid-chunk-gen if
	// two biomes disagree. This list once put the mushrooms before patch_grass_forest while
	// every vanilla forest puts grass first; the result was an IllegalStateException that
	// crashed world generation on contact with old_growth_birch_forest.
	//
	// It cannot be caught in a single-biome test world, because a cycle needs two biomes to
	// disagree — which is exactly why it survived the whole render battery. checkFeatureOrder
	// below now proves it against every vanilla biome at generation time.
	// ---------------------------------------------------------------------------
	"minecraft:patch_grass_forest",
	"minecraft:brown_mushroom_normal",
	"minecraft:red_mushroom_normal",
	"minecraft:patch_leaf_litter",
];

/**
 * The same check the game makes, made before the game gets a chance to.
 *
 * <p>Builds the "A comes before B" graph from every vanilla biome's decoration steps plus
 * ours, and looks for a cycle. Reports the pair that disagrees, which the game's own error
 * does not — it names the two biomes and leaves you to find the features yourself.
 */
function checkFeatureOrder(ourFeatures) {
	const jar = execFileSync("unzip", ["-Z1", SERVER_JAR, "data/minecraft/worldgen/biome/*.json"], { maxBuffer: 1 << 26 })
		.toString().trim().split("\n");

	// step index -> Map(feature -> Set(features that must come after it))
	const after = new Map();
	const note = (step, list) => {
		if (!after.has(step)) after.set(step, new Map());
		const graph = after.get(step);
		for (let i = 0; i < list.length; i++) {
			if (!graph.has(list[i])) graph.set(list[i], new Set());
			for (let j = i + 1; j < list.length; j++) graph.get(list[i]).add(list[j]);
		}
	};

	for (const entry of jar) {
		const biome = JSON.parse(execFileSync("unzip", ["-p", SERVER_JAR, entry], { maxBuffer: 1 << 24 }).toString());
		(biome.features || []).forEach((list, step) => note(step, list));
	}
	ourFeatures.forEach((list, step) => note(step, list));

	const problems = [];
	for (const [step, graph] of after) {
		for (const [a, laterThanA] of graph) {
			for (const b of laterThanA) {
				if (graph.get(b) && graph.get(b).has(a)) {
					problems.push(`step ${step}: "${a}" and "${b}" are ordered both ways across biomes`);
				}
			}
		}
	}
	return [...new Set(problems)];
}

/**
 * Hostiles run hot because the canopy keeps the floor dark at noon — that is the biome's
 * central promise, so the spawn table has to pay it off rather than leaving the player
 * in an empty dark wood. Witches are the headline: 5 in vanilla dark forest, 40 here.
 * Wolves are the Grimm note — something in the trees that is not necessarily hostile yet.
 */
const SPAWNERS = {
	ambient: [{ type: "minecraft:bat", maxCount: 8, minCount: 8, weight: 10 }],
	axolotls: [],
	creature: [
		{ type: "minecraft:wolf", maxCount: 4, minCount: 2, weight: 12 },
		{ type: "minecraft:rabbit", maxCount: 3, minCount: 2, weight: 4 },
	],
	misc: [],
	monster: [
		{ type: "minecraft:witch", maxCount: 2, minCount: 1, weight: 40 },
		{ type: "minecraft:spider", maxCount: 4, minCount: 4, weight: 110 },
		{ type: "minecraft:zombie", maxCount: 4, minCount: 4, weight: 100 },
		{ type: "minecraft:skeleton", maxCount: 4, minCount: 4, weight: 110 },
		{ type: "minecraft:creeper", maxCount: 4, minCount: 4, weight: 100 },
		{ type: "minecraft:enderman", maxCount: 4, minCount: 1, weight: 10 },
		{ type: "minecraft:zombie_villager", maxCount: 1, minCount: 1, weight: 5 },
	],
	underground_water_creature: [{ type: "minecraft:glow_squid", maxCount: 6, minCount: 4, weight: 10 }],
	water_ambient: [],
	water_creature: [],
};

function main() {
	if (!fs.existsSync(SERVER_JAR)) {
		throw new Error(`no extracted server jar at ${SERVER_JAR} — run a gradle build first`);
	}
	const p = palette();
	const base = vanillaBiome("dark_forest");

	const features = base.features.map((step, i) => (i === VEGETAL_DECORATION ? VEGETATION : step));
	if (features.length !== 11) {
		throw new Error(`expected 11 decoration steps from vanilla, got ${features.length}`);
	}
	if (features[VEGETAL_DECORATION] !== VEGETATION) {
		throw new Error("vegetation step was not substituted");
	}

	const biome = {
		attributes: {
			// The mood sound is vanilla's cave cadence on purpose: it is the sound players
			// already read as "something is near", and hearing it above ground with the sun
			// up is the single cheapest way to say this forest is wrong.
			"minecraft:audio/ambient_sounds": {
				mood: {
					sound: "minecraft:ambient.cave",
					tick_delay: 4800,
					block_search_extent: 8,
					offset: 2.0,
				},
				additions: [
					{ sound: "minecraft:entity.creaking.sway", tick_chance: 0.00035 },
					{ sound: "minecraft:entity.creaking.twitch", tick_chance: 0.00022 },
					{ sound: "minecraft:entity.witch.ambient", tick_chance: 0.0001 },
				],
			},
			"minecraft:audio/background_music": {
				default: {
					sound: "minecraft:music.overworld.deep_dark",
					min_delay: 12000,
					max_delay: 24000,
				},
			},
			"minecraft:visual/ambient_particles": [
				{ particle: { type: "minecraft:ash" }, probability: 0.007 },
			],
			"minecraft:visual/fog_color": hex(p.fog),
			"minecraft:visual/sky_color": hex(p.sky),
			"minecraft:visual/water_fog_color": hex(p.waterFog),
			// Pull the fog in close. Short sightlines are the claustrophobia, and they do
			// as much work as the trees do.
			"minecraft:visual/fog_start_distance": 8.0,
			"minecraft:visual/fog_end_distance": 112.0,
			// RULING (Jesse, 2026-08-18): at night this biome is pitch black the way the
			// Warden's territory is, ordinary torches barely cut it, and a soul torch is
			// the one light that behaves normally.
			//
			// This attribute is the *look* half of that rule: every block light source in
			// Waldschatten is tinted cold, so firelight stops reading as firelight and a
			// soul flame is the only thing whose colour looks like it belongs. The
			// *gameplay* half — the darkness itself, and soul light being the only escape
			// from it — is WaldschattenDarkness, because a tint cannot tell a torch from a
			// soul torch and the rule turns on exactly that difference.
			"minecraft:visual/block_light_tint": hex(p.blockLight),
			// Even at noon the canopy owes the floor less light than open sky does.
			"minecraft:visual/sky_light_factor": 0.85,
		},
		carvers: base.carvers,
		downfall: 0.8,
		effects: {
			water_color: hex(p.water),
			foliage_color: hex(p.foliage),
			grass_color: hex(p.grass),
			dry_foliage_color: hex(p.dryFoliage),
			// No grass_color_modifier: vanilla's "dark_forest" modifier darkens grass at
			// render time, and our grass has already been darkened deliberately by the
			// palette shift. Stacking both would double-apply and put the result outside
			// anything tools/palette.js predicts.
		},
		features,
		has_precipitation: true,
		spawn_costs: {},
		spawners: SPAWNERS,
		temperature: 0.6,
	};

	const orderProblems = checkFeatureOrder(features);
	if (orderProblems.length) {
		console.error("FEATURE ORDER CYCLE — this would crash world generation:");
		for (const p of orderProblems) console.error("  - " + p);
		process.exit(1);
	}

	fs.mkdirSync(path.dirname(OUT), { recursive: true });
	fs.writeFileSync(OUT, JSON.stringify(biome, null, 2) + "\n");
	console.log(`wrote ${path.relative(path.join(__dirname, ".."), OUT)}`);
	console.log(`  fog ${hex(p.fog)}  sky ${hex(p.sky)}  water ${hex(p.water)}`);
	console.log(`  grass ${hex(p.grass)}  foliage ${hex(p.foliage)}`);
	console.log(`  ${features.flat().length} features, ${SPAWNERS.monster.length} hostile entries`);
	console.log("  feature order agrees with every vanilla biome (no cycle)");
}

main();
