package net.phoenix.continuum;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

@GTAddon
public class ContinuumGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return net.phoenix.continuum.integration.gtceu.ContinuumGt.REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return PhoenixContinuum.MOD_ID;
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        ContinuumRecipes.init(provider);
    }
}
