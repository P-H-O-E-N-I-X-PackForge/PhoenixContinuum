package net.phoenix.continuum.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.phoenix.continuum.network.S2COpenMapPacket;

public class LaunchPadBlock extends Block {

    public LaunchPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            net.phoenix.continuum.network.ContinuumNetwork.sendToPlayer(serverPlayer, new S2COpenMapPacket(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
