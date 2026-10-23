package net.phoenix.continuum.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.block.LaunchPadBlockEntity;
import net.phoenix.continuum.common.ContinuumMissions;
import net.phoenix.continuum.common.Mission;
import net.phoenix.continuum.common.RocketStats;
import net.phoenix.continuum.common.Swarms;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.machine.PadTiers;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class SwarmMissionGameTests {

    private static int count(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /** The test world is kept between runs, so missions a test launched must not outlive it. */
    private static void clearMissions(MinecraftServer server, net.minecraft.world.entity.player.Player player) {
        var team = net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        var data = net.phoenix.continuum.common.ContinuumTeamData.get(server);
        for (var mission : new java.util.ArrayList<>(data.missions(team))) data.removeMission(team, mission.id);
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void swarmsAndShellsAreBuiltWithTheirOwnItems(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var cfg = ContinuumConfigs.INSTANCE;
        boolean oldRequire = cfg.requireMultiblockPad;
        cfg.requireMultiblockPad = false;
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),
                    "continuumadmin generate 30 11");
            ContinuumBody star = ContinuumData.allBodies().stream().filter(b -> b.type() == ContinuumBody.Type.STAR)
                    .findFirst().orElse(null);
            helper.assertTrue(star != null, "there should be a star to build at");

            var player = FakePlayerFactory.getMinecraft(helper.getLevel());
            clearMissions(server, player);
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
                    "continuumadmin discover all surveyed");

            BlockPos below = new BlockPos(1, 1, 1);
            BlockPos pad = below.above();
            helper.setBlock(below, Blocks.GOLD_BLOCK);
            helper.setBlock(pad, ContinuumRegistry.LAUNCH_PAD.get());
            BlockPos at = helper.absolutePos(pad);
            player.setPos(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);

            // a rocket that can fly to a star
            ItemStack rocket = ContinuumRegistry.ROCKET.asStack();
            RocketStats.setTier(rocket, RocketStats.MAX_TIER);
            RocketStats.setLevel(rocket, RocketStats.STELLAR_SHIELD, 1);
            player.getInventory().add(rocket);
            player.getInventory().add(new ItemStack(ContinuumRegistry.PROBE.get(), 16));
            if (PadTiers.basic()) ((LaunchPadBlockEntity) helper.getBlockEntity(pad)).chargeEnergy(300_000);

            // probes are not what a swarm is built from
            String error = ContinuumMissions.launchMany(player, star.id(), at, Mission.Type.STATION, 0, 1);
            helper.assertTrue(error != null && error.contains("swarm collector"),
                    "a swarm should ask for swarm collector pods, got: " + error);

            int collectors = Swarms.payloadPerLevel(star, false);
            player.getInventory().add(new ItemStack(ContinuumRegistry.SWARM_COLLECTOR.get(), collectors + 1));
            error = ContinuumMissions.launchMany(player, star.id(), at, Mission.Type.STATION, 0, 1);
            helper.assertTrue(error == null, "with the pods it should launch: " + error);
            helper.assertTrue(count(player, ContinuumRegistry.SWARM_COLLECTOR.get()) == 1,
                    "the launch should take " + collectors + " pods, " + count(player,
                            ContinuumRegistry.SWARM_COLLECTOR.get()) + " were left of " + (collectors + 1));
            helper.assertTrue(count(player, ContinuumRegistry.PROBE.get()) == 16, "no probes should be taken");

            // a shell wants its own segments
            ItemStack second = ContinuumRegistry.ROCKET.asStack();
            RocketStats.setTier(second, RocketStats.MAX_TIER);
            RocketStats.setLevel(second, RocketStats.STELLAR_SHIELD, 1);
            player.getInventory().add(second);
            if (PadTiers.basic()) ((LaunchPadBlockEntity) helper.getBlockEntity(pad)).chargeEnergy(300_000);
            error = ContinuumMissions.launchMany(player, star.id(), at, Mission.Type.SHELL, 0, 1);
            helper.assertTrue(error != null && error.contains("shell segment"),
                    "a shell should ask for shell segments, got: " + error);
            int segments = Swarms.payloadPerLevel(star, true);
            player.getInventory().add(new ItemStack(ContinuumRegistry.SHELL_SEGMENT.get(), segments));
            error = ContinuumMissions.launchMany(player, star.id(), at, Mission.Type.SHELL, 0, 1);
            helper.assertTrue(error == null, "with the segments it should launch: " + error);
            helper.assertTrue(count(player, ContinuumRegistry.SHELL_SEGMENT.get()) == 0, "the segments are carried");
        } finally {
            clearMissions(server, FakePlayerFactory.getMinecraft(helper.getLevel()));
            cfg.requireMultiblockPad = oldRequire;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),
                    "continuumadmin generate clear");
        }
        helper.succeed();
    }
}
