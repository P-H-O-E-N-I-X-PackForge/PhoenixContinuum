package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.client.ContinuumClientState;
import net.phoenix.continuum.common.ScheduleService;

import java.util.ArrayList;
import java.util.List;

public class S2CSchedulesPacket {

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

    public static void handle(S2CSchedulesPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) ContinuumClientState.acceptSchedules(msg.pad, msg.views);
        });
        ctx.get().setPacketHandled(true);
    }
}
