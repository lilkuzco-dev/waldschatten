#!/usr/bin/env node
// Composites every block texture into one scaled-up sheet, so the placeholders can be
// judged as ART rather than as 16x16 specks in a screenshot. Checkerboard alpha behind
// each tile makes transparency visible instead of guessed at.
// Usage: node tools/contact-sheet.js [outfile]
const fs = require("node:fs");
const path = require("node:path");
const png = require("./png.js");

const DIR = path.join(__dirname, "..", "src/main/resources/assets/waldschatten/textures/block");
const OUT = process.argv[2] || path.join(__dirname, "..", "build", "texture-contact-sheet.png");
const SCALE = 6, PAD = 2, COLS = 6;

const files = fs.readdirSync(DIR).filter((f) => f.endsWith(".png")).sort();
const tiles = files.map((f) => ({ name: f.replace(/\.png$/, ""), img: png.decode(fs.readFileSync(path.join(DIR, f))) }));

const cell = 16 * SCALE + PAD * 2;
const rows = Math.ceil(tiles.length / COLS);
const W = COLS * cell, H = rows * cell;
const out = Buffer.alloc(W * H * 4);

const put = (x, y, r, g, b, a) => {
	if (x < 0 || y < 0 || x >= W || y >= H) return;
	const i = (y * W + x) * 4;
	out[i] = r; out[i + 1] = g; out[i + 2] = b; out[i + 3] = a;
};

// background: mid-grey checker, so alpha reads as alpha and not as "black pixel"
for (let y = 0; y < H; y++) {
	for (let x = 0; x < W; x++) {
		const c = ((x >> 3) + (y >> 3)) % 2 === 0 ? 150 : 110;
		put(x, y, c, c, c, 255);
	}
}

tiles.forEach((t, i) => {
	const ox = (i % COLS) * cell + PAD;
	const oy = Math.floor(i / COLS) * cell + PAD;
	const { w, h, px } = t.img;
	if (w !== 16 || h !== 16) console.warn(`  !! ${t.name} is ${w}x${h}, not 16x16`);
	for (let y = 0; y < h * SCALE; y++) {
		for (let x = 0; x < w * SCALE; x++) {
			const s = (Math.floor(y / SCALE) * w + Math.floor(x / SCALE)) * 4;
			const a = px[s + 3];
			if (a === 0) continue;                       // let the checker show through
			put(ox + x, oy + y, px[s], px[s + 1], px[s + 2], 255);
		}
	}
});

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, png.encode(W, H, out));
console.log(`wrote ${OUT} (${W}x${H})`);
tiles.forEach((t, i) => {
	if (i % COLS === 0) process.stdout.write(`\nrow ${Math.floor(i / COLS)}: `);
	process.stdout.write(t.name + "  ");
});
console.log();
