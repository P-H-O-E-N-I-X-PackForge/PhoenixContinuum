package net.phoenix.continuum.client;

import net.phoenix.continuum.common.ContinuumLang;
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
            consider(goals, system.id(), ContinuumText.str("phoenix_continuum.continuum_goals.the_system", system.name()), ContinuumData.rawSystem(system.id()));
        }
        for (ContinuumBody body : ContinuumData.allBodies()) {
            consider(goals, body.id(), body.name(), ContinuumData.rawBody(body.id()));
        }

        List<Component> out = new ArrayList<>();
        out.add(ContinuumLang.tr("phoenix_continuum.continuum_goals.continuum_goals"));
        goals.sort((a, b) -> Boolean.compare(b.leadsOn(), a.leadsOn()));
        if (goals.isEmpty()) {
            out.add(ContinuumLang.tr("phoenix_continuum.continuum_goals.nothing_left_to_survey_try"));
        }
        for (Goal goal : goals) {
            out.add(Component.literal(goal.leadsOn() ? ContinuumText.str("phoenix_continuum.continuum_goals.this_will_open_something_new", goal.text()) : "\u00a77  - " + goal.text()));
        }
        return out;
    }

    private static void consider(List<Goal> goals, ResourceLocation id, String name, @Nullable JsonObject raw) {
        DiscoveryStage stage = ContinuumClientState.stage(id);
        if (stage == DiscoveryStage.UNKNOWN || stage == DiscoveryStage.SURVEYED) return;
        goals.add(new Goal(ContinuumText.str("phoenix_continuum.continuum_goals.survey_name", name), grantsOnSurvey(raw)));
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
