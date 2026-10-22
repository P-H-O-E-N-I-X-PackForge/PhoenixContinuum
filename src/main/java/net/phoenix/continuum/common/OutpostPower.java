package net.phoenix.continuum.common;

import net.minecraft.server.MinecraftServer;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.machine.LaunchPadMachine;

import java.util.UUID;

public final class OutpostPower {

    private OutpostPower() {}

    public static boolean enabled() {
        var cfg = ContinuumConfigs.INSTANCE;
        return cfg.outpostRequiresPower && cfg.outpostUpkeepEUPerProbe > 0.0;
    }

    public static long upkeepPerCycle(int probes) {
        if (!enabled()) return 0L;
        return Math.round(ContinuumConfigs.INSTANCE.outpostUpkeepEUPerProbe) * Math.max(0, probes);
    }

    private static long stored(UUID team) {
        long total = 0;
        for (LaunchPadMachine complex : LaunchPadMachine.complexesOf(team)) total += complex.storedEnergy();
        return total;
    }

    private static void drain(UUID team, long amount) {
        long remaining = amount;
        for (LaunchPadMachine complex : LaunchPadMachine.complexesOf(team)) {
            if (remaining <= 0) break;
            long take = Math.min(remaining, complex.storedEnergy());
            if (take > 0 && complex.drainEnergy(take)) remaining -= take;
        }
    }

    public static Outpost.CyclePayer payerFor(MinecraftServer server, UUID team) {
        if (!enabled()) return Outpost.FREE;

        return (cycles, probes) -> {
            long perCycle = upkeepPerCycle(probes);
            if (perCycle <= 0 || cycles <= 0) return cycles;

            long affordable = stored(team) / perCycle;
            int paid = (int) Math.min(affordable, cycles);
            if (paid > 0) drain(team, perCycle * paid);
            return paid;
        };
    }

    public static boolean canAffordCycle(MinecraftServer server, UUID team, int probes) {
        long perCycle = upkeepPerCycle(probes);
        if (perCycle <= 0) return true;
        return stored(team) >= perCycle;
    }
}
