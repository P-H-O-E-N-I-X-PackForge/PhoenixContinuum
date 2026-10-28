package net.phoenix.continuum.integration.chronicles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.phoenix.continuum.api.ContinuumHost;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Continuum's way into Phoenix Chronicles, safe to call with Chronicles not installed (every call does nothing then).
 * Continuum fires a trigger id for what its players do, and any quest with an "External Trigger" task of that id counts it:
 *
 * <ul>
 * <li>{@code phoenix_continuum:discovery/detected}, {@code .../surveyed}: the team found or surveyed a body (data: subject)</li>
 * <li>{@code phoenix_continuum:mission/launched}, {@code .../succeeded}, {@code .../failed} (data: destination, type)</li>
 * <li>{@code phoenix_continuum:anomaly/investigated} (data: body, anomaly)</li>
 * <li>{@code phoenix_continuum:pdim/entered}, {@code .../created}, {@code .../left} (data: body)</li>
 * </ul>
 *
 * Addons can use {@link #registerTrigger} and {@link #fire} for their own ids. The Chronicles classes are only touched in
 * {@link ChroniclesBridgeImpl}, which is only loaded when the mod is there.
 */
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

    /** One player did the thing. Server side only. */
    public static void fire(@Nullable ServerPlayer player, String id, @Nullable CompoundTag data) {
        if (player != null && present()) ChroniclesBridgeImpl.fire(player, id, data);
    }

    /** Every online member of a Continuum team did the thing. */
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

    /** Called once at startup: lists Continuum's triggers in the quest editor. */
    public static void setup() {
        if (present()) ChroniclesBridgeImpl.setup();
    }
}
