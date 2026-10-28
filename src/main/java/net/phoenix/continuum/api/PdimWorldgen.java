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

/**
 * What a personal dimension (pdim) of a body is made of. By default it is void with an obsidian pad to stand on; a
 * pack or a mod can give any body real terrain, a vanilla noise world, or any chunk generator at all (another mod's
 * included, such as a Conflux discipline world with its structures and ores).
 *
 * <p>
 * Register at startup: from Java in a mod constructor or when {@link net.phoenix.continuum.api.PdimWorldgen.Collect}
 * is posted on the game bus; from KubeJS in a startup script with
 * {@code ContinuumEvents.pdimWorldgen(event => ...)}. The dimension files are written when the game builds its list of
 * data packs, so a spec added later only takes effect after a restart.
 *
 * <pre>{@code
 * PdimWorldgen.of(id).noise("minecraft:overworld", "minecraft:plains");
 * PdimWorldgen.of(id).flat("minecraft:plains", "minecraft:bedrock*1", "minecraft:stone*60", "minecraft:grass_block*1");
 * PdimWorldgen.of(id).generator("{\"type\":\"phoenixcore:discipline\", ...}").dimensionType("phoenixcore:discipline");
 * }</pre>
 */
public final class PdimWorldgen {

    private PdimWorldgen() {}

    /** Where a player is put on arriving in a dimension of this body. */
    public enum Arrival {
        /** Build the obsidian pad at the plot (the default for a void world). */
        PLATFORM,
        /** Stand on the ground at the plot, with no pad (the default for anything with terrain). */
        SURFACE,
        /** Stand at a fixed height at the plot (no pad). */
        FIXED
    }

    /** Posted on the game bus when the dimension files are about to be written: register specs here. */
    public static final class Collect extends net.neoforged.bus.api.Event {

        public Collect() {}
    }

    private static final Map<ResourceLocation, Spec> SPECS = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    /** The spec of a body, creating an empty (void) one if it has none. Edit it with the methods on {@link Spec}. */
    public static synchronized Spec of(ResourceLocation body) {
        return SPECS.computeIfAbsent(body, Spec::new);
    }

    /** As {@link #of(ResourceLocation)}, from text such as {@code "mymod:my_moon"}, for scripts. */
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

    /**
     * Configure every body the test matches that has no spec of its own (a spec made with {@link #of} wins). Rules are
     * applied in the order they were added, later ones over earlier ones. They are worked out when a dimension is made,
     * so they need Continuum's runtime dimensions (Infiniverse): the dimension files written at startup are for
     * named bodies only, since the bodies themselves are not loaded yet.
     */
    public static synchronized void where(Predicate<ContinuumBody> test, Consumer<Spec> configure) {
        RULES.add(new Rule(test, configure));
    }

    /** Every body of a type: "planet", "moon", "star" or "black_hole". */
    public static void forType(String type, Consumer<Spec> configure) {
        where(body -> body.type().name().equalsIgnoreCase(type), configure);
    }

    /** Every body in a system. */
    public static void forSystem(String system, Consumer<Spec> configure) {
        ResourceLocation id = ResourceLocation.parse(system.contains(":") ? system : "phoenix_continuum:" + system);
        where(body -> body.system().equals(id), configure);
    }

    /** Every body without a spec of its own. */
    public static void forAll(Consumer<Spec> configure) {
        where(body -> true, configure);
    }

    public static synchronized int ruleCount() {
        return RULES.size();
    }

    /**
     * The spec a body's dimension is made from: its own, or one built from the rules that match it, or plain void;
     * with {@link Spec#auto()} worked out from the body. {@code data} may be null (bodies not loaded yet): then only the
     * body's own spec counts and {@code auto} stays void.
     */
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

    /** Forget everything: every spec and rule. */
    public static synchronized void clear() {
        SPECS.clear();
        RULES.clear();
    }

    /** Forget a body's spec (it goes back to void). */
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
            return c;
        }

        /** A structure template placed at the plot when a player first makes this dimension, or null. */
        public @Nullable ResourceLocation plotStructure() {
            return plotStructure;
        }

        public boolean isAuto() {
            return auto;
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

        /** Whether this is still the plain void world. */
        public boolean isVoid() {
            return voidWorld;
        }

        /** Where players arrive: what was set, or the pad for a void world and the ground for anything else. */
        public Arrival arrival() {
            return arrival != null ? arrival : voidWorld ? Arrival.PLATFORM : Arrival.SURFACE;
        }

        // ---------------------------------------------------------------------------------------- what it is

        /**
         * Worked out from the body when its dimension is made: gas giants, stars and black holes stay void; rocky
         * worlds get terrain and a biome that follow their climate (distance from the star, ice, ocean).
         * Needs the bodies to be loaded, so it applies to dimensions made while the game runs.
         */
        public Spec auto() {
            this.auto = true;
            return this;
        }

        /** Place a structure template at the plot the first time a player makes this dimension. */
        public Spec plotStructure(ResourceLocation template) {
            this.plotStructure = template;
            return this;
        }

        public Spec plotStructure(String template) {
            return plotStructure(ResourceLocation.parse(template));
        }

        /** Empty space (the default). */
        public Spec voidWorld() {
            this.generator = voidGenerator();
            this.voidWorld = true;
            this.auto = false;
            return this;
        }

        /**
         * Layers of blocks from the bottom up, each {@code "block"} or {@code "block*height"}, over one biome.
         */
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

        /** A vanilla style noise world with a single biome. {@code settings} is a noise settings id. */
        public Spec noise(String settings, String biome) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "minecraft:fixed");
            source.addProperty("biome", biome);
            return noise(settings, source);
        }

        /** A noise world with a biome source of your own (a JSON object). */
        public Spec noise(String settings, JsonObject biomeSource) {
            JsonObject gen = new JsonObject();
            gen.addProperty("type", "minecraft:noise");
            gen.addProperty("settings", settings);
            gen.add("biome_source", biomeSource);
            return custom(gen);
        }

        /** The overworld's own terrain and biomes. */
        public Spec overworldLike() {
            JsonObject source = new JsonObject();
            source.addProperty("type", "minecraft:multi_noise");
            source.addProperty("preset", "minecraft:overworld");
            return noise("minecraft:overworld", source);
        }

        /** Any chunk generator, as a JSON object (another mod's too). Use {@link #dimensionType} if it needs its own. */
        public Spec generator(JsonObject json) {
            return custom(json.deepCopy());
        }

        /** As above, from JSON text. */
        public Spec generator(String json) {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("A generator is a JSON object");
            return custom(parsed.getAsJsonObject());
        }

        /** As above, from a map (a script object). */
        public Spec generator(Map<?, ?> map) {
            return custom(GSON.toJsonTree(map).getAsJsonObject());
        }

        private Spec custom(JsonObject gen) {
            this.generator = gen;
            this.voidWorld = false;
            this.auto = false;
            return this;
        }

        /** The dimension type: sky, light, height limits. The default is Continuum's own {@code phoenix_continuum:pdim}. */
        public Spec dimensionType(ResourceLocation id) {
            this.dimensionType = id;
            return this;
        }

        public Spec dimensionType(String id) {
            return dimensionType(ResourceLocation.parse(id));
        }

        // ---------------------------------------------------------------------------------------- arriving

        /** Stand on the ground at the plot, with no pad. */
        public Spec arriveOnSurface() {
            this.arrival = Arrival.SURFACE;
            return this;
        }

        /** Build the obsidian pad at the plot, whatever the terrain. */
        public Spec arriveOnPlatform() {
            this.arrival = Arrival.PLATFORM;
            return this;
        }

        /** Stand at this height at the plot, with no pad. */
        public Spec arriveAt(int y) {
            this.arrival = Arrival.FIXED;
            this.fixedY = y;
            return this;
        }

        /** The dimension file this spec writes. */
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
