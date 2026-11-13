package net.phoenix.continuum.item;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The pod a black hole's Penrose swarm is grown from: what a swarm collector pod is to a star. */
public class ContinuumPenroseCollectorItem extends Item {

    public ContinuumPenroseCollectorItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_penrose_collector_item.carried_by_a_rocket_to").withStyle(ChatFormatting.GRAY));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_penrose_collector_item.enough_of_them_grow_a")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_penrose_collector_item.lost_if_the_mission_fails").withStyle(ChatFormatting.RED));
    }
}
