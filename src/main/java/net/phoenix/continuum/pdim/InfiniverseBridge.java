package net.phoenix.continuum.pdim;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;

import net.commoble.infiniverse.api.InfiniverseAPI;

import java.util.function.Supplier;

/** The only place Infiniverse's classes are touched, so the game runs without it. */
final class InfiniverseBridge {

    private InfiniverseBridge() {}

    static ServerLevel create(MinecraftServer server, ResourceKey<Level> key, Supplier<LevelStem> stem) {
        return InfiniverseAPI.get().getOrCreateLevel(server, key, stem);
    }

    static void unload(MinecraftServer server, ResourceKey<Level> key) {
        InfiniverseAPI.get().markDimensionForUnregistration(server, key);
    }
}
