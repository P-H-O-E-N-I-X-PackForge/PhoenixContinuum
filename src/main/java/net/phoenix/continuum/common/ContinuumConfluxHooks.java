package net.phoenix.continuum.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.phoenix.continuum.data.DiscoveryStage;

import java.util.Map;
import java.util.UUID;

/**
 * What Continuum does when research or team membership changes. The companion mod that owns research calls these through
 * {@link net.phoenix.continuum.api.ContinuumHost}.
 */
public final class ContinuumConfluxHooks {

    private ContinuumConfluxHooks() {}

    public static Object before(MinecraftServer server, UUID team) {
        return ContinuumDiscoveryNotifier.snapshot(server, team);
    }

    @SuppressWarnings("unchecked")
    public static void after(MinecraftServer server, UUID team, Object before) {
        if (before instanceof Map<?, ?> snapshot) {
            ContinuumDiscoveryNotifier.announce(server, team, (Map<ResourceLocation, DiscoveryStage>) snapshot);
        }
        ContinuumServerEvents.sendStateToTeam(server, team);
    }

    public static void teamChanged(MinecraftServer server, UUID team) {
        ContinuumServerEvents.sendStateToTeam(server, team);
    }
}
