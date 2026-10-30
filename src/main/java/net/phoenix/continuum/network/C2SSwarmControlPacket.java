package net.phoenix.continuum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.continuum.common.ContinuumServerEvents;
import net.phoenix.continuum.common.SwarmControlService;

public class C2SSwarmControlPacket {

    public static final byte REFRESH = 0;
    public static final byte SELECT = 1;
    public static final byte BUILD_SWARM = 2;
    public static final byte BUILD_SHELL = 3;
    public static final byte BIGGER = 4;
    public static final byte SMALLER = 5;

    private final byte action;
    private final BlockPos control;
    private final ResourceLocation target;

    public C2SSwarmControlPacket(byte action, BlockPos control, ResourceLocation target) {
        this.action = action;
        this.control = control;
        this.target = target;
    }

    public C2SSwarmControlPacket(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.control = buf.readBlockPos();
        this.target = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeBlockPos(control);
        buf.writeResourceLocation(target);
    }

    public static void handle(C2SSwarmControlPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !net.phoenix.continuum.common.ContinuumGates.check(player, net.phoenix.continuum.common.ContinuumGates.SWARMS)) return;
            if (player == null) return;
            double range = net.phoenix.continuum.config.ContinuumConfigs.INSTANCE.launchRangeBlocks;
            if (!player.level().isLoaded(msg.control) ||
                    player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(msg.control)) > range * range) {
                return;
            }
            switch (msg.action) {
                case SELECT -> SwarmControlService.select(player, msg.control, msg.target);
                case BIGGER -> SwarmControlService.resize(player, msg.control, 1);
                case SMALLER -> SwarmControlService.resize(player, msg.control, -1);
                case BUILD_SWARM, BUILD_SHELL -> {
                    String error = SwarmControlService.build(player, msg.control, msg.action == BUILD_SHELL);
                    if (error != null) player.sendSystemMessage(Component.literal(error));
                    else ContinuumServerEvents.sendStateToTeam(player.server,
                            net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID()));
                }
                default -> {}
            }
            ContinuumNetwork.sendToPlayer(player, new S2CSwarmControlPacket(SwarmControlService.view(player, msg.control),
                    false));
        });
        ctx.get().setPacketHandled(true);
    }
}
