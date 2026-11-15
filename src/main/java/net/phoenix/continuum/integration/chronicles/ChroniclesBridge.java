package net.phoenix.continuum.integration.chronicles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.phoenix.continuum.api.ContinuumHost;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ChroniclesBridge {

    public static final String MOD_ID = "phoenix_chronicles";

    private ChroniclesBridge() {}

    private static Boolean present;

    public static boolean present() {
        if (present == null) present = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        return present;
    }

    public static void registerTrigger(String id, @Nullable String description) {
        if (present()) ChroniclesBridgeImpl.registerTrigger(id, description);
    }

    public static void fire(@Nullable ServerPlayer player, String id, @Nullable CompoundTag data) {
        if (player != null && present()) ChroniclesBridgeImpl.fire(player, id, data);
    }

    public static void fireTeam(MinecraftServer server, UUID team, String id, @Nullable CompoundTag data) {
        if (!present()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (team.equals(ContinuumHost.teamOf(player.getUUID()))) ChroniclesBridgeImpl.fire(player, id, data);
        }
    }

    public static CompoundTag data(String... keysAndValues) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) tag.putString(keysAndValues[i], keysAndValues[i + 1]);
        return tag;
    }

    public static void setup() {
        if (present()) ChroniclesBridgeImpl.setup();
    }
}
