package net.phoenix.continuum;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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

    public PhoenixContinuum(IEventBus modEventBus, ModContainer container) {
        ContinuumConfigs.init();
        ContinuumRegistry.register(modEventBus);
        
        if (GregTech.loaded()) {
            net.phoenix.continuum.integration.gtceu.ContinuumGt.init(modEventBus);
        } else {
            LOGGER.info("GregTech is not installed: missions launch from the Launch Pad, powered by Forge Energy");
        }
        net.phoenix.continuum.integration.solaris.SolarisSites.init();

        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e
                .enqueueWork(net.phoenix.continuum.integration.chronicles.ChroniclesBridge::setup));
        modEventBus.addListener(net.phoenix.continuum.data.ContinuumPackFolder::register);
        modEventBus.addListener(net.phoenix.continuum.pdim.PdimWorldgenPack::register);
        modEventBus.addListener(net.phoenix.continuum.block.LaunchPadBlockEntity::registerCapabilities);
        modEventBus.addListener(net.phoenix.continuum.block.SwarmReceiverBlockEntity::registerCapabilities);
        modEventBus.addListener(net.phoenix.continuum.block.SwarmControlBlockEntity::registerCapabilities);
        if (FMLEnvironment.dist.isClient()) ContinuumClientSetup.init(modEventBus);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        ContinuumNetwork.register(event.registrar("1"));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
