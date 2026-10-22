package net.phoenix.continuum.data;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Builds a set of star systems and their worlds from a seed, as the same JSON a pack author would write. Nothing here
 * touches the game: the same seed and count always give the same galaxy, which is what lets a server tie its galaxy to
 * its world seed.
 */
public final class SystemGenerator {

    public static final String NAMESPACE = "phoenix_continuum_gen";

    public record Result(Map<ResourceLocation, JsonObject> systems, Map<ResourceLocation, JsonObject> bodies) {}

    private record Star(String kind, int color, float radius, float luminosity, int maxPlanets, JsonObject extra) {

        boolean exotic() {
            return switch (kind) {
                case "pulsar", "wolf_rayet", "black_hole", "protostar", "planetary_nebula" -> true;
                default -> false;
            };
        }
    }

    private static final float MIN_SPACING = 3.2f;
    private static final float DISC_INNER = 5.0f;
    private static final float DISC_OUTER = 16.5f;

    private static final String[] START = { "Al", "Ber", "Cal", "Dor", "El", "Fen", "Gal", "Hal", "Ir", "Kel", "Lor",
            "Mar", "Nor", "Or", "Pel", "Quin", "Ros", "Sar", "Tal", "Ul", "Vel", "Wyn", "Xan", "Yor", "Zer", "Ae",
            "Bra", "Cyr", "Dra", "Eri" };
    private static final String[] MIDDLE = { "a", "e", "i", "o", "u", "ar", "en", "il", "or", "ul", "an", "eth", "ia",
            "ou", "ae" };
    private static final String[] END = { "n", "s", "x", "th", "ra", "lis", "dor", "ven", "mir", "tis", "or", "ia",
            "ax", "on", "us", "eth" };

    private SystemGenerator() {}

    public static Result generate(long seed, int count, ContinuumSystemView home) {
        Map<ResourceLocation, JsonObject> systems = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> bodies = new LinkedHashMap<>();
        List<float[]> placed = new ArrayList<>();
        placed.add(new float[] { home.x(), home.y(), home.z() });
        List<String> usedNames = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            Random rng = new Random(mix(seed, index));
            String name = uniqueName(rng, usedNames);
            usedNames.add(name);
            String slug = "gen_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") + "_" + index;
            ResourceLocation systemId = new ResourceLocation(NAMESPACE, slug);

            float[] position = position(rng, placed);
            placed.add(position);
            Star star = star(rng);

            JsonObject system = new JsonObject();
            system.addProperty("format", 1);
            system.addProperty("name", name);
            system.addProperty("description", describe(star, rng));
            JsonArray galaxy = new JsonArray();
            for (float v : position) galaxy.add(round(v, 2));
            system.add("galaxy", galaxy);
            system.addProperty("star_color", hex(star.color()));
            system.addProperty("star_radius", round(star.radius(), 2));
            system.addProperty("star_kind", star.kind());
            system.addProperty("initial_stage", "unknown");
            star.extra().entrySet().forEach(e -> system.add(e.getKey(), e.getValue()));
            systems.put(systemId, system);

            float distance = (float) Math.sqrt(position[0] * position[0] + position[1] * position[1] +
                    position[2] * position[2]);
            generateBodies(rng, systemId, slug, name, star, distance, bodies);
        }
        return new Result(systems, bodies);
    }

    /** Where the home system is, so nothing generated lands on top of it. */
    public record ContinuumSystemView(float x, float y, float z) {

        public static final ContinuumSystemView ORIGIN = new ContinuumSystemView(0.0f, 0.0f, 0.0f);
    }

    private static long mix(long seed, int index) {
        long z = seed + 0x9E3779B97F4A7C15L * (index + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static String uniqueName(Random rng, List<String> used) {
        for (int attempt = 0; attempt < 40; attempt++) {
            StringBuilder sb = new StringBuilder(pick(rng, START));
            if (rng.nextInt(3) > 0) sb.append(pick(rng, MIDDLE));
            sb.append(pick(rng, END));
            String name = sb.toString();
            if (!used.contains(name)) return name;
        }
        return pick(rng, START) + pick(rng, END) + (used.size() + 1);
    }

    private static <T> T pick(Random rng, T[] values) {
        return values[rng.nextInt(values.length)];
    }

    private static float[] position(Random rng, List<float[]> placed) {
        float[] best = null;
        float bestSpacing = -1.0f;
        for (int attempt = 0; attempt < 60; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double radius = DISC_INNER + Math.sqrt(rng.nextDouble()) * (DISC_OUTER - DISC_INNER);
            float[] candidate = { (float) (Math.cos(angle) * radius), (float) ((rng.nextDouble() - 0.5) * 6.0),
                    (float) (Math.sin(angle) * radius) };
            float nearest = Float.MAX_VALUE;
            for (float[] other : placed) {
                float dx = candidate[0] - other[0], dy = candidate[1] - other[1], dz = candidate[2] - other[2];
                nearest = Math.min(nearest, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            if (nearest >= MIN_SPACING) return candidate;
            if (nearest > bestSpacing) {
                bestSpacing = nearest;
                best = candidate;
            }
        }
        return best;
    }

    private static Star star(Random rng) {
        float roll = rng.nextFloat();
        if (roll < 0.52f) {
            int[] colors = { 0xffd9a0, 0xffe9b0, 0xfff4d8, 0xcfe0ff, 0xffb86a, 0xff9a52, 0xb8d0ff };
            int color = colors[rng.nextInt(colors.length)];
            float radius = 0.7f + rng.nextFloat() * 0.8f;
            JsonObject extra = new JsonObject();
            if (rng.nextFloat() < 0.18f) {
                JsonObject companion = new JsonObject();
                companion.addProperty("kind", "star");
                companion.addProperty("color", hex(colors[rng.nextInt(colors.length)]));
                companion.addProperty("radius", round(0.5f + rng.nextFloat() * 0.5f, 2));
                companion.addProperty("separation", round(6.0f + rng.nextFloat() * 6.0f, 1));
                companion.addProperty("mass_ratio", round(0.5f + rng.nextFloat() * 0.5f, 2));
                companion.addProperty("period_seconds", 30 + rng.nextInt(60));
                extra.add("companion", companion);
            }
            return new Star("star", color, radius, radius * radius, 7, extra);
        }
        if (roll < 0.64f) return new Star("red_giant", 0xff6a3a, 1.8f + rng.nextFloat() * 0.8f, 40.0f, 5, new JsonObject());
        if (roll < 0.72f) return new Star("brown_dwarf", 0x9a4a3a, 0.4f + rng.nextFloat() * 0.2f, 0.02f, 4, new JsonObject());
        if (roll < 0.78f) return new Star("rapid_rotator", 0xcfe0ff, 1.0f + rng.nextFloat() * 0.3f, 3.0f, 6, new JsonObject());
        if (roll < 0.83f) return new Star("pulsar", 0xff4fd8, 0.5f + rng.nextFloat() * 0.1f, 0.5f, 3, new JsonObject());
        if (roll < 0.87f) return new Star("wolf_rayet", 0x9fc8ff, 1.0f, 60.0f, 3, new JsonObject());
        if (roll < 0.91f) return new Star("black_hole", 0xb45cff, 0.8f + rng.nextFloat() * 0.4f, 0.4f, 4, new JsonObject());
        if (roll < 0.95f) return new Star("protostar", 0xffa05a, 1.0f + rng.nextFloat() * 0.4f, 6.0f, 4, new JsonObject());
        return new Star("planetary_nebula", 0xe8f0ff, 0.5f, 0.3f, 4, new JsonObject());
    }

    private static String describe(Star star, Random rng) {
        String lead = switch (star.kind()) {
            case "red_giant" -> "A swollen red giant";
            case "brown_dwarf" -> "A failed star, barely warm";
            case "rapid_rotator" -> "A bright star spinning too fast to be round";
            case "pulsar" -> "A pulsar sweeping the dark with a beam";
            case "wolf_rayet" -> "A star shedding itself into space";
            case "black_hole" -> "A black hole with its disk lit";
            case "protostar" -> "A star still being born";
            case "planetary_nebula" -> "The shell a dying star left behind";
            default -> "A quiet star";
        };
        String[] tails = { "and the worlds that keep to it.", "with a few worlds worth a look.",
                "and whatever circles it.", "far enough out to be worth the trip." };
        return lead + " " + tails[rng.nextInt(tails.length)];
    }

    // ---- world kinds ----

    private enum Kind {
        SCORCHED, LAVA, BARREN, TEMPERATE, OCEAN, DESERT, JUNGLE, TOXIC, RUST, ICY, ICE_OCEAN, DARK, CRYSTAL, CHROME,
        SHATTERED, AURORA, STORM_WORLD,
        GAS_GIANT, HOT_GIANT, SULFUR_GIANT, FROZEN_GIANT;

        boolean giant() {
            return this == GAS_GIANT || this == HOT_GIANT || this == SULFUR_GIANT || this == FROZEN_GIANT;
        }
    }

    /** How common each kind is in each band of distance from the star (relative to where the habitable zone is). */
    private static final Map<Kind, float[]> WEIGHTS = new EnumMap<>(Kind.class);

    static {
        //                                    inner   warm    cold    far
        weights(Kind.SCORCHED,     new float[] { 22, 3, 0, 0 });
        weights(Kind.LAVA,         new float[] { 20, 4, 0, 0 });
        weights(Kind.BARREN,       new float[] { 22, 8, 10, 14 });
        weights(Kind.TOXIC,        new float[] { 8, 8, 2, 0 });
        weights(Kind.CHROME,       new float[] { 8, 3, 4, 4 });
        weights(Kind.HOT_GIANT,    new float[] { 10, 0, 0, 0 });
        weights(Kind.TEMPERATE,    new float[] { 0, 14, 1, 0 });
        weights(Kind.OCEAN,        new float[] { 0, 12, 2, 0 });
        weights(Kind.DESERT,       new float[] { 2, 12, 3, 0 });
        weights(Kind.JUNGLE,       new float[] { 0, 10, 0, 0 });
        weights(Kind.RUST,         new float[] { 3, 12, 5, 1 });
        weights(Kind.STORM_WORLD,  new float[] { 3, 5, 3, 0 });
        weights(Kind.ICY,          new float[] { 0, 0, 14, 14 });
        weights(Kind.ICE_OCEAN,    new float[] { 0, 5, 8, 6 });
        weights(Kind.DARK,         new float[] { 2, 0, 5, 10 });
        weights(Kind.CRYSTAL,      new float[] { 5, 6, 6, 6 });
        weights(Kind.SHATTERED,    new float[] { 5, 0, 6, 8 });
        weights(Kind.AURORA,       new float[] { 0, 5, 8, 6 });
        weights(Kind.GAS_GIANT,    new float[] { 0, 3, 16, 14 });
        weights(Kind.SULFUR_GIANT, new float[] { 0, 0, 8, 8 });
        weights(Kind.FROZEN_GIANT, new float[] { 0, 0, 5, 12 });
    }

    private static void weights(Kind kind, float[] perBand) {
        WEIGHTS.put(kind, perBand);
    }

    private static int band(float ratio) {
        return ratio < 0.6f ? 0 : ratio < 1.8f ? 1 : ratio < 4.0f ? 2 : 3;
    }

    /** What kind of world sits at this distance, with some memory of what the system already has. */
    private static Kind classify(Random rng, float ratio, Star star, Map<Kind, Integer> already, int giants) {
        int band = band(ratio);
        Kind[] kinds = Kind.values();
        float[] weight = new float[kinds.length];
        float total = 0.0f;
        for (int i = 0; i < kinds.length; i++) {
            Kind kind = kinds[i];
            float w = WEIGHTS.get(kind)[band];
            if (w <= 0.0f) continue;

            if (star.exotic()) {
                w *= switch (kind) {
                    case SHATTERED, BARREN, CRYSTAL, DARK, CHROME, LAVA -> 2.5f;
                    case TEMPERATE, OCEAN, JUNGLE, RUST -> 0.15f;
                    default -> 1.0f;
                };
            }
            // a system does not repeat itself: each time a kind has been used it is less likely again
            w *= (float) Math.pow(0.35, already.getOrDefault(kind, 0));
            if (kind.giant() && giants >= 2) w *= 0.15f;
            weight[i] = w;
            total += w;
        }
        if (total <= 0.0f) return Kind.BARREN;
        float roll = rng.nextFloat() * total;
        for (int i = 0; i < kinds.length; i++) {
            roll -= weight[i];
            if (weight[i] > 0.0f && roll <= 0.0f) return kinds[i];
        }
        return Kind.BARREN;
    }

    private static void generateBodies(Random rng, ResourceLocation systemId, String slug, String systemName, Star star,
                                       float distance, Map<ResourceLocation, JsonObject> out) {
        int count = 1 + rng.nextInt(Math.max(1, star.maxPlanets()));
        if (hasStarBody(star)) {
            out.put(new ResourceLocation(NAMESPACE, slug + "_star"), centralStar(systemId, systemName, star, distance));
        } else if ("black_hole".equals(star.kind())) {
            out.put(new ResourceLocation(NAMESPACE, slug + "_hole"), centralHole(systemId, systemName, star, distance));
        }
        float habitable = (float) Math.sqrt(star.luminosity());
        float orbit = Math.max(0.12f, habitable * (0.25f + rng.nextFloat() * 0.35f));

        Map<Kind, Integer> already = new EnumMap<>(Kind.class);
        int giants = 0;
        for (int i = 0; i < count; i++) {
            orbit *= 1.3f + rng.nextFloat() * 0.5f;
            Kind kind = classify(rng, orbit / habitable, star, already, giants);
            already.merge(kind, 1, Integer::sum);
            if (kind.giant()) giants++;

            String planetName = systemName + " " + roman(i + 1);
            ResourceLocation id = new ResourceLocation(NAMESPACE, slug + "_" + (i + 1));
            out.put(id, planet(rng, systemId, planetName, kind, orbit, distance, star));

            int moons = kind.giant() ? rng.nextInt(4) : rng.nextInt(3) - 1;
            for (int m = 0; m < moons; m++) {
                ResourceLocation moonId = new ResourceLocation(NAMESPACE, slug + "_" + (i + 1) +
                        "_" + (char) ('a' + m));
                out.put(moonId, moon(rng, systemId, id, planetName + " " + (char) ('a' + m), m, distance));
            }
        }
    }

    /** Ordinary stars are bodies too (a place to survey and build a Dyson swarm); the exotic ones are scenery. */
    private static boolean hasStarBody(Star star) {
        return switch (star.kind()) {
            case "star", "red_giant", "rapid_rotator", "wolf_rayet" -> true;
            default -> false;
        };
    }

    private static JsonObject centralStar(ResourceLocation system, String systemName, Star star, float distance) {
        JsonObject body = new JsonObject();
        body.addProperty("format", 1);
        body.addProperty("system", system.toString());
        body.addProperty("type", "star");
        body.addProperty("name", systemName + " (star)");
        body.addProperty("description", "The star itself. Rockets need stellar shielding to go near it.");
        JsonObject orbit = new JsonObject();
        orbit.addProperty("radius_au", 0.0);
        orbit.addProperty("period_days", 1);
        orbit.addProperty("phase", 0.0);
        body.add("orbit", orbit);
        body.addProperty("radius", round(star.radius(), 2));
        body.addProperty("trip_minutes", Math.min(60, tripMinutes(distance, 0.0f) + 6));
        body.addProperty("initial_stage", "unknown");
        JsonObject gate = new JsonObject();
        gate.addProperty("min_tier", "mid");
        body.add("gate", gate);
        JsonArray yields = new JsonArray();
        yields.add(yieldOf("minecraft:glowstone_dust", 4, 10, 1.0f));
        yields.add(yieldOf("minecraft:blaze_powder", 2, 6, 0.7f));
        yields.add(yieldOf("minecraft:netherite_scrap", 1, 1, 0.05f));
        body.add("yields", yields);
        JsonObject lore = new JsonObject();
        lore.addProperty("detected", "A star, bright enough to read by from here.");
        lore.addProperty("surveyed", systemName + " burns steadily. A swarm of collectors in orbit would catch " +
                "a great deal of it.");
        body.add("lore", lore);
        return body;
    }

    private static JsonObject centralHole(ResourceLocation system, String systemName, Star star, float distance) {
        JsonObject body = new JsonObject();
        body.addProperty("format", 1);
        body.addProperty("system", system.toString());
        body.addProperty("type", "black_hole");
        body.addProperty("name", systemName + " (black hole)");
        body.addProperty("description", "The black hole itself. Rockets need singularity shielding to go near it.");
        JsonObject orbit = new JsonObject();
        orbit.addProperty("radius_au", 0.0);
        orbit.addProperty("period_days", 1);
        orbit.addProperty("phase", 0.0);
        body.add("orbit", orbit);
        body.addProperty("radius", round(star.radius(), 2));
        body.addProperty("trip_minutes", Math.min(60, tripMinutes(distance, 0.0f) + 12));
        body.addProperty("initial_stage", "unknown");
        JsonObject gate = new JsonObject();
        gate.addProperty("min_tier", "late");
        body.add("gate", gate);
        JsonArray yields = new JsonArray();
        yields.add(yieldOf("minecraft:amethyst_shard", 4, 12, 1.0f));
        yields.add(yieldOf("minecraft:echo_shard", 1, 3, 0.5f));
        yields.add(yieldOf("minecraft:netherite_scrap", 1, 2, 0.3f));
        body.add("yields", yields);
        JsonObject lore = new JsonObject();
        lore.addProperty("detected", "Something dark that bends the light around it.");
        lore.addProperty("surveyed", systemName + " spins fast enough that a swarm of collectors could draw the " +
                "energy out of its rotation.");
        body.add("lore", lore);
        return body;
    }

    /** How a kind of world looks and what it gives. Colours are picked from the palettes, so two worlds differ. */
    private record Profile(String[][] palettes, float scaleMin, float scaleMax, float bump, float oceanMin,
                           float oceanMax, String oceanDeep, String oceanShallow, float iceMin, float iceMax,
                           float cloudMin, float cloudMax, int[] atmosphere, float densityMin, float densityMax,
                           String emissive, float emissiveMin, float emissiveMax, String style, float styleChance,
                           float radiusMin, float radiusMax, float ringChance, String description, String detected,
                           String surveyed) {}

    private static String[] p(String low, String mid, String high) {
        return new String[] { low, mid, high };
    }

    private static Profile profile(Kind kind) {
        return switch (kind) {
            case SCORCHED -> new Profile(new String[][] { p("#2a1a14", "#6a3a22", "#c07a42"),
                    p("#2c2220", "#7a4a32", "#d8a070") }, 2.6f, 4.0f, 0.9f, 0, 0, null, null, 0, 0, 0, 0.2f,
                    new int[] { 0xe89a5a, 0xd88a4a }, 0.1f, 0.6f, "#ff5a14", 0.15f, 0.45f, null, 0, 0.6f, 1.2f, 0,
                    "A world too close to its star, glowing where the crust has cracked.",
                    "A hot point of light hugging its star.",
                    "is hot enough to melt a rocket's heat shield, and rich in gold and obsidian.");
            case LAVA -> new Profile(new String[][] { p("#1a0f0c", "#4a1c10", "#a03a18"),
                    p("#14100e", "#3a2018", "#8a4a24") }, 3.0f, 5.0f, 1.2f, 0, 0, null, null, 0, 0, 0, 0.3f,
                    new int[] { 0xff7a3a, 0xffa050 }, 0.2f, 0.7f, "#ff6a10", 0.55f, 0.9f, "veins", 0.8f, 0.6f, 1.3f,
                    0, "A molten world, its surface a sea of lava under a thin crust.",
                    "A glowing orange disk.", "is molten rock under a black crust, and the magma is rich in obsidian.");
            case BARREN -> new Profile(new String[][] { p("#3a3a3a", "#7a7a78", "#c8c6c0"),
                    p("#3a322c", "#7a6c5c", "#b8a890"), p("#2c2c34", "#5e5e6a", "#a0a0b0") }, 3.0f, 6.0f, 0.9f, 0, 0,
                    null, null, 0, 0.1f, 0, 0, new int[] { 0x909090 }, 0.0f, 0.05f, null, 0, 0, null, 0, 0.4f, 1.0f, 0,
                    "An airless, cratered rock.", "A grey, pitted disk with no sign of air.",
                    "is airless and cratered, with iron and flint close to the surface.");
            case TEMPERATE -> new Profile(new String[][] { p("#3b4a2a", "#7a8f4e", "#d8d2b8") }, 2.2f, 3.0f, 0.8f,
                    0.3f, 0.5f, "#0b2a5c", "#2f7fb0", 0.2f, 0.5f, 0.3f, 0.6f, new int[] { 0x6aa8ff }, 0.5f, 0.85f,
                    null, 0, 0, null, 0, 0.7f, 1.5f, 0.05f, "A temperate world with oceans, ice caps and weather.",
                    "A blue-green disk with clouds.", "is an easy landing, mild and rich in common metals.");
            case OCEAN -> new Profile(new String[][] { p("#2d5a4a", "#5f9a78", "#cfe0d0"),
                    p("#2a4a5a", "#5a8aa0", "#d0e0ea") }, 2.0f, 2.8f, 0.6f, 0.6f, 0.85f, "#082a66", "#2a8fc0", 0,
                    0.4f, 0.4f, 0.8f, new int[] { 0x6aa8ff, 0x8abaff }, 0.5f, 0.85f, null, 0, 0, null, 0, 0.7f, 1.5f,
                    0.03f, "An ocean world with only scattered land.", "A world that is almost all water.",
                    "is mostly sea, with prismarine and clay in the shallows.");
            case DESERT -> new Profile(new String[][] { p("#6a4a2a", "#c08a4a", "#ecd2a0"),
                    p("#7a5a3a", "#d8b070", "#f4e4b8") }, 2.4f, 3.4f, 0.8f, 0, 0, null, null, 0, 0.15f, 0, 0.25f,
                    new int[] { 0xe8b27a, 0xf0c890 }, 0.2f, 0.6f, null, 0, 0, null, 0, 0.6f, 1.4f, 0,
                    "A dry, dusty world.", "An orange-brown disk with little cloud.",
                    "is dry and rich in copper and redstone.");
            case JUNGLE -> new Profile(new String[][] { p("#1a3a1a", "#3a7a2a", "#a8c84a"),
                    p("#1a3a2a", "#2a8a4a", "#8ad8a0") }, 2.4f, 3.2f, 0.9f, 0.15f, 0.3f, "#0a3a4a", "#2a8a7a", 0, 0.1f,
                    0.5f, 0.8f, new int[] { 0x8aeaa0, 0x7ad890 }, 0.6f, 0.9f, null, 0, 0, null, 0, 0.8f, 1.5f, 0,
                    "A lush world, green from pole to pole.", "A deep green disk under heavy cloud.",
                    "is thick with growth, and the air is wet, warm and breathable.");
            case TOXIC -> new Profile(new String[][] { p("#4a4a1a", "#a8a02a", "#e8e08a"),
                    p("#3a4a1a", "#7a9a2a", "#c8e068") }, 2.0f, 3.0f, 0.6f, 0, 0, null, null, 0, 0, 0.7f, 1.0f,
                    new int[] { 0xd8e84a, 0xa8d83a }, 0.8f, 1.2f, null, 0, 0, "storm", 0.3f, 0.7f, 1.3f, 0,
                    "A yellow-green world under a thick poisonous haze.", "A pale yellow disk, almost featureless.",
                    "has a crushing acid atmosphere, and the ground below it is full of sulphur.");
            case RUST -> new Profile(new String[][] { p("#4a1a10", "#9a4a2a", "#d88a5a"),
                    p("#3a1c14", "#8a3a22", "#c87a52") }, 2.4f, 3.4f, 0.9f, 0, 0.1f, "#3a1a10", "#6a3a28", 0, 0.25f, 0,
                    0.3f, new int[] { 0xd89a7a, 0xe0a888 }, 0.05f, 0.4f, null, 0, 0, null, 0, 0.6f, 1.2f, 0,
                    "A red, rust-coloured world with a thin sky.", "A red disk with pale poles.",
                    "is rusted iron all the way down, with redstone in the canyon walls.");
            case ICY -> new Profile(new String[][] { p("#8aa0b8", "#cfe0f0", "#ffffff"),
                    p("#a898b8", "#d8cce8", "#fff4ff") }, 2.4f, 3.4f, 0.5f, 0, 0, null, null, 0.6f, 0.9f, 0, 0.2f,
                    new int[] { 0xcfe4ff, 0xe0d8ff }, 0.0f, 0.3f, null, 0, 0, null, 0, 0.6f, 1.3f, 0.03f,
                    "A cold world locked in ice.", "A bright, cold world.",
                    "is a world of ice, with crystals frozen in it.");
            case ICE_OCEAN -> new Profile(new String[][] { p("#6a8aa8", "#a8c8e0", "#f0f8ff") }, 2.0f, 3.0f, 0.4f,
                    0.5f, 0.8f, "#0a2a5a", "#4a8ab8", 0.7f, 0.95f, 0.1f, 0.4f, new int[] { 0xa8d0ff }, 0.2f, 0.5f,
                    null, 0, 0, null, 0, 0.8f, 1.5f, 0, "A frozen ocean world, its sea locked under thick ice.",
                    "A white disk with dark cracks.", "has a sea under its ice, and prismarine crystals in the dark water.");
            case DARK -> new Profile(new String[][] { p("#0a0a0e", "#1c1c26", "#3a3a4a"),
                    p("#0e0a0a", "#241c1c", "#4a3a3a") }, 3.0f, 5.0f, 0.8f, 0, 0, null, null, 0, 0.1f, 0, 0.2f,
                    new int[] { 0x4a4a6a }, 0.0f, 0.4f, null, 0, 0, null, 0, 0.6f, 1.4f, 0.03f,
                    "A black, carbon-dark world that gives back almost no light.", "A barely visible dark disk.",
                    "is tar and coal, and nearly invisible until you are on top of it.");
            case CRYSTAL -> new Profile(new String[][] { p("#3a2a6a", "#8a6ad8", "#e0d0ff"),
                    p("#1a4a5a", "#4ab8d8", "#d0f4ff"), p("#5a2a4a", "#d86aa8", "#ffd8ee") }, 3.0f, 5.0f, 1.0f, 0, 0,
                    null, null, 0, 0.1f, 0, 0.2f, new int[] { 0xc8a8ff, 0xa8e0ff }, 0.1f, 0.5f, null, 0, 0, "crystal",
                    1.0f, 0.6f, 1.2f, 0.02f, "A world grown over with enormous crystals.",
                    "A glittering disk that throws light back in odd colours.",
                    "is crystal from the crust up, and its gems are easy to cut.");
            case CHROME -> new Profile(new String[][] { p("#4a4e58", "#a0a8b8", "#f0f4ff"),
                    p("#584a3a", "#b8a078", "#fff0d0") }, 3.0f, 5.0f, 0.5f, 0, 0, null, null, 0, 0.05f, 0, 0.1f,
                    new int[] { 0xc0c8d8 }, 0.0f, 0.2f, null, 0, 0, "chrome", 1.0f, 0.6f, 1.2f, 0,
                    "A mirror-bright world of bare metal.", "A disk so bright it looks polished.",
                    "is solid metal, and a rocket's lander can strip it with little effort.");
            case SHATTERED -> new Profile(new String[][] { p("#2a2a30", "#6a6a78", "#b8b8c8"),
                    p("#2a1a1a", "#7a5a4a", "#c8a890") }, 3.0f, 5.0f, 1.3f, 0, 0, null, null, 0, 0.1f, 0, 0.1f,
                    new int[] { 0x9090a0 }, 0.0f, 0.1f, null, 0, 0, "shatter", 1.0f, 0.5f, 1.1f, 0.03f,
                    "A world broken into drifting plates by some old impact.", "A fractured disk, its pieces still moving.",
                    "is in pieces, and the gaps between them are full of exposed ore.");
            case AURORA -> new Profile(new String[][] { p("#1a2a3a", "#3a5a7a", "#a8d0e8"),
                    p("#1a1a3a", "#4a3a8a", "#c8b8f0") }, 2.4f, 3.4f, 0.7f, 0, 0.2f, "#0a1a4a", "#2a4a9a", 0.2f, 0.6f,
                    0.1f, 0.4f, new int[] { 0x6affc0, 0x8af0ff }, 0.4f, 0.8f, null, 0, 0, "aurora", 1.0f, 0.7f, 1.4f,
                    0, "A world whose poles burn with permanent light.", "A dark disk crowned in green and violet.",
                    "is wrapped in aurora, and its air is thick with charged particles.");
            case STORM_WORLD -> new Profile(new String[][] { p("#2a2a3a", "#5a5a7a", "#a0a0c0") }, 2.0f, 3.0f, 0.6f, 0,
                    0.2f, "#1a2a4a", "#3a5a8a", 0, 0.2f, 0.7f, 1.0f, new int[] { 0x8a9ad8 }, 0.8f, 1.2f, null, 0, 0,
                    "storm", 1.0f, 0.8f, 1.5f, 0, "A rocky world under storms that never stop.",
                    "A grey disk wrapped in moving cloud.", "is battered by storms, and its lightning leaves copper veins in the rock.");
            case GAS_GIANT -> new Profile(new String[][] { p("#8a5a3a", "#d8a878", "#f6e6cc"),
                    p("#6a4a2a", "#c89a5a", "#ecd8a8"), p("#5a4a7a", "#a08ac0", "#e0d4f0"),
                    p("#7a3a2a", "#d8805a", "#f4d0b0") }, 4.5f, 6.0f, 0, 0, 0, null, null, 0, 0, 0, 0,
                    new int[] { 0xe8c090, 0xd8b0f0, 0xf0d898 }, 0.5f, 0.9f, null, 0, 0, "storm", 0.4f, 3.0f, 7.0f,
                    0.4f, "A banded gas giant.", "A large banded world.",
                    "offers little to land on, but its upper air is rich in blaze and glowstone.");
            case HOT_GIANT -> new Profile(new String[][] { p("#6a2a10", "#d8602a", "#ffb878"),
                    p("#5a1a1a", "#c83a3a", "#ff9a8a") }, 4.5f, 6.0f, 0, 0, 0, null, null, 0, 0, 0, 0,
                    new int[] { 0xff8a4a }, 0.6f, 1.0f, "#ff6a10", 0.1f, 0.3f, "storm", 0.5f, 3.0f, 6.0f, 0.1f,
                    "A giant so close to its star that it glows.", "A bright orange point, much larger than it should be.",
                    "is a roaring furnace, and its clouds are molten metal.");
            case SULFUR_GIANT -> new Profile(new String[][] { p("#8a7a1a", "#d8c83a", "#f8f0a0"),
                    p("#7a6a2a", "#c8b050", "#f0e0a0") }, 4.5f, 6.0f, 0, 0, 0, null, null, 0, 0, 0, 0,
                    new int[] { 0xf0e060 }, 0.6f, 1.0f, null, 0, 0, "storm", 0.5f, 3.0f, 7.0f, 0.3f,
                    "A yellow giant wrapped in sulphur clouds.", "A pale yellow giant with dark bands.",
                    "is a cloud of sulphur and gunpowder-grade nitre, with a core nobody has seen.");
            case FROZEN_GIANT -> new Profile(new String[][] { p("#3a5a8a", "#6a9ad0", "#cfe4ff"),
                    p("#3a6a7a", "#6ac0d0", "#d0f4f8") }, 4.5f, 6.0f, 0, 0, 0, null, null, 0, 0, 0, 0,
                    new int[] { 0x8ab4f0, 0x8ae0f0 }, 0.5f, 0.9f, null, 0, 0, null, 0, 3.0f, 6.0f, 0.3f,
                    "A pale ice giant far from its star.", "A faint blue giant at the edge of the system.",
                    "offers nothing to land on, but its ice is rich in lapis.");
        };
    }

    private static JsonObject planet(Random rng, ResourceLocation system, String name, Kind kind, float orbit,
                                     float distance, Star star) {
        Profile profile = profile(kind);
        boolean giant = kind.giant();
        JsonObject body = new JsonObject();
        body.addProperty("format", 1);
        body.addProperty("system", system.toString());
        body.addProperty("type", "planet");
        body.addProperty("name", name);
        body.addProperty("description", profile.description());

        JsonObject orbitJson = new JsonObject();
        orbitJson.addProperty("radius_au", round(orbit, 2));
        orbitJson.addProperty("period_days", Math.max(2, Math.round(365.0 * Math.pow(orbit, 1.5) /
                Math.sqrt(Math.max(0.2f, star.luminosity() > 5 ? 1.6f : 1.0f)))));
        orbitJson.addProperty("phase", round(rng.nextFloat(), 2));
        body.add("orbit", orbitJson);

        body.addProperty("radius", round(between(rng, profile.radiusMin(), profile.radiusMax()), 2));
        body.addProperty("gas_giant", giant);
        body.addProperty("axial_tilt", rng.nextInt(giant ? 30 : 40));
        body.addProperty("spin_deg_per_sec", giant ? 8 + rng.nextInt(10) : 2 + rng.nextInt(8));
        if (!giant) body.addProperty("cloud_spin_deg_per_sec", 3 + rng.nextInt(8));

        JsonObject surface = new JsonObject();
        surface.addProperty("seed", rng.nextInt(1000));
        surface.addProperty("scale", round(between(rng, profile.scaleMin(), profile.scaleMax()), 1));
        if (!giant) {
            surface.addProperty("ocean_level", round(between(rng, profile.oceanMin(), profile.oceanMax()), 2));
            surface.addProperty("polar_ice", round(between(rng, profile.iceMin(), profile.iceMax()), 2));
            surface.addProperty("cloud_cover", round(between(rng, profile.cloudMin(), profile.cloudMax()), 2));
            surface.addProperty("bump", profile.bump());
        }
        String[] palette = profile.palettes()[rng.nextInt(profile.palettes().length)];
        surface.addProperty("low", palette[0]);
        surface.addProperty("mid", palette[1]);
        surface.addProperty("high", palette[2]);
        if (profile.oceanDeep() != null) {
            surface.addProperty("ocean_deep", profile.oceanDeep());
            surface.addProperty("ocean_shallow", profile.oceanShallow());
        }
        if (profile.emissive() != null) {
            surface.addProperty("emissive", profile.emissive());
            surface.addProperty("emissive_amount", round(between(rng, profile.emissiveMin(), profile.emissiveMax()), 2));
        }
        body.add("surface", surface);

        JsonObject atmosphere = new JsonObject();
        atmosphere.addProperty("color", hex(profile.atmosphere()[rng.nextInt(profile.atmosphere().length)]));
        atmosphere.addProperty("density", round(between(rng, profile.densityMin(), profile.densityMax()), 2));
        body.add("atmosphere", atmosphere);

        body.addProperty("trip_minutes", tripMinutes(distance, orbit));
        body.addProperty("initial_stage", "unknown");

        if (profile.style() != null && rng.nextFloat() < profile.styleChance()) {
            body.addProperty("style", profile.style());
        }
        if (rng.nextFloat() < profile.ringChance()) body.add("rings", rings(rng));

        body.add("yields", yields(rng, kind));
        JsonObject lore = new JsonObject();
        lore.addProperty("detected", profile.detected());
        lore.addProperty("surveyed", name + " " + profile.surveyed());
        body.add("lore", lore);
        return body;
    }

    private static float between(Random rng, float min, float max) {
        return min + rng.nextFloat() * (max - min);
    }

    private static JsonObject moon(Random rng, ResourceLocation system, ResourceLocation parent, String name,
                                   int index, float distance) {
        JsonObject body = new JsonObject();
        body.addProperty("format", 1);
        body.addProperty("system", system.toString());
        body.addProperty("type", "moon");
        body.addProperty("parent", parent.toString());
        body.addProperty("name", name);

        // moons are not all the same grey rock
        int variant = rng.nextInt(5);
        String description = switch (variant) {
            case 1 -> "A small ice-covered moon.";
            case 2 -> "A small volcanic moon, yellow with sulphur.";
            case 3 -> "A small rust-red moon.";
            default -> "A small airless moon.";
        };
        body.addProperty("description", description);
        JsonObject orbit = new JsonObject();
        orbit.addProperty("radius_au", round(0.005f + index * 0.004f + rng.nextFloat() * 0.004f, 4));
        orbit.addProperty("period_days", 3 + index * 6 + rng.nextInt(8));
        orbit.addProperty("phase", round(rng.nextFloat(), 2));
        body.add("orbit", orbit);
        body.addProperty("radius", round(0.15f + rng.nextFloat() * 0.25f, 2));
        body.addProperty("gas_giant", false);
        body.addProperty("axial_tilt", rng.nextInt(10));
        body.addProperty("spin_deg_per_sec", 1 + rng.nextInt(4));

        JsonObject surface = new JsonObject();
        surface.addProperty("seed", rng.nextInt(1000));
        surface.addProperty("scale", round(3.5f + rng.nextFloat() * 2.0f, 1));
        surface.addProperty("ocean_level", 0.0);
        surface.addProperty("polar_ice", variant == 1 ? 0.8 : 0.0);
        surface.addProperty("cloud_cover", 0.0);
        switch (variant) {
            case 1 -> {
                surface.addProperty("low", "#9ab0c8");
                surface.addProperty("mid", "#d8e8f4");
                surface.addProperty("high", "#ffffff");
            }
            case 2 -> {
                surface.addProperty("low", "#6a5a1a");
                surface.addProperty("mid", "#d8c83a");
                surface.addProperty("high", "#f8f0a0");
                surface.addProperty("emissive", "#ff8a1a");
                surface.addProperty("emissive_amount", 0.2);
            }
            case 3 -> {
                surface.addProperty("low", "#4a2a1a");
                surface.addProperty("mid", "#9a5a3a");
                surface.addProperty("high", "#d89a7a");
            }
            default -> {
                int grey = 0x3a + rng.nextInt(0x20);
                surface.addProperty("low", hex(grey << 16 | grey << 8 | grey));
                surface.addProperty("mid", hex((grey + 0x40) << 16 | (grey + 0x40) << 8 | (grey + 0x40)));
                surface.addProperty("high", hex(0xc8c8c8));
            }
        }
        body.add("surface", surface);
        JsonObject atmosphere = new JsonObject();
        atmosphere.addProperty("density", 0.0);
        body.add("atmosphere", atmosphere);

        body.addProperty("trip_minutes", Math.max(6, tripMinutes(distance, 0.0f) - 2));
        body.addProperty("initial_stage", "unknown");
        JsonArray yields = new JsonArray();
        switch (variant) {
            case 1 -> {
                yields.add(yieldOf("minecraft:packed_ice", 3, 8, 1.0f));
                yields.add(yieldOf("minecraft:snowball", 4, 10, 0.7f));
            }
            case 2 -> {
                yields.add(yieldOf("minecraft:gunpowder", 3, 8, 1.0f));
                yields.add(yieldOf("minecraft:blaze_powder", 1, 3, 0.4f));
            }
            case 3 -> {
                yields.add(yieldOf("minecraft:raw_iron", 3, 8, 1.0f));
                yields.add(yieldOf("minecraft:redstone", 2, 5, 0.6f));
            }
            default -> {
                yields.add(yieldOf("minecraft:flint", 3, 8, 1.0f));
                yields.add(yieldOf(rng.nextBoolean() ? "minecraft:quartz" : "minecraft:raw_iron", 1, 4, 0.5f));
            }
        }
        body.add("yields", yields);
        JsonObject lore = new JsonObject();
        lore.addProperty("detected", "A small companion world.");
        lore.addProperty("surveyed", name + " has no air and a good place for an outpost.");
        body.add("lore", lore);
        return body;
    }

    private static JsonObject rings(Random rng) {
        JsonObject r = new JsonObject();
        r.addProperty("inner", round(1.4f + rng.nextFloat() * 0.3f, 2));
        r.addProperty("outer", round(2.2f + rng.nextFloat() * 0.6f, 2));
        r.addProperty("color", "#e8d8b8");
        r.addProperty("color2", "#a08868");
        r.addProperty("tilt", rng.nextInt(25));
        r.addProperty("opacity", round(0.6f + rng.nextFloat() * 0.3f, 2));
        return r;
    }

    private static JsonArray yields(Random rng, Kind kind) {
        JsonArray list = new JsonArray();
        switch (kind) {
            case SCORCHED -> {
                list.add(yieldOf("minecraft:raw_gold", 2, 6, 1.0f));
                list.add(yieldOf("minecraft:obsidian", 2, 5, 0.8f));
                list.add(yieldOf("minecraft:blaze_powder", 1, 4, 0.5f));
                list.add(yieldOf("minecraft:netherite_scrap", 1, 1, 0.06f));
            }
            case LAVA -> {
                list.add(yieldOf("minecraft:obsidian", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:magma_cream", 1, 4, 0.7f));
                list.add(yieldOf("minecraft:blaze_rod", 1, 3, 0.4f));
                list.add(yieldOf("minecraft:netherite_scrap", 1, 1, 0.08f));
            }
            case BARREN -> {
                list.add(yieldOf("minecraft:raw_iron", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:flint", 3, 8, 0.8f));
                list.add(yieldOf("minecraft:quartz", 1, 4, 0.4f));
            }
            case TEMPERATE -> {
                list.add(yieldOf("minecraft:iron_ingot", 4, 10, 1.0f));
                list.add(yieldOf("minecraft:copper_ingot", 4, 10, 0.8f));
                list.add(yieldOf(rng.nextBoolean() ? "minecraft:coal" : "minecraft:clay_ball", 2, 6, 0.6f));
            }
            case OCEAN -> {
                list.add(yieldOf("minecraft:prismarine_shard", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:clay_ball", 3, 8, 0.8f));
                list.add(yieldOf("minecraft:nautilus_shell", 1, 2, 0.12f));
            }
            case DESERT -> {
                list.add(yieldOf("minecraft:raw_copper", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:redstone", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:gold_nugget", 3, 9, 0.5f));
            }
            case JUNGLE -> {
                list.add(yieldOf("minecraft:cocoa_beans", 4, 10, 1.0f));
                list.add(yieldOf("minecraft:slime_ball", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:glow_berries", 3, 8, 0.6f));
                list.add(yieldOf("minecraft:emerald", 1, 2, 0.1f));
            }
            case TOXIC -> {
                list.add(yieldOf("minecraft:gunpowder", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:spider_eye", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:glowstone_dust", 2, 6, 0.5f));
            }
            case RUST -> {
                list.add(yieldOf("minecraft:raw_iron", 4, 10, 1.0f));
                list.add(yieldOf("minecraft:redstone", 3, 8, 0.8f));
                list.add(yieldOf("minecraft:gold_nugget", 2, 6, 0.4f));
            }
            case ICY -> {
                list.add(yieldOf("minecraft:packed_ice", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:amethyst_shard", 1, 3, 0.5f));
                list.add(yieldOf("minecraft:diamond", 1, 1, 0.08f));
            }
            case ICE_OCEAN -> {
                list.add(yieldOf("minecraft:blue_ice", 2, 6, 1.0f));
                list.add(yieldOf("minecraft:prismarine_crystals", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:nautilus_shell", 1, 1, 0.08f));
            }
            case DARK -> {
                list.add(yieldOf("minecraft:coal", 4, 10, 1.0f));
                list.add(yieldOf("minecraft:charcoal", 3, 8, 0.7f));
                list.add(yieldOf("minecraft:obsidian", 1, 4, 0.5f));
            }
            case CRYSTAL -> {
                list.add(yieldOf("minecraft:amethyst_shard", 3, 9, 1.0f));
                list.add(yieldOf("minecraft:quartz", 2, 6, 0.8f));
                list.add(yieldOf("minecraft:diamond", 1, 1, 0.12f));
            }
            case CHROME -> {
                list.add(yieldOf("minecraft:iron_ingot", 4, 10, 1.0f));
                list.add(yieldOf("minecraft:gold_ingot", 2, 5, 0.6f));
                list.add(yieldOf("minecraft:copper_ingot", 3, 8, 0.8f));
                list.add(yieldOf("minecraft:netherite_scrap", 1, 1, 0.04f));
            }
            case SHATTERED -> {
                list.add(yieldOf("minecraft:flint", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:raw_iron", 2, 6, 0.8f));
                list.add(yieldOf("minecraft:quartz", 1, 4, 0.5f));
                list.add(yieldOf("minecraft:echo_shard", 1, 1, 0.05f));
            }
            case AURORA -> {
                list.add(yieldOf("minecraft:glowstone_dust", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:prismarine_crystals", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:ender_pearl", 1, 2, 0.25f));
            }
            case STORM_WORLD -> {
                list.add(yieldOf("minecraft:copper_ingot", 4, 10, 1.0f));
                list.add(yieldOf("minecraft:lightning_rod", 1, 2, 0.3f));
                list.add(yieldOf("minecraft:redstone", 2, 6, 0.7f));
            }
            case GAS_GIANT -> {
                list.add(yieldOf("minecraft:blaze_powder", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:glowstone_dust", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:ender_pearl", 1, 2, 0.2f));
            }
            case HOT_GIANT -> {
                list.add(yieldOf("minecraft:blaze_powder", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:magma_cream", 2, 5, 0.6f));
                list.add(yieldOf("minecraft:netherite_scrap", 1, 1, 0.05f));
            }
            case SULFUR_GIANT -> {
                list.add(yieldOf("minecraft:gunpowder", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:glowstone_dust", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:slime_ball", 1, 4, 0.4f));
            }
            default -> {
                list.add(yieldOf("minecraft:blue_ice", 2, 6, 0.9f));
                list.add(yieldOf("minecraft:lapis_lazuli", 3, 9, 0.7f));
                list.add(yieldOf("minecraft:amethyst_shard", 1, 3, 0.4f));
            }
        }
        return list;
    }

    private static JsonObject yieldOf(String item, int min, int max, float chance) {
        JsonObject y = new JsonObject();
        y.addProperty("item", item);
        y.addProperty("min", min);
        y.addProperty("max", max);
        y.addProperty("chance", chance);
        return y;
    }

    private static int tripMinutes(float distance, float orbit) {
        return Math.max(6, Math.min(60, Math.round(8.0f + distance * 1.4f + orbit * 2.0f)));
    }

    private static String roman(int n) {
        String[] numerals = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        return n >= 1 && n <= numerals.length ? numerals[n - 1] : Integer.toString(n);
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    private static double round(float value, int places) {
        double scale = Math.pow(10.0, places);
        return Math.round(value * scale) / scale;
    }
}
