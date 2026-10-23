package net.phoenix.continuum.data;

import java.util.ArrayList;
import java.util.List;

public final class ContinuumReport {

    public enum Level {
        ERROR,
        WARNING
    }

    public record Issue(Level level, String where, String message) {

        @Override
        public String toString() {
            return (level == Level.ERROR ? "[error] " : "[warn]  ") + where + ": " + message;
        }
    }

    private static volatile ContinuumReport last = new ContinuumReport();

    private final List<Issue> issues = new ArrayList<>();

    public static ContinuumReport last() {
        return last;
    }

    static void publish(ContinuumReport report) {
        last = report;
    }

    public void error(String where, String message) {
        issues.add(new Issue(Level.ERROR, where, message));
    }

    public void warn(String where, String message) {
        issues.add(new Issue(Level.WARNING, where, message));
    }

    public List<Issue> issues() {
        return issues;
    }

    public long errors() {
        return issues.stream().filter(i -> i.level() == Level.ERROR).count();
    }

    public long warnings() {
        return issues.stream().filter(i -> i.level() == Level.WARNING).count();
    }
}
