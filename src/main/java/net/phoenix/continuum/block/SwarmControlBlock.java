package net.phoenix.continuum.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.phoenix.continuum.common.ContinuumServerEvents;
import net.phoenix.continuum.common.SwarmControlService;
import net.phoenix.continuum.network.ContinuumNetwork;
import net.phoenix.continuum.network.S2CSwarmControlPacket;

/**
 * The place swarms and shells are managed from: pick a star or black hole, watch it being built round, and send the
 * pods and segments from a launch pad nearby. (With GregTech the multiblock does the same.)
 */
public class SwarmControlBlock extends Block {

    public SwarmControlBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ContinuumServerEvents.engageAndSend(serverPlayer);
            ContinuumNetwork.sendToPlayer(serverPlayer,
                    new S2CSwarmControlPacket(SwarmControlService.view(serverPlayer, pos), true));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
