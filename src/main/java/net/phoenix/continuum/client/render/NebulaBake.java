package net.phoenix.continuum.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

public final class NebulaBake {

    private static final int WIDTH = 2048;
    private static final int HEIGHT = 1024;
    private static final int STRIP = 256;

    private record Key(float p0, float p1, float p2, float s0, float s1, float s2, int octaves) {}

    private static final class Pending {
        final TextureTarget target = create();
        int row;
    }

    private static final int MAX_DONE = 4;
    private static final java.util.Map<Key, TextureTarget> done = new java.util.LinkedHashMap<>(8, 0.75f, true);
    private static final java.util.Map<Key, Pending> building = new java.util.HashMap<>();

    private NebulaBake() {}

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

        GlStateManager._bindTexture(target.getColorTextureId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
        return target;
    }

    private static void bake(ShaderInstance shader, TextureTarget target, Key key, int y0, int y1) {
        int previousFbo = GL11.glGetInteger(36006);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        boolean blendWas = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTestWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullWas = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMaskWas = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        target.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(0, y0, WIDTH, y1 - y0);

        try {
            shader.safeGetUniform("Octaves").set((float) key.octaves());
            shader.safeGetUniform("PrimaryColor").set(key.p0(), key.p1(), key.p2());
            shader.safeGetUniform("SecondaryColor").set(key.s0(), key.s1(), key.s2());
            SceneRenderer.drawFullQuad(shader);
        } finally {
            GlStateManager._disableScissorTest();
            if (cullWas) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            RenderSystem.depthMask(depthMaskWas);
            if (depthTestWas) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (blendWas) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            GlStateManager._glBindFramebuffer(36160, previousFbo);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
}