package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix.continuum.client.ContinuumClientState;
import net.phoenix.continuum.common.ScheduleService;

import java.util.ArrayList;
import java.util.List;

public class S2CSchedulesPacket implements CustomPacketPayload {

    public static final Type<S2CSchedulesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "s2_c_schedules_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSchedulesPacket> STREAM_CODEC = StreamCodec
            .ofMember(S2CSchedulesPacket::encode, S2CSchedulesPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final BlockPos pad;
    private final List<ScheduleService.View> views;

    public S2CSchedulesPacket(BlockPos pad, List<ScheduleService.View> views) {
        this.pad = pad;
        this.views = views;
    }

    public S2CSchedulesPacket(FriendlyByteBuf buf) {
        this.pad = buf.readBlockPos();
        int size = Math.min(buf.readVarInt(), 64);
        this.views = new ArrayList<>();
        for (int i = 0; i < size; i++) views.add(ScheduleService.View.read(buf));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pad);
        buf.writeVarInt(views.size());
        for (ScheduleService.View view : views) view.write(buf);
    }

    public static void handle(S2CSchedulesPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientState.acceptSchedules(msg.pad, msg.views);
        });
    }
}
