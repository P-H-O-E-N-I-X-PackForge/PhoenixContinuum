package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.client.ContinuumClientHooks;
import net.phoenix.continuum.common.SwarmReport;

public class S2COpenPowerPacket {

    private final SwarmReport report;
    private final net.minecraft.core.BlockPos pos;
    private final boolean open;

    public S2COpenPowerPacket(SwarmReport report, net.minecraft.core.BlockPos pos, boolean open) {
        this.report = report;
        this.pos = pos;
        this.open = open;
    }

    public S2COpenPowerPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.open = buf.readBoolean();
        this.report = SwarmReport.read(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(open);
        report.write(buf);
    }

    public static void handle(S2COpenPowerPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientHooks.openPower(msg.report, msg.pos, msg.open);
        });
        ctx.get().setPacketHandled(true);
    }
}
