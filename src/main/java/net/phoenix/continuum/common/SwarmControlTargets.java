package net.phoenix.continuum.common;

import net.minecraft.core.BlockPos;
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
    private final Map<String, Float> scales = new HashMap<>();

    public static final float[] SIZES = { 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f };

    public float scale(Level level, BlockPos pos) {
        return scales.getOrDefault(key(level, pos), 1.0f);
    }

    /** One step bigger (+1) or smaller (-1) among the sizes. */
    public float step(Level level, BlockPos pos, int by) {
        float now = scale(level, pos);
        int index = 2;
        for (int i = 0; i < SIZES.length; i++) {
            if (Math.abs(SIZES[i] - now) < 0.01f) index = i;
        }
        float next = SIZES[Math.max(0, Math.min(SIZES.length - 1, index + by))];
        scales.put(key(level, pos), next);
        setDirty();
        return next;
    }

    public static SwarmControlTargets get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SwarmControlTargets::load, SwarmControlTargets::new, ID);
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

    public static SwarmControlTargets load(CompoundTag tag) {
        SwarmControlTargets data = new SwarmControlTargets();
        for (Tag t : tag.getList("Targets", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.targets.put(e.getString("Key"), new ResourceLocation(e.getString("Target")));
        }
        for (Tag t : tag.getList("Scales", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.scales.put(e.getString("Key"), e.getFloat("Scale"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var entry : targets.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString("Key", entry.getKey());
            e.putString("Target", entry.getValue().toString());
            list.add(e);
        }
        tag.put("Targets", list);
        ListTag scaleList = new ListTag();
        for (var entry : scales.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString("Key", entry.getKey());
            e.putFloat("Scale", entry.getValue());
            scaleList.add(e);
        }
        tag.put("Scales", scaleList);
        return tag;
    }
}
