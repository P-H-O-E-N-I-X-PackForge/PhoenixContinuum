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
}
