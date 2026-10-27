package net.phoenix.continuum.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.block.LaunchPadBlockEntity;
import net.phoenix.continuum.common.ContinuumMissions;
import net.phoenix.continuum.common.ContinuumSchedules;
import net.phoenix.continuum.common.ContinuumTeamData;
import net.phoenix.continuum.common.Mission;
import net.phoenix.continuum.common.Schedule;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;
import net.phoenix.continuum.integration.gtceu.GregTech;

import java.util.UUID;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScheduleGameTests {

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void aPadLaunchesWaitsAndTakesItsRocketHomeWithNobodyThere(GameTestHelper helper) {
        if (GregTech.loaded()) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos below = new BlockPos(1, 1, 1);
        BlockPos at = below.above();
        helper.setBlock(below, Blocks.IRON_BLOCK);
        helper.setBlock(at, ContinuumRegistry.LAUNCH_PAD.get());
        LaunchPadBlockEntity pad = (LaunchPadBlockEntity) helper.getBlockEntity(at);
        BlockPos pos = helper.absolutePos(at);

        UUID team = UUID.randomUUID();
        ContinuumBody destination = ContinuumData.allBodies().stream()
                .filter(b -> b.transientInfo() == null && b.type() == ContinuumBody.Type.PLANET &&
                        net.phoenix.continuum.common.visibility.ContinuumVisibility.accessible(server, team, b))
                .findFirst().orElse(null);
        helper.assertTrue(destination != null, "there should be a planet to fly to");

        ContinuumTeamData data = ContinuumTeamData.get(server);
        data.raise(team, destination.id(), DiscoveryStage.DETECTED);

        var store = ContinuumSchedules.get(server);
        store.clearPad(ContinuumSchedules.dimensionOf(helper.getLevel()), pos.asLong());
        Schedule schedule = new Schedule(UUID.randomUUID(), team, team, "Schedule", ContinuumSchedules.dimensionOf(
                helper.getLevel()), pos.asLong(), destination.id());
        schedule.type = Mission.Type.SURVEY;
        schedule.autoCollect = true;
        schedule.enabled = true;
        helper.assertTrue(store.add(schedule) == null, "the schedule should be accepted");
        helper.assertTrue(schedule.cap == 1, "a schedule with no interval is a single run");
        helper.assertTrue(pad.count(s -> true) == 0, "the pad starts empty");

        // nothing in the pad: it waits, stays armed, and remembers why
        ContinuumSchedules.tick(server);
        helper.assertTrue(schedule.runs == 0 && schedule.enabled, "a schedule without a rocket waits");
        helper.assertTrue(!schedule.lastFailure.isEmpty(), "it remembers why it waits");
        String why = schedule.lastFailure;
        schedule.retryAt = 0;
        ContinuumSchedules.tick(server);
        helper.assertTrue(schedule.lastFailure.equals(why) && schedule.runs == 0, "and waits again for the same reason");

        // the pad is stocked and charged: the next try launches, with nobody there
        pad.stock(ContinuumRegistry.ROCKET.asStack());
        helper.assertTrue(pad.count(s -> s.getItem() == ContinuumRegistry.ROCKET.get()) == 1, "the rocket is in the pad");
        pad.chargeEnergy(pad.energyCapacity());
        schedule.retryAt = 0;
        ContinuumSchedules.tick(server);
        helper.assertTrue(schedule.runs == 1, "the schedule should have launched, waits: " + schedule.lastFailure);
        helper.assertTrue(!schedule.enabled, "a single run switches itself off");
        helper.assertTrue(pad.count(s -> s.getItem() == ContinuumRegistry.ROCKET.get()) == 0, "the rocket flew");
        Mission flown = data.missions(team).stream().findFirst().orElse(null);
        helper.assertTrue(flown != null && flown.autoCollect, "the mission is out, marked to be taken in");

        // it lands, and the pad takes the rocket in without anyone collecting it
        flown.startMillis -= flown.durationMillis + 1000L;
        ContinuumMissions.resolveDue(server);
        helper.assertTrue(data.missions(team).isEmpty(), "the pad should have taken the mission in");
        ItemStack home = pad.peek(s -> s.getItem() == ContinuumRegistry.ROCKET.get());
        helper.assertTrue(!home.isEmpty(), "the rocket should be back in the pad");

        store.remove(schedule.id);
        helper.succeed();
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 100)
    public static void repeatingSchedulesKeepTheirTimeAndStopAtTheirCap(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var store = ContinuumSchedules.get(server);
        ContinuumBody destination = ContinuumData.allBodies().stream().findFirst().orElse(null);
        helper.assertTrue(destination != null, "there should be a body");
        Schedule schedule = new Schedule(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "x", "minecraft:overworld",
                7L, destination.id());
        schedule.intervalMillis = 1000L;
        schedule.cap = 3;
        schedule.crew.add("Someone");
        schedule.expedition = "Run";
        helper.assertTrue(store.add(schedule) == null, "accepted");
        helper.assertTrue(schedule.intervalMillis >= 1000L, "the interval is not below the configured minimum");

        var back = Schedule.load(schedule.save());
        helper.assertTrue(back.cap == 3 && back.intervalMillis == schedule.intervalMillis, "cap and interval survive saving");
        helper.assertTrue(back.crew.equals(schedule.crew) && back.expedition.equals("Run"), "crew and name survive saving");

        schedule.runs = 3;
        helper.assertTrue(schedule.finished(), "three runs of a cap of three is finished");
        store.remove(schedule.id);
        helper.assertTrue(store.get(schedule.id) == null, "removed schedules are gone");
        helper.succeed();
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 100)
    public static void playersManageTheirPadsSchedulesFromTheServerSide(GameTestHelper helper) {
        if (GregTech.loaded()) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.IRON_BLOCK);
        helper.setBlock(at, ContinuumRegistry.LAUNCH_PAD.get());
        BlockPos pos = helper.absolutePos(at);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        var store = ContinuumSchedules.get(server);
        store.clearPad(ContinuumSchedules.dimensionOf(helper.getLevel()), pos.asLong());
        UUID team = net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        ContinuumBody destination = ContinuumData.allBodies().stream().filter(b -> b.transientInfo() == null)
                .findFirst().orElse(null);
        helper.assertTrue(destination != null, "there should be a body");
        ContinuumTeamData.get(server).raise(team, destination.id(), DiscoveryStage.DETECTED);

        String error = net.phoenix.continuum.common.ScheduleService.create(player, pos, destination.id(),
                Mission.Type.SURVEY, 0, 1, "Run", java.util.List.of(), 0, 120, 4, true);
        helper.assertTrue(error == null, "creating should work: " + error);
        var views = net.phoenix.continuum.common.ScheduleService.views(player, pos);
        helper.assertTrue(views.size() == 1 && views.get(0).enabled() && views.get(0).cap() == 4 &&
                views.get(0).interval() == 120_000L, "the pad should list the new schedule: " + views);

        UUID id = views.get(0).id();
        helper.assertTrue(net.phoenix.continuum.common.ScheduleService.toggle(player, pos, id) == null, "toggle works");
        helper.assertTrue(!net.phoenix.continuum.common.ScheduleService.views(player, pos).get(0).enabled(),
                "it is paused");
        player.setPos(pos.getX() + 500.0, pos.getY(), pos.getZ());
        helper.assertTrue(net.phoenix.continuum.common.ScheduleService.delete(player, pos, id) != null,
                "a player far from the pad cannot change its schedules");
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        helper.assertTrue(net.phoenix.continuum.common.ScheduleService.delete(player, pos, id) == null, "delete works");
        helper.assertTrue(net.phoenix.continuum.common.ScheduleService.views(player, pos).isEmpty(), "and it is gone");
        helper.succeed();
    }
}
