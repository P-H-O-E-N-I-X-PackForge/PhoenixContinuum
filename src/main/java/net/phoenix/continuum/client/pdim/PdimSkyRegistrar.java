package net.phoenix.continuum.client.pdim;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.phoenix.continuum.PhoenixContinuum;

@Mod.EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PdimSkyRegistrar {

    private PdimSkyRegistrar() {}

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(PhoenixContinuum.id("pdim"), new PdimSkyEffects());
    }
}
