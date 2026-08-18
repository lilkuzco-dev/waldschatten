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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

	/**
	 * Wraps the consumer vanilla is about to pour its biomes into, so each entry can be
	 * re-pointed on the way past.
	 *
	 * <p>Appending at TAIL — which is what this used to do — cannot work: multi-noise scores
	 * a sample by distance-to-range and keeps the first strictly-better point, so an added
	 * point that merely overlaps a vanilla one ties at zero and always loses. Claiming
	 * entries as they are emitted is the only way to actually own ground.
	 */
	@ModifyVariable(method = "addBiomes", at = @At("HEAD"), argsOnly = true)
	private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> waldschatten$claimSlice(
			Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> original) {
		// The preset is built more than once per session, so the counter is reset here at
		// HEAD — otherwise it accumulates and the log reads 36, 72, 108 for the same work.
		WaldschattenWorldgen.resetClaimCount();
		return entry -> original.accept(WaldschattenWorldgen.claim(entry));
	}

	@Inject(method = "addBiomes", at = @At("TAIL"))
	private void waldschatten$reportClaim(
			Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, CallbackInfo ci) {
		WaldschattenWorldgen.logClaimed();
	}
}
