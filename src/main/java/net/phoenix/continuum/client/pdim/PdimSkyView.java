package net.phoenix.continuum.client.pdim;

import net.minecraft.client.multiplayer.ClientLevel;

public record PdimSkyView(double dayTicks, double gameTicks, float lockedTime, float daySpeed, boolean orbit,
                          boolean fixed, boolean showUndiscovered) {

    public static PdimSkyView live(ClientLevel level, float partialTick) {
        return new PdimSkyView(level.getDayTime() + (double) partialTick, level.getGameTime() + (double) partialTick,
                PdimClientState.lockedTime(), PdimClientState.daySpeed(), PdimClientState.orbitView(),
                PdimClientState.fixedSky(), false);
    }
}
