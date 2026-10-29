package net.phoenix.continuum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.phoenix.continuum.PhoenixContinuum;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * A log of what Continuum's screens do, for finding out why a transition sometimes flickers and shows the world behind
 * it. Off by default; turn it on with {@code /continuum screenlog on} (or start the game with
 * {@code -Dcontinuum.screenlog=true}) and the log, {@code latest.log}, lines starting {@code [screens]}, has:
 * <ul>
 * <li>every screen opened or closed, from what to what, and who asked (the first calls from this mod and its
 * neighbours): a Continuum screen replaced by nothing is the world showing;</li>
 * <li>the map's transitions (zoom out, the switch, zoom in) and when its scene picture is made again;</li>
 * <li>an <b>anomaly</b> line whenever a frame was drawn that could show the world, or went missing, followed by the
 * last frames the screen drew, so the cause is next to the symptom.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class ContinuumScreenLog {

    private static final int RING = 80;
    private static final long DUMP_EVERY_MS = 750L;

    private static volatile boolean enabled = Boolean.getBoolean("continuum.screenlog");
    private static final ArrayDeque<String> FRAMES = new ArrayDeque<>();
    private static long frameNumber;
    private static long lastDump;
    private static long lastContinuumFrameAt;
    private static String lastContinuumScreen = "";
    private static long leftAt;
    private static String leftFor = "";

    private ContinuumScreenLog() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        if (on) {
            synchronized (FRAMES) {
                FRAMES.clear();
            }
        }
        PhoenixContinuum.LOGGER.info("[screens] logging {}", on ? "on" : "off");
    }

    public static boolean isContinuum(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("net.phoenix.continuum.");
    }

    /** One line, when logging is on. */
    public static void event(String format, Object... args) {
        if (!enabled) return;
        PhoenixContinuum.LOGGER.info("[screens] #{} {}", frameNumber, String.format(Locale.ROOT, format, args));
    }

    /** What a screen drew this frame (kept, not printed, until an anomaly asks for the recent ones). */
    public static void frame(Screen screen, String detail) {
        if (!enabled) return;
        frameNumber++;
        long now = System.currentTimeMillis();
        long gap = lastContinuumFrameAt == 0L ? 0L : now - lastContinuumFrameAt;
        lastContinuumFrameAt = now;
        lastContinuumScreen = screen.getClass().getSimpleName();
        String line = String.format(Locale.ROOT, "frame %d %s +%dms %s", frameNumber, lastContinuumScreen, gap, detail);
        synchronized (FRAMES) {
            if (FRAMES.size() >= RING) FRAMES.removeFirst();
            FRAMES.addLast(line);
        }
        // a frame that came a long time after the last one: the world, or a different screen, was drawn in between
        if (gap > 120L && gap < 5000L) anomaly("frame gap of " + gap + " ms (a frame, or more, was drawn without this screen)");
    }

    /** Something that could show the world or a blank frame: say what, then the frames just before it. */
    public static void anomaly(String why) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        PhoenixContinuum.LOGGER.warn("[screens] #{} ANOMALY: {}", frameNumber, why);
        if (now - lastDump < DUMP_EVERY_MS) return;
        lastDump = now;
        synchronized (FRAMES) {
            int skip = Math.max(0, FRAMES.size() - 12);
            int i = 0;
            for (String line : FRAMES) {
                if (i++ >= skip) PhoenixContinuum.LOGGER.warn("[screens]     {}", line);
            }
        }
    }

    /** A screen was opened (or none: the world) in place of another. */
    public static void opening(Screen from, Screen to) {
        if (!enabled) return;
        boolean fromOurs = isContinuum(from);
        boolean toOurs = isContinuum(to);
        if (!fromOurs && !toOurs) return;
        event("screen %s -> %s%s", name(from), name(to), who());
        if (fromOurs && !toOurs) {
            leftAt = System.currentTimeMillis();
            leftFor = name(to);
            if (to == null) anomaly("a Continuum screen was closed with nothing after it: the world shows (" + name(from) + ")");
            else anomaly("a Continuum screen (" + name(from) + ") was replaced by " + name(to) + ", which is not ours");
        } else if (toOurs && Minecraft.getInstance().screen == null) {
            event("a Continuum screen opened over the world (first frame may show it)");
        }
    }

    /** Every game frame: a frame with no screen just after leaving a Continuum one is the flicker. */
    public static void gameFrame() {
        if (!enabled || leftAt == 0L) return;
        long since = System.currentTimeMillis() - leftAt;
        if (since > 600L) {
            leftAt = 0L;
            return;
        }
        Screen current = Minecraft.getInstance().screen;
        if (current == null) {
            PhoenixContinuum.LOGGER.warn("[screens] #{} world frame {} ms after leaving {} (for {})", frameNumber, since,
                    lastContinuumScreen, leftFor);
        }
    }

    private static String name(Screen s) {
        return s == null ? "(none)" : s.getClass().getSimpleName();
    }

    /** The first few callers from this mod or its neighbours, so the one that asked for the change is named. */
    private static String who() {
        StringBuilder sb = new StringBuilder(" <- ");
        int shown = 0;
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String c = e.getClassName();
            if (c.startsWith("net.phoenix") || c.startsWith("net.minecraft.client.Minecraft")) {
                if (c.contains("ContinuumScreenLog")) continue;
                if (shown > 0) sb.append(" < ");
                sb.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(e.getMethodName()).append(':')
                        .append(e.getLineNumber());
                if (++shown >= 4) break;
            }
        }
        return shown == 0 ? "" : sb.toString();
    }
}
