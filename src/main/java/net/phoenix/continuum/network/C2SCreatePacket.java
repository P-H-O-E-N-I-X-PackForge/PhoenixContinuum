package net.phoenix.continuum.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix.continuum.data.ContentCreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** An operator asks the server to create a new system or body from the tuner. */
public class C2SCreatePacket implements CustomPacketPayload {

    public static final Type<C2SCreatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_continuum", "c2s_create"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCreatePacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SCreatePacket::encode, C2SCreatePacket::new);

    private final boolean system;
    private final ResourceLocation id;
    private final String json;

    public C2SCreatePacket(boolean system, ResourceLocation id, String json) {
        this.system = system;
        this.id = id;
        this.json = json;
    }

    public C2SCreatePacket(RegistryFriendlyByteBuf buf) {
        this.system = buf.readBoolean();
        this.id = buf.readResourceLocation();
        this.json = buf.readUtf(16384);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(system);
        buf.writeResourceLocation(id);
        buf.writeUtf(json, 16384);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SCreatePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
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
    }
}
