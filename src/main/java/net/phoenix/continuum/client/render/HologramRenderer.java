package net.phoenix.continuum.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.client.ContinuumVisuals;
import net.phoenix.continuum.common.Swarms;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Swarm Control hologram. It is built piece by piece as the control comes online:
 * emitters light round a ring one after another and fire beams into the middle, the middle swells into the star or
 * black hole, and then the swarm or shell round it forms up to how built it really is. When the control loses power
 * or is broken it comes apart again, quickly.
 */
@EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class HologramRenderer {

    private static final double HEIGHT = 4.2;
    private static final float STAR_RADIUS = 0.3f;
    private static final float RING_RADIUS = 2.3f;
    private static final int EMITTERS = 12;
    private static final double RANGE = 72.0;

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
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        Matrix4f projection = event.getProjectionMatrix();
        PoseStack pose = new PoseStack();
        pose.mulPose(view);
        var buffers = mc.renderBuffers().bufferSource();
        float seconds = (float) (System.currentTimeMillis() % 100000L) / 1000.0f;

        for (HologramClient.Holo holo : HologramClient.all()) {
            holo.step();
            if (holo.boot <= 0.0f) continue;
            var state = holo.state;
            double cx = state.pos().getX() + 0.5 + state.offsetX() - camera.x;
            double cy = state.pos().getY() + HEIGHT - camera.y;
            double cz = state.pos().getZ() + 0.5 + state.offsetZ() - camera.z;
            if (cx * cx + cy * cy + cz * cz > RANGE * RANGE) continue;

            ContinuumBody body = ContinuumData.body(state.target());
            var system = body == null ? null : ContinuumData.system(body.system());
            boolean shown = body != null && system != null && Swarms.isSwarm(body);
            int tint = shown ? (Swarms.isPenrose(body) ? 0x9a78ff : system.starColor()) : 0xffd9a0;

            float time = holo.boot * HologramClient.BOOT_SECONDS;
            float emit = ease(time / 2.2f);
            float star = ease((time - 2.4f) / 2.2f);
            float structure = ease((time - 4.6f) / 3.2f);
            float flicker = holo.state.active() ? 1.0f : 0.55f + 0.45f * (float) Math.sin(seconds * 40.0f);

            VertexConsumer lines = buffers.getBuffer(RenderType.lightning());
            beams(lines, pose.last().pose(), cx, cy, cz, emit, star, seconds, tint, flicker);
            buffers.endBatch(RenderType.lightning());

            if (shown && star > 0.001f) {
                Vector3f at = new Vector3f((float) cx, (float) cy, (float) cz);
                float radius = STAR_RADIUS * star;
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
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
            }
        }
    }

    private static void beams(VertexConsumer out, Matrix4f pose, double cx, double cy, double cz, float emit,
                              float star, float seconds, int tint, float flicker) {
        float tr = ((tint >> 16) & 0xFF) / 255.0f;
        float tg = ((tint >> 8) & 0xFF) / 255.0f;
        float tb = (tint & 0xFF) / 255.0f;
        float r = Mth.lerp(0.4f, 0.4f, tr);
        float g = Mth.lerp(0.4f, 0.85f, tg);
        float b = Mth.lerp(0.4f, 1.0f, tb);

        Vector3f centre = new Vector3f((float) cx, (float) cy, (float) cz);

        // the projector: a pillar of light up from the block into the middle, then a turning ring of light
        float pillar = ease(emit * 1.6f);
        if (pillar > 0.0f) {
            Vector3f base = new Vector3f((float) cx, (float) (cy - HEIGHT + 1.02), (float) cz);
            Vector3f top = new Vector3f(base).lerp(centre, pillar);
            line(out, pose, base, top, 0.09f, r, g, b, 0.55f * flicker);
        }

        float ringGrow = ease(emit * 1.3f);
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments + seconds * 0.35;
            double a1 = Math.PI * 2.0 * (i + 1) / segments + seconds * 0.35;
            float fade = ringGrow * (0.5f + 0.5f * (float) Math.sin(a0 * 3.0 - seconds * 2.0));
            if (fade <= 0.02f) continue;
            float rad = RING_RADIUS * ringGrow;
            Vector3f p0 = new Vector3f((float) (cx + Math.cos(a0) * rad), (float) cy, (float) (cz + Math.sin(a0) * rad));
            Vector3f p1 = new Vector3f((float) (cx + Math.cos(a1) * rad), (float) cy, (float) (cz + Math.sin(a1) * rad));
            line(out, pose, p0, p1, 0.025f, r, g, b, 0.7f * fade * flicker);
        }

        // emitters light round the ring one after another and fire into the middle
        float lit = emit * EMITTERS;
        for (int i = 0; i < EMITTERS; i++) {
            float on = Mth.clamp(lit - i, 0.0f, 1.0f);
            if (on <= 0.0f) continue;
            double angle = Math.PI * 2.0 * i / EMITTERS + seconds * 0.35;
            double tilt = (i % 2 == 0 ? 0.45 : -0.45) * ringGrow;
            Vector3f node = new Vector3f((float) (cx + Math.cos(angle) * RING_RADIUS),
                    (float) (cy + Math.sin(angle * 2.0 + seconds) * 0.12 + tilt * 0.6),
                    (float) (cz + Math.sin(angle) * RING_RADIUS));
            line(out, pose, new Vector3f(node).add(0.0f, -0.12f, 0.0f), new Vector3f(node).add(0.0f, 0.12f, 0.0f),
                    0.07f, 1.0f, 1.0f, 1.0f, 0.9f * on * flicker);

            float pulse = 0.6f + 0.4f * (float) Math.sin(seconds * 7.0 + i * 1.3);
            float reach = on * (star < 0.01f ? 1.0f : 0.8f);
            Vector3f end = new Vector3f(node).lerp(centre, reach);
            float width = star > 0.01f ? 0.018f : 0.04f;
            line(out, pose, node, end, width * 2.6f, r, g, b, 0.16f * pulse * flicker);
            line(out, pose, node, end, width, 1.0f, 1.0f, 1.0f, 0.75f * pulse * on * flicker);
        }

        // before the star swells there is only a bright point, growing where the beams meet
        if (star < 0.4f && emit > 0.4f) {
            float spark = ease((emit - 0.4f) * 1.7f) * (1.0f - star / 0.4f);
            line(out, pose, new Vector3f(centre).add(0.0f, -0.35f * spark, 0.0f),
                    new Vector3f(centre).add(0.0f, 0.35f * spark, 0.0f), 0.16f * spark, 1.0f, 1.0f, 1.0f,
                    0.9f * flicker);
        }
    }

    /** A flat strip from a to b that turns to face the camera (which is at the origin). */
    private static void line(VertexConsumer out, Matrix4f pose, Vector3f a, Vector3f b, float width, float r, float g,
                             float bl, float alpha) {
        if (alpha <= 0.01f) return;
        Vector3f dir = new Vector3f(b).sub(a);
        if (dir.lengthSquared() < 1.0e-8f) return;
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        Vector3f side = dir.cross(mid, new Vector3f());
        if (side.lengthSquared() < 1.0e-10f) return;
        side.normalize(width * 0.5f);
        int ri = (int) (Mth.clamp(r, 0.0f, 1.0f) * 255.0f);
        int gi = (int) (Mth.clamp(g, 0.0f, 1.0f) * 255.0f);
        int bi = (int) (Mth.clamp(bl, 0.0f, 1.0f) * 255.0f);
        int ai = (int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        out.addVertex(pose, a.x - side.x, a.y - side.y, a.z - side.z).setColor(ri, gi, bi, ai);
        out.addVertex(pose, a.x + side.x, a.y + side.y, a.z + side.z).setColor(ri, gi, bi, ai);
        out.addVertex(pose, b.x + side.x, b.y + side.y, b.z + side.z).setColor(ri, gi, bi, ai);
        out.addVertex(pose, b.x - side.x, b.y - side.y, b.z - side.z).setColor(ri, gi, bi, ai);
    }
}
