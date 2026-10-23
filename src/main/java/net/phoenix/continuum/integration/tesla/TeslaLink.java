package net.phoenix.continuum.integration.tesla;

import net.neoforged.fml.ModList;

import java.util.UUID;

public final class TeslaLink {

    private static Boolean loaded;

    private TeslaLink() {}

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("phoenix_tesla_network");
        return loaded;
    }

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
