package net.phoenix.continuum.item;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ContinuumPenroseCollectorItem extends Item {

    public ContinuumPenroseCollectorItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_penrose_collector_item.carried_by_a_rocket_to").withStyle(ChatFormatting.GRAY));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_penrose_collector_item.enough_of_them_grow_a")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_penrose_collector_item.lost_if_the_mission_fails").withStyle(ChatFormatting.RED));
    }
}
