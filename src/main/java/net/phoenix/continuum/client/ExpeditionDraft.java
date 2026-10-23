package net.phoenix.continuum.client;

import net.phoenix.continuum.common.ContinuumMissions;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ExpeditionDraft {

    private ExpeditionDraft() {}

    public static String name = "";
    public static final Set<String> crew = new LinkedHashSet<>();

    public static boolean isSet() {
        return !name.isBlank() || !crew.isEmpty();
    }

    public static int crewCount() {
        return Math.min(crew.size(), ContinuumMissions.MAX_CREW);
    }

    public static String summary() {
        String label = name.isBlank() ? "Crew" : name;
        return crew.isEmpty() ? label : label + " +" + crew.size();
    }
}
