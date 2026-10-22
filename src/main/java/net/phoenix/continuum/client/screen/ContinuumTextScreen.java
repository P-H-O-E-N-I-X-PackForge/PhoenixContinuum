package net.phoenix.continuum.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * A roomy editor for long text (names, descriptions, lore): a wrapping multi-line box with a character count and
 * Save / Cancel, in place of the one-line box the tuner list has room for.
 */
@OnlyIn(Dist.CLIENT)
public final class ContinuumTextScreen extends Screen {

    private static final int MAX_LENGTH = 4000;

    private final Screen parent;
    private final String label;
    private final String initial;
    private final Consumer<String> onSave;
    private MultiLineEditBox box;

    public ContinuumTextScreen(Screen parent, String label, String initial, Consumer<String> onSave) {
        super(Component.literal(label));
        this.parent = parent;
        this.label = label;
        this.initial = initial == null ? "" : initial;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        int boxW = Math.min(width - 40, 560);
        int boxH = Math.max(80, height - 120);
        int x = (width - boxW) / 2;
        int y = 40;

        box = new MultiLineEditBox(font, x, y, boxW, boxH, Component.literal("Type here..."), Component.empty());
        box.setCharacterLimit(MAX_LENGTH);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);

        int buttonY = y + boxH + 10;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(x + boxW - 164, buttonY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + boxW - 80, buttonY, 80, 20).build());
    }

    private void save() {
        // a line break in a name or description is never wanted, so a paragraph pasted in becomes one line
        onSave.accept(box.getValue().replace("\r", "").replace('\n', ' ').trim());
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xF00A0E14);
        int boxW = Math.min(width - 40, 560);
        int x = (width - boxW) / 2;
        g.drawString(font, "Editing: " + label, x, 22, 0xFFFFFFFF, false);
        String count = box == null ? "" : box.getValue().length() + " / " + MAX_LENGTH;
        g.drawString(font, count, x, 40 + Math.max(80, height - 120) + 16, 0xFF7F8A99, false);
        super.render(g, mx, my, partialTick);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {}

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {}

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER && hasControlDown()) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
