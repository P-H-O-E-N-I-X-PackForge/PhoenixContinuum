package net.phoenix.continuum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.data.ContentCreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class C2SCreatePacket {

    private final boolean system;
    private final ResourceLocation id;
    private final String json;

    public C2SCreatePacket(boolean system, ResourceLocation id, String json) {
        this.system = system;
        this.id = id;
        this.json = json;
    }

    public C2SCreatePacket(FriendlyByteBuf buf) {
        this.system = buf.readBoolean();
        this.id = buf.readResourceLocation();
        this.json = buf.readUtf(16384);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(system);
        buf.writeResourceLocation(id);
        buf.writeUtf(json, 16384);
    }

    public static void handle(C2SCreatePacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !net.phoenix.continuum.common.ContinuumGates.check(player, net.phoenix.continuum.common.ContinuumGates.SWARMS)) return;
            if (player == null) return;
            boolean allowed = player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
            if (!allowed) {
                player.displayClientMessage(Component.literal("Only operators can create Continuum content."), true);
                return;
            }
            JsonObject def;
            try {
                def = JsonParser.parseString(msg.json).getAsJsonObject();
            } catch (RuntimeException e) {
                player.displayClientMessage(Component.literal("That definition is not valid JSON."), true);
                return;
            }
            var result = msg.system ? ContentCreator.createSystem(player.server, msg.id, def) :
                    ContentCreator.createBody(player.server, msg.id, def);
            player.sendSystemMessage(Component.literal(result.message()));
        });
        ctx.get().setPacketHandled(true);
    }
}
