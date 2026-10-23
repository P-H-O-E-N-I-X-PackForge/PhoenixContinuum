package net.phoenix.continuum.common.visibility;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

public class ContinuumVisibilityEvent extends Event {

    public enum Kind {
        
        VISIBLE,
        
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
