package net.phoenix.continuum.machine;

import net.minecraft.network.chat.Component;

import java.util.List;

public final class SwarmReceiverShape {

    private SwarmReceiverShape() {}

    public static final String[][] SLICES = {
            { "BHB", "AAA", "AAA", "BBB" },
            { "HBH", "AFA", "AFA", "BBB" },
            { "BSB", "AAA", "AAA", "BBB" } };

    public static final List<Component> TOOLTIPS = List.of(
            Component.literal("\u00a76Receives the power of your team's Dyson swarms."),
            Component.literal("\u00a77Build an orbital station at a star to start a swarm."),
            Component.literal("\u00a77The swarm's power is delivered into its energy output hatches, which pass it on"),
            Component.literal("\u00a77like any generator; a stronger hatch can pass on more."),
            Component.literal("\u00a77Power nobody takes is lost, so build enough receivers for the swarm."));
}
