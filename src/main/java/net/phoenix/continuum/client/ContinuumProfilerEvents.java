package net.phoenix.continuum.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.phoenix.continuum.PhoenixContinuum;

@EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class ContinuumProfilerEvents {

    private ContinuumProfilerEvents() {}

    @SubscribeEvent
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        ContinuumProfiler.endFrame();
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        ContinuumProfiler.drawOverlay(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Render.Post event) {
        ContinuumProfiler.drawOverlay(event.getGuiGraphics());
    }
}
