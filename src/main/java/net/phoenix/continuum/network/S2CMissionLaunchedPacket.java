package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.client.ContinuumClientHooks;

public class S2CMissionLaunchedPacket {

    private final boolean accepted;
    private final ResourceLocation destination;
    private final BlockPos pad;
    private final String message;

    public S2CMissionLaunchedPacket(boolean accepted, ResourceLocation destination, BlockPos pad, String message) {
        this.accepted = accepted;
        this.destination = destination;
        this.pad = pad;
        this.message = message;
    }

    public S2CMissionLaunchedPacket(FriendlyByteBuf buf) {
        this.accepted = buf.readBoolean();
        this.destination = buf.readResourceLocation();
        this.pad = buf.readBlockPos();
        this.message = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(accepted);
        buf.writeResourceLocation(destination);
        buf.writeBlockPos(pad);
        buf.writeUtf(message, 256);
    }

    public static void handle(S2CMissionLaunchedPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                ContinuumClientHooks.missionResult(msg.accepted, msg.destination, msg.pad, msg.message);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
