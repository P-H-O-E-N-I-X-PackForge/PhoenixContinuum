package net.phoenix.continuum.common;

import net.minecraft.server.MinecraftServer;

/** A block that takes in the power of a team's swarms: the plain Swarm Receiver or the GregTech array. */
public interface SwarmReceiver {

    SwarmReport report(MinecraftServer server);

    /** Energy held, and the most it can hold, for how full it looks. */
    long stored();

    long capacity();
}
