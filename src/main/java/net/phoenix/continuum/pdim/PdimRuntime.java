package net.phoenix.continuum.pdim;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraftforge.fml.ModList;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.PdimWorldgen;
import net.phoenix.continuum.config.ContinuumConfigs;

import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

/**
 * Personal dimensions made while the game runs. With Infiniverse installed (and the config left on) a body's
 * dimension is created the first time somebody needs it, from its {@link PdimWorldgen} spec, so a new body or a changed
 * spec needs no restart. Without it, dimensions are the files the game read when the world loaded.
 */
public final class PdimRuntime {

    private PdimRuntime() {}

    /** Whether dimensions can be made now. */
    public static boolean available() {
        return ModList.get().isLoaded("infiniverse") && ContinuumConfigs.INSTANCE != null &&
                ContinuumConfigs.INSTANCE.runtimePersonalDimensions;
    }

    /** The level of a body's personal dimension: the loaded one, or (if available) a new one made from its spec. */
    public static @Nullable ServerLevel level(MinecraftServer server, ResourceLocation body) {
        var key = PdimDimensions.keyFor(body);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) return existing;
        if (!available()) return null;
        try {
            mirrorOres(server, body);
            return InfiniverseBridge.create(server, key, () -> stem(server, body));
        } catch (RuntimeException e) {
            PhoenixContinuum.LOGGER.error("Could not make the personal dimension of {}: {}", body, e.getMessage());
            return null;
        }
    }

    /** Whether the body has (or can be given) a personal dimension. */
    public static boolean exists(MinecraftServer server, ResourceLocation body) {
        if (available()) return true;
        return server.registryAccess().registryOrThrow(Registries.LEVEL_STEM)
                .containsKey(PdimDimensions.keyFor(body).location());
    }

    /**
     * Drop a body's dimension so the next visit makes it again from its current spec (what was built stays on disk, so
     * a changed generator only affects chunks not yet made). Players in it are moved out by Infiniverse.
     */
    public static boolean unload(MinecraftServer server, ResourceLocation body) {
        if (!available() || server.getLevel(PdimDimensions.keyFor(body)) == null) return false;
        InfiniverseBridge.unload(server, PdimDimensions.keyFor(body));
        return true;
    }

    /** Give the body's dimension the GregTech ores of the dimension its spec says it is like. */
    public static void mirrorOres(MinecraftServer server, ResourceLocation body) {
        var spec = PdimWorldgen.resolve(body, net.phoenix.continuum.data.ContinuumData.body(body));
        if (spec.gtOresFrom() == null || !ModList.get().isLoaded("gtceu")) return;
        PdimGt.mirror(server, net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, spec.gtOresFrom()),
                PdimDimensions.keyFor(body));
    }

    static LevelStem stem(MinecraftServer server, ResourceLocation body) {
        var spec = PdimWorldgen.resolve(body, net.phoenix.continuum.data.ContinuumData.body(body));
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        return LevelStem.CODEC.parse(ops, spec.toDimensionJson()).result()
                .orElseThrow(() -> new IllegalStateException("The generator of " + body + " is not valid"));
    }
}
