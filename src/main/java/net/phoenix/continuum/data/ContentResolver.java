package net.phoenix.continuum.data;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves {@code "inherits"}: a body or system can name another one (any namespace) and only write what differs.
 * Objects are merged key by key, everything else (numbers, strings, arrays) is replaced, and a {@code null} deletes
 * the inherited key. {@code "abstract": true} marks a template that is never registered itself, and
 * {@code "disabled": true} removes a definition a lower layer made. Neither flag is inherited.
 */
final class ContentResolver {

    static final int SUPPORTED_FORMAT = 1;

    private ContentResolver() {}

    static Map<ResourceLocation, JsonObject> resolve(String kind, Map<ResourceLocation, JsonObject> raw,
                                                     ContinuumReport report) {
        Map<ResourceLocation, JsonObject> resolved = new HashMap<>();
        Set<ResourceLocation> failed = new LinkedHashSet<>();
        for (ResourceLocation id : raw.keySet()) resolveOne(kind, id, raw, resolved, failed, new ArrayList<>(), report);

        Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
        for (ResourceLocation id : raw.keySet()) {
            JsonObject own = raw.get(id);
            JsonObject result = resolved.get(id);
            if (result == null || failed.contains(id)) continue;
            if (flag(own, "abstract") || flag(own, "disabled")) continue;
            result.remove("abstract");
            result.remove("disabled");
            result.remove("inherits");
            out.put(id, result);
        }
        return out;
    }

    private static boolean flag(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsBoolean();
    }

    private static JsonObject resolveOne(String kind, ResourceLocation id, Map<ResourceLocation, JsonObject> raw,
                                         Map<ResourceLocation, JsonObject> resolved, Set<ResourceLocation> failed,
                                         List<ResourceLocation> stack, ContinuumReport report) {
        JsonObject done = resolved.get(id);
        if (done != null) return done;

        JsonObject own = raw.get(id);
        if (own == null) return null;

        if (stack.contains(id)) {
            StringBuilder chain = new StringBuilder();
            for (ResourceLocation step : stack) chain.append(step).append(" -> ");
            chain.append(id);
            report.error(kind + " " + id, "inheritance cycle: " + chain);
            failed.add(id);
            return null;
        }

        if (!own.has("inherits")) {
            resolved.put(id, own.deepCopy());
            return resolved.get(id);
        }

        stack.add(id);
        String parentName = own.get("inherits").isJsonPrimitive() ? own.get("inherits").getAsString() : "";
        ResourceLocation parentId = parentName.isEmpty() ? null : ResourceLocation.tryParse(
                parentName.contains(":") ? parentName : id.getNamespace() + ":" + parentName);
        if (parentId == null) {
            report.error(kind + " " + id, "\"inherits\" must be a resource id, got: " + own.get("inherits"));
            stack.remove(stack.size() - 1);
            failed.add(id);
            return null;
        }

        JsonObject parent = raw.containsKey(parentId) ?
                resolveOne(kind, parentId, raw, resolved, failed, stack, report) : null;
        stack.remove(stack.size() - 1);

        if (parent == null) {
            if (!raw.containsKey(parentId)) {
                report.error(kind + " " + id, "inherits " + parentId + " but no such " + kind + " exists");
            }
            failed.add(id);
            return null;
        }

        JsonObject merged = merge(parent, own);
        // flags belong to the definition that wrote them
        merged.remove("abstract");
        merged.remove("disabled");
        if (own.has("abstract")) merged.add("abstract", own.get("abstract"));
        if (own.has("disabled")) merged.add("disabled", own.get("disabled"));
        resolved.put(id, merged);
        return merged;
    }

    static JsonObject merge(JsonObject base, JsonObject over) {
        JsonObject out = base.deepCopy();
        for (Map.Entry<String, JsonElement> entry : over.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (key.equals("inherits")) continue;
            if (value.isJsonNull()) {
                out.remove(key);
            } else if (value.isJsonObject() && out.has(key) && out.get(key).isJsonObject()) {
                out.add(key, merge(out.getAsJsonObject(key), value.getAsJsonObject()));
            } else {
                out.add(key, value.deepCopy());
            }
        }
        return out;
    }
}
