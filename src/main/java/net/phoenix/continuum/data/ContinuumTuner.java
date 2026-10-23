package net.phoenix.continuum.data;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public final class ContinuumTuner {

    private static final Map<ResourceLocation, JsonObject> TWEAKS = new HashMap<>();

    private ContinuumTuner() {}

    public static JsonObject merged(ResourceLocation id) {
        JsonObject base = ContinuumData.rawBody(id);
        if (base == null) return new JsonObject();
        JsonObject tweaks = TWEAKS.get(id);
        return tweaks == null ? base : ContentResolver.merge(base, tweaks);
    }

    public static boolean isTuned(ResourceLocation id) {
        return TWEAKS.containsKey(id);
    }

    public static void set(ResourceLocation id, String path, JsonElement value) {
        JsonObject tweaks = TWEAKS.computeIfAbsent(id, key -> new JsonObject());
        JsonObject cursor = tweaks;
        String[] parts = path.split("[.]");
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement next = cursor.get(parts[i]);
            if (next == null || !next.isJsonObject()) {
                next = new JsonObject();
                cursor.add(parts[i], next);
            }
            cursor = next.getAsJsonObject();
        }
        cursor.add(parts[parts.length - 1], value);
        apply(id);
    }

    public static Map<String, JsonElement> leaves(ResourceLocation id) {
        Map<String, JsonElement> out = new java.util.LinkedHashMap<>();
        JsonObject tweaks = TWEAKS.get(id);
        if (tweaks != null) collect("", tweaks, out);
        return out;
    }

    private static void collect(String prefix, JsonObject json, Map<String, JsonElement> out) {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue().isJsonObject()) collect(path, entry.getValue().getAsJsonObject(), out);
            else out.put(path, entry.getValue());
        }
    }

    public static void reset(ResourceLocation id) {
        TWEAKS.remove(id);
        apply(id);
    }

    public static void reapply() {
        for (ResourceLocation id : new java.util.ArrayList<>(TWEAKS.keySet())) apply(id);
    }

    private static void apply(ResourceLocation id) {
        JsonObject json = merged(id);
        if (json.size() == 0) return;
        try {
            ContinuumData.replaceBody(id, ContinuumData.parseBody(id, json));
        } catch (RuntimeException ignored) {
            
        }
    }
}
