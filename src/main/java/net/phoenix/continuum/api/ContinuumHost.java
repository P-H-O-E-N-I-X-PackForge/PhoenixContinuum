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

/**
 * What Continuum needs from the pack or mod around it, as plain interfaces. Nothing here depends on another mod: a
 * companion (PhoenixCore's Conflux research, a guild or team mod) installs its own implementations at startup, and
 * Continuum works with the defaults until then. Defaults: a team is just the player, research is Continuum's own (see
 * {@link net.phoenix.continuum.common.BuiltinResearch}), and there are no discipline worlds to travel to.
 */
public final class ContinuumHost {

    private ContinuumHost() {}

    /** Who counts as a team. */
    public interface Teams {

        /** The team id a player's progress is stored under. */
        UUID teamOf(UUID player);

        /** Whether the player belongs to that team. */
        boolean onTeam(Player player, UUID team);
    }

    /** What a team has researched (Continuum keeps its own unless replaced). Used by research gates in definitions and by the "research" visibility rules. */
    public interface Research {

        boolean isUnlocked(UUID team, ResourceLocation node);

        boolean hasFlag(UUID team, String flag);

        /** The discipline the team chose, or null. */
        @Nullable
        String getDiscipline(UUID team);
    }

    /** Travel to a discipline's own world (Conflux). */
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

    /** A team id for a player, never null. */
    public static UUID teamOf(UUID player) {
        UUID team = teams.teamOf(player);
        return team == null ? player : team;
    }

    // ---------------------------------------------------------------- hooks for the companion to call

    /** Call before a team's research changes; hand the result to {@link #afterResearch}. */
    public static Object beforeResearch(MinecraftServer server, UUID team) {
        return net.phoenix.continuum.common.ContinuumConfluxHooks.before(server, team);
    }

    /** Call after a team's research changed: announces new discoveries and refreshes the team's map. */
    public static void afterResearch(MinecraftServer server, UUID team, Object token) {
        net.phoenix.continuum.common.ContinuumConfluxHooks.after(server, team, token);
    }

    /** Call when a player joins or leaves a team: refreshes that team's map. */
    public static void teamChanged(MinecraftServer server, UUID team) {
        net.phoenix.continuum.common.ContinuumConfluxHooks.teamChanged(server, team);
    }
}
