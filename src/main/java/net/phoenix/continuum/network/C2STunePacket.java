package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.TuningPatches;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/** An operator edit from the tuner: set one value in a body's server-side patch, or reset the body. */
public class C2STunePacket {

    private static final int MAX_VALUE = 8192;

    private final boolean reset;
    private final ResourceLocation body;
    private final String path;
    private final String valueJson;

    public C2STunePacket(ResourceLocation body, String path, String valueJson) {
        this.reset = false;
        this.body = body;
        this.path = path;
        this.valueJson = valueJson;
    }

    /** Removes every server-side edit of the body. */
    public static C2STunePacket reset(ResourceLocation body) {
        return new C2STunePacket(true, body);
    }

    private C2STunePacket(boolean reset, ResourceLocation body) {
        this.reset = reset;
        this.body = body;
        this.path = "";
        this.valueJson = "";
    }

    public C2STunePacket(FriendlyByteBuf buf) {
        this.reset = buf.readBoolean();
        this.body = buf.readResourceLocation();
        this.path = buf.readUtf(128);
        this.valueJson = buf.readUtf(MAX_VALUE);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(reset);
        buf.writeResourceLocation(body);
        buf.writeUtf(path, 128);
        buf.writeUtf(valueJson, MAX_VALUE);
    }

    public static void handle(C2STunePacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            boolean allowed = player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
            if (!allowed) {
                player.displayClientMessage(Component.literal("Only operators can tune Continuum."), true);
                return;
            }
            if (ContinuumData.body(msg.body) == null) return;

            String error;
            if (msg.reset) {
                error = TuningPatches.reset(msg.body);
            } else {
                JsonElement value;
                try {
                    value = msg.valueJson.isEmpty() ? null : JsonParser.parseString(msg.valueJson);
                } catch (RuntimeException e) {
                    player.displayClientMessage(Component.literal("That value is not valid JSON."), true);
                    return;
                }
                error = TuningPatches.set(msg.body, msg.path, value);
            }
            if (error != null) {
                player.displayClientMessage(Component.literal("Tuner: " + error), true);
                return;
            }
            ContinuumData.reloadNow(player.server);
        });
        ctx.get().setPacketHandled(true);
    }
}
