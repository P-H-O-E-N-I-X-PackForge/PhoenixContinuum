package net.phoenix.continuum.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

public final class ContinuumText {

    private ContinuumText() {}

    public static String str(String key, Object... args) {
        return I18n.get(key, args);
    }

    public static String label(String key) {
        return Language.getInstance().getOrDefault(key);
    }
}
