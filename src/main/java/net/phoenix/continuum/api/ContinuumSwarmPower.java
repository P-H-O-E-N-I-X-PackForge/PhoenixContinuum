package net.phoenix.continuum.api;

import net.minecraft.server.MinecraftServer;
import net.phoenix.continuum.common.Swarms;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ContinuumSwarmPower {

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
