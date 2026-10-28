package net.phoenix.continuum.integration.jade;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade tooltips for the launch pad and complex, the swarm control block and array, and the swarm receiver block and
 * array. One provider reads what the server knows about the block and shows it, so the plain blocks and the GregTech
 * multiblocks look alike.
 */
@WailaPlugin
public class ContinuumJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new ContinuumServerInfo(), BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new ContinuumTooltip(), Block.class);
    }
}
