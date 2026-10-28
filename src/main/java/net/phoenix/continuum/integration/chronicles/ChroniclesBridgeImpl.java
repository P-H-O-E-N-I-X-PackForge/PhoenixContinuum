package net.phoenix.continuum.integration.chronicles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.QuestAPI;

import org.jetbrains.annotations.Nullable;

/** The part of the bridge that uses Chronicles classes: only loaded when Chronicles is installed. */
final class ChroniclesBridgeImpl {

    private ChroniclesBridgeImpl() {}

    static void registerTrigger(String id, @Nullable String description) {
        QuestAPI.registerExternalTrigger(id, description);
    }

    static void fire(Player player, String id, @Nullable CompoundTag data) {
        QuestAPI.fireExternalEvent(player, id, data);
    }

    static void setup() {
        registerTrigger(ContinuumTriggers.DISCOVERY_DETECTED, "Their team detected a body.");
        registerTrigger(ContinuumTriggers.DISCOVERY_SURVEYED, "Their team surveyed a body.");
        registerTrigger(ContinuumTriggers.MISSION_LAUNCHED, "Their team launched a mission.");
        registerTrigger(ContinuumTriggers.MISSION_SUCCEEDED, "A mission of their team succeeded.");
        registerTrigger(ContinuumTriggers.MISSION_FAILED, "A mission of their team failed.");
        registerTrigger(ContinuumTriggers.ANOMALY_INVESTIGATED, "They investigated an anomaly.");
        registerTrigger(ContinuumTriggers.PDIM_ENTERED, "They arrived in a personal dimension.");
        registerTrigger(ContinuumTriggers.PDIM_CREATED, "They made their personal dimension (the first arrival).");
        registerTrigger(ContinuumTriggers.PDIM_LEFT, "They left a personal dimension through Continuum.");
    }
}
