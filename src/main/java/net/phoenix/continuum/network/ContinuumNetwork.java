package net.phoenix.continuum.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ContinuumNetwork {

    private ContinuumNetwork() {}

    public static void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload message) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(message);
    }

    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player,
                                    net.minecraft.network.protocol.common.custom.CustomPacketPayload message) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, message);
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(S2CContinuumDataPacket.TYPE, S2CContinuumDataPacket.STREAM_CODEC,
                S2CContinuumDataPacket::handle);
        registrar.playToClient(S2CContinuumStatePacket.TYPE, S2CContinuumStatePacket.STREAM_CODEC,
                S2CContinuumStatePacket::handle);
        registrar.playToClient(S2COpenMapPacket.TYPE, S2COpenMapPacket.STREAM_CODEC, S2COpenMapPacket::handle);
        registrar.playToClient(S2COpenPowerPacket.TYPE, S2COpenPowerPacket.STREAM_CODEC, S2COpenPowerPacket::handle);
        registrar.playToClient(S2CMissionLaunchedPacket.TYPE, S2CMissionLaunchedPacket.STREAM_CODEC,
                S2CMissionLaunchedPacket::handle);
        registrar.playToClient(S2CPadStockPacket.TYPE,
                S2CPadStockPacket.STREAM_CODEC,
                S2CPadStockPacket::handle);
        registrar.playToServer(C2SRequestPadStockPacket.TYPE,
                C2SRequestPadStockPacket.STREAM_CODEC,
                C2SRequestPadStockPacket::handle);
        registrar.playToClient(S2CPdimStatePacket.TYPE,
                S2CPdimStatePacket.STREAM_CODEC,
                S2CPdimStatePacket::handle);
        registrar.playToServer(C2SPdimPacket.TYPE,
                C2SPdimPacket.STREAM_CODEC,
                C2SPdimPacket::handle);
        registrar.playToServer(C2SConfluxTravelPacket.TYPE,
                C2SConfluxTravelPacket.STREAM_CODEC,
                C2SConfluxTravelPacket::handle);
        registrar.playToServer(C2SCollectAllPacket.TYPE,
                C2SCollectAllPacket.STREAM_CODEC,
                C2SCollectAllPacket::handle);
        registrar.playToServer(C2SRequestHistoryPacket.TYPE,
                C2SRequestHistoryPacket.STREAM_CODEC,
                C2SRequestHistoryPacket::handle);
        registrar.playToClient(S2CHistoryPacket.TYPE,
                S2CHistoryPacket.STREAM_CODEC,
                S2CHistoryPacket::handle);
        registrar.playToServer(C2SRefitPacket.TYPE,
                C2SRefitPacket.STREAM_CODEC,
                C2SRefitPacket::handle);
        registrar.playToServer(C2SRequestStatePacket.TYPE, C2SRequestStatePacket.STREAM_CODEC,
                C2SRequestStatePacket::handle);
        registrar.playToServer(C2SLaunchMissionPacket.TYPE, C2SLaunchMissionPacket.STREAM_CODEC,
                C2SLaunchMissionPacket::handle);
        registrar.playToServer(C2SCollectMissionPacket.TYPE, C2SCollectMissionPacket.STREAM_CODEC,
                C2SCollectMissionPacket::handle);
        registrar.playToServer(C2SInvestigatePacket.TYPE, C2SInvestigatePacket.STREAM_CODEC,
                C2SInvestigatePacket::handle);
        registrar.playToClient(S2CAnomalyStatePacket.TYPE, S2CAnomalyStatePacket.STREAM_CODEC,
                S2CAnomalyStatePacket::handle);
        registrar.playToServer(C2STunePacket.TYPE, C2STunePacket.STREAM_CODEC, C2STunePacket::handle);
        registrar.playToServer(C2SCreatePacket.TYPE, C2SCreatePacket.STREAM_CODEC, C2SCreatePacket::handle);
        registrar.playToServer(C2SScheduleActionPacket.TYPE, C2SScheduleActionPacket.STREAM_CODEC,
                C2SScheduleActionPacket::handle);
        registrar.playToClient(S2CSchedulesPacket.TYPE, S2CSchedulesPacket.STREAM_CODEC,
                S2CSchedulesPacket::handle);
        registrar.playToServer(C2SSwarmControlPacket.TYPE, C2SSwarmControlPacket.STREAM_CODEC,
                C2SSwarmControlPacket::handle);
        registrar.playToClient(S2CSwarmControlPacket.TYPE, S2CSwarmControlPacket.STREAM_CODEC,
                S2CSwarmControlPacket::handle);
        registrar.playToClient(S2CHologramPacket.TYPE, S2CHologramPacket.STREAM_CODEC, S2CHologramPacket::handle);
        registrar.playToServer(C2SReceiverRefreshPacket.TYPE, C2SReceiverRefreshPacket.STREAM_CODEC,
                C2SReceiverRefreshPacket::handle);
    }
}
