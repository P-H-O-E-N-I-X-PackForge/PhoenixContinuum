package net.phoenix.continuum.integration.gtceu;

import net.minecraftforge.eventbus.api.IEventBus;

/** The few things that differ between GregTech 7.x and 8.x. One implementation per version; see {@link GTCEuCompat}. */
public interface GtmBootstrap {

    void init(IEventBus modEventBus);

    /** Called when GregTech asks addons to register their machines. */
    void registerMachines();
}
