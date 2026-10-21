package net.phoenix.continuum.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Server-side edits made with the in-game tuner. Each body with edits has a small file
 * {@code config/phoenix_continuum/tuning/<namespace>/<name>.json} holding only the changed keys. They are merged over
 * the resolved definition on every load, so the original files stay as they were, and deleting a patch (or using
 * Reset in the tuner) puts the body back.
 */
public final class TuningPatches {

    /** The top-level keys the tuner may change on the server. */
    public static final Set<String> EDITABLE = Set.of("name", "description", "trip_minutes", "initial_stage", "orbit",
            "gate", "yields", "pdim_cost", "discipline", "radius", "gas_giant", "axial_tilt", "spin_deg_per_sec",
            "cloud_spin_deg_per_sec", "surface", "atmosphere", "rings", "style", "hole_tilt", "sky_hole_inclination",
            "sky_hole_lean", "lore", "visibility");

    private TuningPatches() {}

    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve("phoenix_continuum").resolve("tuning");
    }

    private static Path file(ResourceLocation id) {
        return root().resolve(id.getNamespace()).resolve(id.getPath() + ".json");
    }

    /** Merges every patch file over the matching resolved body. */
    static void applyAll(Map<ResourceLocation, JsonObject> resolvedBodies, ContinuumReport report) {
        Path root = root();
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                Path relative = root.relativize(path);
                if (relative.getNameCount() < 2) return;
                String namespace = relative.getName(0).toString();
                String name = root.resolve(namespace).relativize(path).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".json".length());
                ResourceLocation id = ResourceLocation.tryParse(namespace + ":" + name);
                if (id == null) return;
                JsonObject target = resolvedBodies.get(id);
                if (target == null) {
                    report.warn("tuning " + id, "has a patch but no such body exists (ignored)");
                    return;
                }
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonElement patch = JsonParser.parseReader(reader);
                    if (patch.isJsonObject()) resolvedBodies.put(id, ContentResolver.merge(target, patch.getAsJsonObject()));
                } catch (Exception e) {
                    report.error("tuning " + id, "could not be read: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            report.error("tuning", "could not scan " + root + ": " + e.getMessage());
        }
    }

    private static JsonObject read(ResourceLocation id) {
        Path file = file(id);
        if (!Files.exists(file)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void write(ResourceLocation id, JsonObject patch) throws IOException {
        Path file = file(id);
        if (patch.size() == 0) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(patch));
    }

    /**
     * Sets one value at a dotted path in the body's patch ({@code orbit.radius_au}); a whole array or object can be
     * given at once. A null value removes that key from the patch.
     *
     * @return null on success, otherwise what was wrong
     */
    public static String set(ResourceLocation id, String path, JsonElement value) {
        String[] parts = path.split("[.]");
        if (parts.length == 0 || !EDITABLE.contains(parts[0])) return "\"" + path + "\" cannot be edited here";

        JsonObject patch = read(id);
        JsonObject cursor = patch;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement next = cursor.get(parts[i]);
            if (next == null || !next.isJsonObject()) {
                next = new JsonObject();
                cursor.add(parts[i], next);
            }
            cursor = next.getAsJsonObject();
        }
        if (value == null || value.isJsonNull()) cursor.remove(parts[parts.length - 1]);
        else cursor.add(parts[parts.length - 1], value);

        try {
            write(id, patch);
        } catch (IOException e) {
            return "could not write the patch: " + e.getMessage();
        }
        return null;
    }

    /** Removes every server-side edit of a body. */
    public static String reset(ResourceLocation id) {
        try {
            write(id, new JsonObject());
        } catch (IOException e) {
            return "could not remove the patch: " + e.getMessage();
        }
        return null;
    }
}
