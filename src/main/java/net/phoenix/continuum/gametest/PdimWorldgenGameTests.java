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
        ResourceLocation rockId = null;
        try {
            var bodies = net.phoenix.continuum.data.ContinuumData.allBodies();
            var rock = bodies.stream().filter(b -> b.type() == net.phoenix.continuum.data.ContinuumBody.Type.PLANET &&
                    b.params() != null && !b.params().gasGiant()).findFirst().orElse(null);
            helper.assertTrue(rock != null, "there should be a rocky planet to test with");
            rockId = rock.id();

            // a rule from a file-format spec, for every planet
            var json = com.google.gson.JsonParser.parseString(
                    "{\"match\":{\"type\":\"planet\"},\"type\":\"flat\",\"biome\":\"minecraft:plains\"," +
                            "\"layers\":[\"minecraft:stone*10\"],\"plot_structure\":\"phoenix_continuum:landing\"}")
                    .getAsJsonObject();
            PdimWorldgen.clearRules();
            PdimWorldgen.remove(rock.id());
            net.phoenix.continuum.pdim.PdimWorldgenFiles.apply(json);
            helper.assertTrue(PdimWorldgen.ruleCount() == 1, "the file made one rule");
            var viaRule = PdimWorldgen.resolve(rock.id(), rock);
            helper.assertTrue(!viaRule.isVoid() && viaRule.plotStructure() != null, "the rule applies to a planet");
            accepts(helper, viaRule, "the rule's flat world");

            // a spec of its own beats the rules
            PdimWorldgen.of(rock.id()).voidWorld();
            helper.assertTrue(PdimWorldgen.resolve(rock.id(), rock).isVoid(), "its own spec wins over a rule");

            // auto: rocky worlds get terrain, and the generator is one the game can load
            PdimWorldgen.clearRules();
            PdimWorldgen.remove(rock.id());
            PdimWorldgen.of(rock.id()).auto();
            var auto = PdimWorldgen.resolve(rock.id(), rock);
            helper.assertTrue(!auto.isVoid() && auto.arrival() == PdimWorldgen.Arrival.SURFACE,
                    "a rocky planet gets terrain");
            accepts(helper, auto, "auto");
            helper.assertTrue(PdimWorldgen.get(rock.id()).isAuto(), "resolving does not change the stored spec");
            helper.succeed();
        } finally {
            PdimWorldgen.clearRules();
            PdimWorldgen.remove(id);
            PdimWorldgen.remove(rockId);
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

    @GameTest(template = "gametest_empty")
    public static void rulesAreKeptAndPlotsMapBackToTheirNumber(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "gametest_world_rules");
        try {
            PdimWorldgen.of(id).mobs(false).fallDamage(true).requireOxygen("#mypack:space_helmets").plotRadius(300)
                    .protectPlots(true).gtOres("minecraft:overworld");
            var spec = PdimWorldgen.resolve(id, null);
            helper.assertTrue(!spec.mobs() && spec.fallDamage() && "#mypack:space_helmets".equals(spec.oxygenGear()) &&
                    spec.plotRadius() == 300 && spec.protectPlots() && spec.gtOresFrom() != null,
                    "every rule should survive resolving");

            for (int plot : new int[] { 0, 1, 31, 32, 33, 100, 500 }) {
                var origin = net.phoenix.continuum.pdim.PdimDimensions.plotOrigin(plot);
                helper.assertTrue(net.phoenix.continuum.pdim.PdimDimensions.plotAt(origin) == plot,
                        "plot " + plot + " should map back from its origin");
                helper.assertTrue(net.phoenix.continuum.pdim.PdimDimensions.plotAt(origin.offset(200, 0, -200)) == plot,
                        "and from a spot inside it");
            }

            var json = com.google.gson.JsonParser.parseString("{\"body\":\"" + id + "\",\"type\":\"void\"," +
                    "\"mobs\":false,\"oxygen\":\"mypack:helmet\",\"plot_radius\":50,\"protect_plots\":true}")
                    .getAsJsonObject();
            net.phoenix.continuum.pdim.PdimWorldgenFiles.apply(json);
            var fromFile = PdimWorldgen.resolve(id, null);
            helper.assertTrue(!fromFile.mobs() && "mypack:helmet".equals(fromFile.oxygenGear()) &&
                    fromFile.plotRadius() == 50, "the file format sets the rules too");
            helper.succeed();
        } finally {
            PdimWorldgen.remove(id);
        }
    }

    @GameTest(template = "gametest_empty")
    public static void gregTechOresGenerateInAPersonalDimensionLikeItsSource(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "gametest_ore_world");
        try {
            PdimWorldgen.of(id).gtOres("minecraft:overworld");
            net.phoenix.continuum.pdim.PdimRuntime.mirrorOres(server, id);
            var key = net.phoenix.continuum.pdim.PdimDimensions.keyFor(id);
            var veins = server.registryAccess().registryOrThrow(com.gregtechceu.gtceu.api.registry.GTRegistries.Keys.ORE_VEIN);
            boolean any = false;
            boolean overworldOnes = false;
            for (var vein : veins) {
                var filter = vein.dimensionFilter();
                if (filter != null && filter.contains(net.minecraft.world.level.Level.OVERWORLD)) {
                    overworldOnes = true;
                    any |= filter.contains(key);
                }
            }
            helper.assertTrue(!overworldOnes || any, "the overworld's ore veins should now list the personal dimension");
            helper.succeed();
        } finally {
            PdimWorldgen.remove(id);
        }
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 400)
    public static void aPersonalDimensionCanHaveItsOwnWeather(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "gametest_weather_world");
        PdimWorldgen.of(id).flat("minecraft:plains", "minecraft:bedrock", "minecraft:stone*20").weather("storm");
        var level = net.phoenix.continuum.pdim.PdimRuntime.level(server, id);
        helper.assertTrue(level != null, "the dimension should be made");
        helper.assertTrue(level.rainLevel < 0.5f, "it starts dry");
        // the overworld is clear, so only the dimension's own weather step can make this rain: the mixin has to be in
        helper.runAfterDelay(160, () -> {
            try {
                helper.assertTrue(level.rainLevel >= 0.99f, "a storm world should be raining by now, was " + level.rainLevel);
                helper.assertTrue(level.thunderLevel >= 0.99f, "and thundering");
                helper.assertTrue(level.isThundering(), "isThundering follows");
                PdimWorldgen.of(id).weather("clear");
                helper.runAfterDelay(160, () -> {
                    try {
                        helper.assertTrue(level.rainLevel <= 0.01f, "then it clears, was " + level.rainLevel);
                        helper.succeed();
                    } finally {
                        net.phoenix.continuum.pdim.PdimRuntime.unload(server, id);
                        PdimWorldgen.remove(id);
                    }
                });
            } catch (RuntimeException e) {
                net.phoenix.continuum.pdim.PdimRuntime.unload(server, id);
                PdimWorldgen.remove(id);
                throw e;
            }
        });
    }

    @GameTest(template = "gametest_empty")
    public static void plotsCanBeTurnedOffForABodyOrForEverything(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "gametest_no_plots");
        var config = net.phoenix.continuum.config.ContinuumConfigs.INSTANCE;
        boolean before = config.personalPlots;
        try {
            var spec = PdimWorldgen.of(id).protectPlots(true).plotRadius(100);
            helper.assertTrue(net.phoenix.continuum.pdim.PdimRules.plotsOn(spec), "plots are on by default");
            spec.plots(false).spawnAt(10, 70, -20);
            helper.assertTrue(!net.phoenix.continuum.pdim.PdimRules.plotsOn(spec), "a spec can turn them off");
            helper.assertTrue(spec.spawn()[0] == 10 && spec.spawn()[2] == -20, "and set where everybody arrives");
            spec.plots(true);
            config.personalPlots = false;
            helper.assertTrue(!net.phoenix.continuum.pdim.PdimRules.plotsOn(spec), "the config turns them off for all");

            var json = com.google.gson.JsonParser.parseString("{\"body\":\"" + id + "\",\"plots\":false," +
                    "\"spawn\":[1,2,3]}").getAsJsonObject();
            net.phoenix.continuum.pdim.PdimWorldgenFiles.apply(json);
            config.personalPlots = true;
            var fromFile = PdimWorldgen.resolve(id, null);
            helper.assertTrue(!fromFile.plots() && fromFile.spawn()[1] == 2, "the file format sets both");
            helper.succeed();
        } finally {
            config.personalPlots = before;
            PdimWorldgen.remove(id);
        }
    }
}
