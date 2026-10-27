package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What a Swarm Control hologram should show: whether it is on, what it is looking at, and how built it is. */
public class S2CHologramPacket implements CustomPacketPayload {

    public static final Type<S2CHologramPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "s2_c_hologram_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CHologramPacket> STREAM_CODEC = StreamCodec
            .ofMember(S2CHologramPacket::encode, S2CHologramPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record State(BlockPos pos, boolean active, ResourceLocation target, int swarm, int swarmMax, int shell,
                        int shellMax, int offsetX, int offsetZ) {}

    private final State state;

    public S2CHologramPacket(State state) {
        this.state = state;
    }

    public S2CHologramPacket(FriendlyByteBuf buf) {
        this.state = new State(buf.readBlockPos(), buf.readBoolean(), buf.readResourceLocation(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
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
    }

    public static void handle(S2CHologramPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) net.phoenix.continuum.client.render.HologramClient.accept(msg.state);
        });
    }
}
