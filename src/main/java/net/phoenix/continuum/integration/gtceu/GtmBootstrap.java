package net.phoenix.continuum.integration.gtceu;

import net.minecraftforge.eventbus.api.IEventBus;

public interface GtmBootstrap {

    void init(IEventBus modEventBus);

    void registerMachines();
}
