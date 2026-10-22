package net.phoenix.continuum;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.phoenix.continuum.client.ContinuumClientSetup;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.integration.gtceu.GregTech;
import net.phoenix.continuum.network.ContinuumNetwork;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixContinuum.MOD_ID)
public class PhoenixContinuum {

    public static final String MOD_ID = "phoenix_continuum";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public PhoenixContinuum() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ContinuumConfigs.init();
        ContinuumRegistry.register(modEventBus);
        // with GregTech: the launch complex multiblock and its recipes; without it: the energy-powered Launch Pad
        if (GregTech.loaded()) {
            net.phoenix.continuum.integration.gtceu.ContinuumGt.init(modEventBus);
        } else {
            LOGGER.info("GregTech is not installed: missions launch from the Launch Pad, powered by Forge Energy");
        }
        net.phoenix.continuum.integration.solaris.SolarisSites.init();

        ContinuumNetwork.register();
        modEventBus.addListener(net.phoenix.continuum.data.ContinuumPackFolder::register);
        if (FMLLoader.getDist().isClient()) ContinuumClientSetup.init(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
