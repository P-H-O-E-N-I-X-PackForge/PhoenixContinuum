package net.phoenix.continuum.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.PdimWorldgen;

import com.mojang.serialization.JsonOps;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class PdimWorldgenGameTests {

    private static void accepts(GameTestHelper helper, PdimWorldgen.Spec spec, String what) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var parsed = LevelStem.CODEC.parse(ops, spec.toDimensionJson());
        helper.assertTrue(parsed.result().isPresent(),
                what + " should be a dimension the game can load: " + parsed.error().map(e -> e.message()).orElse(""));
    }

    @GameTest(template = "gametest_empty")
    public static void everyWorldgenHelperWritesADimensionTheGameCanLoad(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "gametest_worldgen");
        try {
            var spec = PdimWorldgen.of(id);
            helper.assertTrue(spec.isVoid() && spec.arrival() == PdimWorldgen.Arrival.PLATFORM,
                    "a body with no worldgen is void with the pad");
            accepts(helper, spec, "void");

            spec.flat("minecraft:plains", "minecraft:bedrock*1", "minecraft:stone*60", "minecraft:grass_block");
            helper.assertTrue(!spec.isVoid() && spec.arrival() == PdimWorldgen.Arrival.SURFACE,
                    "terrain means arriving on the ground");
            accepts(helper, spec, "flat");

            spec.noise("minecraft:overworld", "minecraft:plains");
            accepts(helper, spec, "noise with one biome");

            spec.overworldLike();
            accepts(helper, spec, "overworld like");

            spec.arriveAt(100);
            helper.assertTrue(spec.arrival() == PdimWorldgen.Arrival.FIXED && spec.fixedY() == 100, "fixed height");
            spec.arriveOnPlatform();
            helper.assertTrue(spec.arrival() == PdimWorldgen.Arrival.PLATFORM, "the pad can be forced on");

            spec.voidWorld();
            helper.assertTrue(spec.isVoid(), "and back to void");
            helper.succeed();
        } finally {
            PdimWorldgen.remove(id);
        }
    }

    @GameTest(template = "gametest_empty")
    public static void rulesFilesAndAutoResolveFromTheBody(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "gametest_rules");
        try {
            var bodies = net.phoenix.continuum.data.ContinuumData.allBodies();
            var rock = bodies.stream().filter(b -> b.type() == net.phoenix.continuum.data.ContinuumBody.Type.PLANET &&
                    b.params() != null && !b.params().gasGiant()).findFirst().orElse(null);
            helper.assertTrue(rock != null, "there should be a rocky planet to test with");

            // a rule from a file-format spec, for every planet
            var json = com.google.gson.JsonParser.parseString(
                    "{\"match\":{\"type\":\"planet\"},\"type\":\"flat\",\"biome\":\"minecraft:plains\"," +
                            "\"layers\":[\"minecraft:stone*10\"],\"plot_structure\":\"phoenix_continuum:landing\"}")
                    .getAsJsonObject();
            PdimWorldgen.clear();
            net.phoenix.continuum.pdim.PdimWorldgenFiles.apply(json);
            helper.assertTrue(PdimWorldgen.ruleCount() == 1, "the file made one rule");
            var viaRule = PdimWorldgen.resolve(rock.id(), rock);
            helper.assertTrue(!viaRule.isVoid() && viaRule.plotStructure() != null, "the rule applies to a planet");
            accepts(helper, viaRule, "the rule's flat world");

            // a spec of its own beats the rules
            PdimWorldgen.of(rock.id()).voidWorld();
            helper.assertTrue(PdimWorldgen.resolve(rock.id(), rock).isVoid(), "its own spec wins over a rule");

            // auto: rocky worlds get terrain, and the generator is one the game can load
            PdimWorldgen.clear();
            PdimWorldgen.of(rock.id()).auto();
            var auto = PdimWorldgen.resolve(rock.id(), rock);
            helper.assertTrue(!auto.isVoid() && auto.arrival() == PdimWorldgen.Arrival.SURFACE,
                    "a rocky planet gets terrain");
            accepts(helper, auto, "auto");
            helper.assertTrue(PdimWorldgen.get(rock.id()).isAuto(), "resolving does not change the stored spec");
            helper.succeed();
        } finally {
            PdimWorldgen.clear();
        }
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 400)
    public static void aDimensionIsMadeWhileTheGameRunsWithInfiniverse(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(net.phoenix.continuum.pdim.PdimRuntime.available(), "Infiniverse is in the dev runtime");
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "gametest_runtime_world");
        try {
            PdimWorldgen.of(id).flat("minecraft:plains", "minecraft:bedrock", "minecraft:stone*20");
            var level = net.phoenix.continuum.pdim.PdimRuntime.level(server, id);
            helper.assertTrue(level != null, "the dimension should be made on the spot");
            helper.assertTrue(level.dimension().equals(net.phoenix.continuum.pdim.PdimDimensions.keyFor(id)),
                    "under the key for the body");
            var again = net.phoenix.continuum.pdim.PdimRuntime.level(server, id);
            helper.assertTrue(again == level, "asking again gives the same level");
            var chunk = level.getChunk(net.minecraft.core.BlockPos.ZERO);
            helper.assertTrue(chunk != null &&
                    !level.getBlockState(new net.minecraft.core.BlockPos(0, level.getMinBuildHeight() + 5, 0)).isAir(),
                    "the flat world has ground to stand on");
            net.phoenix.continuum.pdim.PdimRuntime.unload(server, id);
            helper.succeed();
        } finally {
            PdimWorldgen.remove(id);
        }
    }
}
