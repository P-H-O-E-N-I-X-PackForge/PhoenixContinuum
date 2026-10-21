package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.client.ContinuumClientHooks;
import net.phoenix.continuum.common.ContinuumStateSnapshot;

public class S2CContinuumStatePacket {

    private final ContinuumStateSnapshot snapshot;

    public S2CContinuumStatePacket(ContinuumStateSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public S2CContinuumStatePacket(FriendlyByteBuf buf) {
        this.snapshot = ContinuumStateSnapshot.read(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        snapshot.write(buf);
    }

    public static void handle(S2CContinuumStatePacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientHooks.acceptState(msg.snapshot);
        });
        ctx.get().setPacketHandled(true);
    }
}
