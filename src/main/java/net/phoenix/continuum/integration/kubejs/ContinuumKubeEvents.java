package net.phoenix.continuum.integration.kubejs;

import net.minecraft.server.MinecraftServer;
import net.phoenix.continuum.api.ContinuumEvents;
import net.phoenix.continuum.common.visibility.ContinuumVisibilityEvent;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;

import java.util.UUID;

/** The {@code ContinuumEvents} KubeJS event group (server scripts). Only loaded when KubeJS is installed. */
public final class ContinuumKubeEvents {

    public static final EventGroup GROUP = EventGroup.of("ContinuumEvents");

    /** {@code ContinuumEvents.visibility(event => event.deny())}: the last word on whether something shows or is reachable. */
    public static final EventHandler VISIBILITY = GROUP.server("visibility", () -> VisibilityEvent.class);

    /** {@code ContinuumEvents.discovery(event => ...)}: a body or system reached a new stage for a team. */
    public static final EventHandler DISCOVERY = GROUP.server("discovery", () -> DiscoveryEvent.class);

    /** {@code ContinuumEvents.mission(event => ...)}: a mission launched, succeeded or failed. */
    public static final EventHandler MISSION = GROUP.server("mission", () -> MissionEvent.class);

    private ContinuumKubeEvents() {}

    public static class VisibilityEvent implements KubeEvent {

        private final ContinuumVisibilityEvent event;

        VisibilityEvent(ContinuumVisibilityEvent event) {
            this.event = event;
        }

        public MinecraftServer getServer() {
            return event.getServer();
        }

        public UUID getTeam() {
            return event.getTeam();
        }

        /** The body (or system) id, as a string. */
        public String getSubject() {
            return event.getSubject().toString();
        }

        /** "visible" or "accessible". */
        public String getKind() {
            return event.getKind().name().toLowerCase();
        }

        /** What the rules and overrides decided so far. */
        public boolean getResult() {
            return event.getResult();
        }

        public void setResult(boolean result) {
            event.setResult(result);
        }

        public void allow() {
            event.setResult(true);
        }

        public void deny() {
            event.setResult(false);
        }
    }

    public static class DiscoveryEvent implements KubeEvent {

        private final ContinuumEvents.Discovery event;

        DiscoveryEvent(ContinuumEvents.Discovery event) {
            this.event = event;
        }

        public MinecraftServer getServer() {
            return event.getServer();
        }

        public UUID getTeam() {
            return event.getTeam();
        }

        public String getSubject() {
            return event.getSubject().toString();
        }

        /** "detected" or "surveyed". */
        public String getStage() {
            return event.getStage().name().toLowerCase();
        }
    }

    public static class MissionEvent implements KubeEvent {

        private final ContinuumEvents.MissionChanged event;

        MissionEvent(ContinuumEvents.MissionChanged event) {
            this.event = event;
        }

        public MinecraftServer getServer() {
            return event.getServer();
        }

        public UUID getTeam() {
            return event.getTeam();
        }

        /** "launched", "succeeded" or "failed". */
        public String getKind() {
            return event.getKind().name().toLowerCase();
        }

        public String getDestination() {
            return event.getDestination().toString();
        }

        /** SURVEY, EXTRACT, DEPLOY, HAUL, REPAIR, STATION or RESCUE. */
        public String getMissionType() {
            return event.getMissionType();
        }

        public int getProbes() {
            return event.getProbes();
        }
    }
}
