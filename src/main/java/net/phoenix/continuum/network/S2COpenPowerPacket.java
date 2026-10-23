package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.client.ContinuumClientHooks;
import net.phoenix.continuum.common.SwarmReport;

public class S2COpenPowerPacket {

    private final SwarmReport report;

    public S2COpenPowerPacket(SwarmReport report) {
        this.report = report;
    }

    public S2COpenPowerPacket(FriendlyByteBuf buf) {
        this.report = SwarmReport.read(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        report.write(buf);
    }

    public static void handle(S2COpenPowerPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientHooks.openPower(msg.report);
        });
        ctx.get().setPacketHandled(true);
    }
}
