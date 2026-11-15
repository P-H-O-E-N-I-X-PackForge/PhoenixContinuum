package net.phoenix.continuum.api.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.phoenix.continuum.client.ContinuumVisuals;
import net.phoenix.continuum.client.render.ContinuumShaders;
import net.phoenix.continuum.client.render.HologramRenderer;
import net.phoenix.continuum.client.render.PlanetParams;
import net.phoenix.continuum.client.render.PlanetRenderer;
import net.phoenix.continuum.client.render.SceneRenderer;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumSystem;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ContinuumRender {

    private ContinuumRender() {}

    public static boolean ready() {
        return ContinuumShaders.ready();
    }

    public static PlanetRenderer.Quality quality() {
        return ContinuumVisuals.quality();
    }

    public static void beginWorldObject(Matrix4f view, Matrix4f projection, Vector3f cameraRelativeCentre) {
        beginWorldObject(view, projection, cameraRelativeCentre, false);
    }

    public static void beginWorldObject(Matrix4f view, Matrix4f projection, Vector3f cameraRelativeCentre,
                                        boolean writeDepth) {
        
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthFunc(515);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        org.joml.Vector4f clip = new org.joml.Vector4f(cameraRelativeCentre, 1.0f).mul(view).mul(projection);
        SceneRenderer.setWorldDepth(clip.w > 0.0f ? clip.z / clip.w : Float.NaN, writeDepth);
    }

    public static void endWorldObject() {
        SceneRenderer.setWorldDepth(Float.NaN);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
    }

    public static void drawStar(@Nullable ContinuumSystem system, int rgb, Matrix4f view, Matrix4f projection,
                                Vector3f worldPos, float radius) {
        SceneRenderer.drawSystemStar(system, rgb, view, projection, worldPos, radius, quality(), false);
    }

    public static void drawBlackHole(@Nullable ContinuumSystem system, Matrix4f view, Matrix4f projection,
                                     Vector3f worldPos, float radius) {
        SceneRenderer.drawHole(system, view, projection, worldPos, radius, quality(), false);
    }

    public static void drawBlackHole(int hotRgb, int coolRgb, Matrix4f view, Matrix4f projection, Vector3f worldPos,
                                     float radius) {
        SceneRenderer.drawHoleStyled(
                net.phoenix.continuum.data.ContinuumSystem.HoleStyle.DEFAULT.withColors(hotRgb, coolRgb), view,
                projection, worldPos, radius, quality(), false);
    }

    public static void drawBlackHole(int hotRgb, int coolRgb, float diskSize, float diskIntensity, float feeding,
                                     Matrix4f view, Matrix4f projection, Vector3f worldPos, float radius) {
        SceneRenderer.drawHoleStyled(net.phoenix.continuum.data.ContinuumSystem.HoleStyle.DEFAULT
                .withColors(hotRgb, coolRgb).withDisk(diskSize, diskIntensity).withFeeding(feeding), view,
                projection, worldPos, radius, quality(), false);
    }

    public static void drawProtostar(int rgb, Matrix4f view, Matrix4f projection, Vector3f worldPos, float radius) {
        SceneRenderer.drawProtostar(rgb, view, projection, worldPos, radius, quality(), false,
                new float[] { 3.4f, 5.1f, 7.3f, 0.0f });
    }

    public static void drawGasCloud(int rgb, Matrix4f view, Matrix4f projection, Vector3f worldPos, float radius) {
        SceneRenderer.drawPlanetaryNebula(rgb, view, projection, worldPos, radius, quality(), false);
    }

    public static void drawMegastructure(ContinuumBody central, int rgb, Matrix4f view, Matrix4f projection,
                                         Vector3f worldPos, float radius, boolean shell, float completion) {
        SceneRenderer.drawSwarmCompletion(central, rgb, view, projection, worldPos, radius, quality(), false, shell,
                completion);
    }

    public static void drawPlanet(PlanetParams params, Matrix4f view, Matrix4f projection, Vector3f worldPos,
                                  float radius, Vector3f sunWorld, float spinDegrees, float cloudSpinDegrees,
                                  boolean atmosphere) {
        SceneRenderer.drawPlanet(params, view, projection, worldPos, radius, sunWorld, spinDegrees, cloudSpinDegrees,
                quality(), atmosphere);
    }

    public static void drawBeam(VertexConsumer out, Matrix4f pose, Vector3f a, Vector3f b, float width, int rgb,
                                float strength) {
        HologramRenderer.glow(out, pose, a, b, width, new float[] { ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f }, strength);
    }

    public static void drawBeamPulse(VertexConsumer out, Matrix4f pose, Vector3f a, Vector3f b, float t, float length,
                                     float width, float strength) {
        HologramRenderer.pulse(out, pose, a, b, t, length, width, strength);
    }

    private static int mixToward(int rgb, int other, float t) {
        int r = (int) (((rgb >> 16) & 0xFF) * (1 - t) + ((other >> 16) & 0xFF) * t);
        int g = (int) (((rgb >> 8) & 0xFF) * (1 - t) + ((other >> 8) & 0xFF) * t);
        int b = (int) ((rgb & 0xFF) * (1 - t) + (other & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    public enum Body {
        GAS_CLOUD,
        PROTOSTAR,
        STAR,
        BLACK_HOLE
    }

    private static @Nullable Preview shared;

    public static Preview newPreview() {
        return new Preview();
    }

    public static Preview newCheapPreview(int pixels) {
        return new Preview(Math.max(64, Math.min(512, pixels)), true);
    }

    public static synchronized Preview preview() {
        if (shared == null) shared = new Preview();
        return shared;
    }

    public static final class Preview {

        private final int pixels;
        private final boolean lowQuality;

        private com.mojang.blaze3d.pipeline.@Nullable RenderTarget target;
        private float yaw = 20.0f;
        private float pitch = 14.0f;
        
        private float viewYaw = 0.0f;
        
        private float zoomOut = 1.0f;
        private float distance = 4.6f;
        private long last = net.minecraft.Util.getMillis();

        private Preview() {
            this(512, false);
        }

        private Preview(int pixels, boolean lowQuality) {
            this.pixels = pixels;
            this.lowQuality = lowQuality;
        }

        public void release() {
            if (target != null) {
                target.destroyBuffers();
                target = null;
            }
        }

        public void rotate(float dx, float dy) {
            yaw += dx * 0.6f;
            pitch = ((pitch + dy * 0.6f + 180.0f) % 360.0f + 360.0f) % 360.0f - 180.0f;
        }

        public void zoom(float delta) {
            distance = Math.max(2.4f, Math.min(14.0f, distance - delta * 0.4f));
        }

        public void reset() {
            yaw = 20.0f;
            pitch = 14.0f;
            distance = 4.6f;
        }

        public boolean draw(net.minecraft.client.gui.GuiGraphics g, Body body, int rgb, float size01, int x, int y,
                            int size) {
            return draw(g, body, rgb, size01, x, y, size, true);
        }

        public boolean draw(net.minecraft.client.gui.GuiGraphics g, Body body, int rgb, float size01, int x, int y,
                            int size, boolean backdrop) {
            return draw(g, body, rgb, size01, x, y, size, backdrop, -1);
        }

        private boolean renderScene(Body body, int rgb, float size01, boolean backdrop, int holeTint) {
            if (target == null) {
                target = new com.mojang.blaze3d.pipeline.TextureTarget(pixels, pixels, true,
                        net.minecraft.client.Minecraft.ON_OSX);
            }
            
            boolean scissored = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            int[] box = new int[4];
            if (scissored) {
                org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, box);
                com.mojang.blaze3d.systems.RenderSystem.disableScissor();
            }
            try {
                SceneRenderer.begin(target, yaw, backdrop);
                Matrix4f projection = SceneRenderer.projection(target.width, target.height, 32.0f);
                Matrix4f view = new Matrix4f().translate(0.0f, 0.0f, -(distance * zoomOut + 1.5f))
                        .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(viewYaw));
                Vector3f at = new Vector3f();
                float radius = 0.6f + 0.9f * Math.max(0.0f, Math.min(1.0f, size01));
                
                if (body == Body.GAS_CLOUD || body == Body.PROTOSTAR) radius *= 0.067f;
                var quality = lowQuality ? net.phoenix.continuum.client.render.PlanetRenderer.Quality.LOW : quality();
                switch (body) {
                    case GAS_CLOUD -> SceneRenderer.drawPlanetaryNebula(rgb, view, projection, at, radius, quality, false);
                    case PROTOSTAR -> SceneRenderer.drawProtostar(rgb, view, projection, at, radius, quality, false,
                            new float[] { 3.4f, 5.1f, 7.3f, 0.0f });
                    case STAR -> SceneRenderer.drawSystemStar(null, rgb, view, projection, at, radius, quality, false);
                    case BLACK_HOLE -> {
                        if (holeTint >= 0) {
                            SceneRenderer.drawHoleStyled(net.phoenix.continuum.data.ContinuumSystem.HoleStyle.DEFAULT
                                    .withColors(holeTint, mixToward(holeTint, 0x000000, 0.45f)), view, projection, at,
                                    radius, quality, false);
                        } else {
                            SceneRenderer.drawHole(null, view, projection, at, radius, quality, false);
                        }
                    }
                }
                SceneRenderer.end();
            } catch (RuntimeException e) {
                return false;
            } finally {
                if (scissored) com.mojang.blaze3d.systems.RenderSystem.enableScissor(box[0], box[1], box[2], box[3]);
            }

            return true;
        }

        public int renderTexture(Body body, int rgb, float size01, boolean backdrop, int holeTint, float yawDeg,
                                 float pitchDeg) {
            return renderTexture(body, rgb, size01, backdrop, holeTint, yawDeg, pitchDeg, 1.0f);
        }

        public int renderTexture(Body body, int rgb, float size01, boolean backdrop, int holeTint, float yawDeg,
                                 float pitchDeg, float zoomOut) {
            if (!ContinuumShaders.ready()) return -1;
            this.zoomOut = Math.max(1.0f, zoomOut);
            this.yaw = yawDeg;
            this.viewYaw = yawDeg;
            this.pitch = pitchDeg;
            if (!renderScene(body, rgb, size01, backdrop, holeTint)) return -1;
            return target != null ? target.getColorTextureId() : -1;
        }

        public boolean draw(net.minecraft.client.gui.GuiGraphics g, Body body, int rgb, float size01, int x, int y,
                            int size, boolean backdrop, int holeTint) {
            if (!ContinuumShaders.ready()) return false;
            long now = net.minecraft.Util.getMillis();
            float dt = Math.min(0.1f, (now - last) / 1000.0f);
            last = now;
            yaw += dt * 4.0f;
            if (!renderScene(body, rgb, size01, backdrop, holeTint)) return false;

            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, target.getColorTextureId());
            Matrix4f pose = g.pose().last().pose();
            var bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            bb.vertex(pose, x, y + size, 0).uv(0, 0).endVertex();
            bb.vertex(pose, x + size, y + size, 0).uv(1, 0).endVertex();
            bb.vertex(pose, x + size, y, 0).uv(1, 1).endVertex();
            bb.vertex(pose, x, y, 0).uv(0, 1).endVertex();
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            return true;
        }
    }
}
