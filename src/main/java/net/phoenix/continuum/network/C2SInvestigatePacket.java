package net.phoenix.continuum.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.common.AnomalyActions;

public class C2SInvestigatePacket {

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

    public static void handle(C2SInvestigatePacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !net.phoenix.continuum.common.ContinuumGates.check(player, net.phoenix.continuum.common.ContinuumGates.ANOMALIES)) return;
            if (player == null) return;
            String error = AnomalyActions.investigate(player, msg.body, msg.anomaly);
            if (error != null) player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
        });
        ctx.get().setPacketHandled(true);
    }
}
