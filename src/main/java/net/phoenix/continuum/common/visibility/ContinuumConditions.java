package net.phoenix.continuum.common.visibility;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.phoenix.continuum.common.ContinuumTeamData;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;
import net.phoenix.core.integration.conflux.research.WorldResearchData;
import net.phoenix.core.utils.TeamUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The condition types a rule can use, and the place other mods add their own:
 * {@code ContinuumConditions.register(id, json -> context -> ...)}.
 *
 * <p>Built in: {@code always}, {@code never}, {@code research} ({@code node}), {@code flag} ({@code name}),
 * {@code discipline} ({@code is}), {@code stage} ({@code body} or {@code system}, {@code at_least}), {@code visible}
 * ({@code body}), {@code advancement} ({@code id}, any online team member), and the combinators {@code all_of},
 * {@code any_of} and {@code not}. A bare array means all_of; {@code true} / {@code false} are always / never.
 */
public final class ContinuumConditions {

    private static final Map<ResourceLocation, Function<JsonObject, ContinuumCondition>> TYPES =
            new ConcurrentHashMap<>();

    private ContinuumConditions() {}

    public static void register(ResourceLocation type, Function<JsonObject, ContinuumCondition> parser) {
        TYPES.put(type, parser);
    }

    static {
        register(id("always"), json -> context -> true);
        register(id("never"), json -> context -> false);
        register(id("research"), json -> {
            ResourceLocation node = required(json, "node");
            return c -> WorldResearchData.get(c.server().overworld()).isUnlocked(c.team(), node);
        });
        register(id("flag"), json -> {
            String name = string(json, "name");
            return c -> WorldResearchData.get(c.server().overworld()).hasFlag(c.team(), name);
        });
        register(id("discipline"), json -> {
            String is = string(json, "is");
            return c -> {
                String chosen = WorldResearchData.get(c.server().overworld()).getDiscipline(c.team());
                return is.equals(chosen == null ? "none" : chosen);
            };
        });
        register(id("stage"), json -> {
            ResourceLocation subject = json.has("body") ? required(json, "body") : required(json, "system");
            DiscoveryStage least = DiscoveryStage.parse(json.has("at_least") ? string(json, "at_least") : "detected",
                    DiscoveryStage.DETECTED);
            return c -> ContinuumTeamData.get(c.server()).effectiveStage(c.server(), c.team(), subject)
                    .atLeast(least);
        });
        register(id("visible"), json -> {
            ResourceLocation body = required(json, "body");
            return c -> {
                var target = ContinuumData.body(body);
                return target != null && ContinuumVisibility.visible(c.server(), c.team(), target);
            };
        });
        register(id("advancement"), json -> {
            ResourceLocation advancement = required(json, "id");
            return c -> {
                for (ServerPlayer player : c.server().getPlayerList().getPlayers()) {
                    if (!c.team().equals(TeamUtils.getTeamIdOrPlayerFallback(player.getUUID()))) continue;
                    AdvancementHolder holder = c.server().getAdvancements().get(advancement);
                    if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) return true;
                }
                return false;
            };
        });
        register(id("all_of"), json -> all(parseList(json.get("conditions"))));
        register(id("any_of"), json -> {
            List<ContinuumCondition> list = parseList(json.get("conditions"));
            return c -> {
                for (ContinuumCondition condition : list) if (condition.test(c)) return true;
                return false;
            };
        });
        register(id("not"), json -> {
            ContinuumCondition inner = parse(json.get("condition"));
            return c -> !inner.test(c);
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("phoenix_continuum", path);
    }

    private static ContinuumCondition all(List<ContinuumCondition> list) {
        return c -> {
            for (ContinuumCondition condition : list) if (!condition.test(c)) return false;
            return true;
        };
    }

    private static ResourceLocation required(JsonObject json, String key) {
        ResourceLocation id = ResourceLocation.tryParse(string(json, key));
        if (id == null) throw new IllegalArgumentException("\"" + key + "\" is not a valid id");
        return id;
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing \"" + key + "\"");
        }
        return json.get(key).getAsString();
    }

    private static List<ContinuumCondition> parseList(JsonElement element) {
        List<ContinuumCondition> list = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("\"conditions\" must be a list");
        }
        for (JsonElement entry : (JsonArray) element) list.add(parse(entry));
        return list;
    }

    /** Throws IllegalArgumentException with a readable message if the rule is malformed. */
    public static ContinuumCondition parse(JsonElement element) {
        if (element == null || element.isJsonNull()) throw new IllegalArgumentException("missing condition");
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            boolean value = element.getAsBoolean();
            return c -> value;
        }
        if (element.isJsonArray()) return all(parseList(element));
        if (!element.isJsonObject()) throw new IllegalArgumentException("a condition is an object, list or boolean");

        JsonObject json = element.getAsJsonObject();
        // shorthand: { "all_of": [...] } / { "any_of": [...] } / { "not": {...} }
        for (String combinator : List.of("all_of", "any_of")) {
            if (json.has(combinator) && !json.has("type")) {
                JsonObject wrapped = new JsonObject();
                wrapped.add("conditions", json.get(combinator));
                return TYPES.get(id(combinator)).apply(wrapped);
            }
        }
        if (json.has("not") && !json.has("type")) {
            JsonObject wrapped = new JsonObject();
            wrapped.add("condition", json.get("not"));
            return TYPES.get(id("not")).apply(wrapped);
        }

        String typeName = string(json, "type");
        ResourceLocation type = ResourceLocation.tryParse(typeName.contains(":") ? typeName :
                "phoenix_continuum:" + typeName);
        Function<JsonObject, ContinuumCondition> parser = type == null ? null : TYPES.get(type);
        if (parser == null) throw new IllegalArgumentException("unknown condition type \"" + typeName + "\"");
        return parser.apply(json);
    }
}
