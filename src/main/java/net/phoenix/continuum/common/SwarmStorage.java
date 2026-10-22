package net.phoenix.continuum.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** The energy held in each Swarm Receiver Array's capacitor, by dimension and position. */
public final class SwarmStorage extends SavedData {

    private static final String ID = "phoenix_continuum_swarm_storage";

    private final Map<String, Long> stored = new HashMap<>();

    public static SwarmStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SwarmStorage::new, SwarmStorage::load), ID);
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    public long get(Level level, BlockPos pos) {
        return stored.getOrDefault(key(level, pos), 0L);
    }

    public void set(Level level, BlockPos pos, long amount) {
        if (amount <= 0) stored.remove(key(level, pos));
        else stored.put(key(level, pos), amount);
        setDirty();
    }

    public static SwarmStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        SwarmStorage data = new SwarmStorage();
        for (Tag t : tag.getList("Stored", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.stored.put(e.getString("Key"), e.getLong("Amount"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : stored.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString("Key", entry.getKey());
            e.putLong("Amount", entry.getValue());
            list.add(e);
        }
        tag.put("Stored", list);
        return tag;
    }

    /**
     * Puts power into the capacitor and fills the hatches from it. {@code fill} takes what the hatches have room for
     * and returns how much it kept. Returns how much of the offered power the array kept.
     */
    public static long exchange(MinecraftServer server, Level level, BlockPos pos, long offered,
                                java.util.function.LongUnaryOperator fill) {
        SwarmStorage data = get(server);
        long capacity = (long) Math.min(9.0E18,
                Math.max(0.0, net.phoenix.continuum.config.ContinuumConfigs.INSTANCE.swarmArrayCapacitorEU));
        long held = Math.min(data.get(level, pos), capacity);

        // the capacitor feeds the hatches first, then the swarm's own power tops it up (or goes straight to them)
        long available = held + offered;
        long delivered = fill.applyAsLong(available);
        long left = available - delivered;
        long kept = Math.min(left, capacity);
        data.set(level, pos, kept);
        // what the capacitor could not hold is lost, and only the part of it that came from the swarm counts as kept
        long fromSwarm = Math.max(0L, Math.min(offered, delivered + kept - held));
        return fromSwarm;
    }
}
