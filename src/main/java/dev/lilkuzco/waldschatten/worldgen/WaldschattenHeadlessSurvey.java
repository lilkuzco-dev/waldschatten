package dev.lilkuzco.waldschatten.worldgen;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.Waldschatten;
import dev.lilkuzco.waldschatten.WaldschattenBlocks;
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
import net.minecraft.resources.Identifier;
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
	private static final ResourceKey<Biome> ENCHANTED_FOREST = ResourceKey.create(
			Registries.BIOME, Identifier.fromNamespaceAndPath("enchanted_forest", "enchanted_forest"));

	public static void run(ServerLevel level, long seed) {
		BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
		boolean present = source.possibleBiomes().stream()
				.anyMatch(holder -> holder.is(WaldschattenWorldgen.WALDSCHATTEN));
		if (!present) {
			throw new AssertionError("Waldschatten is absent from seed " + seed);
		}

		// The other empire mod that rewrites the same parameter list. 0.1.2 passed twelve
		// surveys alone and was absent from every real world because the two claims
		// collided; a survey that does not prove BOTH biomes land beside each other does not
		// answer the shipping question. Asserted whenever the mod is loaded, and the run
		// configs stage it, so "it was not there" cannot quietly turn this check off.
		boolean besideEnchantedForest = FabricLoader.getInstance().isModLoaded("enchanted_forest");
		if (besideEnchantedForest) {
			boolean theirsPresent = source.possibleBiomes().stream()
					.anyMatch(holder -> holder.is(ENCHANTED_FOREST));
			if (!theirsPresent) {
				throw new AssertionError("Enchanted Forest is loaded but its biome is absent from seed "
						+ seed + " — the two parameter-list claims are colliding again");
			}
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
		int theirs = counts.getOrDefault(ENCHANTED_FOREST, 0);
		if (besideEnchantedForest && theirs == 0) {
			throw new AssertionError("Enchanted Forest is loaded but sampled 0 of " + samples
					+ " cells for seed " + seed + " — its claim did not land");
		}
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

		// Trees must not stack. 0.1.0–0.1.3 placed the twisted trees bare inside the
		// vegetation selector — no would_survive filter — and sixteen attempts per chunk on an
		// OCEAN_FLOOR heightmap that counts leaves as floor built towers of trees (measured
		// 83 blocks of log over 72 of ground in a live world; reported at 300+, and the same
		// world held twisted_log at y=319). A single twisted tree is at most 15 blocks of
		// trunk plus a few branch steps, and a neighbour's branch can reach into the column
		// above it (measured: 17 on seed 0 with the fix in place, 15 on the other eleven).
		// Stacking is never subtle — it is all-or-nothing across four hundred attempts in
		// these 25 chunks — so the bar is two whole trees: over 32 means a tree grew on a tree.
		int logSpan = tallestLogSpan(level, nearest.getFirst());
		if (logSpan > 32) {
			throw new AssertionError("Twisted trees are stacking for seed " + seed + ": a column near "
					+ nearest.getFirst() + " carries " + logSpan + " blocks of twisted_log");
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
				int ground = groundHeight(level, blockX, blockZ);
				minGround = Math.min(minGround, ground);
				maxGround = Math.max(maxGround, ground);
				terrainSamples++;
				open.addLast(new Cell(cell.x() - 1, cell.z()));
				open.addLast(new Cell(cell.x() + 1, cell.z()));
				open.addLast(new Cell(cell.x(), cell.z() - 1));
				open.addLast(new Cell(cell.x(), cell.z() + 1));
			}
			relief = maxGround - minGround;
			// 48, not the 24 that stood from 0.1.1 to 0.1.3: that bar was never measured
			// against real terrain (see groundHeight). Honest values on the six release
			// seeds, 2026-08-22: 0, 0, 14, 17, 29, 36 — gently rolling over ~64 chunk
			// centres, which is what the erosion slice promises. A mountain reads 100+.
			if (relief > 48) {
				throw new AssertionError("Waldschatten terrain is too steep for seed " + seed
						+ ": " + relief + " blocks of relief across a 128-block sample");
			}
		}
		Waldschatten.LOGGER.info(
				"WALDSCHATTEN_HEADLESS PASS seed={} spawn={} biomes={} samples={} waldschatten={} ({}%) "
						+ "dark_forest={} forest={} enchanted_forest={} ({}%) nearest={} blocks hut={} blocks "
						+ "relief={} blocks",
				seed, spawn, source.possibleBiomes().size(), samples, ours, pct(ours, samples),
				darkForest, forest, besideEnchantedForest ? theirs : -1, pct(theirs, samples),
				biomeDistance, hutDistance, relief);
	}

	/**
	 * Real ground at a column, through the trees.
	 *
	 * <p>Two things this must do that {@code getHeight} alone does not. First, generate the
	 * chunk: in 26.2 a height query on an ungenerated chunk answers from an empty placeholder
	 * at the world floor, and the relief probe had been reading exactly that — every column
	 * −64, relief 0, six seeds on record as "flat" that were never measured. Second, step
	 * down through logs and leaves, because MOTION_BLOCKING_NO_LEAVES stops on a trunk and a
	 * 15-block tree is not terrain.
	 */
	private static int groundHeight(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y - 1, z);
		while (pos.getY() > level.getMinY()) {
			var state = level.getBlockState(pos);
			if (!state.isAir() && !state.is(net.minecraft.tags.BlockTags.LOGS) && !state.is(net.minecraft.tags.BlockTags.LEAVES)
					&& !state.is(WaldschattenBlocks.TWISTED_LOG) && !state.canBeReplaced()) {
				return pos.getY() + 1;
			}
			pos.move(0, -1, 0);
		}
		return y;
	}

	/**
	 * Generates the 5x5 chunks around {@code near} and returns the tallest vertical run of
	 * twisted_log in any column (topmost log minus lowest log, plus one).
	 */
	private static int tallestLogSpan(ServerLevel level, BlockPos near) {
		int centreX = Math.floorDiv(near.getX(), 16);
		int centreZ = Math.floorDiv(near.getZ(), 16);
		int worst = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = centreX - 2; cx <= centreX + 2; cx++) {
			for (int cz = centreZ - 2; cz <= centreZ + 2; cz++) {
				level.getChunk(cx, cz); // forces generation on the dedicated server
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						int lowest = Integer.MAX_VALUE;
						int highest = Integer.MIN_VALUE;
						for (int y = level.getMinY(); y < level.getMaxY(); y++) {
							pos.set((cx << 4) + x, y, (cz << 4) + z);
							if (level.getBlockState(pos).is(WaldschattenBlocks.TWISTED_LOG)) {
								lowest = Math.min(lowest, y);
								highest = Math.max(highest, y);
							}
						}
						if (highest >= lowest) {
							worst = Math.max(worst, highest - lowest + 1);
						}
					}
				}
			}
		}
		Waldschatten.LOGGER.info("WALDSCHATTEN_HEADLESS tallest twisted_log column span over 25 chunks: {}", worst);
		return worst;
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
