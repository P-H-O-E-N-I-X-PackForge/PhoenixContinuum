package net.phoenix.continuum;

import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.data.recipes.RecipeOutput;
import net.phoenix.continuum.machine.ContinuumMachines;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.CASING_STEEL_SOLID;
import static com.gregtechceu.gtceu.common.data.GTItems.*;
import static com.gregtechceu.gtceu.common.data.GTMaterials.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES;

public final class ContinuumRecipes {

    private ContinuumRecipes() {}

    private static com.gregtechceu.gtceu.api.data.chemical.material.Material weave() {
        var found = com.gregtechceu.gtceu.api.registry.GTRegistries.MATERIALS.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("phoenixcore", "ensorcelled_weave"));
        return found != null && !"null".equals(found.getName()) && found.hasFluid() ? found : Titanium.get();
    }

    public static void init(RecipeOutput provider) {
        ASSEMBLER_RECIPES.recipeBuilder("continuum_probe")
                .inputItems(plate, Steel, 2)
                .inputItems(rod, Steel, 2)
                .inputItems(ELECTRIC_MOTOR_MV)
                .inputItems(CustomTags.MV_CIRCUITS, 1)
                .inputFluids(SolderingAlloy, 72)
                .outputItems(ContinuumRegistry.PROBE.asStack(2))
                .duration(200).EUt(VA[MV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_repair_kit")
                .inputItems(plate, Steel, 4)
                .inputItems(screw, Steel, 4)
                .inputItems(plate, Aluminium, 2)
                .inputItems(CustomTags.MV_CIRCUITS, 1)
                .inputFluids(SolderingAlloy, 72)
                .outputItems(ContinuumRegistry.REPAIR_KIT.asStack(2))
                .duration(160).EUt(VA[MV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_rocket")
                .inputItems(plate, Titanium, 16)
                .inputItems(frameGt, Titanium, 1)
                .inputItems(pipeLargeFluid, Titanium, 2)
                .inputItems(ELECTRIC_MOTOR_HV, 2)
                .inputItems(ELECTRIC_PUMP_HV, 1)
                .inputItems(CustomTags.HV_CIRCUITS, 4)
                .inputFluids(SolderingAlloy, 288)
                .outputItems(ContinuumRegistry.ROCKET)
                .duration(600).EUt(VA[HV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_hull_refit_mk2")
                .inputItems(plate, Titanium, 8)
                .inputItems(plate, StainlessSteel, 8)
                .inputItems(CustomTags.HV_CIRCUITS, 2)
                .inputFluids(SolderingAlloy, 144)
                .outputItems(ContinuumRegistry.REFIT_MK2)
                .duration(400).EUt(VA[HV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_hull_refit_mk3")
                .inputItems(plate, TungstenSteel, 8)
                .inputItems(plate, Titanium, 8)
                .inputItems(CustomTags.EV_CIRCUITS, 2)
                .inputFluids(SolderingAlloy, 288)
                .outputItems(ContinuumRegistry.REFIT_MK3)
                .duration(500).EUt(VA[EV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_hull_refit_mk4")
                .inputItems(plate, Iridium, 4)
                .inputItems(plate, TungstenSteel, 8)
                .inputItems(CustomTags.IV_CIRCUITS, 2)
                .inputFluids(SolderingAlloy, 576)
                .inputFluids(weave().getFluid(288))
                .outputItems(ContinuumRegistry.REFIT_MK4)
                .duration(600).EUt(VA[IV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_module_hull")
                .inputItems(plate, Titanium, 6)
                .inputItems(plate, StainlessSteel, 4)
                .inputItems(screw, Titanium, 4)
                .inputFluids(SolderingAlloy, 144)
                .outputItems(ContinuumRegistry.MODULE_HULL)
                .duration(300).EUt(VA[HV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_module_overdrive")
                .inputItems(ELECTRIC_MOTOR_EV, 2)
                .inputItems(plate, Titanium, 2)
                .inputItems(CustomTags.EV_CIRCUITS, 2)
                .inputFluids(SolderingAlloy, 144)
                .outputItems(ContinuumRegistry.MODULE_OVERDRIVE)
                .duration(400).EUt(VA[EV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_module_interlock")
                .inputItems(SENSOR_EV, 1)
                .inputItems(EMITTER_EV, 1)
                .inputItems(plate, StainlessSteel, 2)
                .inputItems(CustomTags.EV_CIRCUITS, 4)
                .inputFluids(SolderingAlloy, 144)
                .outputItems(ContinuumRegistry.MODULE_INTERLOCK)
                .duration(400).EUt(VA[EV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_module_stellar_shield")
                .inputItems(plate, TungstenSteel, 6)
                .inputItems(plate, Titanium, 4)
                .inputItems(SENSOR_IV, 2)
                .inputItems(EMITTER_IV, 2)
                .inputItems(CustomTags.IV_CIRCUITS, 4)
                .inputFluids(SolderingAlloy, 288)
                .outputItems(ContinuumRegistry.MODULE_STELLAR_SHIELD)
                .duration(600).EUt(VA[IV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_module_singularity_shield")
                .inputItems(plate, Iridium, 6)
                .inputItems(plate, TungstenSteel, 4)
                .inputItems(SENSOR_LuV, 2)
                .inputItems(EMITTER_LuV, 2)
                .inputItems(CustomTags.LuV_CIRCUITS, 4)
                .inputFluids(SolderingAlloy, 576)
                .outputItems(ContinuumRegistry.MODULE_SINGULARITY_SHIELD)
                .duration(800).EUt(VA[LuV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_swarm_collector")
                .inputItems(plate, Aluminium, 4)
                .inputItems(SENSOR_HV, 1)
                .inputItems(EMITTER_HV, 1)
                .inputItems(CustomTags.HV_CIRCUITS, 2)
                .inputFluids(SolderingAlloy, 144)
                .outputItems(ContinuumRegistry.SWARM_COLLECTOR.asStack(4))
                .duration(300).EUt(VA[HV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_shell_segment")
                .inputItems(plate, TungstenSteel, 6)
                .inputItems(plate, Titanium, 4)
                .inputItems(frameGt, Titanium, 1)
                .inputItems(CustomTags.EV_CIRCUITS, 2)
                .inputFluids(SolderingAlloy, 288)
                .outputItems(ContinuumRegistry.SHELL_SEGMENT.asStack(2))
                .duration(400).EUt(VA[EV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_penrose_collector")
                .inputItems(plate, Iridium, 4)
                .inputItems(SENSOR_IV, 1)
                .inputItems(EMITTER_IV, 1)
                .inputItems(CustomTags.IV_CIRCUITS, 2)
                .inputFluids(SolderingAlloy, 288)
                .outputItems(ContinuumRegistry.PENROSE_COLLECTOR.asStack(4))
                .duration(300).EUt(VA[IV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_penrose_segment")
                .inputItems(plate, TungstenSteel, 6)
                .inputItems(plate, Iridium, 4)
                .inputItems(frameGt, TungstenSteel, 1)
                .inputItems(CustomTags.LuV_CIRCUITS, 2)
                .inputFluids(SolderingAlloy, 576)
                .outputItems(ContinuumRegistry.PENROSE_SEGMENT.asStack(2))
                .duration(400).EUt(VA[LuV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_swarm_array")
                .inputItems(GTMachines.HULL[IV].asStack())
                .inputItems(CASING_STEEL_SOLID, 8)
                .inputItems(frameGt, Steel, 4)
                .inputItems(SENSOR_IV, 2)
                .inputItems(EMITTER_IV, 2)
                .inputItems(CustomTags.IV_CIRCUITS, 4)
                .inputFluids(SolderingAlloy, 576)
                .outputItems(ContinuumMachines.SWARM_RECEIVER)
                .duration(600).EUt(VA[IV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_swarm_array")
                .inputItems(GTMachines.HULL[IV].asStack())
                .inputItems(CASING_STEEL_SOLID, 8)
                .inputItems(frameGt, Steel, 4)
                .inputItems(SENSOR_IV, 2)
                .inputItems(EMITTER_IV, 2)
                .inputItems(CustomTags.IV_CIRCUITS, 4)
                .inputFluids(SolderingAlloy, 576)
                .outputItems(ContinuumMachines.SWARM_RECEIVER)
                .duration(600).EUt(VA[IV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder("continuum_launch_complex")
                .inputItems(GTMachines.HULL[EV].asStack())
                .inputItems(CASING_STEEL_SOLID, 8)
                .inputItems(frameGt, Steel, 4)
                .inputItems(ELECTRIC_MOTOR_EV, 4)
                .inputItems(ROBOT_ARM_EV, 2)
                .inputItems(ELECTRIC_PUMP_HV, 2)
                .inputItems(CustomTags.EV_CIRCUITS, 4)
                .inputFluids(SolderingAlloy, 576)
                .outputItems(ContinuumMachines.LAUNCH_COMPLEX)
                .duration(600).EUt(VA[EV])
                .save(provider);
    }
}
