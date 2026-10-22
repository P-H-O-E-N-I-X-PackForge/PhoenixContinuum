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
}
