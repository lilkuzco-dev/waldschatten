#!/usr/bin/env node
// Block/item/biome tags. Small files, but there are a lot of them and every one is a
// silent gameplay failure when missed: a log outside #minecraft:logs is not a log to any
// recipe, a plant outside #sword_efficient takes a swing to break, and a biome outside
// #is_forest is invisible to every mod that asks "is this a forest".
// Usage: node tools/gen-tags.js
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..", "src/main/resources/data");
const NS = "waldschatten";
const B = (n) => `${NS}:${n}`;

const LOGS = [B("twisted_log"), B("stripped_twisted_log"), B("twisted_wood"), B("stripped_twisted_wood")];
const PLANTS = [B("witch_hazel_bush"), B("nightshade_plant"), B("mandrake_root"), B("thorn_vine"), B("twisted_sapling"), B("gnarled_roots")];

// namespace -> relative path -> values
const TAGS = {
	minecraft: {
		"tags/block/logs": LOGS,
		"tags/block/logs_that_burn": LOGS,
		"tags/block/planks": [B("twisted_planks")],
		"tags/block/leaves": [B("twisted_leaves")],
		"tags/block/saplings": [B("twisted_sapling")],
		"tags/block/climbable": [B("thorn_vine")],
		"tags/block/sword_efficient": PLANTS,
		"tags/block/replaceable_by_trees": [B("gnarled_roots"), B("thorn_vine"), B("witch_hazel_bush"), B("nightshade_plant"), B("mandrake_root")],
		"tags/block/mineable/axe": [...LOGS, B("twisted_planks"), B("bone_chime")],
		"tags/block/mineable/pickaxe": [B("mossy_cairn_stone"), B("iron_lantern")],
		"tags/block/mineable/shovel": [B("ashen_soil")],
		"tags/block/mineable/hoe": [B("twisted_leaves")],
		"tags/block/dirt": [B("ashen_soil")],
		"tags/block/supports_vegetation": [B("ashen_soil")],

		"tags/item/logs": LOGS,
		"tags/item/logs_that_burn": LOGS,
		"tags/item/planks": [B("twisted_planks")],
		"tags/item/leaves": [B("twisted_leaves")],
		"tags/item/saplings": [B("twisted_sapling")],

		// The biome IS a forest, and saying so is what lets other content treat it as one.
		// That cuts both ways and it is a deliberate choice, not an oversight: see the
		// "tag reach" note in WALDSCHATTEN.md before removing it.
		"tags/worldgen/biome/is_forest": [B("waldschatten")],
		"tags/worldgen/biome/is_overworld": [B("waldschatten")],
	},
	c: {
		"tags/worldgen/biome/is_forest": [B("waldschatten")],
	},
	[NS]: {
		// The wood family's own tag, exactly as vanilla gives every wood type one. The
		// planks recipe keys off it, so all four log variants craft down the same way.
		"tags/block/twisted_logs": LOGS,
		"tags/item/twisted_logs": LOGS,

		// The mod's own hook. Future content — and other mods — can target the mood
		// rather than the biome id, so a second spooky biome inherits everything for free.
		"tags/worldgen/biome/is_spooky": [B("waldschatten")],
		"tags/worldgen/biome/has_structure/witch_hut": [B("waldschatten")],
		"tags/worldgen/biome/has_structure/set_pieces": [B("waldschatten")],
	},
};

let n = 0;
for (const [ns, entries] of Object.entries(TAGS)) {
	for (const [rel, values] of Object.entries(entries)) {
		const file = path.join(ROOT, ns, `${rel}.json`);
		fs.mkdirSync(path.dirname(file), { recursive: true });
		fs.writeFileSync(file, JSON.stringify({ replace: false, values }, null, 2) + "\n");
		n++;
	}
}
console.log(`wrote ${n} tag files`);
