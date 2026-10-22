package net.phoenix.continuum.machine;

import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.integration.gtceu.ContinuumGt;
import net.phoenix.continuum.integration.gtceu.GregTech;

import java.util.Locale;

/**
 * Pad tiers and launch power in one place. With GregTech they are GregTech voltage tiers and EU, set by the Launch
 * Complex's energy hatches; without it they are the Launch Pad's own tiers (set by the foundation block under it) and
 * Forge Energy.
 */
public final class PadTiers {

    /** The names of the Launch Pad's tiers, from a bare pad to the best foundation. */
    private static final String[] BASIC_NAMES = { "Basic", "Reinforced", "Advanced", "Superior", "Apex" };

    public static final int BASIC_MAX_TIER = BASIC_NAMES.length - 1;

    private PadTiers() {}

    /** True when the plain Launch Pad is the launcher (no GregTech): tiers, power and recipes are Continuum's own. */
    public static boolean basic() {
        return !GregTech.loaded();
    }

    public static int required(ContinuumBody body) {
        var cfg = ContinuumConfigs.INSTANCE;
        String gate = body.minTier().toLowerCase(Locale.ROOT);
        if (basic()) {
            return switch (gate) {
                case "early" -> 1;
                case "mid" -> 2;
                case "late" -> 3;
                case "endgame" -> 4;
                default -> 0;
            };
        }
        return switch (gate) {
            case "early" -> cfg.padTierEarly;
            case "mid" -> cfg.padTierMid;
            case "late" -> cfg.padTierLate;
            case "endgame" -> cfg.padTierEndgame;
            default -> cfg.padTierStart;
        };
    }

    public static String name(int tier) {
        if (basic()) return BASIC_NAMES[Math.max(0, Math.min(tier, BASIC_MAX_TIER))];
        return ContinuumGt.tierName(tier);
    }

    /** The energy a launch from a pad of this tier draws. */
    public static long launchEnergy(int tier) {
        var cfg = ContinuumConfigs.INSTANCE;
        if (basic()) return (long) cfg.basicLaunchEnergyFE * (1 + Math.max(0, tier));
        return ContinuumGt.voltage(tier) * cfg.launchEnergyAmpTicks;
    }

    public static String energyUnit() {
        return basic() ? "FE" : "EU";
    }
}
