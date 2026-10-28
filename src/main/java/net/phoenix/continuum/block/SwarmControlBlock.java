package net.phoenix.continuum.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.common.ContinuumServerEvents;
import net.phoenix.continuum.common.SwarmControlService;
import net.phoenix.continuum.network.ContinuumNetwork;
import net.phoenix.continuum.network.S2CSwarmControlPacket;

import org.jetbrains.annotations.Nullable;

/**
 * The place swarms and shells are managed from: pick a star or black hole, watch it being built round, and send the
 * pods and segments from a launch pad nearby. It must be fed power to stay online, and while it is it projects a
 * hologram of its target above itself. (With GregTech the Swarm Control Array does the same.)
 */
public class SwarmControlBlock extends Block implements EntityBlock {

    public SwarmControlBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwarmControlBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide || type != ContinuumRegistry.SWARM_CONTROL_ENTITY.get()) return null;
        return (lvl, pos, st, entity) -> SwarmControlBlockEntity.tick(lvl, pos, st, (SwarmControlBlockEntity) entity);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof SwarmControlBlockEntity control) {
            control.setOwner(player.getUUID());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof SwarmControlBlockEntity control) control.setOwner(player.getUUID());
            ContinuumServerEvents.engageAndSend(serverPlayer);
            ContinuumNetwork.sendToPlayer(serverPlayer,
                    new S2CSwarmControlPacket(SwarmControlService.view(serverPlayer, pos), true));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
