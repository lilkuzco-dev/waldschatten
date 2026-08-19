package dev.lilkuzco.waldschatten;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Turns only witches spawned inside a naturally generated Waldschatten hut into its boss. */
public final class WaldschattenHutWitch {
	public static final String HEXMOTHER_TAG = "waldschatten.hexmother";
	private static final TagKey<Structure> WITCH_HUTS =
			TagKey.create(Registries.STRUCTURE, Waldschatten.id("witch_huts"));

	private static final double MAX_HEALTH = 130.0; // vanilla witch: 26, exactly fivefold

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof Witch witch) || isHexmother(witch)) {
				return;
			}
			if (level.structureManager().getStructureWithPieceAt(witch.blockPosition(), WITCH_HUTS).isValid()) {
				makeHexmother(witch);
			}
		});
	}

	public static boolean isHexmother(Witch witch) {
		return witch.entityTags().contains(HEXMOTHER_TAG);
	}

	private static void makeHexmother(Witch witch) {
		witch.addTag(HEXMOTHER_TAG);
		setBase(witch, Attributes.MAX_HEALTH, MAX_HEALTH);
		setBase(witch, Attributes.ARMOR, 15.0);
		setBase(witch, Attributes.ARMOR_TOUGHNESS, 8.0);
		setBase(witch, Attributes.KNOCKBACK_RESISTANCE, 0.6);
		setBase(witch, Attributes.MOVEMENT_SPEED, 0.30);
		setBase(witch, Attributes.FOLLOW_RANGE, 48.0);
		witch.setHealth(witch.getMaxHealth());
		witch.addEffect(new MobEffectInstance(
				MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		witch.addEffect(new MobEffectInstance(
				MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		witch.setCustomName(Component.literal("Waldschatten Hexmother"));
		witch.setCustomNameVisible(true);
		witch.setPersistenceRequired();
		Waldschatten.LOGGER.info("Awakened Waldschatten Hexmother at {}", witch.blockPosition().toShortString());
	}

	private static void setBase(Witch witch, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			double value) {
		AttributeInstance instance = witch.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private WaldschattenHutWitch() {
	}
}
