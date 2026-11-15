package net.phoenix.continuum.common;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.wiki.api.gate.SuiteGates;

public final class ContinuumGates {

    public static final String MAP = "continuum:map";
    
    public static final String MISSIONS = "continuum:missions";
    
    public static final String SWARMS = "continuum:swarms";
    
    public static final String TRAVEL = "continuum:travel";
    
    public static final String ANOMALIES = "continuum:anomalies";

    private ContinuumGates() {}

    public static boolean allowed(ServerPlayer player, String feature) {
        return SuiteGates.allowed(player, feature, SuiteGates.ANY_SCOPE);
    }

    public static boolean check(ServerPlayer player, String feature) {
        if (allowed(player, feature)) return true;
        player.displayClientMessage(SuiteGates.denial(feature, SuiteGates.ANY_SCOPE), false);
        return false;
    }
}
