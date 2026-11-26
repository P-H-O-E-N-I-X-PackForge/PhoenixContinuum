package net.phoenix.continuum.common;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.block.LaunchPadBlock;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;
import net.phoenix.continuum.item.ContinuumProbeItem;
import net.phoenix.continuum.item.ContinuumRepairKitItem;
import net.phoenix.continuum.item.ContinuumRocketItem;
import net.phoenix.continuum.machine.LaunchComplex;
import net.phoenix.continuum.machine.LaunchSites;
import net.phoenix.continuum.machine.PadTiers;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ContinuumMissions {

    private ContinuumMissions() {}

    private static final java.util.function.Predicate<ItemStack> IS_ROCKET = s -> s
            .getItem() instanceof ContinuumRocketItem;
    private static final java.util.function.Predicate<ItemStack> IS_PROBE = s -> s
            .getItem() instanceof ContinuumProbeItem;
    private static final java.util.function.Predicate<ItemStack> IS_KIT = s -> s
            .getItem() instanceof ContinuumRepairKitItem;
    private static final java.util.function.Predicate<ItemStack> IS_COLLECTOR = s -> s
            .getItem() instanceof net.phoenix.continuum.item.ContinuumSwarmCollectorItem;
    private static final java.util.function.Predicate<ItemStack> IS_SEGMENT = s -> s
            .getItem() instanceof net.phoenix.continuum.item.ContinuumShellSegmentItem;
    private static final java.util.function.Predicate<ItemStack> IS_PENROSE_COLLECTOR = s -> s
            .getItem() instanceof net.phoenix.continuum.item.ContinuumPenroseCollectorItem;
    private static final java.util.function.Predicate<ItemStack> IS_PENROSE_SEGMENT = s -> s
            .getItem() instanceof net.phoenix.continuum.item.ContinuumPenroseSegmentItem;

    public static boolean isStockItem(ItemStack stack) {
        return IS_ROCKET.test(stack) || IS_PROBE.test(stack) || IS_KIT.test(stack) || IS_COLLECTOR.test(stack) ||
                IS_SEGMENT.test(stack) || IS_PENROSE_COLLECTOR.test(stack) || IS_PENROSE_SEGMENT.test(stack);
    }

    private interface Stock {

        ItemStack peekRocket();

        int rockets();

        int probes();

        int kits();

        ItemStack takeRocket();

        void takeProbes(int count);

        void takeKits(int count);

        int count(java.util.function.Predicate<ItemStack> matches);

        void take(java.util.function.Predicate<ItemStack> matches, int count);

        String place();
    }

    private record BusStock(LaunchComplex complex) implements Stock {

        @Override
        public ItemStack peekRocket() {
            return complex.peek(IS_ROCKET);
        }

        @Override
        public int rockets() {
            return complex.count(IS_ROCKET);
        }

        @Override
        public int probes() {
            return complex.count(IS_PROBE);
        }

        @Override
        public int kits() {
            return complex.count(IS_KIT);
        }

        @Override
        public ItemStack takeRocket() {
            List<ItemStack> taken = complex.take(IS_ROCKET, 1);
            return taken.isEmpty() ? ItemStack.EMPTY : taken.get(0);
        }

        @Override
        public void takeProbes(int count) {
            complex.take(IS_PROBE, count);
        }

        @Override
        public void takeKits(int count) {
            complex.take(IS_KIT, count);
        }

        @Override
        public int count(java.util.function.Predicate<ItemStack> matches) {
            return complex.count(matches);
        }

        @Override
        public void take(java.util.function.Predicate<ItemStack> matches, int count) {
            complex.take(matches, count);
        }

        @Override
        public String place() {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.place_buses");
        }
    }

    private record PlayerStock(ServerPlayer player) implements Stock {

        @Override
        public ItemStack peekRocket() {
            return findRocket(player);
        }

        @Override
        public int rockets() {
            return countRockets(player);
        }

        @Override
        public int probes() {
            return countProbes(player);
        }

        @Override
        public int kits() {
            return countKits(player);
        }

        @Override
        public ItemStack takeRocket() {
            ItemStack rocket = findRocket(player);
            return rocket.isEmpty() ? ItemStack.EMPTY : rocket.split(1);
        }

        @Override
        public void takeProbes(int count) {
            removeProbes(player, count);
        }

        @Override
        public void takeKits(int count) {
            removeKits(player, count);
        }

        @Override
        public int count(java.util.function.Predicate<ItemStack> matches) {
            return countItems(player, matches);
        }

        @Override
        public void take(java.util.function.Predicate<ItemStack> matches, int count) {
            removeItems(player, matches, count);
        }

        @Override
        public String place() {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.place_inventory");
        }
    }

    public record PadStockView(boolean buses, ItemStack rocket, int probes, int kits, int rockets, int collectors,
                               int segments, int penroseCollectors, int penroseSegments) {}

    public static PadStockView padStock(ServerPlayer player, BlockPos pad) {
        var level = player.level();
        LaunchComplex complex = level.isLoaded(pad) ? LaunchSites.at(level, pad) : null;
        if (complex != null && complex.isFormed() && complex.hasBuses()) {
            BusStock stock = new BusStock(complex);
            return new PadStockView(true, stock.peekRocket(), stock.probes(), stock.kits(), stock.rockets(),
                    stock.count(IS_COLLECTOR), stock.count(IS_SEGMENT), stock.count(IS_PENROSE_COLLECTOR),
                    stock.count(IS_PENROSE_SEGMENT));
        }
        PlayerStock stock = new PlayerStock(player);
        return new PadStockView(false, stock.peekRocket(), stock.probes(), stock.kits(), stock.rockets(),
                stock.count(IS_COLLECTOR), stock.count(IS_SEGMENT), stock.count(IS_PENROSE_COLLECTOR),
                stock.count(IS_PENROSE_SEGMENT));
    }

    public static @Nullable String launchMany(ServerPlayer player, ResourceLocation destinationId, BlockPos pad,
                                              Mission.Type type, int probes, int count) {
        return launchMany(player, destinationId, pad, type, probes, count, "", List.of());
    }

    public static final int MAX_CREW = 3;
    
    public static final float CREW_RISK_REDUCTION = 0.03f;

    public static @Nullable String launchMany(ServerPlayer player, ResourceLocation destinationId, BlockPos pad,
                                              Mission.Type type, int probes, int count, String expeditionName,
                                              List<String> crewNames) {
        boolean convoy = type == Mission.Type.EXTRACT || type == Mission.Type.DEPLOY || type == Mission.Type.RESCUE;
        int wanted = convoy ? Math.max(1, Math.min(count, ContinuumConfigs.INSTANCE.maxRocketsPerLaunch)) : 1;

        int launched = 0;
        String error = null;
        for (int i = 0; i < wanted; i++) {
            error = launch(player, destinationId, pad, type, probes, expeditionName, crewNames);
            if (error != null) break;
            launched++;
        }
        if (launched == 0) return error;
        if (wanted > 1) {
            ContinuumBody body = ContinuumData.body(destinationId);
            String where = body != null ? body.name() : destinationId.getPath();
            player.sendSystemMessage(
                    ContinuumLang.tr("phoenix_continuum.continuum_missions.launched_of_rockets_to", launched, wanted, where, (error != null ? ContinuumLang.tr("phoenix_continuum.continuum_missions.stopped_suffix", error) : Component.literal("."))).withStyle(
                                    error != null ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        }
        return null;
    }

    public static @Nullable String launch(ServerPlayer player, ResourceLocation destinationId, BlockPos pad,
                                          Mission.Type type, int probes) {
        return launch(player, destinationId, pad, type, probes, "", List.of());
    }

    public static List<String> validCrew(ServerPlayer player, List<String> wanted) {
        List<String> crew = new ArrayList<>();
        UUID team = net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        for (String name : wanted) {
            if (crew.size() >= MAX_CREW) break;
            ServerPlayer member = player.server.getPlayerList().getPlayerByName(name);
            if (member == null || member == player || crew.contains(member.getGameProfile().getName())) continue;
            if (!net.phoenix.continuum.api.ContinuumHost.teams().onTeam(member, team)) continue;
            crew.add(member.getGameProfile().getName());
        }
        return crew;
    }

    private static String cleanName(String raw) {
        StringBuilder out = new StringBuilder();
        for (char c : raw.trim().toCharArray()) {
            if (c >= 32 && c != 127 && c != '"' && c != '\u00a7') out.append(c);
            if (out.length() >= 24) break;
        }
        return out.toString().trim();
    }

    public static @Nullable String launch(ServerPlayer player, ResourceLocation destinationId, BlockPos pad,
                                          Mission.Type type, int probes, String expeditionName,
                                          List<String> crewNames) {
        var level = player.level();
        if (!level.isLoaded(pad)) return ContinuumLang.enc("phoenix_continuum.continuum_missions.no_pad");
        double range = ContinuumConfigs.INSTANCE.launchRangeBlocks;
        if (player.distanceToSqr(Vec3.atCenterOf(pad)) > range * range) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.too_far_pad");
        }
        return launchFrom(player.server, level, pad, net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID()),
                player.getUUID(), player.getGameProfile().getName(), player.getRandom(),
                complex -> complex != null && complex.hasBuses() ? new BusStock(complex) : new PlayerStock(player),
                destinationId, type, probes, expeditionName, validCrew(player, crewNames), false);
    }

    public static @Nullable String launchFromControl(ServerPlayer player, BlockPos control, BlockPos pad,
                                                     ResourceLocation destinationId, Mission.Type type) {
        var level = player.level();
        double range = ContinuumConfigs.INSTANCE.launchRangeBlocks;
        if (!level.isLoaded(control) || player.distanceToSqr(Vec3.atCenterOf(control)) > range * range) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.too_far_control");
        }
        int reach = ContinuumConfigs.INSTANCE.swarmControlPadRange;
        if (!level.isLoaded(pad) || Math.abs(pad.getX() - control.getX()) > reach ||
                Math.abs(pad.getY() - control.getY()) > reach || Math.abs(pad.getZ() - control.getZ()) > reach) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.no_pad_near_control");
        }
        return launchFrom(player.server, level, pad, net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID()),
                player.getUUID(), player.getGameProfile().getName(), player.getRandom(),
                complex -> complex != null && complex.hasBuses() ? new BusStock(complex) : new PlayerStock(player),
                destinationId, type, 0, "", List.of(), false);
    }

    public static @Nullable String launchScheduled(MinecraftServer server, net.minecraft.world.level.Level level,
                                                   BlockPos pad, UUID team, UUID owner, String ownerName,
                                                   ResourceLocation destinationId, Mission.Type type, int probes,
                                                   String expeditionName, List<String> crew,
                                                   boolean autoCollect) {
        return launchFrom(server, level, pad, team, owner, ownerName, level.getRandom(),
                complex -> complex == null ? null : new BusStock(complex), destinationId, type, probes,
                expeditionName, crew, autoCollect);
    }

    private static @Nullable String launchFrom(MinecraftServer server, net.minecraft.world.level.Level level,
                                               BlockPos pad, UUID team, UUID ownerId, String ownerName,
                                               RandomSource random,
                                               java.util.function.Function<LaunchComplex, Stock> stockOf,
                                               ResourceLocation destinationId, Mission.Type type, int probes,
                                               String expeditionName, List<String> crew, boolean autoCollect) {
        var cfg = ContinuumConfigs.INSTANCE;
        if (!level.isLoaded(pad)) return ContinuumLang.enc("phoenix_continuum.continuum_missions.no_pad");

        ContinuumBody body = ContinuumData.body(destinationId);
        if (body == null) return ContinuumLang.enc("phoenix_continuum.continuum_missions.no_destination");

        long[] passing = null;
        if (body.transientInfo() != null) {
            if (type != Mission.Type.EXTRACT) return ContinuumLang.enc("phoenix_continuum.continuum_missions.only_extract_passing", body.visitorLabel());
            passing = ContinuumTransients.get(server).window(destinationId, System.currentTimeMillis());
            if (passing == null) return ContinuumLang.enc("phoenix_continuum.continuum_missions.passed_beyond", body.name());
        }

        LaunchComplex complex = LaunchSites.at(level, pad);
        long launchEnergy = 0;
        if (complex != null) {
            if (!complex.isFormed()) return ContinuumLang.enc("phoenix_continuum.continuum_missions.not_built");

            int padTier = complex.padTier();
            int needed = requiredPadTier(body);
            if (padTier < needed) {
                return ContinuumLang.enc(PadTiers.basic() ? "phoenix_continuum.continuum_missions.pad_tier_low_basic" : "phoenix_continuum.continuum_missions.pad_tier_low_hatch", tierName(padTier), body.name(), tierName(needed));
            }

            launchEnergy = PadTiers.launchEnergy(padTier);

            launchEnergy = Math.min(launchEnergy, complex.energyCapacity() * 9 / 10);
            if (complex.storedEnergy() < launchEnergy) {
                return ContinuumLang.enc(PadTiers.basic() ? "phoenix_continuum.continuum_missions.pad_energy_basic" : "phoenix_continuum.continuum_missions.pad_energy_hatches", launchEnergy, PadTiers.energyUnit(),
                        complex.storedEnergy());
            }
        } else if (level.getBlockState(pad).getBlock() instanceof LaunchPadBlock) {
            if (cfg.requireMultiblockPad && !PadTiers.basic()) {
                return ContinuumLang.enc("phoenix_continuum.continuum_missions.complex_required");
            }
        } else {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.no_pad");
        }

        ContinuumTeamData data = ContinuumTeamData.get(server);

        DiscoveryStage stage = data.effectiveStage(server, team, destinationId);
        if (stage == DiscoveryStage.UNKNOWN) return ContinuumLang.enc("phoenix_continuum.continuum_missions.unresolved");
        ServerPlayer gated = server.getPlayerList().getPlayer(ownerId);
        if (gated != null) {
            Component refused = ContinuumGates.missionDenial(gated, ContinuumGates.featureOf(type),
                    destinationId.toString(), body.system().toString());
            if (refused != null) return ContinuumLang.fromComponent(refused);
        }
        if (!net.phoenix.continuum.common.visibility.ContinuumVisibility.accessible(server, team, body)) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.cannot_reach", body.name());
        }

        if (data.missions(team).size() >= cfg.maxMissionsPerTeam + data.stationCount(team)) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.too_many");
        }
        int stationLevel = data.stationLevel(team, destinationId);
        Affinities.Factors affinity = Affinities.of(server, team, body);

        Stock stock = stockOf.apply(complex);
        if (stock == null) return ContinuumLang.enc("phoenix_continuum.continuum_missions.no_pad");

        long now = System.currentTimeMillis();
        int carried = 0;
        int kitsCarried = 0;
        java.util.function.Predicate<ItemStack> special = null;
        Outpost outpost = null;
        Mission stranded = null;
        switch (type) {
            case SHELL -> {
                if (!Swarms.isSwarm(body)) return ContinuumLang.enc("phoenix_continuum.continuum_missions.shell_needs_star");
                if (stage != DiscoveryStage.SURVEYED) return ContinuumLang.enc("phoenix_continuum.continuum_missions.shell_needs_survey");
                if (data.shellLevel(team, destinationId) >= Swarms.shellMaxLevel(body)) {
                    return ContinuumLang.enc("phoenix_continuum.continuum_missions.shell_finished", Swarms.shellName(body), body.name());
                }
                for (Mission other : data.missions(team)) {
                    if (other.type == Mission.Type.SHELL && other.destination.equals(destinationId) &&
                            !other.state.finished()) {
                        return ContinuumLang.enc("phoenix_continuum.continuum_missions.shell_crew_en_route", body.name());
                    }
                }
                carried = Swarms.payloadPerLevel(body, true);
                boolean penroseShell = Swarms.isPenrose(body);
                special = penroseShell ? IS_PENROSE_SEGMENT : IS_SEGMENT;
                if (stock.count(special) < carried) return notEnoughPayload(carried, true, penroseShell, stock);
            }
            case STATION -> {
                if (Swarms.isSwarm(body) && data.shellLevel(team, destinationId) > 0) {
                    return ContinuumLang.enc("phoenix_continuum.continuum_missions.swarm_in_shell", Swarms.shellName(body));
                }
                if (stage != DiscoveryStage.SURVEYED) return ContinuumLang.enc("phoenix_continuum.continuum_missions.station_needs_survey");
                if (stationLevel >= Stations.maxLevel(body)) {
                    return ContinuumLang.enc(Swarms.isSwarm(body) ? "phoenix_continuum.continuum_missions.swarm_full" : "phoenix_continuum.continuum_missions.station_full", body.name());
                }
                for (Mission other : data.missions(team)) {
                    if (other.type == Mission.Type.STATION && other.destination.equals(destinationId) &&
                            !other.state.finished()) {
                        return ContinuumLang.enc("phoenix_continuum.continuum_missions.station_crew_en_route", body.name());
                    }
                }
                if (Swarms.isSwarm(body)) {
                    carried = Swarms.payloadPerLevel(body, false);
                    boolean penroseSwarm = Swarms.isPenrose(body);
                    special = penroseSwarm ? IS_PENROSE_COLLECTOR : IS_COLLECTOR;
                    if (stock.count(special) < carried) return notEnoughPayload(carried, false, penroseSwarm, stock);
                } else {
                    carried = Stations.probesPerLevel(body);
                    if (stock.probes() < carried) return notEnoughProbes(carried, stock);
                }
            }
            case RESCUE -> {
                stranded = strandedAt(data, team, destinationId);
                if (stranded == null) return ContinuumLang.enc("phoenix_continuum.continuum_missions.no_distress", body.name());
                if (stranded.rescuing) return ContinuumLang.enc("phoenix_continuum.continuum_missions.rescue_en_route", body.name());
                kitsCarried = Math.max(1, cfg.rescueKits);
                if (stock.kits() < kitsCarried) {
                    return ContinuumLang.enc(kitsCarried == 1 ? "phoenix_continuum.continuum_missions.rescue_kits_one" : "phoenix_continuum.continuum_missions.rescue_kits_many", kitsCarried, stock.place());
                }
            }
            case EXTRACT -> {
                if (stage != DiscoveryStage.SURVEYED) {
                    return ContinuumLang.enc("phoenix_continuum.continuum_missions.extract_needs_survey");
                }
                if (body.yields().isEmpty()) return ContinuumLang.enc("phoenix_continuum.continuum_missions.nothing_extract");
                carried = Math.max(1, Math.min(probes, cfg.maxProbesPerMission));
                if (stock.probes() < carried) return notEnoughProbes(carried, stock);
            }
            case DEPLOY -> {
                if (stage != DiscoveryStage.SURVEYED) return ContinuumLang.enc("phoenix_continuum.continuum_missions.outpost_needs_survey");
                if (body.yields().isEmpty()) return ContinuumLang.enc("phoenix_continuum.continuum_missions.nothing_build");
                Outpost existing = data.outpost(team, destinationId);
                int room = cfg.maxProbesPerOutpost - (existing == null ? 0 : existing.probes());

                for (Mission other : data.missions(team)) {
                    if (other.type == Mission.Type.DEPLOY && other.destination.equals(destinationId) &&
                            !other.state.finished()) {
                        room -= other.probes;
                    }
                }
                if (room <= 0) return ContinuumLang.enc("phoenix_continuum.continuum_missions.outpost_full");
                carried = Math.max(1, Math.min(Math.min(probes, room), cfg.maxProbesPerMission));
                if (stock.probes() < carried) return notEnoughProbes(carried, stock);
            }
            case HAUL -> {
                outpost = data.outpost(team, destinationId);
                if (outpost == null) return ContinuumLang.enc("phoenix_continuum.continuum_missions.no_outpost");
                outpost.settle(now, RocketStats.outpostCycleMillis(), RocketStats.outpostMaxReady(),
                        OutpostPower.payerFor(server, team));
                if (outpost.readyCycles() <= 0) return ContinuumLang.enc("phoenix_continuum.continuum_missions.nothing_haul");
            }
            case REPAIR -> {
                outpost = data.outpost(team, destinationId);
                if (outpost == null) return ContinuumLang.enc("phoenix_continuum.continuum_missions.no_outpost");
                outpost.settle(now, RocketStats.outpostCycleMillis(), RocketStats.outpostMaxReady(),
                        OutpostPower.payerFor(server, team));
                if (!outpost.damaged()) return ContinuumLang.enc("phoenix_continuum.continuum_missions.not_damaged");
                kitsCarried = Math.max(1, cfg.outpostRepairKits);
                if (stock.kits() < kitsCarried) {
                    return ContinuumLang.enc(kitsCarried == 1 ? "phoenix_continuum.continuum_missions.repair_kits_one" : "phoenix_continuum.continuum_missions.repair_kits_many", kitsCarried, stock.place());
                }
            }
            case SURVEY -> {

                if (stage == DiscoveryStage.SURVEYED &&
                        (body.yields().isEmpty() || data.depth(team, destinationId) >= body.yields().size())) {
                    return ContinuumLang.enc("phoenix_continuum.continuum_missions.fully_surveyed", body.name());
                }
            }
        }

        ItemStack rocket = stock.peekRocket();
        if (rocket.isEmpty()) return ContinuumLang.enc("phoenix_continuum.continuum_missions.need_rocket", stock.place());

        if (type != Mission.Type.SURVEY && cfg.rocketTierGating &&
                RocketStats.tier(rocket) < RocketStats.requiredTier(body)) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.hull_too_low", body.name(), RocketStats.tierName(RocketStats.requiredTier(body)),
                    RocketStats.tierName(RocketStats.tier(rocket)));
        }
        String shield = RocketStats.requiredShield(body);
        if (shield != null && type != Mission.Type.SURVEY && RocketStats.level(rocket, shield) <= 0) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.needs_shield", body.name(), RocketStats.shieldName(shield));
        }
        if (RocketStats.interlockRefuses(rocket)) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.interlock");
        }
        if (passing != null) {
            long arrives = now + Math.round(RocketStats.tripMillis(body, rocket) * Stations.tripFactor(stationLevel) *
                    affinity.trip());
            if (arrives > passing[1]) {
                return ContinuumLang.enc("phoenix_continuum.continuum_missions.gone_before_arrive", body.name(), durationText(passing[1] - now));
            }
        }
        if (stranded != null) {
            long fades = stranded.distressUntil - now;
            if (Math.round(RocketStats.tripMillis(body, rocket) * Stations.tripFactor(stationLevel) * affinity.trip()) >
                    fades) {
                return ContinuumLang.enc("phoenix_continuum.continuum_missions.signal_fades_first",
                        net.phoenix.continuum.common.ContinuumMissions.durationText(fades));
            }
        }

        if (complex != null && !complex.drainEnergy(launchEnergy)) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.lost_power");
        }

        ItemStack flown = stock.takeRocket();
        if (flown.isEmpty()) return ContinuumLang.enc("phoenix_continuum.continuum_missions.rocket_taken");
        if (carried > 0) {
            if (special != null) stock.take(special, carried);
            else stock.takeProbes(carried);
        }
        if (kitsCarried > 0) stock.takeKits(kitsCarried);

        int haulProbes = 0;
        int haulCycles = 0;
        if (type == Mission.Type.HAUL && outpost != null) {
            haulProbes = outpost.probes();
            haulCycles = outpost.claim(now);
            data.setDirty();
        }

        String expedition = cleanName(expeditionName);
        
        float failureChance = Math.max(0.0f, RocketStats.failureChance(flown, stage) - CREW_RISK_REDUCTION * crew.size());
        boolean succeeds = random.nextFloat() >= failureChance;

        boolean kitRun = type == Mission.Type.REPAIR || type == Mission.Type.RESCUE;
        int payload = type == Mission.Type.HAUL ? haulProbes : kitRun ? kitsCarried : carried;
        MissionEvent event = MissionEvent.roll(type, payload, random, cfg.missionEventChance);
        List<String> events = new ArrayList<>();
        if (event != null) events.add(event.id);

        List<ItemStack> rewards = new ArrayList<>();
        if (succeeds && type == Mission.Type.EXTRACT) {
            int rolls = carried - (event == MissionEvent.MICROMETEOROIDS ? 1 : 0) +
                    (event == MissionEvent.DERELICT ? 1 : 0);
            rewards = rollRewards(team, body,
                    Affinities.scaled(Math.max(0, rolls), affinity.yield(), random),
                    random);
        }
        if (succeeds && type == Mission.Type.HAUL) {
            int rolls = Affinities.scaled(haulProbes * haulCycles + (event == MissionEvent.DERELICT ? haulProbes : 0),
                    Stations.yieldFactor(stationLevel) * affinity.yield(), random);
            rewards = rollRewards(team, body, rolls, random);
        }

        long trip = Math.round(RocketStats.tripMillis(body, flown) * Stations.tripFactor(stationLevel) *
                affinity.trip());
        if (event == MissionEvent.TAILWIND) trip = Math.max(1000L, Math.round(trip * MissionEvent.TAILWIND_FACTOR));
        
        LaunchWindows.Window window = LaunchWindows.of(body, now);
        trip = Math.max(1000L, Math.round(trip * window.factor()));

        int shownProbes = type == Mission.Type.HAUL ? haulProbes : kitRun ? kitsCarried : carried;
        Mission mission = new Mission(UUID.randomUUID(), team, ownerId, ownerName,
                destinationId, type, shownProbes, rewards, now, trip, succeeds,
                RocketStats.wearCost(body, flown) * Stations.wearFactor(stationLevel) * affinity.wear() *
                        window.factor(),
                flown,
                Mission.State.ACTIVE, events);
        if (complex != null) {
            mission.padDimension = level.dimension().location().toString();
            mission.padPos = pad.asLong();
        }
        mission.expedition = expedition;
        mission.crew = crew;
        mission.autoCollect = autoCollect;
        data.addMission(mission);
        net.phoenix.continuum.api.ContinuumEvents.postMission(
                net.phoenix.continuum.api.ContinuumEvents.MissionChanged.Kind.LAUNCHED, mission);
        data.log(team, MissionLog.Kind.LAUNCH, destinationId,
                ContinuumLang.enc("phoenix_continuum.continuum_missions.log_launched",
                        mission.expedition.isEmpty() ? ContinuumLang.enc(type.labelKey()) :
                                ContinuumLang.enc("phoenix_continuum.continuum_missions.log_expedition_head", mission.expeditionTag(), ContinuumLang.enc(type.labelKey())),
                        body.name(), durationText(trip),
                        window.applies() ? ContinuumLang.enc("phoenix_continuum.continuum_missions.log_window", window.label().toLowerCase(java.util.Locale.ROOT)) : "",
                        stranded != null ? ContinuumLang.enc("phoenix_continuum.continuum_missions.log_rescue_tail") : ""));
        if (stranded != null) {
            stranded.rescuing = true;
            data.setDirty();
        }
        return null;
    }

    public static int requiredPadTier(ContinuumBody body) {
        return PadTiers.required(body);
    }

    public static String tierName(int tier) {
        return PadTiers.name(tier);
    }

    private static String notEnoughPayload(int needed, boolean shell, boolean penrose, Stock stock) {
        return ContinuumLang.enc("phoenix_continuum.continuum_missions.need_payload", Swarms.payloadName(shell, needed, penrose), stock.place());
    }

    private static String lostPayload(Mission mission) {
        ContinuumBody body = ContinuumData.body(mission.destination);
        if (mission.type == Mission.Type.SHELL) return Swarms.payloadName(true, mission.probes, Swarms.isPenrose(body));
        if (Swarms.isSwarm(body)) return Swarms.payloadName(false, mission.probes, Swarms.isPenrose(body));
        return ContinuumLang.text(mission.probes == 1 ? "phoenix_continuum.continuum_missions.probes_one" : "phoenix_continuum.continuum_missions.probes_many", mission.probes);
    }

    private static String notEnoughProbes(int needed, Stock stock) {
        return ContinuumLang.enc(needed == 1 ? "phoenix_continuum.continuum_missions.need_probes_one" : "phoenix_continuum.continuum_missions.need_probes_many", needed, stock.place());
    }

    public static ItemStack findRocket(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ContinuumRocketItem) return stack;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof ContinuumRocketItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static int countItems(ServerPlayer player, java.util.function.Predicate<ItemStack> matches) {
        int total = 0;
        for (var list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack stack : list) {
                if (matches.test(stack)) total += stack.getCount();
            }
        }
        return total;
    }

    private static void removeItems(ServerPlayer player, java.util.function.Predicate<ItemStack> matches, int count) {
        int remaining = count;
        for (var list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack stack : list) {
                if (remaining <= 0) return;
                if (!matches.test(stack)) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static int countRockets(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ContinuumRocketItem) total += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof ContinuumRocketItem) total += stack.getCount();
        }
        return total;
    }

    private static int countProbes(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ContinuumProbeItem) total += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof ContinuumProbeItem) total += stack.getCount();
        }
        return total;
    }

    private static int countKits(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ContinuumRepairKitItem) total += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof ContinuumRepairKitItem) total += stack.getCount();
        }
        return total;
    }

    private static void removeKits(ServerPlayer player, int count) {
        int remaining = count;
        for (var list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack stack : list) {
                if (remaining <= 0) return;
                if (!(stack.getItem() instanceof ContinuumRepairKitItem)) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static void removeProbes(ServerPlayer player, int count) {
        int remaining = count;
        for (var list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack stack : list) {
                if (remaining <= 0) return;
                if (!(stack.getItem() instanceof ContinuumProbeItem)) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    static List<ItemStack> rollRewards(UUID team, ContinuumBody body, int probes, RandomSource random) {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (int i = 0; i < probes; i++) {
            for (ContinuumBody.Yield yield : body.yields()) {
                if (random.nextFloat() >= yield.chance()) continue;
                Item item = BuiltInRegistries.ITEM.get(yield.item());
                if (item == Items.AIR) continue;
                int base = yield.min() + random.nextInt(yield.max() - yield.min() + 1);

                int count = Math.max(1, Math.round(base * Deposits.richness(team, body.id(), yield.item())));
                totals.merge(item, count, Integer::sum);
            }
        }

        List<ItemStack> stacks = new ArrayList<>();
        totals.forEach((item, total) -> {
            int left = total;
            while (left > 0) {
                int size = Math.min(left, item.getMaxStackSize());
                stacks.add(new ItemStack(item, size));
                left -= size;
            }
        });
        return stacks;
    }

    public static void resolveDue(MinecraftServer server) {
        long now = System.currentTimeMillis();
        ContinuumTeamData data = ContinuumTeamData.get(server);

        for (UUID team : data.teamsWithMissions()) {
            boolean changed = false;
            for (Mission mission : List.copyOf(data.missions(team))) {
                if (!mission.due(now)) continue;
                land(server, data, mission);
                changed = true;
            }

            for (Mission mission : List.copyOf(data.missions(team))) {
                if (mission.autoCollect && mission.state.finished() && !mission.stranded() &&
                        autoCollect(server, data, mission)) {
                    changed = true;
                }
            }

            for (Mission mission : List.copyOf(data.missions(team))) {
                if (!mission.stranded() || mission.rescuing || now < mission.distressUntil) continue;
                ContinuumBody where = ContinuumData.body(mission.destination);
                data.removeMission(team, mission.id);
                notifyLogged(server, data, team, MissionLog.Kind.LOST, mission.destination,
                        ContinuumLang.tr("phoenix_continuum.continuum_missions.the_distress_signal_from_faded", (where != null ? where.name() : mission.destination.getPath())).withStyle(ChatFormatting.DARK_RED));
                changed = true;
            }
            if (changed) {
                data.setDirty();
                ContinuumServerEvents.sendStateToTeam(server, team);
            }
        }
    }

    private static boolean autoCollect(MinecraftServer server, ContinuumTeamData data, Mission mission) {
        LaunchComplex pad = launchComplexOf(server, mission);
        if (pad == null) return false;

        boolean changed = false;
        if (!mission.rocket.isEmpty()) {
            ItemStack rest = pad.insertInput(mission.rocket.copy());
            if (!rest.isEmpty()) rest = pad.insertOutput(rest);
            if (rest.getCount() != mission.rocket.getCount()) changed = true;
            mission.rocket.setCount(rest.getCount());
        }
        int items = 0;
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack reward : mission.rewards) {
            ItemStack rest = pad.insertOutput(reward.copy());
            items += reward.getCount() - rest.getCount();
            if (!rest.isEmpty()) left.add(rest);
        }
        if (items > 0) changed = true;
        mission.rewards.clear();
        mission.rewards.addAll(left);

        if (mission.rocket.isEmpty() && left.isEmpty()) {
            data.removeMission(mission.team, mission.id);
            ContinuumBody from = ContinuumData.body(mission.destination);
            data.log(mission.team, MissionLog.Kind.COLLECTED, mission.destination,
                    ContinuumLang.enc(items > 0 ? "phoenix_continuum.continuum_missions.pad_took_in_items" : "phoenix_continuum.continuum_missions.pad_took_in",
                            from != null ? from.name() : mission.destination.getPath(), items));
            return true;
        }
        if (changed) data.setDirty();
        if (!mission.fullNoticeSent) {
            mission.fullNoticeSent = true;
            data.setDirty();
            notifyTeam(server, mission.team, ContinuumLang.tr("phoenix_continuum.continuum_missions.a_scheduled_return_does_not").withStyle(ChatFormatting.GOLD));
        }
        return changed;
    }

    public static int collectAll(ServerPlayer player) {
        UUID team = net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        ContinuumTeamData data = ContinuumTeamData.get(player.server);
        int collected = 0;
        for (Mission mission : List.copyOf(data.missions(team))) {
            if (!mission.state.finished() || mission.stranded()) continue;
            if (collect(player, mission.id) == null) collected++;
        }
        if (collected == 0) {
            player.sendSystemMessage(ContinuumLang.tr("phoenix_continuum.continuum_missions.nothing_is_ready_to_collect").withStyle(ChatFormatting.GRAY));
        }
        return collected;
    }

    private static @Nullable Mission strandedAt(ContinuumTeamData data, UUID team, ResourceLocation body) {
        return strandedAt(data, team, body, false);
    }

    private static @Nullable Mission strandedAt(ContinuumTeamData data, UUID team, ResourceLocation body,
                                                boolean beingRescued) {
        Mission best = null;
        for (Mission mission : data.missions(team)) {
            if (!mission.stranded() || !mission.destination.equals(body)) continue;
            if (!beingRescued && mission.rescuing) continue;
            if (beingRescued && !mission.rescuing) continue;
            if (best == null || mission.distressUntil < best.distressUntil) best = mission;
        }
        return best;
    }

    public static String durationText(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + String.format("%02dm", minutes);
        return minutes + "m " + String.format("%02ds", seconds % 60);
    }

    private static void land(MinecraftServer server, ContinuumTeamData data, Mission mission) {
        ContinuumBody destination = ContinuumData.body(mission.destination);
        String name = destination != null ? destination.name() : mission.destination.getPath();
        Component probesText = ContinuumLang.tr(mission.probes == 1 ? "phoenix_continuum.continuum_missions.probes_text_one" : "phoenix_continuum.continuum_missions.probes_text_many", mission.probes);

        mission.events.removeIf(id -> {
            MissionEvent e = MissionEvent.byId(id);
            return e == null || (!mission.willSucceed && e != MissionEvent.SOLAR_FLARE);
        });
        boolean flare = mission.events.contains(MissionEvent.SOLAR_FLARE.id);
        boolean micro = mission.events.contains(MissionEvent.MICROMETEOROIDS.id);
        boolean echo = mission.events.contains(MissionEvent.SIGNAL_ECHO.id);

        float wear = RocketStats.wear(mission.rocket) + mission.wearCost +
                (flare ? MissionEvent.SOLAR_FLARE_WEAR : 0.0f);
        if (mission.willSucceed) {
            mission.state = Mission.State.SUCCESS;
            net.phoenix.continuum.api.ContinuumEvents.postMission(
                    net.phoenix.continuum.api.ContinuumEvents.MissionChanged.Kind.SUCCEEDED, mission);
            RocketStats.setWear(mission.rocket, wear);

            Component message = switch (mission.type) {
                case SURVEY -> {
                    if (destination != null) {
                        boolean first = !data.storedStage(mission.team, destination.id())
                                .atLeast(DiscoveryStage.SURVEYED);
                        if (first) reveal(data, mission.team, destination);
                        if (echo) revealExtra(data, mission.team, destination);
                        surveyFindings(data, mission, destination);
                    }
                    yield ContinuumLang.tr("phoenix_continuum.continuum_missions.landed_survey", name);
                }
                case EXTRACT -> ContinuumLang.tr("phoenix_continuum.continuum_missions.landed_extract", name);
                case DEPLOY -> {
                    int deployed = Math.max(1, mission.probes - (micro ? 1 : 0));
                    data.deployProbes(mission.team, mission.destination, deployed, System.currentTimeMillis());
                    yield ContinuumLang.tr(deployed == 1 ? "phoenix_continuum.continuum_missions.landed_deploy_one" : "phoenix_continuum.continuum_missions.landed_deploy_many", name, deployed);
                }
                case HAUL -> ContinuumLang.tr("phoenix_continuum.continuum_missions.landed_haul", name);
                case REPAIR -> {
                    Outpost repaired = data.outpost(mission.team, mission.destination);
                    if (repaired != null) repaired.repair(System.currentTimeMillis());
                    yield ContinuumLang.tr("phoenix_continuum.continuum_missions.landed_repair", name);
                }
                case SHELL -> {
                    int shellBuilt = data.shellLevel(mission.team, mission.destination) + 1;
                    boolean tookApart = data.stationLevel(mission.team, mission.destination) > 0;
                    data.setShellLevel(mission.team, mission.destination, shellBuilt);
                    ContinuumBody shellBody = ContinuumData.body(mission.destination);
                    int shown = Math.min(shellBuilt, Swarms.shellMaxLevel(shellBody));
                    yield ContinuumLang.tr(tookApart ? "phoenix_continuum.continuum_missions.landed_shell_apart" : "phoenix_continuum.continuum_missions.landed_shell", Swarms.shellName(shellBody), name, shown,
                            Swarms.describeShell(shellBody, shown));
                }
                case STATION -> {
                    int built = data.stationLevel(mission.team, mission.destination) + 1;
                    data.setStationLevel(mission.team, mission.destination, built);
                    ContinuumBody station = ContinuumData.body(mission.destination);
                    if (Swarms.isSwarm(station)) {
                        yield ContinuumLang.tr("phoenix_continuum.continuum_missions.landed_swarm", Swarms.kindName(station).substring(0, 1).toUpperCase(java.util.Locale.ROOT) +
                                Swarms.kindName(station).substring(1), name,
                                Math.min(built, Stations.maxLevel(station)), Swarms.describe(station,
                                        Math.min(built, Stations.maxLevel(station))));
                    }
                    yield ContinuumLang.tr("phoenix_continuum.continuum_missions.landed_station", name, Math.min(built, Stations.MAX_LEVEL), Stations.describe(built));
                }
                case RESCUE -> {

                    Mission saved = strandedAt(data, mission.team, mission.destination, true);
                    if (saved == null) saved = strandedAt(data, mission.team, mission.destination, false);
                    if (saved != null) {
                        saved.distressUntil = 0;
                        saved.rescuing = false;
                    }
                    yield ContinuumLang.tr("phoenix_continuum.continuum_missions.landed_rescue", name);
                }
            };
            notifyLogged(server, data, mission.team,
                    mission.type == Mission.Type.STATION || mission.type == Mission.Type.SHELL ?
                            MissionLog.Kind.STATION :
                            mission.type == Mission.Type.RESCUE ? MissionLog.Kind.RESCUED : MissionLog.Kind.LANDED,
                    mission.destination,
                    (mission.expedition.isEmpty() ? message.copy().append(eventText(mission)) :
                            ContinuumLang.tr("phoenix_continuum.continuum_missions.landed_expedition", mission.expeditionTag(), message, eventText(mission)))
                            .withStyle(ChatFormatting.GREEN));
        } else {
            mission.state = Mission.State.FAILED;
            net.phoenix.continuum.api.ContinuumEvents.postMission(
                    net.phoenix.continuum.api.ContinuumEvents.MissionChanged.Kind.FAILED, mission);
            RocketStats.setWear(mission.rocket, wear + RocketStats.FAILURE_WEAR);

            Component lost = switch (mission.type) {
                case SURVEY -> Component.empty();
                case EXTRACT, DEPLOY -> ContinuumLang.tr("phoenix_continuum.continuum_missions.lost_probes", probesText);
                case STATION, SHELL -> ContinuumLang.tr("phoenix_continuum.continuum_missions.lost_payload", lostPayload(mission));
                case HAUL -> ContinuumLang.tr("phoenix_continuum.continuum_missions.lost_haul");
                case REPAIR -> ContinuumLang.tr("phoenix_continuum.continuum_missions.lost_repair");
                case RESCUE -> ContinuumLang.tr("phoenix_continuum.continuum_missions.lost_rescue");
            };

            if (mission.type == Mission.Type.RESCUE) {
                Mission waiting = strandedAt(data, mission.team, mission.destination, true);
                if (waiting != null) waiting.rescuing = false;
            }

            var cfg = ContinuumConfigs.INSTANCE;
            boolean canStrand = mission.type == Mission.Type.EXTRACT || mission.type == Mission.Type.DEPLOY ||
                    mission.type == Mission.Type.HAUL;
            if (canStrand && !Stations.preventsStranding(data.stationLevel(mission.team, mission.destination)) &&
                    RandomSource.create().nextDouble() < cfg.distressChance) {
                mission.distressUntil = System.currentTimeMillis() + cfg.distressWindowMinutes * 60_000L;
                notifyLogged(server, data, mission.team, MissionLog.Kind.STRANDED, mission.destination,
                        ContinuumLang.tr("phoenix_continuum.continuum_missions.distress_signal_from_the_failed", name, mission.type.label().toLowerCase(), lost, Math.max(1, cfg.rescueKits), durationText(cfg.distressWindowMinutes * 60_000L), eventText(mission)).withStyle(ChatFormatting.GOLD));
            } else {
                notifyLogged(server, data, mission.team, MissionLog.Kind.FAILED, mission.destination,
                        ContinuumLang.tr("phoenix_continuum.continuum_missions.to_failed_the_rocket_came", mission.type.label(), name, lost, eventText(mission)).withStyle(ChatFormatting.RED));
            }
        }
    }

    private static void reveal(ContinuumTeamData data, UUID team, ContinuumBody destination) {
        data.raise(team, destination.id(), DiscoveryStage.SURVEYED);

        for (ContinuumBody moon : ContinuumData.moonsOf(destination.id())) {
            data.raise(team, moon.id(), DiscoveryStage.DETECTED);
        }

        ContinuumBody nearest = null;
        for (ContinuumBody other : ContinuumData.bodiesOf(destination.system())) {
            if (other.isMoon() || other.id().equals(destination.id())) continue;
            DiscoveryStage stage = data.storedStage(team, other.id());
            if (stage != DiscoveryStage.UNKNOWN) continue;
            if (nearest == null || other.orbitAu() < nearest.orbitAu()) nearest = other;
        }
        if (nearest != null) data.raise(team, nearest.id(), DiscoveryStage.DETECTED);
    }

    private static net.minecraft.network.chat.MutableComponent eventText(Mission mission) {
        net.minecraft.network.chat.MutableComponent text = Component.empty();
        for (String id : mission.events) {
            MissionEvent event = MissionEvent.byId(id);
            if (event != null) text.append(" ").append(ContinuumLang.tr("phoenix_continuum.mission_event." + event.id));
        }
        return text;
    }

    private static void surveyFindings(ContinuumTeamData data, Mission mission, ContinuumBody destination) {
        int total = destination.yields().size();
        data.revealDeposit(mission.team, destination.id(), total);

        RandomSource random = RandomSource.create();
        if (random.nextDouble() >= ContinuumConfigs.INSTANCE.surveyAnomalyChance) return;

        MissionEvent anomaly = MissionEvent.rollAnomaly(random);
        mission.events.add(anomaly.id);
        switch (anomaly) {
            case SURFACE_CACHE -> mission.rewards.addAll(rollRewards(mission.team, destination, 2, random));
            case ANCIENT_RUINS -> mission.rewards.addAll(rollRewards(mission.team, destination, 5, random));
            case VEIN_SIGNATURE -> data.revealDeposit(mission.team, destination.id(), total);
            default -> {}
        }
    }

    private static void revealExtra(ContinuumTeamData data, UUID team, ContinuumBody destination) {
        ContinuumBody nearest = null;
        for (ContinuumBody other : ContinuumData.bodiesOf(destination.system())) {
            if (other.isMoon() || other.id().equals(destination.id())) continue;
            if (data.storedStage(team, other.id()) != DiscoveryStage.UNKNOWN) continue;
            if (nearest == null || other.orbitAu() < nearest.orbitAu()) nearest = other;
        }
        if (nearest != null) data.raise(team, nearest.id(), DiscoveryStage.DETECTED);
    }

    public static @Nullable String collect(ServerPlayer player, UUID missionId) {
        MinecraftServer server = player.server;
        UUID team = net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        ContinuumTeamData data = ContinuumTeamData.get(server);

        Mission mission = data.mission(team, missionId).orElse(null);
        if (mission == null) return ContinuumLang.enc("phoenix_continuum.continuum_missions.mission_gone");
        if (!mission.state.finished()) return ContinuumLang.enc("phoenix_continuum.continuum_missions.mission_underway");
        if (mission.stranded()) {
            return ContinuumLang.enc("phoenix_continuum.continuum_missions.rocket_stranded", durationText(mission.distressUntil - System.currentTimeMillis()));
        }

        LaunchComplex pad = launchComplexOf(server, mission);
        
        if (pad != null && !pad.hasBuses()) pad = null;
        ItemStack rocket = mission.rocket.copy();
        int items = 0;
        int delivered = 0;
        if (pad != null) {
            rocket = pad.insertInput(rocket);
            if (!rocket.isEmpty()) rocket = pad.insertOutput(rocket);
        }
        if (!rocket.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, rocket);
        for (ItemStack reward : mission.rewards) {
            items += reward.getCount();
            ItemStack rest = pad != null ? pad.insertOutput(reward.copy()) : reward.copy();
            delivered += reward.getCount() - rest.getCount();
            if (!rest.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, rest);
        }
        data.removeMission(team, missionId);
        ContinuumServerEvents.sendStateToTeam(server, team);

        ContinuumBody collectedFrom = ContinuumData.body(mission.destination);
        data.log(team, MissionLog.Kind.COLLECTED, mission.destination,
                ContinuumLang.enc("phoenix_continuum.continuum_missions.log_collected",
                        collectedFrom != null ? collectedFrom.name() : mission.destination.getPath(),
                        items > 0 ? ContinuumLang.enc(delivered > 0 ? "phoenix_continuum.continuum_missions.log_collected_items_delivered" : "phoenix_continuum.continuum_missions.log_collected_items", items, delivered) : ""));
        if (items > 0) {
            Component where = delivered >= items ? ContinuumLang.tr("phoenix_continuum.continuum_missions.where_all_buses") :
                    delivered > 0 ? ContinuumLang.tr("phoenix_continuum.continuum_missions.where_some_buses", delivered) : ContinuumLang.tr("phoenix_continuum.continuum_missions.where_inventory");
            player.sendSystemMessage(
                    ContinuumLang.tr("phoenix_continuum.continuum_missions.collected_the_rocket_and_resources", items, where)
                            .withStyle(ChatFormatting.GREEN));
        }
        return null;
    }

    private static @Nullable LaunchComplex launchComplexOf(MinecraftServer server, Mission mission) {
        if (mission.padDimension == null) return null;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation(mission.padDimension));
        var level = server.getLevel(key);
        BlockPos pos = BlockPos.of(mission.padPos);
        if (level == null || !level.isLoaded(pos)) return null;
        LaunchComplex machine = LaunchSites.at(level, pos);
        return machine != null && machine.isFormed() ? machine : null;
    }

    static void notifyLogged(MinecraftServer server, ContinuumTeamData data, UUID team, MissionLog.Kind kind,
                             ResourceLocation body, Component message) {
        data.log(team, kind, body, ContinuumLang.fromComponent(message));
        notifyTeam(server, team, message);
    }

    static void notifyTeam(MinecraftServer server, UUID team, Component message) {
        if (!ContinuumTeamData.get(server).isEngaged(team)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (net.phoenix.continuum.api.ContinuumHost.teams().onTeam(player, team)) player.sendSystemMessage(message);
        }
    }
}
