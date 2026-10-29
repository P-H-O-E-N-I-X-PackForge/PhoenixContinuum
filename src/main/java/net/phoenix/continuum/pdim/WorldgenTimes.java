package net.phoenix.continuum.pdim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * How long chunks take to generate, kept per kind of work and per dimension so that a personal dimension and, say, a
 * Conflux one can be compared: {@code /continuumadmin worldgentimes} prints it, {@code reset} starts again.
 * <ul>
 * <li><b>noise</b>: the terrain pass of a noise generator, from asking for it to its being done (so it includes waiting
 * for a worker); keyed by the noise settings it uses;</li>
 * <li><b>decoration</b>: trees, ores (GregTech's veins included), lakes and the rest of the features of a chunk, keyed
 * by the dimension. This is where most of the time goes for a world with a lot of ore.</li>
 * </ul>
 */
public final class WorldgenTimes {

    private static final class Stat {

        final LongAdder count = new LongAdder();
        final LongAdder nanos = new LongAdder();
        final AtomicLong max = new AtomicLong();
    }

    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();
    private static volatile long since = System.currentTimeMillis();

    private WorldgenTimes() {}

    public static void record(String kind, String key, long nanos) {
        Stat stat = STATS.computeIfAbsent(kind + " " + key, k -> new Stat());
        stat.count.increment();
        stat.nanos.add(nanos);
        stat.max.accumulateAndGet(nanos, Math::max);
    }

    public static void reset() {
        STATS.clear();
        since = System.currentTimeMillis();
    }

    /** Lines for the chat: the slowest kinds first. */
    public static List<String> report() {
        List<Map.Entry<String, Stat>> entries = new ArrayList<>(STATS.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().nanos.sum(), a.getValue().nanos.sum()));
        List<String> lines = new ArrayList<>();
        long seconds = (System.currentTimeMillis() - since) / 1000L;
        lines.add("Chunk generation since " + seconds + " s ago (average / slowest, per chunk):");
        if (entries.isEmpty()) lines.add("  nothing generated yet");
        for (Map.Entry<String, Stat> e : entries) {
            long n = e.getValue().count.sum();
            double avg = n == 0 ? 0.0 : e.getValue().nanos.sum() / 1.0e6 / n;
            lines.add(String.format(Locale.ROOT, "  %s: %,d chunks, %.1f ms avg, %.0f ms slowest, %.1f s in all", e.getKey(),
                    n, avg, e.getValue().max.get() / 1.0e6, e.getValue().nanos.sum() / 1.0e9));
        }
        return lines;
    }
}
