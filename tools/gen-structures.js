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

// Deterministic noise, so "weathered and patchy" regenerates byte-identically and a diff
// means a real change rather than a reroll.
function mulberry32(seed) {
	return function () {
		seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
		let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
		t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
		return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
	};
}

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
	const rand = mulberry32(0x5ca1ab1e);

	// Footprint 13x13; the shack itself is the 7x7 box inside it.
	const X0 = 3, X1 = 9, Z0 = 3, Z1 = 9;
	const FLOOR = 2, WALL_TOP = 6, EAVE = 7;

	const MOSSY = "minecraft:mossy_cobblestone";
	const COBBLE = "minecraft:cobblestone";
	const SPRUCE = "minecraft:spruce_planks";
	const DARK = "minecraft:dark_oak_planks";
	const stair = (facing, half = "bottom") =>
		["minecraft:dark_oak_stairs", { facing, half, shape: "straight", waterlogged: "false" }];
	const trapdoor = (facing, half, open) =>
		["minecraft:dark_oak_trapdoor", { facing, half, open, powered: "false", waterlogged: "false" }];
	const fence = { east: "false", north: "false", south: "false", waterlogged: "false", west: "false" };

	// --- 1. a stone plinth, so the shack sits on something rather than floating -------
	// Ragged on purpose: this is a footing that has been sinking for a century, not a slab.
	for (let x = X0 - 1; x <= X1 + 1; x++) {
		for (let z = Z0 - 1; z <= Z1 + 1; z++) {
			const edge = x === X0 - 1 || x === X1 + 1 || z === Z0 - 1 || z === Z1 + 1;
			if (edge && rand() < 0.75) {
				t.set(x, 0, z, rand() < 0.65 ? MOSSY : COBBLE);
			}
		}
	}

	// --- 2. stilts, none of them the same height -------------------------------------
	// The lean is the whole silhouette. Corner heights differ by up to two blocks, and the
	// floor above them is flat, so the shack reads as having settled unevenly.
	const stilts = [[X0, Z0, 1], [X1, Z0, 2], [X0, Z1, 2], [X1, Z1, 0], [6, Z1, 1], [6, Z0, 2]];
	for (const [x, z, top] of stilts) {
		t.box(x, 0, z, x, top, z, LOG, { axis: "y" });
		if (rand() < 0.6) t.set(x, 0, z, MOSSY);            // stone pad under the post
	}
	// spindly fence props under the sagging side
	t.box(X0 + 1, 0, Z1, X0 + 1, 1, Z1, "minecraft:oak_fence", fence);
	t.box(X1 - 1, 0, Z0, X1 - 1, 1, Z0, "minecraft:oak_fence", fence);
	// cobwebs in the crawlspace
	for (const [x, z] of [[4, 4], [8, 8], [5, 8]]) t.set(x, 1, z, "minecraft:cobweb");

	// --- 3. floor and the room carved out above it -----------------------------------
	t.box(X0, FLOOR, Z0, X1, FLOOR, Z1, PLANKS);
	t.air(X0 + 1, FLOOR + 1, Z0 + 1, X1 - 1, EAVE - 1, Z1 - 1);

	// --- 4. corner posts, then walls between them ------------------------------------
	// Framing the walls with logs is what stops a 7x7 box reading as a box.
	for (const [x, z] of [[X0, Z0], [X1, Z0], [X0, Z1], [X1, Z1]]) {
		t.box(x, FLOOR + 1, z, x, EAVE, z, LOG, { axis: "y" });
	}
	for (let y = FLOOR + 1; y <= WALL_TOP; y++) {
		for (let x = X0; x <= X1; x++) {
			for (let z = Z0; z <= Z1; z++) {
				if (x !== X0 && x !== X1 && z !== Z0 && z !== Z1) continue;   // interior
				if ((x === X0 || x === X1) && (z === Z0 || z === Z1)) continue; // corner post
				// Lowest course is damp stone; above it, boards with a patched repair here
				// and there, and two gaps where boards have come away entirely.
				const r = rand();
				if (y === FLOOR + 1 && r < 0.45) t.set(x, y, z, r < 0.3 ? MOSSY : COBBLE);
				else if (r > 0.965) { /* a missing board — leave it open */ }
				else if (r > 0.80) t.set(x, y, z, SPRUCE);
				else t.set(x, y, z, PLANKS);
			}
		}
	}
	// a sagging lintel beam across the front
	for (let x = X0; x <= X1; x++) t.set(x, EAVE, x % 3 === 0 ? Z1 : Z1, LOG, { axis: "x" });

	// --- 5. door, shutters, boarded window -------------------------------------------
	t.set(6, FLOOR + 1, Z1, "minecraft:dark_oak_door", { facing: "south", half: "lower", hinge: "left", open: "false", powered: "false" });
	t.set(6, FLOOR + 2, Z1, "minecraft:dark_oak_door", { facing: "south", half: "upper", hinge: "left", open: "false", powered: "false" });
	// west window: glazed, with one shutter hanging open
	t.set(X0, FLOOR + 3, 6, "minecraft:glass_pane", { east: "false", north: "true", south: "true", waterlogged: "false", west: "false" });
	t.set(X0 - 1, FLOOR + 3, 5, ...trapdoor("east", "bottom", "true"));
	// east window: boarded over from outside
	t.set(X1 + 1, FLOOR + 2, 5, ...trapdoor("west", "bottom", "true"));
	t.set(X1 + 1, FLOOR + 3, 5, ...trapdoor("west", "top", "true"));
	t.set(X1, FLOOR + 3, 6, "minecraft:glass_pane", { east: "false", north: "true", south: "true", waterlogged: "false", west: "false" });

	// --- 6. ceiling, then the roof over it -------------------------------------------
	t.box(X0, EAVE, Z0, X1, EAVE, Z1, DARK);

	// A gabled roof running north-south, overhanging a block on every side. Four courses
	// of stairs stepping inward to a slab ridge, with the gable ends filled in so the
	// attic is not open to the weather.
	for (let i = 0; i <= 3; i++) {
		const y = EAVE + i;
		const west = X0 - 1 + i;
		const east = X1 + 1 - i;
		for (let z = Z0 - 1; z <= Z1 + 1; z++) {
			t.set(west, y, z, ...stair("east"));
			t.set(east, y, z, ...stair("west"));
			// gable ends: close the triangle at both ends of the run
			if (z === Z0 - 1 || z === Z1 + 1) {
				for (let x = west + 1; x < east; x++) t.set(x, y, z, DARK);
			}
		}
	}
	for (let z = Z0 - 1; z <= Z1 + 1; z++) {
		t.set(6, EAVE + 3, z, "minecraft:dark_oak_slab", { type: "bottom", waterlogged: "false" });
	}
	// weathering: mossy patches where the roof has been patched, and two holes
	for (let n = 0; n < 7; n++) {
		const i = Math.floor(rand() * 4);
		const z = Z0 - 1 + Math.floor(rand() * 9);
		const x = rand() < 0.5 ? X0 - 1 + i : X1 + 1 - i;
		t.set(x, EAVE + i, z, rand() < 0.6 ? MOSSY : COBBLE);
	}

	// --- 7. chimney, punched up through the roof -------------------------------------
	// Placed after the roof on purpose: a chimney goes through a roof, it does not stop
	// at it. Two blocks wide at the hearth, tapering to one above the ridge.
	const CX = 4, CZ = 4;
	for (let y = FLOOR; y <= EAVE + 5; y++) {
		t.set(CX, y, CZ, rand() < 0.55 ? MOSSY : COBBLE);
		if (y <= FLOOR + 2) t.set(CX + 1, y, CZ, rand() < 0.5 ? MOSSY : COBBLE);
	}
	t.set(CX, FLOOR + 1, CZ, "minecraft:campfire", { facing: "north", lit: "true", signal_fire: "false", waterlogged: "false" });
	t.set(CX, EAVE + 6, CZ, "minecraft:cobblestone_wall", { east: "none", north: "none", south: "none", up: "true", waterlogged: "false", west: "none" });

	// --- 8. porch ---------------------------------------------------------------------
	t.box(5, FLOOR, Z1 + 1, 7, FLOOR, Z1 + 2, DARK);
	t.box(5, 0, Z1 + 2, 5, FLOOR - 1, Z1 + 2, "minecraft:oak_fence", fence);
	t.box(7, 0, Z1 + 2, 7, FLOOR - 1, Z1 + 2, "minecraft:oak_fence", fence);
	t.set(5, FLOOR + 1, Z1 + 1, "minecraft:oak_fence", fence);
	t.set(7, FLOOR + 1, Z1 + 1, "minecraft:oak_fence", fence);
	t.set(6, FLOOR - 1, Z1 + 3, ...stair("south"));   // the step down

	// --- 9. inside --------------------------------------------------------------------
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
	t.set(4, FLOOR + 2, 6, `${NS}:witch_hazel_bush`);   // herbs on the table
	t.set(4, FLOOR + 2, 7, `${NS}:witch_hazel_bush`);   // and on the shelf
	t.set(8, FLOOR + 2, 6, `${NS}:witch_hazel_bush`);   // and on the barrel
	for (const [x, z] of [[5, 4], [7, 8]]) t.set(x, WALL_TOP, z, "minecraft:cobweb");

	// --- 10. the yard -----------------------------------------------------------------
	t.set(4, 0, 11, "minecraft:cauldron");
	t.set(4, 0, 12, "minecraft:campfire", { facing: "north", lit: "false", signal_fire: "false", waterlogged: "false" });
	for (let z = Z1 + 3; z <= 12; z++) { t.set(6, 0, z, STONE); if (z % 2 === 0) t.set(7, 0, z, STONE); }
	t.walls(0, 0, 0, 3, 0, 4, "minecraft:oak_fence", fence);
	t.box(1, 0, 1, 2, 0, 3, ASH);
	t.set(1, 1, 1, `${NS}:nightshade_plant`);
	t.set(2, 1, 2, `${NS}:mandrake_root`);
	t.set(1, 1, 3, `${NS}:nightshade_plant`);
	t.set(2, 1, 1, `${NS}:witch_hazel_bush`);
	// the scarecrow
	t.box(11, 0, 2, 11, 2, 2, LOG, { axis: "y" });
	t.set(11, 3, 2, "minecraft:carved_pumpkin", { facing: "south" });
	t.set(10, 2, 2, LOG, { axis: "x" });
	t.set(12, 2, 2, LOG, { axis: "x" });
	// bones, a rain barrel and a marker stone
	t.set(11, 0, 5, "minecraft:bone_block", { axis: "x" });
	t.set(0, 0, 8, STONE);
	t.set(1, 0, 8, MOSSY);
	t.set(11, 0, 9, "minecraft:cauldron");
	// A skull on a stone, not on a fence post — a fence top is not a floor and the skull
	// drops straight off it.
	t.set(0, 0, 6, STONE);
	t.set(0, 1, 6, "minecraft:skeleton_skull", { powered: "false", rotation: "8" });

	// --- 11. things that hang, placed last so nothing overwrites them -----------------
	t.set(X0 - 1, EAVE - 1, Z1 + 1, CHIME);
	t.set(X1 + 1, EAVE - 1, Z0, "minecraft:chain", { axis: "y", waterlogged: "false" });
	// The lantern stands on its own stone where the path starts, clear of the porch.
	t.set(8, 0, 12, MOSSY);
	t.set(8, 1, 12, LANTERN, { hanging: "false", waterlogged: "false" });

	// --- 12. outward sockets for the optional annexes ---------------------------------
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
	// NOTE: `fallback` is the pool used when jigsaw depth is EXHAUSTED, not a weighted
	// "place nothing" option — so with these two elements an annex attaches to each socket
	// whenever it physically fits, which on flat ground is nearly always. The variation
	// between spawns is therefore WHICH annex, not whether there is one. To make them
	// genuinely optional, add a weighted `minecraft:empty_pool_element` entry here.
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
