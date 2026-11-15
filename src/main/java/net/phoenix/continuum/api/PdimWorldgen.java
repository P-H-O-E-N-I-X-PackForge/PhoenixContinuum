package net.phoenix.continuum.api;

import net.minecraft.resources.ResourceLocation;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.data.ContinuumBody;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PdimWorldgen {

    private PdimWorldgen() {}

    public enum Arrival {
        
        PLATFORM,
        
        SURFACE,
        
        FIXED
    }

    public static final class Collect extends net.neoforged.bus.api.Event {

        public Collect() {}
    }

    private static final Map<ResourceLocation, Spec> SPECS = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    public static synchronized Spec of(ResourceLocation body) {
        return SPECS.computeIfAbsent(body, Spec::new);
    }

    public static Spec of(String body) {
        return of(ResourceLocation.parse(body));
    }

    public static synchronized @Nullable Spec get(ResourceLocation body) {
        return SPECS.get(body);
    }

    public static synchronized Collection<Spec> all() {
        return java.util.List.copyOf(SPECS.values());
    }

    private record Rule(Predicate<ContinuumBody> test, Consumer<Spec> configure) {}

    private static final List<Rule> RULES = new ArrayList<>();

    public static synchronized void where(Predicate<ContinuumBody> test, Consumer<Spec> configure) {
        RULES.add(new Rule(test, configure));
    }

    public static void forType(String type, Consumer<Spec> configure) {
        where(body -> body.type().name().equalsIgnoreCase(type), configure);
    }

    public static void forSystem(String system, Consumer<Spec> configure) {
        ResourceLocation id = ResourceLocation.parse(system.contains(":") ? system : "phoenix_continuum:" + system);
        where(body -> body.system().equals(id), configure);
    }

    public static void forAll(Consumer<Spec> configure) {
        where(body -> true, configure);
    }

    public static synchronized int ruleCount() {
        return RULES.size();
    }

    public static synchronized Spec resolve(ResourceLocation body, @Nullable ContinuumBody data) {
        Spec own = SPECS.get(body);
        Spec spec = own != null ? own.copy() : new Spec(body);
        if (own == null && data != null) {
            for (Rule rule : RULES) {
                if (rule.test().test(data)) rule.configure().accept(spec);
            }
        }
        if (spec.auto && data != null) net.phoenix.continuum.pdim.PdimAuto.apply(spec, data);
        return spec;
    }

    public static synchronized void clearRules() {
        RULES.clear();
    }

    public static synchronized void clear() {
        SPECS.clear();
        RULES.clear();
    }

    public static synchronized void remove(ResourceLocation body) {
        SPECS.remove(body);
    }

    public static final class Spec {

        private final ResourceLocation body;
        private ResourceLocation dimensionType = PhoenixContinuum.id("pdim");
        private JsonObject generator = voidGenerator();
        private @Nullable Arrival arrival;
        private int fixedY = 80;
        private boolean voidWorld = true;
        private boolean auto;
        private @Nullable ResourceLocation plotStructure;
        private boolean mobs = true;
        private boolean fallDamage;
        private @Nullable String oxygenGear;
        private int plotRadius;
        private boolean protectPlots;
        private boolean plots = true;
        private @Nullable int[] spawn;
        private @Nullable ResourceLocation gtOresFrom;
        private net.phoenix.continuum.pdim.PdimWeather.Mode weather = net.phoenix.continuum.pdim.PdimWeather.Mode.NORMAL;
        private net.phoenix.continuum.pdim.PdimWeather.Cycle weatherCycle = net.phoenix.continuum.pdim.PdimWeather.Cycle.DEFAULT;

        Spec(ResourceLocation body) {
            this.body = body;
        }

        public ResourceLocation body() {
            return body;
        }

        Spec copy() {
            Spec c = new Spec(body);
            c.dimensionType = dimensionType;
            c.generator = generator.deepCopy();
            c.arrival = arrival;
            c.fixedY = fixedY;
            c.voidWorld = voidWorld;
            c.auto = auto;
            c.plotStructure = plotStructure;
            c.mobs = mobs;
            c.fallDamage = fallDamage;
            c.oxygenGear = oxygenGear;
            c.plotRadius = plotRadius;
            c.protectPlots = protectPlots;
            c.plots = plots;
            c.spawn = spawn == null ? null : spawn.clone();
            c.gtOresFrom = gtOresFrom;
            c.weather = weather;
            c.weatherCycle = weatherCycle;
            return c;
        }

        public @Nullable ResourceLocation plotStructure() {
            return plotStructure;
        }

        public boolean isAuto() {
            return auto;
        }

        public Spec mobs(boolean spawn) {
            this.mobs = spawn;
            return this;
        }

        public boolean mobs() {
            return mobs;
        }

        public Spec fallDamage(boolean hurts) {
            this.fallDamage = hurts;
            return this;
        }

        public boolean fallDamage() {
            return fallDamage;
        }

        public Spec requireOxygen(String gear) {
            this.oxygenGear = gear;
            return this;
        }

        public Spec breathable() {
            this.oxygenGear = null;
            return this;
        }

        public @Nullable String oxygenGear() {
            return oxygenGear;
        }

        public Spec plotRadius(int blocks) {
            this.plotRadius = Math.max(0, blocks);
            return this;
        }

        public int plotRadius() {
            return plotRadius;
        }

        public Spec plots(boolean on) {
            this.plots = on;
            return this;
        }

        public boolean plots() {
            return plots;
        }

        public Spec spawnAt(int x, int y, int z) {
            this.spawn = new int[] { x, y, z };
            return this;
        }

        public @Nullable int[] spawn() {
            return spawn == null ? null : spawn.clone();
        }

        public Spec protectPlots(boolean protect) {
            this.protectPlots = protect;
            return this;
        }

        public boolean protectPlots() {
            return protectPlots;
        }

        public Spec gtOres(String likeDimension) {
            this.gtOresFrom = ResourceLocation.parse(likeDimension);
            return this;
        }

        public @Nullable ResourceLocation gtOresFrom() {
            return gtOresFrom;
        }

        public Spec weather(String mode) {
            this.weather = net.phoenix.continuum.pdim.PdimWeather.Mode.of(mode);
            return this;
        }

        public net.phoenix.continuum.pdim.PdimWeather.Mode weather() {
            return weather;
        }

        public Spec weatherCycle(int clearMinSeconds, int clearMaxSeconds, int rainMinSeconds, int rainMaxSeconds,
                                 float stormChance) {
            this.weather = net.phoenix.continuum.pdim.PdimWeather.Mode.CYCLE;
            this.weatherCycle = new net.phoenix.continuum.pdim.PdimWeather.Cycle(clearMinSeconds * 20,
                    clearMaxSeconds * 20, rainMinSeconds * 20, rainMaxSeconds * 20,
                    Math.max(0.0f, Math.min(1.0f, stormChance)));
            return this;
        }

        public net.phoenix.continuum.pdim.PdimWeather.Cycle weatherCycle() {
            return weatherCycle;
        }

        public ResourceLocation dimensionType() {
            return dimensionType;
        }

        public JsonObject generator() {
            return generator.deepCopy();
        }

        public int fixedY() {
            return fixedY;
        }

        public boolean isVoid() {
            return voidWorld;
        }

        public Arrival arrival() {
            return arrival != null ? arrival : voidWorld ? Arrival.PLATFORM : Arrival.SURFACE;
        }

        public Spec auto() {
            this.auto = true;
            return this;
        }

        public Spec plotStructure(ResourceLocation template) {
            this.plotStructure = template;
            return this;
        }

        public Spec plotStructure(String template) {
            return plotStructure(ResourceLocation.parse(template));
        }

        public Spec voidWorld() {
            this.generator = voidGenerator();
            this.voidWorld = true;
            this.auto = false;
            return this;
        }

        public Spec flat(String biome, String... layers) {
            JsonArray array = new JsonArray();
            for (String layer : layers) {
                String block = layer;
                int height = 1;
                int star = layer.indexOf('*');
                if (star > 0) {
                    block = layer.substring(0, star);
                    height = Integer.parseInt(layer.substring(star + 1).trim());
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("block", block.trim());
                entry.addProperty("height", height);
                array.add(entry);
            }
            JsonObject settings = new JsonObject();
            settings.addProperty("biome", biome);
            settings.addProperty("features", true);
            settings.addProperty("lakes", false);
            settings.add("layers", array);
            settings.add("structure_overrides", new JsonArray());
            JsonObject gen = new JsonObject();
            gen.addProperty("type", "minecraft:flat");
            gen.add("settings", settings);
            return custom(gen);
        }

        public Spec noise(String settings, String biome) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "minecraft:fixed");
            source.addProperty("biome", biome);
            return noise(settings, source);
        }

        public Spec noise(String settings, Object biomeSource) {
            JsonObject gen = new JsonObject();
            gen.addProperty("type", "minecraft:noise");
            gen.addProperty("settings", settings);
            gen.add("biome_source", asObject(biomeSource));
            return custom(gen);
        }

        public Spec overworldLike() {
            JsonObject source = new JsonObject();
            source.addProperty("type", "minecraft:multi_noise");
            source.addProperty("preset", "minecraft:overworld");
            return noise("minecraft:overworld", source);
        }

        public Spec generator(Object json) {
            return custom(asObject(json));
        }

        private static JsonObject asObject(Object value) {
            JsonElement element;
            if (value instanceof JsonElement json) element = json.deepCopy();
            else if (value instanceof CharSequence text) element = JsonParser.parseString(text.toString());
            else if (value instanceof Map<?, ?> map) element = GSON.toJsonTree(map);
            else throw new IllegalArgumentException("Expected a JSON object, JSON text or an object, got " + value);
            if (!element.isJsonObject()) throw new IllegalArgumentException("Expected a JSON object");
            return element.getAsJsonObject();
        }

        private Spec custom(JsonObject gen) {
            this.generator = gen;
            this.voidWorld = false;
            this.auto = false;
            return this;
        }

        public Spec dimensionType(ResourceLocation id) {
            this.dimensionType = id;
            return this;
        }

        public Spec dimensionType(String id) {
            return dimensionType(ResourceLocation.parse(id));
        }

        public Spec arriveOnSurface() {
            this.arrival = Arrival.SURFACE;
            return this;
        }

        public Spec arriveOnPlatform() {
            this.arrival = Arrival.PLATFORM;
            return this;
        }

        public Spec arriveAt(int y) {
            this.arrival = Arrival.FIXED;
            this.fixedY = y;
            return this;
        }

        public JsonObject toDimensionJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", dimensionType.toString());
            json.add("generator", generator.deepCopy());
            return json;
        }
    }

    private static JsonObject voidGenerator() {
        JsonObject settings = new JsonObject();
        settings.addProperty("biome", "minecraft:the_void");
        settings.addProperty("features", false);
        settings.addProperty("lakes", false);
        settings.add("layers", new JsonArray());
        settings.add("structure_overrides", new JsonArray());
        JsonObject gen = new JsonObject();
        gen.addProperty("type", "minecraft:flat");
        gen.add("settings", settings);
        return gen;
    }
}
