package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix.continuum.client.ContinuumClientHooks;
import net.phoenix.continuum.common.SwarmControlView;

public class S2CSwarmControlPacket implements CustomPacketPayload {

    public static final Type<S2CSwarmControlPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "s2_c_swarm_control_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSwarmControlPacket> STREAM_CODEC = StreamCodec
            .ofMember(S2CSwarmControlPacket::encode, S2CSwarmControlPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final SwarmControlView view;
    private final boolean open;

    public S2CSwarmControlPacket(SwarmControlView view, boolean open) {
        this.view = view;
        this.open = open;
    }

    public S2CSwarmControlPacket(FriendlyByteBuf buf) {
        this.open = buf.readBoolean();
        this.view = SwarmControlView.read(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        view.write(buf);
    }

    public static void handle(S2CSwarmControlPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientHooks.swarmControl(msg.view, msg.open);
        });
    }
}
