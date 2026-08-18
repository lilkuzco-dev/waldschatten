#!/usr/bin/env node
// Generates every per-block resource: blockstate, block model, item model, item
// definition, loot table, and the en_us lang entries.
//
// Sixteen blocks times five files is eighty files that must all agree with each other and
// with WaldschattenBlocks.java. Hand-written, one of them is wrong and the symptom is a
// checkerboard or a block that drops nothing. Generated from one table, they cannot
// disagree — and per CLAUDE.md rule 4 the table is the thing to edit, never the output.
// Usage: node tools/gen-assets.js
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..", "src/main/resources");
const NS = "waldschatten";

const A = (...p) => path.join(ROOT, "assets", NS, ...p);
const D = (...p) => path.join(ROOT, "data", NS, ...p);

// kind: how the block is modelled. name: the display name for en_us.
// tex: texture basename(s); defaults to the block's own id.
const BLOCKS = {
	twisted_log:            { kind: "pillar", name: "Twisted Log", side: "twisted_log", end: "twisted_log_top" },
	stripped_twisted_log:   { kind: "pillar", name: "Stripped Twisted Log", side: "stripped_twisted_log", end: "stripped_twisted_log_top" },
	twisted_wood:           { kind: "pillar", name: "Twisted Wood", side: "twisted_log", end: "twisted_log" },
	stripped_twisted_wood:  { kind: "pillar", name: "Stripped Twisted Wood", side: "stripped_twisted_log", end: "stripped_twisted_log" },
	twisted_planks:         { kind: "cube", name: "Twisted Planks" },
	twisted_leaves:         { kind: "leaves", name: "Twisted Leaves" },
	twisted_sapling:        { kind: "cross", name: "Twisted Sapling" },
	gnarled_roots:          { kind: "carpet", name: "Gnarled Roots" },
	witch_hazel_bush:       { kind: "cross", name: "Witch Hazel Bush" },
	nightshade_plant:       { kind: "cross", name: "Nightshade" },
	mandrake_root:          { kind: "cross", name: "Mandrake Root" },
	bone_chime:             { kind: "cross", name: "Bone Chime" },
	ashen_soil:             { kind: "cube", name: "Ashen Soil" },
	mossy_cairn_stone:      { kind: "cube", name: "Mossy Cairn Stone" },
	iron_lantern:           { kind: "lantern", name: "Iron Lantern" },
	thorn_vine:             { kind: "cross", name: "Thorn Vine" },
};

const write = (file, obj) => {
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, JSON.stringify(obj, null, 2) + "\n");
};
const tex = (name) => `${NS}:block/${name}`;

// ---------------------------------------------------------------------------
// A lantern's own model rather than vanilla's template_lantern: that template maps a
// texture laid out as an atlas of six separate regions, and feeding it a plain 16x16
// placeholder produces a lantern with the wrong slice of image on every face. Its own
// small box takes the texture as-is and is honest about being a placeholder.
// ---------------------------------------------------------------------------
function lanternModel(name, hanging) {
	const y0 = hanging ? 1 : 0;
	return {
		parent: "minecraft:block/block",
		textures: { particle: tex(name), all: tex(name) },
		elements: [
			{
				from: [5, y0, 5],
				to: [11, y0 + 7, 11],
				faces: Object.fromEntries(["down", "up", "north", "south", "west", "east"]
					.map((f) => [f, { uv: [0, 0, 16, 16], texture: "#all" }])),
			},
			{	// the bail: a chain up to the block above, or a hoop on top
				from: [7, y0 + 7, 8],
				to: [9, y0 + (hanging ? 9 : 8), 8],
				faces: { north: { uv: [0, 0, 16, 16], texture: "#all" }, south: { uv: [0, 0, 16, 16], texture: "#all" } },
			},
		],
	};
}

function blockstate(id, b) {
	switch (b.kind) {
		case "pillar":
			return {
				variants: {
					"axis=y": { model: `${NS}:block/${id}` },
					"axis=z": { model: `${NS}:block/${id}`, x: 90 },
					"axis=x": { model: `${NS}:block/${id}`, x: 90, y: 90 },
				},
			};
		case "lantern":
			return {
				variants: {
					"hanging=false": { model: `${NS}:block/${id}` },
					"hanging=true": { model: `${NS}:block/${id}_hanging` },
				},
			};
		default:
			return { variants: { "": { model: `${NS}:block/${id}` } } };
	}
}

function blockModel(id, b) {
	switch (b.kind) {
		case "pillar":
			return { parent: "minecraft:block/cube_column", textures: { end: tex(b.end), side: tex(b.side) } };
		case "cube":
			return { parent: "minecraft:block/cube_all", textures: { all: tex(id) } };
		case "leaves":
			return { parent: "minecraft:block/leaves", textures: { all: tex(id) } };
		case "cross":
			return { parent: "minecraft:block/cross", textures: { cross: tex(id) } };
		case "carpet":
			return { parent: "minecraft:block/carpet", textures: { wool: tex(id) } };
		case "lantern":
			return lanternModel(id, false);
		default:
			throw new Error(`unknown kind ${b.kind}`);
	}
}

/**
 * Flat-model blocks (cross, carpet) look like a folded sheet of paper when held, so they
 * get a sprite item the way vanilla saplings and flowers do. Solid blocks are shown as
 * their block model.
 */
const FLAT = new Set(["cross", "carpet"]);

function lootTable(id, b) {
	if (b.kind === "leaves") {
		// Shears or Silk Touch give the block; otherwise the tree's own sapling, rarely,
		// and sticks. Same bargain vanilla leaves offer, so the player already knows it.
		return {
			type: "minecraft:block",
			random_sequence: `${NS}:blocks/${id}`,
			pools: [
				{
					rolls: 1.0,
					bonus_rolls: 0.0,
					entries: [{
						type: "minecraft:alternatives",
						children: [
							{
								type: "minecraft:item",
								name: `${NS}:${id}`,
								conditions: [{
									condition: "minecraft:any_of",
									terms: [
										{ condition: "minecraft:match_tool", predicate: { items: "minecraft:shears" } },
										{
											condition: "minecraft:match_tool",
											predicate: { predicates: { "minecraft:enchantments": [{ enchantments: "minecraft:silk_touch", levels: { min: 1 } }] } },
										},
									],
								}],
							},
							{
								type: "minecraft:item",
								name: `${NS}:twisted_sapling`,
								conditions: [{ condition: "minecraft:survives_explosion" },
									{ condition: "minecraft:table_bonus", enchantment: "minecraft:fortune", chances: [0.05, 0.0625, 0.083333336, 0.1] }],
							},
						],
					}],
				},
				{
					rolls: 1.0,
					bonus_rolls: 0.0,
					conditions: [{
						condition: "minecraft:inverted",
						term: {
							condition: "minecraft:any_of",
							terms: [
								{ condition: "minecraft:match_tool", predicate: { items: "minecraft:shears" } },
								{
									condition: "minecraft:match_tool",
									predicate: { predicates: { "minecraft:enchantments": [{ enchantments: "minecraft:silk_touch", levels: { min: 1 } }] } },
								},
							],
						},
					}],
					entries: [{
						type: "minecraft:item",
						name: "minecraft:stick",
						functions: [
							{ function: "minecraft:set_count", count: { type: "minecraft:uniform", min: 1.0, max: 2.0 } },
							{ function: "minecraft:explosion_decay" },
						],
						conditions: [{ condition: "minecraft:table_bonus", enchantment: "minecraft:fortune", chances: [0.02, 0.022222223, 0.025, 0.033333335, 0.1] }],
					}],
				},
			],
		};
	}
	return {
		type: "minecraft:block",
		random_sequence: `${NS}:blocks/${id}`,
		pools: [{
			rolls: 1.0,
			bonus_rolls: 0.0,
			conditions: [{ condition: "minecraft:survives_explosion" }],
			entries: [{ type: "minecraft:item", name: `${NS}:${id}` }],
		}],
	};
}

function main() {
	const lang = {};
	let files = 0;

	for (const [id, b] of Object.entries(BLOCKS)) {
		write(A("blockstates", `${id}.json`), blockstate(id, b));
		write(A("models", "block", `${id}.json`), blockModel(id, b));
		files += 2;

		if (b.kind === "lantern") {
			write(A("models", "block", `${id}_hanging.json`), lanternModel(id, true));
			files++;
		}

		if (FLAT.has(b.kind)) {
			write(A("models", "item", `${id}.json`), {
				parent: "minecraft:item/generated",
				textures: { layer0: tex(id) },
			});
			write(A("items", `${id}.json`), { model: { type: "minecraft:model", model: `${NS}:item/${id}` } });
			files += 2;
		} else {
			write(A("items", `${id}.json`), { model: { type: "minecraft:model", model: `${NS}:block/${id}` } });
			files++;
		}

		write(D("loot_table", "blocks", `${id}.json`), lootTable(id, b));
		files++;

		lang[`block.${NS}.${id}`] = b.name;
	}

	// --- recipes ---------------------------------------------------------
	// Without these, twisted planks and both stripped variants exist only in creative:
	// there is no vanilla recipe that consumes a modded log, so a wood set that ships
	// without its own recipes is a wood set nobody can use.
	write(D("recipe", "twisted_planks.json"), {
		type: "minecraft:crafting_shapeless",
		category: "building",
		group: "planks",
		ingredients: [`#${NS}:twisted_logs`],
		result: { count: 4, id: `${NS}:twisted_planks` },
	});
	for (const [from, to] of [["twisted_log", "twisted_wood"], ["stripped_twisted_log", "stripped_twisted_wood"]]) {
		write(D("recipe", `${to}.json`), {
			type: "minecraft:crafting_shaped",
			category: "building",
			group: "bark",
			key: { "#": `${NS}:${from}` },
			pattern: ["##", "##"],
			result: { count: 3, id: `${NS}:${to}` },
		});
	}
	files += 3;

	lang[`itemGroup.${NS}.main`] = "Waldschatten";
	lang[`biome.${NS}.waldschatten`] = "Waldschatten";

	const ordered = Object.fromEntries(Object.keys(lang).sort().map((k) => [k, lang[k]]));
	write(A("lang", "en_us.json"), ordered);
	files++;

	console.log(`wrote ${files} files for ${Object.keys(BLOCKS).length} blocks`);
}

main();
