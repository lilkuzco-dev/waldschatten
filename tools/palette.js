// The Waldschatten palette, derived — not invented.
//
// RULING (Jesse, 2026-08-18): "the haunted forest should be a shade or two darker than
// regular forests. just hue shift it." So every colour in this mod is vanilla *forest*
// run through ONE transform. Nothing here is hand-picked, which means the mood is tuned
// by editing three numbers and re-running the generators, and the biome keeps its family
// resemblance to the woods it is a corruption of.
//
// The vanilla baselines are read from the real 26.2 client jar (colormaps) and the real
// 26.2 server jar (biome/dimension JSON) rather than transcribed, so they cannot drift.
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { execFileSync } = require("node:child_process");
const png = require("./png.js");

// ---------------------------------------------------------------------------
// THE TRANSFORM — the three knobs. Everything downstream follows from these.
// ---------------------------------------------------------------------------
const HUE_SHIFT = -35;      // degrees. Takes forest green -> olive, sky blue -> cold teal.
const SATURATION = 0.55;    // x, toward grey
const LIGHTNESS = 0.60;     // x, "a shade or two darker" — for ground surfaces
// Sky and fog start far lighter than leaves do (L 0.74 and 0.87 against foliage's 0.44),
// so the surface multiplier leaves them a bright teal — lighter-feeling than the forest
// they are supposed to be a darker version of. Atmospherics take the same hue shift and
// the same desaturation, and a harder pull on value. One transform, two value targets.
const LIGHTNESS_SKY = 0.35; // x, for fog / sky / water fog
// Pastels like vanilla's sky (#79a6ff) sit at HSL saturation 1.0 because one channel is
// pinned at 255 — scaling only their value turns them into a vivid teal rather than a
// muted one. Atmospherics get the harder desaturation that "almost colorless" needs.
const SATURATION_SKY = 0.22;

// Vanilla forest's climate. Its grass and foliage colours are looked up from the
// colormaps at exactly this point, which is what makes them *forest's* colours.
const FOREST_TEMPERATURE = 0.7;
const FOREST_DOWNFALL = 0.8;

// ---------------------------------------------------------------------------
function rgbToHsl(r, g, b) {
	r /= 255; g /= 255; b /= 255;
	const max = Math.max(r, g, b), min = Math.min(r, g, b);
	const l = (max + min) / 2;
	if (max === min) return [0, 0, l];
	const d = max - min;
	const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
	let h;
	if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
	else if (max === g) h = ((b - r) / d + 2) / 6;
	else h = ((r - g) / d + 4) / 6;
	return [h * 360, s, l];
}

function hslToRgb(h, s, l) {
	h = ((h % 360) + 360) % 360 / 360;
	if (s === 0) { const v = Math.round(l * 255); return [v, v, v]; }
	const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
	const p = 2 * l - q;
	const f = (t) => {
		if (t < 0) t += 1;
		if (t > 1) t -= 1;
		if (t < 1 / 6) return p + (q - p) * 6 * t;
		if (t < 1 / 2) return q;
		if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
		return p;
	};
	return [Math.round(f(h + 1 / 3) * 255), Math.round(f(h) * 255), Math.round(f(h - 1 / 3) * 255)];
}

/** The one transform. Vanilla forest colour in, Waldschatten colour out. */
function shift(rgb, { lightness = LIGHTNESS, saturation = SATURATION, hue = HUE_SHIFT } = {}) {
	const [h, s, l] = rgbToHsl(rgb[0], rgb[1], rgb[2]);
	return hslToRgb(h + hue, Math.min(1, s * saturation), Math.min(1, l * lightness));
}

const hex = (rgb) => "#" + rgb.map((c) => Math.max(0, Math.min(255, c)).toString(16).padStart(2, "0")).join("");
const unhex = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];

// ---------------------------------------------------------------------------
// Vanilla baselines, read from the jars.
// ---------------------------------------------------------------------------
function loomCache() {
	return path.join(os.homedir(), ".gradle/caches/fabric-loom/26.2");
}

function readFromJar(jar, entry) {
	return execFileSync("unzip", ["-p", jar, entry], { maxBuffer: 1 << 28 });
}

/** Vanilla's ColorMapColorUtil.get, reimplemented exactly. */
function colormapLookup(pxData, w, temp, rain) {
	rain *= temp;
	const x = Math.trunc((1.0 - temp) * 255.0);
	const y = Math.trunc((1.0 - rain) * 255.0);
	const index = (y << 8) | x;
	const i = index * 4;
	return [pxData[i], pxData[i + 1], pxData[i + 2]];
}

let cached = null;

function vanillaForest() {
	if (cached) return cached;
	const clientJar = path.join(loomCache(), "minecraft-client.jar");
	const serverJar = path.join(loomCache(), "minecraft-extracted_server.jar");
	if (!fs.existsSync(clientJar)) {
		throw new Error(`no Loom cache at ${clientJar} — run a gradle build in this repo first`);
	}

	const grass = png.decode(readFromJar(clientJar, "assets/minecraft/textures/colormap/grass.png"));
	const foliage = png.decode(readFromJar(clientJar, "assets/minecraft/textures/colormap/foliage.png"));
	const dryFoliage = png.decode(readFromJar(clientJar, "assets/minecraft/textures/colormap/dry_foliage.png"));

	const forestBiome = JSON.parse(readFromJar(serverJar, "data/minecraft/worldgen/biome/forest.json").toString());
	const overworldDim = JSON.parse(readFromJar(serverJar, "data/minecraft/dimension_type/overworld.json").toString());

	cached = {
		grass: colormapLookup(grass.px, grass.w, FOREST_TEMPERATURE, FOREST_DOWNFALL),
		foliage: colormapLookup(foliage.px, foliage.w, FOREST_TEMPERATURE, FOREST_DOWNFALL),
		dryFoliage: colormapLookup(dryFoliage.px, dryFoliage.w, FOREST_TEMPERATURE, FOREST_DOWNFALL),
		water: unhex(forestBiome.effects.water_color),
		sky: unhex(forestBiome.attributes["minecraft:visual/sky_color"]),
		// The overworld dimension supplies fog and water fog; forest does not override them,
		// so the forest a player knows is showing them the dimension's defaults.
		fog: unhex(overworldDim.attributes["minecraft:visual/fog_color"]),
		waterFog: unhex(overworldDim.attributes["minecraft:visual/water_fog_color"] ?? "#050533"),
		// EnvironmentAttributes.BLOCK_LIGHT_TINT's own default (-10100). The overworld
		// does not override it, so this warm value is the colour every torch a player has
		// ever lit has been tinted with.
		blockLightTint: unhex(overworldDim.attributes["minecraft:visual/block_light_tint"] ?? "#ffd88c"),
	};
	return cached;
}

/** The shifted palette: what Waldschatten actually paints with. */
function palette() {
	const v = vanillaForest();
	const p = {};
	const ATMOSPHERIC = new Set(["sky", "fog", "waterFog"]);
	for (const [k, rgb] of Object.entries(v)) {
		p[k] = shift(rgb, ATMOSPHERIC.has(k)
			? { lightness: LIGHTNESS_SKY, saturation: SATURATION_SKY }
			: {});
	}
	// Bark, bone, ash and thorn are not forest-tinted surfaces, so they are anchored to
	// the shifted foliage's hue and pushed to the ends of the value range instead. Same
	// transform, different target lightness — still no free-hand colour picking.
	p.bark = shift(v.foliage, { lightness: 0.16, saturation: 0.30 });
	p.barkMoss = shift(v.foliage, { lightness: 0.34, saturation: 0.45 });
	p.strippedBark = shift(v.foliage, { lightness: 0.52, saturation: 0.35 });
	p.planks = shift(v.foliage, { lightness: 0.40, saturation: 0.32 });
	p.ash = shift(v.fog, { lightness: 0.30, saturation: 0.18 });
	p.bone = shift(v.fog, { lightness: 0.86, saturation: 0.06 });
	p.stone = shift(v.fog, { lightness: 0.42, saturation: 0.10 });
	p.thorn = shift(v.foliage, { hue: -95, lightness: 0.28, saturation: 0.75 });   // dried blood
	p.berry = shift(v.foliage, { hue: -110, lightness: 0.30, saturation: 0.95 });  // nightshade
	p.candle = shift(v.foliage, { hue: -55, lightness: 1.90, saturation: 1.8 });   // the only warm light
	p.root = shift(v.foliage, { lightness: 0.26, saturation: 0.40 });
	// Waldschatten's block light. Vanilla's warm torch tint rotated all the way to the
	// cold half of the wheel: firelight does not read as firelight here, which is the
	// whole point of the soul-torch rule. The player's torches still burn, they just
	// stop looking like they help.
	p.blockLight = shift(v.blockLightTint, { hue: 165, lightness: 0.62, saturation: 0.85 });
	return p;
}

module.exports = {
	palette, vanillaForest, shift, hex, unhex, rgbToHsl, hslToRgb,
	HUE_SHIFT, SATURATION, LIGHTNESS, LIGHTNESS_SKY, SATURATION_SKY,
};

if (require.main === module) {
	const v = vanillaForest();
	const p = palette();
	console.log(`transform: hue ${HUE_SHIFT}deg, saturation x${SATURATION}, lightness x${LIGHTNESS}\n`);
	console.log("key".padEnd(14), "vanilla forest".padEnd(16), "waldschatten");
	for (const k of Object.keys(v)) console.log(k.padEnd(14), hex(v[k]).padEnd(16), hex(p[k]));
	console.log("\nderived (anchored to the shifted foliage hue):");
	for (const k of Object.keys(p)) if (!(k in v)) console.log(k.padEnd(14), "".padEnd(16), hex(p[k]));
}
