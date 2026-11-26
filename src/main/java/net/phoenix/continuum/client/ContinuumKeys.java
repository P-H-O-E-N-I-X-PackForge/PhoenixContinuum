package net.phoenix.continuum.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;

/** The keys of the Continuum map. They are in the controls screen under "Phoenix Continuum" and can be rebound. */
public final class ContinuumKeys {

    private static final String CATEGORY = "key.categories.phoenix_continuum";

    public static final KeyMapping CYCLE_QUALITY = new KeyMapping("key.phoenix_continuum.cycle_quality",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, InputConstants.KEY_Q, CATEGORY);
    public static final KeyMapping TOGGLE_STYLE = new KeyMapping("key.phoenix_continuum.toggle_style",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, InputConstants.KEY_C, CATEGORY);
    public static final KeyMapping TOGGLE_ORBITS = new KeyMapping("key.phoenix_continuum.toggle_orbits",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, InputConstants.KEY_P, CATEGORY);
    public static final KeyMapping OPEN_HISTORY = new KeyMapping("key.phoenix_continuum.open_history",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, InputConstants.KEY_H, CATEGORY);

    private ContinuumKeys() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CYCLE_QUALITY);
        event.register(TOGGLE_STYLE);
        event.register(TOGGLE_ORBITS);
        event.register(OPEN_HISTORY);
    }

    public static boolean pressed(KeyMapping key, int keyCode, int scanCode) {
        return key.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }
}
