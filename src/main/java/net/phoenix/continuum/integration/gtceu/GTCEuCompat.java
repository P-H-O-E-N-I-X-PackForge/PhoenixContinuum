package net.phoenix.continuum.integration.gtceu;

import net.minecraftforge.eventbus.api.IEventBus;
import net.phoenix.continuum.PhoenixContinuum;

import org.jetbrains.annotations.NotNull;

/**
 * Picks the GregTech integration that matches the installed GregTech. Continuum is compiled against GregTech 7.5.3; the
 * parts written for GregTech 8 live in their own source set and are only loaded when GregTech 8 is installed.
 */
public final class GTCEuCompat {

    private static GtmBootstrap bootstrap;

    private GTCEuCompat() {}

    public static boolean usesNewApi() {
        try {
            Class.forName("com.gregtechceu.gtceu.api.multiblock.MultiblockWorldSavedData", false,
                    GTCEuCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static GtmBootstrap bootstrap() {
        if (bootstrap == null) {
            boolean eight = usesNewApi();
            String name = "net.phoenix.continuum.integration.gtceu." + (eight ? "GtmBootstrap8" : "GtmBootstrap7");
            try {
                bootstrap = (GtmBootstrap) Class.forName(name).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("Could not start the GregTech integration " + name, e);
            }
            PhoenixContinuum.LOGGER.info("Using the {} GregTech integration", eight ? "GregTech 8" : "GregTech 7");
        }
        return bootstrap;
    }

    public static void init(@NotNull IEventBus modEventBus) {
        bootstrap().init(modEventBus);
    }
}
