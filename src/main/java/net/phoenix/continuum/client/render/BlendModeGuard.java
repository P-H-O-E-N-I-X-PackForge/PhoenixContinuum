package net.phoenix.continuum.client.render;

import com.mojang.blaze3d.shaders.BlendMode;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

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

    public static void enter() {
        if (touched || LAST_APPLIED == null) return;
        try {
            saved = (BlendMode) LAST_APPLIED.get(null);
            touched = true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    public static void restoreBeforeGui() {
        if (!touched) return;
        touched = false;
        try {
            LAST_APPLIED.set(null, saved);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        saved = null;
    }
}
