package net.phoenix.continuum.common;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.network.ContinuumNetwork;
import net.phoenix.continuum.network.S2CHologramPacket;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every Swarm Control (the plain block or the GregTech multiblock) that is loaded, and the hologram each one shows.
 * The state is sent to players near it when it changes and now and then after, so one that walks up sees it.
 */
public final class SwarmHolograms {

    private SwarmHolograms() {}

    public interface Source {

        /** Whether it is online: powered, and for the multiblock, formed and running its recipe. */
        boolean active();

        @Nullable UUID owner();

        /** How far (x, z) the middle of the hologram is from the block: a multiblock's middle is not its controller. */
        default int[] offset() {
            return new int[] { 0, 0 };
        }

        /** A control shows a hologram of a star or black hole; a receiver shows its uplink to the sky. */
        default int kind() {
            return S2CHologramPacket.State.CONTROL;
        }

        /** For a receiver: how full it is, 0 to 100, which sets how bright the uplink is. */
        default int fill() {
            return 0;
        }
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<Key, Source> SOURCES = new ConcurrentHashMap<>();
    private static final Map<Key, S2CHologramPacket.State> LAST = new ConcurrentHashMap<>();
    private static final double RANGE = 128.0;
    private static int tick;

    public static void register(Level level, BlockPos pos, Source source) {
        if (level.isClientSide) return;
        SOURCES.put(new Key(level.dimension(), pos.immutable()), source);
    }

    public static void unregister(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        Key key = new Key(level.dimension(), pos.immutable());
        if (SOURCES.remove(key) == null) return;
        S2CHologramPacket.State last = LAST.remove(key);
        if (last != null && level instanceof ServerLevel server) {
            send(server, key, new S2CHologramPacket.State(last.pos(), false, last.target(), last.swarm(),
                    last.swarmMax(), last.shell(), last.shellMax(), last.offsetX(), last.offsetZ(), last.scale(),
                    last.kind()));
        }
    }

    public static @Nullable Source sourceAt(Level level, BlockPos pos) {
        return SOURCES.get(new Key(level.dimension(), pos.immutable()));
    }

    public static boolean isActive(Level level, BlockPos pos) {
        Source source = SOURCES.get(new Key(level.dimension(), pos.immutable()));
        return source != null && source.active();
    }

    private static S2CHologramPacket.@Nullable State stateOf(MinecraftServer server, Key key, Source source) {
        ServerLevel level = server.getLevel(key.dimension());
        UUID owner = source.owner();
        if (level == null || owner == null) return null;
        UUID team = ContinuumHost.teamOf(owner);
        int[] where = source.offset();
        if (source.kind() == S2CHologramPacket.State.RECEIVER) {
            return new S2CHologramPacket.State(key.pos(), source.active(),
                    new ResourceLocation("minecraft", "none"), source.fill(), 100, 0, 1, where[0],
                    where[1], 1.0f, S2CHologramPacket.State.RECEIVER);
        }
        float scale = SwarmControlTargets.get(server).scale(level, key.pos());
        ResourceLocation target = SwarmControlService.targetOf(server, level, key.pos(), team);
        ContinuumBody body = target == null ? null : ContinuumData.body(target);
        if (body == null || !Swarms.isSwarm(body)) {
            return new S2CHologramPacket.State(key.pos(), false, new ResourceLocation("minecraft", "none"),
                    0, 1, 0, 1, where[0], where[1], scale, S2CHologramPacket.State.CONTROL);
        }
        var data = ContinuumTeamData.get(server);
        return new S2CHologramPacket.State(key.pos(), source.active(), target, data.stationLevel(team, target),
                Swarms.maxLevel(body), data.shellLevel(team, target), Swarms.shellMaxLevel(body), where[0], where[1],
                scale, S2CHologramPacket.State.CONTROL);
    }

    private static void send(ServerLevel level, Key key, S2CHologramPacket.State state) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(key.pos().getX() + 0.5, key.pos().getY() + 0.5, key.pos().getZ() + 0.5) <=
                    RANGE * RANGE) {
                ContinuumNetwork.sendToPlayer(player, new S2CHologramPacket(state));
            }
        }
    }

    /** Once a second. */
    public static void tick(MinecraftServer server) {
        boolean resend = ++tick % 5 == 0;
        for (Map.Entry<Key, Source> entry : SOURCES.entrySet()) {
            S2CHologramPacket.State state = stateOf(server, entry.getKey(), entry.getValue());
            if (state == null) continue;
            S2CHologramPacket.State before = LAST.put(entry.getKey(), state);
            if (resend || !state.equals(before)) {
                ServerLevel level = server.getLevel(entry.getKey().dimension());
                if (level != null) send(level, entry.getKey(), state);
            }
        }
    }
}
