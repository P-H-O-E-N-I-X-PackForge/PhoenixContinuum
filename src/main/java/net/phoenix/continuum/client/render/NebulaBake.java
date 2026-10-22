package net.phoenix.continuum.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * The nebula of the map backdrop and the personal-dimension sky, drawn once into a longitude / latitude picture of the
 * whole sphere, so the backdrop shaders look it up instead of evaluating the noise for every pixel of every frame (about
 * eight times cheaper on the same picture; stars are not in it, so they still twinkle live). The picture only depends on
 * the nebula's two colours and the noise detail, so it is made once. When those change, the new one is drawn a strip of
 * rows at a time into a second target and swapped in when it is whole, so there is no hitch and no half-drawn frame.
 */
public final class NebulaBake {

    private static final int WIDTH = 2048;
    private static final int HEIGHT = 1024;
    private static final int STRIP = 256;

    private record Key(float p0, float p1, float p2, float s0, float s1, float s2, int octaves) {}

    /** A picture being drawn, a strip of rows at a time. */
    private static final class Pending {
        final TextureTarget target = create();
        int row;
    }

    private static final int MAX_DONE = 4;
    private static final java.util.Map<Key, TextureTarget> done = new java.util.LinkedHashMap<>(8, 0.75f, true);
    private static final java.util.Map<Key, Pending> building = new java.util.HashMap<>();

    private NebulaBake() {}

    /**
     * The baked picture for these colours, or null while it is still being drawn (the shaders then work the nebula out
     * live, which looks the same). Several colour sets can be in use at once (the map backdrop and a dimension's sky are
     * both drawn in a dimension), so each has its own picture rather than fighting over one.
     */
    public static @Nullable RenderTarget get(float[] primary, float[] secondary, int octaves) {
        ShaderInstance shader = ContinuumShaders.NEBULA_BAKE;
        if (shader == null) return null;
        Key key = new Key(primary[0], primary[1], primary[2], secondary[0], secondary[1], secondary[2], octaves);

        TextureTarget have = done.get(key);
        if (have != null) return have;

        Pending pending = building.computeIfAbsent(key, k -> new Pending());
        bake(shader, pending.target, key, pending.row, Math.min(HEIGHT, pending.row + STRIP));
        pending.row += STRIP;
        if (pending.row < HEIGHT) return null;

        building.remove(key);
        done.put(key, pending.target);
        if (done.size() > MAX_DONE) {
            var oldest = done.entrySet().iterator();
            var gone = oldest.next();
            oldest.remove();
            gone.getValue().destroyBuffers();
        }
        return pending.target;
    }

    private static TextureTarget create() {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, false, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
        // it wraps round the sphere horizontally
        GlStateManager._bindTexture(target.getColorTextureId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
        return target;
    }

    private static void bake(ShaderInstance shader, TextureTarget target, Key key, int y0, int y1) {
        int previousFbo = GL11.glGetInteger(36006);   // GL_DRAW_FRAMEBUFFER_BINDING
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        target.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(0, y0, WIDTH, y1 - y0);

        shader.safeGetUniform("Octaves").set((float) key.octaves());
        shader.safeGetUniform("PrimaryColor").set(key.p0(), key.p1(), key.p2());
        shader.safeGetUniform("SecondaryColor").set(key.s0(), key.s1(), key.s2());
        SceneRenderer.drawFullQuad(shader);

        GlStateManager._disableScissorTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        GlStateManager._glBindFramebuffer(36160, previousFbo);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
}
