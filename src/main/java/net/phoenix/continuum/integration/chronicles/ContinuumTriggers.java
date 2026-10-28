package net.phoenix.continuum.integration.chronicles;

/** The trigger ids Continuum fires for Phoenix Chronicles (see {@link ChroniclesBridge}). */
public final class ContinuumTriggers {

    private ContinuumTriggers() {}

    public static final String DISCOVERY_DETECTED = "phoenix_continuum:discovery/detected";
    public static final String DISCOVERY_SURVEYED = "phoenix_continuum:discovery/surveyed";

    public static final String MISSION_LAUNCHED = "phoenix_continuum:mission/launched";
    public static final String MISSION_SUCCEEDED = "phoenix_continuum:mission/succeeded";
    public static final String MISSION_FAILED = "phoenix_continuum:mission/failed";

    public static final String ANOMALY_INVESTIGATED = "phoenix_continuum:anomaly/investigated";

    public static final String PDIM_ENTERED = "phoenix_continuum:pdim/entered";
    public static final String PDIM_CREATED = "phoenix_continuum:pdim/created";
    public static final String PDIM_LEFT = "phoenix_continuum:pdim/left";
}
