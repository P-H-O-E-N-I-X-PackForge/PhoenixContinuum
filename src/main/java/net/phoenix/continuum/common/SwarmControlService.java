package net.phoenix.continuum.common;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;
import net.phoenix.continuum.machine.LaunchComplex;
import net.phoenix.continuum.machine.LaunchSites;
import net.phoenix.continuum.machine.PadTiers;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** What a Swarm Control block lets a player do, checked on the server. */
public final class SwarmControlService {

    private SwarmControlService() {}

    /** The nearest launch pad within reach of the control block, or null. */
    public static @Nullable BlockPos findPad(Level level, BlockPos control) {
        int reach = ContinuumConfigs.INSTANCE.swarmControlPadRange;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(control.offset(-reach, -reach, -reach),
                control.offset(reach, reach, reach))) {
            if (!level.isLoaded(pos)) continue;
            if (LaunchSites.at(level, pos) == null) continue;
            double distance = pos.distSqr(control);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    public static SwarmControlView view(ServerPlayer player, BlockPos control) {
        MinecraftServer server = player.server;
        Level level = player.level();
        UUID team = ContinuumHost.teamOf(player.getUUID());
        ContinuumTeamData data = ContinuumTeamData.get(server);

        List<SwarmControlView.Candidate> candidates = new ArrayList<>();
        for (ContinuumBody body : ContinuumData.allBodies()) {
            if (!Swarms.isSwarm(body) || !body.isCentral()) continue;
            if (data.effectiveStage(server, team, body.id()) != DiscoveryStage.SURVEYED) continue;
            candidates.add(new SwarmControlView.Candidate(body.id(), body.name(), Swarms.isPenrose(body),
                    data.stationLevel(team, body.id()), Swarms.maxLevel(body), data.shellLevel(team, body.id()),
                    Swarms.shellMaxLevel(body)));
        }
        candidates.sort(java.util.Comparator.comparing(SwarmControlView.Candidate::name));

        ResourceLocation target = SwarmControlTargets.get(server).get(level, control);
        boolean known = false;
        for (var c : candidates) known |= c.body().equals(target);
        if (!known) target = candidates.isEmpty() ? ResourceLocation.fromNamespaceAndPath("minecraft", "none") :
                candidates.get(0).body();

        int swarmFlying = 0;
        int shellFlying = 0;
        for (Mission mission : data.missions(team)) {
            if (mission.state.finished() || !mission.destination.equals(target)) continue;
            if (mission.type == Mission.Type.STATION) swarmFlying++;
            if (mission.type == Mission.Type.SHELL) shellFlying++;
        }

        BlockPos pad = findPad(level, control);
        LaunchComplex complex = pad == null ? null : LaunchSites.at(level, pad);
        String padName = complex == null ? "" : (PadTiers.basic() ? "Launch Pad" : "Launch Complex") + " at " +
                pad.toShortString();
        ContinuumMissions.PadStockView stock = pad == null ? null : ContinuumMissions.padStock(player, pad);
        long launchEnergy = complex == null ? 0 : Math.min(PadTiers.launchEnergy(complex.padTier()),
                complex.energyCapacity() * 9 / 10);

        long powerNow = 0;
        long powerDone = 0;
        ContinuumBody body = ContinuumData.body(target);
        if (body != null && Swarms.isSwarm(body)) {
            int swarm = data.stationLevel(team, target);
            int shell = data.shellLevel(team, target);
            powerNow = Math.round(shell > 0 ? Swarms.shellPower(body, shell) : Swarms.swarmPower(body, swarm));
            powerDone = Math.round(Swarms.shellPower(body, Swarms.shellMaxLevel(body)));
        }

        String note = candidates.isEmpty() ? "Survey a star or black hole first: only surveyed ones can be built round." :
                pad == null ? "No launch pad within " + ContinuumConfigs.INSTANCE.swarmControlPadRange +
                        " blocks. Build one near this block." : "";
        return new SwarmControlView(control, target, candidates, padName, complex != null,
                complex == null ? 0 : complex.storedEnergy(), launchEnergy, stock == null ? 0 : stock.collectors(),
                stock == null ? 0 : stock.segments(), stock == null ? 0 : stock.rockets(), swarmFlying, shellFlying,
                powerNow, powerDone, PadTiers.energyUnit(), note);
    }

    public static void select(ServerPlayer player, BlockPos control, ResourceLocation target) {
        ContinuumBody body = ContinuumData.body(target);
        if (body == null || !Swarms.isSwarm(body)) return;
        SwarmControlTargets.get(player.server).set(player.level(), control, target);
    }

    /** @return why the build could not be sent, or null once it has been */
    public static @Nullable String build(ServerPlayer player, BlockPos control, boolean shell) {
        Level level = player.level();
        ResourceLocation target = view(player, control).target();
        BlockPos pad = findPad(level, control);
        if (pad == null) return "There is no launch pad near this control block.";
        return ContinuumMissions.launchFromControl(player, control, pad, target,
                shell ? Mission.Type.SHELL : Mission.Type.STATION);
    }
}
