#!/usr/bin/env node
// The witch hut and the five set-pieces: NBT templates plus every JSON that points at
// them (template_pool, structure, structure_set) and the two loot tables.
//
// Emitting the JSON from the same script as the NBT is the point. A jigsaw structure is
// four files that must agree on names that appear nowhere else; when they disagree the
// structure simply never generates, with no error. Generated together, they cannot.
// Usage: node tools/gen-structures.js
const fs = require("node:fs");
const path = require("node:path");
const { Template } = require("./structure-lib.js");

const NS = "waldschatten";
const RES = path.join(__dirname, "..", "src/main/resources");
const STRUCT_DIR = path.join(RES, "data", NS, "structure");
const WG = path.join(RES, "data", NS, "worldgen");

const write = (file, obj) => {
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, JSON.stringify(obj, null, 2) + "\n");
};

const LOG = `${NS}:twisted_log`;
const PLANKS = `${NS}:twisted_planks`;
const STONE = `${NS}:mossy_cairn_stone`;
const ASH = `${NS}:ashen_soil`;
const LANTERN = `${NS}:iron_lantern`;
const CHIME = `${NS}:bone_chime`;

// ===========================================================================
// The witch hut
// ===========================================================================
//
// One template rather than a jigsaw of rooms. Jigsaw children are rejected outright when
// their bounding box overlaps a placed piece, so a cottage assembled from four pieces
// spends most of its spawns as one-and-a-half pieces of cottage. The reliable shape is a
// single composed plate with jigsaw sockets pointing OUTWARD for optional sprawl, which
// is where the variation between spawns comes from.
function witchHut() {
	const t = new Template("witch_hut");

	// footprint of the cabin proper
	const X0 = 3, X1 = 9, Z0 = 3, Z1 = 9;
	const FLOOR = 2;

	// --- stilts. Uneven on purpose: the north-west corner has sunk a block into the
	// ground and the whole silhouette leans with it.
	t.box(X0, 0, Z0, X0, 0, Z0, LOG, { axis: "y" });
	for (const [x, h] of [[X0, 1], [X1, 2]]) {
		for (const z of [Z0, Z1]) {
			const height = z === Z0 ? h : h + 1;
			t.box(x, 0, z, x, height, z, LOG, { axis: "y" });
		}
	}
	t.box(6, 0, Z0, 6, 1, Z0, LOG, { axis: "y" });
	t.box(6, 0, Z1, 6, 2, Z1, LOG, { axis: "y" });

	// --- floor, and the air the rooms are carved out of
	t.box(X0, FLOOR, Z0, X1, FLOOR, Z1, PLANKS);
	t.air(X0 + 1, FLOOR + 1, Z0 + 1, X1 - 1, FLOOR + 4, Z1 - 1);

	// --- walls
	t.walls(X0, FLOOR + 1, Z0, X1, FLOOR + 4, Z1, PLANKS);
	// patched with a different wood where it has been repaired badly
	for (const [x, y, z] of [[X0, 4, 5], [X0, 5, 6], [X1, 4, 7], [6, 6, Z0], [4, 5, Z1]]) {
		t.set(x, y, z, "minecraft:spruce_planks");
	}

	// --- door, south face, with a lantern beside it and a chime above
	t.set(6, FLOOR + 1, Z1, "minecraft:dark_oak_door", { facing: "south", half: "lower", hinge: "left", open: "false", powered: "false" });
	t.set(6, FLOOR + 2, Z1, "minecraft:dark_oak_door", { facing: "south", half: "upper", hinge: "left", open: "false", powered: "false" });
	t.set(7, FLOOR + 3, Z1 + 1, LANTERN, { hanging: "false", waterlogged: "false" });
	t.set(5, FLOOR + 5, Z1, CHIME);

	// --- windows: one glazed and askew, one boarded over
	t.set(X0, FLOOR + 2, 6, "minecraft:glass_pane", { east: "false", north: "true", south: "true", waterlogged: "false", west: "false" });
	t.set(X0, FLOOR + 3, 6, "minecraft:dark_oak_trapdoor", { facing: "east", half: "top", open: "true", powered: "false", waterlogged: "false" });
	t.set(X1, FLOOR + 2, 5, "minecraft:dark_oak_trapdoor", { facing: "west", half: "bottom", open: "true", powered: "false", waterlogged: "false" });
	t.set(X1, FLOOR + 3, 5, "minecraft:dark_oak_trapdoor", { facing: "west", half: "top", open: "true", powered: "false", waterlogged: "false" });

	// --- steep roof. Stairs stepped inward twice, then a ridge: reads as thatch from the
	// ground and costs six rows of blocks.
	const roofY = FLOOR + 5;
	for (let i = 0; i < 3; i++) {
		const y = roofY + i;
		const west = X0 + i, east = X1 - i;
		for (let z = Z0 - 1 + i; z <= Z1 + 1 - i; z++) {
			t.set(west, y, z, "minecraft:dark_oak_stairs", { facing: "east", half: "bottom", shape: "straight", waterlogged: "false" });
			t.set(east, y, z, "minecraft:dark_oak_stairs", { facing: "west", half: "bottom", shape: "straight", waterlogged: "false" });
			if (i > 0) {
				for (let x = west + 1; x < east; x++) t.set(x, y - 1, z, "minecraft:dark_oak_planks");
			}
		}
	}
	for (let z = Z0 - 1; z <= Z1 + 1; z++) t.set(6, roofY + 3, z, "minecraft:dark_oak_slab", { type: "bottom", waterlogged: "false" });

	// --- chimney, crooked, smoking
	const CX = 4, CZ = 4;
	t.box(CX, FLOOR, CZ, CX, roofY + 4, CZ, "minecraft:cobblestone");
	t.set(CX, FLOOR + 1, CZ, "minecraft:campfire", { facing: "north", lit: "true", signal_fire: "false", waterlogged: "false" });
	t.set(CX, roofY + 4, CZ, "minecraft:cobblestone_wall", { east: "none", north: "none", south: "none", up: "true", waterlogged: "false", west: "none" });

	// --- interior. One cramped room: everything the brief asks for, arranged so a player
	// standing in the doorway can see all of it at once.
	t.set(6, FLOOR + 1, 5, "minecraft:cauldron");
	t.set(8, FLOOR + 1, 4, "minecraft:brewing_stand", { has_bottle_0: "true", has_bottle_1: "false", has_bottle_2: "true" });
	t.set(4, FLOOR + 1, 7, "minecraft:bookshelf");
	t.set(4, FLOOR + 1, 6, "minecraft:crafting_table");
	t.set(8, FLOOR + 1, 7, "minecraft:red_bed", { facing: "north", occupied: "false", part: "head" });
	t.set(8, FLOOR + 1, 8, "minecraft:red_bed", { facing: "north", occupied: "false", part: "foot" });
	t.chest(7, FLOOR + 1, 4, "south", `${NS}:chests/witch_hut`);
	t.set(5, FLOOR + 1, 8, "minecraft:candle", { candles: "3", lit: "true", waterlogged: "false" });
	t.set(4, FLOOR + 1, 8, "minecraft:flower_pot");
	t.set(8, FLOOR + 1, 6, "minecraft:barrel", { facing: "up", open: "false" });
	// herbs drying under the rafters
	for (const z of [5, 6, 7]) t.set(6, FLOOR + 5, z, `${NS}:witch_hazel_bush`);

	// --- the yard
	// cauldron over a fire, out front, where you see it before you see the door
	t.set(4, 0, 11, "minecraft:cauldron");
	t.set(4, 0, 12, "minecraft:campfire", { facing: "north", lit: "false", signal_fire: "false", waterlogged: "false" });
	// a path of old stones to the door
	for (let z = Z1 + 1; z <= 12; z++) { t.set(6, 0, z, STONE); if (z % 2 === 0) t.set(7, 0, z, STONE); }
	// the herb garden, fenced, growing what she brews with
	t.walls(0, 0, 0, 3, 0, 4, "minecraft:oak_fence", { east: "false", north: "false", south: "false", waterlogged: "false", west: "false" });
	t.box(1, 0, 1, 2, 0, 3, ASH);
	t.set(1, 1, 1, `${NS}:nightshade_plant`);
	t.set(2, 1, 2, `${NS}:mandrake_root`);
	t.set(1, 1, 3, `${NS}:nightshade_plant`);
	t.set(2, 1, 1, `${NS}:witch_hazel_bush`);
	// a scarecrow of sorts, and the bones of something
	t.box(11, 0, 2, 11, 2, 2, LOG, { axis: "y" });
	t.set(11, 3, 2, "minecraft:carved_pumpkin", { facing: "south" });
	t.set(10, 2, 2, LOG, { axis: "x" });
	t.set(12, 2, 2, LOG, { axis: "x" });
	t.set(11, 0, 5, "minecraft:bone_block", { axis: "x" });
	t.set(0, 0, 8, STONE);
	t.set(1, 0, 8, STONE);
	// rain barrel
	t.set(11, 0, 9, "minecraft:cauldron");

	// --- outward sockets. This is where between-spawn variation comes from: an annex may
	// or may not attach, and there are several it could be.
	t.jigsaw(12, 0, 6, { name: `${NS}:hut_side`, target: `${NS}:annex`, pool: `${NS}:witch_hut/annex`, orientation: "east_up" });
	t.jigsaw(0, 0, 10, { name: `${NS}:hut_side`, target: `${NS}:annex`, pool: `${NS}:witch_hut/annex`, orientation: "west_up" });

	return t;
}

/** Small optional outbuildings that may attach to the hut's sockets. */
function annexWoodpile() {
	const t = new Template("annex_woodpile");
	t.jigsaw(0, 0, 2, { name: `${NS}:annex`, target: `${NS}:hut_side`, pool: "minecraft:empty", orientation: "west_up" });
	t.box(1, 0, 1, 3, 0, 3, "minecraft:coarse_dirt");
	for (let y = 1; y <= 2; y++) for (let x = 1; x <= 3; x++) t.set(x, y, 2, LOG, { axis: "x" });
	t.set(2, 3, 2, LOG, { axis: "x" });
	t.set(3, 1, 1, LOG, { axis: "y" });   // the chopping block
	return t;
}

function annexGraves() {
	const t = new Template("annex_graves");
	t.jigsaw(0, 0, 2, { name: `${NS}:annex`, target: `${NS}:hut_side`, pool: "minecraft:empty", orientation: "west_up" });
	t.box(1, 0, 1, 4, 0, 3, ASH);
	for (const z of [1, 3]) {
		t.set(2, 1, z, STONE);
		t.set(2, 2, z, "minecraft:cobblestone_wall", { east: "none", north: "none", south: "none", up: "true", waterlogged: "false", west: "none" });
		t.set(4, 1, z, `${NS}:gnarled_roots`);
	}
	t.set(3, 1, 2, "minecraft:skeleton_skull", { powered: "false", rotation: "4" });
	return t;
}

// ===========================================================================
// Set-pieces: small, frequent, no two the same kind of wrong
// ===========================================================================
function gallows() {
	const t = new Template("gallows");
	t.box(2, 0, 2, 6, 0, 6, ASH);
	t.box(3, 1, 3, 3, 5, 3, LOG, { axis: "y" });     // post
	for (let x = 3; x <= 5; x++) t.set(x, 5, 3, LOG, { axis: "x" });  // beam
	t.set(5, 4, 3, "minecraft:chain", { axis: "y", waterlogged: "false" });
	t.set(5, 3, 3, "minecraft:chain", { axis: "y", waterlogged: "false" });
	t.set(3, 1, 4, STONE);
	t.set(2, 1, 2, `${NS}:gnarled_roots`);
	t.set(6, 1, 5, "minecraft:bone_block", { axis: "z" });
	t.set(4, 1, 5, `${NS}:thorn_vine`);
	return t;
}

function stoneCircle() {
	const t = new Template("stone_circle");
	t.box(1, 0, 1, 9, 0, 9, "minecraft:podzol", { snowy: "false" });
	t.box(4, 0, 4, 6, 0, 6, ASH);
	// five monoliths of uneven height — a circle that has been standing a long time
	const at = [[5, 1], [8, 4], [2, 4], [7, 8], [3, 8]];
	at.forEach(([x, z], i) => {
		const h = 2 + (i % 3);
		t.box(x, 1, z, x, h, z, STONE);
		if (i % 2 === 0) t.set(x, h + 1, z, "minecraft:cobblestone_wall", { east: "none", north: "none", south: "none", up: "true", waterlogged: "false", west: "none" });
	});
	t.set(5, 1, 5, "minecraft:sculk");      // the faint wrongness at the centre
	t.set(5, 1, 4, `${NS}:mandrake_root`);
	return t;
}

function grimmShrine() {
	const t = new Template("grimm_shrine");
	t.box(1, 0, 1, 5, 0, 5, "minecraft:podzol", { snowy: "false" });
	t.box(2, 1, 2, 4, 1, 4, STONE);
	t.set(3, 2, 3, STONE);
	t.set(3, 3, 3, "minecraft:skeleton_skull", { powered: "false", rotation: "8" });
	t.set(2, 2, 2, "minecraft:candle", { candles: "2", lit: "true", waterlogged: "false" });
	t.set(4, 2, 4, "minecraft:candle", { candles: "1", lit: "true", waterlogged: "false" });
	t.set(4, 2, 2, "minecraft:bone_block", { axis: "y" });
	t.set(2, 2, 4, `${NS}:witch_hazel_bush`);
	t.set(1, 1, 3, `${NS}:gnarled_roots`);
	t.set(5, 1, 3, `${NS}:gnarled_roots`);
	return t;
}

function sunkenCottage() {
	const t = new Template("sunken_cottage");
	// a cabin that the ground has taken back: two walls left, the rest a suggestion
	t.box(1, 0, 1, 7, 0, 7, "minecraft:coarse_dirt");
	t.air(2, 1, 2, 6, 3, 6);
	for (let x = 1; x <= 7; x++) { t.set(x, 1, 1, PLANKS); if (x % 3 !== 0) t.set(x, 2, 1, PLANKS); }
	for (let z = 1; z <= 5; z++) { t.set(1, 1, z, PLANKS); if (z % 2 === 0) t.set(1, 2, z, PLANKS); }
	t.set(1, 3, 1, LOG, { axis: "y" });
	t.set(7, 1, 2, "minecraft:dark_oak_stairs", { facing: "north", half: "bottom", shape: "straight", waterlogged: "false" });
	// cobwebs, thick, and something worth the walk
	for (const [x, y, z] of [[2, 2, 2], [3, 2, 2], [2, 2, 3], [5, 2, 5], [4, 3, 3], [6, 2, 2]]) {
		t.set(x, y, z, "minecraft:cobweb");
	}
	t.chest(3, 1, 4, "south", `${NS}:chests/sunken_cottage`);
	t.set(5, 1, 3, "minecraft:cauldron");
	t.set(4, 1, 6, `${NS}:gnarled_roots`);
	t.set(6, 1, 6, `${NS}:thorn_vine`);
	return t;
}

function fairyRing() {
	const t = new Template("fairy_ring");
	t.box(1, 0, 1, 7, 0, 7, "minecraft:podzol", { snowy: "false" });
	// the ring itself
	const ring = [[4, 1], [6, 2], [7, 4], [6, 6], [4, 7], [2, 6], [1, 4], [2, 2]];
	ring.forEach(([x, z], i) => t.set(x, 1, z, i % 3 === 0 ? "minecraft:red_mushroom" : "minecraft:brown_mushroom"));
	// the glow at the centre is a real firefly bush rather than a particle system: it
	// only shows at night, which is exactly the behaviour the brief asked for, and it
	// costs no client code at all.
	t.set(4, 1, 4, "minecraft:firefly_bush");
	t.set(3, 1, 5, `${NS}:witch_hazel_bush`);
	return t;
}

// ===========================================================================
function main() {
	const templates = [
		{ t: witchHut(), dir: path.join(STRUCT_DIR, "witch_hut") },
		{ t: annexWoodpile(), dir: path.join(STRUCT_DIR, "witch_hut") },
		{ t: annexGraves(), dir: path.join(STRUCT_DIR, "witch_hut") },
		{ t: gallows(), dir: path.join(STRUCT_DIR, "set_piece") },
		{ t: stoneCircle(), dir: path.join(STRUCT_DIR, "set_piece") },
		{ t: grimmShrine(), dir: path.join(STRUCT_DIR, "set_piece") },
		{ t: sunkenCottage(), dir: path.join(STRUCT_DIR, "set_piece") },
		{ t: fairyRing(), dir: path.join(STRUCT_DIR, "set_piece") },
	];
	for (const { t, dir } of templates) {
		const r = t.emit(dir);
		console.log(`  ${t.name.padEnd(18)} ${r.size.join("x").padEnd(12)} ${String(r.blocks).padStart(5)} blocks, ${r.palette} states`);
	}

	// --- template pools -----------------------------------------------------
	const pool = (fallback, elements) => ({ fallback, elements });
	const single = (loc, projection = "rigid") => ({
		weight: 1,
		element: {
			element_type: "minecraft:single_pool_element",
			location: loc,
			processors: "minecraft:empty",
			projection,
		},
	});

	write(path.join(WG, "template_pool", "witch_hut", "start.json"),
		pool("minecraft:empty", [single(`${NS}:witch_hut/witch_hut`)]));
	// "empty" as fallback with weight on the real annexes means the annex is genuinely
	// optional — some huts get one, some do not, which is the variation the brief wants.
	write(path.join(WG, "template_pool", "witch_hut", "annex.json"),
		pool("minecraft:empty", [
			single(`${NS}:witch_hut/annex_woodpile`),
			single(`${NS}:witch_hut/annex_graves`),
		]));

	const SET_PIECES = ["gallows", "stone_circle", "grimm_shrine", "sunken_cottage", "fairy_ring"];
	for (const name of SET_PIECES) {
		write(path.join(WG, "template_pool", "set_piece", `${name}.json`),
			pool("minecraft:empty", [single(`${NS}:set_piece/${name}`)]));
	}

	// --- structures ---------------------------------------------------------
	write(path.join(WG, "structure", "witch_hut.json"), {
		type: "minecraft:jigsaw",
		biomes: `#${NS}:has_structure/witch_hut`,
		start_pool: `${NS}:witch_hut/start`,
		size: 2,
		max_distance_from_center: 48,
		start_height: { absolute: 0 },
		project_start_to_heightmap: "WORLD_SURFACE_WG",
		step: "surface_structures",
		terrain_adaptation: "beard_thin",
		use_expansion_hack: false,
		// The inhabitant. Vanilla's swamp hut does exactly this, and it beats baking an
		// entity into the NBT: a spawn override re-populates the hut if the witch is
		// killed before the player ever finds it.
		spawn_overrides: {
			monster: {
				bounding_box: "piece",
				spawns: [{ type: "minecraft:witch", weight: 1, minCount: 1, maxCount: 1 }],
			},
			creature: {
				bounding_box: "piece",
				spawns: [{ type: "minecraft:cat", weight: 1, minCount: 1, maxCount: 1 }],
			},
		},
	});

	for (const name of SET_PIECES) {
		write(path.join(WG, "structure", `${name}.json`), {
			type: "minecraft:jigsaw",
			biomes: `#${NS}:has_structure/set_pieces`,
			start_pool: `${NS}:set_piece/${name}`,
			size: 1,
			max_distance_from_center: 16,
			start_height: { absolute: 0 },
			project_start_to_heightmap: "WORLD_SURFACE_WG",
			step: "surface_structures",
			terrain_adaptation: "beard_thin",
			use_expansion_hack: false,
			spawn_overrides: {},
		});
	}

	// --- structure sets -----------------------------------------------------
	// Rarer than vanilla's swamp hut (spacing 32 / separation 8): finding one should be
	// an event, and the biome is already rare on top of this.
	write(path.join(WG, "structure_set", "witch_huts.json"), {
		placement: { type: "minecraft:random_spread", salt: 793214077, spacing: 44, separation: 17 },
		structures: [{ structure: `${NS}:witch_hut`, weight: 1 }],
	});

	// The set-pieces share one set so they compete for the same slots — that keeps them
	// from stacking on top of each other, and tight spacing makes the wood feel inhabited.
	write(path.join(WG, "structure_set", "set_pieces.json"), {
		placement: {
			type: "minecraft:random_spread",
			salt: 481357911,
			spacing: 14,
			separation: 6,
			// Never right on the hut's doorstep: the hut should be found on its own terms.
			exclusion_zone: { chunk_count: 4, other_set: `${NS}:witch_huts` },
		},
		structures: [
			{ structure: `${NS}:fairy_ring`, weight: 6 },
			{ structure: `${NS}:stone_circle`, weight: 4 },
			{ structure: `${NS}:gallows`, weight: 3 },
			{ structure: `${NS}:grimm_shrine`, weight: 3 },
			{ structure: `${NS}:sunken_cottage`, weight: 2 },
		],
	});

	// --- self-check: every pool element must name a template file that exists ---
	const problems = [];
	const poolFiles = [];
	const walk = (d) => {
		if (!fs.existsSync(d)) return;
		for (const e of fs.readdirSync(d, { withFileTypes: true })) {
			const p = path.join(d, e.name);
			if (e.isDirectory()) walk(p); else if (e.name.endsWith(".json")) poolFiles.push(p);
		}
	};
	walk(path.join(WG, "template_pool"));
	for (const f of poolFiles) {
		const p = JSON.parse(fs.readFileSync(f, "utf8"));
		for (const el of p.elements) {
			const loc = el.element.location;
			const nbt = path.join(STRUCT_DIR, loc.slice(NS.length + 1) + ".nbt");
			if (!fs.existsSync(nbt)) problems.push(`${path.basename(f)} references missing template ${loc} (${nbt})`);
		}
	}
	// every structure named in a set must exist, and must be tagged into a biome
	for (const setFile of fs.readdirSync(path.join(WG, "structure_set"))) {
		const s = JSON.parse(fs.readFileSync(path.join(WG, "structure_set", setFile), "utf8"));
		for (const entry of s.structures) {
			const sf = path.join(WG, "structure", entry.structure.slice(NS.length + 1) + ".json");
			if (!fs.existsSync(sf)) problems.push(`${setFile} references missing structure ${entry.structure}`);
		}
	}
	if (problems.length) {
		console.error("STRUCTURE CHECK FAILED:");
		for (const p of problems) console.error("  - " + p);
		process.exit(1);
	}
	console.log("cross-check: every pool element resolves to a template, every set entry to a structure");
}

main();
