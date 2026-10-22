package net.phoenix.continuum.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

public final class ContinuumGoals {

    private ContinuumGoals() {}

    private record Goal(String text, boolean leadsOn) {}

    public static List<Component> lines() {
        List<Goal> goals = new ArrayList<>();
        for (ContinuumSystem system : ContinuumData.systems()) {
            consider(goals, system.id(), "the " + system.name() + " system", ContinuumData.rawSystem(system.id()));
        }
        for (ContinuumBody body : ContinuumData.allBodies()) {
            consider(goals, body.id(), body.name(), ContinuumData.rawBody(body.id()));
        }

        List<Component> out = new ArrayList<>();
        out.add(Component.literal("\u00a76Continuum goals"));
        goals.sort((a, b) -> Boolean.compare(b.leadsOn(), a.leadsOn()));
        if (goals.isEmpty()) {
            out.add(Component.literal("\u00a77  Nothing left to survey. Try outposts, hauling and personal dimensions."));
        }
        for (Goal goal : goals) {
            out.add(Component.literal(goal.leadsOn() ? "\u00a7e  * " + goal.text() + " \u00a77- this will open something new" :
                    "\u00a77  - " + goal.text()));
        }
        return out;
    }

    private static void consider(List<Goal> goals, ResourceLocation id, String name, @Nullable JsonObject raw) {
        DiscoveryStage stage = ContinuumClientState.stage(id);
        if (stage == DiscoveryStage.UNKNOWN || stage == DiscoveryStage.SURVEYED) return;
        goals.add(new Goal("Survey " + name, grantsOnSurvey(raw)));
    }

    private static boolean grantsOnSurvey(@Nullable JsonObject raw) {
        if (raw == null || !raw.has("grants_research")) return false;
        JsonElement grants = raw.get("grants_research");
        if (grants.isJsonArray()) return !grants.getAsJsonArray().isEmpty();
        if (grants.isJsonObject()) {
            JsonObject object = grants.getAsJsonObject();
            return object.get("surveyed") instanceof JsonArray list && !list.isEmpty();
        }
        return false;
    }
}
