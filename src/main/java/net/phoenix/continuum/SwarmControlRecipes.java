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

/** The Swarm Control Array: how it is built, and the recipe that keeps it online. */
public final class SwarmControlRecipes {

    private SwarmControlRecipes() {}

    public static void init(RecipeOutput provider) {
        ASSEMBLER_RECIPES.recipeBuilder("continuum_swarm_control_array")
                .inputItems(GTMachines.HULL[IV].asStack())
                .inputItems(CASING_STEEL_SOLID, 8)
                .inputItems(frameGt, Steel, 4)
                .inputItems(SENSOR_IV, 2)
                .inputItems(EMITTER_IV, 4)
                .inputItems(FIELD_GENERATOR_IV, 1)
                .inputItems(CustomTags.IV_CIRCUITS, 4)
                .inputFluids(SolderingAlloy, 576)
                .outputItems(ContinuumMachines.SWARM_CONTROL)
                .duration(600).EUt(VA[IV])
                .save(provider);

        // the uplink: a minute of helium coolant and power keeps the hologram and the controls online
        ContinuumMachines.SWARM_CONTROL_RECIPES.recipeBuilder("swarm_control_uplink")
                .inputFluids(Helium, 100)
                .duration(1200).EUt(VA[EV])
                .save(provider);
    }
}
