package net.phoenix.continuum.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ContinuumHost {

    private ContinuumHost() {}

    public interface Teams {

        UUID teamOf(UUID player);

        boolean onTeam(Player player, UUID team);
    }

    public interface Research {

        boolean isUnlocked(UUID team, ResourceLocation node);

        boolean hasFlag(UUID team, String flag);

        @Nullable
        String getDiscipline(UUID team);
    }

    public interface Travel {

        @Nullable
        ResourceKey<Level> dimensionOf(String discipline);

        boolean visit(ServerPlayer player, String discipline);
    }

    private static final Teams DEFAULT_TEAMS = new Teams() {

        @Override
        public UUID teamOf(UUID player) {
            return player;
        }

        @Override
        public boolean onTeam(Player player, UUID team) {
            return player.getUUID().equals(team);
        }
    };

    private static volatile Teams teams = DEFAULT_TEAMS;
    private static volatile Research research = net.phoenix.continuum.common.BuiltinResearch.PROVIDER;
    private static volatile @Nullable Travel travel;

    public static void setTeams(Teams provider) {
        teams = provider == null ? DEFAULT_TEAMS : provider;
    }

    public static void setResearch(Research provider) {
        research = provider == null ? net.phoenix.continuum.common.BuiltinResearch.PROVIDER : provider;
    }

    public static void setTravel(@Nullable Travel provider) {
        travel = provider;
    }

    public static Teams teams() {
        return teams;
    }

    public static Research research() {
        return research;
    }

    public static Research research(ServerLevel ignored) {
        return research;
    }

    public static @Nullable Travel travel() {
        return travel;
    }

    public static UUID teamOf(UUID player) {
        UUID team = teams.teamOf(player);
        return team == null ? player : team;
    }

    public static Object beforeResearch(MinecraftServer server, UUID team) {
        return net.phoenix.continuum.common.ContinuumConfluxHooks.before(server, team);
    }

    public static void afterResearch(MinecraftServer server, UUID team, Object token) {
        net.phoenix.continuum.common.ContinuumConfluxHooks.after(server, team, token);
    }

    public static void teamChanged(MinecraftServer server, UUID team) {
        net.phoenix.continuum.common.ContinuumConfluxHooks.teamChanged(server, team);
    }
}
