package net.phoenix.continuum.machine;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class SwarmReceiverShape {

    private SwarmReceiverShape() {}

    public static final String[][] SLICES = {
            { "BHB", "AAA", "AAA", "BBB" },
            { "HBH", "AFA", "AFA", "BBB" },
            { "BSB", "AAA", "AAA", "BBB" } };

    public static final List<Component> TOOLTIPS = List.of(
            ContinuumLang.tr("phoenix_continuum.swarm_receiver_shape.receives_the_power_of_your"),
            ContinuumLang.tr("phoenix_continuum.swarm_receiver_shape.build_an_orbital_station_at"),
            ContinuumLang.tr("phoenix_continuum.swarm_receiver_shape.the_swarm_s_power_is"),
            ContinuumLang.tr("phoenix_continuum.swarm_receiver_shape.like_any_generator_a_stronger"),
            ContinuumLang.tr("phoenix_continuum.swarm_receiver_shape.power_nobody_takes_is_lost"));
}
