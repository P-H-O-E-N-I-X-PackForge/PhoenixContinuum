package net.phoenix.continuum.common;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.machine.LaunchComplex;
import net.phoenix.continuum.machine.LaunchSites;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every pad's schedules. They are kept here, keyed by the pad, so a Greg launch complex and a basic pad need no
 * storage of their own. A schedule only runs while its pad is loaded: a run missed while it was not is skipped, not
 * stacked up.
 */
public final class ContinuumSchedules extends SavedData {

    private static final String ID = "phoenix_continuum_schedules";
    private static final long RETRY_MILLIS = 5000L;

    private final Map<UUID, Schedule> schedules = new LinkedHashMap<>();

    public static ContinuumSchedules get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ContinuumSchedules::new, ContinuumSchedules::load), ID);
    }

    public static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }

    public List<Schedule> all() {
        return new ArrayList<>(schedules.values());
    }

    public List<Schedule> atPad(String dimension, long pad) {
        List<Schedule> found = new ArrayList<>();
        for (Schedule schedule : schedules.values()) {
            if (schedule.padPos == pad && schedule.padDimension.equals(dimension)) found.add(schedule);
        }
        return found;
    }

    public @Nullable Schedule get(UUID id) {
        return schedules.get(id);
    }

    /** @return why it could not be added, or null once it has been */
    public @Nullable String add(Schedule schedule) {
        var cfg = ContinuumConfigs.INSTANCE;
        if (atPad(schedule.padDimension, schedule.padPos).size() >= cfg.maxSchedulesPerPad) {
            return "This pad already holds " + cfg.maxSchedulesPerPad + " schedules.";
        }
        schedule.intervalMillis = schedule.intervalMillis <= 0 ? 0 :
                Math.max(cfg.minScheduleIntervalSeconds * 1000L, schedule.intervalMillis);
        if (schedule.intervalMillis == 0) schedule.cap = 1;
        schedules.put(schedule.id, schedule);
        setDirty();
        return null;
    }

    public boolean remove(UUID id) {
        boolean removed = schedules.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }

    public int clearPad(String dimension, long pad) {
        int before = schedules.size();
        schedules.values().removeIf(s -> s.padPos == pad && s.padDimension.equals(dimension));
        if (schedules.size() != before) setDirty();
        return before - schedules.size();
    }

    public static void tick(MinecraftServer server) {
        ContinuumSchedules store = get(server);
        if (store.schedules.isEmpty() || !ContinuumConfigs.INSTANCE.scheduledLaunches) return;
        long now = System.currentTimeMillis();
        for (Schedule schedule : store.all()) {
            if (schedule.enabled && now >= schedule.nextFire && now >= schedule.retryAt) {
                store.run(server, schedule, now);
            }
        }
    }

    private void run(MinecraftServer server, Schedule schedule, long now) {
        Level level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(schedule.padDimension)));
        BlockPos pad = BlockPos.of(schedule.padPos);
        if (level == null || !level.isLoaded(pad)) return;

        ContinuumBody body = ContinuumData.body(schedule.destination);
        if (body == null) {
            stop(server, schedule, "its destination no longer exists.");
            return;
        }
        LaunchComplex complex = LaunchSites.at(level, pad);
        if (complex == null) {
            stop(server, schedule, "its launch pad is gone.");
            return;
        }

        boolean convoy = schedule.type == Mission.Type.EXTRACT || schedule.type == Mission.Type.DEPLOY ||
                schedule.type == Mission.Type.RESCUE;
        int wanted = convoy ? Math.max(1, Math.min(schedule.count, ContinuumConfigs.INSTANCE.maxRocketsPerLaunch)) : 1;
        int launched = 0;
        String error = null;
        for (int i = 0; i < wanted; i++) {
            error = ContinuumMissions.launchScheduled(server, level, pad, schedule.team, schedule.owner,
                    schedule.ownerName, schedule.destination, schedule.type, schedule.probes, schedule.expedition,
                    schedule.crew, schedule.autoCollect);
            if (error != null) break;
            launched++;
        }

        if (launched == 0) {
            schedule.retryAt = now + RETRY_MILLIS;
            if (!error.equals(schedule.lastFailure)) {
                schedule.lastFailure = error;
                ContinuumMissions.notifyTeam(server, schedule.team, Component.literal("Scheduled " +
                        schedule.type.label().toLowerCase(java.util.Locale.ROOT) + " to " + body.name() +
                        " is waiting: " + error).withStyle(ChatFormatting.GOLD));
            }
            setDirty();
            return;
        }

        schedule.lastFailure = "";
        schedule.runs++;
        if (schedule.finished() || schedule.intervalMillis <= 0) {
            schedule.enabled = false;
            ContinuumMissions.notifyTeam(server, schedule.team, Component.literal("Scheduled " +
                    schedule.type.label().toLowerCase(java.util.Locale.ROOT) + " to " + body.name() +
                    " has made its last run.").withStyle(ChatFormatting.GRAY));
        } else {
            schedule.nextFire = now + schedule.intervalMillis;
        }
        setDirty();
    }

    private void stop(MinecraftServer server, Schedule schedule, String why) {
        schedule.enabled = false;
        setDirty();
        ContinuumMissions.notifyTeam(server, schedule.team, Component.literal("A scheduled launch was switched off: " +
                why).withStyle(ChatFormatting.RED));
    }

    public static ContinuumSchedules load(CompoundTag tag, HolderLookup.Provider registries) {
        ContinuumSchedules data = new ContinuumSchedules();
        for (Tag t : tag.getList("Schedules", Tag.TAG_COMPOUND)) {
            try {
                Schedule schedule = Schedule.load((CompoundTag) t);
                data.schedules.put(schedule.id, schedule);
            } catch (RuntimeException e) {
                // a schedule that cannot be read is dropped, the others stay
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Schedule schedule : schedules.values()) list.add(schedule.save());
        tag.put("Schedules", list);
        return tag;
    }
}
