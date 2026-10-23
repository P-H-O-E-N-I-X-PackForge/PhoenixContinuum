package net.phoenix.continuum.client.pdim;

import net.minecraft.client.multiplayer.ClientLevel;
import net.phoenix.continuum.client.ContinuumClientState;
import net.phoenix.continuum.client.ContinuumVisuals;
import net.phoenix.continuum.client.render.PlanetParams;
import net.phoenix.continuum.client.render.PlanetRenderer;
import net.phoenix.continuum.client.render.SceneRenderer;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PdimSkyBodies {

    private static final float SKY_DISTANCE = 90.0f;
    private static final int MAX_BODIES = 6;

    private PdimSkyBodies() {}

    private record Item(ContinuumBody body, Vector3f direction, float skyRadius, Vector3f light, boolean moon) {}

    private record Plan(Vector3f e1, Vector3f e2, Vector3f normal, List<Item> items) {}

    private static final float SUN_ANGULAR_RADIUS = (float) Math.atan(6.0 / 98.0);

    private static @org.jetbrains.annotations.Nullable Plan plan(PdimSkyView view0, ContinuumBody self,
                                                                  PdimSkyModel.Sample sample) {
        if (self == null || self.type() == ContinuumBody.Type.STAR || self.type() == ContinuumBody.Type.BLACK_HOLE) {
            return null;
        }

        float lat = PdimSkyModel.latitude(self);
        Vector3f e1 = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f e2 = new Vector3f(0.0f, (float) Math.cos(lat), (float) -Math.sin(lat));
        Vector3f normal = new Vector3f(0.0f, (float) Math.sin(lat), (float) Math.cos(lat));

        boolean fixed = view0.fixed();
        double thetaSun = Math.atan2(sample.sunDir().dot(e2), sample.sunDir().dot(e1));
        double days = view0.gameTicks() / 24000.0;

        ContinuumBody home = self.isMoon() && self.parent() != null ? ContinuumData.body(self.parent()) : self;
        if (home == null) home = self;
        double[] homePos = positionAu(home, days);
        double sunBearing = Math.atan2(-homePos[1], -homePos[0]);   

        List<Item> items = new ArrayList<>();
        for (ContinuumBody other : ContinuumData.bodiesOf(self.system())) {
            if (other.id().equals(self.id()) || other.id().equals(home.id()) || other.isCentral()) continue;
            if (other.transientInfo() != null) continue;
            if (!view0.showUndiscovered() && ContinuumClientState.stage(other.id()) == DiscoveryStage.UNKNOWN) continue;

            double bearing;
            float skyRadius;
            Vector3f light;
            if (other.isMoon()) {
                
                if (other.parent() == null || !other.parent().equals(home.id())) continue;
                double angle = 2.0 * Math.PI * (other.phase() + days / Math.max(0.05, other.periodDays()));
                bearing = angle;
                skyRadius = clamp(2.6f + 8.0f * (float) Math.sqrt(other.radius()) / (1.0f + 40.0f * other.orbitAu()),
                        2.0f, 15.0f);
                light = new Vector3f(sample.sunDir()).mul(400.0f);
            } else {
                double[] p = positionAu(other, days);
                double dx = p[0] - homePos[0];
                double dy = p[1] - homePos[1];
                double dist = Math.max(0.05, Math.hypot(dx, dy));
                bearing = Math.atan2(dy, dx);
                skyRadius = clamp(1.3f + 5.5f * (float) Math.sqrt(other.radius()) / (0.6f + (float) dist), 1.2f, 14.0f);
                
                double toStar = Math.atan2(-p[1], -p[0]);
                light = ecliptic(e1, e2, normal, thetaSun + (toStar - sunBearing), 0.0).mul(400.0f);
            }
            double seed = other.params().seed();
            double angle = thetaSun + (bearing - sunBearing);
            if (fixed) {
                
                double frac = ((bearing - sunBearing) / (2.0 * Math.PI)) % 1.0;
                if (frac < 0.0) frac += 1.0;
                angle = Math.PI * (0.10 + 0.80 * frac);
            }

            Vector3f direction;
            if (other.isMoon() && !fixed) {

                double wobble = 0.15 * Math.sin(2.0 * Math.PI * days / (Math.max(0.05, other.periodDays()) * 11.0) +
                        seed * 3.7);
                direction = ecliptic(e1, e2, normal, angle, wobble);
            } else {

                double inclination = 0.32 * Math.sin(seed * 12.9898) + 0.12 * Math.sin(seed * 78.233 + other.orbitAu());
                direction = ecliptic(e1, e2, normal, angle, inclination);
                
                double side = inclination >= 0.0 ? 1.0 : -1.0;
                double gap = 0.17 + 0.012 * skyRadius;
                for (int k = 0; k < 12 && sample.hasSun() && angleBetween(direction, sample.sunDir()) < gap; k++) {
                    inclination += side * 0.07;
                    direction = ecliptic(e1, e2, normal, angle, inclination);
                }
            }
            items.add(new Item(other, direction, skyRadius, light, other.isMoon()));
        }

        items.sort(Comparator.comparingDouble((Item i) -> i.skyRadius()).reversed());
        if (items.size() > MAX_BODIES) items = new ArrayList<>(items.subList(0, MAX_BODIES));
        return new Plan(e1, e2, normal, items);
    }

    public record SkyBody(ContinuumBody body, float elevationDeg, float azimuthDeg, float apparentDeg, boolean moon) {}

    public static List<SkyBody> describe(PdimSkyView view0, ContinuumBody self, PdimSkyModel.Sample sample) {
        Plan plan = plan(view0, self, sample);
        List<SkyBody> out = new ArrayList<>();
        if (plan == null) return out;
        for (Item item : plan.items()) {
            Vector3f d = item.direction();
            float elevation = (float) Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, d.y))));
            float azimuth = (float) Math.toDegrees(Math.atan2(d.x, -d.z));
            if (azimuth < 0.0f) azimuth += 360.0f;
            float apparent = (float) Math.toDegrees(2.0 * Math.atan(item.skyRadius() / SKY_DISTANCE));
            out.add(new SkyBody(item.body(), elevation, azimuth, apparent, item.moon()));
        }
        return out;
    }

    public static double[] orbitPosition(ContinuumBody body, double days) {
        return positionAu(body, days);
    }

    static float eclipse(PdimSkyView view0, ContinuumBody self, PdimSkyModel.Sample sample) {
        if (!sample.hasSun()) return 0.0f;
        Plan plan = plan(view0, self, sample);
        if (plan == null) return 0.0f;
        float covered = 0.0f;
        for (Item item : plan.items()) {
            if (!item.moon()) continue;
            float bodyR = (float) Math.atan(item.skyRadius() / SKY_DISTANCE);
            float sep = (float) angleBetween(item.direction(), sample.sunDir());
            float reach = bodyR + SUN_ANGULAR_RADIUS;
            if (sep >= reach) continue;
            float small = Math.min(bodyR, SUN_ANGULAR_RADIUS);
            float overlap = clamp((reach - sep) / (2.0f * small), 0.0f, 1.0f);
            
            float area = Math.min(1.0f, (bodyR * bodyR) / (SUN_ANGULAR_RADIUS * SUN_ANGULAR_RADIUS));
            covered = Math.max(covered, overlap * area);
        }
        return covered;
    }

    static void draw(PdimSkyView view0, Matrix4f view, Matrix4f projection, ContinuumBody self,
                     PdimSkyModel.Sample sample, float time) {
        Plan plan = plan(view0, self, sample);
        if (plan == null) return;

        float unit = 40.0f;
        Vector3f center = new Vector3f(0.0f, -unit, 0.0f);
        if (!self.isMoon() && self.params().ring() != null) {
            SceneRenderer.drawSkyRing(self.params(), view, projection, center, unit, sample.sunDir(), plan.normal());
        }

        PlanetRenderer.Quality quality = ContinuumVisuals.quality();
        for (Item item : plan.items()) {
            var params = item.body().params();
            Vector3f pos = new Vector3f(item.direction()).mul(SKY_DISTANCE);
            Vector3f lightPos = new Vector3f(pos).add(item.light());
            PlanetRenderer.Quality q = item.skyRadius() < 4.0f ? PlanetRenderer.Quality.LOW : quality;
            SceneRenderer.drawPlanet(params, view, projection, pos, item.skyRadius(), lightPos,
                    time * params.spinDegPerSec() * 0.5f, time * params.cloudSpinDegPerSec() * 0.5f, q,
                    item.skyRadius() >= 4.0f);
        }

        drawComets(self, view, projection, plan, sample, time);

        com.mojang.blaze3d.systems.RenderSystem.clear(256, net.minecraft.client.Minecraft.ON_OSX);
    }

    private static void drawComets(ContinuumBody self, Matrix4f view, Matrix4f projection, Plan plan,
                                   PdimSkyModel.Sample sample, float time) {
        for (ContinuumBody comet : ContinuumData.bodiesOf(self.system())) {
            if (comet.transientInfo() == null || comet.params().style() == PlanetParams.STYLE_TIDAL) continue;
            long[] window = ContinuumClientState.transientWindow(comet.id());
            if (window == null) continue;
            float progress = clamp((ContinuumClientState.now() - window[0]) / (float) Math.max(1L, window[1] - window[0]),
                    0.0f, 1.0f);

            double seed = comet.params().seed();
            double angle = Math.PI * (0.18 + 0.64 * progress);
            Vector3f direction = ecliptic(plan.e1(), plan.e2(), plan.normal(), angle, 0.22 + 0.2 * Math.sin(seed * 5.1));
            Vector3f pos = new Vector3f(direction).mul(SKY_DISTANCE);

            if (comet.isRemnant()) {
                
                float grow = (float) Math.pow(progress, 0.55);
                float shell = SKY_DISTANCE * (0.03f + 0.28f * grow);
                SceneRenderer.drawRemnant(comet.params().mid(), progress, view, projection,
                        new Vector3f(plan.e1()).mul((float) Math.cos(seed * 2.3)).add(
                                new Vector3f(plan.e2()).mul((float) Math.abs(Math.sin(seed * 2.3)) + 0.2f))
                                .normalize().mul(SKY_DISTANCE),
                        shell / 0.72f * (float) Math.max(0.45, grow + 0.1), ContinuumVisuals.quality(), true);
                continue;
            }
            if (comet.isRogue()) {
                
                float size = 1.6f + 6.0f * (float) Math.sin(Math.PI * progress);
                SceneRenderer.drawPlanet(comet.params(), view, projection, pos, size,
                        new Vector3f(pos).add(new Vector3f(sample.sunDir()).mul(400.0f)), time * 2.0f, 0.0f,
                        ContinuumVisuals.quality(), size >= 4.0f);
                continue;
            }
            float radius = 1.1f;
            Vector3f away = new Vector3f(pos).sub(new Vector3f(sample.sunDir()).mul(98.0f)).normalize();

            SceneRenderer.drawPlanet(comet.params(), view, projection, pos, radius,
                    new Vector3f(pos).add(new Vector3f(sample.sunDir()).mul(400.0f)), time * 3.0f, 0.0f,
                    PlanetRenderer.Quality.LOW, false);
            SceneRenderer.drawCometTail(comet.params().mid(), view, projection, pos, radius, away,
                    0.35f + 0.65f * progress, 34.0f, ContinuumVisuals.quality(), true);
        }
    }

    private static double[] positionAu(ContinuumBody body, double days) {
        if (body.isCentral()) return new double[] {0.0, 0.0};
        double angle = 2.0 * Math.PI * (body.phase() + days / Math.max(0.05, body.periodDays()));
        return new double[] {Math.cos(angle) * body.orbitAu(), Math.sin(angle) * body.orbitAu()};
    }

    private static Vector3f ecliptic(Vector3f e1, Vector3f e2, Vector3f normal, double angle, double inclination) {
        Vector3f v = new Vector3f(e1).mul((float) Math.cos(angle))
                .add(new Vector3f(e2).mul((float) Math.sin(angle)))
                .add(new Vector3f(normal).mul((float) inclination));
        return v.normalize();
    }

    private static double angleBetween(Vector3f a, Vector3f b) {
        return Math.acos(Math.max(-1.0, Math.min(1.0, new Vector3f(a).normalize().dot(new Vector3f(b).normalize()))));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
