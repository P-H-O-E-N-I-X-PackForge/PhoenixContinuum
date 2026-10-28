package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix.continuum.common.SwarmReceiver;
import net.phoenix.continuum.integration.gtceu.GregTech;

public class C2SReceiverRefreshPacket implements CustomPacketPayload {

    public static final Type<C2SReceiverRefreshPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "c2_s_receiver_refresh_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SReceiverRefreshPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SReceiverRefreshPacket::encode, C2SReceiverRefreshPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

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

    public static void handle(C2SReceiverRefreshPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
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
    }
}
