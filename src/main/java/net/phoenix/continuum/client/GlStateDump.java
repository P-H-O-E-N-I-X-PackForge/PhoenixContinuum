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
