package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.common.ContinuumServerEvents;

public class C2SRequestStatePacket {

    public C2SRequestStatePacket() {}

    public C2SRequestStatePacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static void handle(C2SRequestStatePacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ContinuumServerEvents.sendState(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
