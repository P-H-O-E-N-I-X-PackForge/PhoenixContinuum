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

/** Reads what the server knows about our blocks, as lines of text for the tooltip. */
final class ContinuumServerInfo implements IServerDataProvider<BlockAccessor> {

    static final String KEY = "ContinuumLines";

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(PhoenixContinuum.MOD_ID, "info_server");
    }

    private static String line(String label, String value) {
        return "\u00a77" + label + ": \u00a7f" + value;
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
        lines.add(line("Status", complex.isFormed() ? "\u00a7aReady" : "\u00a7cNot formed"));
        if (!complex.isFormed()) return;
        lines.add(line("Tier", PadTiers.name(complex.padTier())));
        lines.add(line("Energy", String.format(Locale.ROOT, "%,d / %,d %s", complex.storedEnergy(),
                complex.energyCapacity(), unit)));
        int rockets = complex.count(s -> s.getItem() instanceof ContinuumRocketItem);
        int probes = complex.count(s -> s.getItem() instanceof ContinuumProbeItem);
        int pods = complex.count(s -> s.getItem() instanceof ContinuumSwarmCollectorItem);
        int segments = complex.count(s -> s.getItem() instanceof ContinuumShellSegmentItem);
        if (complex.hasBuses() || rockets + probes + pods + segments > 0) {
            lines.add(line(complex.hasBuses() ? "In the buses" : "Stocked", rockets + " rockets, " + probes +
                    " probes" + (pods + segments > 0 ? ", " + pods + " pods, " + segments + " segments" : "")));
        }
        var schedules = ContinuumSchedules.get(level.getServer()).atPad(ContinuumSchedules.dimensionOf(level),
                pos.asLong());
        if (!schedules.isEmpty()) {
            long active = schedules.stream().filter(s -> s.enabled).count();
            lines.add(line("Schedules", schedules.size() + " (" + active + " running)"));
        }
    }

    private static void receiver(List<String> lines, Level level, BlockPos pos, SwarmHolograms.Source source) {
        SwarmReceiver receiver = level.getBlockEntity(pos) instanceof SwarmReceiver plain ? plain :
                GregTech.loaded() ? net.phoenix.continuum.integration.gtceu.ContinuumGt.receiverAt(level, pos) : null;
        if (receiver == null) return;
        var report = receiver.report(level.getServer());
        lines.add(line("Status", source.active() ? "\u00a7aReceiving" : "\u00a7eIdle"));
        lines.add(line("Team swarms make", String.format(Locale.ROOT, "%,d %s/t", report.teamPerTick(),
                report.unit())));
        if (report.capacity() > 0) {
            lines.add(line("Stored", String.format(Locale.ROOT, "%,d / %,d %s", report.stored(), report.capacity(),
                    report.unit())));
        }
        if (report.maxOut() > 0) {
            lines.add(line("Passes on", String.format(Locale.ROOT, "up to %,d %s/t", report.maxOut(), report.unit())));
        }
    }

    private static void control(List<String> lines, Level level, BlockPos pos, SwarmHolograms.Source source) {
        lines.add(line("Status", source.active() ? "\u00a7aOnline" : "\u00a7cOffline"));
        UUID owner = source.owner();
        if (owner == null) return;
        UUID team = ContinuumHost.teamOf(owner);
        ResourceLocation target = SwarmControlService.targetOf(level.getServer(), level, pos, team);
        ContinuumBody body = target == null ? null : ContinuumData.body(target);
        if (body == null) {
            lines.add(line("Target", "\u00a77none surveyed yet"));
            return;
        }
        var data = ContinuumTeamData.get(level.getServer());
        lines.add(line("Target", body.name() + (Swarms.isPenrose(body) ? " (black hole)" : " (star)")));
        lines.add(line("Swarm", data.stationLevel(team, target) + " / " + Swarms.maxLevel(body)));
        lines.add(line("Shell", data.shellLevel(team, target) + " / " + Swarms.shellMaxLevel(body)));
        lines.add(line("Hologram size", String.format(Locale.ROOT, "%.2gx",
                SwarmControlTargets.get(level.getServer()).scale(level, pos))));
    }
}
