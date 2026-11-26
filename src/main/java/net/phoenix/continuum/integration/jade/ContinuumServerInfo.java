package net.phoenix.continuum.integration.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.common.ContinuumSchedules;
import net.phoenix.continuum.common.ContinuumTeamData;
import net.phoenix.continuum.common.SwarmControlService;
import net.phoenix.continuum.common.SwarmControlTargets;
import net.phoenix.continuum.common.SwarmHolograms;
import net.phoenix.continuum.common.SwarmReceiver;
import net.phoenix.continuum.common.Swarms;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.integration.gtceu.GregTech;
import net.phoenix.continuum.item.ContinuumProbeItem;
import net.phoenix.continuum.item.ContinuumRocketItem;
import net.phoenix.continuum.item.ContinuumSwarmCollectorItem;
import net.phoenix.continuum.item.ContinuumShellSegmentItem;
import net.phoenix.continuum.machine.LaunchComplex;
import net.phoenix.continuum.machine.LaunchSites;
import net.phoenix.continuum.machine.PadTiers;
import net.phoenix.continuum.network.S2CHologramPacket;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ContinuumServerInfo implements IServerDataProvider<BlockAccessor> {

    static final String KEY = "ContinuumLines";

    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation(PhoenixContinuum.MOD_ID, "info_server");
    }

    private static String line(String label, String value) {
        return net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.line",
                net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.label_" + label),
                value);
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        Level level = accessor.getLevel();
        BlockPos pos = accessor.getPosition();
        if (level.getServer() == null) return;
        List<String> lines = new ArrayList<>();

        var source = SwarmHolograms.sourceAt(level, pos);
        if (source != null && source.kind() == S2CHologramPacket.State.CONTROL) {
            control(lines, level, pos, source);
        } else if (source != null && source.kind() == S2CHologramPacket.State.RECEIVER) {
            receiver(lines, level, pos, source);
        } else {
            LaunchComplex complex = LaunchSites.at(level, pos);
            if (complex != null) pad(lines, level, pos, complex);
        }

        if (lines.isEmpty()) return;
        ListTag list = new ListTag();
        for (String s : lines) list.add(StringTag.valueOf(s));
        data.put(KEY, list);
    }

    private static void pad(List<String> lines, Level level, BlockPos pos, LaunchComplex complex) {
        String unit = PadTiers.energyUnit();
        lines.add(line("status", complex.isFormed() ? net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.ready") : net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.not_formed")));
        if (!complex.isFormed()) return;
        lines.add(line("tier", PadTiers.name(complex.padTier())));
        lines.add(line("energy", String.format(Locale.ROOT, "%,d / %,d %s", complex.storedEnergy(),
                complex.energyCapacity(), unit)));
        int rockets = complex.count(s -> s.getItem() instanceof ContinuumRocketItem);
        int probes = complex.count(s -> s.getItem() instanceof ContinuumProbeItem);
        int pods = complex.count(s -> s.getItem() instanceof ContinuumSwarmCollectorItem ||
                s.getItem() instanceof net.phoenix.continuum.item.ContinuumPenroseCollectorItem);
        int segments = complex.count(s -> s.getItem() instanceof ContinuumShellSegmentItem ||
                s.getItem() instanceof net.phoenix.continuum.item.ContinuumPenroseSegmentItem);
        if (complex.hasBuses() || rockets + probes + pods + segments > 0) {
            lines.add(line(complex.hasBuses() ? "in_the_buses" : "stocked", net.phoenix.continuum.common.ContinuumLang.enc(pods + segments > 0 ? "phoenix_continuum.continuum_server_info.stock_full" : "phoenix_continuum.continuum_server_info.stock", rockets, probes, pods, segments)));
        }
        var schedules = ContinuumSchedules.get(level.getServer()).atPad(ContinuumSchedules.dimensionOf(level),
                pos.asLong());
        if (!schedules.isEmpty()) {
            long active = schedules.stream().filter(s -> s.enabled).count();
            lines.add(line("schedules", net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.schedules_value", schedules.size(), active)));
        }
    }

    private static void receiver(List<String> lines, Level level, BlockPos pos, SwarmHolograms.Source source) {
        SwarmReceiver receiver = level.getBlockEntity(pos) instanceof SwarmReceiver plain ? plain :
                GregTech.loaded() ? net.phoenix.continuum.integration.gtceu.ContinuumGt.receiverAt(level, pos) : null;
        if (receiver == null) return;
        var report = receiver.report(level.getServer());
        lines.add(line("status", source.active() ? net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.receiving") : net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.idle")));
        lines.add(line("team_swarms_make", String.format(Locale.ROOT, "%,d %s/t", report.teamPerTick(),
                report.unit())));
        if (report.capacity() > 0) {
            lines.add(line("stored", String.format(Locale.ROOT, "%,d / %,d %s", report.stored(), report.capacity(),
                    report.unit())));
        }
        if (report.maxOut() > 0) {
            lines.add(line("passes_on", String.format(Locale.ROOT, "up to %,d %s/t", report.maxOut(), report.unit())));
        }
    }

    private static void control(List<String> lines, Level level, BlockPos pos, SwarmHolograms.Source source) {
        lines.add(line("status", source.active() ? net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.online") : net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.offline")));
        UUID owner = source.owner();
        if (owner == null) return;
        UUID team = ContinuumHost.teamOf(owner);
        ResourceLocation target = SwarmControlService.targetOf(level.getServer(), level, pos, team);
        ContinuumBody body = target == null ? null : ContinuumData.body(target);
        if (body == null) {
            lines.add(line("target", net.phoenix.continuum.common.ContinuumLang.enc("phoenix_continuum.continuum_server_info.none_surveyed_yet")));
            return;
        }
        var data = ContinuumTeamData.get(level.getServer());
        lines.add(line("target", net.phoenix.continuum.common.ContinuumLang.enc(Swarms.isPenrose(body) ? "phoenix_continuum.continuum_server_info.target_hole" : "phoenix_continuum.continuum_server_info.target_star", body.name())));
        lines.add(line("swarm", data.stationLevel(team, target) + " / " + Swarms.maxLevel(body)));
        lines.add(line("shell", data.shellLevel(team, target) + " / " + Swarms.shellMaxLevel(body)));
        lines.add(line("hologram_size", String.format(Locale.ROOT, "%.2gx",
                SwarmControlTargets.get(level.getServer()).scale(level, pos))));
    }
}
