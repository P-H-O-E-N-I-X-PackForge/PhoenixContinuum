package net.phoenix.continuum.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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

    public static void postDiscovery(UUID team, ResourceLocation subject, DiscoveryStage stage) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        NeoForge.EVENT_BUS.post(new Discovery(server, team, subject, stage));
    }

    public static void postMission(MissionChanged.Kind kind, Mission mission) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        NeoForge.EVENT_BUS.post(new MissionChanged(server, kind, mission));
    }
}
