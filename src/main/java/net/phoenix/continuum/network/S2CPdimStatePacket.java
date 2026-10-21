package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix.continuum.client.pdim.PdimClientState;

import java.util.HashMap;
import java.util.Map;

public class S2CPdimStatePacket implements CustomPacketPayload {

    public static final Type<S2CPdimStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "s2_c_pdim_state_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPdimStatePacket> STREAM_CODEC = StreamCodec
            .ofMember(S2CPdimStatePacket::encode, S2CPdimStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final boolean inside;
    private final float gravity;
    private final float daySpeed;
    private final float lockedTime;
    private final boolean orbitView;
    private final Map<ResourceLocation, Float> mine;
    private final Map<ResourceLocation, net.phoenix.continuum.pdim.PdimCosts.Cost> costs;
    private final boolean everyVisit;

    public S2CPdimStatePacket(boolean inside, float gravity, float daySpeed, float lockedTime,
                              boolean orbitView, Map<ResourceLocation, Float> mine,
                              Map<ResourceLocation, net.phoenix.continuum.pdim.PdimCosts.Cost> costs,
                              boolean everyVisit) {
        this.inside = inside;
        this.gravity = gravity;
        this.daySpeed = daySpeed;
        this.lockedTime = lockedTime;
        this.orbitView = orbitView;
        this.mine = mine;
        this.costs = costs;
        this.everyVisit = everyVisit;
    }

    public S2CPdimStatePacket(FriendlyByteBuf buf) {
        this.inside = buf.readBoolean();
        this.gravity = buf.readFloat();
        this.daySpeed = buf.readFloat();
        this.lockedTime = buf.readFloat();
        this.orbitView = buf.readBoolean();
        int count = buf.readVarInt();
        this.mine = new HashMap<>();
        for (int i = 0; i < count; i++) mine.put(buf.readResourceLocation(), buf.readFloat());
        int costCount = buf.readVarInt();
        this.costs = new HashMap<>();
        for (int i = 0; i < costCount; i++) {
            ResourceLocation body = buf.readResourceLocation();
            costs.put(body, new net.phoenix.continuum.pdim.PdimCosts.Cost(
                    buf.readResourceLocation(), buf.readVarInt()));
        }
        this.everyVisit = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(inside);
        buf.writeFloat(gravity);
        buf.writeFloat(daySpeed);
        buf.writeFloat(lockedTime);
        buf.writeBoolean(orbitView);
        buf.writeVarInt(mine.size());
        mine.forEach((body, g) -> {
            buf.writeResourceLocation(body);
            buf.writeFloat(g);
        });
        buf.writeVarInt(costs.size());
        costs.forEach((body, cost) -> {
            buf.writeResourceLocation(body);
            buf.writeResourceLocation(cost.item());
            buf.writeVarInt(cost.count());
        });
        buf.writeBoolean(everyVisit);
    }

    public static void handle(S2CPdimStatePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient())
                PdimClientState.accept(msg.inside, msg.gravity, msg.daySpeed, msg.lockedTime, msg.orbitView, msg.mine, msg.costs, msg.everyVisit);
        });
    }
}
