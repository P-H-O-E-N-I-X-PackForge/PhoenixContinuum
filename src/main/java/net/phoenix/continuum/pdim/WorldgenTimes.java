package net.phoenix.continuum.pdim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

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
