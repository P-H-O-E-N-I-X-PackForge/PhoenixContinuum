package net.phoenix.continuum.common.visibility;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.common.ContinuumTeamData;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides, per team, what shows on the map and what can be flown to. The order, for a body:
 * its system's rule, then its own rule ({@code "visibility": {"visible_if": ..., "accessible_if": ...}}), then a
 * per-team override set with {@code /continuumadmin show|hide}, then {@link ContinuumVisibilityEvent}. With no rules
 * and no overrides everything is visible and accessible.
 */
public final class ContinuumVisibility {

    private record Rules(@Nullable ContinuumCondition visible, @Nullable ContinuumCondition accessible) {

        static final Rules NONE = new Rules(null, null);
    }

    private static final Map<ResourceLocation, Rules> CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<ResourceLocation>> EVALUATING = ThreadLocal.withInitial(HashSet::new);

    private ContinuumVisibility() {}

    /** Forgets compiled rules; called when Continuum data is reloaded. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static Rules rules(ResourceLocation id, @Nullable JsonObject visibility) {
        if (visibility == null) return Rules.NONE;
        return CACHE.computeIfAbsent(id, key -> new Rules(compile(key, visibility, "visible_if"),
                compile(key, visibility, "accessible_if")));
    }

    private static @Nullable ContinuumCondition compile(ResourceLocation id, JsonObject visibility, String key) {
        if (!visibility.has(key)) return null;
        try {
            return ContinuumConditions.parse(visibility.get(key));
        } catch (RuntimeException e) {
            PhoenixContinuum.LOGGER.error("[Continuum] {} {} is invalid and ignored: {}", id, key, e.getMessage());
            return null;
        }
    }

    private static boolean test(@Nullable ContinuumCondition condition, MinecraftServer server, UUID team) {
        if (condition == null) return true;
        try {
            return condition.test(new ContinuumCondition.Context(server, team));
        } catch (RuntimeException e) {
            PhoenixContinuum.LOGGER.error("[Continuum] a visibility rule failed: {}", e.getMessage());
            return true;
        }
    }

    public static boolean visible(MinecraftServer server, UUID team, ContinuumBody body) {
        return evaluate(server, team, body, ContinuumVisibilityEvent.Kind.VISIBLE);
    }

    public static boolean accessible(MinecraftServer server, UUID team, ContinuumBody body) {
        return evaluate(server, team, body, ContinuumVisibilityEvent.Kind.ACCESSIBLE);
    }

    private static boolean evaluate(MinecraftServer server, UUID team, ContinuumBody body,
                                    ContinuumVisibilityEvent.Kind kind) {
        Set<ResourceLocation> active = EVALUATING.get();
        // rules can refer to other bodies; a reference loop counts as "visible" rather than hanging the server
        ResourceLocation guard = ResourceLocation.fromNamespaceAndPath(kind.name().toLowerCase(), body.id().toString()
                .replace(':', '/'));
        if (!active.add(guard)) return true;
        try {
            boolean visibleKind = kind == ContinuumVisibilityEvent.Kind.VISIBLE;
            boolean result = true;

            ContinuumSystem system = ContinuumData.system(body.system());
            if (system != null) {
                Rules rules = rules(system.id(), system.visibility());
                result = test(visibleKind ? rules.visible() : rules.accessible(), server, team);
            }
            if (result) {
                Rules rules = rules(body.id(), body.visibility());
                result = test(visibleKind ? rules.visible() : rules.accessible(), server, team);
            }

            if (visibleKind) {
                ContinuumTeamData data = ContinuumTeamData.get(server);
                Boolean forced = data.visibilityOverride(team, body.id());
                if (forced == null) forced = data.visibilityOverride(team, body.system());
                if (forced != null) result = forced;
            }

            ContinuumVisibilityEvent event = new ContinuumVisibilityEvent(server, team, body.id(), kind, result);
            NeoForge.EVENT_BUS.post(event);
            return event.getResult();
        } finally {
            active.remove(guard);
        }
    }

    /** A readable account of why a body is or is not visible and accessible to a team, for /continuumadmin explain. */
    public static List<String> explain(MinecraftServer server, UUID team, ContinuumBody body) {
        List<String> lines = new ArrayList<>();
        ContinuumSystem system = ContinuumData.system(body.system());
        if (system != null) lines.add(describe("system " + system.id(), rules(system.id(), system.visibility()),
                server, team));
        lines.add(describe("body " + body.id(), rules(body.id(), body.visibility()), server, team));

        ContinuumTeamData data = ContinuumTeamData.get(server);
        Boolean forced = data.visibilityOverride(team, body.id());
        Boolean forcedSystem = data.visibilityOverride(team, body.system());
        if (forced != null) lines.add("override on the body: " + (forced ? "show" : "hide"));
        if (forcedSystem != null) lines.add("override on the system: " + (forcedSystem ? "show" : "hide"));

        lines.add("=> visible: " + visible(server, team, body) + ", accessible: " + accessible(server, team, body));
        return lines;
    }

    private static String describe(String what, Rules rules, MinecraftServer server, UUID team) {
        if (rules == Rules.NONE) return what + ": no rules";
        return what + ": visible_if " + (rules.visible() == null ? "none" : test(rules.visible(), server, team)) +
                ", accessible_if " + (rules.accessible() == null ? "none" : test(rules.accessible(), server, team));
    }
}
