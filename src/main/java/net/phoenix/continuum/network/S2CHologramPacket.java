package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/** What a Swarm Control hologram should show: whether it is on, what it is looking at, and how built it is. */
public class S2CHologramPacket {

    public record State(BlockPos pos, boolean active, ResourceLocation target, int swarm, int swarmMax, int shell,
                        int shellMax, int offsetX, int offsetZ, float scale, int kind) {

        public static final int CONTROL = 0;
        public static final int RECEIVER = 1;
    }

    private final State state;

    public S2CHologramPacket(State state) {
        this.state = state;
    }

    public S2CHologramPacket(FriendlyByteBuf buf) {
        this.state = new State(buf.readBlockPos(), buf.readBoolean(), buf.readResourceLocation(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(state.pos());
        buf.writeBoolean(state.active());
        buf.writeResourceLocation(state.target());
        buf.writeVarInt(state.swarm());
        buf.writeVarInt(state.swarmMax());
        buf.writeVarInt(state.shell());
        buf.writeVarInt(state.shellMax());
        buf.writeVarInt(state.offsetX());
        buf.writeVarInt(state.offsetZ());
        buf.writeFloat(state.scale());
        buf.writeVarInt(state.kind());
    }

    public static void handle(S2CHologramPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) net.phoenix.continuum.client.render.HologramClient.accept(msg.state);
        });
        ctx.get().setPacketHandled(true);
    }
}
