package net.phoenix.continuum.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public final class SceneTarget implements AutoCloseable {

    /** The most pixels a scene is drawn at (about 1440 x 810). */
    private static final double PIXEL_BUDGET = 1_200_000.0;

    private @Nullable RenderTarget target;
    private int width = -1;
    private int height = -1;

    public @Nullable RenderTarget ensure() {
        Minecraft mc = Minecraft.getInstance();
        int w = Math.max(64, mc.getWindow().getWidth());
        int h = Math.max(64, mc.getWindow().getHeight());
        // these scenes are soft (nebulae, glows) and cost in proportion to the pixels, so a big window is drawn at
        // fewer than it has and stretched: fullscreen on a large display would otherwise cost two and a half times a window
        double scale = Math.min(1.0, Math.sqrt(PIXEL_BUDGET / ((double) w * h)));
        if (scale < 1.0) {
            w = Math.max(64, (int) (w * scale));
            h = Math.max(64, (int) (h * scale));
        }
        if (target != null && width == w && height == h) return target;

        close();
        target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        target.setFilterMode(9729);
        width = w;
        height = h;
        return target;
    }

    public int pixelWidth() {
        return width;
    }

    public int pixelHeight() {
        return height;
    }

    public void blit(GuiGraphics graphics, int guiWidth, int guiHeight) {
        if (target == null) return;

        graphics.fill(0, 0, guiWidth, guiHeight, 0xFF05060f);

        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());

        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder bb = net.phoenix.continuum.client.render.Vtx.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        bb.vertex(pose, -1, guiHeight + 1, 0).uv(0, 0).endVertex();
        bb.vertex(pose, guiWidth + 1, guiHeight + 1, 0).uv(1, 0).endVertex();
        bb.vertex(pose, guiWidth + 1, -1, 0).uv(1, 1).endVertex();
        bb.vertex(pose, -1, -1, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableBlend();
    }

    @Override
    public void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        width = -1;
        height = -1;
    }
}
