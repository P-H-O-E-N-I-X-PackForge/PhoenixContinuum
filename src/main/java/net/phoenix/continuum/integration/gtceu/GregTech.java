package net.phoenix.continuum.integration.gtceu;

import net.neoforged.fml.loading.FMLLoader;

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
