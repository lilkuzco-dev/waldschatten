package dev.lilkuzco.waldschatten.worldgen;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.Waldschatten;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * Where Waldschatten sits in the world.
 *
 * <h2>Why this is a mixin and not an API call</h2>
 * Fabric API ships {@code NetherBiomes} and {@code TheEndBiomes} and nothing for the
 * overworld — verified against fabric-biome-api-v1 18.0.6, which is what this mod builds
 * against. The overworld's biome layout comes from
 * {@code MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD}, which is built in code by
 * {@code OverworldBiomeBuilder.addBiomes}, so appending to that consumer is the injection
 * point. {@link dev.lilkuzco.waldschatten.mixin.OverworldBiomeBuilderMixin} does exactly
 * that and nothing else.
 *
 * <h2>What this does NOT do</h2>
 * <b>It is a no-op on a Terralith world, silently.</b> Terralith replaces the overworld
 * biome source outright, so a world generated with it never asks the vanilla preset what
 * biomes exist and never sees these entries. That is the single most important thing to
 * know about this file, because the empire server runs Terralith (CLAUDE.md rule 2) and
 * "no crash, no log line, no biome" is exactly the invisible failure this repo keeps
 * getting bitten by. {@link #logPlacement} therefore says out loud, at startup, what was
 * injected — so a missing biome is a discrepancy between the log and the world rather
 * than a mystery. Shipping to a Terralith world needs a separate decision and a
 * lithostitched or TerraBlender route; see WALDSCHATTEN.md.
 */
public final class WaldschattenWorldgen {

	public static final ResourceKey<Biome> WALDSCHATTEN =
			ResourceKey.create(Registries.BIOME, Waldschatten.id("waldschatten"));

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
	// So instead of competing with dark forest, Waldschatten TAKES a defined piece of it:
	// every dark forest entry vanilla emits on the hilly erosion band becomes Waldschatten
	// instead. That is a real, bounded, explainable slice — "the dark forest that grows on
	// broken ground" — it inherits dark forest's whole climate envelope, and its frequency
	// is a known fraction of a biome whose frequency is already known.
	// ---------------------------------------------------------------------------

	/** Erosion band 2 (-0.375 .. -0.2225): vanilla's hilly, broken ground. */
	private static final long HILLY_MIN = Climate.quantizeCoord(-0.375F);
	private static final long HILLY_MAX = Climate.quantizeCoord(-0.2225F);

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
	 * <p>Every dark forest entry that overlaps the hilly erosion band becomes Waldschatten.
	 * Overlap rather than exact equality on purpose: vanilla emits that band both alone and
	 * spanned together with its neighbour, and a mod's list may be shaped differently again.
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
		int taken = 0;
		int darkForest = 0;
		for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : original.values()) {
			if (entry.getSecond().is(Biomes.DARK_FOREST)) {
				darkForest++;
			}
			if (entry.getSecond().is(Biomes.DARK_FOREST) && overlapsHillyErosion(entry.getFirst())) {
				claimed.add(Pair.of(entry.getFirst(), ours.get()));
				taken++;
			} else {
				claimed.add(entry);
			}
		}

		if (taken == 0) {
			// Only worth a warning if there was dark forest here to claim from. This runs for
			// EVERY multi-noise source, and the Nether has five entries and no dark forest —
			// warning about that is noise that trains people to ignore the warning that
			// matters, which is a dark forest we somehow failed to take a slice of.
			if (darkForest > 0) {
				Waldschatten.LOGGER.warn(
						"Waldschatten claimed NOTHING from a source with {} dark forest entries — the "
								+ "biome will not generate in this world.", darkForest);
			}
			return original;
		}
		Waldschatten.LOGGER.info("Waldschatten claimed {} of {} climate entries from this world's biome source.",
				taken, original.values().size());
		return new Climate.ParameterList<>(claimed);
	}

	private static boolean overlapsHillyErosion(Climate.ParameterPoint point) {
		return point.erosion().min() <= HILLY_MAX && point.erosion().max() >= HILLY_MIN;
	}

	public static void register() {
		logPlacement();
	}

	private static void logPlacement() {
		Waldschatten.LOGGER.info(
				"Waldschatten will claim dark-forest points on the hilly erosion band directly "
						+ "from each multi-noise biome source. Sources without an overlapping dark-forest "
						+ "entry remain unchanged.");
	}


	private WaldschattenWorldgen() {
	}
}
