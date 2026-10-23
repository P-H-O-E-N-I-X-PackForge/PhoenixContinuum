package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.phoenix.continuum.integration.gtceu.GTCEuCompat;

public final class ContinuumMachines {

    private ContinuumMachines() {}

    public static MultiblockMachineDefinition LAUNCH_COMPLEX;

    public static MultiblockMachineDefinition SWARM_RECEIVER;

    public static void init() {
        GTCEuCompat.bootstrap().registerMachines();
    }
}
