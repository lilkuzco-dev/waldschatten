package dev.lilkuzco.waldschatten.worldgen;

import com.mojang.serialization.MapCodec;
import dev.lilkuzco.waldschatten.mixin.ChunkGeneratorStructureStateAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Elects at least one deterministic hut anchor in every contiguous Waldschatten patch.
 *
 * <p>A chunk is an anchor when its centre is Waldschatten and neither its west nor north
 * neighbour is Waldschatten. Every finite cardinally connected set of chunks has such a
 * north-west boundary cell. Some concave patches can elect more than one; none elect zero.
 *
 * <p>This extends random-spread placement with a 1x1 grid so vanilla's structure locator
 * can still enumerate candidates. Randomness is deliberately absent from the final test.
 */
public final class WaldschattenPatchPlacement extends RandomSpreadStructurePlacement {

	public static final MapCodec<WaldschattenPatchPlacement> CODEC =
			MapCodec.unit(WaldschattenPatchPlacement::new);

	private static final int SURFACE_QUART_Y = 64 >> 2;

	public WaldschattenPatchPlacement() {
		super(1, 0, RandomSpreadType.LINEAR, 793214077);
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
		BiomeSource source = ((ChunkGeneratorStructureStateAccessor) (Object) state)
				.waldschatten$getBiomeSource();
		return isPatchAnchor(source, state.randomState().sampler(), chunkX, chunkZ);
	}

	public static boolean isPatchAnchor(
			BiomeSource source, net.minecraft.world.level.biome.Climate.Sampler sampler,
			int chunkX, int chunkZ) {
		return isWaldschatten(source, sampler, chunkX, chunkZ)
				&& !isWaldschatten(source, sampler, chunkX - 1, chunkZ)
				&& !isWaldschatten(source, sampler, chunkX, chunkZ - 1);
	}

	private static boolean isWaldschatten(
			BiomeSource source, net.minecraft.world.level.biome.Climate.Sampler sampler,
			int chunkX, int chunkZ) {
		return source.getNoiseBiome(
				(chunkX << 2) + 2,
				SURFACE_QUART_Y,
				(chunkZ << 2) + 2,
				sampler).is(WaldschattenWorldgen.WALDSCHATTEN);
	}

	@Override
	public StructurePlacementType<?> type() {
		return WaldschattenWorldgen.PATCH_ANCHOR_PLACEMENT;
	}
}
