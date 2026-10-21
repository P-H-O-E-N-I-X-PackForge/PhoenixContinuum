package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.phoenix.continuum.integration.gtceu.GTCEuCompat;

public final class ContinuumMachines {

    private ContinuumMachines() {}

    /** Set when GregTech asks addons to register their machines; the recipes that make it run after that. */
    public static MultiblockMachineDefinition LAUNCH_COMPLEX;

    public static void init() {
        GTCEuCompat.bootstrap().registerMachines();
    }
}
