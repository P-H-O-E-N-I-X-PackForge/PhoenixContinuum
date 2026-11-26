package net.phoenix.continuum.client.screen;

import net.phoenix.continuum.client.ContinuumText;
import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.phoenix.continuum.client.ExpeditionDraft;
import net.phoenix.continuum.common.ContinuumMissions;

import java.util.ArrayList;
import java.util.List;

public class ContinuumExpeditionScreen extends ContinuumFitScreen {

    private static final String K = "phoenix_continuum.continuum_expedition_screen.";

    private static final int W = 232;
    private static final int H = 196;

    private final Screen parent;
    private EditBox nameBox;
    private final List<String> players = new ArrayList<>();

    public ContinuumExpeditionScreen(Screen parent) {
        super(ContinuumLang.tr("phoenix_continuum.continuum_expedition_screen.name_and_crew"));
        this.parent = parent;
    }

    private int panelX() {
        return (width - W) / 2;
    }

    private int panelY() {
        return Math.max(4, (height - H) / 2);
    }

    @Override
    protected void init() {
        super.init();
        var connection = Minecraft.getInstance().getConnection();
        String self = Minecraft.getInstance().player == null ? "" :
                Minecraft.getInstance().player.getGameProfile().getName();
        players.clear();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                String name = info.getProfile().getName();
                if (!name.equals(self)) players.add(name);
            }
        }
        players.sort(String.CASE_INSENSITIVE_ORDER);

        int x = panelX() + 16;
        int y = panelY();
        int w = W - 32;
        nameBox = new EditBox(font, x, y + 28, w, 18, ContinuumLang.tr(K + "expedition_name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(ExpeditionDraft.name);
        nameBox.setResponder(value -> ExpeditionDraft.name = value);
        nameBox.setHint(ContinuumLang.tr(K + "name_this_expedition"));
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        int py = y + 76;
        for (String name : players.subList(0, Math.min(players.size(), 5))) {
            final String player = name;
            addRenderableWidget(MapButton.of(x, py, w, 16, label(player), b -> {
                if (!ExpeditionDraft.crew.remove(player) && ExpeditionDraft.crew.size() < ContinuumMissions.MAX_CREW) {
                    ExpeditionDraft.crew.add(player);
                }
                rebuildWidgets();
            }).selected(ExpeditionDraft.crew.contains(player)));
            py += 18;
        }
        int by = y + H - 28;
        addRenderableWidget(MapButton.of(x, by, (w - 6) / 2, 18, ContinuumText.str(K + "done"), b -> onClose()));
        addRenderableWidget(MapButton.of(x + (w - 6) / 2 + 6, by, (w - 6) / 2, 18, ContinuumText.str(K + "clear"),
                b -> {
                    ExpeditionDraft.name = "";
                    ExpeditionDraft.crew.clear();
                    nameBox.setValue("");
                    rebuildWidgets();
                }));
    }

    private static String label(String name) {
        return (ExpeditionDraft.crew.contains(name) ? "[x] " : "[  ] ") + name;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, MapUi.SCREEN_BG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        renderBackground(g, mouseX, mouseY, partialTick);

        int x = panelX();
        int y = panelY();
        MapUi.panel(g, x, y, W, H);
        g.drawString(font, ContinuumText.str(K + "title"), x + 16, y + 9, MapUi.TITLE);
        g.drawString(font, ContinuumText.str(K + "name_label"), x + 16, y + 19, MapUi.DIM);
        int ly = y + 54;
        for (var line : font.split(net.minecraft.network.chat.FormattedText.of(ContinuumText.str(K + "crew_line",
                ExpeditionDraft.crewCount(), ContinuumMissions.MAX_CREW,
                Math.round(ContinuumMissions.CREW_RISK_REDUCTION * 100))), W - 32)) {
            g.drawString(font, line, x + 16, ly, MapUi.DIM, false);
            ly += 10;
        }
        if (players.isEmpty()) {
            g.drawString(font, ContinuumText.str("phoenix_continuum.continuum_expedition.no_other_players_are_online"),
                    x + 16, y + 80, MapUi.DIM);
        }

        for (Renderable renderable : this.renderables) renderable.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
