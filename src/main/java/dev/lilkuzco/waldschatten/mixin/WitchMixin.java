package dev.lilkuzco.waldschatten.mixin;

import dev.lilkuzco.waldschatten.WaldschattenHutWitch;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Hexmother's offensive curse after her normal potion throw. */
@Mixin(Witch.class)
public abstract class WitchMixin {
	@Inject(method = "performRangedAttack", at = @At("TAIL"))
	private void waldschatten$hexVolley(LivingEntity target, float power, CallbackInfo ci) {
		Witch witch = (Witch) (Object) this;
		// Witches also throw healing potions to raider allies. Only enhance a real attack.
		if (!WaldschattenHutWitch.isHexmother(witch) || witch.getTarget() != target) {
			return;
		}

		target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1), witch);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1), witch);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1), witch);
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), witch);
		witch.heal(6.0F);
	}
}
