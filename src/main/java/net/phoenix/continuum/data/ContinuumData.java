package net.phoenix.continuum.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.phoenix.continuum.client.render.PlanetParams;
import net.phoenix.continuum.common.ContinuumServerEvents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.*;

public final class ContinuumData {

    private ContinuumData() {}

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String SYSTEM_DIR = "continuum/systems";
    private static final String BODY_DIR = "continuum/bodies";

    private static Map<ResourceLocation, ContinuumSystem> systems = Map.of();
    private static Map<ResourceLocation, ContinuumBody> bodies = Map.of();
    private static Map<ResourceLocation, String> rawSystems = Map.of();
    private static Map<ResourceLocation, String> rawBodies = Map.of();

    public record Snapshot(Map<ResourceLocation, String> systems, Map<ResourceLocation, String> bodies) {}

    public static void registerServerListener(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    public static Snapshot snapshot() {
        return new Snapshot(rawSystems, rawBodies);
    }

    private static int version;

    /** Goes up whenever the loaded systems and bodies change, so a screen that listed them can list them again. */
    public static int version() {
        return version;
    }

    /**
     * The body at the centre of a system is its star: when the system's star kind is changed (a star tuned into a black
     * hole), the body follows, so the map, the missions and the swarms see what the system shows. A black hole body in
     * a generated system goes back to a star the same way.
     */
    static void reconcileCentral(Map<ResourceLocation, ContinuumSystem> systems,
                                 Map<ResourceLocation, ContinuumBody> bodies) {
        for (Map.Entry<ResourceLocation, ContinuumBody> entry : bodies.entrySet()) {
            ContinuumBody body = entry.getValue();
            if (!body.isCentral()) continue;
            ContinuumSystem owner = systems.get(body.system());
            if (owner == null) continue;
            if (owner.hasHole() && body.type() == ContinuumBody.Type.STAR) {
                entry.setValue(body.withType(ContinuumBody.Type.BLACK_HOLE));
            } else if (!owner.hasHole() && body.type() == ContinuumBody.Type.BLACK_HOLE &&
                    body.id().getNamespace().equals(net.phoenix.continuum.data.SystemGenerator.NAMESPACE)) {
                entry.setValue(body.withType(ContinuumBody.Type.STAR));
            }
        }
    }

    public static void acceptSync(Snapshot snapshot) {
        version++;
        Map<ResourceLocation, ContinuumSystem> newSystems = new LinkedHashMap<>();
        Map<ResourceLocation, ContinuumBody> newBodies = new LinkedHashMap<>();
        snapshot.systems().forEach((id, json) -> parse(id, json, o -> newSystems.put(id, parseSystem(id, o))));
        snapshot.bodies().forEach((id, json) -> parse(id, json, o -> newBodies.put(id, parseBody(id, o))));

        reconcileCentral(newSystems, newBodies);
        systems = newSystems;
        bodies = newBodies;
        rawSystems = snapshot.systems();
        rawBodies = snapshot.bodies();
        ContinuumTuner.reapply();
    }

    public static @Nullable JsonObject rawBody(ResourceLocation id) {
        String json = rawBodies.get(id);
        return json == null ? null : JsonParser.parseString(json).getAsJsonObject();
    }

    public static @Nullable JsonObject rawSystem(ResourceLocation id) {
        String json = rawSystems.get(id);
        return json == null ? null : JsonParser.parseString(json).getAsJsonObject();
    }

    static void replaceBody(ResourceLocation id, ContinuumBody body) {
        Map<ResourceLocation, ContinuumBody> copy = new LinkedHashMap<>(bodies);
        copy.put(id, body);
        bodies = copy;
    }

    private static void parse(ResourceLocation id, String json, java.util.function.Consumer<JsonObject> consumer) {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element.isJsonObject()) consumer.accept(element.getAsJsonObject());
        } catch (Exception e) {
            LOGGER.error("[PhoenixCore/Continuum] Bad definition {}: {}", id, e.getMessage());
        }
    }

    public static Collection<ContinuumSystem> systems() {
        return systems.values();
    }

    public static @Nullable ContinuumSystem system(ResourceLocation id) {
        return systems.get(id);
    }

    public static @Nullable ContinuumBody body(ResourceLocation id) {
        return bodies.get(id);
    }

    public static Collection<ContinuumBody> allBodies() {
        return bodies.values();
    }

    public static List<ContinuumBody> bodiesOf(ResourceLocation system) {
        List<ContinuumBody> result = new ArrayList<>();
        for (ContinuumBody body : bodies.values()) {
            if (body.system().equals(system)) result.add(body);
        }
        result.sort(Comparator.comparing(ContinuumBody::isMoon).thenComparing(ContinuumBody::orbitAu)
                .thenComparing(b -> b.id().toString()));
        return result;
    }

    public static List<ContinuumBody> moonsOf(ResourceLocation parent) {
        List<ContinuumBody> result = new ArrayList<>();
        for (ContinuumBody body : bodies.values()) {
            if (parent.equals(body.parent())) result.add(body);
        }
        result.sort(Comparator.comparing(ContinuumBody::orbitAu));
        return result;
    }

    public static ContinuumSystem parseSystem(ResourceLocation id, JsonObject json) {
        float[] pos = new float[] { 0, 0, 0 };
        if (json.has("galaxy")) {
            var array = GsonHelper.getAsJsonArray(json, "galaxy");
            for (int i = 0; i < 3 && i < array.size(); i++) pos[i] = array.get(i).getAsFloat();
        }
        JsonObject gate = GsonHelper.getAsJsonObject(json, "gate", new JsonObject());
        return new ContinuumSystem(
                id,
                GsonHelper.getAsString(json, "name", id.getPath()),
                GsonHelper.getAsString(json, "description", ""),
                pos[0], pos[1], pos[2],
                color(json, "star_color", 0xffd9a0),
                GsonHelper.getAsFloat(json, "star_radius", 1.0f),
                GsonHelper.getAsString(json, "star_kind", "star"),
                DiscoveryStage.parse(GsonHelper.getAsString(json, "initial_stage", "unknown"),
                        DiscoveryStage.UNKNOWN),
                optionalId(gate, "detected_research"),
                optionalId(gate, "surveyed_research"),
                parseHole(GsonHelper.getAsJsonObject(json, "hole", new JsonObject())),
                json.has("visibility") && json.get("visibility").isJsonObject() ?
                        json.getAsJsonObject("visibility") : null,
                parseCompanion(json));
    }

    private static ContinuumSystem.Companion parseCompanion(JsonObject json) {
        if (!json.has("companion") || !json.get("companion").isJsonObject()) return null;
        JsonObject c = json.getAsJsonObject("companion");
        return new ContinuumSystem.Companion(GsonHelper.getAsString(c, "kind", "star"), color(c, "color", 0xffd9a0),
                Math.max(0.1f, GsonHelper.getAsFloat(c, "radius", 0.8f)),
                Math.max(1.5f, GsonHelper.getAsFloat(c, "separation", 8.0f)),
                Math.max(0.1f, Math.min(1.0f, GsonHelper.getAsFloat(c, "mass_ratio", 0.8f))),
                Math.max(2.0f, GsonHelper.getAsFloat(c, "period_seconds", 40.0f)));
    }

    private static ContinuumSystem.HoleStyle parseHole(JsonObject hole) {
        ContinuumSystem.HoleStyle d = ContinuumSystem.HoleStyle.DEFAULT;
        return new ContinuumSystem.HoleStyle(
                Math.max(0.2f, GsonHelper.getAsFloat(hole, "disk_size", d.diskSize())),
                Math.max(0.0f, GsonHelper.getAsFloat(hole, "disk_intensity", d.diskIntensity())),
                Math.max(0.0f, Math.min(1.0f, GsonHelper.getAsFloat(hole, "feeding", d.feeding()))),
                Math.max(0.0f, Math.min(1.0f, GsonHelper.getAsFloat(hole, "tilt", d.tilt()))),
                color(hole, "hot_color", -1),
                color(hole, "cool_color", -1),
                Math.max(2.2f, GsonHelper.getAsFloat(hole, "separation", d.separation())),
                Math.max(0.1f, Math.min(1.0f, GsonHelper.getAsFloat(hole, "mass_ratio", d.massRatio()))),
                Math.max(2.0f, GsonHelper.getAsFloat(hole, "period_seconds", d.periodSeconds())),
                hole.has("sky_inclination") ? GsonHelper.getAsFloat(hole, "sky_inclination") : Float.NaN,
                hole.has("sky_lean") ? GsonHelper.getAsFloat(hole, "sky_lean") : Float.NaN);
    }

    public static ContinuumBody parseBody(ResourceLocation id, JsonObject json) {
        JsonObject orbit = GsonHelper.getAsJsonObject(json, "orbit", new JsonObject());
        JsonObject surface = GsonHelper.getAsJsonObject(json, "surface", new JsonObject());
        JsonObject atmosphere = GsonHelper.getAsJsonObject(json, "atmosphere", new JsonObject());
        JsonObject gate = GsonHelper.getAsJsonObject(json, "gate", new JsonObject());

        String name = GsonHelper.getAsString(json, "name", id.getPath());
        String description = GsonHelper.getAsString(json, "description", "");

        ContinuumBody.Type type = ContinuumBody.Type.PLANET;
        try {
            type = ContinuumBody.Type.valueOf(GsonHelper.getAsString(json, "type", "planet").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {}

        PlanetParams.Ring ring = null;
        if (json.has("rings")) {
            JsonObject r = GsonHelper.getAsJsonObject(json, "rings");
            ring = new PlanetParams.Ring(GsonHelper.getAsFloat(r, "inner", 1.4f),
                    GsonHelper.getAsFloat(r, "outer", 2.4f),
                    color(r, "color", 0xd8c8a0), color(r, "color2", 0x8a7a60), GsonHelper.getAsFloat(r, "tilt", 0.0f),
                    GsonHelper.getAsFloat(r, "opacity", 0.85f));
        }
        PlanetParams params = new PlanetParams(
                name, description,
                GsonHelper.getAsBoolean(json, "gas_giant", false),
                GsonHelper.getAsFloat(surface, "seed", 1.0f),
                GsonHelper.getAsFloat(surface, "scale", 2.5f),
                GsonHelper.getAsFloat(surface, "ocean_level", 0.0f),
                GsonHelper.getAsFloat(surface, "polar_ice", 0.0f),
                GsonHelper.getAsFloat(surface, "cloud_cover", 0.0f),
                GsonHelper.getAsFloat(surface, "bump", 0.7f),
                color(surface, "low", 0x444444), color(surface, "mid", 0x888888), color(surface, "high", 0xcccccc),
                color(surface, "ocean_deep", 0x03123a), color(surface, "ocean_shallow", 0x1a6f8f),
                color(surface, "emissive", 0xff5a14), GsonHelper.getAsFloat(surface, "emissive_amount", 0.0f),
                color(atmosphere, "color", 0x6aa8ff), GsonHelper.getAsFloat(atmosphere, "density", 0.0f),
                GsonHelper.getAsFloat(json, "axial_tilt", 0.0f),
                GsonHelper.getAsFloat(json, "spin_deg_per_sec", 4.0f),
                GsonHelper.getAsFloat(json, "cloud_spin_deg_per_sec", 6.0f),
                PlanetParams.styleOf(GsonHelper.getAsString(json, "style", "")), ring);

        JsonObject lore = GsonHelper.getAsJsonObject(json, "lore", new JsonObject());
        return new ContinuumBody(
                id,
                new ResourceLocation(GsonHelper.getAsString(json, "system")),
                optionalId(json, "parent"),
                type, name, description,
                GsonHelper.getAsFloat(orbit, "radius_au", 1.0f),
                Math.max(GsonHelper.getAsFloat(orbit, "period_days", 100.0f), 0.01f),
                GsonHelper.getAsFloat(orbit, "phase", 0.0f),
                GsonHelper.getAsFloat(json, "radius", 1.0f),
                tripMinutes(json),
                GsonHelper.getAsString(gate, "min_tier", "start"),
                optionalId(gate, "detected_research"),
                optionalId(gate, "surveyed_research"),
                params,
                parseYields(json),
                DiscoveryStage.parse(GsonHelper.getAsString(json, "initial_stage", "unknown"),
                        DiscoveryStage.UNKNOWN),
                GsonHelper.getAsString(lore, "detected", ""),
                GsonHelper.getAsString(lore, "surveyed", ""),
                json.has("discipline") && !json.get("discipline").isJsonNull() ?
                        GsonHelper.getAsString(json, "discipline") : null,
                parsePdimCost(json), parseTransient(json),
                json.has("hole_tilt") ? Math.max(0.0f, Math.min(1.0f, GsonHelper.getAsFloat(json, "hole_tilt"))) :
                        null,
                json.has("sky_hole_inclination") ? GsonHelper.getAsFloat(json, "sky_hole_inclination") : null,
                json.has("sky_hole_lean") ? GsonHelper.getAsFloat(json, "sky_hole_lean") : null,
                json.has("visibility") && json.get("visibility").isJsonObject() ?
                        json.getAsJsonObject("visibility") : null,
                parseAnomalies(json));
    }

    /** Minutes, but a world can say "trip_seconds" instead and fly in seconds (never under one). */
    private static float tripMinutes(JsonObject json) {
        float minutes = json.has("trip_seconds") ? GsonHelper.getAsFloat(json, "trip_seconds") / 60.0f :
                GsonHelper.getAsFloat(json, "trip_minutes", 10.0f);
        return Math.max(1.0f / 60.0f, minutes);
    }

    private static List<ContinuumBody.Anomaly> parseAnomalies(JsonObject json) {
        List<ContinuumBody.Anomaly> list = new ArrayList<>();
        if (!json.has("anomalies") || !json.get("anomalies").isJsonArray()) return list;
        for (var element : json.getAsJsonArray("anomalies")) {
            if (!element.isJsonObject()) continue;
            JsonObject a = element.getAsJsonObject();
            String id = GsonHelper.getAsString(a, "id", "");
            if (id.isEmpty()) continue;
            JsonObject reward = GsonHelper.getAsJsonObject(a, "reward", new JsonObject());
            List<ContinuumBody.Yield> rewards = new ArrayList<>();
            if (reward.has("items") && reward.get("items").isJsonArray()) {
                for (var item : reward.getAsJsonArray("items")) {
                    if (!item.isJsonObject()) continue;
                    JsonObject i = item.getAsJsonObject();
                    ResourceLocation itemId = ResourceLocation.tryParse(GsonHelper.getAsString(i, "item", ""));
                    if (itemId == null) continue;
                    int min = Math.max(1, GsonHelper.getAsInt(i, "min", GsonHelper.getAsInt(i, "count", 1)));
                    int max = Math.max(min, GsonHelper.getAsInt(i, "max", min));
                    rewards.add(new ContinuumBody.Yield(itemId, min, max, GsonHelper.getAsFloat(i, "chance", 1.0f)));
                }
            }
            ContinuumBody.PdimCost cost = null;
            if (a.has("cost") && a.get("cost").isJsonObject()) {
                JsonObject c = a.getAsJsonObject("cost");
                ResourceLocation costItem = ResourceLocation.tryParse(GsonHelper.getAsString(c, "item", ""));
                if (costItem != null) cost = new ContinuumBody.PdimCost(costItem, Math.max(1, GsonHelper.getAsInt(c, "count", 1)));
            }
            list.add(new ContinuumBody.Anomaly(id, GsonHelper.getAsString(a, "name", id),
                    GsonHelper.getAsString(a, "description", ""),
                    DiscoveryStage.parse(GsonHelper.getAsString(a, "reveal", "surveyed"), DiscoveryStage.SURVEYED),
                    rewards, optionalId(reward, "research"), GsonHelper.getAsString(reward, "message", ""), cost));
        }
        return list;
    }

    private static @Nullable ContinuumBody.Transient parseTransient(JsonObject json) {
        if (!json.has("transient") || !json.get("transient").isJsonObject()) return null;
        JsonObject t = GsonHelper.getAsJsonObject(json, "transient");
        return new ContinuumBody.Transient(Math.max(1, GsonHelper.getAsInt(t, "duration_minutes", 360)),
                Math.max(0, GsonHelper.getAsInt(t, "cooldown_minutes", 720)),
                GsonHelper.getAsFloat(t, "chance", 0.3f), GsonHelper.getAsFloat(t, "inner_au", 0.4f),
                GsonHelper.getAsFloat(t, "outer_au", 4.0f));
    }

    private static @Nullable ContinuumBody.PdimCost parsePdimCost(JsonObject json) {
        if (!json.has("pdim_cost") || !json.get("pdim_cost").isJsonObject()) return null;
        JsonObject cost = GsonHelper.getAsJsonObject(json, "pdim_cost");
        ResourceLocation item = optionalId(cost, "item");
        return new ContinuumBody.PdimCost(
                item != null ? item : new ResourceLocation("minecraft", "air"),
                Math.max(0, GsonHelper.getAsInt(cost, "count", 0)));
    }

    private static List<ContinuumBody.Yield> parseYields(JsonObject json) {
        List<ContinuumBody.Yield> yields = new ArrayList<>();
        if (!json.has("yields")) return yields;

        for (JsonElement element : GsonHelper.getAsJsonArray(json, "yields")) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            int min = Math.max(1, GsonHelper.getAsInt(entry, "min", 1));
            int max = Math.max(min, GsonHelper.getAsInt(entry, "max", min));
            yields.add(new ContinuumBody.Yield(new ResourceLocation(GsonHelper.getAsString(entry, "item")), min, max,
                    Math.max(0.0f, Math.min(1.0f, GsonHelper.getAsFloat(entry, "chance", 1.0f)))));
        }
        return yields;
    }

    private static @Nullable ResourceLocation optionalId(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return null;
        return new ResourceLocation(GsonHelper.getAsString(json, key));
    }

    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        String text = GsonHelper.getAsString(json, key);
        try {
            return Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private record Parsed(Map<ResourceLocation, ContinuumSystem> systems, Map<ResourceLocation, ContinuumBody> bodies,
                          Map<ResourceLocation, String> rawSystems, Map<ResourceLocation, String> rawBodies) {}

    public static ContinuumReport reloadNow(net.minecraft.server.MinecraftServer server) {
        Loader loader = new Loader();
        Parsed parsed = loader.prepare(server.getResourceManager(),
                net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        loader.apply(parsed, server.getResourceManager(), net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        return ContinuumReport.last();
    }

    private static final class Loader extends SimplePreparableReloadListener<Parsed> {

        @Override
        protected Parsed prepare(ResourceManager manager, ProfilerFiller profiler) {
            ContinuumReport report = new ContinuumReport();

            Map<ResourceLocation, JsonObject> rawSystems = new LinkedHashMap<>();
            Map<ResourceLocation, JsonObject> rawBodies = new LinkedHashMap<>();
            read(manager, SYSTEM_DIR, rawSystems::put, report);
            read(manager, BODY_DIR, rawBodies::put, report);

            // This world's generated galaxy joins the files before anything is resolved: a body made in game can then
            // start from one of its worlds ("inherits"), or sit in one of its systems, like any other content.
            addGenerated(rawSystems, rawBodies, report);

            Map<ResourceLocation, JsonObject> systemJson = ContentResolver.resolve("system", rawSystems, report);
            Map<ResourceLocation, JsonObject> bodyJson = ContentResolver.resolve("body", rawBodies, report);
            applyDisabled(manager, systemJson, bodyJson, report);
            TuningPatches.applyAll(bodyJson, report);
            TuningPatches.applySystems(systemJson, report);

            Map<ResourceLocation, ContinuumSystem> loadedSystems = new LinkedHashMap<>();
            Map<ResourceLocation, ContinuumBody> loadedBodies = new LinkedHashMap<>();
            Map<ResourceLocation, String> loadedRawSystems = new LinkedHashMap<>();
            Map<ResourceLocation, String> loadedRawBodies = new LinkedHashMap<>();

            systemJson.forEach((id, json) -> {
                ContinuumValidator.checkShape("system", id, rawSystems.getOrDefault(id, json), report);
                ContinuumValidator.checkStage("system", id, json, report);
                try {
                    loadedSystems.put(id, parseSystem(id, json));
                    loadedRawSystems.put(id, json.toString());
                } catch (Exception e) {
                    report.error("system " + id, "could not be read: " + e.getMessage());
                }
            });
            bodyJson.forEach((id, json) -> {
                ContinuumValidator.checkShape("body", id, rawBodies.getOrDefault(id, json), report);
                ContinuumValidator.checkStage("body", id, json, report);
                try {
                    loadedBodies.put(id, parseBody(id, json));
                    loadedRawBodies.put(id, json.toString());
                } catch (Exception e) {
                    report.error("body " + id, "could not be read: " + e.getMessage());
                }
            });
            reconcileCentral(loadedSystems, loadedBodies);
            ContinuumValidator.checkReferences(loadedSystems, loadedBodies, report);
            ContinuumReport.publish(report);
            return new Parsed(loadedSystems, loadedBodies, loadedRawSystems, loadedRawBodies);
        }

        @Override
        protected void apply(Parsed parsed, ResourceManager manager, ProfilerFiller profiler) {
            systems = parsed.systems();
            bodies = parsed.bodies();
            rawSystems = parsed.rawSystems();
            rawBodies = parsed.rawBodies();
            version++;
            ContinuumReport report = ContinuumReport.last();
            net.phoenix.continuum.common.visibility.ContinuumVisibility.clearCache();
            LOGGER.info("[PhoenixCore/Continuum] Loaded {} systems and {} bodies ({} errors, {} warnings)",
                    systems.size(), bodies.size(), report.errors(), report.warnings());
            for (ContinuumReport.Issue issue : report.issues()) {
                if (issue.level() == ContinuumReport.Level.ERROR) LOGGER.error("[Continuum] {}", issue);
                else LOGGER.warn("[Continuum] {}", issue);
            }
            ContinuumServerEvents.onDataReloaded();
        }

        /**
         * Any pack can ship {@code data/<namespace>/continuum/disable.json} with {@code "systems"} and
         * {@code "bodies"} lists of ids to remove. Every copy in every pack is read, so it does not matter which
         * pack wins the load order (it is how a modpack drops the built-in example without overriding its files).
         * Removing a system removes its worlds too.
         */
        private static void applyDisabled(ResourceManager manager, Map<ResourceLocation, JsonObject> systemJson,
                                          Map<ResourceLocation, JsonObject> bodyJson, ContinuumReport report) {
            Set<ResourceLocation> systemIds = new HashSet<>();
            Set<ResourceLocation> bodyIds = new HashSet<>();
            for (String namespace : manager.getNamespaces()) {
                ResourceLocation file = new ResourceLocation(namespace, "continuum/disable.json");
                for (Resource resource : manager.getResourceStack(file)) {
                    try (Reader reader = resource.openAsReader()) {
                        JsonElement element = JsonParser.parseReader(reader);
                        if (!element.isJsonObject()) {
                            report.error(file.toString(), "must contain a JSON object");
                            continue;
                        }
                        collectIds(element.getAsJsonObject(), "systems", systemIds, file, report);
                        collectIds(element.getAsJsonObject(), "bodies", bodyIds, file, report);
                    } catch (Exception e) {
                        report.error(file.toString(), "invalid JSON: " + e.getMessage());
                    }
                }
            }
            if (systemIds.isEmpty() && bodyIds.isEmpty()) return;

            systemJson.keySet().removeAll(systemIds);
            bodyJson.keySet().removeAll(bodyIds);
            bodyJson.values().removeIf(body -> {
                ResourceLocation system = body.has("system") && body.get("system").isJsonPrimitive() ?
                        ResourceLocation.tryParse(body.get("system").getAsString()) : null;
                return system != null && systemIds.contains(system);
            });
        }

        /** This world's seeded galaxy, if it has one: generated again from its seed on every load. */
        private static void addGenerated(Map<ResourceLocation, JsonObject> systemJson,
                                         Map<ResourceLocation, JsonObject> bodyJson, ContinuumReport report) {
            try {
                ResourceLocation homeId = new ResourceLocation("phoenix_continuum", "home");
                // home may start from another system: resolve once to find where it is (problems are reported later)
                JsonObject homeJson = ContentResolver.resolve("system", new LinkedHashMap<>(systemJson),
                        new ContinuumReport()).get(homeId);
                ContinuumSystem home = homeJson == null ? null : parseSystem(homeId, homeJson);
                var generated = net.phoenix.continuum.common.GeneratedGalaxy.currentResult(home);
                if (generated == null) return;
                generated.systems().forEach(systemJson::putIfAbsent);
                generated.bodies().forEach(bodyJson::putIfAbsent);
            } catch (Exception e) {
                report.error("generated galaxy", "could not be added: " + e.getMessage());
            }
        }

        private static void collectIds(JsonObject json, String key, Set<ResourceLocation> into, ResourceLocation file,
                                       ContinuumReport report) {
            if (!json.has(key)) return;
            if (!json.get(key).isJsonArray()) {
                report.error(file.toString(), "\"" + key + "\" must be a list of ids");
                return;
            }
            for (JsonElement entry : json.getAsJsonArray(key)) {
                ResourceLocation id = entry.isJsonPrimitive() ? ResourceLocation.tryParse(entry.getAsString()) : null;
                if (id == null) report.error(file.toString(), "\"" + key + "\" has an entry that is not an id: " + entry);
                else into.add(id);
            }
        }

        private static void read(ResourceManager manager, String dir,
                                 java.util.function.BiConsumer<ResourceLocation, JsonObject> consumer,
                                 ContinuumReport report) {
            for (Map.Entry<ResourceLocation, Resource> entry : manager
                    .listResources(dir, path -> path.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                ResourceLocation id = new ResourceLocation(file.getNamespace(),
                        path.substring(dir.length() + 1, path.length() - ".json".length()));
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonObject()) consumer.accept(id, element.getAsJsonObject());
                    else report.error(file.toString(), "must contain a JSON object");
                } catch (Exception e) {
                    report.error(file.toString(), "invalid JSON: " + e.getMessage());
                }
            }
        }
    }
}
