package net.phoenix.continuum.client.pdim;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.phoenix.continuum.PhoenixContinuum;

@EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PdimSkyRegistrar {

    private PdimSkyRegistrar() {}

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(PhoenixContinuum.id("pdim"), new PdimSkyEffects());
    }
}
