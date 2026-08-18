#!/usr/bin/env node
// Static support check for every structure template.
//
// A block placed where it cannot survive does not fail loudly: during chunk generation it
// is written without neighbour updates, so it sits there looking fine until the first
// block update near it, and then it is simply gone. A screenshot taken right after
// generation shows it. A player arriving later does not. That is the worst kind of bug
// this repo knows, so it gets a lint rather than an eyeball.
// Usage: node tools/lint-structures.js
const fs = require("node:fs");
const path = require("node:path");
const { parse } = require("./nbt.js");

const ROOT = path.join(__dirname, "..", "src/main/resources/data/waldschatten/structure");

// Blocks that fall if what is under them is not solid.
const NEEDS_SOLID_BELOW = new Set([
	"waldschatten:witch_hazel_bush", "waldschatten:nightshade_plant", "waldschatten:mandrake_root",
	"waldschatten:thorn_vine", "waldschatten:twisted_sapling", "waldschatten:gnarled_roots",
	"minecraft:red_mushroom", "minecraft:brown_mushroom", "minecraft:firefly_bush",
	"minecraft:candle", "minecraft:flower_pot", "minecraft:skeleton_skull",
	"minecraft:red_bed", "minecraft:torch", "minecraft:soul_torch",
]);
// Blocks that hang and fall if what is above them is not solid.
const NEEDS_SOLID_ABOVE = new Set(["waldschatten:bone_chime"]);

// Things that are not a floor, whatever else they are.
const NEVER_SUPPORTS = new Set([
	"minecraft:air", "minecraft:chain", "minecraft:cobweb", "minecraft:torch", "minecraft:soul_torch",
	"minecraft:candle", "minecraft:flower_pot", "minecraft:red_bed", "minecraft:skeleton_skull",
	"minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:firefly_bush",
	"minecraft:oak_fence", "minecraft:glass_pane", "minecraft:cobblestone_wall",
	"minecraft:dark_oak_trapdoor", "minecraft:dark_oak_door", "minecraft:jigsaw",
	"minecraft:campfire", "minecraft:soul_campfire", "minecraft:brewing_stand", "minecraft:cauldron",
	"waldschatten:witch_hazel_bush", "waldschatten:nightshade_plant", "waldschatten:mandrake_root",
	"waldschatten:thorn_vine", "waldschatten:twisted_sapling", "waldschatten:gnarled_roots",
	"waldschatten:bone_chime", "waldschatten:iron_lantern",
]);

// Support is per-FACE, not per-block, and getting that wrong makes the lint lie in both
// directions. A bottom stair fills the lower half of its cube, so its underside is a full
// square and a chime hangs from it happily, while its top is not a floor at all.
//   supportsUp   — something can stand on top of this
//   supportsDown — something can hang beneath this
function supportsUp(b) {
	if (!b || NEVER_SUPPORTS.has(b.name)) return false;
	if (b.name.endsWith("_stairs")) return b.props.half === "top";
	return true;   // slabs of either half can be stood on; full blocks trivially
}

function supportsDown(b) {
	if (!b) return false;
	// bone_chime accepts leaves overhead by design — it hangs from the canopy, and leaves
	// are never "sturdy" because LeavesBlock.getBlockSupportShape is empty.
	if (b.name.endsWith("_leaves")) return true;
	if (NEVER_SUPPORTS.has(b.name)) return false;
	if (b.name.endsWith("_stairs")) return b.props.half === "bottom";
	if (b.name.endsWith("_slab")) return b.props.type !== "top";
	return true;
}

function readTemplate(file) {
	const { root } = parse(fs.readFileSync(file));
	const palette = root.v.palette.v.items.map((p) => ({
		name: p.Name.v,
		props: p.Properties ? Object.fromEntries(Object.entries(p.Properties.v).map(([k, v]) => [k, v.v])) : {},
	}));
	const blocks = new Map();
	for (const b of root.v.blocks.v.items) {
		const [x, y, z] = b.pos.v.items;
		blocks.set(`${x},${y},${z}`, palette[b.state.v]);
	}
	return { blocks, size: root.v.size.v.items };
}

let problems = 0;
let checked = 0;

function lint(file, rel) {
	const { blocks } = readTemplate(file);
	const at = (x, y, z) => blocks.get(`${x},${y},${z}`);


	for (const [key, block] of blocks) {
		const [x, y, z] = key.split(",").map(Number);
		checked++;

		if (NEEDS_SOLID_BELOW.has(block.name)) {
			// y == 0 rests on whatever terrain the structure lands on — unknowable here,
			// and legitimately fine, so it is not flagged.
			if (y > 0 && !supportsUp(at(x, y - 1, z))) {
				const under = at(x, y - 1, z);
				console.log(`  ${rel}: ${block.name} at ${x},${y},${z} has ${under ? under.name : "nothing"} under it — it will fall`);
				problems++;
			}
		}

		if (NEEDS_SOLID_ABOVE.has(block.name)) {
			if (!supportsDown(at(x, y + 1, z))) {
				const over = at(x, y + 1, z);
				console.log(`  ${rel}: ${block.name} at ${x},${y},${z} has ${over ? over.name : "nothing"} above it — it will fall`);
				problems++;
			}
		}

		if (block.name === "waldschatten:iron_lantern") {
			const hanging = block.props.hanging === "true";
			const support = hanging ? at(x, y + 1, z) : (y > 0 ? at(x, y - 1, z) : undefined);
			if (y > 0 && !(hanging ? supportsDown(support) : supportsUp(support))) {
				console.log(`  ${rel}: iron_lantern (hanging=${hanging}) at ${x},${y},${z} has ${support ? support.name : "nothing"} ${hanging ? "above" : "below"} it — it will fall`);
				problems++;
			}
		}
	}
}

const files = [];
(function walk(d) {
	for (const e of fs.readdirSync(d, { withFileTypes: true })) {
		const p = path.join(d, e.name);
		if (e.isDirectory()) walk(p);
		else if (e.name.endsWith(".nbt")) files.push(p);
	}
})(ROOT);

console.log(`linting ${files.length} templates for blocks that cannot survive where they are placed\n`);
for (const f of files) lint(f, path.relative(ROOT, f));

console.log(`\n${checked} blocks checked, ${problems} problem(s)`);
process.exit(problems ? 1 : 0);
