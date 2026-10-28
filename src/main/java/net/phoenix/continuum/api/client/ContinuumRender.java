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

/**
 * Continuum's space shaders for other mods: stars, black holes, swarms and shells, planets, and the light beams the
 * Swarm Control hologram is made of. Client side only, called while rendering (for a hologram or a machine, at the
 * level render stage {@code AFTER_PARTICLES} with the event's model-view and projection matrices).
 *
 * <p>
 * World positions are relative to the camera, the way the level render stage works with them: pass
 * {@code blockPos - camera.getPosition()}. Radii are in blocks. Always call {@link #ready()} first: the shaders are
 * not there until the resources have loaded.
 *
 * <pre>{@code
 * Matrix4f view = event.getModelViewMatrix();
 * Matrix4f projection = event.getProjectionMatrix();
 * ContinuumRender.beginWorldObject(view, projection, centre); // blocks in front now hide black holes
 * ContinuumRender.drawBlackHole(system, view, projection, centre, 0.5f);
 * ContinuumRender.endWorldObject();
 * }</pre>
 */
public final class ContinuumRender {

    private ContinuumRender() {}

    /** Whether the Continuum shaders are loaded and can draw. */
    public static boolean ready() {
        return ContinuumShaders.ready();
    }

    /** The quality the player has chosen for the Continuum views. */
    public static PlanetRenderer.Quality quality() {
        return ContinuumVisuals.quality();
    }

    /**
     * Tells black holes how deep the thing being drawn is, so blocks in front of it hide it (they are drawn aside and
     * laid over the picture, and would otherwise show through everything). Pair with {@link #endWorldObject()}.
     */
    public static void beginWorldObject(Matrix4f view, Matrix4f projection, Vector3f cameraRelativeCentre) {
        org.joml.Vector4f clip = new org.joml.Vector4f(cameraRelativeCentre, 1.0f).mul(view).mul(projection);
        SceneRenderer.setWorldDepth(clip.w > 0.0f ? clip.z / clip.w : Float.NaN);
    }

    public static void endWorldObject() {
        SceneRenderer.setWorldDepth(Float.NaN);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
    }

    /** A star as {@code system} describes it (its type, colour and style), or a plain star of {@code rgb} without one. */
    public static void drawStar(@Nullable ContinuumSystem system, int rgb, Matrix4f view, Matrix4f projection,
                                Vector3f worldPos, float radius) {
        SceneRenderer.drawSystemStar(system, rgb, view, projection, worldPos, radius, quality(), false);
    }

    /** A black hole with its accretion disk and lensing, in the style {@code system} gives it. */
    public static void drawBlackHole(@Nullable ContinuumSystem system, Matrix4f view, Matrix4f projection,
                                     Vector3f worldPos, float radius) {
        SceneRenderer.drawHole(system, view, projection, worldPos, radius, quality(), false);
    }

    /**
     * A black hole whose accretion disk is tinted: {@code hotRgb} for the inner edge and {@code coolRgb} for the outer
     * (0xRRGGBB, or -1 to keep the default for either).
     */
    public static void drawBlackHole(int hotRgb, int coolRgb, Matrix4f view, Matrix4f projection, Vector3f worldPos,
                                     float radius) {
        SceneRenderer.drawHoleStyled(
                net.phoenix.continuum.data.ContinuumSystem.HoleStyle.DEFAULT.withColors(hotRgb, coolRgb), view,
                projection, worldPos, radius, quality(), false);
    }

    /** A young star still gathering itself: a hot core in a disk of gas with gaps where it is clearing a path. */
    public static void drawProtostar(int rgb, Matrix4f view, Matrix4f projection, Vector3f worldPos, float radius) {
        SceneRenderer.drawProtostar(rgb, view, projection, worldPos, radius, quality(), false,
                new float[] { 3.4f, 5.1f, 7.3f, 0.0f });
    }

    /** A cloud of glowing gas, an expanding shell of it lit from within. */
    public static void drawGasCloud(int rgb, Matrix4f view, Matrix4f projection, Vector3f worldPos, float radius) {
        SceneRenderer.drawPlanetaryNebula(rgb, view, projection, worldPos, radius, quality(), false);
    }

    /**
     * A Dyson swarm or shell round a star, or a Penrose swarm or sphere round a black hole, partly built:
     * {@code completion} is 0 (nothing) to 1 (finished). {@code central} is the system's star or black hole body.
     */
    public static void drawMegastructure(ContinuumBody central, int rgb, Matrix4f view, Matrix4f projection,
                                         Vector3f worldPos, float radius, boolean shell, float completion) {
        SceneRenderer.drawSwarmCompletion(central, rgb, view, projection, worldPos, radius, quality(), false, shell,
                completion);
    }

    /** A planet or moon from its parameters, lit from {@code sunWorld}. */
    public static void drawPlanet(PlanetParams params, Matrix4f view, Matrix4f projection, Vector3f worldPos,
                                  float radius, Vector3f sunWorld, float spinDegrees, float cloudSpinDegrees,
                                  boolean atmosphere) {
        SceneRenderer.drawPlanet(params, view, projection, worldPos, radius, sunWorld, spinDegrees, cloudSpinDegrees,
                quality(), atmosphere);
    }

    /**
     * A glowing beam from {@code a} to {@code b} (camera-relative): a soft halo, a body and a white core. Draw into
     * {@code RenderType.lightning()}; {@code pose} is an identity matrix at the level render stage.
     */
    public static void drawBeam(VertexConsumer out, Matrix4f pose, Vector3f a, Vector3f b, float width, int rgb,
                                float strength) {
        HologramRenderer.glow(out, pose, a, b, width, new float[] { ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f }, strength);
    }

    /** A bright dash at {@code t} (0 to 1) along a beam, for light that seems to travel down it. */
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

    /** What a {@link Preview} can show. */
    public enum Body {
        GAS_CLOUD,
        PROTOSTAR,
        STAR,
        BLACK_HOLE
    }

    private static @Nullable Preview shared;

    /** One preview for everyone to share: it keeps a single render target, so screens need not manage their own. */
    public static synchronized Preview preview() {
        if (shared == null) shared = new Preview();
        return shared;
    }

    /**
     * A body drawn with the real shaders into a square of a GUI screen, turning slowly, that the player can drag to
     * rotate and scroll to zoom. Call {@link #draw} every frame from a screen or widget.
     */
    public static final class Preview {

        private static final int PIXELS = 512;

        private com.mojang.blaze3d.pipeline.@Nullable RenderTarget target;
        private float yaw = 20.0f;
        private float pitch = 14.0f;
        private float distance = 4.6f;
        private long last = net.minecraft.Util.getMillis();

        private Preview() {}

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

        /**
         * @param size01 how big the body is, 0 (small) to 1 (large)
         * @return false when it could not be drawn (the shaders are not loaded): draw something else
         */
        public boolean draw(net.minecraft.client.gui.GuiGraphics g, Body body, int rgb, float size01, int x, int y,
                            int size) {
            return draw(g, body, rgb, size01, x, y, size, true);
        }

        /** As above; {@code backdrop} false draws the body alone on plain dark space, with no stars or galaxies. */
        public boolean draw(net.minecraft.client.gui.GuiGraphics g, Body body, int rgb, float size01, int x, int y,
                            int size, boolean backdrop) {
            return draw(g, body, rgb, size01, x, y, size, backdrop, -1);
        }

        /** As above; {@code holeTint} (0xRRGGBB, or -1) colours a black hole's disk. */
        public boolean draw(net.minecraft.client.gui.GuiGraphics g, Body body, int rgb, float size01, int x, int y,
                            int size, boolean backdrop, int holeTint) {
            if (!ContinuumShaders.ready()) return false;
            long now = net.minecraft.Util.getMillis();
            float dt = Math.min(0.1f, (now - last) / 1000.0f);
            last = now;
            yaw += dt * 4.0f;
            if (target == null) {
                target = new com.mojang.blaze3d.pipeline.TextureTarget(PIXELS, PIXELS, true,
                        net.minecraft.client.Minecraft.ON_OSX);
            }
            // the screen this is drawn in may have a scissor on (a page clips to its area); the offscreen picture must not
            boolean scissored = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            int[] box = new int[4];
            if (scissored) {
                org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, box);
                com.mojang.blaze3d.systems.RenderSystem.disableScissor();
            }
            try {
                SceneRenderer.begin(target, yaw, backdrop);
                Matrix4f projection = SceneRenderer.projection(target.width, target.height, 32.0f);
                Matrix4f view = new Matrix4f().translate(0.0f, 0.0f, -(distance + 1.5f))
                        .rotateX((float) Math.toRadians(pitch));
                Vector3f at = new Vector3f();
                float radius = 0.6f + 0.9f * Math.max(0.0f, Math.min(1.0f, size01));
                // clouds and protostars carry their glow far out from the core: scale them to fit the frame
                if (body == Body.GAS_CLOUD || body == Body.PROTOSTAR) radius *= 0.067f;
                var quality = quality();
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

            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, target.getColorTextureId());
            Matrix4f pose = g.pose().last().pose();
            var bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            bb.addVertex(pose, x, y + size, 0).setUv(0, 0);
            bb.addVertex(pose, x + size, y + size, 0).setUv(1, 0);
            bb.addVertex(pose, x + size, y, 0).setUv(1, 1);
            bb.addVertex(pose, x, y, 0).setUv(0, 1);
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.buildOrThrow());
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            return true;
        }
    }
}
