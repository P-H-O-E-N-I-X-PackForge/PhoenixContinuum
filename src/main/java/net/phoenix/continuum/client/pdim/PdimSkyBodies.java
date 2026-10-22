package net.phoenix.continuum.client.pdim;

import net.minecraft.client.multiplayer.ClientLevel;
import net.phoenix.continuum.client.ContinuumClientState;
import net.phoenix.continuum.client.ContinuumVisuals;
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

/**
 * The rest of a world's system as seen from standing on it: the other planets (rings and all) and, on a planet, its own
 * moons, each at the place its orbit puts it and lit from the side the star is on, so they have phases. This is what makes
 * a personal dimension feel like being on that planet instead of looking at it.
 *
 * <p>Positions come from the definitions (orbit radius, period and phase) and the game clock, in the same frame as the
 * sun: a body keeps the same angle from the sun in the ecliptic, and the whole sky turns once a day with the sun. Only
 * what the team has found is drawn, so the sky does not give away worlds nobody has detected yet. Sizes are exaggerated
 * so a distant planet is a visible disc rather than a speck, and only the biggest few are drawn so it stays cheap.
 */
final class PdimSkyBodies {

    private static final float SKY_DISTANCE = 90.0f;
    private static final int MAX_BODIES = 6;

    private PdimSkyBodies() {}

    private record Item(ContinuumBody body, Vector3f direction, float skyRadius, Vector3f light, boolean moon) {}

    /** Where everything is in the sky right now: the frame of the ecliptic and the worlds to draw, biggest first. */
    private record Plan(Vector3f normal, List<Item> items) {}

    /** The sun's apparent radius as drawn (see PdimSkyEffects: radius 6 at 98), in radians. */
    private static final float SUN_ANGULAR_RADIUS = (float) Math.atan(6.0 / 98.0);

    private static @org.jetbrains.annotations.Nullable Plan plan(ClientLevel level, float partialTick,
                                                                  ContinuumBody self, PdimSkyModel.Sample sample) {
        if (self == null || self.type() == ContinuumBody.Type.STAR || self.type() == ContinuumBody.Type.BLACK_HOLE) {
            return null;
        }

        float lat = PdimSkyModel.latitude(self);
        Vector3f e1 = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f e2 = new Vector3f(0.0f, (float) Math.cos(lat), (float) -Math.sin(lat));
        Vector3f normal = new Vector3f(0.0f, (float) Math.sin(lat), (float) Math.cos(lat));

        // where the sun is on its daily circle: the angle every other body is measured from
        boolean fixed = PdimClientState.fixedSky();
        double thetaSun = Math.atan2(sample.sunDir().dot(e2), sample.sunDir().dot(e1));
        double days = (level.getGameTime() + (double) partialTick) / 24000.0;

        ContinuumBody home = self.isMoon() && self.parent() != null ? ContinuumData.body(self.parent()) : self;
        if (home == null) home = self;
        double[] homePos = positionAu(home, days);
        double sunBearing = Math.atan2(-homePos[1], -homePos[0]);   // the way to the star, in the ecliptic

        List<Item> items = new ArrayList<>();
        for (ContinuumBody other : ContinuumData.bodiesOf(self.system())) {
            if (other.id().equals(self.id()) || other.id().equals(home.id()) || other.isCentral()) continue;
            if (other.transientInfo() != null) continue;
            if (ContinuumClientState.stage(other.id()) == DiscoveryStage.UNKNOWN) continue;

            double bearing;
            float skyRadius;
            Vector3f light;
            if (other.isMoon()) {
                // only the moons of the world you stand on (a sibling moon is close enough to matter too)
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
                // lit from the star's side, as that body sees it
                double toStar = Math.atan2(-p[1], -p[0]);
                light = ecliptic(e1, e2, normal, thetaSun + (toStar - sunBearing), 0.0).mul(400.0f);
            }
            double seed = other.params().seed();
            double angle = thetaSun + (bearing - sunBearing);
            if (fixed) {
                // pinned to the sky and always shown: spread along the upper arc in the order they orbit the star
                double frac = ((bearing - sunBearing) / (2.0 * Math.PI)) % 1.0;
                if (frac < 0.0) frac += 1.0;
                angle = Math.PI * (0.10 + 0.80 * frac);
            }

            Vector3f direction;
            if (other.isMoon() && !fixed) {
                // A moon's path wobbles above and below the sun's: for a stretch of its cycle it passes straight across the
                // star (an eclipse), then misses it for a while, the way eclipse seasons come and go.
                double wobble = 0.15 * Math.sin(2.0 * Math.PI * days / (Math.max(0.05, other.periodDays()) * 11.0) +
                        seed * 3.7);
                direction = ecliptic(e1, e2, normal, angle, wobble);
            } else {
                // Each world sits a little above or below the plane it orbits in, a different amount each, so they
                // spread across the sky instead of lining up on one arc with the star.
                double inclination = 0.32 * Math.sin(seed * 12.9898) + 0.12 * Math.sin(seed * 78.233 + other.orbitAu());
                direction = ecliptic(e1, e2, normal, angle, inclination);
                // never sit on the star, or a ringed world reads as the star having rings: slide it off to a clear gap
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
        return new Plan(normal, items);
    }

    /**
     * How much of the star a world in the sky is covering right now, 0 (none) to 1 (all of it). Only the moons of the
     * world you stand on ever cross the star. Used to dim the day, bring out the stars and draw the corona.
     */
    static float eclipse(ClientLevel level, float partialTick, ContinuumBody self, PdimSkyModel.Sample sample) {
        if (!sample.hasSun()) return 0.0f;
        Plan plan = plan(level, partialTick, self, sample);
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
            // a body smaller than the star leaves a ring of it showing (an annular eclipse) rather than covering it all
            float area = Math.min(1.0f, (bodyR * bodyR) / (SUN_ANGULAR_RADIUS * SUN_ANGULAR_RADIUS));
            covered = Math.max(covered, overlap * area);
        }
        return covered;
    }

    static void draw(ClientLevel level, float partialTick, Matrix4f view, Matrix4f projection, ContinuumBody self,
                     PdimSkyModel.Sample sample, float time) {
        Plan plan = plan(level, partialTick, self, sample);
        if (plan == null) return;

        // If the world has rings they arc across the sky. (The planet under your feet is not drawn, so nothing is hidden
        // below the horizon: a ring or a world that dips under it keeps going instead of being cut off.)
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

        // the horizon sphere only wrote depth to hide things behind it; the world is drawn after the sky, so remove it
        com.mojang.blaze3d.systems.RenderSystem.clear(256, net.minecraft.client.Minecraft.ON_OSX);
    }

    /** A body's place in the system plane, in AU, at the given game day. */
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
