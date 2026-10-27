package net.phoenix.continuum.machine;

import net.minecraft.network.chat.Component;

import java.util.List;

public final class SwarmControlShape {

    private SwarmControlShape() {}

    public static final String[][] SLICES = {
            { "BHB", "AAA", "AAA", "BBB" },
            { "HBH", "AFA", "AFA", "BBB" },
            { "BSB", "AAA", "AAA", "BBB" } };

    public static final List<Component> TOOLTIPS = List.of(
            Component.literal("\u00a76The control array of your team's Dyson swarms and shells."),
            Component.literal("\u00a77While it runs, it projects a hologram of the star or black hole it is aimed at,"),
            Component.literal("\u00a77built up round it as far as your team has built."),
            Component.literal("\u00a77Use it to pick the target and send pods and segments from a launch complex near it."),
            Component.literal("\u00a77It is a high-energy link to the real thing: it needs power and coolant (see its"),
            Component.literal("\u00a77recipe) to stay online, and the hologram collapses when it stops."));
}
