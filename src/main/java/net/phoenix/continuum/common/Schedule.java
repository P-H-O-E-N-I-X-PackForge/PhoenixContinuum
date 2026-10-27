package net.phoenix.continuum.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A launch a pad makes by itself: one at a set time, or one that repeats, as many times as it is allowed to. It is
 * about a pad, not a player; the pad's owner is who it launches as.
 */
public final class Schedule {

    public final UUID id;
    public final UUID team;
    public final UUID owner;
    public final String ownerName;
    public final String padDimension;
    public final long padPos;

    public ResourceLocation destination;
    public Mission.Type type = Mission.Type.SURVEY;
    public int probes;

    /** How many rockets a run sends (extraction, deployment and rescue runs only). */
    public int count = 1;
    public String expedition = "";
    public List<String> crew = new ArrayList<>();

    /** Milliseconds between runs; 0 is a single run. */
    public long intervalMillis;

    /** How many runs it makes before it stops; 0 is no limit. */
    public int cap;
    public int runs;
    public boolean autoCollect;
    public boolean enabled;

    public long nextFire;
    public long retryAt;

    /** Why the last attempt could not launch: the team is told once per reason. */
    public String lastFailure = "";

    public Schedule(UUID id, UUID team, UUID owner, String ownerName, String padDimension, long padPos,
                    ResourceLocation destination) {
        this.id = id;
        this.team = team;
        this.owner = owner;
        this.ownerName = ownerName;
        this.padDimension = padDimension;
        this.padPos = padPos;
        this.destination = destination;
    }

    public boolean finished() {
        return cap > 0 && runs >= cap;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Team", team);
        tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putString("Dimension", padDimension);
        tag.putLong("Pad", padPos);
        tag.putString("Destination", destination.toString());
        tag.putByte("Type", (byte) type.ordinal());
        tag.putInt("Probes", probes);
        tag.putInt("Count", count);
        tag.putString("Expedition", expedition);
        ListTag crewList = new ListTag();
        for (String member : crew) crewList.add(StringTag.valueOf(member));
        tag.put("Crew", crewList);
        tag.putLong("Interval", intervalMillis);
        tag.putInt("Cap", cap);
        tag.putInt("Runs", runs);
        tag.putBoolean("AutoCollect", autoCollect);
        tag.putBoolean("Enabled", enabled);
        tag.putLong("NextFire", nextFire);
        tag.putString("LastFailure", lastFailure);
        return tag;
    }

    public static Schedule load(CompoundTag tag) {
        Schedule schedule = new Schedule(tag.getUUID("Id"), tag.getUUID("Team"), tag.getUUID("Owner"),
                tag.getString("OwnerName"), tag.getString("Dimension"), tag.getLong("Pad"),
                ResourceLocation.parse(tag.getString("Destination")));
        schedule.type = Mission.Type.values()[Math.max(0,
                Math.min(tag.getByte("Type"), Mission.Type.values().length - 1))];
        schedule.probes = tag.getInt("Probes");
        schedule.count = Math.max(1, tag.getInt("Count"));
        schedule.expedition = tag.getString("Expedition");
        for (Tag t : tag.getList("Crew", Tag.TAG_STRING)) schedule.crew.add(t.getAsString());
        schedule.intervalMillis = tag.getLong("Interval");
        schedule.cap = tag.getInt("Cap");
        schedule.runs = tag.getInt("Runs");
        schedule.autoCollect = tag.getBoolean("AutoCollect");
        schedule.enabled = tag.getBoolean("Enabled");
        schedule.nextFire = tag.getLong("NextFire");
        schedule.lastFailure = tag.getString("LastFailure");
        return schedule;
    }
}
