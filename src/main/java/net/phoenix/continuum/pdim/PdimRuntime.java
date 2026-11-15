package net.phoenix.continuum.pdim;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.fml.ModList;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.PdimWorldgen;
import net.phoenix.continuum.config.ContinuumConfigs;

import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

public final class PdimRuntime {

    private PdimRuntime() {}

    public static boolean available() {
        return ModList.get().isLoaded("infiniverse") && ContinuumConfigs.INSTANCE != null &&
                ContinuumConfigs.INSTANCE.runtimePersonalDimensions;
    }

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

    public static boolean exists(MinecraftServer server, ResourceLocation body) {
        if (available()) return true;
        return server.registryAccess().registryOrThrow(Registries.LEVEL_STEM)
                .containsKey(PdimDimensions.keyFor(body).location());
    }

    public static boolean unload(MinecraftServer server, ResourceLocation body) {
        if (!available() || server.getLevel(PdimDimensions.keyFor(body)) == null) return false;
        InfiniverseBridge.unload(server, PdimDimensions.keyFor(body));
        return true;
    }

    public static void mirrorOres(MinecraftServer server, ResourceLocation body) {
        var spec = PdimWorldgen.resolve(body, net.phoenix.continuum.data.ContinuumData.body(body));
        if (spec.gtOresFrom() == null || !ModList.get().isLoaded("gtceu")) return;
        PdimGt.mirror(server, net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, spec.gtOresFrom()),
                PdimDimensions.keyFor(body));
    }

    static LevelStem stem(MinecraftServer server, ResourceLocation body) {
        var spec = PdimWorldgen.resolve(body, net.phoenix.continuum.data.ContinuumData.body(body));
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        return LevelStem.CODEC.parse(ops, spec.toDimensionJson()).getOrThrow(IllegalStateException::new);
    }
}
