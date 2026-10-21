package net.phoenix.continuum.pdim;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber(modid = PhoenixContinuum.MOD_ID)
public final class PdimCommands {

    private PdimCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("pdim")
                .then(literal("create").then(argument("body", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                ContinuumData.allBodies().stream().map(ContinuumBody::id), builder))
                        .executes(ctx -> enter(ctx.getSource(), ResourceLocationArgument.getId(ctx, "body"), null))
                        .then(argument("gravity", FloatArgumentType.floatArg(PdimDimensions.MIN_GRAVITY,
                                PdimDimensions.MAX_GRAVITY))
                                .executes(ctx -> enter(ctx.getSource(), ResourceLocationArgument.getId(ctx, "body"),
                                        FloatArgumentType.getFloat(ctx, "gravity"))))))
                .then(literal("enter").then(argument("body", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                ContinuumData.allBodies().stream().map(ContinuumBody::id), builder))
                        .executes(ctx -> enter(ctx.getSource(), ResourceLocationArgument.getId(ctx, "body"), null))))
                .then(literal("gravity").then(argument("gravity", FloatArgumentType.floatArg(
                        PdimDimensions.MIN_GRAVITY, PdimDimensions.MAX_GRAVITY))
                        .executes(ctx -> adjust(ctx.getSource(), "gravity x" + String.format(Locale.ROOT, "%.2f",
                                FloatArgumentType.getFloat(ctx, "gravity")),
                                e -> new PdimData.Entry(PdimDimensions.clampGravity(
                                        FloatArgumentType.getFloat(ctx, "gravity")), e.plot(), e.daySpeed(),
                                        e.lockedTime(), e.orbitView())))))
                .then(literal("sky")
                        .then(literal("surface").executes(ctx -> adjust(ctx.getSource(), "sky from the surface",
                                e -> new PdimData.Entry(e.gravity(), e.plot(), e.daySpeed(), e.lockedTime(), false))))
                        .then(literal("orbit").executes(ctx -> adjust(ctx.getSource(), "sky from orbit",
                                e -> new PdimData.Entry(e.gravity(), e.plot(), e.daySpeed(), e.lockedTime(), true)))))
                .then(literal("day")
                        .then(literal("speed").then(argument("speed", FloatArgumentType.floatArg(
                                PdimDimensions.MIN_DAY_SPEED, PdimDimensions.MAX_DAY_SPEED))
                                .executes(ctx -> adjust(ctx.getSource(), "day speed x" + String.format(Locale.ROOT,
                                        "%.2f", FloatArgumentType.getFloat(ctx, "speed")),
                                        e -> new PdimData.Entry(e.gravity(), e.plot(), PdimDimensions.clampDaySpeed(
                                                FloatArgumentType.getFloat(ctx, "speed")), -1.0f, e.orbitView())))))
                        .then(literal("lock").then(argument("tick", FloatArgumentType.floatArg(0.0f, 24000.0f))
                                .executes(ctx -> adjust(ctx.getSource(), "time locked at tick " + (int) FloatArgumentType
                                        .getFloat(ctx, "tick"),
                                        e -> new PdimData.Entry(e.gravity(), e.plot(), e.daySpeed(),
                                                FloatArgumentType.getFloat(ctx, "tick"), e.orbitView())))))
                        .then(literal("unlock").executes(ctx -> adjust(ctx.getSource(), "time running",
                                e -> new PdimData.Entry(e.gravity(), e.plot(), e.daySpeed(), -1.0f, e.orbitView())))))
                .then(literal("leave").executes(ctx -> leave(ctx.getSource())))
                .then(literal("list").executes(ctx -> list(ctx.getSource())))
                .then(literal("delete").then(argument("body", ResourceLocationArgument.id())
                        .executes(ctx -> delete(ctx.getSource(), ResourceLocationArgument.getId(ctx, "body"))))));
    }

    private static int enter(CommandSourceStack source, ResourceLocation id, Float gravity)
                                                                                            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ContinuumBody body = ContinuumData.body(id);
        float g = gravity != null ? gravity : body != null ? PdimDimensions.naturalGravity(body) : 1.0f;
        String error = PdimActions.createAndEnter(player, id, g, false);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    /** Changes a setting of the personal dimension the player is standing in. */
    private static int adjust(CommandSourceStack source, String what,
                              java.util.function.UnaryOperator<PdimData.Entry> change) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation body = PdimDimensions.bodyOf(player.level().dimension());
        if (body == null) {
            source.sendFailure(Component.literal("Stand in your personal dimension to change it."));
            return 0;
        }
        if (PdimData.get(player.server).update(player.getUUID(), body, change) == null) {
            source.sendFailure(Component.literal("You have no personal dimension on " + body + "."));
            return 0;
        }
        PdimActions.sendState(player);
        source.sendSuccess(() -> Component.literal("Set " + what + " (sky changes are shown only to you)."), false);
        return 1;
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        String error = PdimActions.leave(source.getPlayerOrException());
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var entries = PdimData.get(player.server).entries(player.getUUID());
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You have no personal dimensions."), false);
            return 0;
        }
        entries.forEach((body, entry) -> source.sendSuccess(() -> Component.literal(
                body + "  gravity x" + String.format(Locale.ROOT, "%.2f", entry.gravity())), false));
        return entries.size();
    }

    private static int delete(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        boolean removed = PdimActions.delete(source.getPlayerOrException(), id);
        if (!removed) {
            source.sendFailure(Component.literal("You have no personal dimension on " + id + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Forgot your dimension on " + id + "."), false);
        return 1;
    }
}
