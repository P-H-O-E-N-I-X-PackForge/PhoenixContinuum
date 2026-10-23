package net.phoenix.continuum.common.visibility;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

@FunctionalInterface
public interface ContinuumCondition {

    record Context(MinecraftServer server, UUID team) {}

    boolean test(Context context);
}
