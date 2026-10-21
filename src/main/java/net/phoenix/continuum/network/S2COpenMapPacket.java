package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.client.ContinuumClientHooks;

public class S2COpenMapPacket {

    private final BlockPos pad;

    public S2COpenMapPacket(BlockPos pad) {
        this.pad = pad;
    }

    public S2COpenMapPacket(FriendlyByteBuf buf) {
        this.pad = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pad);
    }

    public static void handle(S2COpenMapPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientHooks.openMap(msg.pad);
        });
        ctx.get().setPacketHandled(true);
    }
}
