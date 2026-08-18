package dev.lilkuzco.waldschatten.mixin;

import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Claims Waldschatten's ground from whatever parameter list this world actually uses.
 *
 * <h2>Why here and not in OverworldBiomeBuilder</h2>
 * Because there are two overworlds in play and only this layer sees both.
 *
 * <p>On vanilla worldgen the list is built by {@code OverworldBiomeBuilder}, and claiming
 * entries there worked. On the empire server it does not exist as far as that builder is
 * concerned: Terralith ships a {@code multi_noise_biome_source_parameter_list/overworld.json}
 * whose {@code lithostitched:biomes} field supplies the entries outright, so the vanilla
 * builder's output is discarded and a claim made there vanishes with it. Measured, not
 * assumed — a survey with Terralith and lithostitched installed reported 146 biomes and
 * Waldschatten absent, while the claim itself had logged 72 successful substitutions.
 *
 * <p>Every one of those routes ends in a {@link MultiNoiseBiomeSource} resolving a
 * {@link Climate.ParameterList}. Taking the list as it comes out of here means the mod does
 * not care who wrote it, needs no new dependency, and never fights another mod over a file
 * path — which is the other way this could have gone, since the only lithostitched-shaped
 * answer is to ship the same {@code overworld.json} Terralith already owns.
 *
 * <p>Memoised because this is called for every biome cell during chunk generation. The
 * underlying field is final, so the answer cannot change.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class MultiNoiseBiomeSourceMixin {

	@Unique
	private Climate.ParameterList<Holder<Biome>> waldschatten$claimed;

	@Inject(method = "parameters", at = @At("RETURN"), cancellable = true)
	private void waldschatten$claimSlice(CallbackInfoReturnable<Climate.ParameterList<Holder<Biome>>> cir) {
		if (this.waldschatten$claimed == null) {
			this.waldschatten$claimed = WaldschattenWorldgen.claimIn(cir.getReturnValue());
		}
		cir.setReturnValue(this.waldschatten$claimed);
	}
}
