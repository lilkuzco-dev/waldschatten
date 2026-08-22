#!/usr/bin/env node
// Configured and placed features for Waldschatten's floor and canopy.
//
// NOTE FOR ANYONE PORTING 1.21-ERA WORLDGEN: `minecraft:random_patch` DOES NOT EXIST in
// 26.2. Ground scatter is a `minecraft:simple_block` configured feature plus placement
// modifiers (count / in_square / heightmap / block_predicate_filter) on the placed side —
// verified against the 26.2 server jar, where no configured feature uses random_patch and
// vanilla's own dead bush and fire patches are built this way.
//
// The last thing this script does is check its own output: every feature the biome asks
// for must exist, and every placed feature must point at a configured feature that exists.
// A typo in a feature id is otherwise a datapack error at world load, or worse, a feature
// that quietly never places.
// Usage: node tools/gen-worldgen.js
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..", "src/main/resources/data/waldschatten/worldgen");
const NS = "waldschatten";
const CF = (n) => path.join(ROOT, "configured_feature", `${n}.json`);
const PF = (n) => path.join(ROOT, "placed_feature", `${n}.json`);

const write = (file, obj) => {
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, JSON.stringify(obj, null, 2) + "\n");
};

const state = (name, props) => (props ? { Name: name, Properties: props } : { Name: name });
const simple = (name, props) => ({ type: "minecraft:simple_state_provider", state: state(name, props) });
const weighted = (entries) => ({
	type: "minecraft:weighted_state_provider",
	entries: entries.map(([name, weight, props]) => ({ data: state(name, props), weight })),
});
const leaves = (name) => simple(name, { distance: "7", persistent: "false", waterlogged: "false" });

/** Vanilla's below-trunk rule, so a tree grown on stone still gets dirt under it. */
const BELOW_TRUNK = {
	type: "minecraft:rule_based_state_provider",
	rules: [{
		if_true: { type: "minecraft:not", predicate: { type: "minecraft:matching_block_tag", tag: "minecraft:cannot_replace_below_tree_trunk" } },
		then: simple("minecraft:dirt"),
	}],
};

const configured = {};
const placed = {};

// ---------------------------------------------------------------------------
// The canopy
// ---------------------------------------------------------------------------

// The tall one. `upwards_branching_trunk_placer` is vanilla's mangrove placer and it is
// the only vanilla placer that produces a genuinely crooked trunk with side branches —
// dark oak's placer makes a neat 2x2 pillar, which is the opposite of the brief. A high
// branch probability plus a wide, thin random-spread canopy is what makes neighbouring
// trees interlock overhead instead of standing in their own pools of daylight.
configured.twisted_tree = {
	type: "minecraft:tree",
	config: {
		below_trunk_provider: BELOW_TRUNK,
		trunk_provider: simple(`${NS}:twisted_log`, { axis: "y" }),
		foliage_provider: leaves(`${NS}:twisted_leaves`),
		trunk_placer: {
			type: "minecraft:upwards_branching_trunk_placer",
			base_height: 9,
			height_rand_a: 3,
			height_rand_b: 3,
			extra_branch_steps: { type: "minecraft:uniform", min_inclusive: 2, max_inclusive: 5 },
			extra_branch_length: { type: "minecraft:uniform", min_inclusive: 1, max_inclusive: 3 },
			place_branch_per_log_probability: 0.42,
			can_grow_through: "#minecraft:replaceable_by_trees",
		},
		foliage_placer: {
			type: "minecraft:random_spread_foliage_placer",
			foliage_height: 3,
			leaf_placement_attempts: 110,
			offset: 0,
			radius: 4,
		},
		minimum_size: { type: "minecraft:two_layers_feature_size", limit: 2, upper_size: 2 },
		ignore_vines: true,
		decorators: [
			// Roots spilling out around the base of every trunk. Cheaper and more reliable
			// than scattering roots separately and hoping they land near a tree.
			{
				type: "minecraft:place_on_ground",
				block_state_provider: simple(`${NS}:gnarled_roots`),
				height: 2,
				radius: 3,
				tries: 48,
			},
			// Someone has been through here.
			{
				type: "minecraft:attached_to_leaves",
				block_provider: simple(`${NS}:bone_chime`),
				directions: ["down"],
				exclusion_radius_xz: 3,
				exclusion_radius_y: 2,
				probability: 0.012,
				required_empty_blocks: 2,
			},
		],
	},
};

// The squat one, for variety in the stand and for what a sapling grows into when it is
// crowded. Dark oak's placer earns its place here: short, thick, heavy-crowned.
configured.twisted_tree_small = {
	type: "minecraft:tree",
	config: {
		below_trunk_provider: BELOW_TRUNK,
		trunk_provider: simple(`${NS}:twisted_log`, { axis: "y" }),
		foliage_provider: leaves(`${NS}:twisted_leaves`),
		trunk_placer: { type: "minecraft:dark_oak_trunk_placer", base_height: 5, height_rand_a: 2, height_rand_b: 1 },
		foliage_placer: { type: "minecraft:dark_oak_foliage_placer", radius: 0, offset: 0 },
		minimum_size: { type: "minecraft:three_layers_feature_size", upper_size: 2 },
		ignore_vines: true,
		decorators: [{
			type: "minecraft:place_on_ground",
			block_state_provider: simple(`${NS}:gnarled_roots`),
			height: 2,
			radius: 2,
			tries: 32,
		}],
	},
};

configured.fallen_twisted_log = {
	type: "minecraft:fallen_tree",
	config: {
		trunk_provider: simple(`${NS}:twisted_log`, { axis: "y" }),
		log_length: { type: "minecraft:uniform", min_inclusive: 4, max_inclusive: 9 },
		stump_decorators: [],
		log_decorators: [{
			type: "minecraft:attached_to_logs",
			block_provider: weighted([["minecraft:red_mushroom", 2], ["minecraft:brown_mushroom", 3]]),
			directions: ["up"],
			probability: 0.22,
		}],
	},
};

// A random_selector's `default` and each `feature` are PLACED features, not configured
// ones — the field is typed PlacedFeature even though what you want to name is a tree.
// Vanilla writes the inline `{feature, placement: []}` form for exactly this case (see
// dark_forest_vegetation's huge mushrooms), and naming a configured feature here fails at
// world load with "Unbound values in registry ... placed_feature", which does not mention
// the selector that caused it.
const inline = (id) => ({ feature: id, placement: [] });

// A tree entry must be CHECKED, never bare. Vanilla's selectors name `oak_checked` and
// friends — placed features whose only modifier is `would_survive <sapling>` — and that
// filter is the only thing standing between "a tree" and "a tree on top of a tree".
// The biome-level placement lands on OCEAN_FLOOR, which leaves and logs both count as,
// and the tree feature itself plants dirt under whatever it is given. Shipped bare in
// 0.1.0–0.1.3: sixteen attempts per chunk stacked into towers, measured in a live world
// at 83 blocks of twisted_log over 72 of ground, with dirt at y=148 — and Jesse's report
// of trees past 300. The sapling is our own, so the rule is exactly "where a player could
// plant one": dirt-family ground, never a roof, never a canopy.
const checked = (id) => ({
	feature: id,
	placement: [{
		type: "minecraft:block_predicate_filter",
		predicate: { type: "minecraft:would_survive", state: state(`${NS}:twisted_sapling`) },
	}],
});

configured.twisted_forest_vegetation = {
	type: "minecraft:random_selector",
	config: {
		default: checked(`${NS}:twisted_tree`),
		features: [
			{ chance: 0.03, feature: inline("minecraft:huge_brown_mushroom") },
			{ chance: 0.02, feature: inline("minecraft:huge_red_mushroom") },
			{ chance: 0.30, feature: checked(`${NS}:twisted_tree_small`) },
		],
	},
};

// Count 16 with no rarity filter is what vanilla dark forest uses, and it is what produces
// a closed canopy rather than a wood with sky in it. This is the biome's core promise —
// hostile spawns under a noon canopy — so it is not the number to economise on.
placed.twisted_forest_vegetation = {
	feature: `${NS}:twisted_forest_vegetation`,
	placement: [
		{ type: "minecraft:count", count: 16 },
		{ type: "minecraft:in_square" },
		{ type: "minecraft:surface_water_depth_filter", max_water_depth: 0 },
		{ type: "minecraft:heightmap", heightmap: "OCEAN_FLOOR" },
		{ type: "minecraft:biome" },
	],
};

placed.fallen_twisted_log = {
	feature: `${NS}:fallen_twisted_log`,
	placement: [
		{ type: "minecraft:rarity_filter", chance: 5 },
		{ type: "minecraft:in_square" },
		{ type: "minecraft:heightmap", heightmap: "WORLD_SURFACE_WG" },
		{ type: "minecraft:biome" },
	],
};

// ---------------------------------------------------------------------------
// The floor
// ---------------------------------------------------------------------------

/** simple_block configured feature + the standard "scatter it on suitable ground" placement. */
function scatter(id, block, { count = 3, rarity = null, spread = 7, props = null, provider = null } = {}) {
	configured[id] = { type: "minecraft:simple_block", config: { to_place: provider ?? simple(block, props) } };

	const placement = [];
	if (rarity !== null) placement.push({ type: "minecraft:rarity_filter", chance: rarity });
	placement.push(
		{ type: "minecraft:count", count },
		{ type: "minecraft:in_square" },
		{ type: "minecraft:heightmap", heightmap: "WORLD_SURFACE_WG" },
		{ type: "minecraft:biome" },
		{
			type: "minecraft:random_offset",
			xz_spread: { type: "minecraft:trapezoid", min: -spread, max: spread, plateau: 0 },
			y_spread: { type: "minecraft:trapezoid", min: -2, max: 2, plateau: 0 },
		},
		// Both halves matter: the target must be empty, and the block must be able to
		// stand there. Without `would_survive` a plant is placed and pops off on the next
		// block update, which reads as the feature never having run.
		{
			type: "minecraft:block_predicate_filter",
			predicate: {
				type: "minecraft:all_of",
				predicates: [
					{ type: "minecraft:matching_block_tag", tag: "minecraft:air" },
					{ type: "minecraft:would_survive", state: state(block, props) },
				],
			},
		},
	);
	placed[id] = { feature: `${NS}:${id}`, placement };
}

scatter("patch_gnarled_roots", `${NS}:gnarled_roots`, { count: 8 });
scatter("patch_thorn_vine", `${NS}:thorn_vine`, { count: 5, rarity: 2 });
scatter("patch_witch_hazel", `${NS}:witch_hazel_bush`, { count: 3, rarity: 2 });
scatter("patch_nightshade", `${NS}:nightshade_plant`, { count: 2, rarity: 3 });
scatter("patch_mandrake", `${NS}:mandrake_root`, { count: 1, rarity: 6 });
scatter("forest_floor_cobweb", "minecraft:cobweb", { count: 3, rarity: 4, spread: 5 });
scatter("scattered_cairn_stone", `${NS}:mossy_cairn_stone`, { count: 3, rarity: 3 });

// A loose ring of mushrooms. The tight xz spread is what turns four scattered mushrooms
// into something that reads as a ring somebody's grandmother warned them about.
configured.mushroom_ring = {
	type: "minecraft:simple_block",
	config: { to_place: weighted([["minecraft:red_mushroom", 2], ["minecraft:brown_mushroom", 3]]) },
};
placed.mushroom_ring = {
	feature: `${NS}:mushroom_ring`,
	placement: [
		{ type: "minecraft:rarity_filter", chance: 3 },
		{ type: "minecraft:count", count: 7 },
		{ type: "minecraft:in_square" },
		{ type: "minecraft:heightmap", heightmap: "WORLD_SURFACE_WG" },
		{ type: "minecraft:biome" },
		{
			type: "minecraft:random_offset",
			xz_spread: { type: "minecraft:trapezoid", min: -3, max: 3, plateau: 0 },
			y_spread: { type: "minecraft:trapezoid", min: -1, max: 1, plateau: 0 },
		},
		{
			type: "minecraft:block_predicate_filter",
			predicate: {
				type: "minecraft:all_of",
				predicates: [
					{ type: "minecraft:matching_block_tag", tag: "minecraft:air" },
					{ type: "minecraft:would_survive", state: state("minecraft:brown_mushroom") },
				],
			},
		},
	],
};

// The floor itself: podzol and coarse dirt over the grass, so the biome is not a dark
// canopy standing on cheerful green. Laid before anything is planted on it.
configured.forest_floor = {
	type: "minecraft:disk",
	config: {
		half_height: 1,
		radius: { type: "minecraft:uniform", min_inclusive: 3, max_inclusive: 7 },
		target: { type: "minecraft:matching_blocks", blocks: ["minecraft:grass_block", "minecraft:dirt"] },
		state_provider: {
			type: "minecraft:rule_based_state_provider",
			fallback: weighted([["minecraft:podzol", 5, { snowy: "false" }], ["minecraft:coarse_dirt", 3]]),
			rules: [],
		},
	},
};
placed.forest_floor = {
	feature: `${NS}:forest_floor`,
	placement: [
		{ type: "minecraft:count", count: 10 },
		{ type: "minecraft:in_square" },
		{ type: "minecraft:heightmap", heightmap: "WORLD_SURFACE_WG" },
		{ type: "minecraft:biome" },
	],
};

// Ash where something burned, or where something is buried.
configured.ashen_soil_patch = {
	type: "minecraft:disk",
	config: {
		half_height: 1,
		radius: { type: "minecraft:uniform", min_inclusive: 1, max_inclusive: 3 },
		target: { type: "minecraft:matching_blocks", blocks: ["minecraft:grass_block", "minecraft:dirt", "minecraft:podzol", "minecraft:coarse_dirt"] },
		state_provider: simple(`${NS}:ashen_soil`),
	},
};
placed.ashen_soil_patch = {
	feature: `${NS}:ashen_soil_patch`,
	placement: [
		{ type: "minecraft:rarity_filter", chance: 6 },
		{ type: "minecraft:count", count: 2 },
		{ type: "minecraft:in_square" },
		{ type: "minecraft:heightmap", heightmap: "WORLD_SURFACE_WG" },
		{ type: "minecraft:biome" },
	],
};

// ---------------------------------------------------------------------------
function main() {
	for (const [id, obj] of Object.entries(configured)) write(CF(id), obj);
	for (const [id, obj] of Object.entries(placed)) write(PF(id), obj);

	// --- self-check ---------------------------------------------------------
	const biomePath = path.join(__dirname, "..", "src/main/resources/data/waldschatten/worldgen/biome/waldschatten.json");
	const problems = [];

	// inline placed-feature references inside a random_selector must resolve too — this
	// is the class of mistake that produced "Unbound values in registry placed_feature"
	const checkInline = (node, where) => {
		if (!node || typeof node !== "object") return;
		if (typeof node.feature === "string" && node.feature.startsWith(`${NS}:`)) {
			const name = node.feature.slice(NS.length + 1);
			if (!fs.existsSync(CF(name)) && !fs.existsSync(PF(name))) {
				problems.push(`${where} references ${node.feature}, which is neither a configured nor a placed feature`);
			}
		}
		for (const v of Object.values(node)) {
			if (Array.isArray(v)) v.forEach((e) => checkInline(e, where));
			else checkInline(v, where);
		}
	};
	for (const [id, cf] of Object.entries(configured)) checkInline(cf, `configured_feature ${id}`);

	// every placed feature must point at a configured feature that exists
	for (const [id, pf] of Object.entries(placed)) {
		const ref = pf.feature;
		if (ref.startsWith(`${NS}:`) && !fs.existsSync(CF(ref.slice(NS.length + 1)))) {
			problems.push(`placed_feature ${id} references missing configured_feature ${ref}`);
		}
	}
	// every waldschatten feature the biome asks for must exist as a placed feature
	if (fs.existsSync(biomePath)) {
		const biome = JSON.parse(fs.readFileSync(biomePath, "utf8"));
		for (const step of biome.features) {
			for (const ref of step) {
				if (ref.startsWith(`${NS}:`) && !fs.existsSync(PF(ref.slice(NS.length + 1)))) {
					problems.push(`biome asks for missing placed_feature ${ref}`);
				}
			}
		}
		// and the reverse: a feature written but never referenced is dead weight
		const referenced = new Set(biome.features.flat());
		for (const id of Object.keys(placed)) {
			if (!referenced.has(`${NS}:${id}`)) {
				problems.push(`placed_feature ${id} is not referenced by the biome (dead feature)`);
			}
		}
	} else {
		problems.push(`biome not generated yet at ${biomePath} — run tools/gen-biome.js`);
	}

	if (problems.length) {
		console.error("WORLDGEN CHECK FAILED:");
		for (const p of problems) console.error("  - " + p);
		process.exit(1);
	}

	console.log(`wrote ${Object.keys(configured).length} configured + ${Object.keys(placed).length} placed features`);
	console.log("cross-check: every biome feature exists, every placed feature resolves, no dead features");
}

main();
