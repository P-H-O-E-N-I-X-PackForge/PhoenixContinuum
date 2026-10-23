package net.phoenix.continuum.integration.gtceu;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenix.continuum.machine.ContinuumMachines;
import net.phoenix.continuum.machine.LaunchComplexShape;
import net.phoenix.continuum.machine.LaunchPadMachine;
import net.phoenix.continuum.machine.SwarmReceiverMachine;
import net.phoenix.continuum.machine.SwarmReceiverShape;

public final class GtmBootstrap7 implements GtmBootstrap {

    @Override
    public void init(IEventBus modEventBus) {
        ContinuumGt.REGISTRATE.registerRegistrate();
    }

    @Override
    public void registerMachines() {
        ContinuumMachines.LAUNCH_COMPLEX = ContinuumGt.REGISTRATE
                .multiblock("continuum_launch_complex", LaunchPadMachine::new)
                .langValue("Continuum Launch Complex")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .tooltips(LaunchComplexShape.TOOLTIPS)
                .pattern(GtmBootstrap7::pattern)
                .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTCEu.id("block/multiblock/pyrolyse_oven"))
                .register();

        ContinuumMachines.SWARM_RECEIVER = ContinuumGt.REGISTRATE
                .multiblock("continuum_swarm_array", SwarmReceiverMachine::new)
                .langValue("Swarm Receiver Array")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .tooltips(SwarmReceiverShape.TOOLTIPS)
                .pattern(GtmBootstrap7::swarmPattern)
                .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTCEu.id("block/multiblock/pyrolyse_oven"))
                .register();
    }

    private static BlockPattern swarmPattern(MultiblockMachineDefinition definition) {
        var builder = FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.UP,
                RelativeDirection.BACK);
        for (String[] slice : SwarmReceiverShape.SLICES) builder = builder.aisle(slice);

        var casing = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get());
        var frame = Predicates.blocks(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "steel_frame")));

        return builder
                .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                .where('B', casing)
                .where('H', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                        .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMinGlobalLimited(1)
                                .setMaxGlobalLimited(2)))
                .where('F', frame)
                .where('A', Predicates.air())
                .build();
    }

    private static BlockPattern pattern(MultiblockMachineDefinition definition) {
        var builder = FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.UP,
                RelativeDirection.BACK);
        for (String[] slice : LaunchComplexShape.SLICES) builder = builder.aisle(slice);

        var casing = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get());
        var frame = Predicates.blocks(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "steel_frame")));

        return builder
                .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                .where('B', casing)
                .where('H', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                .setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(2)))
                .where('F', frame)
                .where('A', Predicates.air())
                .build();
    }
}
