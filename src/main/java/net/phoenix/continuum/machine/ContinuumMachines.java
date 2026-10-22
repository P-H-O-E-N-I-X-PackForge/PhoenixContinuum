package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.phoenix.continuum.integration.gtceu.GTCEuCompat;

/** The GregTech launch complex. Only touched when GregTech is installed. */
public final class ContinuumMachines {

    private ContinuumMachines() {}

    /** Set when GregTech asks addons to register their machines; the recipes that make it run after that. */
    public static MultiblockMachineDefinition LAUNCH_COMPLEX;

    /** The multiblock that receives Dyson swarm power. */
    public static MultiblockMachineDefinition SWARM_RECEIVER;

    public static void init() {
        GTCEuCompat.bootstrap().registerMachines();
    }
}
