package net.phoenix.continuum.integration.chronicles;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.ContinuumEvents;
import net.phoenix.continuum.data.DiscoveryStage;

/** Continuum's own events, passed on to Chronicles as triggers. */
@EventBusSubscriber(modid = PhoenixContinuum.MOD_ID)
public final class ContinuumChroniclesEvents {

    private ContinuumChroniclesEvents() {}

    @SubscribeEvent
    public static void onDiscovery(ContinuumEvents.Discovery event) {
        String id = event.getStage() == DiscoveryStage.SURVEYED ? ContinuumTriggers.DISCOVERY_SURVEYED :
                event.getStage() == DiscoveryStage.DETECTED ? ContinuumTriggers.DISCOVERY_DETECTED : null;
        if (id == null) return;
        ChroniclesBridge.fireTeam(event.getServer(), event.getTeam(), id,
                ChroniclesBridge.data("subject", event.getSubject().toString()));
    }

    @SubscribeEvent
    public static void onMission(ContinuumEvents.MissionChanged event) {
        String id = switch (event.getKind()) {
            case LAUNCHED -> ContinuumTriggers.MISSION_LAUNCHED;
            case SUCCEEDED -> ContinuumTriggers.MISSION_SUCCEEDED;
            case FAILED -> ContinuumTriggers.MISSION_FAILED;
        };
        ChroniclesBridge.fireTeam(event.getServer(), event.getTeam(), id,
                ChroniclesBridge.data("destination", event.getDestination().toString(), "type",
                        event.getMissionType()));
    }

    @SubscribeEvent
    public static void onAnomaly(ContinuumEvents.AnomalyInvestigated event) {
        ChroniclesBridge.fire(event.getPlayer(), ContinuumTriggers.ANOMALY_INVESTIGATED,
                ChroniclesBridge.data("body", event.getBody().toString(), "anomaly", event.getAnomaly()));
    }

    @SubscribeEvent
    public static void onPdimEntered(ContinuumEvents.PdimEntered event) {
        var data = ChroniclesBridge.data("body", event.getBody().toString());
        ChroniclesBridge.fire(event.getPlayer(), ContinuumTriggers.PDIM_ENTERED, data);
        if (event.isCreated()) ChroniclesBridge.fire(event.getPlayer(), ContinuumTriggers.PDIM_CREATED, data);
    }

    @SubscribeEvent
    public static void onPdimLeft(ContinuumEvents.PdimLeft event) {
        ChroniclesBridge.fire(event.getPlayer(), ContinuumTriggers.PDIM_LEFT,
                ChroniclesBridge.data("body", event.getBody().toString()));
    }
}
