package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.client.ContinuumClientState;

import java.util.HashSet;
import java.util.Set;

public class S2CAnomalyStatePacket {

    private final Set<String> found;

    public S2CAnomalyStatePacket(Set<String> found) {
        this.found = found;
    }

    public S2CAnomalyStatePacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.found = new HashSet<>();
        for (int i = 0; i < count; i++) found.add(buf.readUtf(256));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(found.size());
        for (String key : found) buf.writeUtf(key, 256);
    }

    public static void handle(S2CAnomalyStatePacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientState.acceptAnomalies(msg.found);
        });
        ctx.get().setPacketHandled(true);
    }
}
