package net.phoenix.continuum.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.data.ContentCreator;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumPackFolder;
import net.phoenix.continuum.data.SystemGenerator;
import net.phoenix.continuum.data.TuningPatches;
import net.phoenix.continuum.pdim.PdimDimensions;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.file.Files;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class GeneratedWorldGameTests {

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), command);
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 100)
    public static void aGeneratedWorldFindsItsBodyFromItsDimension(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        run(server, "continuumadmin generate 10 42");
        ContinuumBody star = ContinuumData.allBodies().stream()
                .filter(b -> b.id().getNamespace().equals(SystemGenerator.NAMESPACE)).findFirst().orElse(null);
        helper.assertTrue(star != null, "there should be a generated body");
        ResourceLocation found = PdimDimensions.bodyOf(PdimDimensions.keyFor(star.id()));
        helper.assertTrue(star.id().equals(found), "the dimension of " + star.id() + " should lead back to it, led to "
                + found);
        run(server, "continuumadmin generate clear");
        helper.succeed();
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void tuningAGeneratedStarIntoABlackHoleChangesItsBody(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        run(server, "continuumadmin generate 20 5");
        ContinuumBody star = ContinuumData.allBodies().stream()
                .filter(b -> b.id().getNamespace().equals(SystemGenerator.NAMESPACE) && b.isCentral()
                        && b.type() == ContinuumBody.Type.STAR).findFirst().orElse(null);
        helper.assertTrue(star != null, "there should be a generated star");
        try {
            String error = TuningPatches.set(star.system(), "star_kind", new JsonPrimitive("black_hole"), true);
            helper.assertTrue(error == null, "the patch should be accepted: " + error);
            ContinuumData.reloadNow(server);
            var changed = ContinuumData.body(star.id());
            helper.assertTrue(changed != null && changed.type() == ContinuumBody.Type.BLACK_HOLE,
                    "its star body should now be a black hole, is " + (changed == null ? "gone" : changed.type()));
            helper.assertTrue(ContinuumData.system(star.system()).hasHole(), "the system should be a black hole system");
        } finally {
            TuningPatches.reset(star.system(), true);
            ContinuumData.reloadNow(server);
        }
        var back = ContinuumData.body(star.id());
        helper.assertTrue(back != null && back.type() == ContinuumBody.Type.STAR, "without the patch it is a star again");
        run(server, "continuumadmin generate clear");
        helper.succeed();
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void aBodyMadeInAGeneratedSystemLoadsAndIsListed(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        run(server, "continuumadmin generate 5 9");
        var system = ContinuumData.systems().stream()
                .filter(sys -> sys.id().getNamespace().equals(SystemGenerator.NAMESPACE)).findFirst().orElse(null);
        
        var template = ContinuumData.allBodies().stream().filter(b -> b.type() == ContinuumBody.Type.PLANET
                && b.id().getNamespace().equals(SystemGenerator.NAMESPACE)).findFirst().orElse(null);
        helper.assertTrue(system != null && template != null, "a generated system and a template to start from");

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "gametest_tuner_made");
        JsonObject orbit = new JsonObject();
        orbit.addProperty("radius_au", 2.5);
        orbit.addProperty("period_days", 500);
        orbit.addProperty("phase", 0.3);
        JsonObject surface = new JsonObject();
        surface.addProperty("seed", 77);
        JsonObject def = new JsonObject();
        def.addProperty("inherits", template.id().toString());
        def.addProperty("system", system.id().toString());
        def.addProperty("name", "Tuner Made");
        def.addProperty("description", "Created in game.");
        def.addProperty("type", "planet");
        def.addProperty("initial_stage", "detected");
        def.add("orbit", orbit);
        def.add("surface", surface);
        var folder = ContinuumPackFolder.folder();
        var file = folder.resolve("data").resolve("phoenix_continuum").resolve("continuum").resolve("bodies")
                .resolve("gametest_tuner_made.json");
        var dimension = folder.resolve("data").resolve("phoenix_continuum").resolve("dimension").resolve("pdim")
                .resolve("gametest_tuner_made.json");
        try {
            var result = ContentCreator.createBody(server, id, def);
            helper.assertTrue(result.ok(), "creating should work: " + result.message());
            helper.assertTrue(ContinuumData.body(id) != null, "the body should be loaded");
            var source = server.createCommandSourceStack().withPermission(4);
            var parse = server.getCommands().getDispatcher().parse("continuumadmin missions send ", source);
            var list = server.getCommands().getDispatcher().getCompletionSuggestions(parse).get().getList().stream()
                    .map(s -> s.getText()).toList();
            helper.assertTrue(list.contains(id.toString()), "the missions send list should offer the new body");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dimension);
            run(server, "continuumadmin generate clear");
            ContinuumData.reloadNow(server);
        }
        helper.succeed();
    }
}
