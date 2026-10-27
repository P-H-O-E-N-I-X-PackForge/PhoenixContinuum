package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.registry.registrate.entry.GTRecipeTypeEntry;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenix.continuum.integration.gtceu.ContinuumGt;

public final class ContinuumMachines {

    private ContinuumMachines() {}

    /** What keeps a Swarm Control Array online: a recipe like any other, so packs can edit it. */
    public static final GTRecipeTypeEntry SWARM_CONTROL_RECIPES = ContinuumGt.REGISTRATE
            .recipeType("swarm_control_uplink", GTRecipeTypes.MULTIBLOCK)
            .setMaxIOSize(0, 0, 1, 0)
            .setEUIO(IO.IN)
            .lang("Swarm Control Uplink")
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> SWARM_CONTROL = ContinuumGt.REGISTRATE
            .multiblock("continuum_swarm_control_array", SwarmControlMachine::new)
            .langValue("Swarm Control Array")
            .rotationState(RotationState.NON_Y_AXIS)
            .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
            .recipeType(SWARM_CONTROL_RECIPES)
            .tooltips(SwarmControlShape.TOOLTIPS.toArray(new Component[0]))
            .pattern(definition -> buildControlPattern(definition))
            .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTCEu.id("block/multiblock/pyrolyse_oven"))
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> LAUNCH_COMPLEX = ContinuumGt.REGISTRATE
            .multiblock("continuum_launch_complex", LaunchPadMachine::new)
            .langValue("Continuum Launch Complex")
            .rotationState(RotationState.NON_Y_AXIS)
            .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
            .tooltips(LaunchComplexShape.TOOLTIPS.toArray(new Component[0]))
            .pattern(definition -> buildPattern(definition, LaunchComplexShape.SLICES, true))
            .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTCEu.id("block/multiblock/pyrolyse_oven"))
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> SWARM_RECEIVER = ContinuumGt.REGISTRATE
            .multiblock("continuum_swarm_array", SwarmReceiverMachine::new)
            .langValue("Swarm Receiver Array")
            .rotationState(RotationState.NON_Y_AXIS)
            .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
            .tooltips(SwarmReceiverShape.TOOLTIPS.toArray(new Component[0]))
            .pattern(definition -> buildPattern(definition, SwarmReceiverShape.SLICES, false))
            .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTCEu.id("block/multiblock/pyrolyse_oven"))
            .register();

    private static IBlockPattern buildControlPattern(MultiblockMachineDefinition definition) {
        var builder = MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP,
                RelativeDirection.RIGHT);
        for (String[] slice : SwarmControlShape.SLICES) builder = builder.slice(slice);

        var casing = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get());
        var frame = Predicates.blocks(BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("gtceu", "steel_frame")));
        var hatches = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1).setMaxGlobalLimited(2));

        return builder
                .where('S', Predicates.controller(definition))
                .where('B', casing)
                .where('H', hatches)
                .where('F', frame)
                .where('A', Predicates.air())
                .build();
    }

    private static IBlockPattern buildPattern(MultiblockMachineDefinition definition, String[][] slices,
                                              boolean launchComplex) {
        var builder = MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP,
                RelativeDirection.RIGHT);
        for (String[] slice : slices) builder = builder.slice(slice);

        var casing = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get());
        var frame = Predicates.blocks(BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("gtceu", "steel_frame")));
        var hatches = launchComplex ?
                Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                .setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(2)) :
                Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                        .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMinGlobalLimited(1)
                                .setMaxGlobalLimited(2));

        return builder
                .where('S', Predicates.controller(definition))
                .where('B', casing)
                .where('H', hatches)
                .where('F', frame)
                .where('A', Predicates.air())
                .build();
    }

    public static void init() {}
}
