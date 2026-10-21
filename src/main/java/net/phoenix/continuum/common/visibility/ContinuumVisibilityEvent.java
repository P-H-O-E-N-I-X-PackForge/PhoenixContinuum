package net.phoenix.continuum.common.visibility;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Posted on the game bus after a body or system rules and per-team overrides have been applied, so code (Java mods,
 * KubeJS) has the final say in both directions: call {@link #setResult(boolean)}.
 */
public class ContinuumVisibilityEvent extends Event {

    public enum Kind {
        /** Whether the body or system appears at all. */
        VISIBLE,
        /** Whether missions can be sent there. */
        ACCESSIBLE
    }

    private final MinecraftServer server;
    private final UUID team;
    private final ResourceLocation subject;
    private final Kind kind;
    private boolean result;

    public ContinuumVisibilityEvent(MinecraftServer server, UUID team, ResourceLocation subject, Kind kind,
                                    boolean result) {
        this.server = server;
        this.team = team;
        this.subject = subject;
        this.kind = kind;
        this.result = result;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public UUID getTeam() {
        return team;
    }

    /** The body id (or, for a system rule, the system id). */
    public ResourceLocation getSubject() {
        return subject;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean getVisible() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }
}
