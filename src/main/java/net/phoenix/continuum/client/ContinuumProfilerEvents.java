package net.phoenix.continuum.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenix.continuum.PhoenixContinuum;

@Mod.EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class ContinuumProfilerEvents {

    private ContinuumProfilerEvents() {}

    @SubscribeEvent
    public static void onFrameEnd(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ContinuumProfiler.endFrame();
    }

    @SubscribeEvent
    public static void onLevelStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        var stage = event.getStage();
        if (stage == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) {
            GlStateDump.sampleStage("after sky");
        } else if (stage == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            GlStateDump.sampleStage("after solid blocks");
        } else if (stage == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            GlStateDump.sampleStage("after weather");
            GlStateDump.endLevelFrame();
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        GlStateDump.samplePixels();
        ContinuumProfiler.drawOverlay(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Render.Post event) {
        ContinuumProfiler.drawOverlay(event.getGuiGraphics());
    }
}
