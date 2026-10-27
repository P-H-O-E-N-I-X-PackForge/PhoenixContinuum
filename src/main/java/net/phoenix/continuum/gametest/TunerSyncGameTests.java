package net.phoenix.continuum.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumTuner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class TunerSyncGameTests {

    @GameTest(template = "gametest_empty", timeoutTicks = 100)
    public static void pushedEditsStayUntilTheServerCopyHasThemThenTheServerWins(GameTestHelper helper) {
        var planet = ContinuumData.allBodies().stream().filter(b -> b.type() == ContinuumBody.Type.PLANET).findFirst()
                .orElse(null);
        helper.assertTrue(planet != null, "there should be a planet to tune");
        var id = planet.id();
        var before = ContinuumData.snapshot();
        try {
            ContinuumTuner.set(id, "surface.seed", new JsonPrimitive(4242));
            helper.assertTrue(ContinuumTuner.isTuned(id), "a local edit tunes the body");
            Map<String, com.google.gson.JsonElement> leaves = new LinkedHashMap<>(ContinuumTuner.leaves(id));
            ContinuumTuner.pushed(id, leaves);

            // the server's copy arrives without the edit (it has not applied it yet): the edit stays on screen
            ContinuumData.acceptSync(before);
            helper.assertTrue(ContinuumTuner.isTuned(id), "the pushed edit stays until the server has it");
            helper.assertTrue(ContinuumData.body(id).params().seed() == 4242.0f, "and it is still shown");

            // the next copy has it: the server's word counts now, and the local copy is dropped
            Map<net.minecraft.resources.ResourceLocation, String> bodies = new LinkedHashMap<>(before.bodies());
            JsonObject json = JsonParser.parseString(bodies.get(id)).getAsJsonObject();
            JsonObject surface = json.has("surface") ? json.getAsJsonObject("surface") : new JsonObject();
            surface.addProperty("seed", 4242);
            json.add("surface", surface);
            bodies.put(id, json.toString());
            ContinuumData.acceptSync(new ContinuumData.Snapshot(before.systems(), bodies));
            helper.assertTrue(!ContinuumTuner.isTuned(id), "the local copy is dropped once the server has the edit");
            helper.assertTrue(ContinuumData.body(id).params().seed() == 4242.0f, "the value is the server's now");
        } finally {
            ContinuumTuner.reset(id);
            ContinuumData.acceptSync(before);
        }
        helper.succeed();
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 100)
    public static void aCentralBodyIsTheSizeItsSystemDrawsItAndVisitorsAreNamedForWhatTheyAre(GameTestHelper helper) {
        for (ContinuumBody body : ContinuumData.allBodies()) {
            if (body.isCentral() && (body.type() == ContinuumBody.Type.STAR ||
                    body.type() == ContinuumBody.Type.BLACK_HOLE)) {
                var system = ContinuumData.system(body.system());
                helper.assertTrue(system != null && Math.abs(body.radius() - system.starRadius()) < 1.0e-3f,
                        "the star body of " + body.system() + " should be " + (system == null ? "?" :
                                system.starRadius()) + " big, is " + body.radius());
            }
            if (body.transientInfo() != null) {
                helper.assertTrue(!body.kindLabel().equals("Planet"), body.id() + " is a visitor, not a planet: "
                        + body.kindLabel());
            }
        }
        helper.succeed();
    }
}
