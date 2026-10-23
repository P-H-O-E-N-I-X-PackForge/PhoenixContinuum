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

public final class ContinuumAPI {

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

    public static UUID team(Object who) {
        if (who instanceof ServerPlayer player) return net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        if (who instanceof UUID uuid) return uuid;
        return UUID.fromString(String.valueOf(who));
    }

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

    public static boolean raise(Object who, String id, String stage) {
        DiscoveryStage target = DiscoveryStage.parse(stage, DiscoveryStage.UNKNOWN);
        boolean changed = ContinuumTeamData.get(server()).raise(team(who), id(id), target);
        if (changed) ContinuumServerEvents.onDataReloaded();
        return changed;
    }

    public static boolean grantResearch(Object who, String node) {
        return changeResearch(who, node, true);
    }

    public static boolean revokeResearch(Object who, String node) {
        return changeResearch(who, node, false);
    }

    private static boolean changeResearch(Object who, String node, boolean grant) {
        var server = server();
        UUID team = team(who);
        Object token = ContinuumHost.beforeResearch(server, team);
        var research = net.phoenix.continuum.common.BuiltinResearch.get(server);
        boolean changed = grant ? research.grant(team, id(node)) : research.revoke(team, id(node));
        ContinuumHost.afterResearch(server, team, token);
        return changed;
    }

    public static boolean hasResearch(Object who, String node) {
        return ContinuumHost.research().isUnlocked(team(who), id(node));
    }

    public static void setResearchFlag(Object who, String flag, boolean on) {
        var server = server();
        UUID team = team(who);
        Object token = ContinuumHost.beforeResearch(server, team);
        net.phoenix.continuum.common.BuiltinResearch.get(server).setFlag(team, flag, on);
        ContinuumHost.afterResearch(server, team, token);
    }

    public static void setDiscipline(Object who, String discipline) {
        var server = server();
        UUID team = team(who);
        Object token = ContinuumHost.beforeResearch(server, team);
        net.phoenix.continuum.common.BuiltinResearch.get(server).setDiscipline(team, discipline);
        ContinuumHost.afterResearch(server, team, token);
    }

    public static void setVisible(Object who, String id, boolean visible) {
        ContinuumTeamData.get(server()).setVisibilityOverride(team(who), id(id), visible);
        ContinuumServerEvents.onDataReloaded();
    }

    public static void clearOverride(Object who, String id) {
        ContinuumTeamData.get(server()).setVisibilityOverride(team(who), id(id), null);
        ContinuumServerEvents.onDataReloaded();
    }

    public static ContinuumReport reload() {
        return ContinuumData.reloadNow(server());
    }

    public static ContinuumReport report() {
        return ContinuumReport.last();
    }

    public static void registerCondition(String type, ScriptCondition condition) {
        ResourceLocation id = ResourceLocation.tryParse(type);
        if (id == null || !type.contains(":")) {
            throw new IllegalArgumentException("a condition type needs a namespace, like mypack:name");
        }
        ContinuumConditions.register(id, params -> context -> condition.test(params, context.server(),
                context.team()));
        ContinuumVisibility.clearCache();
    }

    public static void registerConditionType(ResourceLocation type,
                                         java.util.function.Function<JsonObject, ContinuumCondition> parser) {
        ContinuumConditions.register(type, parser);
        ContinuumVisibility.clearCache();
    }
}
