package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.client.ContinuumClientState;

public class S2CPadStockPacket {

    private final BlockPos pad;
    private final boolean buses;
    private final ItemStack rocket;
    private final int probes;
    private final int kits;
    private final int rockets;
    private final int collectors;
    private final int segments;
    private final int penroseCollectors;
    private final int penroseSegments;

    public S2CPadStockPacket(BlockPos pad, boolean buses, ItemStack rocket, int probes, int kits, int rockets,
                             int collectors, int segments, int penroseCollectors, int penroseSegments) {
        this.penroseCollectors = penroseCollectors;
        this.penroseSegments = penroseSegments;
        this.collectors = collectors;
        this.segments = segments;
        this.rockets = rockets;
        this.pad = pad;
        this.buses = buses;
        this.rocket = rocket;
        this.probes = probes;
        this.kits = kits;
    }

    public S2CPadStockPacket(FriendlyByteBuf buf) {
        this.pad = buf.readBlockPos();
        this.buses = buf.readBoolean();
        this.rocket = buf.readItem();
        this.probes = buf.readVarInt();
        this.kits = buf.readVarInt();
        this.rockets = buf.readVarInt();
        this.collectors = buf.readVarInt();
        this.segments = buf.readVarInt();
        this.penroseCollectors = buf.readVarInt();
        this.penroseSegments = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pad);
        buf.writeBoolean(buses);
        buf.writeItem(rocket);
        buf.writeVarInt(probes);
        buf.writeVarInt(kits);
        buf.writeVarInt(rockets);
        buf.writeVarInt(collectors);
        buf.writeVarInt(segments);
        buf.writeVarInt(penroseCollectors);
        buf.writeVarInt(penroseSegments);
    }

    public static void handle(S2CPadStockPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                ContinuumClientState.acceptPadStock(msg.pad, msg.buses, msg.rocket, msg.probes, msg.kits,
                        msg.rockets, msg.collectors, msg.segments, msg.penroseCollectors, msg.penroseSegments);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
