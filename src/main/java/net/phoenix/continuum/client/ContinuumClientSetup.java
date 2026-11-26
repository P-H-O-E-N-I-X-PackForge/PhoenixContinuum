package net.phoenix.continuum.client;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.client.render.ContinuumShaders;
import net.phoenix.continuum.client.screen.ContinuumMapScreen;
import net.phoenixvine.wiki.client.suite.SuiteHudBar;

public final class ContinuumClientSetup {

    private ContinuumClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ContinuumShaders::onRegisterShaders);
        modBus.addListener(ContinuumClientSetup::onClientSetup);
        modBus.addListener(ContinuumKeys::register);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            SuiteHudBar.register(
                    PhoenixContinuum.MOD_ID,
                    90,
                    ResourceLocation.fromNamespaceAndPath(PhoenixContinuum.MOD_ID, "textures/gui/suite_continuum.png"),
                    () -> ContinuumLang.tr("phoenix_continuum.continuum_client_setup.continuum_map_view"),
                    () -> 1,
                    () -> mc.setScreen(new ContinuumMapScreen()),
                    16, 16, false);
        });
    }

    @EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeEvents {

        @SubscribeEvent
        public static void registerClientCommands(RegisterClientCommandsEvent event) {
            ContinuumCommand.register(event);
        }
    }
}
