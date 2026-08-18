// A tiny builder for structure NBT templates.
//
// Freehand geometry in code rather than blocks placed in a creative world, following the
// warfront precedent: a template built by a script is reviewable in a diff, regenerates
// byte-identically, and can be re-themed by changing one constant. A template built by
// hand in-game is a binary blob nobody can review.
const fs = require("node:fs");
const path = require("node:path");
const { TAG, N, write } = require("./nbt.js");

// 26.2. Same value warfront ships its structures with.
const DATA_VERSION = 4903;

class Template {
	constructor(name) {
		this.name = name;
		this.blocks = new Map(); // "x,y,z" -> {name, props, nbt}
		this.entities = [];
	}

	set(x, y, z, name, props = null, nbt = null) {
		if (x < 0 || y < 0 || z < 0) {
			throw new Error(`${this.name}: block at negative coordinate ${x},${y},${z} — templates are 0-based`);
		}
		this.blocks.set(`${x},${y},${z}`, { name, props, nbt });
		return this;
	}

	/** Solid box, inclusive of both corners. */
	box(x0, y0, z0, x1, y1, z1, name, props = null) {
		for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					this.set(x, y, z, name, props);
				}
			}
		}
		return this;
	}

	/** Box walls only — no floor, no ceiling, hollow middle. */
	walls(x0, y0, z0, x1, y1, z1, name, props = null) {
		for (let x = x0; x <= x1; x++) {
			for (let y = y0; y <= y1; y++) {
				for (let z = z0; z <= z1; z++) {
					if (x === x0 || x === x1 || z === z0 || z === z1) this.set(x, y, z, name, props);
				}
			}
		}
		return this;
	}

	/**
	 * Air is a real block in a template: it is what carves a room out of the ground the
	 * structure lands in. Leaving it out means the hut's interior fills with hillside.
	 */
	air(x0, y0, z0, x1, y1, z1) {
		return this.box(x0, y0, z0, x1, y1, z1, "minecraft:air");
	}

	/**
	 * A jigsaw connector. `final_state` is what the block turns into once the piece is
	 * placed, which is why natural generation never shows a jigsaw block and
	 * `/place template` does (memory: warfront's v0.2.1 battery).
	 */
	jigsaw(x, y, z, { name, target, pool, orientation = "north_up", finalState = "minecraft:air" }) {
		return this.set(x, y, z, "minecraft:jigsaw", { orientation }, N.compound({
			id: N.string("minecraft:jigsaw"),
			name: N.string(name),
			target: N.string(target),
			pool: N.string(pool),
			final_state: N.string(finalState),
			joint: N.string("rollable"),
		}));
	}

	chest(x, y, z, facing, lootTable) {
		return this.set(x, y, z, "minecraft:chest", { facing, type: "single", waterlogged: "false" },
			N.compound({ id: N.string("minecraft:chest"), LootTable: N.string(lootTable) }));
	}

	emit(dir) {
		const palette = [];
		const paletteIndex = new Map();
		const blockItems = [];
		let maxX = 0, maxY = 0, maxZ = 0;

		const wrapProps = (props) => ({
			t: TAG.compound,
			v: Object.fromEntries(Object.entries(props).map(([k, v]) => [k, N.string(String(v))])),
		});

		for (const [key, block] of this.blocks) {
			const [x, y, z] = key.split(",").map(Number);
			maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
			const pk = block.name + "|" + JSON.stringify(block.props ?? {});
			if (!paletteIndex.has(pk)) {
				paletteIndex.set(pk, palette.length);
				const entry = { Name: N.string(block.name) };
				if (block.props && Object.keys(block.props).length) entry.Properties = wrapProps(block.props);
				palette.push(entry);
			}
			const rec = { pos: N.list(TAG.int, [x, y, z]), state: N.int(paletteIndex.get(pk)) };
			if (block.nbt) rec.nbt = block.nbt;
			blockItems.push(rec);
		}

		const root = N.compound({
			size: N.list(TAG.int, [maxX + 1, maxY + 1, maxZ + 1]),
			DataVersion: N.int(DATA_VERSION),
			palette: { t: TAG.list, v: { itemType: TAG.compound, items: palette } },
			blocks: { t: TAG.list, v: { itemType: TAG.compound, items: blockItems } },
			entities: { t: TAG.list, v: { itemType: TAG.compound, items: this.entities } },
		});

		const file = path.join(dir, `${this.name}.nbt`);
		fs.mkdirSync(path.dirname(file), { recursive: true });
		fs.writeFileSync(file, write(root));
		return { file, size: [maxX + 1, maxY + 1, maxZ + 1], blocks: this.blocks.size, palette: palette.length };
	}
}

module.exports = { Template, DATA_VERSION };
