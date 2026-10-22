package net.phoenix.continuum.client.pdim;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class PdimClientState {

    private PdimClientState() {}

    private static boolean inside;
    private static float gravity = 1.0f;
    private static float daySpeed = 1.0f;
    private static float lockedTime = -1.0f;
    private static boolean orbitView;
    private static boolean fixedSky;
    private static Map<ResourceLocation, net.phoenix.continuum.pdim.PdimData.Entry> mine = Map.of();
    private static Map<ResourceLocation, net.phoenix.continuum.pdim.PdimCosts.Cost> costs = Map.of();
    private static boolean everyVisit = true;

    public static void accept(boolean insideNow, float gravityNow, float daySpeedNow, float lockedTimeNow, boolean orbitViewNow, boolean fixedSkyNow,
                              Map<ResourceLocation, net.phoenix.continuum.pdim.PdimData.Entry> dimensions,
                              Map<ResourceLocation, net.phoenix.continuum.pdim.PdimCosts.Cost> costMap,
                              boolean chargesEveryVisit) {
        inside = insideNow;
        gravity = gravityNow;
        daySpeed = daySpeedNow;
        lockedTime = lockedTimeNow;
        orbitView = orbitViewNow;
        fixedSky = fixedSkyNow;
        mine = new HashMap<>(dimensions);
        costs = new HashMap<>(costMap);
        everyVisit = chargesEveryVisit;
    }

    public static @Nullable net.phoenix.continuum.pdim.PdimCosts.Cost cost(ResourceLocation body) {
        return costs.get(body);
    }

    public static boolean chargesEveryVisit() {
        return everyVisit;
    }

    public static boolean inside() {
        return inside;
    }

    public static float daySpeed() {
        return daySpeed;
    }

    public static float lockedTime() {
        return lockedTime;
    }

    public static boolean orbitView() {
        return orbitView;
    }

    public static boolean fixedSky() {
        return fixedSky;
    }

    public static float gravity() {
        return gravity;
    }

    public static @Nullable Float dimension(ResourceLocation body) {
        var e = mine.get(body);
        return e == null ? null : e.gravity();
    }

    public static @Nullable net.phoenix.continuum.pdim.PdimData.Entry entry(ResourceLocation body) {
        return mine.get(body);
    }
}
