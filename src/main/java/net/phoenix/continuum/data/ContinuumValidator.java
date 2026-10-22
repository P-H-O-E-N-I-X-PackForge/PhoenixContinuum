package net.phoenix.continuum.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Checks Continuum definitions for the mistakes pack authors actually make, and says where they are. */
final class ContinuumValidator {

    /** Keys that mean something to the loader itself, valid on every definition. */
    private static final Set<String> META = Set.of("format", "inherits", "abstract", "disabled");

    private static final Set<String> BODY_KEYS = Set.of("system", "type", "name", "description", "orbit", "radius",
            "gas_giant", "axial_tilt", "spin_deg_per_sec", "cloud_spin_deg_per_sec", "surface", "atmosphere", "gate",
            "trip_minutes", "initial_stage", "yields", "lore", "parent", "rings", "style", "discipline", "pdim_cost",
            "transient", "hole_tilt", "sky_hole_inclination", "sky_hole_lean", "visibility", "grants_research", "anomalies");

    private static final Set<String> SYSTEM_KEYS = Set.of("name", "description", "galaxy", "star_color", "star_radius",
            "star_kind", "initial_stage", "gate", "hole", "visibility", "grants_research", "companion");

    private ContinuumValidator() {}

    static void checkShape(String kind, ResourceLocation id, JsonObject json, ContinuumReport report) {
        String where = kind + " " + id;

        if (json.has("format")) {
            JsonElement format = json.get("format");
            if (!format.isJsonPrimitive() || !format.getAsJsonPrimitive().isNumber()) {
                report.error(where, "\"format\" must be a whole number");
            } else if (format.getAsInt() > ContentResolver.SUPPORTED_FORMAT) {
                report.error(where, "written for format " + format.getAsInt() + " but this Continuum reads format " +
                        ContentResolver.SUPPORTED_FORMAT + "; update Continuum or lower the format");
            }
        }

        if (json.has("visibility")) {
            if (!json.get("visibility").isJsonObject()) {
                report.error(where, "\"visibility\" must be an object with visible_if / accessible_if");
            } else {
                JsonObject visibility = json.getAsJsonObject("visibility");
                for (String key : visibility.keySet()) {
                    if (!key.equals("visible_if") && !key.equals("accessible_if")) {
                        report.warn(where, "unknown visibility key \"" + key + "\" (ignored)");
                        continue;
                    }
                    try {
                        net.phoenix.continuum.common.visibility.ContinuumConditions.parse(visibility.get(key));
                    } catch (RuntimeException e) {
                        report.error(where, "visibility." + key + ": " + e.getMessage());
                    }
                }
            }
        }

        Set<String> known = kind.equals("body") ? BODY_KEYS : SYSTEM_KEYS;
        for (String key : json.keySet()) {
            if (!known.contains(key) && !META.contains(key)) {
                report.warn(where, "unknown key \"" + key + "\" (ignored)" + suggestion(key, known));
            }
        }
    }

    private static String suggestion(String key, Set<String> known) {
        String normal = key.replace("_", "").toLowerCase(Locale.ROOT);
        for (String candidate : known) {
            if (candidate.replace("_", "").equals(normal)) return "; did you mean \"" + candidate + "\"?";
        }
        return "";
    }

    static void checkReferences(Map<ResourceLocation, ContinuumSystem> systems,
                                Map<ResourceLocation, ContinuumBody> bodies, ContinuumReport report) {
        for (ContinuumBody body : bodies.values()) {
            String where = "body " + body.id();
            if (!systems.containsKey(body.system())) {
                report.error(where, "its system " + body.system() + " does not exist");
            }
            if (body.parent() != null) {
                ContinuumBody parent = bodies.get(body.parent());
                if (parent == null) {
                    report.error(where, "its parent body " + body.parent() + " does not exist");
                } else if (!parent.system().equals(body.system())) {
                    report.warn(where, "orbits " + parent.id() + ", which is in a different system");
                }
            }
            for (ContinuumBody.Yield yield : body.yields()) {
                if (!BuiltInRegistries.ITEM.containsKey(yield.item())) {
                    report.error(where, "yield item " + yield.item() + " is not a registered item");
                }
            }
            if (body.pdimCost() != null && !BuiltInRegistries.ITEM.containsKey(body.pdimCost().item())) {
                report.error(where, "pdim cost item " + body.pdimCost().item() + " is not a registered item");
            }
            if (body.orbitAu() < 0.0f) report.warn(where, "negative orbit radius");
        }
    }

    static void checkStage(String kind, ResourceLocation id, JsonObject json, ContinuumReport report) {
        if (!json.has("initial_stage") || !json.get("initial_stage").isJsonPrimitive()) return;
        String value = json.get("initial_stage").getAsString();
        for (DiscoveryStage stage : DiscoveryStage.values()) {
            if (stage.name().equalsIgnoreCase(value)) return;
        }
        report.warn(kind + " " + id, "initial_stage \"" + value + "\" is not unknown, detected or surveyed");
    }
}
