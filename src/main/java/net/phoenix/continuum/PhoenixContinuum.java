package net.phoenix.continuum;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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

    public static RegistryEntry<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = REGISTRATE
            .defaultCreativeTab(MOD_ID,
                    builder -> builder
                            .title(REGISTRATE.addLang("itemGroup", id("creative_tab"), "Phoenix Continuum"))
                            .icon(() -> ContinuumRegistry.ROCKET.asStack())
                            .build())
            .register();

    public PhoenixContinuum(IEventBus modEventBus, ModContainer container) {
        ContinuumConfigs.init();
        REGISTRATE.registerEventListeners(modEventBus);

        ContinuumRegistry.init();
        ContinuumMachines.init();

        modEventBus.addListener(this::registerPayloads);
        if (FMLEnvironment.dist.isClient()) ContinuumClientSetup.init(modEventBus);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        ContinuumNetwork.register(event.registrar("1"));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
