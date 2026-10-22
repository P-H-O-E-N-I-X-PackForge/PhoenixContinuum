package net.phoenix.continuum.common;

import net.minecraft.resources.ResourceLocation;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;

public final class LaunchWindows {

    public static final ResourceLocation HOME_SYSTEM = PhoenixContinuum.id("home");

    private static final double OPEN = 0.8;

    private LaunchWindows() {}

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

        ContinuumBody orbiting = body;
        if (body.isMoon() && body.parent() != null) {
            ContinuumBody parent = ContinuumData.body(body.parent());
            if (parent != null) orbiting = parent;
        }
        if (orbiting.periodDays() <= 0.0f) return Window.NONE;

        double dayMillis = Math.max(0.1, cfg.launchWindowDayMinutes) * 60_000.0;
        double days = nowMillis / dayMillis;
        double omega = 2.0 * Math.PI / orbiting.periodDays();               
        double angle = 2.0 * Math.PI * orbiting.phase() + omega * days;       

        double best;
        double bestRate = 0.0;                                                
        if (system.id().equals(HOME_SYSTEM)) {
            best = 2.0 * Math.PI * days / 365.0;                              
            bestRate = 2.0 * Math.PI / 365.0;
        } else {
            ContinuumSystem home = ContinuumData.system(HOME_SYSTEM);
            best = home == null ? 0.0 : Math.atan2(home.galaxyZ() - system.galaxyZ(), home.galaxyX() - system.galaxyX());
        }

        // keep the speed at which the world drifts into and out of line within real cycle times: a year close to
        // home's would otherwise take days to come round, and a very fast one would open and shut in seconds
        double naturalRate = omega - bestRate;
        double slowest = 2.0 * Math.PI / Math.max(1.0, cfg.launchWindowMaxCycleMinutes * 60_000.0 / dayMillis);
        double fastest = 2.0 * Math.PI / Math.max(1.0, cfg.launchWindowMinCycleMinutes * 60_000.0 / dayMillis);
        double driftRate = Math.copySign(Math.max(slowest, Math.min(fastest, Math.abs(naturalRate))),
                naturalRate == 0.0 ? 1.0 : naturalRate);
        double relative = 2.0 * Math.PI * orbiting.phase() - (system.id().equals(HOME_SYSTEM) ? 0.0 : best) +
                driftRate * days;                                       
        double alignment = Math.cos(relative);
        double factor = 1.0 + strength * (1.0 - (alignment + 1.0));         
        String label = factor < 1.0 - 0.6 * strength ? "Prime" : factor < 1.0 - 0.15 * strength ? "Good" :
                factor < 1.0 + 0.5 * strength ? "Fair" : "Poor";

        double rate = driftRate;
        long changes = 0L;
        if (Math.abs(rate) > 1.0e-9) {
            double half = Math.acos(OPEN);                                    
            double wrapped = Math.IEEEremainder(relative, 2.0 * Math.PI);     
            double radiansToChange;
            if (Math.abs(wrapped) < half) {
                radiansToChange = rate > 0 ? half - wrapped : wrapped + half; 
            } else {
                radiansToChange = rate > 0 ? (wrapped > 0 ? 2.0 * Math.PI - wrapped - half : -wrapped - half) :
                        (wrapped < 0 ? wrapped + 2.0 * Math.PI - half : wrapped - half);   
            }
            changes = Math.max(0L, Math.round(radiansToChange / Math.abs(rate) * dayMillis));
        }
        return new Window((float) factor, (float) alignment, label, alignment >= OPEN, changes);
    }
}
