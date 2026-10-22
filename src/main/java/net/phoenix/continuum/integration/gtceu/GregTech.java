package net.phoenix.continuum.integration.gtceu;

import net.neoforged.fml.loading.FMLLoader;

/** Whether GregTech is installed. Nothing outside the {@code integration.gtceu} package may touch GregTech classes unless this is true. */
public final class GregTech {

    private static Boolean loaded;

    private GregTech() {}

    public static boolean loaded() {
        if (loaded == null) {
            loaded = FMLLoader.getLoadingModList().getModFileById("gtceu") != null;
        }
        return loaded;
    }
}
