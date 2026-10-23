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

    public static int shellProbesPerLevel() {
        return Math.max(1, ContinuumConfigs.INSTANCE.shellProbesPerLevel);
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

    public static long powerPerTick(MinecraftServer server, UUID team) {
        double total = 0.0;
        var cfg = ContinuumConfigs.INSTANCE;
        for (Map.Entry<ResourceLocation, Integer> entry : ContinuumTeamData.get(server).stations(team).entrySet()) {
            ContinuumBody body = ContinuumData.body(entry.getKey());
            if (!isSwarm(body)) continue;
            total += entry.getValue() * cfg.swarmPowerPerLevel * luminosity(body) *
                    (isPenrose(body) ? cfg.penrosePowerMultiplier : 1.0);
        }
        for (Map.Entry<ResourceLocation, Integer> entry : ContinuumTeamData.get(server).shells(team).entrySet()) {
            ContinuumBody body = ContinuumData.body(entry.getKey());
            if (isSwarm(body)) total += shellPower(body, entry.getValue());
        }
        return Math.round(total);
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
