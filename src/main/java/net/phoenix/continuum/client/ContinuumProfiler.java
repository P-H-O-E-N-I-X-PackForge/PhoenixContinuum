package net.phoenix.continuum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import org.lwjgl.opengl.GL33C;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small profiler for Continuum's drawing, so a heavy shader shows up as a number instead of a guess.
 *
 * <p>Every instrumented draw is a {@link #scope scope}. While profiling is off a scope costs one boolean check. While it is
 * on, each scope is timed on the CPU (how long the game thread spent issuing it) and on the GPU with a timer query (how long
 * the card spent drawing it). The GPU numbers are the ones that matter for shaders. Scopes nest, and a nested scope takes its
 * own time away from the one around it, so every figure is time spent in that pass alone.
 *
 * <p>GPU results arrive a few frames late (the card is still busy when the CPU asks), so they are read back on later
 * frames. Figures are smoothed over roughly the last second.
 *
 * <p>Use it with {@code /continuum profile start}, look at the numbers on screen or with {@code /continuum profile report},
 * and {@code /continuum profile stop}.
 */
public final class ContinuumProfiler {

    /** What a scope is; closing it ends the timing. */
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }

    private static final Scope NOOP = () -> {};

    private static final int MAX_PENDING = 512;
    private static final double SMOOTH = 0.08;

    private static boolean enabled;
    private static boolean gpuSupported = true;

    private static final class Stat {

        double cpuMs;
        double gpuMs;
        double calls;
        double gpuMax;
        // this frame
        long cpuNanos;
        int callCount;
    }

    private static final Map<String, Stat> STATS = new HashMap<>();

    private record Frame(String name, long start) {}

    private record Pending(String name, int query) {}

    private static final Deque<Frame> STACK = new ArrayDeque<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Deque<Integer> FREE = new ArrayDeque<>();
    private static int activeQuery = -1;
    private static String activeName;

    private ContinuumProfiler() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void start() {
        STATS.clear();
        STACK.clear();
        enabled = true;
    }

    public static void stop() {
        enabled = false;
        STACK.clear();
        endActive();
    }

    public static void reset() {
        STATS.clear();
    }

    /** Times a section of drawing. Always use it in a try-with-resources. */
    public static Scope scope(String name) {
        if (!enabled) return NOOP;

        // hand the card's timer to this scope: whatever was running (the scope around it) stops being charged now
        endActive();
        STACK.push(new Frame(name, System.nanoTime()));
        beginQuery(name);
        return ContinuumProfiler::finish;
    }

    private static void finish() {
        if (STACK.isEmpty()) return;
        Frame frame = STACK.pop();
        long nanos = System.nanoTime() - frame.start();
        endActive();

        Stat stat = STATS.computeIfAbsent(frame.name(), n -> new Stat());
        stat.cpuNanos += nanos;
        stat.callCount++;

        // the scope around this one carries on, on a fresh query
        Frame parent = STACK.peek();
        if (parent != null) beginQuery(parent.name());
    }

    private static void beginQuery(String name) {
        if (!gpuSupported) return;
        try {
            int id = FREE.isEmpty() ? GL33C.glGenQueries() : FREE.pop();
            GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, id);
            activeQuery = id;
            activeName = name;
        } catch (RuntimeException | LinkageError e) {
            gpuSupported = false;
            activeQuery = -1;
        }
    }

    private static void endActive() {
        if (activeQuery < 0) return;
        try {
            GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        } catch (RuntimeException | LinkageError e) {
            gpuSupported = false;
        }
        if (PENDING.size() < MAX_PENDING) PENDING.add(new Pending(activeName, activeQuery));
        else FREE.push(activeQuery);
        activeQuery = -1;
        activeName = null;
    }

    /** Call once per rendered frame: reads back finished GPU timings and folds this frame's numbers into the averages. */
    public static void endFrame() {
        if (!enabled) return;
        endActive();

        Map<String, Double> gpuThisPoll = new HashMap<>();
        if (gpuSupported) {
            try {
                for (int i = PENDING.size() - 1; i >= 0; i--) {
                    Pending p = PENDING.get(i);
                    if (GL33C.glGetQueryObjecti(p.query(), GL33C.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
                    long ns = GL33C.glGetQueryObjectui64(p.query(), GL33C.GL_QUERY_RESULT);
                    gpuThisPoll.merge(p.name(), ns / 1.0e6, Double::sum);
                    FREE.push(p.query());
                    PENDING.remove(i);
                }
            } catch (RuntimeException | LinkageError e) {
                gpuSupported = false;
            }
        }

        for (var entry : STATS.entrySet()) {
            Stat s = entry.getValue();
            double cpu = s.cpuNanos / 1.0e6;
            double gpu = gpuThisPoll.getOrDefault(entry.getKey(), 0.0);
            s.cpuMs += (cpu - s.cpuMs) * SMOOTH;
            s.gpuMs += (gpu - s.gpuMs) * SMOOTH;
            s.calls += (s.callCount - s.calls) * SMOOTH;
            s.gpuMax = Math.max(s.gpuMax * 0.995, gpu);
            s.cpuNanos = 0;
            s.callCount = 0;
        }
    }

    private static List<Map.Entry<String, Stat>> sorted() {
        List<Map.Entry<String, Stat>> list = new ArrayList<>(STATS.entrySet());
        list.sort(Comparator.comparingDouble((Map.Entry<String, Stat> e) -> e.getValue().gpuMs +
                e.getValue().cpuMs * 0.25).reversed());
        return list;
    }

    private static String line(String name, Stat s) {
        return String.format(Locale.ROOT, "%-24s gpu %6.2f ms  cpu %6.2f ms  x%.1f  peak %5.2f", name, s.gpuMs, s.cpuMs,
                s.calls, s.gpuMax);
    }

    /** Lines for chat or the log, heaviest first. */
    public static List<String> report() {
        List<String> out = new ArrayList<>();
        if (STATS.isEmpty()) {
            out.add(enabled ? "Nothing drawn yet. Open the map or a personal dimension and try again." :
                    "Profiling is off. Use /continuum profile start.");
            return out;
        }
        double totalGpu = 0.0;
        double totalCpu = 0.0;
        for (Stat s : STATS.values()) {
            totalGpu += s.gpuMs;
            totalCpu += s.cpuMs;
        }
        int fps = Minecraft.getInstance().getFps();
        out.add(String.format(Locale.ROOT, "Continuum draw: gpu %.2f ms, cpu %.2f ms per frame (%d fps%s)", totalGpu,
                totalCpu, fps, gpuSupported ? "" : ", no GPU timer on this card"));
        for (var e : sorted()) out.add(line(e.getKey(), e.getValue()));
        return out;
    }

    /** A compact list in the corner of the screen while profiling. */
    public static void drawOverlay(GuiGraphics g) {
        if (!enabled || STATS.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        int y = 4;
        for (String text : report().stream().limit(10).toList()) {
            g.drawString(font, text, 4, y, 0xFFFFE060, true);
            y += 10;
        }
    }
}
