package net.phoenix.continuum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Temporary bisect switches for the "world goes black after the map" bug. Use /continuumskip <mask>. */
public final class ContinuumDebug {

    public static final int BACKDROP = 1;
    public static final int BACKDROP_GALAXIES = 2;
    public static final int WASH = 4;
    public static final int CONTENT = 8;
    public static final int BLIT = 16;
    public static final int WHOLE_SCENE = 32;
    public static final int SKIP_CLEAR = 64;
    public static final int RESTORE_CLEAR_COLOR = 128;

    private static volatile int skip = 0;

    private ContinuumDebug() {}

    public static boolean skip(int bit) {
        return (skip & bit) != 0;
    }

    public static void setSkip(int mask) {
        skip = mask;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("continuum skip mask = " + mask), false);
        }
    }
}