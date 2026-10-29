package net.phoenix.continuum.client;

import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Writes the OpenGL state to the log, to find what a screen leaves behind (a world that renders black after the map closes
 * is state left over: a wrong framebuffer, viewport, mask or texture). Turn it on with /continuum glstate on; each line
 * starts with [glstate], so two runs (say with and without a baked texture) can be compared.
 */
public final class GlStateDump {

    private static boolean enabled;

    private GlStateDump() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        few = 0;
        // a few world frames right away, to have a normal one to compare the black ones with
        stageFrames = on ? 3 : 0;
    }

    private static int few;

    /** Like dump, but only the first 15 calls since logging was turned on, for places that run every frame. */
    public static void dumpFew(String when) {
        if (!enabled || few >= 15) return;
        few++;
        dump(when);
    }

    /** Logs the state now. */
    public static void dump(String when) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(when).append(": ");
        int mainFbo = mc.getMainRenderTarget() != null ? mc.getMainRenderTarget().frameBufferId : -1;
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        sb.append("drawFbo=").append(draw).append(draw == mainFbo ? "(main)" : "(NOT main " + mainFbo + ")");
        sb.append(" readFbo=").append(read).append(read == mainFbo ? "(main)" : "(NOT main)");
        int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
        sb.append(" status=0x").append(Integer.toHexString(status));
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        sb.append(" viewport=").append(vp[0]).append(',').append(vp[1]).append(',').append(vp[2]).append(',')
                .append(vp[3]);
        sb.append(" window=").append(mc.getWindow().getWidth()).append('x').append(mc.getWindow().getHeight());
        sb.append(" scissor=").append(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            int[] sc = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, sc);
            sb.append("[").append(sc[0]).append(',').append(sc[1]).append(',').append(sc[2]).append(',').append(sc[3])
                    .append("]");
        }
        ByteBuffer mask = BufferUtils.createByteBuffer(16);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
        sb.append(" colorMask=").append(mask.get(0)).append(mask.get(1)).append(mask.get(2)).append(mask.get(3));
        sb.append(" depthMask=").append(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        sb.append(" depthTest=").append(GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        sb.append(" depthFunc=").append(GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        sb.append(" blend=").append(GL11.glIsEnabled(GL11.GL_BLEND));
        sb.append("(").append(GL11.glGetInteger(GL30.GL_BLEND_SRC_RGB)).append("/")
                .append(GL11.glGetInteger(GL30.GL_BLEND_DST_RGB)).append(")");
        sb.append(" cull=").append(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        sb.append(" program=").append(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        sb.append(" vao=").append(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        sb.append(" activeUnit=").append(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0);
        sb.append(" tex2d=[");
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int unit = 0; unit < 8; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            sb.append(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)).append(unit < 7 ? "," : "");
        }
        GL13.glActiveTexture(active);
        sb.append("]");
        float[] color = RenderSystem.getShaderColor();
        sb.append(" shaderColor=").append(color[0]).append(',').append(color[1]).append(',').append(color[2])
                .append(',').append(color[3]);
        sb.append(" glError=").append(GL11.glGetError());
        sb.append(" main=").append(mc.getMainRenderTarget().width).append('x').append(mc.getMainRenderTarget().height)
                .append("/view").append(mc.getMainRenderTarget().viewWidth).append('x')
                .append(mc.getMainRenderTarget().viewHeight).append(" mainColorTex=")
                .append(mc.getMainRenderTarget().getColorTextureId());
        sb.append(" cacheVsReal=").append(cacheComparison());
        net.phoenix.continuum.PhoenixContinuum.LOGGER.info("[glstate] {}", sb);
    }

    /**
     * The game keeps its own record of which texture is bound on each unit and skips a bind it thinks is already done, so
     * if that record and the real binding disagree a draw samples the wrong texture (black). Reads the record by reflection
     * (it is private) and lists the units where the two differ.
     */
    private static String cacheComparison() {
        try {
            var texturesField = com.mojang.blaze3d.platform.GlStateManager.class.getDeclaredField("TEXTURES");
            texturesField.setAccessible(true);
            Object[] states = (Object[]) texturesField.get(null);
            StringBuilder out = new StringBuilder();
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            for (int unit = 0; unit < Math.min(8, states.length); unit++) {
                var bindingField = states[unit].getClass().getDeclaredField("binding");
                bindingField.setAccessible(true);
                int cached = bindingField.getInt(states[unit]);
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                int real = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                if (cached != real) out.append("unit").append(unit).append("(cached ").append(cached).append(" real ")
                        .append(real).append(") ");
            }
            GL13.glActiveTexture(active);
            return out.length() == 0 ? "ok" : out.toString().trim();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "unavailable:" + e.getClass().getSimpleName();
        }
    }

    private static int pixelFrames;
    private static int stageFrames;

    /** Where in the frame the world's colour first goes black: samples after the sky, the solid blocks and the whole level. */
    public static void sampleStage(String stage) {
        if (!enabled || stageFrames <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        ByteBuffer px = BufferUtils.createByteBuffer(4);
        StringBuilder sb = new StringBuilder("level stage ").append(stage).append(": fbo=")
                .append(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)).append("/main").append(main.frameBufferId);
        int[][] points = { { main.width / 4, main.height * 3 / 4 }, { main.width / 4, main.height / 2 } };
        for (int[] p : points) {
            px.clear();
            GL11.glReadPixels(p[0], p[1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
            sb.append(" [").append(p[0]).append(',').append(p[1]).append("]=").append(px.get(0) & 255).append(',')
                    .append(px.get(1) & 255).append(',').append(px.get(2) & 255).append(',').append(px.get(3) & 255);
        }
        float[] fog = RenderSystem.getShaderFogColor();
        sb.append(" fog(start=").append(RenderSystem.getShaderFogStart()).append(" end=")
                .append(RenderSystem.getShaderFogEnd()).append(" color=").append(fog[0]).append(',').append(fog[1])
                .append(',').append(fog[2]).append(',').append(fog[3]).append(")");
        var proj = RenderSystem.getProjectionMatrix();
        sb.append(" proj(m00=").append(proj.m00()).append(" m11=").append(proj.m11()).append(" m22=").append(proj.m22())
                .append(" m32=").append(proj.m32()).append(")");
        sb.append(" graphics=").append(mc.options.graphicsMode().get()).append(" postEffect=")
                .append(mc.gameRenderer.currentEffect() != null).append(" transparencyChain=")
                .append(net.minecraft.client.Minecraft.useShaderTransparency());
        sb.append(" colorMask=");
        ByteBuffer mask = BufferUtils.createByteBuffer(16);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
        sb.append(mask.get(0)).append(mask.get(1)).append(mask.get(2)).append(mask.get(3));
        float[] c = RenderSystem.getShaderColor();
        sb.append(" shaderColor=").append(c[0]).append(',').append(c[1]).append(',').append(c[2]).append(',').append(c[3]);
        net.phoenix.continuum.PhoenixContinuum.LOGGER.info("[glstate] {}", sb);
    }

    /** Called once a frame, at the end of the level, to count the sampled frames down. */
    public static void endLevelFrame() {
        if (stageFrames > 0) stageFrames--;
    }

    /** Asks for the next few frames to have their pixels sampled once the HUD has drawn (see samplePixels). */
    public static void samplePixelsForFrames(int frames) {
        if (enabled) {
            pixelFrames = frames;
            stageFrames = frames;
        }
    }

    /**
     * Reads a few pixels of the framebuffer being drawn (the world, before the final copy to the window) and the main
     * target's texture settings, to tell "the world was drawn black" from "it was drawn but is shown black".
     */
    public static void samplePixels() {
        if (!enabled || pixelFrames <= 0) return;
        pixelFrames--;
        Minecraft mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        StringBuilder sb = new StringBuilder("world pixels (").append(pixelFrames).append(" frames left): drawFbo=")
                .append(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)).append(" mainFbo=").append(main.frameBufferId);
        int[][] points = { { w / 4, h * 3 / 4 }, { w / 4, h / 2 }, { w * 3 / 4, h / 3 }, { w / 2, h * 7 / 8 } };
        ByteBuffer px = BufferUtils.createByteBuffer(4);
        for (int[] p : points) {
            px.clear();
            GL11.glReadPixels(p[0], p[1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
            sb.append(" [").append(p[0]).append(',').append(p[1]).append("]=").append(px.get(0) & 255).append(',')
                    .append(px.get(1) & 255).append(',').append(px.get(2) & 255).append(',').append(px.get(3) & 255);
        }
        // the main target's texture, read on a spare unit so nothing the game bound is disturbed
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 12);
        int was = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, main.getColorTextureId());
        sb.append(" mainTex(min=").append(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER))
                .append(" mag=").append(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER))
                .append(" wrap=").append(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S))
                .append(" size=").append(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH))
                .append("x").append(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT))
                .append(")");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, was);
        GL13.glActiveTexture(active);
        net.phoenix.continuum.PhoenixContinuum.LOGGER.info("[glstate] {}", sb);
    }

    /** Logs the state at the start of each of the next few frames (before the game binds anything for them). */
    public static void dumpNextFrames(String when, int frames) {
        if (!enabled || frames <= 0) return;
        RenderSystem.recordRenderCall(() -> {
            dump(when + " (next frame, " + frames + " left)");
            dumpNextFrames(when, frames - 1);
        });
    }
}
