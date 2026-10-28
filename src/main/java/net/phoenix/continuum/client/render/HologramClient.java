package net.phoenix.continuum.client.render;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.phoenix.continuum.network.S2CHologramPacket;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each Swarm Control hologram near the player is doing. Each one has a boot value, 0 (off) to 1 (fully up),
 * that rises while it is online and falls, faster, when it is not: the renderer builds the hologram from it piece
 * by piece going up, and takes it apart again going down.
 */
public final class HologramClient {

    private HologramClient() {}

    public static final float BOOT_SECONDS = 8.0f;
    private static final float COLLAPSE_SECONDS = 2.0f;
    private static final long STALE_MILLIS = 15_000L;

    public static final class Holo {

        public S2CHologramPacket.State state;
        public float boot;
        public long lastSeen = Util.getMillis();
        private long lastStep = Util.getMillis();

        Holo(S2CHologramPacket.State state) {
            this.state = state;
        }

        public void step() {
            long now = Util.getMillis();
            float dt = Math.min(0.25f, (now - lastStep) / 1000.0f);
            lastStep = now;
            boolean up = state.active() && now - lastSeen < STALE_MILLIS;
            boot = up ? Math.min(1.0f, boot + dt / BOOT_SECONDS) : Math.max(0.0f, boot - dt / COLLAPSE_SECONDS);
        }
    }

    private static final Map<BlockPos, Holo> HOLOS = new ConcurrentHashMap<>();

    public static void accept(S2CHologramPacket.State state) {
        Holo holo = HOLOS.get(state.pos());
        if (holo == null) {
            holo = new Holo(state);
            HOLOS.put(state.pos(), holo);
        }
        holo.state = state;
        holo.lastSeen = Util.getMillis();
    }

    public static Iterable<Holo> all() {
        return HOLOS.values();
    }

    /** Forget the ones that have finished falling apart. */
    public static void prune() {
        Iterator<Holo> it = HOLOS.values().iterator();
        while (it.hasNext()) {
            Holo holo = it.next();
            if (holo.boot <= 0.0f && (!holo.state.active() || Util.getMillis() - holo.lastSeen > STALE_MILLIS)) {
                it.remove();
            }
        }
    }

    public static void clear() {
        HOLOS.clear();
    }
}
