package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.common.ContinuumMissions;

public class C2SRequestPadStockPacket {

    private final BlockPos pad;

    public C2SRequestPadStockPacket(BlockPos pad) {
        this.pad = pad;
    }

    public C2SRequestPadStockPacket(FriendlyByteBuf buf) {
        this.pad = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pad);
    }

    public static void handle(C2SRequestPadStockPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.level().isLoaded(msg.pad)) return;

            double range = ContinuumConfigs.INSTANCE.launchRangeBlocks + 8.0;
            if (player.distanceToSqr(msg.pad.getX() + 0.5, msg.pad.getY() + 0.5, msg.pad.getZ() + 0.5) >
                    range * range) {
                return;
            }

            var stock = ContinuumMissions.padStock(player, msg.pad);
            net.phoenix.continuum.network.ContinuumNetwork.sendToPlayer(player,
                    new S2CPadStockPacket(msg.pad, stock.buses(), stock.rocket(), stock.probes(), stock.kits(),
                            stock.rockets()));
        });
        ctx.get().setPacketHandled(true);
    }
}
