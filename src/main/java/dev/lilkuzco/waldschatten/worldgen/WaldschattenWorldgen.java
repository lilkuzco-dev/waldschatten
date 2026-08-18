package dev.lilkuzco.waldschatten.worldgen;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.Waldschatten;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
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
	// The climate niche.
	//
	// Temperate and wet, which is dark forest's own band — Waldschatten is meant to be
	// the wood next door to the dark forest, not a curiosity in some unrelated climate.
	// It is separated from dark forest by erosion and weirdness rather than by climate:
	// only the hilly, low-weirdness slice, which is also the terrain the brief asks for
	// (rolling ground and root hollows rather than a flat forest floor).
	//
	// These are a starting point, not a measurement. Rarity is an empirical question and
	// the honest way to answer it is to generate a world and count — see the
	// "tuning the rarity" section of WALDSCHATTEN.md.
	// ---------------------------------------------------------------------------
	private static final Climate.Parameter TEMPERATURE = Climate.Parameter.span(0.2F, 0.55F);
	private static final Climate.Parameter HUMIDITY = Climate.Parameter.span(0.3F, 1.0F);
	private static final Climate.Parameter CONTINENTALNESS = Climate.Parameter.span(0.03F, 1.0F);
	private static final Climate.Parameter EROSION = Climate.Parameter.span(-0.375F, -0.2225F);
	private static final Climate.Parameter DEPTH = Climate.Parameter.point(0.0F);
	private static final Climate.Parameter WEIRDNESS = Climate.Parameter.span(-0.15F, 0.15F);
	/**
	 * Offset is a flat penalty on the distance to this point, so it is the rarity dial:
	 * 0 means "win this niche outright", higher means "only when nothing else is close".
	 */
	private static final float OFFSET = 0.0F;

	private static final List<Climate.ParameterPoint> POINTS = List.of(
			Climate.parameters(TEMPERATURE, HUMIDITY, CONTINENTALNESS, EROSION, DEPTH, WEIRDNESS, OFFSET));

	/** Called from the mixin at the tail of the vanilla overworld build. */
	public static void addOverworldBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
		for (Climate.ParameterPoint point : POINTS) {
			consumer.accept(Pair.of(point, WALDSCHATTEN));
		}
	}

	public static void register() {
		logPlacement();
	}

	private static void logPlacement() {
		Waldschatten.LOGGER.info(
				"Waldschatten claims {} multi-noise point(s) in the VANILLA overworld preset. "
						+ "A world whose biome source is replaced (Terralith, Terrablender, a custom "
						+ "dimension) will not contain the biome — that is expected, not a bug.",
				POINTS.size());
	}

	private WaldschattenWorldgen() {
	}
}
