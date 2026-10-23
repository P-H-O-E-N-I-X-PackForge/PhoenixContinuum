package net.phoenix.continuum.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.data.DiscoveryStage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ResearchGrants {

    public static final String KEY = "grants_research";

    private ResearchGrants() {}

    public static void sweep(UUID team) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) sweep(server, team);
    }

    public static void sweep(MinecraftServer server, UUID team) {
        if (ContinuumHost.research() != BuiltinResearch.PROVIDER) return;
        BuiltinResearch research = BuiltinResearch.get(server);
        ContinuumTeamData data = ContinuumTeamData.get(server);

        List<ResourceLocation> fresh = new ArrayList<>();
        boolean any = false;
        boolean again = true;
        for (int round = 0; again && round < 8; round++) {
            again = false;
            for (ContinuumSystem system : ContinuumData.systems()) {
                again |= apply(research, team, data.effectiveStage(server, team, system.id()),
                        ContinuumData.rawSystem(system.id()), fresh);
            }
            for (ContinuumBody body : ContinuumData.allBodies()) {
                again |= apply(research, team, data.effectiveStage(server, team, body.id()),
                        ContinuumData.rawBody(body.id()), fresh);
            }
            any |= again;
        }
        if (any) ContinuumServerEvents.sendStateToTeam(server, team);
        if (!fresh.isEmpty() && net.phoenix.continuum.config.ContinuumConfigs.INSTANCE.announceResearch) {
            for (var player : server.getPlayerList().getPlayers()) {
                if (!ContinuumHost.teams().onTeam(player, team)) continue;
                for (ResourceLocation id : fresh) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "\u00a7bResearch unlocked: \u00a7f" + pretty(id)));
                }
            }
        }
    }

    static String pretty(ResourceLocation id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.getPath().substring(id.getPath().lastIndexOf('/') + 1).split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    private static boolean apply(BuiltinResearch research, UUID team, DiscoveryStage stage, @Nullable JsonObject raw,
                                 List<ResourceLocation> fresh) {
        if (raw == null || !raw.has(KEY)) return false;
        JsonElement grants = raw.get(KEY);
        boolean changed = false;
        if (grants.isJsonArray()) {
            if (stage.atLeast(DiscoveryStage.SURVEYED)) changed |= grantAll(research, team, grants.getAsJsonArray(), fresh);
        } else if (grants.isJsonObject()) {
            JsonObject object = grants.getAsJsonObject();
            if (stage.atLeast(DiscoveryStage.DETECTED) && object.get("detected") instanceof JsonArray list) {
                changed |= grantAll(research, team, list, fresh);
            }
            if (stage.atLeast(DiscoveryStage.SURVEYED) && object.get("surveyed") instanceof JsonArray list) {
                changed |= grantAll(research, team, list, fresh);
            }
        }
        return changed;
    }

    private static boolean grantAll(BuiltinResearch research, UUID team, JsonArray ids, List<ResourceLocation> fresh) {
        boolean changed = false;
        for (JsonElement element : ids) {
            if (!element.isJsonPrimitive()) continue;
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null && research.grant(team, id)) {
                changed = true;
                fresh.add(id);
            }
        }
        return changed;
    }
}
