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

import java.util.UUID;

/**
 * The built-in way to earn research: a body or system can name research ids it gives a team once the team reaches a stage
 * of it, for example {@code "grants_research": {"detected": ["mypack:sensors"], "surveyed": ["mypack:landing"]}}. A plain list
 * means "when surveyed". Research gates elsewhere then open as it is granted, which can grant more, so a sweep repeats until
 * nothing new is given. Only runs while Continuum's own research is the one in use.
 */
public final class ResearchGrants {

    public static final String KEY = "grants_research";

    private ResearchGrants() {}

    /** Sweeps for the team on the running server, if there is one. */
    public static void sweep(UUID team) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) sweep(server, team);
    }

    public static void sweep(MinecraftServer server, UUID team) {
        if (ContinuumHost.research() != BuiltinResearch.PROVIDER) return;
        BuiltinResearch research = BuiltinResearch.get(server);
        ContinuumTeamData data = ContinuumTeamData.get(server);

        boolean any = false;
        boolean again = true;
        for (int round = 0; again && round < 8; round++) {
            again = false;
            for (ContinuumSystem system : ContinuumData.systems()) {
                again |= apply(research, team, data.effectiveStage(server, team, system.id()),
                        ContinuumData.rawSystem(system.id()));
            }
            for (ContinuumBody body : ContinuumData.allBodies()) {
                again |= apply(research, team, data.effectiveStage(server, team, body.id()),
                        ContinuumData.rawBody(body.id()));
            }
            any |= again;
        }
        if (any) ContinuumServerEvents.sendStateToTeam(server, team);
    }

    private static boolean apply(BuiltinResearch research, UUID team, DiscoveryStage stage, @Nullable JsonObject raw) {
        if (raw == null || !raw.has(KEY)) return false;
        JsonElement grants = raw.get(KEY);
        boolean changed = false;
        if (grants.isJsonArray()) {
            if (stage.atLeast(DiscoveryStage.SURVEYED)) changed |= grantAll(research, team, grants.getAsJsonArray());
        } else if (grants.isJsonObject()) {
            JsonObject object = grants.getAsJsonObject();
            if (stage.atLeast(DiscoveryStage.DETECTED) && object.get("detected") instanceof JsonArray list) {
                changed |= grantAll(research, team, list);
            }
            if (stage.atLeast(DiscoveryStage.SURVEYED) && object.get("surveyed") instanceof JsonArray list) {
                changed |= grantAll(research, team, list);
            }
        }
        return changed;
    }

    private static boolean grantAll(BuiltinResearch research, UUID team, JsonArray ids) {
        boolean changed = false;
        for (JsonElement element : ids) {
            if (!element.isJsonPrimitive()) continue;
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null && research.grant(team, id)) changed = true;
        }
        return changed;
    }
}
