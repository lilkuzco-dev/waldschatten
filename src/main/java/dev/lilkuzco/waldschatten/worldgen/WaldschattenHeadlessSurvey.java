package dev.lilkuzco.waldschatten.worldgen;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.Waldschatten;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
		if (spawn.getY() <= level.getMinY()) {
			throw new AssertionError("Default spawn is at or below the world floor for seed "
					+ seed + ": " + spawn);
		}
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
			Cell start = findWaldschattenCell(source, sampler, nearest.getFirst());
			if (start == null) {
				throw new AssertionError("Could not find the chunk-centre Waldschatten patch for seed " + seed);
			}

			ArrayDeque<Cell> open = new ArrayDeque<>();
			Set<Cell> seen = new HashSet<>();
			open.add(start);
			while (!open.isEmpty() && terrainSamples < 64) {
				Cell cell = open.removeFirst();
				if (!seen.add(cell) || !isWaldschatten(source, sampler, cell.x(), cell.z())) {
					continue;
				}
				int blockX = (cell.x() << 4) + 8;
				int blockZ = (cell.z() << 4) + 8;
				level.getChunkAt(new BlockPos(blockX, SURVEY_Y, blockZ));
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
				minGround = Math.min(minGround, ground);
				maxGround = Math.max(maxGround, ground);
				terrainSamples++;
				open.addLast(new Cell(cell.x() - 1, cell.z()));
				open.addLast(new Cell(cell.x() + 1, cell.z()));
				open.addLast(new Cell(cell.x(), cell.z() - 1));
				open.addLast(new Cell(cell.x(), cell.z() + 1));
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

	private static Cell findWaldschattenCell(BiomeSource source, Climate.Sampler sampler, BlockPos near) {
		int centreX = Math.floorDiv(near.getX(), 16);
		int centreZ = Math.floorDiv(near.getZ(), 16);
		for (int radius = 0; radius <= 4; radius++) {
			for (int x = centreX - radius; x <= centreX + radius; x++) {
				for (int z = centreZ - radius; z <= centreZ + radius; z++) {
					if (isWaldschatten(source, sampler, x, z)) {
						return new Cell(x, z);
					}
				}
			}
		}
		return null;
	}

	private static boolean isWaldschatten(
			BiomeSource source, Climate.Sampler sampler, int chunkX, int chunkZ) {
		return source.getNoiseBiome((chunkX << 2) + 2, SURVEY_Y >> 2, (chunkZ << 2) + 2, sampler)
				.is(WaldschattenWorldgen.WALDSCHATTEN);
	}

	private record Cell(int x, int z) {
	}

	private static String pct(int n, int total) {
		return total == 0 ? "0" : String.format("%.2f", 100.0 * n / total);
	}

	private WaldschattenHeadlessSurvey() {
	}
}
