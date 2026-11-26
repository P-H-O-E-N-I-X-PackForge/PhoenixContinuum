package net.phoenix.continuum.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.phoenix.continuum.api.ContinuumSwarmPower;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.machine.LaunchComplex;

import java.util.Map;
import java.util.UUID;

public final class Swarms {

    private Swarms() {}

    public static boolean isSwarm(ContinuumBody body) {
        return body != null && (body.type() == ContinuumBody.Type.STAR || isPenrose(body));
    }

    public static boolean isPenrose(ContinuumBody body) {
        return body != null && body.type() == ContinuumBody.Type.BLACK_HOLE;
    }

    public static String kindName(ContinuumBody body) {
        return ContinuumLang.text(isPenrose(body) ? "phoenix_continuum.swarms.kind_penrose" : isSwarm(body) ? "phoenix_continuum.swarms.kind_dyson" : "phoenix_continuum.swarms.kind_station");
    }

    public static String shellName(ContinuumBody body) {
        return ContinuumLang.text(isPenrose(body) ? "phoenix_continuum.swarms.shell_penrose" : "phoenix_continuum.swarms.shell_dyson");
    }

    public static int shellMaxLevel(ContinuumBody body) {
        return ContinuumConfigs.INSTANCE.shellMaxLevel;
    }

    public static double shellCompletion(ContinuumBody body, int level) {
        return Math.max(0.0, Math.min(1.0, level / (double) Math.max(1, shellMaxLevel(body))));
    }

    public static int payloadPerLevel(ContinuumBody body, boolean shell) {
        var cfg = ContinuumConfigs.INSTANCE;
        int count = shell ? (isPenrose(body) ? cfg.penroseSegmentsPerLevel : cfg.shellSegmentsPerLevel) :
                (isPenrose(body) ? cfg.penroseCollectorsPerLevel : cfg.swarmCollectorsPerLevel);
        return Math.max(1, count);
    }

    public static String payloadName(boolean shell, int count) {
        return payloadName(shell, count, false);
    }

    public static String payloadName(boolean shell, int count, boolean penrose) {
        boolean one = count == 1;
        if (penrose) {
            return shell ? ContinuumLang.text(one ? "phoenix_continuum.swarms.payload_penrose_segment_one" : "phoenix_continuum.swarms.payload_penrose_segment_many", count) : ContinuumLang.text(one ? "phoenix_continuum.swarms.payload_penrose_pod_one" : "phoenix_continuum.swarms.payload_penrose_pod_many", count);
        }
        return shell ? ContinuumLang.text(one ? "phoenix_continuum.swarms.payload_segment_one" : "phoenix_continuum.swarms.payload_segment_many", count) : ContinuumLang.text(one ? "phoenix_continuum.swarms.payload_pod_one" : "phoenix_continuum.swarms.payload_pod_many", count);
    }

    public static double shadow(ContinuumTeamData data, UUID team, ContinuumBody outpost) {
        if (outpost == null) return 1.0;
        double worst = 0.0;
        for (Map.Entry<ResourceLocation, Integer> entry : data.shells(team).entrySet()) {
            ContinuumBody shell = ContinuumData.body(entry.getKey());
            if (shell == null || !shell.system().equals(outpost.system())) continue;
            worst = Math.max(worst, shellCompletion(shell, entry.getValue()));
        }
        return 1.0 - ContinuumConfigs.INSTANCE.shellOutpostShadow * worst;
    }

    public static double shellPower(ContinuumBody body, int level) {
        var cfg = ContinuumConfigs.INSTANCE;
        double completion = shellCompletion(body, level);
        if (completion < cfg.shellMinCompletion) return 0.0;
        return cfg.swarmMaxLevel * cfg.swarmPowerPerLevel * luminosity(body) * cfg.shellPowerMultiplier *
                completion * completion * (isPenrose(body) ? cfg.penrosePowerMultiplier : 1.0);
    }

    public static String describeShell(ContinuumBody body, int level) {
        double completion = shellCompletion(body, level);
        long power = Math.round(shellPower(body, level));
        String unit = net.phoenix.continuum.machine.PadTiers.energyUnit();
        if (power <= 0) {
            return ContinuumLang.text("phoenix_continuum.swarms.nothing_yet",
                    Math.round(ContinuumConfigs.INSTANCE.shellMinCompletion * 100));
        }
        return ContinuumLang.text("phoenix_continuum.swarms.power_built", power, unit,
                Math.round(completion * 100));
    }

    public static int maxLevel(ContinuumBody body) {
        var cfg = ContinuumConfigs.INSTANCE;
        return isPenrose(body) ? cfg.penroseMaxLevel : isSwarm(body) ? cfg.swarmMaxLevel : Stations.MAX_LEVEL;
    }

    public static double luminosity(ContinuumBody star) {
        return Math.max(0.5, Math.min(4.0, star.radius()));
    }

    public static String describe(ContinuumBody star, int level) {
        return ContinuumLang.text("phoenix_continuum.swarms.power_for_team", Math.round(
                level * ContinuumConfigs.INSTANCE.swarmPowerPerLevel * luminosity(star) *
                        (isPenrose(star) ? ContinuumConfigs.INSTANCE.penrosePowerMultiplier : 1.0)),
                net.phoenix.continuum.machine.PadTiers.energyUnit());
    }

    public static double swarmPower(ContinuumBody body, int level) {
        var cfg = ContinuumConfigs.INSTANCE;
        return level * cfg.swarmPowerPerLevel * luminosity(body) * (isPenrose(body) ? cfg.penrosePowerMultiplier : 1.0);
    }

    public record Entry(ResourceLocation body, String name, boolean shell, int level, int max, long perTick) {}

    public static java.util.List<Entry> entries(Map<ResourceLocation, Integer> swarms,
                                                Map<ResourceLocation, Integer> shells) {
        java.util.List<Entry> out = new java.util.ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> e : swarms.entrySet()) {
            ContinuumBody body = ContinuumData.body(e.getKey());
            if (!isSwarm(body) || e.getValue() <= 0) continue;
            out.add(new Entry(e.getKey(), body.name(), false, e.getValue(), maxLevel(body),
                    Math.round(swarmPower(body, e.getValue()))));
        }
        for (Map.Entry<ResourceLocation, Integer> e : shells.entrySet()) {
            ContinuumBody body = ContinuumData.body(e.getKey());
            if (!isSwarm(body) || e.getValue() <= 0) continue;
            out.add(new Entry(e.getKey(), body.name(), true, e.getValue(), shellMaxLevel(body),
                    Math.round(shellPower(body, e.getValue()))));
        }
        return out;
    }

    public static java.util.List<Entry> entries(MinecraftServer server, UUID team) {
        var data = ContinuumTeamData.get(server);
        return entries(data.stations(team), data.shells(team));
    }

    public static long total(java.util.List<Entry> entries) {
        long total = 0;
        for (Entry entry : entries) total += entry.perTick();
        return total;
    }

    public static long powerPerTick(MinecraftServer server, UUID team) {
        return total(entries(server, team));
    }

    public static void tick(MinecraftServer server) {
        var data = ContinuumTeamData.get(server);
        java.util.Set<UUID> teams = data.teamsWithStations();
        teams.addAll(ContinuumSwarmPower.teams());
        for (UUID team : teams) {
            long remaining = powerPerTick(server, team) * 20L;
            
            for (ContinuumSwarmPower.Sink sink : ContinuumSwarmPower.sinks(team)) {
                remaining -= Math.max(0L, Math.min(remaining, sink.accept(remaining)));
            }
            if (remaining <= 0) continue;
            for (LaunchComplex complex : LaunchComplex.complexesOf(team)) {
                if (remaining <= 0) break;
                remaining -= Math.max(0L, Math.min(remaining, complex.chargeEnergy(remaining)));
            }
        }
    }
}
