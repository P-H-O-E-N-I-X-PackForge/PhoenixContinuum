package net.phoenix.continuum.item;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.common.RocketStats;

import java.util.List;

public class ContinuumRefitKitItem extends Item {

    private final int tier;

    public ContinuumRefitKitItem(Properties properties, int tier) {
        super(properties.stacksTo(4));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            player.displayClientMessage(ContinuumLang.tr("phoenix_continuum.continuum_refit_kit_item.hold_a_rocket_and_sneak")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_refit_kit_item.raises_a_rocket_s_hull", RocketStats.tierName(tier - 1), RocketStats.tierName(tier)).withStyle(ChatFormatting.AQUA));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_refit_kit_item.a_better_hull_flies_faster").withStyle(ChatFormatting.GRAY));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_refit_kit_item.sneak_use_a_rocket_to")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
