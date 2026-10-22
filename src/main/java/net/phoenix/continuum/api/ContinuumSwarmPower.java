package net.phoenix.continuum.api;

import net.minecraft.server.MinecraftServer;
import net.phoenix.continuum.common.Swarms;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The power a team's Dyson swarms produce, and who receives it.
 *
 * <p>
 * A swarm is an orbital station built at a star. Every second the power of all of a team's swarms is offered to that
 * team's {@link Sink}s, then to its launch complexes. Anything that wants to turn swarm power into energy of its own
 * (a receiver block, a GregTech multiblock, another mod's machine) registers a sink for the team that owns it.
 * Power nobody takes is lost: swarms do not store it.
 */
public final class ContinuumSwarmPower {

    /** Takes swarm power; returns how much of the offered amount it kept. */
    @FunctionalInterface
    public interface Sink {

        long accept(long amount);
    }

    private static final Map<UUID, Set<Sink>> SINKS = new ConcurrentHashMap<>();

    private ContinuumSwarmPower() {}

    public static void register(UUID team, Sink sink) {
        SINKS.computeIfAbsent(team, k -> ConcurrentHashMap.newKeySet()).add(sink);
    }

    public static void unregister(UUID team, Sink sink) {
        if (team == null) return;
        Set<Sink> set = SINKS.get(team);
        if (set == null) return;
        set.remove(sink);
        if (set.isEmpty()) SINKS.remove(team);
    }

    /** What the team's swarms produce, per tick, in EU with GregTech and FE without. */
    public static long outputPerTick(MinecraftServer server, UUID team) {
        return Swarms.powerPerTick(server, team);
    }

    public static Set<UUID> teams() {
        return SINKS.keySet();
    }

    public static Set<Sink> sinks(UUID team) {
        Set<Sink> set = SINKS.get(team);
        return set == null ? Set.of() : set;
    }
}
