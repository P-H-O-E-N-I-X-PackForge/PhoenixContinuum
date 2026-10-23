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
        ADJUST,
        DELETE
    }

    private final Action action;
    private final ResourceLocation body;
    private final float gravity;
    private final float daySpeed;
    private final float lockedTime;
    private final boolean orbitView;
    private final boolean fixedSky;

    public C2SPdimPacket(Action action, ResourceLocation body, float gravity, boolean orbitView) {
        this(action, body, gravity, 1.0f, -1.0f, orbitView);
    }

    public C2SPdimPacket(Action action, ResourceLocation body, float gravity, float daySpeed, float lockedTime,
                         boolean orbitView) {
        this(action, body, gravity, daySpeed, lockedTime, orbitView, false);
    }

    public C2SPdimPacket(Action action, ResourceLocation body, float gravity, float daySpeed, float lockedTime,
                         boolean orbitView, boolean fixedSky) {
        this.orbitView = orbitView;
        this.fixedSky = fixedSky;
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
        this.fixedSky = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeResourceLocation(body);
        buf.writeFloat(gravity);
        buf.writeFloat(daySpeed);
        buf.writeFloat(lockedTime);
        buf.writeBoolean(orbitView);
        buf.writeBoolean(fixedSky);
    }

    public static void handle(C2SPdimPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            String error = switch (msg.action) {
                case LEAVE -> PdimActions.leave(player);
                case DELETE -> PdimActions.delete(player, msg.body) ? null : "You have no personal dimension there.";
                case ADJUST -> PdimActions.adjust(player, msg.body, msg.gravity, msg.daySpeed, msg.lockedTime,
                        msg.orbitView, msg.fixedSky);
                default -> {
                    String made = PdimActions.createAndEnter(player, msg.body, msg.gravity, msg.orbitView);
                    
                    if (made == null && (msg.daySpeed != 1.0f || msg.lockedTime >= 0.0f || msg.fixedSky)) {
                        PdimActions.adjust(player, msg.body, msg.gravity, msg.daySpeed, msg.lockedTime, msg.orbitView,
                                msg.fixedSky);
                    }
                    yield made;
                }
            };
            if (error != null) player.displayClientMessage(Component.literal(error), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
