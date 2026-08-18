package dev.lilkuzco.waldschatten.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Roots crawling over the forest floor — vanilla's hanging roots turned the other way up.
 *
 * <p>No collision. The floor is already hard to cross thanks to
 * {@link ThornVineBlock}; roots are there to make the ground look old, and a second thing
 * that snags the player would turn atmosphere into an obstacle course.
 */
public class GnarledRootsBlock extends VegetationBlock {
	public static final MapCodec<GnarledRootsBlock> CODEC = simpleCodec(GnarledRootsBlock::new);

	private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

	public GnarledRootsBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends VegetationBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isFaceSturdy(level, pos, Direction.UP);
	}
}
