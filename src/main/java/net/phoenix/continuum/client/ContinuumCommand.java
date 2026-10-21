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
