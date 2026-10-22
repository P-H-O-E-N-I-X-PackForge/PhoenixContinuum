package net.phoenix.continuum.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.phoenix.continuum.common.Mission;
import net.phoenix.continuum.data.DiscoveryStage;

import java.util.UUID;

/** Game-bus events other mods and scripts can listen to. All are posted on the logical server. */
public final class ContinuumEvents {

    private ContinuumEvents() {}

    /** A body or system moved up a discovery stage for a team. */
    public static class Discovery extends Event {

        private final MinecraftServer server;
        private final UUID team;
        private final ResourceLocation subject;
        private final DiscoveryStage stage;

        public Discovery(MinecraftServer server, UUID team, ResourceLocation subject, DiscoveryStage stage) {
            this.server = server;
            this.team = team;
            this.subject = subject;
            this.stage = stage;
        }

        public MinecraftServer getServer() {
            return server;
        }

        public UUID getTeam() {
            return team;
        }

        /** The body or system id. */
        public ResourceLocation getSubject() {
            return subject;
        }

        public DiscoveryStage getStage() {
            return stage;
        }
    }

    /** A mission left the pad, or came back. */
    public static class MissionChanged extends Event {

        public enum Kind {
            LAUNCHED,
            SUCCEEDED,
            FAILED
        }

        private final MinecraftServer server;
        private final Kind kind;
        private final Mission mission;

        public MissionChanged(MinecraftServer server, Kind kind, Mission mission) {
            this.server = server;
            this.kind = kind;
            this.mission = mission;
        }

        public MinecraftServer getServer() {
            return server;
        }

        public Kind getKind() {
            return kind;
        }

        public UUID getTeam() {
            return mission.team;
        }

        public ResourceLocation getDestination() {
            return mission.destination;
        }

        /** SURVEY, EXTRACT, DEPLOY, HAUL, REPAIR, STATION or RESCUE. */
        public String getMissionType() {
            return mission.type.name();
        }

        public int getProbes() {
            return mission.probes;
        }
    }

    /** A player investigated an anomaly on a body, and was paid for it. */
    public static class AnomalyInvestigated extends Event {

        private final MinecraftServer server;
        private final net.minecraft.server.level.ServerPlayer player;
        private final UUID team;
        private final ResourceLocation body;
        private final String anomaly;

        public AnomalyInvestigated(MinecraftServer server, net.minecraft.server.level.ServerPlayer player, UUID team,
                                   ResourceLocation body, String anomaly) {
            this.server = server;
            this.player = player;
            this.team = team;
            this.body = body;
            this.anomaly = anomaly;
        }

        public MinecraftServer getServer() {
            return server;
        }

        public net.minecraft.server.level.ServerPlayer getPlayer() {
            return player;
        }

        public UUID getTeam() {
            return team;
        }

        public ResourceLocation getBody() {
            return body;
        }

        /** The anomaly's id, as authored in the body's "anomalies" list. */
        public String getAnomaly() {
            return anomaly;
        }
    }

    public static void postAnomaly(net.minecraft.server.level.ServerPlayer player, UUID team, ResourceLocation body,
                                   String anomaly) {
        MinecraftForge.EVENT_BUS.post(new AnomalyInvestigated(player.server, player, team, body, anomaly));
    }

    public static void postDiscovery(UUID team, ResourceLocation subject, DiscoveryStage stage) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        MinecraftForge.EVENT_BUS.post(new Discovery(server, team, subject, stage));
    }

    public static void postMission(MissionChanged.Kind kind, Mission mission) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        MinecraftForge.EVENT_BUS.post(new MissionChanged(server, kind, mission));
    }
}
