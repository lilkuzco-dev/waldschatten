package dev.lilkuzco.waldschatten.worldgen;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.Waldschatten;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

/** Ensures the default world spawn can never strand Waldschatten beyond discovery range. */
public final class WaldschattenSpawnAnchor {

	public static final int PROMISED_DISTANCE = 1000;
	private static final int FALLBACK_SEARCH_DISTANCE = 8192;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> ensureWithinReach(server.overworld()));
	}

	private static void ensureWithinReach(ServerLevel level) {
		BlockPos spawn = level.getRespawnData().pos();
		Pair<BlockPos, Holder<Biome>> nearby = find(level, spawn, PROMISED_DISTANCE);
		if (nearby != null) {
			Waldschatten.LOGGER.info("Waldschatten spawn guarantee satisfied from {} ({} blocks).",
					spawn, distance(spawn, nearby.getFirst()));
			return;
		}

		Pair<BlockPos, Holder<Biome>> fallback = find(level, spawn, FALLBACK_SEARCH_DISTANCE);
		if (fallback == null) {
			throw new IllegalStateException("No Waldschatten found within " + FALLBACK_SEARCH_DISTANCE
					+ " blocks; refusing to leave the 1,000-block spawn promise silently broken");
		}
		BlockPos biome = fallback.getFirst();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, biome.getX(), biome.getZ());
		BlockPos corrected = new BlockPos(biome.getX(), y, biome.getZ());
		level.setRespawnData(LevelData.RespawnData.of(level.dimension(), corrected, 0.0F, 0.0F));
		Waldschatten.LOGGER.warn(
				"Nearest Waldschatten was {} blocks from {}; moved default spawn to {} to enforce the 1,000-block promise.",
				distance(spawn, biome), spawn, corrected);
	}

	private static Pair<BlockPos, Holder<Biome>> find(ServerLevel level, BlockPos origin, int radius) {
		return level.findClosestBiome3d(
				holder -> holder.is(WaldschattenWorldgen.WALDSCHATTEN),
				origin, radius, 32, 64);
	}

	private static int distance(BlockPos a, BlockPos b) {
		return (int) Math.sqrt(a.distSqr(b));
	}

	private WaldschattenSpawnAnchor() {
	}
}
