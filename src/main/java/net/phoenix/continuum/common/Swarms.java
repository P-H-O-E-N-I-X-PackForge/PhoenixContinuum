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

/** Dyson swarms: orbital stations built at a star, whose power is delivered to the team that built them. */
public final class Swarms {

    private Swarms() {}

    /** A swarm is built at a star (a Dyson swarm) or a black hole (a Penrose swarm, which draws on its spin). */
    public static boolean isSwarm(ContinuumBody body) {
        return body != null && (body.type() == ContinuumBody.Type.STAR || isPenrose(body));
    }

    public static boolean isPenrose(ContinuumBody body) {
        return body != null && body.type() == ContinuumBody.Type.BLACK_HOLE;
    }

    /** What a station at this body is called. */
    public static String kindName(ContinuumBody body) {
        return isPenrose(body) ? "Penrose swarm" : isSwarm(body) ? "Dyson swarm" : "orbital station";
    }

    /** What a shell at this body is called. */
    public static String shellName(ContinuumBody body) {
        return isPenrose(body) ? "Penrose sphere" : "Dyson shell";
    }

    public static int shellMaxLevel(ContinuumBody body) {
        return ContinuumConfigs.INSTANCE.shellMaxLevel;
    }

    public static double shellCompletion(ContinuumBody body, int level) {
        return Math.max(0.0, Math.min(1.0, level / (double) Math.max(1, shellMaxLevel(body))));
    }

    /** What a rocket carries for one level: Swarm Collector Pods for a swarm, Shell Segments for a shell. */
    public static int payloadPerLevel(ContinuumBody body, boolean shell) {
        var cfg = ContinuumConfigs.INSTANCE;
        int count = shell ? (isPenrose(body) ? cfg.penroseSegmentsPerLevel : cfg.shellSegmentsPerLevel) :
                (isPenrose(body) ? cfg.penroseCollectorsPerLevel : cfg.swarmCollectorsPerLevel);
        return Math.max(1, count);
    }

    public static String payloadName(boolean shell, int count) {
        return shell ? count + " shell segment" + (count == 1 ? "" : "s") :
                count + " swarm collector pod" + (count == 1 ? "" : "s");
    }

    /**
     * The share of an outpost's cycles that still get made: a shell round the star it circles shades it. It is the
     * most complete of the team's shells in the outpost's system.
     */
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

    /** What a shell of this level makes per tick: nothing until it is mostly built, then the square of how built. */
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
            return String.format(java.util.Locale.ROOT, "nothing yet, it needs %d%% built",
                    Math.round(ContinuumConfigs.INSTANCE.shellMinCompletion * 100));
        }
        return String.format(java.util.Locale.ROOT, "%,d %s/t for your team, %d%% built", power, unit,
                Math.round(completion * 100));
    }

    /** How many levels a station at this body can have: a swarm has many, an ordinary station has a few. */
    public static int maxLevel(ContinuumBody body) {
        var cfg = ContinuumConfigs.INSTANCE;
        return isPenrose(body) ? cfg.penroseMaxLevel : isSwarm(body) ? cfg.swarmMaxLevel : Stations.MAX_LEVEL;
    }

    /** Bigger stars catch more light. */
    public static double luminosity(ContinuumBody star) {
        return Math.max(0.5, Math.min(4.0, star.radius()));
    }

    public static String describe(ContinuumBody star, int level) {
        return String.format(java.util.Locale.ROOT, "%,d %s/t for your team", Math.round(
                level * ContinuumConfigs.INSTANCE.swarmPowerPerLevel * luminosity(star) *
                        (isPenrose(star) ? ContinuumConfigs.INSTANCE.penrosePowerMultiplier : 1.0)),
                net.phoenix.continuum.machine.PadTiers.energyUnit());
    }

    /** What a swarm of this level makes per tick, before rounding. */
    public static double swarmPower(ContinuumBody body, int level) {
        var cfg = ContinuumConfigs.INSTANCE;
        return level * cfg.swarmPowerPerLevel * luminosity(body) * (isPenrose(body) ? cfg.penrosePowerMultiplier : 1.0);
    }

    /** One swarm or shell a team has built, and what it makes per tick. */
    public record Entry(ResourceLocation body, String name, boolean shell, int level, int max, long perTick) {}

    /** The swarms and shells in these level maps, with their output. Works on the client with its synced maps. */
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

    /** Once a second: offers each team's swarm power to its sinks, then to its launch complexes. */
    public static void tick(MinecraftServer server) {
        var data = ContinuumTeamData.get(server);
        java.util.Set<UUID> teams = data.teamsWithStations();
        teams.addAll(ContinuumSwarmPower.teams());
        for (UUID team : teams) {
            long remaining = powerPerTick(server, team) * 20L;
            // a sink is offered power even when there is none, so a capacitor can keep feeding what it holds
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
