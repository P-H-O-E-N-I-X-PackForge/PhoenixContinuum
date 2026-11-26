package net.phoenix.continuum.data;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ContentCreator {

    private static final String PDIM_TEMPLATE = """
            {
              "type": "phoenix_continuum:pdim",
              "generator": {
                "type": "minecraft:flat",
                "settings": {
                  "biome": "minecraft:the_void",
                  "features": false,
                  "lakes": false,
                  "layers": [],
                  "structure_overrides": []
                }
              }
            }
            """;

    private ContentCreator() {}

    public record Result(boolean ok, String message) {

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    private static boolean safe(ResourceLocation id) {
        for (String part : id.getPath().split("/")) {
            if (part.isEmpty() || part.startsWith(".")) return false;
        }
        return true;
    }

    private static Path dataFile(String kind, ResourceLocation id) {
        return ContinuumPackFolder.folder().resolve("data").resolve(id.getNamespace()).resolve("continuum")
                .resolve(kind).resolve(id.getPath() + ".json");
    }

    public static Result createSystem(MinecraftServer server, ResourceLocation id, JsonObject def) {
        if (!safe(id)) return Result.fail(ContinuumLang.enc("phoenix_continuum.content_creator.id_not_allowed"));
        if (ContinuumData.system(id) != null) return Result.fail(ContinuumLang.enc("phoenix_continuum.content_creator.system_exists", id));
        try {
            write(dataFile("systems", id), def);
        } catch (IOException e) {
            return Result.fail(ContinuumLang.enc("phoenix_continuum.content_creator.write_failed", e.getMessage()));
        }
        ContinuumData.reloadNow(server);
        return new Result(true, ContinuumLang.enc("phoenix_continuum.content_creator.created_system", id));
    }

    public static Result createBody(MinecraftServer server, ResourceLocation id, JsonObject def) {
        if (!safe(id)) return Result.fail(ContinuumLang.enc("phoenix_continuum.content_creator.id_not_allowed"));
        if (ContinuumData.body(id) != null) return Result.fail(ContinuumLang.enc("phoenix_continuum.content_creator.body_exists", id));

        JsonElement system = def.get("system");
        ResourceLocation systemId = system != null && system.isJsonPrimitive() ?
                ResourceLocation.tryParse(system.getAsString()) : null;
        if (systemId == null || ContinuumData.system(systemId) == null) {
            return Result.fail(ContinuumLang.enc("phoenix_continuum.content_creator.no_system"));
        }
        JsonElement parent = def.get("parent");
        if (parent != null && parent.isJsonPrimitive()) {
            ResourceLocation parentId = ResourceLocation.tryParse(parent.getAsString());
            if (parentId == null || ContinuumData.body(parentId) == null) {
                return Result.fail(ContinuumLang.enc("phoenix_continuum.content_creator.no_parent"));
            }
        }

        try {
            write(dataFile("bodies", id), def);
        } catch (IOException e) {
            return Result.fail(ContinuumLang.enc("phoenix_continuum.content_creator.write_failed", e.getMessage()));
        }

        boolean needsRestart = false;
        ResourceLocation dimension = new ResourceLocation("phoenix_continuum",
                "pdim/" + id.getPath());
        var stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (!stems.containsKey(dimension) && !net.phoenix.continuum.pdim.PdimRuntime.available()) {
            try {
                Path file = ContinuumPackFolder.folder().resolve("data").resolve("phoenix_continuum")
                        .resolve("dimension").resolve("pdim").resolve(id.getPath() + ".json");
                Files.createDirectories(file.getParent());
                Files.writeString(file, PDIM_TEMPLATE);
                needsRestart = true;
            } catch (IOException ignored) {
                
            }
        }

        ContinuumReport report = ContinuumData.reloadNow(server);
        if (ContinuumData.body(id) == null) {
            
            StringBuilder why = new StringBuilder();
            for (ContinuumReport.Issue issue : report.issues()) {
                if (issue.toString().contains(id.toString())) why.append(' ').append(issue);
            }
            return Result.fail(why.length() == 0 ?
                    ContinuumLang.enc("phoenix_continuum.content_creator.not_loaded_hint", id, id.getNamespace()) :
                    ContinuumLang.enc("phoenix_continuum.content_creator.not_loaded", id, why.toString()));
        }
        return new Result(true, ContinuumLang.enc(needsRestart ? "phoenix_continuum.content_creator.created_body_restart" : "phoenix_continuum.content_creator.created_body", id));
    }

    private static void write(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json));
    }
}
