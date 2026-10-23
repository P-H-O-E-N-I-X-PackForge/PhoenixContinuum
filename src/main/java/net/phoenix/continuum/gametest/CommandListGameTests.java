package net.phoenix.continuum.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.data.ContentCreator;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumPackFolder;

import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.util.concurrent.ExecutionException;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommandListGameTests {

    private static java.util.List<String> complete(MinecraftServer server, String command) {
        var source = server.createCommandSourceStack().withPermission(4);
        var parse = server.getCommands().getDispatcher().parse(command, source);
        try {
            return server.getCommands().getDispatcher().getCompletionSuggestions(parse).get().getList().stream()
                    .map(s -> s.getText()).toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void bodiesMadeInGameShowInTheCommandListsAndCanBeSearchedByName(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        var system = ContinuumData.systems().stream().findFirst().orElse(null);
        helper.assertTrue(system != null, "there should be a system to put a body in");

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "gametest_created_world");
        JsonObject orbit = new JsonObject();
        orbit.addProperty("radius_au", 1.7);
        orbit.addProperty("period_days", 400);
        JsonObject def = new JsonObject();
        def.addProperty("system", system.id().toString());
        def.addProperty("name", "Zzyzx Prime");
        def.addProperty("type", "planet");
        def.add("orbit", orbit);
        def.addProperty("trip_minutes", 10);
        var file = ContinuumPackFolder.folder().resolve("data").resolve("phoenix_continuum").resolve("continuum")
                .resolve("bodies").resolve("gametest_created_world.json");
        var dimension = ContinuumPackFolder.folder().resolve("data").resolve("phoenix_continuum").resolve("dimension")
                .resolve("pdim").resolve("gametest_created_world.json");
        try {
            var result = ContentCreator.createBody(server, id, def);
            helper.assertTrue(result.ok(), "creating the body should work: " + result.message());
            helper.assertTrue(ContinuumData.body(id) != null, "the created body should be loaded straight away");
            helper.assertTrue(ContinuumData.allBodies().stream().anyMatch(b -> b.id().equals(id)),
                    "the created body should be in the list of bodies");

            helper.assertTrue(complete(server, "continuumadmin dump ").contains(id.toString()),
                    "the created body should be offered by the command list");
            helper.assertTrue(complete(server, "continuumadmin dump zzyz").contains(id.toString()),
                    "typing part of the name should find the body");
            helper.assertTrue(complete(server, "continuumadmin dump gametest_created").contains(id.toString()),
                    "typing part of the id should find the body");
            helper.assertTrue(!complete(server, "continuumadmin dump zzyz").contains(
                    ContinuumData.allBodies().stream().filter(b -> !b.id().equals(id)).findFirst().get().id()
                            .toString()),
                    "a search should narrow the list");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dimension);
            ContinuumData.reloadNow(server);
        }
        helper.succeed();
    }
}
