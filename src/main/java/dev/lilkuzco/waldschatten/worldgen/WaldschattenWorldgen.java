package dev.lilkuzco.waldschatten.worldgen;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.Waldschatten;
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
	private static final long HILLY_EROSION_MIN = Climate.quantizeCoord(-0.375F);

	private static int claimed = 0;

	/** Called at the head of each preset build, so the count is per-build not cumulative. */
	public static void resetClaimCount() {
		claimed = 0;
	}

	/**
	 * Called for every entry vanilla adds to the overworld preset, via the mixin.
	 * Returns the entry unchanged, or the same climate point re-pointed at Waldschatten.
	 */
	public static Pair<Climate.ParameterPoint, ResourceKey<Biome>> claim(
			Pair<Climate.ParameterPoint, ResourceKey<Biome>> entry) {
		if (!Biomes.DARK_FOREST.equals(entry.getSecond())) {
			return entry;
		}
		if (entry.getFirst().erosion().min() != HILLY_EROSION_MIN) {
			return entry;
		}
		claimed++;
		return Pair.of(entry.getFirst(), WALDSCHATTEN);
	}

	public static void register() {
		logPlacement();
	}

	private static void logPlacement() {
		Waldschatten.LOGGER.info(
				"Waldschatten will claim vanilla dark-forest points on the hilly erosion band. "
						+ "A world whose biome source is replaced (Terralith, Terrablender, a custom "
						+ "dimension) never builds that preset and will not contain the biome — that "
						+ "is expected, not a bug.");
	}

	/**
	 * Reports what was actually taken, from the tail of the preset build.
	 *
	 * <p>Logged there and not at mod init because the preset is built lazily, long after
	 * {@code onInitialize} — an init-time log of this counter prints 0 every single time and
	 * reads exactly like a broken injection.
	 */
	public static void logClaimed() {
		Waldschatten.LOGGER.info("Waldschatten claimed {} dark-forest climate point(s) from the "
				+ "vanilla overworld preset.", claimed);
	}

	private WaldschattenWorldgen() {
	}
}
