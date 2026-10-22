package net.phoenix.continuum.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.api.ContinuumSwarmPower;
import net.phoenix.continuum.machine.PadTiers;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Receives the power of the owner's Dyson swarms as Forge Energy. */
public class SwarmReceiverBlock extends Block implements EntityBlock {

    public SwarmReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwarmReceiverBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide || type != ContinuumRegistry.SWARM_RECEIVER_ENTITY.get()) return null;
        return (lvl, pos, st, entity) -> SwarmReceiverBlockEntity.tick(lvl, pos, st,
                (SwarmReceiverBlockEntity) entity);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof SwarmReceiverBlockEntity receiver) {
            receiver.setOwner(player.getUUID());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            String message;
            if (!PadTiers.basic()) {
                message = "With GregTech installed the swarm is received by the Swarm Receiver multiblock, not this block.";
            } else {
                long perTick = ContinuumSwarmPower.outputPerTick(serverPlayer.server,
                        ContinuumHost.teamOf(player.getUUID()));
                message = perTick <= 0 ? "Your team has no Dyson swarm yet. Build orbital stations at a star." :
                        String.format(Locale.ROOT, "Your swarms produce %,d FE/t, shared by your receivers and pads.",
                                perTick) + (level.getBlockEntity(pos) instanceof SwarmReceiverBlockEntity receiver ?
                                        String.format(Locale.ROOT, " This receiver holds %,d FE.", receiver.stored()) :
                                        "");
            }
            serverPlayer.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
