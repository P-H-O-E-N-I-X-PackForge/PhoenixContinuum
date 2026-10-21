package net.phoenix.continuum.config;

import net.phoenix.continuum.PhoenixContinuum;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(id = PhoenixContinuum.MOD_ID)
public class ContinuumConfigs {

    public static ContinuumConfigs INSTANCE;
    public static ConfigHolder<ContinuumConfigs> CONFIG_HOLDER;

    public static void init() {
        CONFIG_HOLDER = Configuration.registerConfig(ContinuumConfigs.class, ConfigFormats.yaml());
        INSTANCE = CONFIG_HOLDER.getConfigInstance();
    }


        public enum RenderQuality {
            LOW,
            MEDIUM,
            HIGH
        }

        public enum PlanetStyle {
            SPHERE,
            CUBE
        }

        public enum ProgressionMode {
            SHOW_ALL,
            RESEARCH_GATED
        }

        public enum PdimChargeMode {
            EVERY_VISIT,
            FIRST_VISIT
        }

        @Configurable
        @Configurable.Comment({
                "RESEARCH_GATED: the research gates in definitions (detected_research / surveyed_research) decide what is",
                "found. Research is Continuum's own unless another mod provides it; grant it with",
                "/continuumadmin research, the Continuum API or KubeJS. SHOW_ALL: every system and body is on the map from",
                "the start (surveying them is still a mission) and the gates are not required. Rules a pack wrote itself",
                "(\"visibility\") apply either way. Default: RESEARCH_GATED"
        })
        public ProgressionMode progressionMode = ProgressionMode.RESEARCH_GATED;

        @Configurable
        @Configurable.Comment({
                "What going to a personal dimension costs, taken from the player's inventory. The item here and",
                "the count below are the default for every body; a body's definition can override them with a",
                "\"pdim_cost\" entry. Creative players are never charged. Default: minecraft:ender_pearl"
        })
        public String pdimCostItem = "minecraft:ender_pearl";

        @Configurable
        @Configurable.Comment({
                "How many of the cost item one trip costs. 0 makes personal dimensions free. Default: 2" })
        @Configurable.Range(min = 0, max = 64)
        public int pdimCostCount = 2;

        @Configurable
        @Configurable.Comment({
                "EVERY_VISIT charges on every trip there (going back and forth costs each time). FIRST_VISIT charges",
                "only when a dimension is first made. Leaving is always free. Default: EVERY_VISIT"
        })
        public PdimChargeMode pdimChargeMode = PdimChargeMode.EVERY_VISIT;

        @Configurable
        @Configurable.Comment({
                "Visual quality of the Continuum space views (this client only). LOW uses far fewer mesh",
                "triangles, fewer noise layers and a simpler backdrop, for weaker GPUs. Pressing Q on the map",
                "changes it for that session without touching this setting.",
                "Default: MEDIUM"
        })
        public RenderQuality renderQuality = RenderQuality.MEDIUM;

        @Configurable
        @Configurable.Comment({
                "How planets and moons are drawn. SPHERE is the smooth shaded globe. CUBE draws every world as a",
                "blocky cube whose surface is built from chunky square cells (8 / 12 / 16 per face at",
                "LOW / MEDIUM / HIGH), for a more Minecraft look. Stars and black holes stay round.",
                "Pressing C on the map flips it for that session.",
                "Default: SPHERE"
        })
        public PlanetStyle planetStyle = PlanetStyle.SPHERE;

        @Configurable
        @Configurable.Comment({
                "Scales every mission's real-time length. 1.0 = the trip times in the body definitions",
                "(5 minutes for the nearest body up to 2 hours for the farthest). Lower it to test.",
                "Default: 1.0"
        })
        @Configurable.DecimalRange(min = 0.001, max = 100.0)
        public double tripTimeMultiplier = 1.0;

        @Configurable
        @Configurable.Comment({
                "The most trip time rocket upgrades can remove in total (0.5 = up to half).",
                "Default: 0.5"
        })
        @Configurable.DecimalRange(min = 0.0, max = 0.9)
        public double maxTripReduction = 0.5;

        @Configurable
        @Configurable.Comment({
                "Rocket wear (0 = new, 1 = ruined) at or above which a rocket with a Hull Interlock upgrade",
                "refuses to launch. Without the interlock a worn rocket launches anyway and may fail.",
                "Default: 0.7"
        })
        @Configurable.DecimalRange(min = 0.1, max = 1.0)
        public double interlockWearThreshold = 0.7;

        @Configurable
        @Configurable.Comment({ "Scales how much wear each mission puts on the rocket.", "Default: 1.0" })
        @Configurable.DecimalRange(min = 0.0, max = 10.0)
        public double wearMultiplier = 1.0;

        @Configurable
        @Configurable.Comment({ "Missions a team can have in flight (or waiting to be collected) at once.",
                "Default: 8" })
        @Configurable.Range(min = 1, max = 64)
        public int maxMissionsPerTeam = 8;

        @Configurable
        @Configurable.Comment({
                "Real minutes one outpost production cycle takes. Each probe at an outpost rolls the body's",
                "yield table once per cycle.", "Default: 10"
        })
        @Configurable.DecimalRange(min = 0.05, max = 1440.0)
        public double outpostCycleMinutes = 10.0;

        @Configurable
        @Configurable.Comment({
                "How many finished cycles an outpost stockpiles before it stops producing (144 cycles of",
                "10 minutes is a day). A Haul mission empties it.", "Default: 144"
        })
        @Configurable.Range(min = 1, max = 100000)
        public int outpostMaxStoredCycles = 144;

        @Configurable
        @Configurable.Comment({
                "Chance that a successful survey turns up an anomaly: a surface cache of resources, a vein",
                "signature that maps another deposit, or (rarely) ancient ruins with a large cache.",
                "Default: 0.25"
        })
        @Configurable.DecimalRange(min = 0.0, max = 1.0)
        public double surveyAnomalyChance = 0.25;

        @Configurable
        @Configurable.Comment({
                "Chance that a mission has a random event on the way (solar flare, micrometeoroids, a tailwind,",
                "a derelict cache, a signal echo). At most one per mission, revealed when it lands. 0 turns events",
                "off. Default: 0.3"
        })
        @Configurable.DecimalRange(min = 0.0, max = 1.0)
        public double missionEventChance = 0.3;

        @Configurable
        @Configurable.Comment({
                "Chance, per finished production cycle, that something breaks an outpost. A broken outpost",
                "stops producing (and stops costing upkeep) until a repair run fixes it; what it had already",
                "stockpiled is kept. 0 turns incidents off. Default: 0.05 (about once per 3 hours of 10 min cycles)"
        })
        @Configurable.DecimalRange(min = 0.0, max = 1.0)
        public double outpostIncidentChance = 0.05;

        @Configurable
        @Configurable.Comment({
                "Repair kits a repair run uses up (they are taken from the launching player's inventory).",
                "Default: 1"
        })
        @Configurable.Range(min = 1, max = 16)
        public int outpostRepairKits = 1;

        @Configurable
        @Configurable.Comment({
                "Chance that a failed extraction, outpost deployment or haul leaves the rocket stranded and sends a",
                "distress signal instead of bringing it home: it can only be recovered by a rescue run. 0 turns",
                "stranding off. Default: 0.4"
        })
        @Configurable.DecimalRange(min = 0.0, max = 1.0)
        public double distressChance = 0.4;

        @Configurable
        @Configurable.Comment({
                "How long, in real minutes, a distress signal lasts. If no rescue arrives in time the rocket is lost.",
                "Default: 180"
        })
        @Configurable.Range(min = 5, max = 10080)
        public int distressWindowMinutes = 180;

        @Configurable
        @Configurable.Comment({ "Repair kits a rescue run carries (and uses up). Default: 2" })
        @Configurable.Range(min = 1, max = 16)
        public int rescueKits = 2;

        @Configurable
        @Configurable.Comment({
                "Extraction probes a station construction run carries and uses up. Each of a body's three station",
                "levels costs this many. Default: 4"
        })
        @Configurable.Range(min = 1, max = 64)
        public int stationProbesPerLevel = 4;

        @Configurable
        @Configurable.Comment({
                "Most rockets that can be sent in one launch (extraction, deployment and rescue runs). Each rocket",
                "is its own mission with its own rocket, probes and launch energy. Default: 8"
        })
        @Configurable.Range(min = 1, max = 64)
        public int maxRocketsPerLaunch = 8;

        @Configurable
        @Configurable.Comment({
                "Whether a body's tier needs a matching rocket hull to work there: mid tier needs Mk II, late Mk III,",
                "endgame Mk IV (surveys are exempt). Hulls are raised with Hull Refit Kits in the refit screen",
                "(sneak + use a rocket). Default: true"
        })
        public boolean rocketTierGating = true;

        @Configurable
        @Configurable.Comment({
                "Volume of the Continuum map's layered ambience (space bed, wind, stellar roar, black hole hum), 0 to 1.",
                "0 turns it off. Default: 0.6"
        })
        @Configurable.DecimalRange(min = 0.0, max = 1.0)
        public double ambienceVolume = 0.6;

        @Configurable
        @Configurable.Comment({
                "Whether outposts need power. If true, every production cycle costs upkeep drawn from the owning",
                "team's Launch Complex power; a cycle that cannot be paid for is lost, so an unpowered outpost idles.",
                "Default: true"
        })
        public boolean outpostRequiresPower = true;

        @Configurable
        @Configurable.Comment({
                "EU one probe costs per production cycle. With the default 10 minute cycle, 500,000 EU is about",
                "42 EU/t per probe (8 probes ~ 330 EU/t). 0 turns upkeep off. Default: 500000"
        })
        @Configurable.DecimalRange(min = 0.0, max = 1.0E12)
        public double outpostUpkeepEUPerProbe = 500_000.0;

        @Configurable
        @Configurable.Comment({ "The most probes one outpost can hold.", "Default: 8" })
        @Configurable.Range(min = 1, max = 64)
        public int maxProbesPerOutpost = 8;

        @Configurable
        @Configurable.Comment({
                "If true, missions can only be launched from a fully built Continuum Launch Complex. If false the plain",
                "Continuum Launch Pad block also works (it ignores tier and power) - meant for testing and creative.",
                "Default: true"
        })
        public boolean requireMultiblockPad = true;

        @Configurable
        @Configurable.Comment({
                "Energy a launch draws from the Launch Complex's input hatches, in amp-ticks of the pad's own voltage",
                "(20 = 1 amp for a second). A MV pad pays 128 * this in EU. An energy hatch only holds 16 times its",
                "voltage, so a launch never asks for more than 90% of what the pad's hatches can store, whatever this",
                "is set to. Default: 10"
        })
        @Configurable.Range(min = 0, max = 10000000)
        public int launchEnergyAmpTicks = 10;

        @Configurable
        @Configurable.Comment({
                "The pad tier (GT voltage tier: 1 = LV, 2 = MV, 3 = HV, 4 = EV, 5 = IV, 6 = LuV, 7 = ZPM, 8 = UV)",
                "a body needs, by its gate.min_tier. Defaults: start MV, early HV, mid EV, late IV, endgame LuV."
        })
        @Configurable.Range(min = 0, max = 14)
        public int padTierStart = 2;

        @Configurable
        @Configurable.Range(min = 0, max = 14)
        public int padTierEarly = 3;

        @Configurable
        @Configurable.Range(min = 0, max = 14)
        public int padTierMid = 4;

        @Configurable
        @Configurable.Range(min = 0, max = 14)
        public int padTierLate = 5;

        @Configurable
        @Configurable.Range(min = 0, max = 14)
        public int padTierEndgame = 6;

        @Configurable
        @Configurable.Comment({ "The most extraction probes one mission can carry.", "Default: 16" })
        @Configurable.Range(min = 1, max = 64)
        public int maxProbesPerMission = 16;

        @Configurable
        @Configurable.Comment({ "How much wear one Repair Kit removes from a rocket (0.3 = 30%).", "Default: 0.3" })
        @Configurable.DecimalRange(min = 0.01, max = 1.0)
        public double repairKitWear = 0.3;

        @Configurable
        @Configurable.Comment({ "How close to a Launch Pad a player has to be to launch from it, in blocks.",
                "Default: 12" })
        @Configurable.Range(min = 2, max = 64)
        public int launchRangeBlocks = 12;
    }
