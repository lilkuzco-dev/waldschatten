package dev.lilkuzco.waldschatten.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

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
 * <h2>Why {@code @ModifyReturnValue} and not a cancellable {@code @Inject}</h2>
 * Because this is not the only empire mod that rewrites this return value. Enchanted
 * Forest claims birch-forest entries from the same list, and until 0.1.3 both mods did it
 * with {@code @Inject(at = RETURN, cancellable = true)} + {@code setReturnValue}. Mixin
 * emits {@code if (cancelled) return} after each callback at an injection point, so the
 * first handler to cancel ends the method and every later handler is skipped — silently:
 * the skipped mod's code is never entered, so it cannot even log that it lost. Measured
 * 2026-08-22: Enchanted Forest's config registers first, so it claimed 176 entries and
 * this handler never ran. No claim line, no warning, and {@code /locate biome} found
 * nothing in any world on any client or on the server. Every survey had passed because
 * every survey ran this mod alone.
 *
 * <p>{@code @ModifyReturnValue} modifiers chain instead — each receives the previous
 * one's output — so both claims land whichever mod applies first. The two claims touch
 * disjoint biomes (forest/dark forest here, birch forests there), so order does not
 * change the result either.
 *
 * <p>Memoised because this is called for every biome cell during chunk generation. The
 * underlying field is final, so the answer cannot change.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class MultiNoiseBiomeSourceMixin {

	@Unique
	private Climate.ParameterList<Holder<Biome>> waldschatten$claimed;

	@ModifyReturnValue(method = "parameters", at = @At("RETURN"))
	private Climate.ParameterList<Holder<Biome>> waldschatten$claimSlice(
			Climate.ParameterList<Holder<Biome>> original) {
		if (this.waldschatten$claimed == null) {
			this.waldschatten$claimed = WaldschattenWorldgen.claimIn(original);
		}
		return this.waldschatten$claimed;
	}
}
