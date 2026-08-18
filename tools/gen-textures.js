#!/usr/bin/env node
// Procedural PLACEHOLDER textures for every Waldschatten block.
//
// These are deliberately simple, readable and palette-correct, and every one of them is
// meant to be replaced by an artist — see WALDSCHATTEN.md for the list. What they are NOT
// is missing: a checkerboard in a screenshot is indistinguishable from a broken model, so
// shipping a plain-but-real texture keeps the render battery honest about what it is
// actually testing.
//
// Colours come from tools/palette.js, so the blocks and the biome are the same shift of
// the same vanilla forest. Every draw call is seeded off the texture name, so
// regenerating produces byte-identical output and a diff means a real change.
// Usage: node tools/gen-textures.js
const fs = require("node:fs");
const path = require("node:path");
const png = require("./png.js");
const { palette, shift, hex } = require("./palette.js");

const OUT = path.join(__dirname, "..", "src/main/resources/assets/waldschatten/textures/block");
const P = palette();

// ---------- tiny deterministic draw kit ----------
function mulberry32(seed) {
	return function () {
		seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
		let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
		t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
		return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
	};
}
const seedOf = (name) => {
	let h = 2166136261;
	for (let i = 0; i < name.length; i++) { h ^= name.charCodeAt(i); h = Math.imul(h, 16777619); }
	return h >>> 0;
};

class Canvas {
	constructor(w, h, name) {
		this.w = w; this.h = h;
		this.px = Buffer.alloc(w * h * 4); // transparent
		this.rand = mulberry32(seedOf(name));
	}
	set(x, y, rgb, a = 255) {
		if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
		const i = (y * this.w + x) * 4;
		this.px[i] = rgb[0]; this.px[i + 1] = rgb[1]; this.px[i + 2] = rgb[2]; this.px[i + 3] = a;
	}
	get(x, y) {
		const i = (y * this.w + x) * 4;
		return [this.px[i], this.px[i + 1], this.px[i + 2], this.px[i + 3]];
	}
	fill(rgb) { for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) this.set(x, y, rgb); }
	/** per-pixel value jitter, the thing that stops a flat fill looking like plastic */
	grain(amount) {
		for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
			const [r, g, b, a] = this.get(x, y);
			if (a === 0) continue;
			const d = Math.round((this.rand() - 0.5) * 2 * amount);
			this.set(x, y, [r + d, g + d, b + d], a);
		}
	}
	rect(x0, y0, x1, y1, rgb, a = 255) {
		for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) this.set(x, y, rgb, a);
	}
	vline(x, y0, y1, rgb, a = 255) { for (let y = y0; y <= y1; y++) this.set(x, y, rgb, a); }
	hline(y, x0, x1, rgb, a = 255) { for (let x = x0; x <= x1; x++) this.set(x, y, rgb, a); }
	write(name) {
		const file = path.join(OUT, `${name}.png`);
		fs.writeFileSync(file, png.encode(this.w, this.h, this.px));
		return file;
	}
}

const mix = (a, b, t) => [
	Math.round(a[0] + (b[0] - a[0]) * t),
	Math.round(a[1] + (b[1] - a[1]) * t),
	Math.round(a[2] + (b[2] - a[2]) * t),
];
const dark = (rgb, f) => rgb.map((c) => Math.round(c * f));

// ---------- the textures ----------
const TEXTURES = {
	twisted_log(c) {
		c.fill(P.bark);
		// vertical bark ridges, with moss caught in the grooves
		for (let x = 0; x < c.w; x++) {
			const ridge = c.rand();
			for (let y = 0; y < c.h; y++) {
				if (ridge < 0.28) c.set(x, y, dark(P.bark, 0.55));
				else if (ridge > 0.82) c.set(x, y, mix(P.bark, P.barkMoss, 0.5 + c.rand() * 0.5));
			}
		}
		// moss streaks running down the trunk
		for (let n = 0; n < 5; n++) {
			let x = Math.floor(c.rand() * c.w);
			let y = Math.floor(c.rand() * c.h);
			for (let s = 0; s < 4 + c.rand() * 6; s++) {
				c.set(x, y % c.h, mix(P.barkMoss, P.foliage, c.rand() * 0.4));
				y++;
				if (c.rand() < 0.3) x += c.rand() < 0.5 ? 1 : -1;
			}
		}
		c.grain(6);
	},
	twisted_log_top(c) {
		c.fill(P.strippedBark);
		const cx = 7.5, cy = 7.5;
		for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
			const d = Math.hypot(x - cx, y - cy);
			// growth rings, squeezed on one side so the trunk reads as grown crooked
			const ring = Math.sin((d + (x - cx) * 0.25) * 2.1) > 0;
			c.set(x, y, ring ? dark(P.strippedBark, 0.72) : P.strippedBark);
			if (d > 6.6) c.set(x, y, P.bark);           // bark rim
			else if (d > 5.8) c.set(x, y, dark(P.bark, 1.35));
		}
		c.grain(5);
	},
	stripped_twisted_log(c) {
		c.fill(P.strippedBark);
		for (let x = 0; x < c.w; x++) {
			if (c.rand() < 0.35) c.vline(x, 0, c.h - 1, dark(P.strippedBark, 0.82));
		}
		for (let n = 0; n < 3; n++) {  // knots
			const kx = Math.floor(c.rand() * c.w), ky = Math.floor(c.rand() * c.h);
			c.set(kx, ky, dark(P.strippedBark, 0.5));
			c.set(kx + 1, ky, dark(P.strippedBark, 0.65));
			c.set(kx, ky + 1, dark(P.strippedBark, 0.65));
		}
		c.grain(6);
	},
	stripped_twisted_log_top(c) {
		TEXTURES.twisted_log_top(c);
		// same rings, but the bark rim is stripped away
		for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
			const d = Math.hypot(x - 7.5, y - 7.5);
			if (d > 5.8) c.set(x, y, mix(P.strippedBark, P.planks, 0.4));
		}
	},
	twisted_leaves(c) {
		c.fill(P.foliage);
		for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
			const r = c.rand();
			if (r < 0.12) c.set(x, y, [0, 0, 0], 0);                       // gaps you can see sky through
			else if (r < 0.34) c.set(x, y, dark(P.foliage, 0.62));         // deep shadow
			else if (r > 0.90) c.set(x, y, mix(P.foliage, P.grass, 0.55)); // a few lit leaves
		}
		// dead clumps — the canopy is sickly, not healthy
		for (let n = 0; n < 4; n++) {
			const x = Math.floor(c.rand() * c.w), y = Math.floor(c.rand() * c.h);
			c.rect(x, y, x + 1, y + 1, mix(P.foliage, P.dryFoliage, 0.6));
		}
		c.grain(7);
	},
	twisted_planks(c) {
		c.fill(P.planks);
		for (const y of [0, 5, 10, 15]) c.hline(y, 0, c.w - 1, dark(P.planks, 0.6));
		// staggered board ends
		c.vline(6, 1, 4, dark(P.planks, 0.6));
		c.vline(11, 6, 9, dark(P.planks, 0.6));
		c.vline(3, 11, 14, dark(P.planks, 0.6));
		for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
			if (c.rand() < 0.18) c.set(x, y, dark(P.planks, 0.86));
		}
		c.grain(5);
	},
	twisted_sapling(c) {
		// a crooked stem with a small unhappy crown
		let x = 8;
		for (let y = 15; y >= 7; y--) {
			c.set(x, y, P.bark);
			if (c.rand() < 0.45) x += c.rand() < 0.5 ? 1 : -1;
		}
		for (let n = 0; n < 26; n++) {
			const lx = x + Math.floor((c.rand() - 0.5) * 7);
			const ly = 3 + Math.floor(c.rand() * 6);
			c.set(lx, ly, c.rand() < 0.3 ? dark(P.foliage, 0.7) : P.foliage);
		}
	},
	gnarled_roots(c) {
		// a floor tangle: several roots crawling across, nothing in the corners
		for (let n = 0; n < 7; n++) {
			let x = Math.floor(c.rand() * c.w);
			let y = Math.floor(c.rand() * c.h);
			const horizontal = c.rand() < 0.5;
			for (let s = 0; s < 6 + c.rand() * 8; s++) {
				c.set(x, y, c.rand() < 0.3 ? mix(P.root, P.barkMoss, 0.5) : P.root);
				if (horizontal) { x++; if (c.rand() < 0.35) y += c.rand() < 0.5 ? 1 : -1; }
				else { y++; if (c.rand() < 0.35) x += c.rand() < 0.5 ? 1 : -1; }
				x = (x + c.w) % c.w; y = (y + c.h) % c.h;
			}
		}
	},
	witch_hazel_bush(c) {
		for (let n = 0; n < 40; n++) {
			const x = 2 + Math.floor(c.rand() * 12);
			const y = 5 + Math.floor(c.rand() * 10);
			c.set(x, y, c.rand() < 0.35 ? dark(P.foliage, 0.65) : P.foliage);
		}
		// witch hazel's spidery ribbon flowers, the one pale note
		for (let n = 0; n < 9; n++) {
			const x = 3 + Math.floor(c.rand() * 10);
			const y = 5 + Math.floor(c.rand() * 8);
			c.set(x, y, mix(P.candle, P.bone, 0.35));
		}
	},
	nightshade_plant(c) {
		for (let n = 0; n < 34; n++) {
			const x = 3 + Math.floor(c.rand() * 10);
			const y = 6 + Math.floor(c.rand() * 9);
			c.set(x, y, c.rand() < 0.4 ? dark(P.foliage, 0.55) : P.foliage);
		}
		// berries: dark, glossy, obviously not for eating
		for (const [bx, by] of [[5, 9], [9, 11], [7, 13], [11, 8]]) {
			c.set(bx, by, P.berry);
			c.set(bx + 1, by, P.berry);
			c.set(bx, by + 1, P.berry);
			c.set(bx + 1, by + 1, dark(P.berry, 1.6));
		}
	},
	mandrake_root(c) {
		// a pale forked root, half out of the ground
		let x = 8;
		for (let y = 14; y >= 5; y--) {
			c.set(x, y, P.bone);
			c.set(x + 1, y, dark(P.bone, 0.72));
			if (c.rand() < 0.4) x += c.rand() < 0.5 ? 1 : -1;
		}
		for (let s = 0; s < 5; s++) { c.set(6 - s + 2, 12 + (s % 2), dark(P.bone, 0.8)); }
		for (let s = 0; s < 5; s++) { c.set(10 + s - 2, 12 + (s % 2), dark(P.bone, 0.8)); }
		for (let n = 0; n < 8; n++) {   // the leaf tuft it betrays itself by
			c.set(6 + Math.floor(c.rand() * 5), 2 + Math.floor(c.rand() * 3), P.foliage);
		}
	},
	bone_chime(c) {
		c.hline(1, 4, 11, dark(P.root, 1.2));                 // the cord
		for (const x of [4, 6, 8, 10]) {                       // hanging bone strips
			const len = 5 + Math.floor(c.rand() * 6);
			c.vline(x, 2, 2 + len, P.bone);
			c.set(x, 2 + len + 1, dark(P.bone, 0.75));
			c.set(x - 1, 2 + len, dark(P.bone, 0.8));
		}
	},
	ashen_soil(c) {
		c.fill(P.ash);
		for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
			if (c.rand() < 0.22) c.set(x, y, dark(P.ash, 0.7));
			else if (c.rand() > 0.94) c.set(x, y, mix(P.ash, P.bone, 0.25));
		}
		for (let n = 0; n < 4; n++) {   // cracks
			let x = Math.floor(c.rand() * c.w), y = Math.floor(c.rand() * c.h);
			for (let s = 0; s < 5 + c.rand() * 6; s++) {
				c.set(x, y, dark(P.ash, 0.42));
				if (c.rand() < 0.5) x += c.rand() < 0.5 ? 1 : -1; else y += c.rand() < 0.5 ? 1 : -1;
				x = (x + c.w) % c.w; y = (y + c.h) % c.h;
			}
		}
		c.grain(5);
	},
	mossy_cairn_stone(c) {
		c.fill(P.stone);
		// stacked courses, so it reads as piled stones rather than a wall
		for (const y of [0, 5, 11]) c.hline(y, 0, c.w - 1, dark(P.stone, 0.6));
		c.vline(7, 1, 4, dark(P.stone, 0.6));
		c.vline(3, 6, 10, dark(P.stone, 0.6));
		c.vline(12, 6, 10, dark(P.stone, 0.6));
		c.vline(9, 12, 15, dark(P.stone, 0.6));
		for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
			if (c.rand() < 0.30) c.set(x, y, mix(P.stone, P.foliage, 0.35 + c.rand() * 0.5)); // moss
			else if (c.rand() > 0.9) c.set(x, y, dark(P.stone, 1.18));
		}
		c.grain(6);
	},
	iron_lantern(c) {
		const iron = dark(P.stone, 0.45);
		c.rect(5, 3, 10, 12, dark(P.candle, 0.30));      // the glass, dim
		c.rect(6, 5, 9, 10, mix(P.candle, [0, 0, 0], 0.35));
		c.rect(7, 7, 8, 9, P.candle);                     // the flame itself
		c.rect(4, 2, 11, 2, iron);                        // cap
		c.rect(4, 13, 11, 13, iron);                      // base
		c.vline(4, 2, 13, iron); c.vline(11, 2, 13, iron);
		// Cage bars at 5 and 10, NOT 7 and 8: the centre columns are where the flame is,
		// and bars drawn over it left the one warm light source in the biome as a black
		// box with a lid. Draw the bars beside the light, not across it.
		c.vline(5, 3, 12, dark(iron, 1.3)); c.vline(10, 3, 12, dark(iron, 1.3));
		c.rect(7, 6, 8, 10, P.candle);                    // re-assert the flame over the cage
		c.vline(7, 0, 1, iron); c.vline(8, 0, 1, iron);   // the hook
		c.set(6, 14, iron); c.set(9, 14, iron);
	},
	thorn_vine(c) {
		for (let n = 0; n < 5; n++) {
			let x = Math.floor(c.rand() * c.w);
			for (let y = 0; y < c.h; y++) {
				c.set(x, y, c.rand() < 0.25 ? dark(P.thorn, 1.5) : P.thorn);
				if (c.rand() < 0.4) {                       // thorns, sticking out sideways
					const d = c.rand() < 0.5 ? 1 : -1;
					c.set(x + d, y, mix(P.thorn, P.bone, 0.35));
				}
				if (c.rand() < 0.35) x += c.rand() < 0.5 ? 1 : -1;
				x = (x + c.w) % c.w;
			}
		}
	},
};

function main() {
	fs.mkdirSync(OUT, { recursive: true });
	const written = [];
	for (const [name, draw] of Object.entries(TEXTURES)) {
		const c = new Canvas(16, 16, name);
		draw(c);
		c.write(name);
		written.push(name);
	}
	console.log(`wrote ${written.length} placeholder textures to ${path.relative(path.join(__dirname, ".."), OUT)}`);
	console.log(written.join(", "));
}

main();
