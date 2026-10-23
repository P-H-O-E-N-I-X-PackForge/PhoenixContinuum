package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.phoenix.continuum.client.ExpeditionDraft;
import net.phoenix.continuum.common.ContinuumMissions;

import java.util.ArrayList;
import java.util.List;

public class ContinuumExpeditionScreen extends Screen {

    private final Screen parent;
    private EditBox nameBox;
    private final List<String> players = new ArrayList<>();

    public ContinuumExpeditionScreen(Screen parent) {
        super(Component.literal("Name and crew"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var connection = Minecraft.getInstance().getConnection();
        String self = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().getName();
        players.clear();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                String name = info.getProfile().getName();
                if (!name.equals(self)) players.add(name);
            }
        }
        players.sort(String.CASE_INSENSITIVE_ORDER);

        int cx = width / 2;
        int top = height / 2 - 74;
        nameBox = new EditBox(font, cx - 100, top + 22, 200, 18, Component.literal("Expedition name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(ExpeditionDraft.name);
        nameBox.setResponder(value -> ExpeditionDraft.name = value);
        nameBox.setHint(Component.literal("Name this expedition"));
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        int y = top + 62;
        for (String name : players.subList(0, Math.min(players.size(), 6))) {
            addRenderableWidget(Button.builder(label(name), b -> {
                if (!ExpeditionDraft.crew.remove(name) && ExpeditionDraft.crew.size() < ContinuumMissions.MAX_CREW) {
                    ExpeditionDraft.crew.add(name);
                }
                b.setMessage(label(name));
            }).bounds(cx - 100, y, 200, 16).build());
            y += 18;
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 100, height / 2 + 62, 98, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            ExpeditionDraft.name = "";
            ExpeditionDraft.crew.clear();
            nameBox.setValue("");
            rebuildWidgets();
        }).bounds(cx + 2, height / 2 + 62, 98, 18).build());
    }

    private static Component label(String name) {
        return Component.literal((ExpeditionDraft.crew.contains(name) ? "[x] " : "[  ] ") + name);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int top = height / 2 - 74;
        g.drawCenteredString(font, "Name and crew", cx, top + 6, 0xFFFFFFFF);
        g.drawString(font, "Crew (" + ExpeditionDraft.crewCount() + " of " + ContinuumMissions.MAX_CREW +
                "): each lowers the flight's chance of failing by " +
                Math.round(ContinuumMissions.CREW_RISK_REDUCTION * 100) + "%", cx - 100, top + 48, 0xFFb0b8c8);
        if (players.isEmpty()) {
            g.drawString(font, "No other players are online.", cx - 100, top + 64, 0xFF8890a0);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
