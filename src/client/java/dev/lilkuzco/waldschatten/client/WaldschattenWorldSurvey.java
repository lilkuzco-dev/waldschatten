package dev.lilkuzco.waldschatten.client;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.Waldschatten;
import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Does this biome actually turn up in a world nobody painted?
 *
 * <p>Separate from the render battery on purpose. That battery runs in the gametest's
 * default world, which offers exactly <b>one</b> biome — perfect for photographing colours
 * and rules, structurally incapable of saying anything about placement. Everything proved
 * so far is upstream of the question a player actually asks: the biome is in the vanilla
 * preset, the structures are gated to the biome, the features resolve. None of that is the
 * same as "you will find one".
 *
 * <p>So this builds a real world and asks the generator three things:
 * <ol>
 *   <li><b>Is it in this world's biome source at all?</b> The yes/no that everything else
 *       rests on.</li>
 *   <li><b>How much of the world is it?</b> Sampled straight off the biome source, which
 *       needs no chunks generated, and reported <em>beside vanilla dark forest from the same
 *       samples</em> — a bare percentage means nothing without something to hold it against,
 *       and dark forest is the biome this one was deliberately cut out of.</li>
 *   <li><b>How far to the nearest one, and to the nearest witch hut?</b> The distances a
 *       player would actually walk.</li>
 * </ol>
 */
public class WaldschattenWorldSurvey implements FabricClientGameTest {

	/** Half-width of the surveyed square, in blocks. */
	private static final int SURVEY_RADIUS = 4096;
	/** Distance between samples, in blocks. */
	private static final int SURVEY_STEP = 128;
	/** Sampled at sea level: these are surface biomes, and that is where a player meets them. */
	private static final int SURVEY_Y = 64;

	@Override
	public void runTest(ClientGameTestContext context) {
		// A normal world rather than the battery's fixed one — the whole point is to ask a
		// generator that was allowed to make its own mind up.
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
			context.waitTicks(100);

			world.getServer().runOnServer(mcServer -> {
				ServerLevel level = mcServer.overworld();
				BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();

				int possible = source.possibleBiomes().size();
				boolean present = source.possibleBiomes().stream()
						.anyMatch(holder -> holder.is(WaldschattenWorldgen.WALDSCHATTEN));
				Waldschatten.LOGGER.info(
						"WALDSCHATTEN_SURVEY world offers {} biomes; waldschatten present in the biome source: {}",
						possible, present);
				if (!present) {
					Waldschatten.LOGGER.warn("WALDSCHATTEN_SURVEY biome is absent from this world — nothing below is meaningful");
					return;
				}

				survey(level, source);
				distances(level);
			});
			context.waitTicks(20);
		}
	}

	/** Count how often each biome comes up on a coarse grid, straight off the biome source. */
	private static void survey(ServerLevel level, BiomeSource source) {
		Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
		int quartY = SURVEY_Y >> 2;

		Map<ResourceKey<Biome>, Integer> counts = new HashMap<>();
		int samples = 0;
		for (int x = -SURVEY_RADIUS; x <= SURVEY_RADIUS; x += SURVEY_STEP) {
			for (int z = -SURVEY_RADIUS; z <= SURVEY_RADIUS; z += SURVEY_STEP) {
				Holder<Biome> biome = source.getNoiseBiome(x >> 2, quartY, z >> 2, sampler);
				biome.unwrapKey().ifPresent(key -> counts.merge(key, 1, Integer::sum));
				samples++;
			}
		}

		int ours = counts.getOrDefault(WaldschattenWorldgen.WALDSCHATTEN, 0);
		int darkForest = counts.getOrDefault(Biomes.DARK_FOREST, 0);
		int forest = counts.getOrDefault(Biomes.FOREST, 0);

		Waldschatten.LOGGER.info(
				"WALDSCHATTEN_SURVEY {} samples over {}x{} blocks at y={} -- waldschatten {} ({}%), "
						+ "vanilla dark_forest {} ({}%), vanilla forest {} ({}%)",
				samples, SURVEY_RADIUS * 2, SURVEY_RADIUS * 2, SURVEY_Y,
				ours, pct(ours, samples),
				darkForest, pct(darkForest, samples),
				forest, pct(forest, samples));
	}

	/** How far would a player actually have to walk? */
	private static void distances(ServerLevel level) {
		BlockPos origin = BlockPos.ZERO;

		Pair<BlockPos, Holder<Biome>> nearest = level.findClosestBiome3d(
				holder -> holder.is(WaldschattenWorldgen.WALDSCHATTEN), origin, 3200, 32, 64);
		if (nearest == null) {
			Waldschatten.LOGGER.info("WALDSCHATTEN_SURVEY no waldschatten within 3200 blocks of origin");
		} else {
			BlockPos at = nearest.getFirst();
			Waldschatten.LOGGER.info("WALDSCHATTEN_SURVEY nearest waldschatten at {} {} — {} blocks from origin",
					at.getX(), at.getZ(), (int) Math.sqrt(at.distSqr(origin)));
		}

		// The hut is gated to the biome on top of its own spacing, so its rarity compounds
		// the biome's. Searching for it directly is the only honest way to know the result.
		level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.get(ResourceKey.create(Registries.STRUCTURE, Waldschatten.id("witch_hut")))
				.ifPresent(hut -> {
					Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
							.findNearestMapStructure(level, HolderSet.direct(hut), origin, 48, false);
					if (found == null) {
						Waldschatten.LOGGER.info("WALDSCHATTEN_SURVEY no witch hut within a 48-chunk search of origin");
					} else {
						BlockPos at = found.getFirst();
						Waldschatten.LOGGER.info("WALDSCHATTEN_SURVEY nearest witch hut at {} {} — {} blocks from origin",
								at.getX(), at.getZ(), (int) Math.sqrt(at.distSqr(origin)));
					}
				});
	}

	private static String pct(int n, int total) {
		return total == 0 ? "0" : String.format("%.2f", 100.0 * n / total);
	}
}
