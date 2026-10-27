package net.phoenix.continuum.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.phoenix.continuum.common.ContinuumMissions;
import net.phoenix.continuum.machine.PadTiers;
import net.phoenix.continuum.network.S2COpenMapPacket;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class LaunchPadBlock extends Block implements EntityBlock {

    public LaunchPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            pad.setOwner(player.getUUID());
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (PadTiers.basic() && !held.isEmpty() && ContinuumMissions.isStockItem(held) &&
                level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            if (!level.isClientSide) {
                int before = held.getCount();
                pad.stock(held);
                int moved = before - held.getCount();
                if (moved > 0) {
                    player.displayClientMessage(Component.literal("Stocked " + moved +
                            " in the pad, for its schedules.").withStyle(ChatFormatting.GRAY), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(held, state, level, pos, player, hand, hit);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide &&
                level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            for (ItemStack stack : pad.contents()) Block.popResource(level, pos, stack);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (PadTiers.basic() && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
                pad.claim(player.getUUID());
                if (player.isShiftKeyDown()) {
                    serverPlayer.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "Launch Pad: %s tier, %,d / %,d FE.", PadTiers.name(pad.padTier()), pad.storedEnergy(),
                            pad.energyCapacity())).withStyle(ChatFormatting.AQUA));
                    return InteractionResult.CONSUME;
                }
            }
            net.phoenix.continuum.network.ContinuumNetwork.sendToPlayer(serverPlayer, new S2COpenMapPacket(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
