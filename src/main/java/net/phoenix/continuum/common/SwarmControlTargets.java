package net.phoenix.continuum.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Which star or black hole each Swarm Control block is looking at. Kept here so any block can use it. */
public final class SwarmControlTargets extends SavedData {

    private static final String ID = "phoenix_continuum_swarm_control";

    private final Map<String, ResourceLocation> targets = new HashMap<>();

    public static SwarmControlTargets get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SwarmControlTargets::new, SwarmControlTargets::load), ID);
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    public @Nullable ResourceLocation get(Level level, BlockPos pos) {
        return targets.get(key(level, pos));
    }

    public void set(Level level, BlockPos pos, ResourceLocation target) {
        targets.put(key(level, pos), target);
        setDirty();
    }

    public static SwarmControlTargets load(CompoundTag tag, HolderLookup.Provider registries) {
        SwarmControlTargets data = new SwarmControlTargets();
        for (Tag t : tag.getList("Targets", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.targets.put(e.getString("Key"), ResourceLocation.parse(e.getString("Target")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : targets.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString("Key", entry.getKey());
            e.putString("Target", entry.getValue().toString());
            list.add(e);
        }
        tag.put("Targets", list);
        return tag;
    }
}
