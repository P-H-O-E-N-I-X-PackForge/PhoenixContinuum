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
