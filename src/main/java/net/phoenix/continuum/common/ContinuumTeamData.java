package net.phoenix.continuum.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.data.DiscoveryStage;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ContinuumTeamData extends SavedData {

    private static final String ID = "continuum_state";

    private final Map<UUID, Map<ResourceLocation, DiscoveryStage>> stages = new HashMap<>();
    private final Map<UUID, List<Mission>> missions = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, Outpost>> outposts = new HashMap<>();

    private final Map<UUID, Map<ResourceLocation, Integer>> depths = new HashMap<>();
    private final Map<UUID, java.util.Set<String>> anomalies = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, Integer>> stations = new HashMap<>();
    private final Map<UUID, java.util.List<MissionLog.Entry>> history = new HashMap<>();

    private final java.util.Set<UUID> engaged = new java.util.HashSet<>();

    private final Map<UUID, Map<ResourceLocation, Boolean>> visibilityOverrides = new HashMap<>();

    public @org.jetbrains.annotations.Nullable Boolean visibilityOverride(UUID team, ResourceLocation id) {
        return visibilityOverrides.getOrDefault(team, Map.of()).get(id);
    }

    public void setVisibilityOverride(UUID team, ResourceLocation id,
                                      @org.jetbrains.annotations.Nullable Boolean visible) {
        if (visible == null) {
            Map<ResourceLocation, Boolean> mine = visibilityOverrides.get(team);
            if (mine != null) mine.remove(id);
        } else {
            visibilityOverrides.computeIfAbsent(team, t -> new HashMap<>()).put(id, visible);
        }
        setDirty();
    }

    public static ContinuumTeamData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(ContinuumTeamData::new,
                        (tag, provider) -> ContinuumTeamData.load(tag), null), ID);
    }

    public boolean isEngaged(UUID team) {
        return engaged.contains(team) || !missions.getOrDefault(team, List.of()).isEmpty() ||
                !outposts.getOrDefault(team, Map.of()).isEmpty() || !stations.getOrDefault(team, Map.of()).isEmpty();
    }

    public void markEngaged(UUID team) {
        if (engaged.add(team)) setDirty();
        ResearchGrants.sweep(team);
    }

    private static DiscoveryStage initial(ResourceLocation id) {
        ContinuumBody body = ContinuumData.body(id);
        if (body != null) return body.initialStage();
        ContinuumSystem system = ContinuumData.system(id);
        return system != null ? system.initialStage() : DiscoveryStage.UNKNOWN;
    }

    private static DiscoveryStage max(DiscoveryStage a, DiscoveryStage b) {
        return a.atLeast(b) ? a : b;
    }

    public DiscoveryStage storedStage(UUID team, ResourceLocation id) {
        return max(stages.getOrDefault(team, Map.of()).getOrDefault(id, DiscoveryStage.UNKNOWN), initial(id));
    }

    public boolean isHidden(MinecraftServer server, UUID team, ContinuumBody body) {
        if (body.transientInfo() != null &&
                !ContinuumTransients.get(server).active(body.id(), System.currentTimeMillis())) {
            return true;
        }
        if (body.discipline() != null) {
            String chosen = net.phoenix.continuum.api.ContinuumHost.research(server.overworld()).getDiscipline(team);
            if (!body.discipline().equals(chosen == null ? "none" : chosen)) return true;
        }
        return !net.phoenix.continuum.common.visibility.ContinuumVisibility.visible(server, team, body);
    }

    public DiscoveryStage effectiveStage(MinecraftServer server, UUID team, ResourceLocation id) {
        DiscoveryStage stage = max(stages.getOrDefault(team, Map.of()).getOrDefault(id, DiscoveryStage.UNKNOWN),
                initial(id));
        
        if (net.phoenix.continuum.config.ContinuumConfigs.INSTANCE.progressionMode ==
                net.phoenix.continuum.config.ContinuumConfigs.ProgressionMode.SHOW_ALL) {
            stage = max(stage, DiscoveryStage.DETECTED);
        }

        ContinuumBody body = ContinuumData.body(id);
        if (body != null && isHidden(server, team, body)) return DiscoveryStage.UNKNOWN;

        if (body != null && body.transientInfo() != null) {
            return effectiveStage(server, team, body.system()).atLeast(DiscoveryStage.DETECTED) ?
                    DiscoveryStage.SURVEYED : DiscoveryStage.UNKNOWN;
        }
        if (body != null) {
            net.phoenix.continuum.api.ContinuumHost.Research research = null;
            if (body.surveyedResearch() != null || body.detectedResearch() != null) {
                research = net.phoenix.continuum.api.ContinuumHost.research(server.overworld());
            }
            if (research != null) {
                if (body.surveyedResearch() != null && research.isUnlocked(team, body.surveyedResearch())) {
                    stage = max(stage, DiscoveryStage.SURVEYED);
                } else if (body.detectedResearch() != null && research.isUnlocked(team, body.detectedResearch())) {
                    stage = max(stage, DiscoveryStage.DETECTED);
                }
            }
            return stage;
        }

        ContinuumSystem system = ContinuumData.system(id);
        if (system != null) {
            if (system.surveyedResearch() != null || system.detectedResearch() != null) {
                net.phoenix.continuum.api.ContinuumHost.Research research = net.phoenix.continuum.api.ContinuumHost.research(server.overworld());
                if (system.surveyedResearch() != null && research.isUnlocked(team, system.surveyedResearch())) {
                    stage = max(stage, DiscoveryStage.SURVEYED);
                } else if (system.detectedResearch() != null &&
                        research.isUnlocked(team, system.detectedResearch())) {
                            stage = max(stage, DiscoveryStage.DETECTED);
                        }
            }
            for (ContinuumBody inSystem : ContinuumData.bodiesOf(id)) {

                if (inSystem.transientInfo() != null) continue;
                DiscoveryStage bodyStage = effectiveStage(server, team, inSystem.id());
                if (bodyStage == DiscoveryStage.SURVEYED) return DiscoveryStage.SURVEYED;
                if (bodyStage == DiscoveryStage.DETECTED) stage = max(stage, DiscoveryStage.DETECTED);
            }
        }
        return stage;
    }

    public boolean raise(UUID team, ResourceLocation id, DiscoveryStage stage) {
        Map<ResourceLocation, DiscoveryStage> map = stages.computeIfAbsent(team, t -> new HashMap<>());
        DiscoveryStage current = map.getOrDefault(id, DiscoveryStage.UNKNOWN);
        if (current.atLeast(stage)) return false;
        map.put(id, stage);
        setDirty();
        net.phoenix.continuum.api.ContinuumEvents.postDiscovery(team, id, stage);
        ResearchGrants.sweep(team);
        return true;
    }

    public void force(UUID team, ResourceLocation id, DiscoveryStage stage) {
        stages.computeIfAbsent(team, t -> new HashMap<>()).put(id, stage);
        setDirty();
        ResearchGrants.sweep(team);
    }

    public void resetDiscovery(UUID team) {
        stages.remove(team);
        depths.remove(team);
        anomalies.remove(team);
        setDirty();
    }

    public java.util.Set<String> investigatedAnomalies(UUID team) {
        return anomalies.getOrDefault(team, java.util.Set.of());
    }

    public boolean anomalyInvestigated(UUID team, ResourceLocation body, String id) {
        return investigatedAnomalies(team).contains(body + "|" + id);
    }

    public boolean markAnomalyInvestigated(UUID team, ResourceLocation body, String id) {
        boolean added = anomalies.computeIfAbsent(team, t -> new java.util.HashSet<>()).add(body + "|" + id);
        if (added) setDirty();
        return added;
    }

    public void log(UUID team, MissionLog.Kind kind, ResourceLocation body, String text) {
        java.util.List<MissionLog.Entry> list = history.computeIfAbsent(team, t -> new java.util.ArrayList<>());
        list.add(new MissionLog.Entry(System.currentTimeMillis(), kind, body, text));
        while (list.size() > MissionLog.LIMIT) list.remove(0);
        setDirty();
    }

    public java.util.List<MissionLog.Entry> history(UUID team) {
        return history.getOrDefault(team, java.util.List.of());
    }

    public int stationLevel(UUID team, ResourceLocation body) {
        return stations.getOrDefault(team, Map.of()).getOrDefault(body, 0);
    }

    public Map<ResourceLocation, Integer> stations(UUID team) {
        return stations.getOrDefault(team, Map.of());
    }

    /** The stations that add a mission slot: swarms at stars do not. */
    public int stationCount(UUID team) {
        int count = 0;
        for (ResourceLocation body : stations.getOrDefault(team, Map.of()).keySet()) {
            if (!Swarms.isSwarm(ContinuumData.body(body))) count++;
        }
        return count;
    }

    public java.util.Set<UUID> teamsWithStations() {
        return new java.util.HashSet<>(stations.keySet());
    }

    public void setStationLevel(UUID team, ResourceLocation body, int level) {
        if (level <= 0) {
            Map<ResourceLocation, Integer> mine = stations.get(team);
            if (mine != null) mine.remove(body);
        } else {
            stations.computeIfAbsent(team, t -> new HashMap<>()).put(body, Math.min(Swarms.maxLevel(ContinuumData.body(body)), level));
        }
        setDirty();
    }

    public int depth(UUID team, ResourceLocation body) {
        return depths.getOrDefault(team, Map.of()).getOrDefault(body, 0);
    }

    public boolean revealDeposit(UUID team, ResourceLocation body, int total) {
        int current = depth(team, body);
        if (current >= total) return false;
        depths.computeIfAbsent(team, t -> new HashMap<>()).put(body, current + 1);
        setDirty();
        return true;
    }

    public void setDepth(UUID team, ResourceLocation body, int depth) {
        depths.computeIfAbsent(team, t -> new HashMap<>()).put(body, Math.max(0, depth));
        setDirty();
    }

    public List<Mission> missions(UUID team) {
        return missions.computeIfAbsent(team, t -> new ArrayList<>());
    }

    public Collection<UUID> teamsWithMissions() {
        return new ArrayList<>(missions.keySet());
    }

    public void addMission(Mission mission) {
        missions(mission.team).add(mission);
        setDirty();
    }

    public void removeMission(UUID team, UUID missionId) {
        missions(team).removeIf(m -> m.id.equals(missionId));
        setDirty();
    }

    public Optional<Mission> mission(UUID team, UUID missionId) {
        return missions(team).stream().filter(m -> m.id.equals(missionId)).findFirst();
    }

    public Map<ResourceLocation, Outpost> outposts(UUID team) {
        return outposts.computeIfAbsent(team, t -> new HashMap<>());
    }

    public @Nullable Outpost outpost(UUID team, ResourceLocation body) {
        Map<ResourceLocation, Outpost> map = outposts.get(team);
        return map == null ? null : map.get(body);
    }

    public Outpost deployProbes(UUID team, ResourceLocation body, int probes, long now) {
        Outpost outpost = outposts(team).get(body);
        if (outpost == null) {
            outpost = new Outpost(probes, 0, now);
            outposts(team).put(body, outpost);
        } else {
            outpost.addProbes(probes);
        }
        setDirty();
        return outpost;
    }

    public void clearOutposts(UUID team) {
        outposts.remove(team);
        setDirty();
    }

    public java.util.Set<UUID> settleOutposts(MinecraftServer server, long now) {
        long cycleMillis = RocketStats.outpostCycleMillis();
        int maxReady = RocketStats.outpostMaxReady();
        java.util.Set<UUID> newlyDamaged = new java.util.HashSet<>();

        for (var team : outposts.entrySet()) {
            Outpost.CyclePayer payer = OutpostPower.payerFor(server, team.getKey());
            for (var entry : team.getValue().entrySet()) {
                if (!entry.getValue().settle(now, cycleMillis, maxReady, payer)) continue;

                newlyDamaged.add(team.getKey());
                ContinuumBody body = ContinuumData.body(entry.getKey());
                String name = body != null ? body.name() : entry.getKey().getPath();
                ContinuumMissions.notifyTeam(server, team.getKey(), net.minecraft.network.chat.Component
                        .literal("The outpost on " + name + " has been damaged and stopped producing. " +
                                "Fly a repair run to fix it.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
        setDirty();
        return newlyDamaged;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag engagedTeams = new ListTag();
        for (UUID team : engaged) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Team", team);
            engagedTeams.add(e);
        }
        tag.put("Engaged", engagedTeams);

        ListTag stageTeams = new ListTag();
        for (var team : stages.entrySet()) {
            CompoundTag teamTag = new CompoundTag();
            teamTag.putUUID("Team", team.getKey());
            ListTag list = new ListTag();
            for (var entry : team.getValue().entrySet()) {
                CompoundTag e = new CompoundTag();
                e.putString("Id", entry.getKey().toString());
                e.putByte("Stage", (byte) entry.getValue().ordinal());
                list.add(e);
            }
            teamTag.put("Stages", list);
            stageTeams.add(teamTag);
        }
        tag.put("StageTeams", stageTeams);

        ListTag missionList = new ListTag();
        for (List<Mission> list : missions.values()) {
            for (Mission mission : list) missionList.add(mission.save());
        }
        tag.put("Missions", missionList);

        ListTag outpostTeams = new ListTag();
        for (var team : outposts.entrySet()) {
            CompoundTag teamTag = new CompoundTag();
            teamTag.putUUID("Team", team.getKey());
            ListTag list = new ListTag();
            for (var entry : team.getValue().entrySet()) {
                CompoundTag e = entry.getValue().save();
                e.putString("Body", entry.getKey().toString());
                list.add(e);
            }
            teamTag.put("Outposts", list);
            outpostTeams.add(teamTag);
        }
        tag.put("OutpostTeams", outpostTeams);

        ListTag depthTeams = new ListTag();
        for (var team : depths.entrySet()) {
            CompoundTag teamTag = new CompoundTag();
            teamTag.putUUID("Team", team.getKey());
            ListTag list = new ListTag();
            for (var entry : team.getValue().entrySet()) {
                CompoundTag e = new CompoundTag();
                e.putString("Body", entry.getKey().toString());
                e.putInt("Depth", entry.getValue());
                list.add(e);
            }
            teamTag.put("Depths", list);
            depthTeams.add(teamTag);
        }
        tag.put("DepthTeams", depthTeams);

        ListTag anomalyTeams = new ListTag();
        for (var team : anomalies.entrySet()) {
            CompoundTag teamTag = new CompoundTag();
            teamTag.putUUID("Team", team.getKey());
            ListTag list = new ListTag();
            for (String key : team.getValue()) list.add(net.minecraft.nbt.StringTag.valueOf(key));
            teamTag.put("Found", list);
            anomalyTeams.add(teamTag);
        }
        tag.put("AnomalyTeams", anomalyTeams);

        ListTag stationTeams = new ListTag();
        for (var team : stations.entrySet()) {
            CompoundTag teamTag = new CompoundTag();
            teamTag.putUUID("Team", team.getKey());
            ListTag list = new ListTag();
            for (var entry : team.getValue().entrySet()) {
                CompoundTag e = new CompoundTag();
                e.putString("Body", entry.getKey().toString());
                e.putInt("Level", entry.getValue());
                list.add(e);
            }
            teamTag.put("Stations", list);
            stationTeams.add(teamTag);
        }
        tag.put("StationTeams", stationTeams);

        ListTag historyTeams = new ListTag();
        for (var team : history.entrySet()) {
            CompoundTag teamTag = new CompoundTag();
            teamTag.putUUID("Team", team.getKey());
            ListTag list = new ListTag();
            for (MissionLog.Entry entry : team.getValue()) list.add(entry.save());
            teamTag.put("Entries", list);
            historyTeams.add(teamTag);
        }
        tag.put("HistoryTeams", historyTeams);

        ListTag overrideTeams = new ListTag();
        for (var team : visibilityOverrides.entrySet()) {
            CompoundTag teamTag = new CompoundTag();
            teamTag.putUUID("Team", team.getKey());
            ListTag list = new ListTag();
            for (var entry : team.getValue().entrySet()) {
                CompoundTag e = new CompoundTag();
                e.putString("Id", entry.getKey().toString());
                e.putBoolean("Visible", entry.getValue());
                list.add(e);
            }
            teamTag.put("Entries", list);
            overrideTeams.add(teamTag);
        }
        tag.put("VisibilityOverrides", overrideTeams);
        return tag;
    }

    public static ContinuumTeamData load(CompoundTag tag) {
        ContinuumTeamData data = new ContinuumTeamData();

        for (Tag t : tag.getList("Engaged", Tag.TAG_COMPOUND)) data.engaged.add(((CompoundTag) t).getUUID("Team"));
        for (Tag t : tag.getList("StageTeams", Tag.TAG_COMPOUND)) {
            CompoundTag teamTag = (CompoundTag) t;
            Map<ResourceLocation, DiscoveryStage> map = new HashMap<>();
            for (Tag s : teamTag.getList("Stages", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) s;
                int ordinal = Math.max(0, Math.min(e.getByte("Stage"), DiscoveryStage.values().length - 1));
                map.put(ResourceLocation.parse(e.getString("Id")), DiscoveryStage.values()[ordinal]);
            }
            data.stages.put(teamTag.getUUID("Team"), map);
        }

        for (Tag t : tag.getList("Missions", Tag.TAG_COMPOUND)) {
            Mission mission = Mission.load((CompoundTag) t);
            data.missions.computeIfAbsent(mission.team, k -> new ArrayList<>()).add(mission);
        }

        for (Tag t : tag.getList("OutpostTeams", Tag.TAG_COMPOUND)) {
            CompoundTag teamTag = (CompoundTag) t;
            Map<ResourceLocation, Outpost> map = new HashMap<>();
            for (Tag o : teamTag.getList("Outposts", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) o;
                map.put(ResourceLocation.parse(e.getString("Body")), Outpost.load(e));
            }
            data.outposts.put(teamTag.getUUID("Team"), map);
        }

        for (Tag t : tag.getList("DepthTeams", Tag.TAG_COMPOUND)) {
            CompoundTag teamTag = (CompoundTag) t;
            Map<ResourceLocation, Integer> map = new HashMap<>();
            for (Tag d : teamTag.getList("Depths", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) d;
                map.put(ResourceLocation.parse(e.getString("Body")), e.getInt("Depth"));
            }
            data.depths.put(teamTag.getUUID("Team"), map);
        }

        for (Tag t : tag.getList("AnomalyTeams", Tag.TAG_COMPOUND)) {
            CompoundTag teamTag = (CompoundTag) t;
            java.util.Set<String> found = new java.util.HashSet<>();
            for (Tag f : teamTag.getList("Found", Tag.TAG_STRING)) found.add(f.getAsString());
            data.anomalies.put(teamTag.getUUID("Team"), found);
        }

        for (Tag t : tag.getList("StationTeams", Tag.TAG_COMPOUND)) {
            CompoundTag teamTag = (CompoundTag) t;
            Map<ResourceLocation, Integer> map = new HashMap<>();
            for (Tag s : teamTag.getList("Stations", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) s;
                map.put(ResourceLocation.parse(e.getString("Body")), e.getInt("Level"));
            }
            data.stations.put(teamTag.getUUID("Team"), map);
        }

        for (Tag t : tag.getList("HistoryTeams", Tag.TAG_COMPOUND)) {
            CompoundTag teamTag = (CompoundTag) t;
            java.util.List<MissionLog.Entry> list = new java.util.ArrayList<>();
            for (Tag e : teamTag.getList("Entries", Tag.TAG_COMPOUND)) list.add(MissionLog.Entry.load((CompoundTag) e));
            data.history.put(teamTag.getUUID("Team"), list);
        }
        for (Tag t : tag.getList("VisibilityOverrides", Tag.TAG_COMPOUND)) {
            CompoundTag teamTag = (CompoundTag) t;
            Map<ResourceLocation, Boolean> map = new HashMap<>();
            for (Tag e : teamTag.getList("Entries", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) e;
                map.put(ResourceLocation.parse(entry.getString("Id")), entry.getBoolean("Visible"));
            }
            data.visibilityOverrides.put(teamTag.getUUID("Team"), map);
        }
        return data;
    }
}
