package net.phoenix.continuum.client.render;

import com.mojang.blaze3d.shaders.BlendMode;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * Keeps the game's record of the last shader blend mode honest.
 * <p>
 * Every shader the game uses applies its own blend mode first, unless it equals the last one applied; that last one is kept
 * in a private static field, and it is only updated when blending is switched on or off. The vignette is drawn by setting a
 * special blend function and then drawing a textured quad with a shader whose blend mode is the same as the one before it,
 * so nothing overwrites that function. After one of our shaders has run, the record holds one of our blend modes instead,
 * the quad's shader no longer matches it, and the game applies plain alpha blending over the vignette's function: the
 * vignette's near-black texture then covers the whole screen. So the record is saved before our first shader use in a
 * frame and put back just before the HUD is drawn.
 */
public final class BlendModeGuard {

    private static final Field LAST_APPLIED = find();
    private static BlendMode saved;
    private static boolean touched;

    private BlendModeGuard() {}

    private static Field find() {
        try {
            return ObfuscationReflectionHelper.findField(BlendMode.class, "lastApplied");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Called when one of our shaders is about to be applied. */
    public static void enter() {
        if (touched || LAST_APPLIED == null) return;
        try {
            saved = (BlendMode) LAST_APPLIED.get(null);
            touched = true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    /** Called just before the HUD: puts the game's record back to what it was before we used any shader. */
    public static void restoreBeforeGui() {
        if (!touched) return;
        touched = false;
        try {
            LAST_APPLIED.set(null, saved);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        saved = null;
    }
}
