package net.phoenix.continuum.common.visibility;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** A yes/no question about a team, used by visibility and accessibility rules. */
@FunctionalInterface
public interface ContinuumCondition {

    /** Who is asking. */
    record Context(MinecraftServer server, UUID team) {}

    boolean test(Context context);
}
