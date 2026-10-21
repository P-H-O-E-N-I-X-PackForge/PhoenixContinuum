package net.phoenix.continuum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.client.render.ContinuumShaders;
import net.phoenix.continuum.client.screen.ContinuumMapScreen;
import net.phoenixvine.wiki.client.suite.SuiteHudBar;

/** Client-side wiring that used to live in PhoenixCore's PhoenixClient. */
public final class ContinuumClientSetup {

    private ContinuumClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ContinuumShaders::onRegisterShaders);
        modBus.addListener(ContinuumClientSetup::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.phoenix.core.api.client.ConfluxNodeNotes.register(ContinuumNodeNotes::notes);
            Minecraft mc = Minecraft.getInstance();
            SuiteHudBar.register(
                    PhoenixContinuum.MOD_ID,
                    90,
                    new ResourceLocation(PhoenixContinuum.MOD_ID, "textures/gui/suite_continuum.png"),
                    () -> Component.literal("\u00a7dContinuum Map \u00a77(view)"),
                    () -> 1,
                    () -> mc.setScreen(new ContinuumMapScreen()),
                    16, 16, false);
        });
    }

    @Mod.EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeEvents {

        @SubscribeEvent
        public static void registerClientCommands(RegisterClientCommandsEvent event) {
            ContinuumCommand.register(event);
        }
    }
}
