package net.phoenix.continuum.data;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
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

    private record Star(String kind, int color, float radius, float luminosity, int maxPlanets, JsonObject extra) {}

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
            ResourceLocation systemId = ResourceLocation.fromNamespaceAndPath(NAMESPACE, slug);

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
        if (roll < 0.83f) return new Star("pulsar", 0xff4fd8, 0.5f + rng.nextFloat() * 0.1f, 0.5f, 2, new JsonObject());
        if (roll < 0.87f) return new Star("wolf_rayet", 0x9fc8ff, 1.0f, 60.0f, 2, new JsonObject());
        if (roll < 0.91f) return new Star("black_hole", 0xb45cff, 0.8f + rng.nextFloat() * 0.4f, 0.4f, 3, new JsonObject());
        if (roll < 0.95f) return new Star("protostar", 0xffa05a, 1.0f + rng.nextFloat() * 0.4f, 6.0f, 4, new JsonObject());
        return new Star("planetary_nebula", 0xe8f0ff, 0.5f, 0.3f, 3, new JsonObject());
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

    // ---- worlds ----

    private enum Kind {
        SCORCHED, TEMPERATE, OCEAN, DESERT, ICY, FROZEN_GIANT, GAS_GIANT
    }

    private static void generateBodies(Random rng, ResourceLocation systemId, String slug, String systemName, Star star,
                                       float distance, Map<ResourceLocation, JsonObject> out) {
        int count = 1 + rng.nextInt(Math.max(1, star.maxPlanets()));
        float habitable = (float) Math.sqrt(star.luminosity());
        float orbit = Math.max(0.12f, habitable * (0.25f + rng.nextFloat() * 0.35f));

        for (int i = 0; i < count; i++) {
            orbit *= 1.35f + rng.nextFloat() * 0.6f;
            float ratio = orbit / habitable;
            Kind kind = classify(rng, ratio);
            String planetName = systemName + " " + roman(i + 1);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, slug + "_" + (i + 1));
            JsonObject body = planet(rng, systemId, planetName, kind, orbit, distance, star);
            out.put(id, body);

            int moons = (kind == Kind.GAS_GIANT || kind == Kind.FROZEN_GIANT) ? rng.nextInt(4) : rng.nextInt(3) - 1;
            for (int m = 0; m < moons; m++) {
                ResourceLocation moonId = ResourceLocation.fromNamespaceAndPath(NAMESPACE, slug + "_" + (i + 1) +
                        "_" + (char) ('a' + m));
                out.put(moonId, moon(rng, systemId, id, planetName + " " + (char) ('a' + m), m, distance));
            }
        }
    }

    private static Kind classify(Random rng, float ratio) {
        if (ratio < 0.55f) return Kind.SCORCHED;
        if (ratio < 1.5f) {
            float roll = rng.nextFloat();
            return roll < 0.45f ? Kind.TEMPERATE : roll < 0.75f ? Kind.OCEAN : Kind.DESERT;
        }
        if (ratio < 2.8f) return rng.nextFloat() < 0.45f ? Kind.GAS_GIANT : Kind.ICY;
        return rng.nextFloat() < 0.6f ? Kind.FROZEN_GIANT : (rng.nextFloat() < 0.5f ? Kind.GAS_GIANT : Kind.ICY);
    }

    private static JsonObject planet(Random rng, ResourceLocation system, String name, Kind kind, float orbit,
                                     float distance, Star star) {
        boolean giant = kind == Kind.GAS_GIANT || kind == Kind.FROZEN_GIANT;
        JsonObject body = new JsonObject();
        body.addProperty("format", 1);
        body.addProperty("system", system.toString());
        body.addProperty("type", "planet");
        body.addProperty("name", name);
        body.addProperty("description", planetDescription(kind));

        JsonObject orbitJson = new JsonObject();
        orbitJson.addProperty("radius_au", round(orbit, 2));
        orbitJson.addProperty("period_days", Math.max(2, Math.round(365.0 * Math.pow(orbit, 1.5) /
                Math.sqrt(Math.max(0.2f, star.luminosity() > 5 ? 1.6f : 1.0f)))));
        orbitJson.addProperty("phase", round(rng.nextFloat(), 2));
        body.add("orbit", orbitJson);

        body.addProperty("radius", round(giant ? 3.0f + rng.nextFloat() * 4.0f : 0.6f + rng.nextFloat() * 0.9f, 2));
        body.addProperty("gas_giant", giant);
        body.addProperty("axial_tilt", rng.nextInt(35));
        body.addProperty("spin_deg_per_sec", giant ? 8 + rng.nextInt(10) : 2 + rng.nextInt(8));
        if (!giant) body.addProperty("cloud_spin_deg_per_sec", 3 + rng.nextInt(8));

        body.add("surface", surface(rng, kind));
        JsonObject atmosphere = new JsonObject();
        atmosphere.addProperty("color", hex(atmosphereColor(kind, rng)));
        atmosphere.addProperty("density", round(switch (kind) {
            case SCORCHED -> 0.1f + rng.nextFloat() * 0.5f;
            case TEMPERATE, OCEAN -> 0.5f + rng.nextFloat() * 0.35f;
            case DESERT -> 0.2f + rng.nextFloat() * 0.4f;
            case ICY -> rng.nextFloat() * 0.3f;
            default -> 0.5f + rng.nextFloat() * 0.4f;
        }, 2));
        body.add("atmosphere", atmosphere);

        body.addProperty("trip_minutes", tripMinutes(distance, orbit));
        body.addProperty("initial_stage", "unknown");

        if (giant) {
            if (rng.nextFloat() < 0.4f) body.addProperty("style", "storm");
            if (rng.nextFloat() < 0.4f) body.add("rings", rings(rng));
        } else if (kind == Kind.SCORCHED && rng.nextFloat() < 0.35f) {
            body.addProperty("style", "veins");
        }

        body.add("yields", yields(rng, kind));
        JsonObject lore = new JsonObject();
        lore.addProperty("detected", detectedLore(kind));
        lore.addProperty("surveyed", name + " " + surveyedLore(kind));
        body.add("lore", lore);
        return body;
    }

    private static JsonObject moon(Random rng, ResourceLocation system, ResourceLocation parent, String name,
                                   int index, float distance) {
        JsonObject body = new JsonObject();
        body.addProperty("format", 1);
        body.addProperty("system", system.toString());
        body.addProperty("type", "moon");
        body.addProperty("parent", parent.toString());
        body.addProperty("name", name);
        body.addProperty("description", "A small airless moon.");
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
        surface.addProperty("polar_ice", 0.0);
        surface.addProperty("cloud_cover", 0.0);
        int grey = 0x3a + rng.nextInt(0x20);
        surface.addProperty("low", hex(grey << 16 | grey << 8 | grey));
        surface.addProperty("mid", hex((grey + 0x40) << 16 | (grey + 0x40) << 8 | (grey + 0x40)));
        surface.addProperty("high", hex(0xc8c8c8));
        body.add("surface", surface);
        JsonObject atmosphere = new JsonObject();
        atmosphere.addProperty("density", 0.0);
        body.add("atmosphere", atmosphere);

        body.addProperty("trip_minutes", Math.max(6, tripMinutes(distance, 0.0f) - 2));
        body.addProperty("initial_stage", "unknown");
        JsonArray yields = new JsonArray();
        yields.add(yieldOf("minecraft:flint", 3, 8, 1.0f));
        yields.add(yieldOf(rng.nextBoolean() ? "minecraft:quartz" : "minecraft:raw_iron", 1, 4, 0.5f));
        body.add("yields", yields);
        JsonObject lore = new JsonObject();
        lore.addProperty("detected", "A small grey companion.");
        lore.addProperty("surveyed", name + " has no air, plenty of flint, and a good place for an outpost.");
        body.add("lore", lore);
        return body;
    }

    private static JsonObject surface(Random rng, Kind kind) {
        JsonObject s = new JsonObject();
        s.addProperty("seed", rng.nextInt(1000));
        switch (kind) {
            case SCORCHED -> {
                s.addProperty("scale", round(2.6f + rng.nextFloat() * 1.4f, 1));
                s.addProperty("ocean_level", 0.0);
                s.addProperty("polar_ice", 0.0);
                s.addProperty("cloud_cover", round(rng.nextFloat() * 0.2f, 2));
                s.addProperty("bump", 0.9);
                s.addProperty("low", "#2a1a14");
                s.addProperty("mid", "#6a3a22");
                s.addProperty("high", "#c07a42");
                s.addProperty("emissive", "#ff5a14");
                s.addProperty("emissive_amount", round(0.15f + rng.nextFloat() * 0.3f, 2));
            }
            case TEMPERATE -> {
                s.addProperty("scale", round(2.2f + rng.nextFloat() * 0.8f, 1));
                s.addProperty("ocean_level", round(0.3f + rng.nextFloat() * 0.2f, 2));
                s.addProperty("polar_ice", round(0.2f + rng.nextFloat() * 0.3f, 2));
                s.addProperty("cloud_cover", round(0.3f + rng.nextFloat() * 0.3f, 2));
                s.addProperty("bump", 0.8);
                s.addProperty("low", "#3b4a2a");
                s.addProperty("mid", "#7a8f4e");
                s.addProperty("high", "#d8d2b8");
                s.addProperty("ocean_deep", "#0b2a5c");
                s.addProperty("ocean_shallow", "#2f7fb0");
            }
            case OCEAN -> {
                s.addProperty("scale", round(2.0f + rng.nextFloat() * 0.8f, 1));
                s.addProperty("ocean_level", round(0.6f + rng.nextFloat() * 0.25f, 2));
                s.addProperty("polar_ice", round(rng.nextFloat() * 0.4f, 2));
                s.addProperty("cloud_cover", round(0.4f + rng.nextFloat() * 0.4f, 2));
                s.addProperty("bump", 0.6);
                s.addProperty("low", "#2d5a4a");
                s.addProperty("mid", "#5f9a78");
                s.addProperty("high", "#cfe0d0");
                s.addProperty("ocean_deep", "#082a66");
                s.addProperty("ocean_shallow", "#2a8fc0");
            }
            case DESERT -> {
                s.addProperty("scale", round(2.4f + rng.nextFloat() * 1.0f, 1));
                s.addProperty("ocean_level", 0.0);
                s.addProperty("polar_ice", round(rng.nextFloat() * 0.15f, 2));
                s.addProperty("cloud_cover", round(rng.nextFloat() * 0.25f, 2));
                s.addProperty("bump", 0.8);
                s.addProperty("low", "#6a4a2a");
                s.addProperty("mid", "#c08a4a");
                s.addProperty("high", "#ecd2a0");
            }
            case ICY -> {
                s.addProperty("scale", round(2.4f + rng.nextFloat() * 1.0f, 1));
                s.addProperty("ocean_level", 0.0);
                s.addProperty("polar_ice", round(0.6f + rng.nextFloat() * 0.3f, 2));
                s.addProperty("cloud_cover", round(rng.nextFloat() * 0.2f, 2));
                s.addProperty("bump", 0.5);
                s.addProperty("low", "#8aa0b8");
                s.addProperty("mid", "#cfe0f0");
                s.addProperty("high", "#ffffff");
            }
            case FROZEN_GIANT -> {
                s.addProperty("scale", round(4.5f + rng.nextFloat() * 1.5f, 1));
                s.addProperty("low", "#3a5a8a");
                s.addProperty("mid", "#6a9ad0");
                s.addProperty("high", "#cfe4ff");
            }
            default -> {
                s.addProperty("scale", round(4.5f + rng.nextFloat() * 1.5f, 1));
                String[][] palettes = { { "#8a5a3a", "#d8a878", "#f6e6cc" }, { "#6a4a2a", "#c89a5a", "#ecd8a8" },
                        { "#5a4a7a", "#a08ac0", "#e0d4f0" }, { "#7a3a2a", "#d8805a", "#f4d0b0" } };
                String[] palette = palettes[rng.nextInt(palettes.length)];
                s.addProperty("low", palette[0]);
                s.addProperty("mid", palette[1]);
                s.addProperty("high", palette[2]);
            }
        }
        return s;
    }

    private static int atmosphereColor(Kind kind, Random rng) {
        return switch (kind) {
            case SCORCHED -> 0xe89a5a;
            case TEMPERATE, OCEAN -> 0x6aa8ff;
            case DESERT -> 0xe8b27a;
            case ICY -> 0xcfe4ff;
            case FROZEN_GIANT -> 0x8ab4f0;
            default -> new int[] { 0xe8c090, 0xd8b0f0, 0xf0d898 }[rng.nextInt(3)];
        };
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
            case ICY -> {
                list.add(yieldOf("minecraft:packed_ice", 3, 8, 1.0f));
                list.add(yieldOf("minecraft:amethyst_shard", 1, 3, 0.5f));
                list.add(yieldOf("minecraft:diamond", 1, 1, 0.08f));
            }
            case FROZEN_GIANT -> {
                list.add(yieldOf("minecraft:blue_ice", 2, 6, 0.9f));
                list.add(yieldOf("minecraft:lapis_lazuli", 3, 9, 0.7f));
                list.add(yieldOf("minecraft:amethyst_shard", 1, 3, 0.4f));
            }
            default -> {
                list.add(yieldOf("minecraft:blaze_powder", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:glowstone_dust", 2, 6, 0.7f));
                list.add(yieldOf("minecraft:ender_pearl", 1, 2, 0.2f));
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

    private static String planetDescription(Kind kind) {
        return switch (kind) {
            case SCORCHED -> "A world too close to its star, glowing where the crust has cracked.";
            case TEMPERATE -> "A temperate world with oceans, ice caps and weather.";
            case OCEAN -> "An ocean world with only scattered land.";
            case DESERT -> "A dry, dusty world.";
            case ICY -> "A cold world locked in ice.";
            case FROZEN_GIANT -> "A pale ice giant far from its star.";
            default -> "A banded gas giant.";
        };
    }

    private static String detectedLore(Kind kind) {
        return switch (kind) {
            case SCORCHED -> "A hot point of light hugging its star.";
            case TEMPERATE -> "A blue-green disk with clouds.";
            case OCEAN -> "A world that is almost all water.";
            case DESERT -> "An orange-brown disk with little cloud.";
            case ICY -> "A bright, cold world.";
            case FROZEN_GIANT -> "A faint blue giant at the edge of the system.";
            default -> "A large banded world.";
        };
    }

    private static String surveyedLore(Kind kind) {
        return switch (kind) {
            case SCORCHED -> "is hot enough to melt a rocket's heat shield, and rich in gold and obsidian.";
            case TEMPERATE -> "is an easy landing, mild and rich in common metals.";
            case OCEAN -> "is mostly sea, with prismarine and clay in the shallows.";
            case DESERT -> "is dry and rich in copper and redstone.";
            case ICY -> "is a world of ice, with crystals frozen in it.";
            case FROZEN_GIANT -> "offers nothing to land on, but its ice is rich in lapis.";
            default -> "offers little to land on, but its upper air is rich in blaze and glowstone.";
        };
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
