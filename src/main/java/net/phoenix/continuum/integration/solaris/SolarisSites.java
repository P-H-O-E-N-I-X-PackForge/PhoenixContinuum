package net.phoenix.continuum.integration.solaris;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;

import java.util.UUID;

public final class SolarisSites {

    private SolarisSites() {}

    public static boolean present() {
        return ModList.get().isLoaded("solaris");
    }

    public static void init() {
        if (present()) SolarisSitesImpl.init();
    }

    public static void register(ServerLevel level, UUID team, BlockPos pos) {
        if (present()) SolarisSitesImpl.register(level, team, pos);
    }
}
