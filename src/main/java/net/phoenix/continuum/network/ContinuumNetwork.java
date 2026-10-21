package net.phoenix.continuum.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

@SuppressWarnings("removal")
public final class ContinuumNetwork {

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("phoenix_continuum", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int id = 0;

    private ContinuumNetwork() {}

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void register() {
        CHANNEL.registerMessage(id++, S2CContinuumDataPacket.class, S2CContinuumDataPacket::encode, S2CContinuumDataPacket::new, S2CContinuumDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CContinuumStatePacket.class, S2CContinuumStatePacket::encode, S2CContinuumStatePacket::new, S2CContinuumStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2COpenMapPacket.class, S2COpenMapPacket::encode, S2COpenMapPacket::new, S2COpenMapPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CMissionLaunchedPacket.class, S2CMissionLaunchedPacket::encode, S2CMissionLaunchedPacket::new, S2CMissionLaunchedPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CPadStockPacket.class, S2CPadStockPacket::encode, S2CPadStockPacket::new, S2CPadStockPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SRequestPadStockPacket.class, C2SRequestPadStockPacket::encode, C2SRequestPadStockPacket::new, C2SRequestPadStockPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CPdimStatePacket.class, S2CPdimStatePacket::encode, S2CPdimStatePacket::new, S2CPdimStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SPdimPacket.class, C2SPdimPacket::encode, C2SPdimPacket::new, C2SPdimPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SConfluxTravelPacket.class, C2SConfluxTravelPacket::encode, C2SConfluxTravelPacket::new, C2SConfluxTravelPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SCollectAllPacket.class, C2SCollectAllPacket::encode, C2SCollectAllPacket::new, C2SCollectAllPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SRequestHistoryPacket.class, C2SRequestHistoryPacket::encode, C2SRequestHistoryPacket::new, C2SRequestHistoryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CHistoryPacket.class, S2CHistoryPacket::encode, S2CHistoryPacket::new, S2CHistoryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SRefitPacket.class, C2SRefitPacket::encode, C2SRefitPacket::new, C2SRefitPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SRequestStatePacket.class, C2SRequestStatePacket::encode, C2SRequestStatePacket::new, C2SRequestStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SLaunchMissionPacket.class, C2SLaunchMissionPacket::encode, C2SLaunchMissionPacket::new, C2SLaunchMissionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SCollectMissionPacket.class, C2SCollectMissionPacket::encode, C2SCollectMissionPacket::new, C2SCollectMissionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2STunePacket.class, C2STunePacket::encode, C2STunePacket::new, C2STunePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SCreatePacket.class, C2SCreatePacket::encode, C2SCreatePacket::new, C2SCreatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
