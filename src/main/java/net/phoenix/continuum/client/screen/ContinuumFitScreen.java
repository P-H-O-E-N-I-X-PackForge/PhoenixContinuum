package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.Window;

/**
 * A screen that is never laid out on a canvas smaller than it was designed for. If the player's GUI scale (or a small
 * window) leaves less room than {@link #minW()} by {@link #minH()}, the GUI scale is lowered for as long as the screen is
 * open, so text and buttons stay on the screen. Everything else (mouse positions, drawing, {@code width} and
 * {@code height}) follows the new scale by itself. The player's own setting is put back when the screen closes.
 * <p>
 * A subclass that overrides {@code init()} must call {@code super.init()} first, and one that overrides
 * {@code removed()} must call {@code super.removed()}.
 */
public abstract class ContinuumFitScreen extends Screen {

    private boolean shrunk = false;

    protected ContinuumFitScreen(Component title) {
        super(title);
    }

    /** The least width, in GUI pixels, the layout needs. */
    protected int minW() {
        return 520;
    }

    /** The least height, in GUI pixels, the layout needs. */
    protected int minH() {
        return 300;
    }

    private static int baseScale(Minecraft mc) {
        return mc.getWindow().calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int base = baseScale(mc);
        int fit = base;
        while (fit > 1 && (window.getWidth() / fit < minW() || window.getHeight() / fit < minH())) fit--;
        if (fit != base || shrunk) {
            window.setGuiScale(fit);
            shrunk = fit != base;
            this.width = window.getGuiScaledWidth();
            this.height = window.getGuiScaledHeight();
        }
    }

    @Override
    public void removed() {
        if (shrunk) {
            Minecraft mc = Minecraft.getInstance();
            mc.getWindow().setGuiScale(baseScale(mc));
            shrunk = false;
        }
        super.removed();
    }
}
