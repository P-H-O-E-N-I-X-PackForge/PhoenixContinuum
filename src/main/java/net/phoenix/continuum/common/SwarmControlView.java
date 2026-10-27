package net.phoenix.continuum.common;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** What a Swarm Control screen shows: the targets, the chosen one's build, and the pad that builds it. */
public record SwarmControlView(BlockPos control, ResourceLocation target, List<Candidate> candidates, String padName,
                               boolean padFound, boolean online, long energyStored, long launchEnergy, int collectors, int segments,
                               int rockets, int swarmInFlight, int shellInFlight, long powerNow, long powerDone,
                               String unit, String note) {

    public record Candidate(ResourceLocation body, String name, boolean penrose, int swarm, int swarmMax, int shell,
                            int shellMax) {}

    public Candidate current() {
        for (Candidate candidate : candidates) {
            if (candidate.body().equals(target)) return candidate;
        }
        return null;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(control);
        buf.writeResourceLocation(target);
        buf.writeVarInt(candidates.size());
        for (Candidate c : candidates) {
            buf.writeResourceLocation(c.body());
            buf.writeUtf(c.name(), 64);
            buf.writeBoolean(c.penrose());
            buf.writeVarInt(c.swarm());
            buf.writeVarInt(c.swarmMax());
            buf.writeVarInt(c.shell());
            buf.writeVarInt(c.shellMax());
        }
        buf.writeUtf(padName, 64);
        buf.writeBoolean(padFound);
        buf.writeBoolean(online);
        buf.writeVarLong(energyStored);
        buf.writeVarLong(launchEnergy);
        buf.writeVarInt(collectors);
        buf.writeVarInt(segments);
        buf.writeVarInt(rockets);
        buf.writeVarInt(swarmInFlight);
        buf.writeVarInt(shellInFlight);
        buf.writeVarLong(powerNow);
        buf.writeVarLong(powerDone);
        buf.writeUtf(unit, 8);
        buf.writeUtf(note, 256);
    }

    public static SwarmControlView read(FriendlyByteBuf buf) {
        BlockPos control = buf.readBlockPos();
        ResourceLocation target = buf.readResourceLocation();
        int count = Math.min(buf.readVarInt(), 256);
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candidates.add(new Candidate(buf.readResourceLocation(), buf.readUtf(64), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new SwarmControlView(control, target, candidates, buf.readUtf(64), buf.readBoolean(),
                buf.readBoolean(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readUtf(8),
                buf.readUtf(256));
    }
}
