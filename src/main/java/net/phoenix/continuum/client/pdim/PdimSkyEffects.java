package net.phoenix.continuum.client.pdim;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.phoenix.continuum.client.ContinuumVisuals;
import net.phoenix.continuum.client.render.ContinuumShaders;
import net.phoenix.continuum.client.render.SceneRenderer;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.pdim.PdimDimensions;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class PdimSkyEffects extends DimensionSpecialEffects {

    public PdimSkyEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    private static volatile Vec3 horizonFog = null;

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        Vec3 fog = horizonFog;
        return fog != null ? fog : fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        ResourceLocation id = PdimDimensions.bodyOf(level.dimension());
        ContinuumBody body = id == null ? null : ContinuumData.body(id);
        ContinuumSystem system = body == null ? null : ContinuumData.system(body.system());
        Matrix4f view = new Matrix4f(poseStack.last().pose());
        float time = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;

        PdimSkyView live = PdimSkyView.live(level, partialTick);
        PdimSkyModel.Sample sample = drawScene(view, projectionMatrix, live, body, system, time);
        float[] fog = sample.horizonNow();
        horizonFog = new Vec3(fog[0], fog[1], fog[2]);
        return true;
    }

    /**
     * Draws the sky as seen from {@code body}: nebula, horizon, star, the other worlds. Used by the live sky and by the
     * tuner's sky view. Returns the sample it drew with (the sun, the air colours, the eclipse).
     */
    public static PdimSkyModel.Sample drawScene(Matrix4f view, Matrix4f projection, PdimSkyView view0,
                                                ContinuumBody body, ContinuumSystem system, float time) {
        PdimSkyModel.Sample sample = PdimSkyModel.sample(view0, body, system);
        if (!view0.orbit()) {
            sample = sample.withEclipse(PdimSkyBodies.eclipse(view0, body, sample));
        }

        try (var timer = net.phoenix.continuum.client.ContinuumProfiler.scope("pdim.sky")) {
            drawNebula(view, projection, time, body, system, sample, view0.orbit());
        }

        if (body != null && ContinuumShaders.ready()) {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            // standing on a world: its horizon hides what has set, so the star and the other worlds sink behind it
            if (sample.hasSun() && !view0.orbit()) SceneRenderer.drawHorizonPlane(view, projection);

            if (sample.hasSun() && system != null && sample.sunDir().y > -0.25f) {
                Vector3f sunPos = new Vector3f(sample.sunDir()).mul(98.0f);
                if (system.hasHole()) {
                    SceneRenderer.drawHole(system, body, view, projection, sunPos, 4.5f, ContinuumVisuals.quality(),
                            true);
                } else {
                    SceneRenderer.drawSystemStar(system, system.starColor(), view, projection, sunPos, 6.0f,
                            ContinuumVisuals.quality(), true);
                }
                RenderSystem.enableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
            }

            Vector3f position = new Vector3f(52.0f, 30.0f, -76.0f);
            boolean centralBody = body.type() == ContinuumBody.Type.STAR ||
                    body.type() == ContinuumBody.Type.BLACK_HOLE;
            boolean quasar = system != null && system.isQuasar();
            
            float radius = body.type() == ContinuumBody.Type.BLACK_HOLE ? (quasar ? 8.5f : 9.0f) :
                    centralBody ? 16.0f : 22.0f;

            boolean orbit = view0.orbit();
            if (!SceneRenderer.drawCentralBody(body, view, projection, position, radius,
                    ContinuumVisuals.quality(), true)) {
                
                if (!orbit) {
                    try (var timer = net.phoenix.continuum.client.ContinuumProfiler.scope("pdim.system_bodies")) {
                        PdimSkyBodies.draw(view0, view, projection, body, sample, time);
                    }
                }
                
                if (orbit || body.isMoon()) {

                ContinuumBody orbBody = body;
                float orbRadius = radius;
                Vector3f light = new Vector3f(-300.0f, 220.0f, 260.0f);
                if (body.isMoon() && body.parent() != null) {
                    ContinuumBody host = ContinuumData.body(body.parent());
                    if (host != null) {
                        orbBody = host;
                        orbRadius = Math.max(22.0f, Math.min(42.0f, 20.0f + 4.0f * (float) Math.sqrt(host.radius())));
                        if (sample.hasSun()) light = new Vector3f(sample.sunDir()).mul(400.0f);
                    }
                }
                var params = orbBody.params();
                SceneRenderer.drawPlanet(params, view, projection, position, orbRadius, light,
                        time * params.spinDegPerSec() * 0.5f, time * params.cloudSpinDegPerSec() * 0.5f,
                        ContinuumVisuals.quality(), true);
                }
            }
        }

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        return sample;
    }

    private static void drawNebula(Matrix4f view, Matrix4f projection, float time, ContinuumBody body,
                                   ContinuumSystem system, PdimSkyModel.Sample sample, boolean orbit) {
        ShaderInstance sky = ContinuumShaders.SKY;
        if (sky == null) return;

        int primary = body != null ? body.params().atmoDensity() > 0.05f ? body.params().atmoColor() :
                body.params().high() : 0x5a3aa8;
        int secondary = system != null ? system.starColor() : 0x2a5ad0;

        float[] primaryRgb = {((primary >> 16) & 0xFF) / 255.0f * 0.9f, ((primary >> 8) & 0xFF) / 255.0f * 0.9f,
                (primary & 0xFF) / 255.0f * 0.9f};
        float[] secondaryRgb = {((secondary >> 16) & 0xFF) / 255.0f * 0.7f, ((secondary >> 8) & 0xFF) / 255.0f * 0.7f,
                (secondary & 0xFF) / 255.0f * 0.7f};
        
        var baked = net.phoenix.continuum.client.render.NebulaBake.get(primaryRgb, secondaryRgb,
                ContinuumVisuals.backdropOctaves());

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.setShader(() -> sky);
        sky.safeGetUniform("UseBake").set(baked != null ? 1.0f : 0.0f);
        if (baked != null) sky.setSampler("NebulaTex", baked);

        sky.safeGetUniform("InvViewMat").set(new Matrix4f(view).invert());
        sky.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        sky.safeGetUniform("Time").set(time);
        sky.safeGetUniform("Octaves").set((float) ContinuumVisuals.backdropOctaves());
        sky.safeGetUniform("PrimaryColor").set(((primary >> 16) & 0xFF) / 255.0f * 0.9f,
                ((primary >> 8) & 0xFF) / 255.0f * 0.9f, (primary & 0xFF) / 255.0f * 0.9f);
        sky.safeGetUniform("SecondaryColor").set(((secondary >> 16) & 0xFF) / 255.0f * 0.7f,
                ((secondary >> 8) & 0xFF) / 255.0f * 0.7f, (secondary & 0xFF) / 255.0f * 0.7f);

        sky.safeGetUniform("SunDir").set(sample.sunDir().x, sample.sunDir().y, sample.sunDir().z);
        sky.safeGetUniform("Atmo").set(sample.atmo());
        sky.safeGetUniform("ZenithColor").set(sample.zenith()[0], sample.zenith()[1], sample.zenith()[2]);
        sky.safeGetUniform("HorizonColor").set(sample.horizon()[0], sample.horizon()[1], sample.horizon()[2]);
        sky.safeGetUniform("DuskColor").set(sample.dusk()[0], sample.dusk()[1], sample.dusk()[2]);
        sky.safeGetUniform("SunColor").set(sample.sunColor()[0], sample.sunColor()[1], sample.sunColor()[2]);
        sky.safeGetUniform("Ground").set(sample.hasSun() && !orbit ? 1.0f : 0.0f);
        sky.safeGetUniform("Eclipse").set(sample.eclipse());
        sky.safeGetUniform("Aurora").set(sample.aurora());
        sky.safeGetUniform("AuroraColor").set(sample.auroraColor()[0], sample.auroraColor()[1], sample.auroraColor()[2]);
        sky.safeGetUniform("Storm").set(sample.storm());
        sky.safeGetUniform("Lens").set(sample.lens());

        sky.apply();
        BufferBuilder bb = net.phoenix.continuum.client.render.Vtx.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1, -1, 0).endVertex();
        bb.vertex(1, -1, 0).endVertex();
        bb.vertex(1, 1, 0).endVertex();
        bb.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(bb.end());
        sky.clear();
    }
}