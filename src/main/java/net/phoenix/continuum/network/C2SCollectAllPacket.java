package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.common.ContinuumMissions;

public class C2SCollectAllPacket {

    public C2SCollectAllPacket() {}

    public C2SCollectAllPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static void handle(C2SCollectAllPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ContinuumMissions.collectAll(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
