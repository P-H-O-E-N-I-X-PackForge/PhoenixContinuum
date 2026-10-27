package net.phoenix.continuum.client.screen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.phoenix.continuum.client.ContinuumVisuals;
import net.phoenix.continuum.client.render.ContinuumShaders;
import net.phoenix.continuum.client.render.PlanetParams;
import net.phoenix.continuum.client.render.PlanetRenderer;
import net.phoenix.continuum.client.render.SceneRenderer;
import net.phoenix.continuum.data.ContinuumSystem;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ScenePreview {

    private static final int PIXELS = 512;

    private RenderTarget target;
    private float yaw = 20.0f;
    private float pitch = 12.0f;
    private float distance = 4.3f;
    private float surfaceSpin;
    private float cloudSpin;
    private boolean dragging;
    private long last = Util.getMillis();

    private int x;
    private int y;
    private int size;

    private void ensureTarget() {
        if (target == null) target = new TextureTarget(PIXELS, PIXELS, true, Minecraft.ON_OSX);
    }

    private float tick() {
        long now = Util.getMillis();
        float dt = Math.min((now - last) / 1000.0f, 0.1f);
        last = now;
        return dt;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + size && my >= y && my < y + size;
    }

    public boolean mouseClicked(double mx, double my) {
        dragging = contains(mx, my);
        return dragging;
    }

    public void mouseReleased() {
        dragging = false;
    }

    public boolean mouseDragged(double dx, double dy) {
        if (!dragging) return false;
        yaw += (float) dx * 0.6f;
        
        pitch = ((pitch + (float) dy * 0.6f + 180.0f) % 360.0f + 360.0f) % 360.0f - 180.0f;
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my)) return false;
        distance = Math.max(2.2f, Math.min(14.0f, distance - (float) delta * 0.3f));
        return true;
    }

    public void drawPlanet(GuiGraphics g, PlanetParams params, int x, int y, int size) {
        begin(x, y, size);
        float dt = tick();
        surfaceSpin += params.spinDegPerSec() * dt;
        cloudSpin += params.cloudSpinDegPerSec() * dt;
        boolean drawn = ContinuumShaders.ready() && render(() -> PlanetRenderer.render(target, params,
                new PlanetRenderer.View(yaw, pitch, distance, surfaceSpin, cloudSpin), ContinuumVisuals.quality()));
        finish(g, drawn);
    }

    public void drawSystem(GuiGraphics g, ContinuumSystem system, int x, int y, int size) {
        begin(x, y, size);
        tick();
        boolean drawn = ContinuumShaders.ready() && render(() -> {
            SceneRenderer.begin(target, yaw);
            Matrix4f projection = SceneRenderer.projection(target.width, target.height, 32.0f);
            Matrix4f view = new Matrix4f().translate(0.0f, 0.0f, -(distance + 1.5f)).rotateX(
                    (float) Math.toRadians(pitch));
            Vector3f at = new Vector3f();
            if (system.hasHole()) {
                SceneRenderer.drawHole(system, view, projection, at, system.starRadius(),
                        ContinuumVisuals.quality(), false);
            } else {
                SceneRenderer.drawSystemStar(system, system.starColor(), view, projection, at, system.starRadius(),
                        ContinuumVisuals.quality(), false);
            }
            SceneRenderer.drawSwarmShell(system, system.starColor(), view, projection, at, system.starRadius(),
                    ContinuumVisuals.quality(), false);
            SceneRenderer.end();
            return true;
        });
        finish(g, drawn);
    }

    /** A star or black hole with the swarm or shell round it at the levels given, turning slowly. */
    public void drawTarget(GuiGraphics g, net.phoenix.continuum.data.ContinuumBody central, ContinuumSystem system,
                           int swarmLevel, int shellLevel, int x, int y, int size) {
        begin(x, y, size);
        yaw += tick() * 5.0f;
        boolean drawn = ContinuumShaders.ready() && system != null && central != null && render(() -> {
            SceneRenderer.begin(target, yaw);
            Matrix4f projection = SceneRenderer.projection(target.width, target.height, 32.0f);
            Matrix4f view = new Matrix4f().translate(0.0f, 0.0f, -(distance + 1.5f)).rotateX(
                    (float) Math.toRadians(pitch));
            Vector3f at = new Vector3f();
            if (net.phoenix.continuum.common.Swarms.isPenrose(central)) {
                SceneRenderer.drawHole(system, view, projection, at, system.starRadius(),
                        ContinuumVisuals.quality(), false);
            } else {
                SceneRenderer.drawSystemStar(system, system.starColor(), view, projection, at, system.starRadius(),
                        ContinuumVisuals.quality(), false);
            }
            SceneRenderer.drawSwarmShell(central, system.starColor(), view, projection, at, system.starRadius(),
                    ContinuumVisuals.quality(), false, swarmLevel, shellLevel);
            SceneRenderer.end();
            return true;
        });
        finish(g, drawn);
    }

    private void begin(int x, int y, int size) {
        this.x = x;
        this.y = y;
        this.size = size;
        ensureTarget();
    }

    private boolean render(java.util.function.BooleanSupplier body) {
        try {
            return body.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void finish(GuiGraphics g, boolean drawn) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF3A4452);
        if (!drawn) {
            g.fill(x, y, x + size, y + size, 0xFF05060F);
            g.drawCenteredString(Minecraft.getInstance().font, ContinuumShaders.ready() ? "Preview unavailable" :
                    "Shaders not loaded", x + size / 2, y + size / 2 - 4, 0xFFFF6B6B);
            return;
        }
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        Matrix4f pose = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(pose, x, y + size, 0).setUv(0, 0);
        bb.addVertex(pose, x + size, y + size, 0).setUv(1, 0);
        bb.addVertex(pose, x + size, y, 0).setUv(1, 1);
        bb.addVertex(pose, x, y, 0).setUv(0, 1);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.enableBlend();
    }

    public void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
