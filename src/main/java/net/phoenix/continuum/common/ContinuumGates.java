package net.phoenix.continuum.common;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.wiki.api.gate.SuiteGates;

/**
 * What a player may do with Continuum, as features of the shared gate API ({@link SuiteGates}): another mod binds them to
 * research or anything else, e.g. {@code SuiteGates.bind(ContinuumGates.SWARMS, "*", "conflux:node/foo")}. A feature that
 * nothing is bound to is allowed. Continuum has no per-dimension features, so the scope is {@code *}.
 */
public final class ContinuumGates {

    /** Opening the map at a launch pad or complex. */
    public static final String MAP = "continuum:map";
    /** Launching, scheduling and collecting missions. */
    public static final String MISSIONS = "continuum:missions";
    /** Creating, refitting, tuning and controlling swarms. */
    public static final String SWARMS = "continuum:swarms";
    /** Travelling to a pocket dimension or a Conflux dimension from the map. */
    public static final String TRAVEL = "continuum:travel";
    /** Investigating anomalies. */
    public static final String ANOMALIES = "continuum:anomalies";

    private ContinuumGates() {}

    public static boolean allowed(ServerPlayer player, String feature) {
        return SuiteGates.allowed(player, feature, SuiteGates.ANY_SCOPE);
    }

    /** Like {@link #allowed}, and tells the player when the answer is no. */
    public static boolean check(ServerPlayer player, String feature) {
        if (allowed(player, feature)) return true;
        player.displayClientMessage(Component.literal("Your team has not researched this yet."), true);
        return false;
    }
}
