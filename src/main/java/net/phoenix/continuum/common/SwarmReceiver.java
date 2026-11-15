package net.phoenix.continuum.common;

import net.minecraft.server.MinecraftServer;

public interface SwarmReceiver {

    SwarmReport report(MinecraftServer server);

    long stored();

    long capacity();
}
