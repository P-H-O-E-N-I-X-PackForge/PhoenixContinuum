package net.phoenix.continuum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
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
                        .then(literal("screenlog")
                                .then(literal("on").executes(ctx -> screenLog(true)))
                                .then(literal("off").executes(ctx -> screenLog(false))))
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

    private static int screenLog(boolean on) {
        ContinuumScreenLog.setEnabled(on);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(on ?
                    "Continuum screen log on: reproduce the flicker, then read the [screens] lines in latest.log." :
                    "Continuum screen log off."), false);
        }
        return 1;
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
