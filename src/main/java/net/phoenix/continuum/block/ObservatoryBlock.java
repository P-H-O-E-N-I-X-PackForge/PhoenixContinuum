package net.phoenix.continuum.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A telescope: use it to look at every body your team has found, live, with what is known about each. */
public class ObservatoryBlock extends Block {

    public ObservatoryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) net.phoenix.continuum.client.ContinuumClientHooks.openObservatory();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
