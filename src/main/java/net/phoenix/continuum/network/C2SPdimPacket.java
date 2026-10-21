package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix.continuum.pdim.PdimActions;

public class C2SPdimPacket implements CustomPacketPayload {

    public static final Type<C2SPdimPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "c2_s_pdim_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPdimPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SPdimPacket::encode, C2SPdimPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        ENTER,
        LEAVE,
        ADJUST
    }

    private final Action action;
    private final ResourceLocation body;
    private final float gravity;
    private final float daySpeed;
    private final float lockedTime;
    private final boolean orbitView;

    public C2SPdimPacket(Action action, ResourceLocation body, float gravity, boolean orbitView) {
        this(action, body, gravity, 1.0f, -1.0f, orbitView);
    }

    public C2SPdimPacket(Action action, ResourceLocation body, float gravity, float daySpeed, float lockedTime,
                         boolean orbitView) {
        this.orbitView = orbitView;
        this.action = action;
        this.body = body;
        this.gravity = gravity;
        this.daySpeed = daySpeed;
        this.lockedTime = lockedTime;
    }

    public C2SPdimPacket(FriendlyByteBuf buf) {
        this.action = Action.values()[Math.max(0, Math.min(buf.readByte(), Action.values().length - 1))];
        this.body = buf.readResourceLocation();
        this.gravity = buf.readFloat();
        this.daySpeed = buf.readFloat();
        this.lockedTime = buf.readFloat();
        this.orbitView = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeResourceLocation(body);
        buf.writeFloat(gravity);
        buf.writeFloat(daySpeed);
        buf.writeFloat(lockedTime);
        buf.writeBoolean(orbitView);
    }

    public static void handle(C2SPdimPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ((net.minecraft.server.level.ServerPlayer) ctx.player());
            if (player == null) return;

            String error = switch (msg.action) {
                case LEAVE -> PdimActions.leave(player);
                case ADJUST -> PdimActions.adjust(player, msg.body, msg.gravity, msg.daySpeed, msg.lockedTime,
                        msg.orbitView);
                default -> PdimActions.createAndEnter(player, msg.body, msg.gravity, msg.orbitView);
            };
            if (error != null) player.displayClientMessage(Component.literal(error), true);
        });
    }
}
