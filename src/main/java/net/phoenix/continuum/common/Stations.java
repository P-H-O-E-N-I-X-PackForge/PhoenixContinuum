package net.phoenix.continuum.common;

public final class Stations {

    private Stations() {}

    public static final int MAX_LEVEL = 3;

    public static int maxLevel(net.phoenix.continuum.data.ContinuumBody body) {
        return Swarms.maxLevel(body);
    }

    public static int probesPerLevel(net.phoenix.continuum.data.ContinuumBody body) {
        return Math.max(1, net.phoenix.continuum.config.ContinuumConfigs.INSTANCE.stationProbesPerLevel);
    }

    public static float tripFactor(int level) {
        return 1.0f - 0.10f * clamp(level);
    }

    public static float wearFactor(int level) {
        return 1.0f - 0.15f * clamp(level);
    }

    public static float yieldFactor(int level) {
        return 1.0f + 0.15f * clamp(level);
    }

    public static boolean preventsStranding(int level) {
        return level >= MAX_LEVEL;
    }

    public static String describe(int level) {
        return ContinuumLang.text(level >= MAX_LEVEL ? "phoenix_continuum.stations.describe_max" : "phoenix_continuum.stations.describe");
    }

    private static int clamp(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }
}
