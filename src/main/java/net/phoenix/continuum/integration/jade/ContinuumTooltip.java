package net.phoenix.continuum.integration.jade;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenix.continuum.PhoenixContinuum;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Shows the lines the server sent for one of our blocks. */
final class ContinuumTooltip implements IBlockComponentProvider {

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(PhoenixContinuum.MOD_ID, "info");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(ContinuumServerInfo.KEY)) return;
        ListTag lines = data.getList(ContinuumServerInfo.KEY, Tag.TAG_STRING);
        for (Tag line : lines) tooltip.add(Component.literal(line.getAsString()));
    }
}
