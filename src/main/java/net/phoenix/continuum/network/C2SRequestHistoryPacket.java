package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.common.ContinuumTeamData;

public class C2SRequestHistoryPacket {

    public C2SRequestHistoryPacket() {}

    public C2SRequestHistoryPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static void handle(C2SRequestHistoryPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var team = net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
            net.phoenix.continuum.network.ContinuumNetwork.sendToPlayer(player,
                    new S2CHistoryPacket(ContinuumTeamData.get(player.server).history(team)));
        });
        ctx.get().setPacketHandled(true);
    }
}
