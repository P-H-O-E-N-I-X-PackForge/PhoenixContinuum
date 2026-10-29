package net.phoenix.continuum.item;

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
        tooltip.add(Component.literal("Carried by a rocket to a surveyed black hole.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Enough of them grow a level of its Penrose swarm.")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Lost if the mission fails.").withStyle(ChatFormatting.RED));
    }
}
