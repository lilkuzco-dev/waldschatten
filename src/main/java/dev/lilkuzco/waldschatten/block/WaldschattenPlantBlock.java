package dev.lilkuzco.waldschatten.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The undergrowth: witch hazel, nightshade, mandrake.
 *
 * <p>Broader-footed than vanilla's flowers on purpose. Waldschatten's floor is podzol,
 * coarse dirt, ashen soil and old stone rather than grass, and a plant that insists on
 * grass would generate as a scatter of nothing across most of the biome it was written
 * for.
 */
public class WaldschattenPlantBlock extends VegetationBlock {
	public static final MapCodec<WaldschattenPlantBlock> CODEC = simpleCodec(WaldschattenPlantBlock::new);

	public WaldschattenPlantBlock(Properties properties) {
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
}
