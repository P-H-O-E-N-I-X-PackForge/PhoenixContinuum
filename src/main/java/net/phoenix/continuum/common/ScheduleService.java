package net.phoenix.continuum.common;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;
import net.phoenix.continuum.machine.LaunchSites;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** What a player can do with a pad's schedules, checked on the server. */
public final class ScheduleService {

    private ScheduleService() {}

    public record View(UUID id, ResourceLocation destination, Mission.Type type, int probes, int count,
                       long interval, int cap, int runs, boolean autoCollect, boolean enabled, long nextFire,
                       String failure, String expedition, int crew) {

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeResourceLocation(destination);
            buf.writeByte(type.ordinal());
            buf.writeVarInt(probes);
            buf.writeVarInt(count);
            buf.writeVarLong(interval);
            buf.writeVarInt(cap);
            buf.writeVarInt(runs);
            buf.writeBoolean(autoCollect);
            buf.writeBoolean(enabled);
            buf.writeLong(nextFire);
            buf.writeUtf(failure, 256);
            buf.writeUtf(expedition, 32);
            buf.writeVarInt(crew);
        }

        public static View read(FriendlyByteBuf buf) {
            return new View(buf.readUUID(), buf.readResourceLocation(),
                    Mission.Type.values()[Math.max(0, Math.min(buf.readByte(), Mission.Type.values().length - 1))],
                    buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readLong(), buf.readUtf(256), buf.readUtf(32),
                    buf.readVarInt());
        }
    }

    private static @Nullable String reach(ServerPlayer player, BlockPos pad) {
        Level level = player.level();
        if (!level.isLoaded(pad) || LaunchSites.at(level, pad) == null) return "There is no launch pad there.";
        double range = ContinuumConfigs.INSTANCE.launchRangeBlocks;
        if (player.distanceToSqr(Vec3.atCenterOf(pad)) > range * range) return "You are too far from the launch pad.";
        return null;
    }

    public static List<View> views(ServerPlayer player, BlockPos pad) {
        UUID team = ContinuumHost.teamOf(player.getUUID());
        List<View> views = new ArrayList<>();
        for (Schedule s : ContinuumSchedules.get(player.server).atPad(ContinuumSchedules.dimensionOf(player.level()),
                pad.asLong())) {
            if (!s.team.equals(team)) continue;
            views.add(new View(s.id, s.destination, s.type, s.probes, s.count, s.intervalMillis, s.cap, s.runs,
                    s.autoCollect, s.enabled, s.nextFire, s.lastFailure, s.expedition, s.crew.size()));
        }
        return views;
    }

    public static @Nullable String create(ServerPlayer player, BlockPos pad, ResourceLocation destination,
                                          Mission.Type type, int probes, int count, String expedition,
                                          List<String> crew, int delaySeconds, int intervalSeconds, int cap,
                                          boolean autoCollect) {
        String problem = reach(player, pad);
        if (problem != null) return problem;
        ContinuumBody body = ContinuumData.body(destination);
        if (body == null) return "That destination does not exist.";
        UUID team = ContinuumHost.teamOf(player.getUUID());
        var data = ContinuumTeamData.get(player.server);
        if (data.effectiveStage(player.server, team, destination) == DiscoveryStage.UNKNOWN) {
            return "That signal has not been resolved yet.";
        }
        Schedule schedule = new Schedule(UUID.randomUUID(), team, player.getUUID(),
                player.getGameProfile().getName(), ContinuumSchedules.dimensionOf(player.level()), pad.asLong(),
                destination);
        schedule.type = type;
        schedule.probes = Math.max(0, Math.min(probes, ContinuumConfigs.INSTANCE.maxProbesPerMission));
        schedule.count = Math.max(1, Math.min(count, ContinuumConfigs.INSTANCE.maxRocketsPerLaunch));
        schedule.expedition = expedition.length() > 24 ? expedition.substring(0, 24) : expedition;
        schedule.crew = ContinuumMissions.validCrew(player, crew);
        schedule.intervalMillis = Math.max(0, intervalSeconds) * 1000L;
        schedule.cap = Math.max(0, Math.min(cap, 1000));
        schedule.autoCollect = autoCollect;
        schedule.nextFire = System.currentTimeMillis() + Math.max(0, delaySeconds) * 1000L;
        schedule.enabled = true;
        return ContinuumSchedules.get(player.server).add(schedule);
    }

    private static @Nullable Schedule own(ServerPlayer player, BlockPos pad, UUID id) {
        Schedule schedule = ContinuumSchedules.get(player.server).get(id);
        if (schedule == null || !schedule.team.equals(ContinuumHost.teamOf(player.getUUID())) ||
                schedule.padPos != pad.asLong() ||
                !schedule.padDimension.equals(ContinuumSchedules.dimensionOf(player.level()))) {
            return null;
        }
        return schedule;
    }

    public static @Nullable String toggle(ServerPlayer player, BlockPos pad, UUID id) {
        String problem = reach(player, pad);
        if (problem != null) return problem;
        Schedule schedule = own(player, pad, id);
        if (schedule == null) return "That schedule is gone.";
        schedule.enabled = !schedule.enabled;
        if (schedule.enabled) {
            // started again: a finished one starts over, and none of them fires for time that went by while off
            if (schedule.finished() || schedule.intervalMillis <= 0) schedule.runs = 0;
            schedule.nextFire = Math.max(schedule.nextFire, System.currentTimeMillis());
            schedule.retryAt = 0;
        }
        ContinuumSchedules.get(player.server).setDirty();
        return null;
    }

    public static @Nullable String delete(ServerPlayer player, BlockPos pad, UUID id) {
        String problem = reach(player, pad);
        if (problem != null) return problem;
        if (own(player, pad, id) == null) return "That schedule is gone.";
        ContinuumSchedules.get(player.server).remove(id);
        return null;
    }
}
