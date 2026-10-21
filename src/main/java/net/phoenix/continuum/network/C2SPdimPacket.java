package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.pdim.PdimActions;

public class C2SPdimPacket {

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

    public C2SPdimPacket(Action action, ResourceLocation body, float gravity) {
        this(action, body, gravity, 1.0f, -1.0f);
    }

    public C2SPdimPacket(Action action, ResourceLocation body, float gravity, float daySpeed, float lockedTime) {
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
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeResourceLocation(body);
        buf.writeFloat(gravity);
        buf.writeFloat(daySpeed);
        buf.writeFloat(lockedTime);
    }

    public static void handle(C2SPdimPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            String error = switch (msg.action) {
                case LEAVE -> PdimActions.leave(player);
                case ADJUST -> PdimActions.adjust(player, msg.body, msg.gravity, msg.daySpeed, msg.lockedTime);
                default -> PdimActions.createAndEnter(player, msg.body, msg.gravity);
            };
            if (error != null) player.displayClientMessage(Component.literal(error), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
