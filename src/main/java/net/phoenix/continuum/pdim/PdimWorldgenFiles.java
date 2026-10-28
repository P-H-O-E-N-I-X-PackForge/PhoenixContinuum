package net.phoenix.continuum.pdim;

import net.minecraft.resources.ResourceLocation;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.PdimWorldgen;
import net.phoenix.continuum.data.ContinuumBody;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Specs written as files, for packs without a script: {@code config/phoenix_continuum/pdim_worldgen/*.json}. A file is
 * one spec, for one body or, with {@code match}, for every body that fits:
 *
 * <pre>{@code
 * { "body": "phoenix_continuum:luna", "type": "flat", "biome": "minecraft:stony_peaks",
 *   "layers": ["minecraft:bedrock", "minecraft:stone*60", "minecraft:gravel"] }
 *
 * { "match": { "type": "moon" }, "type": "auto" }
 * }</pre>
 *
 * {@code type} is void, flat, noise (with {@code settings} and a {@code biome}), overworld_like, auto or generator (with a
 * {@code generator} object). Also optional: {@code dimension_type}, {@code arrival} (surface, platform or fixed, with
 * {@code y}) and {@code plot_structure}; and the world's rules: {@code mobs}, {@code fall_damage}, {@code oxygen} (the
 * helmet item or #tag needed), {@code weather} (clear, rain, storm, normal, or a cycle object), {@code plots} (false for an ordinary shared dimension), {@code spawn} ([x, y, z]), {@code plot_radius}, {@code protect_plots} and {@code gt_ores} (a dimension id).
 */
public final class PdimWorldgenFiles {

    private PdimWorldgenFiles() {}

    public static void load(Path dir) {
        try {
            Files.createDirectories(dir);
            Path readme = dir.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        One .json file per personal dimension spec. See PACKDEV.md in Continuum ("Personal dimension worldgen").

                        { "body": "phoenix_continuum:luna", "type": "flat", "biome": "minecraft:stony_peaks",
                          "layers": ["minecraft:bedrock", "minecraft:stone*60", "minecraft:gravel"] }

                        { "match": { "type": "moon" }, "type": "auto" }

                        type: void, flat, noise (settings + biome), overworld_like, auto, generator (a generator object)
                        optional: dimension_type, arrival (surface/platform/fixed + y), plot_structure
                        match: type (planet/moon/star/black_hole), system, ids (a list)  - needs Infiniverse
                        """);
            }
        } catch (IOException e) {
            PhoenixContinuum.LOGGER.warn("Could not prepare {}: {}", dir, e.getMessage());
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted()::iterator) {
                try {
                    apply(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                } catch (RuntimeException | IOException e) {
                    PhoenixContinuum.LOGGER.warn("Personal dimension worldgen file {} is not valid: {}",
                            file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            PhoenixContinuum.LOGGER.warn("Could not read {}: {}", dir, e.getMessage());
        }
    }

    /** One spec from its JSON. Public so scripts and tests can use the same format. */
    public static void apply(JsonObject json) {
        Consumer<PdimWorldgen.Spec> configure = spec -> configure(spec, json);
        if (json.has("body")) {
            configure.accept(PdimWorldgen.of(json.get("body").getAsString()));
        } else if (json.has("match")) {
            PdimWorldgen.where(matcher(json.getAsJsonObject("match")), configure);
        } else {
            throw new IllegalArgumentException("a spec needs a \"body\" or a \"match\"");
        }
    }

    private static Predicate<ContinuumBody> matcher(JsonObject match) {
        String type = match.has("type") ? match.get("type").getAsString() : null;
        ResourceLocation system = match.has("system") ? idOf(match.get("system").getAsString()) : null;
        List<ResourceLocation> ids = new ArrayList<>();
        if (match.has("ids")) {
            for (JsonElement each : match.getAsJsonArray("ids")) ids.add(idOf(each.getAsString()));
        }
        return body -> (type == null || body.type().name().equalsIgnoreCase(type)) &&
                (system == null || body.system().equals(system)) && (ids.isEmpty() || ids.contains(body.id()));
    }

    private static ResourceLocation idOf(String text) {
        return new ResourceLocation(text.contains(":") ? text : "phoenix_continuum:" + text);
    }

    private static void configure(PdimWorldgen.Spec spec, JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "void";
        switch (type) {
            case "void" -> spec.voidWorld();
            case "auto" -> spec.auto();
            case "overworld_like" -> spec.overworldLike();
            case "flat" -> {
                JsonArray layers = json.has("layers") ? json.getAsJsonArray("layers") : new JsonArray();
                String[] names = new String[layers.size()];
                for (int i = 0; i < names.length; i++) names[i] = layers.get(i).getAsString();
                spec.flat(json.has("biome") ? json.get("biome").getAsString() : "minecraft:plains", names);
            }
            case "noise" -> {
                String settings = json.has("settings") ? json.get("settings").getAsString() : "minecraft:overworld";
                if (json.has("biome_source")) spec.noise(settings, json.getAsJsonObject("biome_source"));
                else spec.noise(settings, json.has("biome") ? json.get("biome").getAsString() : "minecraft:plains");
            }
            case "generator" -> spec.generator(json.getAsJsonObject("generator"));
            default -> throw new IllegalArgumentException("unknown type " + type);
        }
        if (json.has("mobs")) spec.mobs(json.get("mobs").getAsBoolean());
        if (json.has("fall_damage")) spec.fallDamage(json.get("fall_damage").getAsBoolean());
        if (json.has("oxygen")) spec.requireOxygen(json.get("oxygen").getAsString());
        if (json.has("plot_radius")) spec.plotRadius(json.get("plot_radius").getAsInt());
        if (json.has("protect_plots")) spec.protectPlots(json.get("protect_plots").getAsBoolean());
        if (json.has("plots")) spec.plots(json.get("plots").getAsBoolean());
        if (json.has("spawn")) {
            JsonArray at = json.getAsJsonArray("spawn");
            spec.spawnAt(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt());
        }
        if (json.has("gt_ores")) spec.gtOres(json.get("gt_ores").getAsString());
        if (json.has("weather")) {
            JsonElement weather = json.get("weather");
            if (weather.isJsonObject()) {
                JsonObject cycle = weather.getAsJsonObject();
                spec.weatherCycle(cycle.has("clear_min") ? cycle.get("clear_min").getAsInt() : 600,
                        cycle.has("clear_max") ? cycle.get("clear_max").getAsInt() : 8400,
                        cycle.has("rain_min") ? cycle.get("rain_min").getAsInt() : 600,
                        cycle.has("rain_max") ? cycle.get("rain_max").getAsInt() : 1200,
                        cycle.has("storm_chance") ? cycle.get("storm_chance").getAsFloat() : 0.3f);
            } else {
                spec.weather(weather.getAsString());
            }
        }
        if (json.has("dimension_type")) spec.dimensionType(json.get("dimension_type").getAsString());
        if (json.has("plot_structure")) spec.plotStructure(json.get("plot_structure").getAsString());
        if (json.has("arrival")) {
            switch (json.get("arrival").getAsString()) {
                case "surface" -> spec.arriveOnSurface();
                case "platform" -> spec.arriveOnPlatform();
                case "fixed" -> spec.arriveAt(json.has("y") ? json.get("y").getAsInt() : 80);
                default -> throw new IllegalArgumentException("arrival is surface, platform or fixed");
            }
        }
    }
}
