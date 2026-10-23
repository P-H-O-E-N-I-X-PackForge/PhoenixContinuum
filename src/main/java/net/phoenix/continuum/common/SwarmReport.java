package net.phoenix.continuum.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.phoenix.continuum.machine.PadTiers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SwarmReport(String title, String unit, long stored, long capacity, long maxOut, long teamPerTick,
                          String note, List<Swarms.Entry> entries) {

    public static SwarmReport build(MinecraftServer server, UUID team, String title, long stored, long capacity,
                                    long maxOut, String note) {
        List<Swarms.Entry> entries = Swarms.entries(server, team);
        return new SwarmReport(title, PadTiers.energyUnit(), stored, capacity, maxOut, Swarms.total(entries), note,
                entries);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(title, 64);
        buf.writeUtf(unit, 8);
        buf.writeVarLong(stored);
        buf.writeVarLong(capacity);
        buf.writeVarLong(maxOut);
        buf.writeVarLong(teamPerTick);
        buf.writeUtf(note, 256);
        buf.writeVarInt(entries.size());
        for (Swarms.Entry entry : entries) {
            buf.writeResourceLocation(entry.body());
            buf.writeUtf(entry.name(), 64);
            buf.writeBoolean(entry.shell());
            buf.writeVarInt(entry.level());
            buf.writeVarInt(entry.max());
            buf.writeVarLong(entry.perTick());
        }
    }

    public static SwarmReport read(FriendlyByteBuf buf) {
        String title = buf.readUtf(64);
        String unit = buf.readUtf(8);
        long stored = buf.readVarLong();
        long capacity = buf.readVarLong();
        long maxOut = buf.readVarLong();
        long teamPerTick = buf.readVarLong();
        String note = buf.readUtf(256);
        int count = buf.readVarInt();
        List<Swarms.Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation body = buf.readResourceLocation();
            entries.add(new Swarms.Entry(body, buf.readUtf(64), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarLong()));
        }
        return new SwarmReport(title, unit, stored, capacity, maxOut, teamPerTick, note, entries);
    }
}
