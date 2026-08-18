package dev.lilkuzco.waldschatten.mixin;

import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Borrows the biome lookup on its way past.
 *
 * <p>Rewriting a parameter list needs a {@code Holder<Biome>} for Waldschatten, and the
 * place that list is built is the only place in the chain that is handed a
 * {@link HolderGetter} for biomes. Every multi-noise parameter list goes through this
 * constructor — vanilla's preset and Terralith's lithostitched-extended one alike — so
 * capturing it here works whoever owns the overworld.
 */
@Mixin(MultiNoiseBiomeSourceParameterList.class)
public class MultiNoiseBiomeSourceParameterListMixin {

	@Inject(method = "<init>", at = @At("TAIL"))
	private void waldschatten$captureLookup(
			MultiNoiseBiomeSourceParameterList.Preset preset, HolderGetter<Biome> biomes, CallbackInfo ci) {
		WaldschattenWorldgen.rememberBiomeLookup(biomes);
	}
}
