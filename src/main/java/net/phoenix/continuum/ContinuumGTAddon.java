package net.phoenix.continuum;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.RecipeOutput;

@GTAddon(PhoenixContinuum.MOD_ID)
public class ContinuumGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return net.phoenix.continuum.integration.gtceu.ContinuumGt.REGISTRATE;
    }

    @Override
    public void addRecipes(RecipeOutput provider) {
        ContinuumRecipes.init(provider);
        SwarmControlRecipes.init(provider);
    }
}
