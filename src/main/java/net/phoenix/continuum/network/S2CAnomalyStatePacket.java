package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix.continuum.client.ContinuumClientState;

import java.util.HashSet;
import java.util.Set;

public class S2CAnomalyStatePacket implements CustomPacketPayload {

    public static final Type<S2CAnomalyStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "s2_c_anomaly_state_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CAnomalyStatePacket> STREAM_CODEC = StreamCodec
            .ofMember(S2CAnomalyStatePacket::encode, S2CAnomalyStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final Set<String> found;

    public S2CAnomalyStatePacket(Set<String> found) {
        this.found = found;
    }

    public S2CAnomalyStatePacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.found = new HashSet<>();
        for (int i = 0; i < count; i++) found.add(buf.readUtf(256));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(found.size());
        for (String key : found) buf.writeUtf(key, 256);
    }

    public static void handle(S2CAnomalyStatePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientState.acceptAnomalies(msg.found);
        });
    }
}
