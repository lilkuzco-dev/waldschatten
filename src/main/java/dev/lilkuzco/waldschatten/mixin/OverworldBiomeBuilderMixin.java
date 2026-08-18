package dev.lilkuzco.waldschatten.mixin;

import com.mojang.datafixers.util.Pair;
import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends Waldschatten to the vanilla overworld's multi-noise parameter list.
 *
 * <p>Injected at TAIL so every vanilla biome is already in the list: this only ever adds
 * a point, and adding a point cannot remove a vanilla biome — the sampler picks the
 * nearest point, so the most Waldschatten can do is win ground inside its own niche.
 *
 * <p>{@code addBiomes} is package-private, which is why this is a mixin rather than a
 * call. The rationale for the whole approach is in
 * {@link dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen}.
 */
@Mixin(OverworldBiomeBuilder.class)
public class OverworldBiomeBuilderMixin {

	@Inject(method = "addBiomes", at = @At("TAIL"))
	private void waldschatten$addWaldschatten(
			Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, CallbackInfo ci) {
		WaldschattenWorldgen.addOverworldBiomes(biomes);
	}
}
