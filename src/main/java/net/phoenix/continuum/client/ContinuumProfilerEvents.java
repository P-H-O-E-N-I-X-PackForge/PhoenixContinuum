package net.phoenix.continuum.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenix.continuum.PhoenixContinuum;

/** Feeds the profiler once per frame and draws its numbers while it is on. */
@Mod.EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class ContinuumProfilerEvents {

    private ContinuumProfilerEvents() {}

    @SubscribeEvent
    public static void onFrameEnd(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ContinuumProfiler.endFrame();
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
