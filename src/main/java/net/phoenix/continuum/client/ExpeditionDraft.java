package net.phoenix.continuum.client;

import net.phoenix.continuum.common.ContinuumMissions;

import java.util.LinkedHashSet;
import java.util.Set;

/** The name and crew the player has picked for the next launch (kept until they change it). */
public final class ExpeditionDraft {

    private ExpeditionDraft() {}

    public static String name = "";
    public static final Set<String> crew = new LinkedHashSet<>();

    public static boolean isSet() {
        return !name.isBlank() || !crew.isEmpty();
    }

    /** How many crew actually count (the server takes at most this many). */
    public static int crewCount() {
        return Math.min(crew.size(), ContinuumMissions.MAX_CREW);
    }

    public static String summary() {
        String label = name.isBlank() ? "Crew" : name;
        return crew.isEmpty() ? label : label + " +" + crew.size();
    }
}
