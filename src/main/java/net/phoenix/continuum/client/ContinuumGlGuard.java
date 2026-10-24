package net.phoenix.continuum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenix.continuum.PhoenixContinuum;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * Diagnostic for the "world goes black after closing a Continuum screen" bug.
 *
 * Run the client with -Dcontinuum.glguard.reset=false to turn the safety reset OFF (it is on by default).
 * With the reset off the bug comes back, and the log shows the raw GL/shader state at close next to a
 * vanilla baseline (closing e.g. the inventory), so the stuck value can be read straight off the log.
 */
@Mod.EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class ContinuumGlGuard {

    private static final boolean RESET = !"false".equalsIgnoreCase(System.getProperty("continuum.glguard.reset"));
    private static final int MAX_VANILLA_LOGS = 6;

    private static int framesToLog = 0;
    private static int vanillaLogs = 0;
    private static boolean announced = false;

    private ContinuumGlGuard() {}

    private static boolean isContinuum(Screen screen) {
        return screen.getClass().getName().startsWith("net.phoenix.continuum");
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (!announced) {
            announced = true;
            PhoenixContinuum.LOGGER.info("[GlGuard] active, reset on close = {}", RESET);
        }
        Screen screen = event.getScreen();
        String name = screen.getClass().getSimpleName();
        if (isContinuum(screen)) {
            log("closing " + name + " [continuum]" + (RESET ? " (before reset)" : " (reset disabled)"));
            if (RESET) reset();
            framesToLog = 3;
        } else if (vanillaLogs < MAX_VANILLA_LOGS) {
            vanillaLogs++;
            log("closing " + name + " [vanilla baseline]");
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || framesToLog <= 0) return;
        framesToLog--;
        Screen now = Minecraft.getInstance().screen;
        log("frame end, screen=" + (now == null ? "none" : now.getClass().getSimpleName()));
    }

    public static void reset() {
        try {
            GlStateManager._disableScissorTest();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            GL20.glUseProgram(0);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        } catch (RuntimeException e) {
            PhoenixContinuum.LOGGER.error("[GlGuard] reset failed", e);
        }
    }

    private static int geti(int pname) {
        return GL11.glGetInteger(pname);
    }

    private static String tex2dBindings() {
        int unit = geti(GL13.GL_ACTIVE_TEXTURE);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 4; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            sb.append(i == 0 ? "" : ",").append(geti(32873));
        }
        GL13.glActiveTexture(unit);
        return sb.append("]").toString();
    }

    private static void log(String when) {
        try {
            Minecraft mc = Minecraft.getInstance();

            int[] vp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);

            ByteBuffer mask = BufferUtils.createByteBuffer(16);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            String cm = "[" + (mask.get(0) != 0) + "," + (mask.get(1) != 0) + "," + (mask.get(2) != 0) + "," +
                    (mask.get(3) != 0) + "]";

            FloatBuffer range = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloatv(GL11.GL_DEPTH_RANGE, range);
            IntBuffer poly = BufferUtils.createIntBuffer(16);
            GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, poly);

            float[] clear = new float[4];
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
            float[] color = RenderSystem.getShaderColor();
            var mv = RenderSystem.getModelViewMatrix();
            var proj = RenderSystem.getProjectionMatrix();

            PhoenixContinuum.LOGGER.info("[GlGuard] {}: drawFbo={} (main={}) viewport={} window={}x{} scissor={} " +
                            "colorMask={} depthMask={} depthTest={} depthFunc={} depthRange=[{},{}] blend={} " +
                            "blendFunc=[{},{},{},{}] blendEq={} cull={} cullFace={} frontFace={} polyMode={} " +
                            "activeTex={} tex2D={} program={} stencil={} shaderColor={} clearColor={}",
                    when, geti(36006), mc.getMainRenderTarget().frameBufferId, Arrays.toString(vp),
                    mc.getWindow().getWidth(), mc.getWindow().getHeight(), GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), cm,
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    geti(GL11.GL_DEPTH_FUNC), range.get(0), range.get(1), GL11.glIsEnabled(GL11.GL_BLEND),
                    geti(0x80C9), geti(0x80C8), geti(0x80CB), geti(0x80CA), geti(0x8009),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE), geti(GL11.GL_CULL_FACE_MODE), geti(GL11.GL_FRONT_FACE),
                    poly.get(0), geti(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0, tex2dBindings(),
                    geti(GL20.GL_CURRENT_PROGRAM), GL11.glIsEnabled(GL11.GL_STENCIL_TEST), Arrays.toString(color),
                    Arrays.toString(clear));
            PhoenixContinuum.LOGGER.info("[GlGuard]   modelView={} projection={}", mv, proj);
        } catch (RuntimeException e) {
            PhoenixContinuum.LOGGER.error("[GlGuard] log failed", e);
        }
    }
}