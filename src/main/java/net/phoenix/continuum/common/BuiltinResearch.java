package net.phoenix.continuum.common;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.phoenix.continuum.api.ContinuumHost;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Continuum's own research, used unless a companion installs a different one with
 * {@link ContinuumHost#setResearch}. A team has a set of unlocked research ids, a set of flags and an optional discipline,
 * all saved with the world. They are granted with {@code /continuumadmin research}, {@code ContinuumAPI.grantResearch} or
 * the KubeJS {@code Continuum} binding, so a pack decides what earns each one.
 */
public final class BuiltinResearch extends SavedData {

    /** The provider Continuum uses until another one is installed. */
    public static final ContinuumHost.Research PROVIDER = new ContinuumHost.Research() {

        @Override
        public boolean isUnlocked(UUID team, ResourceLocation node) {
            BuiltinResearch data = current();
            return data != null && data.unlocked.getOrDefault(team, Set.of()).contains(node);
        }

        @Override
        public boolean hasFlag(UUID team, String flag) {
            BuiltinResearch data = current();
            return data != null && data.flags.getOrDefault(team, Set.of()).contains(flag);
        }

        @Override
        public @Nullable String getDiscipline(UUID team) {
            BuiltinResearch data = current();
            return data == null ? null : data.discipline.get(team);
        }
    };

    private final Map<UUID, Set<ResourceLocation>> unlocked = new HashMap<>();
    private final Map<UUID, Set<String>> flags = new HashMap<>();
    private final Map<UUID, String> discipline = new HashMap<>();

    private static @Nullable BuiltinResearch current() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : get(server);
    }

    public static BuiltinResearch get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BuiltinResearch::new, BuiltinResearch::load), "phoenix_continuum_research");
    }

    // ---------------------------------------------------------------- changing

    public boolean grant(UUID team, ResourceLocation node) {
        boolean changed = unlocked.computeIfAbsent(team, k -> new LinkedHashSet<>()).add(node);
        if (changed) setDirty();
        return changed;
    }

    public boolean revoke(UUID team, ResourceLocation node) {
        Set<ResourceLocation> set = unlocked.get(team);
        boolean changed = set != null && set.remove(node);
        if (changed) setDirty();
        return changed;
    }

    public boolean setFlag(UUID team, String flag, boolean on) {
        Set<String> set = flags.computeIfAbsent(team, k -> new LinkedHashSet<>());
        boolean changed = on ? set.add(flag) : set.remove(flag);
        if (changed) setDirty();
        return changed;
    }

    public void setDiscipline(UUID team, @Nullable String name) {
        if (name == null || name.isEmpty()) discipline.remove(team);
        else discipline.put(team, name);
        setDirty();
    }

    public Set<ResourceLocation> unlockedOf(UUID team) {
        return unlocked.getOrDefault(team, Set.of());
    }

    // ---------------------------------------------------------------- saving

    public static BuiltinResearch load(CompoundTag tag, HolderLookup.Provider registries) {
        BuiltinResearch data = new BuiltinResearch();
        ListTag teams = tag.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teams.size(); i++) {
            CompoundTag entry = teams.getCompound(i);
            if (!entry.hasUUID("Team")) continue;
            UUID team = entry.getUUID("Team");
            Set<ResourceLocation> nodes = new LinkedHashSet<>();
            for (Tag node : entry.getList("Unlocked", Tag.TAG_STRING)) {
                ResourceLocation id = ResourceLocation.tryParse(node.getAsString());
                if (id != null) nodes.add(id);
            }
            data.unlocked.put(team, nodes);
            Set<String> flagSet = new HashSet<>();
            for (Tag flag : entry.getList("Flags", Tag.TAG_STRING)) flagSet.add(flag.getAsString());
            data.flags.put(team, flagSet);
            if (entry.contains("Discipline")) data.discipline.put(team, entry.getString("Discipline"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Set<UUID> all = new HashSet<>(unlocked.keySet());
        all.addAll(flags.keySet());
        all.addAll(discipline.keySet());
        ListTag teams = new ListTag();
        for (UUID team : all) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Team", team);
            ListTag nodes = new ListTag();
            for (ResourceLocation node : unlocked.getOrDefault(team, Set.of())) {
                nodes.add(StringTag.valueOf(node.toString()));
            }
            entry.put("Unlocked", nodes);
            ListTag flagList = new ListTag();
            for (String flag : flags.getOrDefault(team, Set.of())) flagList.add(StringTag.valueOf(flag));
            entry.put("Flags", flagList);
            if (discipline.containsKey(team)) entry.putString("Discipline", discipline.get(team));
            teams.add(entry);
        }
        tag.put("Teams", teams);
        return tag;
    }
}
