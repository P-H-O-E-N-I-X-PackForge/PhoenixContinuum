package net.phoenix.continuum.common;

import net.minecraft.resources.ResourceLocation;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;

/**
 * Launch windows: where a world is on its orbit decides how good a time it is to go there. When it is near the side of
 * its star that faces home, the flight is shorter and easier on the hull; when it is on the far side, it takes longer.
 * Inner worlds come round quickly (hours of real time) and outer ones slowly (days), so there is always something worth
 * waiting for, or worth launching at straight away.
 *
 * <p>Orbital time is real time: one "orbital day" is {@code launchWindowDayMinutes} minutes. Within the home system the
 * origin is a world a year long at 1 AU (home itself), so a target is best placed when it is close to home; for other
 * systems the best place is the side of the star that faces the home system. The same maths runs on the server (to set
 * the flight time) and on the client (to show the window), so what the map says is what the launch does.
 */
public final class LaunchWindows {

    public static final ResourceLocation HOME_SYSTEM = PhoenixContinuum.id("home");

    /** Alignment at or above this counts as the window being open. */
    private static final double OPEN = 0.8;

    private LaunchWindows() {}

    /**
     * @param factor     what the flight time and the hull wear are multiplied by now (1 = no effect)
     * @param alignment  -1 (far side) to 1 (best placed)
     * @param label      prime / good / fair / poor
     * @param openNow    whether it is in the open window
     * @param changesInMillis time until it opens (if closed) or closes (if open); 0 if it never changes
     */
    public record Window(float factor, float alignment, String label, boolean openNow, long changesInMillis) {

        public static final Window NONE = new Window(1.0f, 0.0f, "", false, 0L);

        public boolean applies() {
            return !label.isEmpty();
        }
    }

    public static Window of(ContinuumBody body, long nowMillis) {
        var cfg = ContinuumConfigs.INSTANCE;
        if (cfg == null || body == null) return Window.NONE;
        double strength = cfg.launchWindowStrength;
        if (strength <= 0.0 || body.isCentral() || body.transientInfo() != null) return Window.NONE;
        ContinuumSystem system = ContinuumData.system(body.system());
        if (system == null) return Window.NONE;

        // moons share their world's place; work from the world they orbit
        ContinuumBody orbiting = body;
        if (body.isMoon() && body.parent() != null) {
            ContinuumBody parent = ContinuumData.body(body.parent());
            if (parent != null) orbiting = parent;
        }
        if (orbiting.periodDays() <= 0.0f) return Window.NONE;

        double dayMillis = Math.max(0.1, cfg.launchWindowDayMinutes) * 60_000.0;
        double days = nowMillis / dayMillis;
        double omega = 2.0 * Math.PI / orbiting.periodDays();               // radians per orbital day
        double angle = 2.0 * Math.PI * orbiting.phase() + omega * days;       // the target's place on its orbit

        // the angle that is best: towards home
        double best;
        double bestRate = 0.0;                                                // how fast the best place itself moves
        if (system.id().equals(HOME_SYSTEM)) {
            best = 2.0 * Math.PI * days / 365.0;                              // home goes round once a year
            bestRate = 2.0 * Math.PI / 365.0;
        } else {
            ContinuumSystem home = ContinuumData.system(HOME_SYSTEM);
            best = home == null ? 0.0 : Math.atan2(home.galaxyZ() - system.galaxyZ(), home.galaxyX() - system.galaxyX());
        }

        double relative = angle - best;                                       // 0 = best placed
        double alignment = Math.cos(relative);
        double factor = 1.0 + strength * (1.0 - (alignment + 1.0));         // 1 - s at best, 1 + s at worst
        String label = factor < 1.0 - 0.6 * strength ? "Prime" : factor < 1.0 - 0.15 * strength ? "Good" :
                factor < 1.0 + 0.5 * strength ? "Fair" : "Poor";

        // when does it next open / close? the relative angle advances at (omega - bestRate) per orbital day
        double rate = omega - bestRate;
        long changes = 0L;
        if (Math.abs(rate) > 1.0e-9) {
            double half = Math.acos(OPEN);                                    // the window is |relative| < half
            double wrapped = Math.IEEEremainder(relative, 2.0 * Math.PI);     // -pi..pi
            double radiansToChange;
            if (Math.abs(wrapped) < half) {
                radiansToChange = rate > 0 ? half - wrapped : wrapped + half; // until it leaves
            } else {
                radiansToChange = rate > 0 ? (wrapped > 0 ? 2.0 * Math.PI - wrapped - half : -wrapped - half) :
                        (wrapped < 0 ? wrapped + 2.0 * Math.PI - half : wrapped - half);   // until it arrives
            }
            changes = Math.max(0L, Math.round(radiansToChange / Math.abs(rate) * dayMillis));
        }
        return new Window((float) factor, (float) alignment, label, alignment >= OPEN, changes);
    }
}
