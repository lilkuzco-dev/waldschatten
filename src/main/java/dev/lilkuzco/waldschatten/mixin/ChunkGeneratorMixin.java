package dev.lilkuzco.waldschatten.mixin;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.worldgen.WaldschattenPatchPlacement;
import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the 1x1 per-patch hut placement from turning /locate into 65,000 jigsaw probes. */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

	@Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
	private void waldschatten$locatePatchHut(
			ServerLevel level, HolderSet<Structure> requested, BlockPos centre, int radius,
			boolean skipKnownStructures,
			CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
		List<Holder<Structure>> structures = requested.stream().toList();
		if (structures.size() != 1 || !structures.getFirst().is(WaldschattenWorldgen.WITCH_HUT)) {
			return;
		}

		BiomeSource source = ((ChunkGenerator) (Object) this).getBiomeSource();
		Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
		int centreChunkX = Math.floorDiv(centre.getX(), 16);
		int centreChunkZ = Math.floorDiv(centre.getZ(), 16);
		for (int ring = 0; ring <= radius; ring++) {
			for (int x = centreChunkX - ring; x <= centreChunkX + ring; x++) {
				Pair<BlockPos, Holder<Structure>> north = anchorAt(
						source, sampler, structures.getFirst(), x, centreChunkZ - ring);
				if (north != null) {
					cir.setReturnValue(north);
					return;
				}
				if (ring > 0) {
					Pair<BlockPos, Holder<Structure>> south = anchorAt(
							source, sampler, structures.getFirst(), x, centreChunkZ + ring);
					if (south != null) {
						cir.setReturnValue(south);
						return;
					}
				}
			}
			for (int z = centreChunkZ - ring + 1; z < centreChunkZ + ring; z++) {
				Pair<BlockPos, Holder<Structure>> west = anchorAt(
						source, sampler, structures.getFirst(), centreChunkX - ring, z);
				if (west != null) {
					cir.setReturnValue(west);
					return;
				}
				if (ring > 0) {
					Pair<BlockPos, Holder<Structure>> east = anchorAt(
							source, sampler, structures.getFirst(), centreChunkX + ring, z);
					if (east != null) {
						cir.setReturnValue(east);
						return;
					}
				}
			}
		}
		cir.setReturnValue(null);
	}

	private static Pair<BlockPos, Holder<Structure>> anchorAt(
			BiomeSource source, Climate.Sampler sampler, Holder<Structure> hut,
			int chunkX, int chunkZ) {
		if (!WaldschattenPatchPlacement.isPatchAnchor(source, sampler, chunkX, chunkZ)) {
			return null;
		}
		return Pair.of(new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8), hut);
	}
}
