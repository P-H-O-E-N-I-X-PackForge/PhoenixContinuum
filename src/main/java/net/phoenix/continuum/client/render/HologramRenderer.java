package net.phoenix.continuum.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.client.ContinuumVisuals;
import net.phoenix.continuum.common.Swarms;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.network.S2CHologramPacket;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The in-world displays of Swarm Control and the Swarm Receiver.
 *
 * <p>
 * The control's hologram is built piece by piece as it comes online: a pillar of light rises from the block, a
 * gyroscope of rings spins up, emitters light round the ring one after another and fire pulsing beams into the
 * middle, the middle swells into the star or black hole, and then the swarm or shell round it forms up to how built it
 * really is. When the control loses power or is broken it comes apart again, quickly.
 *
 * <p>
 * The receiver's uplink is a column of light into the sky with pulses running up it, brighter the fuller the receiver.
 */
@Mod.EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class HologramRenderer {

    private static final double HEIGHT = 4.2;
    private static final float STAR_RADIUS = 0.3f;
    private static final float RING_RADIUS = 2.3f;
    private static final int EMITTERS = 12;
    private static final double RANGE = 96.0;

    private HologramRenderer() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HologramClient.clear();
    }

    private static float ease(float x) {
        float t = Mth.clamp(x, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        HologramClient.prune();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ContinuumShaders.ready()) return;

        Vec3 camera = event.getCamera().getPosition();
        // 1.20.1: the pose stack already carries the camera turn
        Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
        Matrix4f projection = event.getProjectionMatrix();
        Matrix4f pose = view;
        var buffers = mc.renderBuffers().bufferSource();
        float seconds = (float) (System.currentTimeMillis() % 100000L) / 1000.0f;

        for (HologramClient.Holo holo : HologramClient.all()) {
            holo.step();
            if (holo.boot <= 0.0f) continue;
            var state = holo.state;
            double scale = state.kind() == S2CHologramPacket.State.CONTROL ? state.scale() : 1.0;
            double height = state.kind() == S2CHologramPacket.State.CONTROL ? HEIGHT + (scale - 1.0) * 1.4 : 1.0;
            double cx = state.pos().getX() + 0.5 + state.offsetX() - camera.x;
            double cy = state.pos().getY() + height - camera.y;
            double cz = state.pos().getZ() + 0.5 + state.offsetZ() - camera.z;
            if (cx * cx + cy * cy + cz * cz > RANGE * RANGE) continue;
            float flicker = state.active() ? 1.0f : 0.55f + 0.45f * (float) Math.sin(seconds * 40.0f);
            // once it is all there and nothing is happening the light settles and breathes; a change flares it
            float calm = 0.17f + 0.05f * (float) Math.sin(seconds * 1.3f);
            flicker *= Mth.lerp(holo.settle, 1.0f, calm + (1.0f - calm) * holo.surge);

            if (state.kind() == S2CHologramPacket.State.RECEIVER) {
                VertexConsumer out = buffers.getBuffer(RenderType.lightning());
                uplink(out, pose, cx, cy, cz, ease(holo.boot), seconds, state.swarm() / 100.0f, flicker);
                buffers.endBatch(RenderType.lightning());
                continue;
            }

            ContinuumBody body = ContinuumData.body(state.target());
            var system = body == null ? null : ContinuumData.system(body.system());
            boolean shown = body != null && system != null && Swarms.isSwarm(body);
            int tint = shown ? (Swarms.isPenrose(body) ? 0x9a78ff : system.starColor()) : 0xffd9a0;

            float time = holo.boot * HologramClient.BOOT_SECONDS;
            float emit = ease(time / 2.2f);
            float star = ease((time - 2.4f) / 2.2f);
            float structure = ease((time - 4.6f) / 3.2f);
            float s = (float) scale;

            VertexConsumer lines = buffers.getBuffer(RenderType.lightning());
            beams(lines, pose, cx, cy, cz, emit, star, seconds, tint, flicker, s, height);
            buffers.endBatch(RenderType.lightning());

            if (shown && star > 0.001f) {
                Vector3f at = new Vector3f((float) cx, (float) cy, (float) cz);
                // blocks between the player and the hologram hide it: tell the black hole how deep the middle is
                org.joml.Vector4f clip = new org.joml.Vector4f(at, 1.0f).mul(view).mul(projection);
                SceneRenderer.setWorldDepth(clip.w > 0.0f ? clip.z / clip.w : Float.NaN);
                float radius = STAR_RADIUS * s * star;
                if (Swarms.isPenrose(body)) {
                    SceneRenderer.drawHole(system, view, projection, at, radius, ContinuumVisuals.quality(), false);
                } else {
                    SceneRenderer.drawSystemStar(system, system.starColor(), view, projection, at, radius,
                            ContinuumVisuals.quality(), false);
                }
                boolean shell = state.shell() > 0;
                float real = shell ? state.shell() / (float) Math.max(1, state.shellMax()) :
                        state.swarm() / (float) Math.max(1, state.swarmMax());
                if (real > 0.0f && structure > 0.0f) {
                    SceneRenderer.drawSwarmCompletion(body, system.starColor(), view, projection, at, radius,
                            ContinuumVisuals.quality(), false, shell, Math.min(1.0f, real) * structure);
                }
                SceneRenderer.setWorldDepth(Float.NaN);
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the control

    private static float[] holo(int tint, float mix) {
        float tr = ((tint >> 16) & 0xFF) / 255.0f;
        float tg = ((tint >> 8) & 0xFF) / 255.0f;
        float tb = (tint & 0xFF) / 255.0f;
        return new float[] { Mth.lerp(mix, 0.35f, tr), Mth.lerp(mix, 0.85f, tg), Mth.lerp(mix, 1.0f, tb) };
    }

    private static void beams(VertexConsumer out, Matrix4f pose, double cx, double cy, double cz, float emit,
                              float star, float seconds, int tint, float flicker, float scale, double height) {
        float[] c = holo(tint, 0.35f);
        Vector3f centre = new Vector3f((float) cx, (float) cy, (float) cz);
        float ringRadius = RING_RADIUS * scale;

        // the projector: a pillar of light up from the block into the middle
        float pillar = ease(emit * 1.6f);
        if (pillar > 0.0f) {
            Vector3f base = new Vector3f((float) cx, (float) (cy - height + 1.02), (float) cz);
            Vector3f top = new Vector3f(base).lerp(centre, pillar);
            glow(out, pose, base, top, 0.1f * scale, c, 0.6f * flicker);
            for (int k = 0; k < 3; k++) {
                float t = (seconds * 0.9f + k / 3.0f) % 1.0f;
                pulse(out, pose, base, top, t, 0.09f, 0.1f * scale, 1.0f * flicker);
            }
        }

        // a gyroscope: three turning rings that grow as it comes up
        float grow = ease(emit * 1.3f);
        for (int ring = 0; ring < 3; ring++) {
            float tilt = ring == 0 ? 0.0f : (ring == 1 ? 1.05f : -1.05f);
            float spin = seconds * (ring == 0 ? 0.35f : (ring == 1 ? -0.5f : 0.6f));
            float rad = ringRadius * grow * (ring == 0 ? 1.0f : 0.78f);
            if (rad <= 0.01f) continue;
            int segments = 72;
            Vector3f previous = null;
            for (int i = 0; i <= segments; i++) {
                double a = Math.PI * 2.0 * i / segments + spin;
                float px = (float) Math.cos(a) * rad;
                float pz = (float) Math.sin(a) * rad;
                float py = pz * (float) Math.sin(tilt);
                pz *= (float) Math.cos(tilt);
                Vector3f point = new Vector3f((float) cx + px, (float) cy + py, (float) cz + pz);
                if (previous != null) {
                    float fade = 0.4f + 0.6f * (float) Math.max(0.0, Math.sin(a * 2.0 - seconds * 2.5));
                    ribbon(out, pose, previous, point, 0.028f * scale, c, 0.75f * fade * grow * flicker);
                }
                previous = point;
            }
        }

        // emitters light round the ring one after another and fire into the middle
        float lit = emit * EMITTERS;
        for (int i = 0; i < EMITTERS; i++) {
            float on = Mth.clamp(lit - i, 0.0f, 1.0f);
            if (on <= 0.0f) continue;
            double angle = Math.PI * 2.0 * i / EMITTERS + seconds * 0.35;
            float bob = (float) Math.sin(angle * 2.0 + seconds) * 0.12f * scale;
            Vector3f node = new Vector3f((float) (cx + Math.cos(angle) * ringRadius), (float) cy + bob,
                    (float) (cz + Math.sin(angle) * ringRadius));
            // the emitter: a bright diamond with a halo
            float half = 0.14f * scale;
            glow(out, pose, new Vector3f(node).add(0.0f, -half, 0.0f), new Vector3f(node).add(0.0f, half, 0.0f),
                    0.09f * scale, c, 0.95f * on * flicker);
            Vector3f side = new Vector3f((float) -Math.sin(angle), 0.0f, (float) Math.cos(angle)).mul(half);
            glow(out, pose, new Vector3f(node).sub(side), new Vector3f(node).add(side), 0.07f * scale, c,
                    0.8f * on * flicker);

            // the beam: halo, body and a hard core, with bright pulses running into the star
            float reach = on * (star < 0.01f ? 1.0f : 0.84f);
            Vector3f end = new Vector3f(node).lerp(centre, reach);
            float strength = (star < 0.01f ? 1.0f : 0.6f) * on * flicker;
            glow(out, pose, node, end, (star > 0.01f ? 0.05f : 0.1f) * scale, c, strength);
            for (int k = 0; k < 3; k++) {
                float t = (seconds * 0.75f + i * 0.137f + k / 3.0f) % 1.0f;
                pulse(out, pose, node, end, t, 0.07f, 0.085f * scale, 1.0f * on * flicker);
            }
        }

        // before the star swells there is only a bright point, growing where the beams meet
        if (star < 0.4f && emit > 0.4f) {
            float spark = ease((emit - 0.4f) * 1.7f) * (1.0f - star / 0.4f);
            glow(out, pose, new Vector3f(centre).add(0.0f, -0.4f * spark * scale, 0.0f),
                    new Vector3f(centre).add(0.0f, 0.4f * spark * scale, 0.0f), 0.2f * scale, c, 0.95f * flicker);
            glow(out, pose, new Vector3f(centre).add(-0.4f * spark * scale, 0.0f, 0.0f),
                    new Vector3f(centre).add(0.4f * spark * scale, 0.0f, 0.0f), 0.2f * scale, c, 0.95f * flicker);
        }
    }

    // ------------------------------------------------------------------------------------------------ the receiver

    /** A column of light into the sky from a receiver, with pulses running up it, brighter the fuller it is. */
    private static void uplink(VertexConsumer out, Matrix4f pose, double cx, double cy, double cz, float boot,
                               float seconds, float fill, float flicker) {
        float[] c = { 1.0f, 0.78f, 0.4f };
        float height = 26.0f * boot;
        float power = 0.35f + 0.65f * Mth.clamp(fill, 0.0f, 1.0f);
        Vector3f base = new Vector3f((float) cx, (float) cy, (float) cz);
        Vector3f top = new Vector3f(base).add(0.0f, height, 0.0f);
        glow(out, pose, base, top, 0.22f, c, power * 0.9f * flicker);
        for (int k = 0; k < 6; k++) {
            float t = (seconds * (0.5f + 0.5f * power) + k / 6.0f) % 1.0f;
            pulse(out, pose, base, top, t, 0.06f, 0.18f, flicker);
        }
        // rings that climb the column
        for (int ring = 0; ring < 4; ring++) {
            float t = (seconds * 0.25f + ring * 0.25f) % 1.0f;
            float y = (float) cy + height * t;
            float rad = 0.7f + 0.9f * t;
            int segments = 40;
            Vector3f previous = null;
            for (int i = 0; i <= segments; i++) {
                double a = Math.PI * 2.0 * i / segments + seconds * 0.8 * (ring % 2 == 0 ? 1 : -1);
                Vector3f point = new Vector3f((float) (cx + Math.cos(a) * rad), y, (float) (cz + Math.sin(a) * rad));
                if (previous != null) ribbon(out, pose, previous, point, 0.03f, c, (1.0f - t) * 0.8f * power * flicker);
                previous = point;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ primitives

    /**
     * A beam: a wide faint halo, a body and a thin hot core, each fading smoothly to nothing across its width, so
     * it reads as light and not as a stack of bars.
     */
    public static void glow(VertexConsumer out, Matrix4f pose, Vector3f a, Vector3f b, float width, float[] c,
                            float strength) {
        ribbon(out, pose, a, b, width * 4.0f, c, 0.22f * strength);
        ribbon(out, pose, a, b, width * 1.8f, c, 0.55f * strength);
        ribbon(out, pose, a, b, width * 0.6f, new float[] { 1.0f, 1.0f, 1.0f }, 1.0f * strength);
    }

    /** A soft bright dash at t (0 to 1) along a to b: brightest in its middle, fading out all round. */
    public static void pulse(VertexConsumer out, Matrix4f pose, Vector3f a, Vector3f b, float t, float length,
                             float width, float strength) {
        float t0 = Mth.clamp(t, 0.0f, 1.0f);
        float t1 = Mth.clamp(t + length, 0.0f, 1.0f);
        if (t1 <= t0 || strength <= 0.01f) return;
        Vector3f p0 = new Vector3f(a).lerp(b, t0);
        Vector3f p1 = new Vector3f(a).lerp(b, t1);
        float edge = (float) Math.sin(Math.PI * Mth.clamp(t, 0.0f, 1.0f));
        blob(out, pose, p0, p1, width * 2.6f, new float[] { 0.55f, 0.9f, 1.0f }, 0.55f * strength * edge);
        blob(out, pose, p0, p1, width * 1.0f, new float[] { 1.0f, 1.0f, 1.0f }, 1.0f * strength * edge);
    }

    private static Vector3f sideOf(Vector3f a, Vector3f b, float width) {
        Vector3f dir = new Vector3f(b).sub(a);
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        Vector3f side = dir.cross(mid, new Vector3f());
        if (dir.lengthSquared() < 1.0e-8f || side.lengthSquared() < 1.0e-10f) return null;
        return side.normalize(width * 0.5f);
    }

    /** A flat strip from a to b that turns to face the camera (at the origin): clear in the middle, fading at the edges. */
    public static void ribbon(VertexConsumer out, Matrix4f pose, Vector3f a, Vector3f b, float width, float[] c,
                              float alpha) {
        if (alpha <= 0.01f) return;
        Vector3f side = sideOf(a, b, width);
        if (side == null) return;
        Vector3f a0 = new Vector3f(a).sub(side);
        Vector3f a2 = new Vector3f(a).add(side);
        Vector3f b0 = new Vector3f(b).sub(side);
        Vector3f b2 = new Vector3f(b).add(side);
        float[] edge = { c[0], c[1], c[2], 0.0f };
        float[] mid = { c[0], c[1], c[2], alpha };
        quad(out, pose, a0, a, b, b0, edge, mid, mid, edge);
        quad(out, pose, a, a2, b2, b, mid, edge, edge, mid);
    }

    /** A soft oval of light along a to b, brightest at its centre, fading out in every direction. */
    private static void blob(VertexConsumer out, Matrix4f pose, Vector3f p0, Vector3f p1, float width, float[] c,
                             float alpha) {
        Vector3f side = sideOf(p0, p1, width);
        if (side == null || alpha <= 0.01f) return;
        Vector3f m = new Vector3f(p0).add(p1).mul(0.5f);
        Vector3f l0 = new Vector3f(p0).sub(side), r0 = new Vector3f(p0).add(side);
        Vector3f lm = new Vector3f(m).sub(side), rm = new Vector3f(m).add(side);
        Vector3f l1 = new Vector3f(p1).sub(side), r1 = new Vector3f(p1).add(side);
        float[] none = { c[0], c[1], c[2], 0.0f };
        float[] full = { c[0], c[1], c[2], alpha };
        quad(out, pose, l0, p0, m, lm, none, none, none, none);
        quad(out, pose, p0, r0, rm, m, none, none, none, full);
        quad(out, pose, p0, m, rm, r0, none, full, none, none);
        quad(out, pose, lm, m, p1, l1, none, full, none, none);
        quad(out, pose, m, rm, r1, p1, full, none, none, none);
    }

    /** One quad, with a colour at each corner so the light fades smoothly across it, drawn from both sides. */
    private static void quad(VertexConsumer out, Matrix4f pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                             float[] ca, float[] cb, float[] cc, float[] cd) {
        vertex(out, pose, a, ca);
        vertex(out, pose, b, cb);
        vertex(out, pose, c, cc);
        vertex(out, pose, d, cd);
        vertex(out, pose, d, cd);
        vertex(out, pose, c, cc);
        vertex(out, pose, b, cb);
        vertex(out, pose, a, ca);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, Vector3f p, float[] c) {
        out.vertex(pose, p.x, p.y, p.z).color((int) (Mth.clamp(c[0], 0.0f, 1.0f) * 255.0f),
                (int) (Mth.clamp(c[1], 0.0f, 1.0f) * 255.0f), (int) (Mth.clamp(c[2], 0.0f, 1.0f) * 255.0f),
                (int) (Mth.clamp(c[3], 0.0f, 1.0f) * 255.0f)).endVertex();
    }
}
