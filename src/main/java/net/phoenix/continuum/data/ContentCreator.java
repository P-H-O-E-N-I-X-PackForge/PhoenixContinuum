package net.phoenix.continuum.data;

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
        if (!safe(id)) return Result.fail("That id is not allowed.");
        if (ContinuumData.system(id) != null) return Result.fail("A system " + id + " already exists.");
        try {
            write(dataFile("systems", id), def);
        } catch (IOException e) {
            return Result.fail("Could not write the file: " + e.getMessage());
        }
        ContinuumData.reloadNow(server);
        return new Result(true, "Created the system " + id + ".");
    }

    public static Result createBody(MinecraftServer server, ResourceLocation id, JsonObject def) {
        if (!safe(id)) return Result.fail("That id is not allowed.");
        if (ContinuumData.body(id) != null) return Result.fail("A body " + id + " already exists.");

        JsonElement system = def.get("system");
        ResourceLocation systemId = system != null && system.isJsonPrimitive() ?
                ResourceLocation.tryParse(system.getAsString()) : null;
        if (systemId == null || ContinuumData.system(systemId) == null) {
            return Result.fail("Its system does not exist.");
        }
        JsonElement parent = def.get("parent");
        if (parent != null && parent.isJsonPrimitive()) {
            ResourceLocation parentId = ResourceLocation.tryParse(parent.getAsString());
            if (parentId == null || ContinuumData.body(parentId) == null) {
                return Result.fail("The body it orbits does not exist.");
            }
        }

        try {
            write(dataFile("bodies", id), def);
        } catch (IOException e) {
            return Result.fail("Could not write the file: " + e.getMessage());
        }

        boolean needsRestart = false;
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("phoenix_continuum",
                "pdim/" + id.getPath());
        var stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (!stems.containsKey(dimension)) {
            try {
                Path file = ContinuumPackFolder.folder().resolve("data").resolve("phoenix_continuum")
                        .resolve("dimension").resolve("pdim").resolve(id.getPath() + ".json");
                Files.createDirectories(file.getParent());
                Files.writeString(file, PDIM_TEMPLATE);
                needsRestart = true;
            } catch (IOException ignored) {
                
            }
        }

        ContinuumData.reloadNow(server);
        return new Result(true, "Created the body " + id + "." +
                (needsRestart ? " Its personal dimension appears after a restart." : ""));
    }

    private static void write(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json));
    }
}
