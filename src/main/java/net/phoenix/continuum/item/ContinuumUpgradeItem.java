package net.phoenix.continuum.item;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.common.RocketStats;

import java.util.List;

public class ContinuumUpgradeItem extends Item {

    private final String upgrade;
    private final int maxLevel;
    private final String effect;

    public ContinuumUpgradeItem(Properties properties, String upgrade, int maxLevel, String effect) {
        super(properties.stacksTo(16));
        this.upgrade = upgrade;
        this.maxLevel = maxLevel;
        this.effect = effect;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack module = player.getItemInHand(hand);
        ItemStack rocket = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND :
                InteractionHand.MAIN_HAND);

        if (!(rocket.getItem() instanceof ContinuumRocketItem)) {
            if (!level.isClientSide) {
                player.displayClientMessage(ContinuumLang.tr("phoenix_continuum.continuum_upgrade_item.hold_a_continuum_rocket_in")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(module);
        }

        int current = RocketStats.level(rocket, upgrade);
        int tier = RocketStats.tier(rocket);
        int cap = Math.min(maxLevel, RocketStats.upgradeCap(tier, upgrade));
        if (current >= cap) {
            if (!level.isClientSide) {
                Component why = cap < maxLevel ? ContinuumLang.tr("phoenix_continuum.continuum_upgrade_item.hull_cannot_carry", RocketStats.tierName(tier)) :
                        ContinuumLang.tr("phoenix_continuum.continuum_upgrade_item.at_highest");
                player.displayClientMessage(why.copy().withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResultHolder.fail(module);
        }

        if (!level.isClientSide) {
            RocketStats.setLevel(rocket, upgrade, current + 1);
            if (!player.getAbilities().instabuild) module.shrink(1);
            level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
            player.displayClientMessage(ContinuumLang.tr("phoenix_continuum.continuum_upgrade_item.rocket_upgraded_to_level", (current + 1))
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResultHolder.sidedSuccess(module, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        Level level = context.level();
        tooltip.add(ContinuumLang.tr(effect).withStyle(ChatFormatting.AQUA));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_upgrade_item.up_to_level", maxLevel).withStyle(ChatFormatting.GRAY));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_repair_kit_item.hold_a_rocket_in_your").withStyle(ChatFormatting.DARK_GRAY));
    }
}
