package net.phoenix.continuum.client.screen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.pdim.PdimDimensions;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ObservatoryReadings {

    private ObservatoryReadings() {}

    static String gravity(ContinuumBody body) {
        return String.format(Locale.ROOT, "x%.2f", PdimDimensions.naturalGravity(body));
    }

    static @Nullable String temperature(ContinuumBody body, ContinuumSystem system) {
        if (body.isCentral() || body.type() == ContinuumBody.Type.STAR || body.type() == ContinuumBody.Type.BLACK_HOLE) {
            return null;
        }
        ContinuumBody orbiting = body;
        if (body.isMoon() && body.parent() != null) {
            ContinuumBody parent = ContinuumData.body(body.parent());
            if (parent != null) orbiting = parent;
        }
        float au = Math.max(0.05f, orbiting.orbitAu());

        int c = system.starColor();
        float r = ((c >> 16) & 0xFF) / 255.0f;
        float b = (c & 0xFF) / 255.0f;
        double starTemp = 2500.0 + 5500.0 * Math.min(1.2, b / Math.max(0.05, r));
        double luminosity = system.starRadius() * system.starRadius() * Math.pow(starTemp / 5772.0, 4.0);
        double kelvin = 255.0 * Math.pow(luminosity, 0.25) / Math.sqrt(au) + 60.0 * body.params().atmoDensity();
        return String.format(Locale.ROOT, "%.0f K  (%.0f C)", kelvin, kelvin - 273.15);
    }

    static String atmosphere(ContinuumBody body) {
        float d = body.params().atmoDensity();
        if (d <= 0.0f) return "none";
        return d < 0.2f ? "thin" : d < 0.5f ? "moderate" : d < 0.8f ? "thick" : "crushing";
    }

    static List<Item> spectrum(ContinuumBody body) {
        List<Item> items = new ArrayList<>();
        for (ContinuumBody.Yield yield : body.yields()) {
            Item item = BuiltInRegistries.ITEM.get(yield.item());
            if (item != Items.AIR && !items.contains(item)) items.add(item);
            if (items.size() >= 7) break;
        }
        return items;
    }

    private static final double SUN_RADIUS = Math.atan(6.0 / 98.0);

    private static double[] position(ContinuumBody body, double days) {
        if (body.isCentral()) return new double[] {0.0, 0.0};
        double angle = 2.0 * Math.PI * (body.phase() + days / Math.max(0.05, body.periodDays()));
        return new double[] {Math.cos(angle) * body.orbitAu(), Math.sin(angle) * body.orbitAu()};
    }

    record Eclipse(double inDays, String moon, boolean total) {}

    static @Nullable Eclipse nextEclipse(ContinuumBody world, double nowDays, double horizonDays) {
        List<ContinuumBody> moons = ContinuumData.moonsOf(world.id());
        if (moons.isEmpty()) return null;

        Eclipse best = null;
        for (ContinuumBody moon : moons) {
            double period = Math.max(0.05, moon.periodDays());
            double seed = moon.params().seed();
            double skyRadius = Math.max(2.0, Math.min(15.0,
                    2.6 + 8.0 * Math.sqrt(moon.radius()) / (1.0 + 40.0 * moon.orbitAu())));
            double bodyR = Math.atan(skyRadius / 90.0);
            double reach = bodyR + SUN_RADIUS;
            double small = Math.min(bodyR, SUN_RADIUS);

            double step = Math.min(0.02, period / 200.0);
            for (double t = 0.0; t <= horizonDays; t += step) {
                double d = nowDays + t;
                double[] home = position(world, d);
                double sunBearing = Math.atan2(-home[1], -home[0]);
                double angle = 2.0 * Math.PI * (moon.phase() + d / period);
                double relative = Math.IEEEremainder(angle - sunBearing, 2.0 * Math.PI);
                double wobble = 0.15 * Math.sin(2.0 * Math.PI * d / (period * 11.0) + seed * 3.7);
                double separation = Math.hypot(relative, wobble);
                if (separation < reach) {
                    boolean total = separation + SUN_RADIUS <= bodyR + 1.0e-9 || (reach - separation) > 1.6 * small;
                    if (best == null || t < best.inDays()) best = new Eclipse(t, moon.name(), total);
                    break;
                }
            }
        }
        return best;
    }
}
