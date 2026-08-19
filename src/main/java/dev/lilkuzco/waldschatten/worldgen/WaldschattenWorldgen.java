package dev.lilkuzco.waldschatten.worldgen;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.Waldschatten;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Where Waldschatten sits in the world.
 *
 * <h2>Why this is a mixin and not an API call</h2>
 * Fabric API ships {@code NetherBiomes} and {@code TheEndBiomes} and nothing for the
 * overworld. Both vanilla and Terralith ultimately resolve a {@link Climate.ParameterList}
 * through {@code MultiNoiseBiomeSource}; the mixin at that common layer lets this class
 * make the same bounded replacement in either worldgen stack.
 *
 * <h2>What this does NOT do</h2>
 * It does not edit terrain density functions, surface rules, or another mod's data files.
 * The terrain shape comes from the selected climate slice; the replacement changes only
 * which biome owns that slice. {@link #logPlacement} says what was claimed at startup so a
 * missing biome is a logged discrepancy rather than an invisible failure.
 */
public final class WaldschattenWorldgen {

	public static final ResourceKey<Biome> WALDSCHATTEN =
			ResourceKey.create(Registries.BIOME, Waldschatten.id("waldschatten"));
	public static final ResourceKey<Structure> WITCH_HUT =
			ResourceKey.create(Registries.STRUCTURE, Waldschatten.id("witch_hut"));

	public static final StructurePlacementType<WaldschattenPatchPlacement> PATCH_ANCHOR_PLACEMENT =
			Registry.register(
					BuiltInRegistries.STRUCTURE_PLACEMENT,
					Waldschatten.id("patch_anchor"),
					() -> WaldschattenPatchPlacement.CODEC);

	// ---------------------------------------------------------------------------
	// Claiming a slice, rather than asking nicely for one.
	//
	// The first version of this appended a parameter point covering a narrow sub-box of
	// dark forest's climate. It generated EXACTLY NOTHING — 0 of 4225 samples over eight
	// thousand blocks square — and the multi-noise maths says it never could have:
	//
	//   fitness = SUM over parameters of (distance from the sample to that range)^2
	//
	// `distance` is ZERO when the sample lies inside the range, and the search keeps the
	// first point it finds with a strictly lower fitness. So a sub-box of dark forest's box
	// scores exactly what dark forest scores — zero — for every sample it could ever want,
	// ties, and loses the tie to the entry that was added first. Vanilla is always added
	// first. A strict subset is unwinnable by construction, and no amount of narrowing the
	// ranges helps, because you cannot beat zero with zero.
	//
	// (The old parameters were also simply in the wrong cell: dark forest lives at
	// temperature index 2, span -0.15..0.2, and those parameters described index 3 — which
	// is jungle's row of MIDDLE_BIOMES, not dark forest's.)
	//
	// So instead of competing with forest biomes, Waldschatten TAKES a defined piece of
	// them: the high-erosion lowland slice of ordinary forest and dark forest. In Minecraft
	// terrain terms, high erosion means flat or gently rolling ground. Splitting the point
	// is essential: replacing a whole point merely because it overlaps the desired range
	// also steals the mountain-shaped part of broad Terralith points.
	// ---------------------------------------------------------------------------

	/** Vanilla's high-erosion lowlands: flat first, with enough gentle variation to look natural. */
	static final long LOWLAND_MIN = Climate.quantizeCoord(0.565F);
	static final long LOWLAND_MAX = Climate.quantizeCoord(1.0F);
	static final long LOWLAND_CONTINENTAL_MIN = Climate.quantizeCoord(-0.11F);
	static final long LOWLAND_CONTINENTAL_MAX = Climate.quantizeCoord(0.03F);
	static final long LOWLAND_WEIRDNESS_MIN = Climate.quantizeCoord(-1.0F);
	static final long LOWLAND_WEIRDNESS_MAX = Climate.quantizeCoord(1.0F);

	/**
	 * A biome lookup, borrowed from whoever last built a multi-noise parameter list.
	 * Volatile because worldgen touches it from several threads.
	 */
	private static volatile HolderGetter<Biome> biomeLookup;

	public static void rememberBiomeLookup(HolderGetter<Biome> biomes) {
		biomeLookup = biomes;
	}

	/**
	 * Returns the list with our slice claimed out of it, or the list untouched.
	 *
	 * <p>Only the intersection with the lowland erosion band becomes Waldschatten. Any part
	 * below or above that intersection is re-emitted with its original biome, so a broad
	 * Terralith point cannot drag mountain terrain into Waldschatten with it.
	 */
	public static Climate.ParameterList<Holder<Biome>> claimIn(Climate.ParameterList<Holder<Biome>> original) {
		HolderGetter<Biome> lookup = biomeLookup;
		if (lookup == null) {
			Waldschatten.LOGGER.warn("Waldschatten could not claim any ground: no biome lookup was captured.");
			return original;
		}
		Optional<Holder.Reference<Biome>> ours = lookup.get(WALDSCHATTEN);
		if (ours.isEmpty()) {
			// The biome is not in this world's registry at all (a datapack disabled it, or
			// this is some other dimension's source). Leave everything alone.
			return original;
		}

		List<Pair<Climate.ParameterPoint, Holder<Biome>>> claimed = new ArrayList<>();
		int slices = 0;
		int candidates = 0;
		for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : original.values()) {
			if (!isFlatSourceClimate(entry.getSecond())) {
				claimed.add(entry);
				continue;
			}

			candidates++;
			Climate.ParameterPoint point = entry.getFirst();
			long continentalMin = Math.max(point.continentalness().min(), LOWLAND_CONTINENTAL_MIN);
			long continentalMax = Math.min(point.continentalness().max(), LOWLAND_CONTINENTAL_MAX);
			long intersectionMin = Math.max(point.erosion().min(), LOWLAND_MIN);
			long intersectionMax = Math.min(point.erosion().max(), LOWLAND_MAX);
			long weirdnessMin = Math.max(point.weirdness().min(), LOWLAND_WEIRDNESS_MIN);
			long weirdnessMax = Math.min(point.weirdness().max(), LOWLAND_WEIRDNESS_MAX);
			if (continentalMin > continentalMax || intersectionMin > intersectionMax
					|| weirdnessMin > weirdnessMax) {
				claimed.add(entry);
				continue;
			}

			if (point.continentalness().min() < continentalMin) {
				claimed.add(Pair.of(withContinentalness(
						point, point.continentalness().min(), continentalMin - 1), entry.getSecond()));
			}
			if (continentalMax < point.continentalness().max()) {
				claimed.add(Pair.of(withContinentalness(
						point, continentalMax + 1, point.continentalness().max()), entry.getSecond()));
			}
			Climate.ParameterPoint middle = withContinentalness(point, continentalMin, continentalMax);

			// Climate ranges are inclusive. The +/- 1 keeps the three slices disjoint while
			// preserving every representable coordinate from the source point.
			if (point.erosion().min() < intersectionMin) {
				claimed.add(Pair.of(withErosion(middle, point.erosion().min(), intersectionMin - 1), entry.getSecond()));
			}
			Climate.ParameterPoint flatMiddle = withErosion(middle, intersectionMin, intersectionMax);
			if (point.weirdness().min() < weirdnessMin) {
				claimed.add(Pair.of(withWeirdness(
						flatMiddle, point.weirdness().min(), weirdnessMin - 1), entry.getSecond()));
			}
			claimed.add(Pair.of(withWeirdness(flatMiddle, weirdnessMin, weirdnessMax), ours.get()));
			slices++;
			if (weirdnessMax < point.weirdness().max()) {
				claimed.add(Pair.of(withWeirdness(
						flatMiddle, weirdnessMax + 1, point.weirdness().max()), entry.getSecond()));
			}
			if (intersectionMax < point.erosion().max()) {
				claimed.add(Pair.of(withErosion(middle, intersectionMax + 1, point.erosion().max()), entry.getSecond()));
			}
		}

		if (slices == 0) {
			// This runs for every multi-noise source. The Nether has no forest entries, so it
			// should stay quiet; a forest-bearing source with no usable lowland slice is news.
			if (candidates > 0) {
				Waldschatten.LOGGER.warn(
						"Waldschatten claimed NOTHING from a source with {} flat-source climate entries — the "
								+ "biome will not generate in this world.", candidates);
			}
			return original;
		}
		Waldschatten.LOGGER.info(
				"Waldschatten claimed {} exact lowland slices from {} flat-source climate entries "
						+ "({} source entries became {} entries after lossless splitting).",
				slices, candidates, original.values().size(), claimed.size());
		return new Climate.ParameterList<>(claimed);
	}

	private static boolean isFlatSourceClimate(Holder<Biome> biome) {
		return biome.is(Biomes.SWAMP);
	}

	static Climate.ParameterPoint withErosion(Climate.ParameterPoint point, long min, long max) {
		return new Climate.ParameterPoint(
				point.temperature(),
				point.humidity(),
				point.continentalness(),
				new Climate.Parameter(min, max),
				point.depth(),
				point.weirdness(),
				point.offset());
	}

	static Climate.ParameterPoint withContinentalness(Climate.ParameterPoint point, long min, long max) {
		return new Climate.ParameterPoint(
				point.temperature(),
				point.humidity(),
				new Climate.Parameter(min, max),
				point.erosion(),
				point.depth(),
				point.weirdness(),
				point.offset());
	}

	static Climate.ParameterPoint withWeirdness(Climate.ParameterPoint point, long min, long max) {
		return new Climate.ParameterPoint(
				point.temperature(),
				point.humidity(),
				point.continentalness(),
				point.erosion(),
				point.depth(),
				new Climate.Parameter(min, max),
				point.offset());
	}

	public static void register() {
		logPlacement();
	}

	private static void logPlacement() {
		Waldschatten.LOGGER.info(
				"Waldschatten will claim exact flat/gentle lowland slices from swamp climates "
						+ "in each multi-noise biome source. Sources without an overlapping forest "
						+ "entry remain unchanged.");
	}


	private WaldschattenWorldgen() {
	}
}
