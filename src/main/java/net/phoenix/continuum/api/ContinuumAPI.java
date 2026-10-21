package net.phoenix.continuum.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.phoenix.continuum.common.ContinuumServerEvents;
import net.phoenix.continuum.common.ContinuumTeamData;
import net.phoenix.continuum.common.visibility.ContinuumCondition;
import net.phoenix.continuum.common.visibility.ContinuumConditions;
import net.phoenix.continuum.common.visibility.ContinuumVisibility;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumReport;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.data.DiscoveryStage;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The scripting and mod-facing entry point for Continuum. Everything takes plain ids (strings) and a "team", which can
 * be a player (their team), a UUID, or a UUID string. It is bound as {@code Continuum} in KubeJS server scripts.
 */
public final class ContinuumAPI {

    /** A script-defined condition type; see {@link #registerCondition}. */
    @FunctionalInterface
    public interface ScriptCondition {

        boolean test(JsonObject params, MinecraftServer server, UUID team);
    }

    private ContinuumAPI() {}

    private static MinecraftServer server() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException("Continuum is only available while a server is running");
        return server;
    }

    private static ResourceLocation id(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text.contains(":") ? text : "phoenix_continuum:" + text);
        if (id == null) throw new IllegalArgumentException("not a valid id: " + text);
        return id;
    }

    /** A team from a player, a UUID or a UUID string. */
    public static UUID team(Object who) {
        if (who instanceof ServerPlayer player) return net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        if (who instanceof UUID uuid) return uuid;
        return UUID.fromString(String.valueOf(who));
    }

    // ------------------------------------------------------------------ reading

    public static List<ContinuumBody> bodies() {
        return new ArrayList<>(ContinuumData.allBodies());
    }

    public static List<ContinuumSystem> systems() {
        return new ArrayList<>(ContinuumData.systems());
    }

    public static @Nullable ContinuumBody body(String id) {
        return ContinuumData.body(id(id));
    }

    public static @Nullable ContinuumSystem system(String id) {
        return ContinuumData.system(id(id));
    }

    /** "unknown", "detected" or "surveyed" for a body or system, for that team. */
    public static String stage(Object who, String id) {
        return ContinuumTeamData.get(server()).effectiveStage(server(), team(who), id(id)).name().toLowerCase();
    }

    public static boolean isVisible(Object who, String bodyId) {
        ContinuumBody body = ContinuumData.body(id(bodyId));
        return body != null && ContinuumVisibility.visible(server(), team(who), body);
    }

    public static boolean isAccessible(Object who, String bodyId) {
        ContinuumBody body = ContinuumData.body(id(bodyId));
        return body != null && ContinuumVisibility.accessible(server(), team(who), body);
    }

    // ------------------------------------------------------------------ changing

    /** Raises a body or system to a stage (never lowers it). Returns true if it changed. */
    public static boolean raise(Object who, String id, String stage) {
        DiscoveryStage target = DiscoveryStage.parse(stage, DiscoveryStage.UNKNOWN);
        boolean changed = ContinuumTeamData.get(server()).raise(team(who), id(id), target);
        if (changed) ContinuumServerEvents.onDataReloaded();
        return changed;
    }

    /** Forces the per-team override (true = show, false = hide). Use {@link #clearOverride} to remove it. */
    public static void setVisible(Object who, String id, boolean visible) {
        ContinuumTeamData.get(server()).setVisibilityOverride(team(who), id(id), visible);
        ContinuumServerEvents.onDataReloaded();
    }

    public static void clearOverride(Object who, String id) {
        ContinuumTeamData.get(server()).setVisibilityOverride(team(who), id(id), null);
        ContinuumServerEvents.onDataReloaded();
    }

    /** Reloads Continuum data and re-sends it to everyone. */
    public static ContinuumReport reload() {
        return ContinuumData.reloadNow(server());
    }

    /** The last load report: errors and warnings. */
    public static ContinuumReport report() {
        return ContinuumReport.last();
    }

    // ------------------------------------------------------------------ extending

    /**
     * Adds a condition type usable in visibility rules, for example {@code {"type": "mypack:has_stage", "stage": 3}}.
     * The function receives the rule's JSON object, the server and the team.
     */
    public static void registerCondition(String type, ScriptCondition condition) {
        ResourceLocation id = ResourceLocation.tryParse(type);
        if (id == null || !type.contains(":")) {
            throw new IllegalArgumentException("a condition type needs a namespace, like mypack:name");
        }
        ContinuumConditions.register(id, params -> context -> condition.test(params, context.server(),
                context.team()));
        ContinuumVisibility.clearCache();
    }

    /** Java mods: same as {@link #registerCondition} with a typed condition. */
    public static void registerCondition(ResourceLocation type,
                                         java.util.function.Function<JsonObject, ContinuumCondition> parser) {
        ContinuumConditions.register(type, parser);
        ContinuumVisibility.clearCache();
    }
}
