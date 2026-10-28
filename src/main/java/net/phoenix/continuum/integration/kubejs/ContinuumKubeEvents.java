package net.phoenix.continuum.integration.kubejs;

import net.minecraft.server.MinecraftServer;
import net.phoenix.continuum.api.ContinuumEvents;
import net.phoenix.continuum.common.visibility.ContinuumVisibilityEvent;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;

import java.util.UUID;

public final class ContinuumKubeEvents {

    public static final EventGroup GROUP = EventGroup.of("ContinuumEvents");

    public static final EventHandler VISIBILITY = GROUP.server(
            "visibility", () -> VisibilityEvent.class);

    public static final EventHandler DISCOVERY = GROUP.server(
            "discovery", () -> DiscoveryEvent.class);

    public static final EventHandler MISSION = GROUP.server("mission", () -> MissionEvent.class);

    public static final EventHandler ANOMALY = GROUP.server("anomaly", () -> AnomalyEvent.class);

    /** Startup: give a body's personal dimension its own worldgen. See {@code PdimWorldgen}. */
    public static final EventHandler PDIM_ENTER = GROUP.server("pdimEnter", () -> PdimEnterEvent.class);

    public static final EventHandler PDIM_LEAVE = GROUP.server("pdimLeave", () -> PdimLeaveEvent.class);

    public static final EventHandler PDIM_WORLDGEN = GROUP.startup("pdimWorldgen", () -> PdimWorldgenEvent.class);

    private ContinuumKubeEvents() {}

    public static class PdimWorldgenEvent implements KubeEvent {

        /** The worldgen of a body's personal dimension, to change with {@code .flat(...)}, {@code .noise(...)} and so on. */
        public net.phoenix.continuum.api.PdimWorldgen.Spec pdim(String body) {
            return net.phoenix.continuum.api.PdimWorldgen.of(body);
        }

        /** Every body the test matches that has no pdim of its own. Rules need runtime dimensions (Infiniverse). */
        public void where(java.util.function.Predicate<net.phoenix.continuum.data.ContinuumBody> test,
                          java.util.function.Consumer<net.phoenix.continuum.api.PdimWorldgen.Spec> configure) {
            net.phoenix.continuum.api.PdimWorldgen.where(test, configure);
        }

        /** Every body of a type: 'planet', 'moon', 'star' or 'black_hole'. */
        public void type(String type, java.util.function.Consumer<net.phoenix.continuum.api.PdimWorldgen.Spec> configure) {
            net.phoenix.continuum.api.PdimWorldgen.forType(type, configure);
        }

        /** Every body of a system. */
        public void system(String system,
                           java.util.function.Consumer<net.phoenix.continuum.api.PdimWorldgen.Spec> configure) {
            net.phoenix.continuum.api.PdimWorldgen.forSystem(system, configure);
        }

        /** Every body without a pdim of its own. */
        public void all(java.util.function.Consumer<net.phoenix.continuum.api.PdimWorldgen.Spec> configure) {
            net.phoenix.continuum.api.PdimWorldgen.forAll(configure);
        }
    }

    public static class PdimEnterEvent implements KubeEvent {

        private final ContinuumEvents.PdimEntered event;

        PdimEnterEvent(ContinuumEvents.PdimEntered event) {
            this.event = event;
        }

        public net.minecraft.server.level.ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public MinecraftServer getServer() {
            return event.getServer();
        }

        public String getBody() {
            return event.getBody().toString();
        }

        /** True the first time the player made this dimension. */
        public boolean getCreated() {
            return event.isCreated();
        }
    }

    public static class PdimLeaveEvent implements KubeEvent {

        private final ContinuumEvents.PdimLeft event;

        PdimLeaveEvent(ContinuumEvents.PdimLeft event) {
            this.event = event;
        }

        public net.minecraft.server.level.ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public MinecraftServer getServer() {
            return event.getServer();
        }

        public String getBody() {
            return event.getBody().toString();
        }
    }

    public static class AnomalyEvent implements KubeEvent {

        private final ContinuumEvents.AnomalyInvestigated event;

        AnomalyEvent(ContinuumEvents.AnomalyInvestigated event) {
            this.event = event;
        }

        public MinecraftServer getServer() {
            return event.getServer();
        }

        public net.minecraft.server.level.ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public UUID getTeam() {
            return event.getTeam();
        }

        public String getBody() {
            return event.getBody().toString();
        }

        public String getAnomaly() {
            return event.getAnomaly();
        }
    }

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

        public String getSubject() {
            return event.getSubject().toString();
        }

        public String getKind() {
            return event.getKind().name().toLowerCase();
        }

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

        public String getKind() {
            return event.getKind().name().toLowerCase();
        }

        public String getDestination() {
            return event.getDestination().toString();
        }

        public String getMissionType() {
            return event.getMissionType();
        }

        public int getProbes() {
            return event.getProbes();
        }
    }
}
