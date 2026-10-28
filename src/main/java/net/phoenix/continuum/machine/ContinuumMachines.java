package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.phoenix.continuum.integration.gtceu.GTCEuCompat;

public final class ContinuumMachines {

    private ContinuumMachines() {}

    public static MultiblockMachineDefinition LAUNCH_COMPLEX;

    public static MultiblockMachineDefinition SWARM_RECEIVER;

    public static MultiblockMachineDefinition SWARM_CONTROL;

    /** What keeps a Swarm Control Array online: a recipe like any other, so packs can edit it. */
    public static GTRecipeType SWARM_CONTROL_RECIPES;

    public static void initRecipeTypes() {
        if (SWARM_CONTROL_RECIPES != null) return;
        SWARM_CONTROL_RECIPES = GTRecipeTypes.register("swarm_control_uplink", GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(0, 0, 1, 0)
                .setEUIO(IO.IN);
    }

    public static void init() {
        initRecipeTypes();
        GTCEuCompat.bootstrap().registerMachines();
    }
}
