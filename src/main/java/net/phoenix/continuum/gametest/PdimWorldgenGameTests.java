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
}
