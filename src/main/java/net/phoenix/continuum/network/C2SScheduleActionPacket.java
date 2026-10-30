package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.common.Mission;
import net.phoenix.continuum.common.ScheduleService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class C2SScheduleActionPacket {

    public static final byte LIST = 0;
    public static final byte CREATE = 1;
    public static final byte TOGGLE = 2;
    public static final byte DELETE = 3;

    private final byte action;
    private final BlockPos pad;
    private UUID id = new UUID(0L, 0L);
    private ResourceLocation destination = new ResourceLocation("minecraft", "none");
    private Mission.Type missionType = Mission.Type.SURVEY;
    private int probes;
    private int count = 1;
    private String expedition = "";
    private List<String> crew = new ArrayList<>();
    private int delay;
    private int interval;
    private int cap;
    private boolean autoCollect;

    private C2SScheduleActionPacket(byte action, BlockPos pad) {
        this.action = action;
        this.pad = pad;
    }

    public static C2SScheduleActionPacket list(BlockPos pad) {
        return new C2SScheduleActionPacket(LIST, pad);
    }

    public static C2SScheduleActionPacket toggle(BlockPos pad, UUID id) {
        C2SScheduleActionPacket packet = new C2SScheduleActionPacket(TOGGLE, pad);
        packet.id = id;
        return packet;
    }

    public static C2SScheduleActionPacket delete(BlockPos pad, UUID id) {
        C2SScheduleActionPacket packet = new C2SScheduleActionPacket(DELETE, pad);
        packet.id = id;
        return packet;
    }

    public static C2SScheduleActionPacket create(BlockPos pad, ResourceLocation destination, Mission.Type type,
                                                 int probes, int count, String expedition, List<String> crew,
                                                 int delaySeconds, int intervalSeconds, int cap, boolean autoCollect) {
        C2SScheduleActionPacket packet = new C2SScheduleActionPacket(CREATE, pad);
        packet.destination = destination;
        packet.missionType = type;
        packet.probes = probes;
        packet.count = count;
        packet.expedition = expedition;
        packet.crew = new ArrayList<>(crew);
        packet.delay = delaySeconds;
        packet.interval = intervalSeconds;
        packet.cap = cap;
        packet.autoCollect = autoCollect;
        return packet;
    }

    public C2SScheduleActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.pad = buf.readBlockPos();
        this.id = buf.readUUID();
        this.destination = buf.readResourceLocation();
        this.missionType = Mission.Type.values()[Math.max(0, Math.min(buf.readByte(),
                Mission.Type.values().length - 1))];
        this.probes = buf.readVarInt();
        this.count = buf.readVarInt();
        this.expedition = buf.readUtf(32);
        int crewCount = Math.min(buf.readVarInt(), 8);
        for (int i = 0; i < crewCount; i++) crew.add(buf.readUtf(32));
        this.delay = buf.readVarInt();
        this.interval = buf.readVarInt();
        this.cap = buf.readVarInt();
        this.autoCollect = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeBlockPos(pad);
        buf.writeUUID(id);
        buf.writeResourceLocation(destination);
        buf.writeByte(missionType.ordinal());
        buf.writeVarInt(probes);
        buf.writeVarInt(count);
        buf.writeUtf(expedition, 32);
        buf.writeVarInt(crew.size());
        for (String member : crew) buf.writeUtf(member, 32);
        buf.writeVarInt(delay);
        buf.writeVarInt(interval);
        buf.writeVarInt(cap);
        buf.writeBoolean(autoCollect);
    }

    public static void handle(C2SScheduleActionPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !net.phoenix.continuum.common.ContinuumGates.check(player, net.phoenix.continuum.common.ContinuumGates.MISSIONS)) return;
            if (player == null) return;
            String error = switch (msg.action) {
                case CREATE -> ScheduleService.create(player, msg.pad, msg.destination, msg.missionType, msg.probes,
                        msg.count, msg.expedition, msg.crew, msg.delay, msg.interval, msg.cap, msg.autoCollect);
                case TOGGLE -> ScheduleService.toggle(player, msg.pad, msg.id);
                case DELETE -> ScheduleService.delete(player, msg.pad, msg.id);
                default -> null;
            };
            if (error != null) player.sendSystemMessage(Component.literal(error));
            ContinuumNetwork.sendToPlayer(player,
                    new S2CSchedulesPacket(msg.pad, ScheduleService.views(player, msg.pad)));
        });
        ctx.get().setPacketHandled(true);
    }
}
