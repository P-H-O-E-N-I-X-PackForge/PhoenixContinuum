package net.phoenix.continuum.integration.tesla;

import net.neoforged.fml.ModList;

import java.util.UUID;

/**
 * Swarm power straight into the Phoenix Tesla Network's energy storage, when that mod is installed. Everything that
 * touches its classes is in {@link TeslaImpl}, which is only loaded after the mod is known to be there.
 */
public final class TeslaLink {

    private static Boolean loaded;

    private TeslaLink() {}

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("phoenix_tesla_network");
        return loaded;
    }

    /** Puts up to this much into the team's tesla storage; returns how much it took. */
    public static long fill(UUID team, UUID owner, long amount) {
        if (!loaded() || amount <= 0) return 0;
        try {
            return TeslaImpl.fill(team, owner, amount);
        } catch (LinkageError e) {
            loaded = false;
            return 0;
        }
    }
}
