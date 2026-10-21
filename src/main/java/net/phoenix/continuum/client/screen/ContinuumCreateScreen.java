package net.phoenix.continuum.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.network.C2SCreatePacket;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Create a new system or body from the game (operators). The server writes it as an ordinary definition file in the
 * pack folder. A new body starts as a copy of an existing one (it inherits it) and only states what you chose here.
 */
@OnlyIn(Dist.CLIENT)
public final class ContinuumCreateScreen extends Screen {

    private static final String[] KINDS = { "Planet", "Gas giant", "Moon", "Black hole" };

    private final Screen parent;
    private final List<ContinuumSystem> systems = new ArrayList<>();
    private int systemIndex;
    private int kindIndex;
    private int templateIndex;
    private int parentIndex;

    private EditBox name;
    private Button systemButton;
    private Button kindButton;
    private Button templateButton;
    private Button parentButton;
    private String error = "";

    public ContinuumCreateScreen(Screen parent, @Nullable ResourceLocation startSystem) {
        super(Component.literal("New body"));
        this.parent = parent;
        systems.addAll(ContinuumData.systems());
        systems.sort(Comparator.comparing(ContinuumSystem::name));
        for (int i = 0; i < systems.size(); i++) {
            if (systems.get(i).id().equals(startSystem)) systemIndex = i;
        }
    }

    // ------------------------------------------------------------------ choices

    private ContinuumSystem currentSystem() {
        return systems.isEmpty() ? null : systems.get(Math.floorMod(systemIndex, systems.size()));
    }

    private boolean isMoon() {
        return kindIndex == 2;
    }

    private boolean isHole() {
        return kindIndex == 3;
    }

    /** Existing bodies of the chosen kind, the things a new one can start from. */
    private List<ContinuumBody> templates() {
        List<ContinuumBody> out = new ArrayList<>();
        for (ContinuumBody body : ContinuumData.allBodies()) {
            boolean match = switch (kindIndex) {
                case 0 -> body.type() == ContinuumBody.Type.PLANET && !body.params().gasGiant();
                case 1 -> body.type() == ContinuumBody.Type.PLANET && body.params().gasGiant();
                case 2 -> body.type() == ContinuumBody.Type.MOON;
                default -> body.type() == ContinuumBody.Type.BLACK_HOLE;
            };
            if (match) out.add(body);
        }
        out.sort(Comparator.comparing((ContinuumBody b) -> !b.id().getPath().startsWith("example_"))
                .thenComparing(ContinuumBody::name));
        return out;
    }

    private @Nullable ContinuumBody template() {
        List<ContinuumBody> list = templates();
        return list.isEmpty() ? null : list.get(Math.floorMod(templateIndex, list.size()));
    }

    private List<ContinuumBody> planetsOfSystem() {
        List<ContinuumBody> out = new ArrayList<>();
        ContinuumSystem sys = currentSystem();
        if (sys == null) return out;
        for (ContinuumBody body : ContinuumData.bodiesOf(sys.id())) {
            if (body.type() == ContinuumBody.Type.PLANET) out.add(body);
        }
        return out;
    }

    private @Nullable ContinuumBody orbited() {
        List<ContinuumBody> list = planetsOfSystem();
        return list.isEmpty() ? null : list.get(Math.floorMod(parentIndex, list.size()));
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        int w = 260;
        int x = (width - w) / 2;
        int y = 40;

        name = new EditBox(font, x, y + 12, w, 16, Component.literal("Name"));
        name.setHint(Component.literal("Name"));
        name.setMaxLength(32);
        addRenderableWidget(name);
        setInitialFocus(name);
        y += 38;

        systemButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            systemIndex++;
            parentIndex = 0;
            refreshLabels();
        }).bounds(x, y + 12, w, 16).build());
        y += 38;
        kindButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            kindIndex = (kindIndex + 1) % KINDS.length;
            templateIndex = 0;
            refreshLabels();
        }).bounds(x, y + 12, w, 16).build());
        y += 38;
        parentButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            parentIndex++;
            refreshLabels();
        }).bounds(x, y + 12, w, 16).build());
        y += 38;
        templateButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            templateIndex++;
            refreshLabels();
        }).bounds(x, y + 12, w, 16).build());
        y += 44;

        addRenderableWidget(Button.builder(Component.literal("Create"), b -> submit())
                .bounds(x, y + 6, w / 2 - 3, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + w / 2 + 3, y + 6, w / 2 - 3, 18).build());
        refreshLabels();
    }

    private void refreshLabels() {
        ContinuumSystem sys = currentSystem();
        systemButton.setMessage(Component.literal("System: " + (sys == null ? "none" : sys.name())));
        kindButton.setMessage(Component.literal("Kind: " + KINDS[kindIndex]));
        ContinuumBody orbit = orbited();
        parentButton.active = isMoon();
        parentButton.setMessage(Component.literal(isMoon() ? "Orbits: " + (orbit == null ? "no planet in this system" :
                orbit.name()) : "Orbits: the star"));
        ContinuumBody template = template();
        templateButton.setMessage(Component.literal("Start from: " + (template == null ? "nothing" :
                template.name() + " (" + template.id().getPath() + ")")));
    }

    /** The position in the galaxy furthest from every existing system, close to the middle. */
    static double[] freeSpot() {
        double bestX = 0;
        double bestZ = 0;
        double bestScore = -1;
        for (double x = -30; x <= 30; x += 1.0) {
            for (double z = -30; z <= 30; z += 1.0) {
                double nearest = Double.MAX_VALUE;
                for (ContinuumSystem s : ContinuumData.systems()) {
                    nearest = Math.min(nearest, Math.hypot(x - s.galaxyX(), z - s.galaxyZ()));
                }
                double score = Math.min(nearest, 9.0) - Math.hypot(x, z) * 0.02;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        return new double[] { bestX, 0.0, bestZ };
    }

    // ------------------------------------------------------------------ create

    private ResourceLocation freshId(String display) {
        String slug = display.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) slug = "new_body";
        String candidate = slug;
        int n = 2;
        while (ContinuumData.body(new ResourceLocation("phoenix_continuum", candidate)) != null) {
            candidate = slug + "_" + n++;
        }
        return new ResourceLocation("phoenix_continuum", candidate);
    }

    private void submit() {
        String display = name.getValue().trim();
        if (display.isEmpty()) {
            error = "Give it a name.";
            return;
        }
        ResourceLocation id = freshId(display);
        JsonObject def = new JsonObject();
        def.addProperty("format", 1);

        ContinuumSystem sys = currentSystem();
        if (sys == null) {
            error = "There is no system to put it in.";
            return;
        }
        ContinuumBody template = template();
        ContinuumBody parentBody = isMoon() ? orbited() : null;
        if (isMoon() && parentBody == null) {
            error = "A moon needs a planet to orbit in that system.";
            return;
        }
        if (template != null) def.addProperty("inherits", template.id().toString());
        def.addProperty("system", sys.id().toString());
        def.addProperty("name", display);
        def.addProperty("description", "Created in game.");
        def.addProperty("type", isHole() ? "black_hole" : isMoon() ? "moon" : "planet");
        def.addProperty("initial_stage", "detected");
        if (parentBody != null) def.addProperty("parent", parentBody.id().toString());

        Random random = new Random();
        JsonObject orbit = new JsonObject();
        if (isHole()) {
            orbit.addProperty("radius_au", 0.0);
            orbit.addProperty("period_days", 1.0);
        } else if (isMoon()) {
            double radius = 0.006;
            for (ContinuumBody moon : ContinuumData.moonsOf(parentBody.id())) {
                radius = Math.max(radius, moon.orbitAu() + 0.004);
            }
            orbit.addProperty("radius_au", Math.round(radius * 1000) / 1000.0);
            orbit.addProperty("period_days", Math.round(8 + radius * 900));
        } else {
            double radius = 0.8;
            for (ContinuumBody body : ContinuumData.bodiesOf(sys.id())) {
                if (body.type() == ContinuumBody.Type.PLANET) radius = Math.max(radius, body.orbitAu() + 0.9);
            }
            orbit.addProperty("radius_au", Math.round(radius * 100) / 100.0);
            orbit.addProperty("period_days", Math.round(365 * Math.pow(radius, 1.5)));
        }
        orbit.addProperty("phase", Math.round(random.nextDouble() * 100) / 100.0);
        def.add("orbit", orbit);

        if (!isHole()) {
            JsonObject surface = new JsonObject();
            surface.addProperty("seed", random.nextInt(9000) + 1);
            def.add("surface", surface);
        }

        net.phoenix.continuum.network.ContinuumNetwork.sendToServer(new C2SCreatePacket(false, id, def.toString()));
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ drawing


    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xF00A0E14);
        int w = 260;
        int x = (width - w) / 2;
        g.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
        g.drawString(font, "Name", x, 40, 0xFFAEBBCF, false);
        if (!error.isEmpty()) g.drawCenteredString(font, error, width / 2, height - 24, 0xFFFF6666);
        g.drawCenteredString(font, "A new body inherits the one you start from; edit it afterwards in the tuner.", width / 2,
                height - 40, 0xFF7F8A99);
        super.render(g, mx, my, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
