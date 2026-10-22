package net.phoenix.continuum.integration.solaris;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.machine.LaunchPadMachine;
import net.phoenixvine.solaris.api.SolarisMarkerSyncEvent;
import net.phoenixvine.solaris.api.SolarisMarkers;
import net.phoenixvine.solaris.api.sites.TeamSiteData;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

final class SolarisSitesImpl {

    private static final String KIND = "launch_complex";

    private SolarisSitesImpl() {}

    static void init() {
        NeoForge.EVENT_BUS.register(new SolarisSitesImpl());
    }

    static void register(ServerLevel level, UUID team, BlockPos pos) {
        TeamSiteData.registerSite(level, team, KIND, pos);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (ContinuumHost.teams().onTeam(player, team)) {
                marker(level.dimension().location().toString(), pos).sendTo(player);
            }
        }
    }

    private static SolarisMarkers.Builder marker(String dimension, BlockPos pos) {
        return SolarisMarkers.marker("phoenix_continuum:lc_" + Long.toHexString(pos.asLong()))
                .name("Launch Complex").at(ResourceLocation.parse(dimension), pos.getX(), pos.getY(), pos.getZ())
                .color("FFB040").icon("HOME").category("Continuum");
    }

    @SubscribeEvent
    public void onMarkerSync(SolarisMarkerSyncEvent event) {
        ServerPlayer player = event.getPlayer();
        UUID team = ContinuumHost.teamOf(player.getUUID());
        TeamSiteData data = TeamSiteData.get(player.serverLevel());
        for (Map.Entry<String, TeamSiteData.Site> entry : new ArrayList<>(data.forTeam(team).entrySet())) {
            if (!entry.getKey().startsWith(KIND + "/")) continue;
            TeamSiteData.Site site = entry.getValue();
            ServerLevel level = player.server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(site.dimension())));
            if (level != null && level.isLoaded(site.pos()) &&
                    !(MetaMachine.getMachine(level, site.pos()) instanceof LaunchPadMachine)) {
                data.remove(team, entry.getKey());
                continue;
            }
            marker(site.dimension(), site.pos()).sendTo(player);
        }
    }
}
