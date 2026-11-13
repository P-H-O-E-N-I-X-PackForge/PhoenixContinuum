package net.phoenix.continuum.machine;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class SwarmControlShape {

    private SwarmControlShape() {}

    public static final String[][] SLICES = {
            { "BHB", "AAA", "AAA", "BBB" },
            { "HBH", "AFA", "AFA", "BBB" },
            { "BSB", "AAA", "AAA", "BBB" } };

    public static final List<Component> TOOLTIPS = List.of(
            ContinuumLang.tr("phoenix_continuum.swarm_control_shape.the_control_array_of_your"),
            ContinuumLang.tr("phoenix_continuum.swarm_control_shape.while_it_runs_it_projects"),
            ContinuumLang.tr("phoenix_continuum.swarm_control_shape.built_up_round_it_as"),
            ContinuumLang.tr("phoenix_continuum.swarm_control_shape.use_it_to_pick_the"),
            ContinuumLang.tr("phoenix_continuum.swarm_control_shape.it_is_a_high_energy"),
            ContinuumLang.tr("phoenix_continuum.swarm_control_shape.recipe_to_stay_online_and"));
}
