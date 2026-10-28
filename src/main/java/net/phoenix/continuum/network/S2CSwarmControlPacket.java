package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.client.ContinuumClientHooks;
import net.phoenix.continuum.common.SwarmControlView;

public class S2CSwarmControlPacket {

    private final SwarmControlView view;
    private final boolean open;

    public S2CSwarmControlPacket(SwarmControlView view, boolean open) {
        this.view = view;
        this.open = open;
    }

    public S2CSwarmControlPacket(FriendlyByteBuf buf) {
        this.open = buf.readBoolean();
        this.view = SwarmControlView.read(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        view.write(buf);
    }

    public static void handle(S2CSwarmControlPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientHooks.swarmControl(msg.view, msg.open);
        });
        ctx.get().setPacketHandled(true);
    }
}
