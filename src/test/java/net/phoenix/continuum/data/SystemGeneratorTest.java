package net.phoenix.continuum.data;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemGeneratorTest {

    private static final SystemGenerator.ContinuumSystemView HOME = SystemGenerator.ContinuumSystemView.ORIGIN;

    private static String flatten(SystemGenerator.Result result) {
        return result.systems().toString() + result.bodies().toString();
    }

    @Test
    void sameSeedGivesTheSameGalaxy() {
        assertEquals(flatten(SystemGenerator.generate(12345L, 12, HOME)),
                flatten(SystemGenerator.generate(12345L, 12, HOME)));
    }

    @Test
    void differentSeedsGiveDifferentGalaxies() {
        assertNotEquals(flatten(SystemGenerator.generate(1L, 12, HOME)), flatten(SystemGenerator.generate(2L, 12, HOME)));
    }

    @Test
    void everyGeneratedFileParsesAndPassesTheValidator() {
        for (long seed : new long[] { 0L, 1L, -987654321L, 424242L }) {
            var result = SystemGenerator.generate(seed, 40, HOME);
            assertEquals(40, result.systems().size());

            ContinuumReport report = new ContinuumReport();
            Map<ResourceLocation, ContinuumSystem> systems = new HashMap<>();
            Map<ResourceLocation, ContinuumBody> bodies = new HashMap<>();
            result.systems().forEach((id, json) -> {
                ContinuumValidator.checkShape("system", id, json, report);
                systems.put(id, ContinuumData.parseSystem(id, json));
            });
            result.bodies().forEach((id, json) -> {
                ContinuumValidator.checkShape("body", id, json, report);
                bodies.put(id, ContinuumData.parseBody(id, json));
            });
            assertEquals(0, report.errors(), report.issues().toString());

            for (var body : bodies.values()) {
                assertNotNull(systems.get(body.system()), "body " + body.id() + " names a system that was not generated");
                if (body.isMoon()) {
                    assertNotNull(bodies.get(body.parent()), "moon " + body.id() + " has no parent");
                    assertFalse(bodies.get(body.parent()).isMoon());
                }
                assertTrue(body.orbitAu() > 0.0f);
                assertTrue(body.periodDays() > 0.0f);
                assertFalse(body.yields().isEmpty(), "body " + body.id() + " has nothing to mine");
            }
            for (var system : systems.values()) {
                assertTrue(bodies.values().stream().anyMatch(b -> b.system().equals(system.id()) && !b.isMoon()),
                        "system " + system.id() + " has no planet");
            }
        }
    }

    @Test
    void systemsKeepTheirDistanceFromEachOtherAndFromHome() {
        var result = SystemGenerator.generate(777L, 30, HOME);
        var positions = result.systems().values().stream().map(SystemGeneratorTest::position).toList();
        for (int i = 0; i < positions.size(); i++) {
            float[] a = positions.get(i);
            assertTrue(Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]) >= 4.5, "too close to home");
            for (int j = i + 1; j < positions.size(); j++) {
                float[] b = positions.get(j);
                double d = Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
                assertTrue(d >= 2.0, "systems " + i + " and " + j + " are only " + d + " apart");
            }
        }
    }

    private static float[] position(JsonObject system) {
        var galaxy = system.getAsJsonArray("galaxy");
        return new float[] { galaxy.get(0).getAsFloat(), galaxy.get(1).getAsFloat(), galaxy.get(2).getAsFloat() };
    }
}
