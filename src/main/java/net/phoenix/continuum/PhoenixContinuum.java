package net.phoenix.continuum;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.phoenix.continuum.client.ContinuumClientSetup;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.machine.ContinuumMachines;
import net.phoenix.continuum.network.ContinuumNetwork;

import com.tterrag.registrate.util.entry.RegistryEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixContinuum.MOD_ID)
public class PhoenixContinuum {

    public static final String MOD_ID = "phoenix_continuum";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public static RegistryEntry<CreativeModeTab> CREATIVE_TAB = REGISTRATE
            .defaultCreativeTab(MOD_ID,
                    builder -> builder
                            .title(REGISTRATE.addLang("itemGroup", id("creative_tab"), "Phoenix Continuum"))
                            .icon(() -> ContinuumRegistry.ROCKET.asStack())
                            .build())
            .register();

    public PhoenixContinuum() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ContinuumConfigs.init();
        REGISTRATE.registerEventListeners(modEventBus);

        ContinuumRegistry.init();
        modEventBus.addGenericListener(com.gregtechceu.gtceu.api.machine.MachineDefinition.class,
                (com.gregtechceu.gtceu.api.GTCEuAPI.RegisterEvent<ResourceLocation, com.gregtechceu.gtceu.api.machine.MachineDefinition> event) -> ContinuumMachines.init());

        ContinuumNetwork.register();
        modEventBus.addListener(net.phoenix.continuum.data.ContinuumPackFolder::register);
        if (FMLLoader.getDist().isClient()) ContinuumClientSetup.init(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
