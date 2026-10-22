package net.phoenix.continuum.client.pdim;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Everything the personal-dimension sky needs to know about "now", so it can be drawn for the world you are standing
 * in or, in the tuner's sky view, for any world at any time.
 *
 * @param dayTicks           the time of day clock, in ticks, partial tick included
 * @param gameTicks          the game clock that moves the worlds round their orbits, partial tick included
 * @param lockedTime         a fixed time of day (a day fraction in ticks), or negative for none
 * @param daySpeed           how fast the day runs
 * @param orbit              viewing from orbit instead of the surface
 * @param fixed              worlds held at fixed places in the sky
 * @param showUndiscovered   draw worlds the player has not discovered yet
 */
public record PdimSkyView(double dayTicks, double gameTicks, float lockedTime, float daySpeed, boolean orbit,
                          boolean fixed, boolean showUndiscovered) {

    public static PdimSkyView live(ClientLevel level, float partialTick) {
        return new PdimSkyView(level.getDayTime() + (double) partialTick, level.getGameTime() + (double) partialTick,
                PdimClientState.lockedTime(), PdimClientState.daySpeed(), PdimClientState.orbitView(),
                PdimClientState.fixedSky(), false);
    }
}
