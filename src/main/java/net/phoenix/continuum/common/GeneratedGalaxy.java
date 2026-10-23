package net.phoenix.continuum.common;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.data.SystemGenerator;

import org.jetbrains.annotations.Nullable;

public final class GeneratedGalaxy extends SavedData {

    public record Setup(boolean active, long seed, int count) {

        public static final Setup NONE = new Setup(false, 0L, 0);
    }

    private Setup setup = Setup.NONE;

    public static GeneratedGalaxy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GeneratedGalaxy::new, GeneratedGalaxy::load), "phoenix_continuum_galaxy");
    }

    public Setup setup() {
        return setup;
    }

    public void set(Setup newSetup) {
        setup = newSetup;
        setDirty();
    }

    public static @Nullable SystemGenerator.Result currentResult(@Nullable ContinuumSystem home) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        Setup setup;
        try {
            setup = get(server).setup();
        } catch (RuntimeException e) {
            
            return null;
        }
        if (!setup.active() || setup.count() <= 0) return null;
        var at = home == null ? SystemGenerator.ContinuumSystemView.ORIGIN :
                new SystemGenerator.ContinuumSystemView(home.galaxyX(), home.galaxyY(), home.galaxyZ());
        return SystemGenerator.generate(setup.seed(), setup.count(), at);
    }

    public static GeneratedGalaxy load(CompoundTag tag, HolderLookup.Provider registries) {
        GeneratedGalaxy data = new GeneratedGalaxy();
        data.setup = new Setup(tag.getBoolean("Active"), tag.getLong("Seed"), tag.getInt("Count"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Active", setup.active());
        tag.putLong("Seed", setup.seed());
        tag.putInt("Count", setup.count());
        return tag;
    }
}
