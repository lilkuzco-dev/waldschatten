// Minimal dependency-free PNG decode/encode. Shares its shape with the warfront and
// vibranium texture pipelines in this empire.
//
// Dimension-agnostic on purpose: `encode` takes w/h and a w*h*4 RGBA buffer and never
// assumes 16x16. A texture writer that ignores the shape of its input is a coincidence,
// not a writer (CLAUDE.md rule 9) — cosmos shipped truncated 64x64 entity sheets for
// months because its generator hardcoded 16.
const zlib = require("node:zlib");

const CRC_TABLE = (() => {
	const t = new Int32Array(256);
	for (let n = 0; n < 256; n++) {
		let c = n;
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
		t[n] = c;
	}
	return t;
})();

function crc32(buf) {
	let c = 0xffffffff;
	for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
	return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
	const len = Buffer.alloc(4);
	len.writeUInt32BE(data.length);
	const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(body));
	return Buffer.concat([len, body, crc]);
}

/** @param {number} w @param {number} h @param {Buffer} px RGBA, length w*h*4 */
function encode(w, h, px) {
	if (px.length !== w * h * 4) {
		throw new Error(`pixel buffer is ${px.length} bytes, expected ${w * h * 4} for ${w}x${h}`);
	}
	const raw = Buffer.alloc(h * (w * 4 + 1));
	for (let y = 0; y < h; y++) {
		raw[y * (w * 4 + 1)] = 0; // filter: none
		px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
	}
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0);
	ihdr.writeUInt32BE(h, 4);
	ihdr[8] = 8;  // bit depth
	ihdr[9] = 6;  // colour type: RGBA
	return Buffer.concat([
		Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
		chunk("IHDR", ihdr),
		chunk("IDAT", zlib.deflateSync(raw, { level: 9 })),
		chunk("IEND", Buffer.alloc(0)),
	]);
}

/** @returns {{w:number,h:number,px:Buffer}} px is RGBA */
function decode(buf) {
	let off = 8;
	let w, h, bitDepth, colorType;
	const palette = [];
	const idat = [];
	while (off < buf.length) {
		const len = buf.readUInt32BE(off);
		const type = buf.toString("ascii", off + 4, off + 8);
		const data = buf.subarray(off + 8, off + 8 + len);
		if (type === "IHDR") {
			w = data.readUInt32BE(0);
			h = data.readUInt32BE(4);
			bitDepth = data[8];
			colorType = data[9];
		} else if (type === "PLTE") {
			for (let i = 0; i < data.length; i += 3) palette.push([data[i], data[i + 1], data[i + 2]]);
		} else if (type === "IDAT") idat.push(data);
		off += 12 + len;
	}
	if (bitDepth !== 8) throw new Error(`unsupported bit depth ${bitDepth}`);
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType];
	const bpp = channels;
	const stride = w * channels;
	const out = Buffer.alloc(h * stride);
	let prev = Buffer.alloc(stride);
	for (let y = 0; y < h; y++) {
		const filter = raw[y * (stride + 1)];
		const line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
		for (let x = 0; x < stride; x++) {
			const a = x >= bpp ? line[x - bpp] : 0;
			const b = prev[x];
			const c = x >= bpp ? prev[x - bpp] : 0;
			if (filter === 1) line[x] = (line[x] + a) & 0xff;
			else if (filter === 2) line[x] = (line[x] + b) & 0xff;
			else if (filter === 3) line[x] = (line[x] + ((a + b) >> 1)) & 0xff;
			else if (filter === 4) {
				const p = a + b - c;
				const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
				line[x] = (line[x] + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 0xff;
			}
			out[y * stride + x] = line[x];
		}
		prev = line;
	}
	const px = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) {
		for (let x = 0; x < w; x++) {
			const i = (y * w + x) * 4;
			const s = y * stride + x * channels;
			if (colorType === 6) out.copy(px, i, s, s + 4);
			else if (colorType === 2) { out.copy(px, i, s, s + 3); px[i + 3] = 255; }
			else if (colorType === 3) { const p = palette[out[s]]; px[i] = p[0]; px[i + 1] = p[1]; px[i + 2] = p[2]; px[i + 3] = 255; }
			else throw new Error(`unsupported colour type ${colorType}`);
		}
	}
	return { w, h, px };
}

module.exports = { encode, decode, crc32 };
