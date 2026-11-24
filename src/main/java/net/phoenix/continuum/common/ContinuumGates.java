package net.phoenix.continuum.common;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.wiki.api.gate.SuiteGates;

public final class ContinuumGates {

    public static final String MAP = "continuum:map";
    
    public static final String MISSIONS = "continuum:missions";
    
    public static final String SWARMS = "continuum:swarms";
    
    public static final String TRAVEL = "continuum:travel";
    
    public static final String ANOMALIES = "continuum:anomalies";

    /** Mission kinds gated one by one, and, through a binding's scope, one body or one system at a time. */
    public static final String SURVEY = "continuum:survey";

    public static final String EXTRACT = "continuum:extract";

    public static final String OUTPOST = "continuum:outpost";

    public static final String STATION = "continuum:station";

    public static final String SHELL = "continuum:shell";

    public static final String RESCUE = "continuum:rescue";

    private ContinuumGates() {}

    public static String featureOf(net.phoenix.continuum.common.Mission.Type type) {
        return switch (type) {
            case SURVEY -> SURVEY;
            case EXTRACT -> EXTRACT;
            case DEPLOY, HAUL, REPAIR -> OUTPOST;
            case STATION -> STATION;
            case SHELL -> SHELL;
            case RESCUE -> RESCUE;
        };
    }

    /**
     * The refusal for flying this kind of mission to this body, or null if it is allowed. Two things must hold: the
     * bindings with scope {@code *} (every body), and then, if any bindings name this body or its system as their
     * scope, one of those. So a body can be locked on top of the general rule, never opened past it.
     */
    public static net.minecraft.network.chat.Component missionDenial(ServerPlayer player, String feature,
                                                                     String bodyId, String systemId) {
        if (!SuiteGates.allowed(player, feature, SuiteGates.ANY_SCOPE)) {
            return SuiteGates.denial(feature, SuiteGates.ANY_SCOPE);
        }
        for (String scope : new String[] { bodyId, systemId }) {
            if (scope == null || scope.equals(SuiteGates.ANY_SCOPE)) continue;
            boolean bound = false;
            for (SuiteGates.Binding binding : SuiteGates.bindings()) {
                if (!binding.feature().equals(feature) || !binding.scope().equals(scope)) continue;
                bound = true;
                for (String expression : binding.anyOf()) if (SuiteGates.isOpen(player, expression)) return null;
            }
            if (bound) return SuiteGates.denial(feature, scope);
        }
        return null;
    }

    public static boolean allowed(ServerPlayer player, String feature) {
        return SuiteGates.allowed(player, feature, SuiteGates.ANY_SCOPE);
    }

    public static boolean check(ServerPlayer player, String feature) {
        if (allowed(player, feature)) return true;
        player.displayClientMessage(SuiteGates.denial(feature, SuiteGates.ANY_SCOPE), false);
        return false;
    }
}
