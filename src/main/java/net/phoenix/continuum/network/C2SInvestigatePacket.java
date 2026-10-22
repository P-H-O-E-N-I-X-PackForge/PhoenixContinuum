package net.phoenix.continuum.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix.continuum.common.AnomalyActions;

public class C2SInvestigatePacket implements CustomPacketPayload {

    public static final Type<C2SInvestigatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "c2_s_investigate_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SInvestigatePacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SInvestigatePacket::encode, C2SInvestigatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation body;
    private final String anomaly;

    public C2SInvestigatePacket(ResourceLocation body, String anomaly) {
        this.body = body;
        this.anomaly = anomaly;
    }

    public C2SInvestigatePacket(FriendlyByteBuf buf) {
        this.body = buf.readResourceLocation();
        this.anomaly = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(body);
        buf.writeUtf(anomaly, 64);
    }

    public static void handle(C2SInvestigatePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ((ServerPlayer) ctx.player());
            if (player == null) return;
            String error = AnomalyActions.investigate(player, msg.body, msg.anomaly);
            if (error != null) player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
        });
    }
}
