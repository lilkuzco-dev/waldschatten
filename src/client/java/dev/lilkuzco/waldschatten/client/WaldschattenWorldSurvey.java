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
	/** Stable, deliberately varied seeds; includes the empire server's previous seed. */
	private static final long[] SPAWN_SEEDS = {
			0L, 1L, -1L, 8675309L, -160353759327030922L, Long.MAX_VALUE
	};
	/** Discoverability is a release requirement, not a line in marketing copy. */
	private static final int MAX_SPAWN_DISTANCE = 1000;

	@Override
	public void runTest(ClientGameTestContext context) {
		// Normal worlds rather than the battery's fixed one — the whole point is to ask a
		// generator that was allowed to make its own mind up. Fixed seeds make regressions
		// reproducible and exercise more than one lucky origin.
		for (long seed : SPAWN_SEEDS) {
			try (TestSingleplayerContext world = context.worldBuilder()
					.setUseConsistentSettings(false)
					.adjustSettings(settings -> settings.setSeed(Long.toString(seed)))
					.create()) {
				context.waitTicks(100);

				world.getServer().runOnServer(mcServer -> {
					ServerLevel level = mcServer.overworld();
					BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();

					// Which worldgen is this? The answer changes what the numbers mean, and a
					// survey that does not say is a survey nobody can act on.
					var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
					Waldschatten.LOGGER.info(
							"WALDSCHATTEN_SURVEY seed={} worldgen context: terralith={} lithostitched={} biomeSource={}",
							seed, loader.isModLoaded("terralith"), loader.isModLoaded("lithostitched"),
							source.getClass().getSimpleName());

					int possible = source.possibleBiomes().size();
					boolean present = source.possibleBiomes().stream()
							.anyMatch(holder -> holder.is(WaldschattenWorldgen.WALDSCHATTEN));
					Waldschatten.LOGGER.info(
							"WALDSCHATTEN_SURVEY seed={} world offers {} biomes; waldschatten present: {}",
							seed, possible, present);
					if (!present) {
						throw new AssertionError("Waldschatten is absent from seed " + seed);
					}

					survey(level, source);
					distances(level, seed);
				});
				context.waitTicks(20);
			}
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
	private static void distances(ServerLevel level, long seed) {
		BlockPos origin = level.getRespawnData().pos();

		Pair<BlockPos, Holder<Biome>> nearest = level.findClosestBiome3d(
				holder -> holder.is(WaldschattenWorldgen.WALDSCHATTEN), origin, MAX_SPAWN_DISTANCE, 32, 64);
		if (nearest == null) {
			throw new AssertionError("No Waldschatten within " + MAX_SPAWN_DISTANCE
					+ " blocks of spawn for seed " + seed + " (spawn "
					+ origin.getX() + "," + origin.getY() + "," + origin.getZ() + ")");
		} else {
			BlockPos at = nearest.getFirst();
			Waldschatten.LOGGER.info(
					"WALDSCHATTEN_SURVEY seed={} spawn={} nearest waldschatten at {} {} — {} blocks from spawn",
					seed, origin, at.getX(), at.getZ(), (int) Math.sqrt(at.distSqr(origin)));
		}

		// The custom placement elects a deterministic anchor in each contiguous biome patch.
		// Searching the real structure state verifies that the codec, registry and locator
		// all agree — pure anchor arithmetic alone would miss an integration failure.
		level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.get(ResourceKey.create(Registries.STRUCTURE, Waldschatten.id("witch_hut")))
				.ifPresent(hut -> {
					Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
							.findNearestMapStructure(level, HolderSet.direct(hut), origin, 128, false);
					if (found == null) {
						throw new AssertionError("No Waldschatten witch hut within 128 chunks of spawn for seed " + seed);
					} else {
						BlockPos at = found.getFirst();
						Waldschatten.LOGGER.info(
								"WALDSCHATTEN_SURVEY seed={} nearest witch hut at {} {} — {} blocks from spawn",
								seed, at.getX(), at.getZ(), (int) Math.sqrt(at.distSqr(origin)));
					}
				});
	}

	private static String pct(int n, int total) {
		return total == 0 ? "0" : String.format("%.2f", 100.0 * n / total);
	}
}
