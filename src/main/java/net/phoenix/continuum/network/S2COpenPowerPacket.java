package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix.continuum.client.ContinuumClientHooks;
import net.phoenix.continuum.common.SwarmReport;

public class S2COpenPowerPacket implements CustomPacketPayload {

    public static final Type<S2COpenPowerPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "s2_c_open_power_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenPowerPacket> STREAM_CODEC = StreamCodec
            .ofMember(S2COpenPowerPacket::encode, S2COpenPowerPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

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

    public static void handle(S2COpenPowerPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientHooks.openPower(msg.report, msg.pos, msg.open);
        });
    }
}
