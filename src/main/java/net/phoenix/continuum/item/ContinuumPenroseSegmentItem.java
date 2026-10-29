package net.phoenix.continuum.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A piece of a black hole's Penrose sphere: what a shell segment is to a star's Dyson shell. */
public class ContinuumPenroseSegmentItem extends Item {

    public ContinuumPenroseSegmentItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal("Carried by a rocket to a surveyed black hole.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Enough of them build a level of its Penrose sphere.")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Lost if the mission fails.").withStyle(ChatFormatting.RED));
    }
}
