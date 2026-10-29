package net.phoenix.continuum.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.phoenix.continuum.PhoenixContinuum;

/** Feeds {@link ContinuumScreenLog}: screens opening and closing, and every game frame. */
@EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class ContinuumScreenLogEvents {

    private ContinuumScreenLogEvents() {}

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        ContinuumScreenLog.opening(event.getCurrentScreen(), event.getNewScreen());
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (ContinuumScreenLog.enabled() && ContinuumScreenLog.isContinuum(event.getScreen())) {
            ContinuumScreenLog.event("init %s %dx%d", event.getScreen().getClass().getSimpleName(),
                    event.getScreen().width, event.getScreen().height);
        }
    }

    /** The other Continuum screens: a frame each (the map reports its own, with more). */
    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Pre event) {
        if (!ContinuumScreenLog.enabled() || !ContinuumScreenLog.isContinuum(event.getScreen())) return;
        if (event.getScreen() instanceof net.phoenix.continuum.client.screen.ContinuumMapScreen) return;
        ContinuumScreenLog.frame(event.getScreen(), "");
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        ContinuumScreenLog.gameFrame();
    }
}
