package dev.lilkuzco.waldschatten.worldgen;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.Waldschatten;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Server-only release survey, enabled by a system property and never active in normal play. */
public final class WaldschattenHeadlessSurvey {

	private static final int SURVEY_RADIUS = 4096;
	private static final int SURVEY_STEP = 128;
	private static final int SURVEY_Y = 64;
	private static final int MAX_SPAWN_DISTANCE = 1000;

	public static void run(ServerLevel level, long seed) {
		BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
		boolean present = source.possibleBiomes().stream()
				.anyMatch(holder -> holder.is(WaldschattenWorldgen.WALDSCHATTEN));
		if (!present) {
			throw new AssertionError("Waldschatten is absent from seed " + seed);
		}

		Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
		Map<ResourceKey<Biome>, Integer> counts = new HashMap<>();
		int samples = 0;
		for (int x = -SURVEY_RADIUS; x <= SURVEY_RADIUS; x += SURVEY_STEP) {
			for (int z = -SURVEY_RADIUS; z <= SURVEY_RADIUS; z += SURVEY_STEP) {
				Holder<Biome> biome = source.getNoiseBiome(x >> 2, SURVEY_Y >> 2, z >> 2, sampler);
				biome.unwrapKey().ifPresent(key -> counts.merge(key, 1, Integer::sum));
				samples++;
			}
		}

		int ours = counts.getOrDefault(WaldschattenWorldgen.WALDSCHATTEN, 0);
		int darkForest = counts.getOrDefault(Biomes.DARK_FOREST, 0);
		int forest = counts.getOrDefault(Biomes.FOREST, 0);
		BlockPos spawn = level.getRespawnData().pos();
		Pair<BlockPos, Holder<Biome>> nearest = level.findClosestBiome3d(
				holder -> holder.is(WaldschattenWorldgen.WALDSCHATTEN),
				spawn, MAX_SPAWN_DISTANCE, 32, 64);
		if (nearest == null) {
			throw new AssertionError("No Waldschatten within " + MAX_SPAWN_DISTANCE
					+ " blocks of spawn for seed " + seed + " (spawn "
					+ spawn.getX() + "," + spawn.getY() + "," + spawn.getZ() + ")");
		}

		Holder<Structure> hut = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.get(ResourceKey.create(Registries.STRUCTURE, Waldschatten.id("witch_hut")))
				.orElseThrow(() -> new AssertionError("Waldschatten witch hut is absent from the structure registry"));
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(hut), spawn, 128, false);
		if (found == null) {
			throw new AssertionError("No Waldschatten witch hut within 128 chunks of spawn for seed " + seed);
		}

		int biomeDistance = (int) Math.sqrt(nearest.getFirst().distSqr(spawn));
		int hutDistance = (int) Math.sqrt(found.getFirst().distSqr(spawn));
		boolean liveTerrainStack = FabricLoader.getInstance().isModLoaded("terralith")
				&& FabricLoader.getInstance().isModLoaded("empire_worldgen");
		int relief = -1;
		if (liveTerrainStack) {
			int minGround = Integer.MAX_VALUE;
			int maxGround = Integer.MIN_VALUE;
			int terrainSamples = 0;
			BlockPos centre = nearest.getFirst();
			for (int x = centre.getX() - 64; x <= centre.getX() + 64; x += 32) {
				for (int z = centre.getZ() - 64; z <= centre.getZ() + 64; z += 32) {
					Holder<Biome> biome = source.getNoiseBiome(x >> 2, SURVEY_Y >> 2, z >> 2, sampler);
					if (!biome.is(WaldschattenWorldgen.WALDSCHATTEN)) {
						continue;
					}
					int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					minGround = Math.min(minGround, ground);
					maxGround = Math.max(maxGround, ground);
					terrainSamples++;
				}
			}
			if (terrainSamples < 4) {
				throw new AssertionError("Could not collect a meaningful Waldschatten terrain sample for seed " + seed);
			}
			relief = maxGround - minGround;
			if (relief > 24) {
				throw new AssertionError("Waldschatten terrain is too steep for seed " + seed
						+ ": " + relief + " blocks of relief across a 128-block sample");
			}
		}
		Waldschatten.LOGGER.info(
				"WALDSCHATTEN_HEADLESS PASS seed={} spawn={} biomes={} samples={} waldschatten={} ({}%) "
						+ "dark_forest={} forest={} nearest={} blocks hut={} blocks relief={} blocks",
				seed, spawn, source.possibleBiomes().size(), samples, ours, pct(ours, samples),
				darkForest, forest, biomeDistance, hutDistance, relief);
	}

	private static String pct(int n, int total) {
		return total == 0 ? "0" : String.format("%.2f", 100.0 * n / total);
	}

	private WaldschattenHeadlessSurvey() {
	}
}
