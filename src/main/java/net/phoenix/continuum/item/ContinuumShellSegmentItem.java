package net.phoenix.continuum.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ContinuumShellSegmentItem extends Item {

    public ContinuumShellSegmentItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal("Carried by a rocket to a surveyed star or black hole.")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Enough of them build a level of its shell.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Lost if the mission fails.").withStyle(ChatFormatting.RED));
    }
}
