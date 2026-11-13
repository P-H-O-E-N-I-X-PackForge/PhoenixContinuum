package net.phoenix.continuum.item;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class ContinuumProbeItem extends Item {

    public ContinuumProbeItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        Level level = context.level();
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_probe_item.carried_by_extraction_missions_to")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_probe_item.each_probe_brings_back_a")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_penrose_collector_item.lost_if_the_mission_fails").withStyle(ChatFormatting.RED));
    }
}
