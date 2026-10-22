package net.phoenix.continuum.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.ContinuumSwarmPower;
import net.phoenix.continuum.common.ContinuumTeamData;
import net.phoenix.continuum.common.Stations;
import net.phoenix.continuum.common.Swarms;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class SwarmGameTests {

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void swarmPowerReachesSinksAndStationsAreNotSlots(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),
                "continuumadmin generate 30 11");
        ContinuumBody star = ContinuumData.allBodies().stream().filter(Swarms::isSwarm).findFirst().orElse(null);
        helper.assertTrue(star != null, "the generated galaxy should have a star to build a swarm at");

        UUID team = UUID.randomUUID();
        var data = ContinuumTeamData.get(server);
        helper.assertTrue(data.stationCount(team) == 0, "a new team has no stations");
        helper.assertTrue(Swarms.maxLevel(star) == ContinuumConfigs.INSTANCE.swarmMaxLevel,
                "a swarm has the swarm maximum level");

        data.setStationLevel(team, star.id(), 5);
        helper.assertTrue(data.stationLevel(team, star.id()) == 5, "a swarm can pass the station maximum of "
                + Stations.MAX_LEVEL);
        helper.assertTrue(data.stationCount(team) == 0, "a swarm does not add a mission slot");

        long perTick = ContinuumSwarmPower.outputPerTick(server, team);
        helper.assertTrue(perTick > 0, "level 5 should produce power, produces " + perTick);

        AtomicLong received = new AtomicLong();
        ContinuumSwarmPower.Sink sink = amount -> {
            received.addAndGet(amount);
            return amount;
        };
        ContinuumSwarmPower.register(team, sink);
        Swarms.tick(server);
        ContinuumSwarmPower.unregister(team, sink);
        helper.assertTrue(received.get() == perTick * 20L,
                "the sink should get a second of power (" + perTick * 20L + "), got " + received.get());

        data.setStationLevel(team, star.id(), 0);
        helper.assertTrue(ContinuumSwarmPower.outputPerTick(server, team) == 0, "no swarm, no power");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),
                "continuumadmin generate clear");
        helper.succeed();
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 100)
    public static void capacitorKeepsWhatTheHatchesCannotTake(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        var storage = net.phoenix.continuum.common.SwarmStorage.get(server);
        storage.set(level, pos, 0);

        // the hatches take 300 of 1000 offered: the other 700 wait in the capacitor, and all 1000 count as kept
        long kept = net.phoenix.continuum.common.SwarmStorage.exchange(server, level, pos, 1000, available -> 300);
        helper.assertTrue(kept == 1000, "all of the swarm's power should be kept, was " + kept);
        helper.assertTrue(storage.get(level, pos) == 700, "700 should be stored, is " + storage.get(level, pos));

        // with no swarm power the capacitor keeps feeding the hatches
        kept = net.phoenix.continuum.common.SwarmStorage.exchange(server, level, pos, 0, available -> 500);
        helper.assertTrue(kept == 0, "nothing new was offered, kept " + kept);
        helper.assertTrue(storage.get(level, pos) == 200, "200 should be left, is " + storage.get(level, pos));
        storage.set(level, pos, 0);
        helper.succeed();
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 100)
    public static void blackHolesHostPenroseSwarmsAndTheCommandChangesLevels(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),
                "continuumadmin generate 40 11");
        ContinuumBody hole = ContinuumData.allBodies().stream().filter(Swarms::isPenrose).findFirst().orElse(null);
        helper.assertTrue(hole != null, "the generated galaxy should have a black hole to build a Penrose swarm at");
        helper.assertTrue(Swarms.maxLevel(hole) == ContinuumConfigs.INSTANCE.penroseMaxLevel,
                "a Penrose swarm has its own maximum level");

        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        var source = player.createCommandSourceStack().withPermission(4);
        UUID team = net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        var data = ContinuumTeamData.get(server);
        data.setStationLevel(team, hole.id(), 0);
        String id = hole.id().toString();
        server.getCommands().performPrefixedCommand(source, "continuumadmin swarm add " + id + " 3");
        helper.assertTrue(data.stationLevel(team, hole.id()) == 3, "add 3 should give level 3, is "
                + data.stationLevel(team, hole.id()));
        server.getCommands().performPrefixedCommand(source, "continuumadmin swarm remove " + id + " 1");
        helper.assertTrue(data.stationLevel(team, hole.id()) == 2, "remove 1 should give level 2");
        long perTick = ContinuumSwarmPower.outputPerTick(server, team);
        long expected = Math.round(2 * ContinuumConfigs.INSTANCE.swarmPowerPerLevel * Swarms.luminosity(hole) *
                ContinuumConfigs.INSTANCE.penrosePowerMultiplier);
        helper.assertTrue(perTick == expected, "a level 2 Penrose swarm should make " + expected + " per tick, makes "
                + perTick);
        server.getCommands().performPrefixedCommand(source, "continuumadmin swarm set " + id + " 999");
        helper.assertTrue(data.stationLevel(team, hole.id()) == Swarms.maxLevel(hole),
                "set past the maximum clamps to it");
        server.getCommands().performPrefixedCommand(source, "continuumadmin swarm set " + id + " 0");
        helper.assertTrue(data.stationLevel(team, hole.id()) == 0, "set 0 removes the swarm");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),
                "continuumadmin generate clear");
        helper.succeed();
    }
}
