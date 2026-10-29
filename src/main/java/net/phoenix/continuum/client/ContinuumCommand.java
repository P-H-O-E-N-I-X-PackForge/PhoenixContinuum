package net.phoenix.continuum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.phoenix.continuum.client.screen.ContinuumMapScreen;
import net.phoenix.continuum.client.screen.ContinuumTestScreen;

import java.util.function.Supplier;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@OnlyIn(Dist.CLIENT)
public final class ContinuumCommand {

    private ContinuumCommand() {}

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                literal("continuum")
                        .then(literal("test").executes(ctx -> open(ContinuumTestScreen::new)))
                        .then(literal("map").executes(ctx -> open(ContinuumMapScreen::new)))
                        .then(literal("goals").executes(ctx -> goals()))
                        .then(literal("rescue")
                                .then(literal("textures").executes(ctx -> rescue("textures")))
                                .then(literal("chunks").executes(ctx -> rescue("chunks")))
                                .then(literal("target").executes(ctx -> rescue("target"))))
                        .then(literal("glstate")
                                .then(literal("on").executes(ctx -> glState(true)))
                                .then(literal("off").executes(ctx -> glState(false))))
                        .then(literal("baked")
                                .then(literal("backdrop")
                                        .then(literal("on").executes(ctx -> baked(true, true)))
                                        .then(literal("off").executes(ctx -> baked(true, false))))
                                .then(literal("puffs")
                                        .then(literal("on").executes(ctx -> baked(false, true)))
                                        .then(literal("off").executes(ctx -> baked(false, false)))))
                        .then(literal("profile")
                                .then(literal("start").executes(ctx -> profile("start")))
                                .then(literal("stop").executes(ctx -> profile("stop")))
                                .then(literal("report").executes(ctx -> profile("report")))
                                .then(literal("reset").executes(ctx -> profile("reset"))))
                        .then(literal("tune")
                                .executes(ctx -> tune(null))
                                .then(argument("body", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                                .suggestResource(net.phoenix.continuum.data.ContinuumData.allBodies()
                                                        .stream().map(net.phoenix.continuum.data.ContinuumBody::id),
                                                        builder))
                                        .executes(ctx -> tune(net.minecraft.commands.arguments.ResourceLocationArgument
                                                .getId((com.mojang.brigadier.context.CommandContext) ctx, "body"))))));
    }

    private static int profile(String what) {
        Minecraft mc = Minecraft.getInstance();
        switch (what) {
            case "start" -> net.phoenix.continuum.client.ContinuumProfiler.start();
            case "stop" -> {
                for (String line : net.phoenix.continuum.client.ContinuumProfiler.report()) {
                    net.phoenix.continuum.PhoenixContinuum.LOGGER.info("[profile] {}", line);
                }
                net.phoenix.continuum.client.ContinuumProfiler.stop();
            }
            case "reset" -> net.phoenix.continuum.client.ContinuumProfiler.reset();
            default -> {}
        }
        if (mc.player == null) return 1;
        if (what.equals("start")) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Profiling Continuum drawing. Open the map or a personal dimension; the heaviest passes are listed on screen. "
                            + "/continuum profile report prints them, stop ends it."), false);
        } else if (what.equals("report") || what.equals("stop")) {
            for (String line : net.phoenix.continuum.client.ContinuumProfiler.report()) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(line), false);
            }
        }
        return 1;
    }

    /** For when the screen has gone black: each tries one thing, and which of them brings the world back says what broke. */
    private static int rescue(String what) {
        Minecraft mc = Minecraft.getInstance();
        switch (what) {
            case "textures" -> {
                for (int unit = 0; unit < 12; unit++) {
                    com.mojang.blaze3d.systems.RenderSystem.activeTexture(33984 + unit);
                    com.mojang.blaze3d.systems.RenderSystem.bindTexture(0);
                }
                com.mojang.blaze3d.systems.RenderSystem.activeTexture(33984);
            }
            case "chunks" -> mc.levelRenderer.allChanged();
            case "target" -> mc.getMainRenderTarget().resize(mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                    Minecraft.ON_OSX);
            default -> {}
        }
        say("Tried: " + what + ". Is the world back?");
        return 1;
    }

    private static int glState(boolean on) {
        net.phoenix.continuum.client.GlStateDump.setEnabled(on);
        say(on ? "GL state logging on: open and close the map, then read the [glstate] lines in latest.log." :
                "GL state logging off.");
        return 1;
    }

    private static int baked(boolean backdrop, boolean on) {
        if (backdrop) net.phoenix.continuum.client.render.SceneRenderer.USE_BAKED_BACKDROP = on;
        else net.phoenix.continuum.client.render.SceneRenderer.USE_BAKED_PUFFS = on;
        say("Baked " + (backdrop ? "backdrop" : "nebula puffs") + (on ? " on." : " off."));
        return 1;
    }

    private static void say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal(text), false);
    }

    private static int goals() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        for (net.minecraft.network.chat.Component line : net.phoenix.continuum.client.ContinuumGoals.lines()) {
            mc.player.displayClientMessage(line, false);
        }
        return 1;
    }

    private static int tune(net.minecraft.resources.ResourceLocation body) {
        Minecraft mc = Minecraft.getInstance();
        if (!net.phoenix.continuum.client.screen.ContinuumTunerScreen.allowed()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "The Continuum tuner is for operators (or the single-player owner)."), false);
            }
            return 0;
        }
        mc.tell(() -> mc.setScreen(new net.phoenix.continuum.client.screen.ContinuumTunerScreen(mc.screen, body)));
        return 1;
    }

    private static int open(Supplier<Screen> screen) {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(screen.get()));
        return 1;
    }
}
