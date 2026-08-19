package dev.lilkuzco.waldschatten.mixin;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The structure placement API keeps its biome source private; hut anchors need to sample it. */
@Mixin(ChunkGeneratorStructureState.class)
public interface ChunkGeneratorStructureStateAccessor {

	@Accessor("biomeSource")
	BiomeSource waldschatten$getBiomeSource();
}
