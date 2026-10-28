package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.common.SwarmReceiver;
import net.phoenix.continuum.integration.gtceu.GregTech;

public class C2SReceiverRefreshPacket {

    private final BlockPos pos;

    public C2SReceiverRefreshPacket(BlockPos pos) {
        this.pos = pos;
    }

    public C2SReceiverRefreshPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static void handle(C2SReceiverRefreshPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            double range = net.phoenix.continuum.config.ContinuumConfigs.INSTANCE.launchRangeBlocks;
            if (!player.level().isLoaded(msg.pos) ||
                    player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(msg.pos)) > range * range) {
                return;
            }
            SwarmReceiver receiver = null;
            if (player.level().getBlockEntity(msg.pos) instanceof SwarmReceiver plain) receiver = plain;
            if (receiver == null && GregTech.loaded()) {
                receiver = net.phoenix.continuum.integration.gtceu.ContinuumGt.receiverAt(player.level(), msg.pos);
            }
            if (receiver != null) {
                ContinuumNetwork.sendToPlayer(player, new S2COpenPowerPacket(receiver.report(player.server), msg.pos,
                        false));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
