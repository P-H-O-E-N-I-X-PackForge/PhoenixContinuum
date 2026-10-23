package net.phoenix.continuum.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.block.LaunchPadBlockEntity;
import net.phoenix.continuum.common.ContinuumMissions;
import net.phoenix.continuum.common.Mission;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.integration.gtceu.GregTech;
import net.phoenix.continuum.machine.LaunchSites;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class BasicPadGameTests {

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void padTierEnergyAndLaunchChecks(GameTestHelper helper) {
        if (GregTech.loaded()) {
            helper.succeed();
            return;
        }

        BlockPos below = new BlockPos(1, 1, 1);
        BlockPos pad = below.above();
        helper.setBlock(below, Blocks.DIRT);
        helper.setBlock(pad, ContinuumRegistry.LAUNCH_PAD.get());

        var entity = helper.getBlockEntity(pad);
        helper.assertTrue(entity instanceof LaunchPadBlockEntity, "the pad should have its block entity");
        LaunchPadBlockEntity block = (LaunchPadBlockEntity) entity;
        helper.assertTrue(LaunchSites.at(helper.getLevel(), helper.absolutePos(pad)) == block,
                "the pad should be found as the launch site");
        helper.assertTrue(block.padTier() == 0, "a pad on dirt is tier 0, is " + block.padTier());

        helper.setBlock(below, Blocks.IRON_BLOCK);
        helper.assertTrue(block.padTier() == 1, "iron under the pad makes it tier 1, is " + block.padTier());
        helper.setBlock(below, Blocks.NETHERITE_BLOCK);
        helper.assertTrue(block.padTier() == 4, "netherite under the pad makes it tier 4, is " + block.padTier());

        var energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(pad), null);
        helper.assertTrue(energy != null, "the pad should take Forge Energy");
        helper.assertTrue(energy.receiveEnergy(1000, false) > 0, "the pad should accept energy");
        helper.assertTrue(block.storedEnergy() > 0, "the energy should be stored");
        long stored = block.storedEnergy();
        helper.assertTrue(block.drainEnergy(stored), "draining what is stored should work");
        helper.assertTrue(block.storedEnergy() == 0, "the pad should be empty after draining");
        helper.assertTrue(!block.drainEnergy(1), "an empty pad cannot pay");

        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var destination = ContinuumData.allBodies().stream().filter(b -> b.transientInfo() == null).findFirst()
                .orElse(null);
        helper.assertTrue(destination != null, "there should be a body to launch to");
        player.setPos(helper.absolutePos(pad).getX() + 0.5, helper.absolutePos(pad).getY() + 1,
                helper.absolutePos(pad).getZ() + 0.5);
        String error = ContinuumMissions.launchMany(player, destination.id(), helper.absolutePos(pad),
                Mission.Type.SURVEY, 0, 1);
        helper.assertTrue(error != null, "an unpowered pad should refuse to launch");
        helper.assertTrue(!error.contains("GregTech") && !error.contains("Launch Complex"),
                "the refusal should not talk about GregTech: " + error);
        helper.succeed();
    }
}
