package dev.lilkuzco.waldschatten.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Small bones on a cord, hung under a branch. Someone tied them there.
 *
 * <p>The rattle is driven from {@code animateTick}, which the client only runs for blocks
 * near the camera — so the chime is heard when a player is close to it and costs the
 * server nothing at all. That is a deliberately cheaper reading of "sounds when a player
 * moves past" than a proximity scan would be: the chime is set dressing, and set dressing
 * should not appear in a profiler.
 */
public class BoneChimeBlock extends Block {
	public static final MapCodec<BoneChimeBlock> CODEC = simpleCodec(BoneChimeBlock::new);

	private static final VoxelShape SHAPE = Block.box(4.0, 4.0, 4.0, 12.0, 16.0, 12.0);
	/** Roughly once every few seconds while you are standing under it. */
	private static final int RATTLE_ONE_IN = 55;

	public BoneChimeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/**
	 * Hangs from a branch, a beam, or the canopy itself.
	 *
	 * <p>Leaves are explicitly allowed and that is the whole point of this method.
	 * {@code LeavesBlock.getBlockSupportShape} returns {@code Shapes.empty()} — which is why
	 * you cannot put a torch on leaves — so a sturdy-face test rejects every leaf block in
	 * the game. The only worldgen source of chimes is the {@code attached_to_leaves} tree
	 * decorator, so a sturdy-only rule condemned every chime the mod has ever placed: written
	 * during generation without neighbour updates, looking correct, and deleted by the first
	 * update to reach them. The render battery photographed them in that window and passed.
	 */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		return aboveState.is(BlockTags.LEAVES)
				|| aboveState.isFaceSturdy(level, above, Direction.DOWN);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(RATTLE_ONE_IN) != 0) {
			return;
		}
		level.playLocalSound(
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				SoundEvents.BONE_BLOCK_HIT, SoundSource.BLOCKS,
				0.35F, 0.8F + random.nextFloat() * 0.5F, false);
	}
}
