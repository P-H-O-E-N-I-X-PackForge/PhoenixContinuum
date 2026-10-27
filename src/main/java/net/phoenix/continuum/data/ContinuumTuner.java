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

    private static final Map<ResourceLocation, Map<String, JsonElement>> PENDING = new HashMap<>();
    private static final Map<ResourceLocation, Long> PENDING_SINCE = new HashMap<>();

    /** These edits were sent to the server: they stay on screen until the server's own copy has them. */
    public static void pushed(ResourceLocation id, Map<String, JsonElement> leaves) {
        PENDING.put(id, new java.util.LinkedHashMap<>(leaves));
        PENDING_SINCE.put(id, System.currentTimeMillis());
    }

    private static JsonElement at(JsonElement from, String path) {
        JsonElement cursor = from;
        for (String part : path.split("[.]")) {
            if (cursor == null) return null;
            if (cursor.isJsonObject()) {
                cursor = cursor.getAsJsonObject().get(part);
            } else if (cursor.isJsonArray()) {
                try {
                    int index = Integer.parseInt(part);
                    cursor = index >= 0 && index < cursor.getAsJsonArray().size() ?
                            cursor.getAsJsonArray().get(index) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return cursor;
    }

    /**
     * New data arrived from the server. A body whose pushed edits are now in it stops being tuned locally (the server's
     * word is the one that counts); one that is still waiting keeps them on top, for a few seconds at most.
     */
    public static void afterSync() {
        long now = System.currentTimeMillis();
        for (ResourceLocation id : new java.util.ArrayList<>(PENDING.keySet())) {
            JsonObject raw = ContinuumData.rawBody(id);
            boolean arrived = raw != null;
            if (arrived) {
                for (Map.Entry<String, JsonElement> leaf : PENDING.get(id).entrySet()) {
                    JsonElement have = at(raw, leaf.getKey());
                    if (have == null || !have.equals(leaf.getValue())) {
                        arrived = false;
                        break;
                    }
                }
            }
            if (arrived || now - PENDING_SINCE.getOrDefault(id, now) > 10_000L) {
                PENDING.remove(id);
                PENDING_SINCE.remove(id);
                TWEAKS.remove(id);
            }
        }
        reapply();
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
