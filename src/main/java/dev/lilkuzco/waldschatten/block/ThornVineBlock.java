package dev.lilkuzco.waldschatten.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Thorns. The tax on taking the short way through.
 *
 * <p>Modelled on vanilla's sweet berry bush rather than on cobweb: cobweb stops you dead,
 * which reads as being trapped, while the berry bush's "slowed, and cut if you keep
 * pushing" is the shape of the Grimm image — the wood lets you through and charges for it.
 * The damage only lands if you are actually moving, so standing still in a thorn patch is
 * safe and shoving through it is not.
 *
 * <p>Climbing comes from {@code #minecraft:climbable} rather than from code, so a wall of
 * thorns is a slow way up instead of a wall.
 */
public class ThornVineBlock extends VegetationBlock {
	public static final MapCodec<ThornVineBlock> CODEC = simpleCodec(ThornVineBlock::new);

	/** Harsher than a berry bush (0.8) — this is a thicket, not a fruit crop. */
	private static final Vec3 SLOWDOWN = new Vec3(0.6, 0.6, 0.6);
	private static final float SCRATCH_DAMAGE = 1.0F;
	/** Below this much movement per tick you are picking your way through, not shoving. */
	private static final double MOVING_THRESHOLD = 0.003;

	public ThornVineBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends VegetationBlock> codec() {
		return CODEC;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isFaceSturdy(level, pos, Direction.UP);
	}

	@Override
	protected void entityInside(
			BlockState state, Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (!(entity instanceof LivingEntity)) {
			return;
		}
		entity.makeStuckInBlock(state, SLOWDOWN);

		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		// Client-authoritative entities (the player) report their own movement; everything
		// else is measured from where it was last tick. Same split vanilla uses.
		Vec3 movement = entity.isClientAuthoritative()
				? entity.getKnownMovement()
				: entity.oldPosition().subtract(entity.position());
		if (movement.horizontalDistanceSqr() <= 0.0) {
			return;
		}
		if (Math.abs(movement.x()) >= MOVING_THRESHOLD || Math.abs(movement.z()) >= MOVING_THRESHOLD) {
			entity.hurtServer(serverLevel, level.damageSources().sweetBerryBush(), SCRATCH_DAMAGE);
		}
	}
}
