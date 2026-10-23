package net.phoenix.continuum.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.common.ContinuumServerEvents;
import net.phoenix.continuum.common.ContinuumTeamData;
import net.phoenix.continuum.integration.solaris.SolarisSites;
import net.phoenix.continuum.network.ContinuumNetwork;
import net.phoenix.continuum.network.S2COpenMapPacket;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LaunchComplexes {

    private static final Map<UUID, Set<LaunchComplex>> BY_TEAM = new ConcurrentHashMap<>();

    private LaunchComplexes() {}

    static List<LaunchComplex> of(UUID team) {
        Set<LaunchComplex> set = BY_TEAM.get(team);
        if (set == null) return List.of();
        return set.stream().filter(LaunchComplex::isFormed).toList();
    }

    public static UUID unregister(LaunchComplex complex, UUID registeredTeam) {
        if (registeredTeam == null) return null;
        Set<LaunchComplex> set = BY_TEAM.get(registeredTeam);
        if (set != null) {
            set.remove(complex);
            if (set.isEmpty()) BY_TEAM.remove(registeredTeam);
        }
        return null;
    }

    public static UUID formed(LaunchComplex complex, Level level, BlockPos pos, UUID owner, UUID previousTeam) {
        if (level.isClientSide || level.getServer() == null || owner == null) return previousTeam;
        var server = level.getServer();
        UUID team = ContinuumHost.teamOf(owner);
        ContinuumTeamData.get(server).markEngaged(team);
        unregister(complex, previousTeam);
        BY_TEAM.computeIfAbsent(team, k -> ConcurrentHashMap.newKeySet()).add(complex);
        if (level instanceof ServerLevel serverLevel) SolarisSites.register(serverLevel, team, pos);
        ServerPlayer builder = server.getPlayerList().getPlayer(owner);
        if (builder != null) ContinuumServerEvents.engageAndSend(builder);
        return team;
    }

    public static InteractionResult use(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ContinuumServerEvents.engageAndSend(serverPlayer);
            ContinuumNetwork.sendToPlayer(serverPlayer, new S2COpenMapPacket(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
