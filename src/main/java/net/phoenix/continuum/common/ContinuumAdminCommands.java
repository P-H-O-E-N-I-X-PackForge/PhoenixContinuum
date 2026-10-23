package net.phoenix.continuum.common;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.common.Swarms;
import net.phoenix.continuum.data.DiscoveryStage;
import net.phoenix.continuum.item.ContinuumRocketItem;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

final class ContinuumAdminCommands {

    private ContinuumAdminCommands() {}

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTargets(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        java.util.List<ResourceLocation> ids = new java.util.ArrayList<>();
        ContinuumData.systems().forEach(system -> ids.add(system.id()));
        ContinuumData.allBodies().forEach(body -> ids.add(body.id()));
        return net.minecraft.commands.SharedSuggestionProvider.suggestResource(ids, builder);
    }

    private static int setVisibility(CommandSourceStack source, ResourceLocation id, Boolean visible)
                                                                                                      throws CommandSyntaxException {
        if (ContinuumData.body(id) == null && ContinuumData.system(id) == null) {
            source.sendFailure(Component.literal("No body or system " + id + "."));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        UUID team = net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        ContinuumTeamData.get(source.getServer()).setVisibilityOverride(team, id, visible);
        ContinuumServerEvents.onDataReloaded();
        source.sendSuccess(() -> Component.literal(visible == null ? "Cleared the override on " + id :
                (visible ? "Showing " : "Hiding ") + id + " for your team."), true);
        return 1;
    }

    private static int explain(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        var body = ContinuumData.body(id);
        if (body == null) {
            source.sendFailure(Component.literal("No body " + id + "."));
            return 0;
        }
        UUID team = net.phoenix.continuum.api.ContinuumHost.teamOf(source.getPlayerOrException().getUUID());
        for (String line : net.phoenix.continuum.common.visibility.ContinuumVisibility.explain(source.getServer(),
                team, body)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static boolean hasPersonalDimension(CommandSourceStack source, ResourceLocation body) {
        var stems = source.getServer().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM);
        return stems.containsKey(net.phoenix.continuum.pdim.PdimDimensions.keyFor(body).location());
    }

    private static int writePdimFiles(CommandSourceStack source) {
        return writePdimFiles(source, new java.util.ArrayList<>(), null);
    }

    private static int writePdimFiles(CommandSourceStack source, java.util.List<java.nio.file.Path> writtenFiles,
                                      @org.jetbrains.annotations.Nullable String onlyNamespace) {
        java.nio.file.Path root = net.phoenix.continuum.data.ContinuumPackFolder.folder().resolve("data")
                .resolve("phoenix_continuum").resolve("dimension").resolve("pdim");
        String template = """
                {
                  "type": "phoenix_continuum:pdim",
                  "generator": {
                    "type": "minecraft:flat",
                    "settings": {
                      "biome": "minecraft:the_void",
                      "features": false,
                      "lakes": false,
                      "layers": [],
                      "structure_overrides": []
                    }
                  }
                }
                """;
        int written = 0;
        try {
            java.nio.file.Files.createDirectories(root);
            for (var body : ContinuumData.allBodies()) {
                if (onlyNamespace != null && !body.id().getNamespace().equals(onlyNamespace)) continue;
                if (hasPersonalDimension(source, body.id())) continue;
                
                java.nio.file.Path file = root.resolve(body.id().getPath() + ".json");
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, template);
                writtenFiles.add(file);
                written++;
            }
        } catch (java.io.IOException e) {
            source.sendFailure(Component.literal("Could not write the files: " + e.getMessage()));
            return 0;
        }
        int count = written;
        source.sendSuccess(() -> Component.literal(count == 0 ? "Every body already has a personal dimension." :
                "Wrote " + count + " personal dimension file(s) to " + root + ". Restart the server to enable them."),
                false);
        return written;
    }

    private static final int SHOWN_ISSUES = 12;

    private static int validate(CommandSourceStack source, boolean all) {
        var report = net.phoenix.continuum.data.ContinuumReport.last();
        var issues = new java.util.ArrayList<>(report.issues());
        for (var body : ContinuumData.allBodies()) {
            if (!hasPersonalDimension(source, body.id())) {
                issues.add(new net.phoenix.continuum.data.ContinuumReport.Issue(
                        net.phoenix.continuum.data.ContinuumReport.Level.WARNING, "body " + body.id(),
                        "has no personal dimension (run /continuumadmin pdimfiles, then restart)"));
            }
        }
        if (issues.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Continuum data: no problems found.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Continuum data: " + report.errors() + " errors, " +
                report.warnings() + " warnings" + (all || issues.size() <= SHOWN_ISSUES ? "" :
                        " (first " + SHOWN_ISSUES + "; /continuumadmin validate all for the rest)"))
                .withStyle(report.errors() > 0 ? ChatFormatting.RED : ChatFormatting.YELLOW), false);
        int shown = 0;
        for (var issue : issues) {
            if (!all && shown++ >= SHOWN_ISSUES) break;
            source.sendSuccess(() -> Component.literal(issue.toString()).withStyle(
                    issue.level() == net.phoenix.continuum.data.ContinuumReport.Level.ERROR ? ChatFormatting.RED :
                            ChatFormatting.GOLD),
                    false);
        }
        return issues.size();
    }

    private static int reloadData(CommandSourceStack source) {
        var report = ContinuumData.reloadNow(source.getServer());
        source.sendSuccess(() -> Component.literal("Reloaded Continuum data: " + ContinuumData.systems().size() +
                " systems, " + ContinuumData.allBodies().size() + " bodies, " + report.errors() + " errors, " +
                report.warnings() + " warnings. /continuumadmin validate lists them.")
                .withStyle(report.errors() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN), true);
        return 1;
    }

    private static int dump(CommandSourceStack source, ResourceLocation id) {
        String json = ContinuumData.snapshot().bodies().get(id);
        if (json == null) {
            source.sendFailure(Component.literal("No body " + id + "."));
            return 0;
        }
        String pretty = new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                .toJson(com.google.gson.JsonParser.parseString(json));
        net.phoenix.continuum.PhoenixContinuum.LOGGER.info("[Continuum] resolved {} {}", id, pretty);
        source.sendSuccess(() -> Component.literal(pretty), false);
        return 1;
    }

    private static final int MAX_GENERATED = 64;

    /**
     * What a generate (or clear) replaced, so it can be put back. Kept in memory and tied to the running server: once
     * the world is closed, the undo is gone.
     */
    private record GenerateUndo(net.minecraft.server.MinecraftServer server, GeneratedGalaxy.Setup previous) {}

    private static GenerateUndo lastGenerate;

    /** Called when the world closes: the undo belongs to it. */
    static void forgetUndo() {
        lastGenerate = null;
    }

    private static java.nio.file.Path pdimDirectory() {
        return net.phoenix.continuum.data.ContinuumPackFolder.folder().resolve("data").resolve("phoenix_continuum")
                .resolve("dimension").resolve("pdim");
    }

    private static void deleteTree(java.nio.file.Path root) throws java.io.IOException {
        if (!java.nio.file.Files.exists(root)) return;
        try (var walk = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) walk.sorted(
                    java.util.Comparator.reverseOrder())::iterator) {
                java.nio.file.Files.delete(path);
            }
        }
    }

    /**
     * Personal dimensions are registered from files, once, when the world loads, so the generated worlds need their
     * files before a restart, and another world's leftovers should not keep costing a dimension each. This writes the
     * files this world's generated worlds need and removes generated ones it does not. Returns how many changed.
     */
    static int reconcileGeneratedDimensions(net.minecraft.server.MinecraftServer server) {
        String namespace = net.phoenix.continuum.data.SystemGenerator.NAMESPACE;
        int changed = writePdimFiles(server.createCommandSourceStack(), new java.util.ArrayList<>(), namespace);

        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (var body : ContinuumData.allBodies()) {
            if (body.id().getNamespace().equals(namespace)) wanted.add(body.id().getPath() + ".json");
        }
        java.nio.file.Path dir = pdimDirectory();
        if (java.nio.file.Files.isDirectory(dir)) {
            try (var files = java.nio.file.Files.list(dir)) {
                for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator) {
                    String name = file.getFileName().toString();
                    if (name.startsWith("gen_") && !wanted.contains(name)) {
                        java.nio.file.Files.delete(file);
                        changed++;
                    }
                }
            } catch (java.io.IOException e) {
                net.phoenix.continuum.PhoenixContinuum.LOGGER.warn("Could not tidy generated dimension files: {}",
                        e.getMessage());
            }
        }
        return changed;
    }

    /** At world start: load this world's galaxy (it is generated from its saved seed) and settle the dimension files. */
    static void onServerStarted(net.minecraft.server.MinecraftServer server) {
        try {
            // an earlier version wrote generated systems into the shared pack folder; they belong to a world now
            deleteTree(net.phoenix.continuum.data.ContinuumPackFolder.folder().resolve("data")
                    .resolve(net.phoenix.continuum.data.SystemGenerator.NAMESPACE));
        } catch (java.io.IOException e) {
            net.phoenix.continuum.PhoenixContinuum.LOGGER.warn("Could not remove the old generated pack files: {}",
                    e.getMessage());
        }
        if (GeneratedGalaxy.get(server).setup().active()) ContinuumData.reloadNow(server);
        int changed = reconcileGeneratedDimensions(server);
        if (changed > 0) {
            net.phoenix.continuum.PhoenixContinuum.LOGGER.warn("[Continuum] {} generated dimension file(s) changed " +
                    "for this world: restart the server once for the generated worlds' personal dimensions.", changed);
        }
    }

    private static long generatedBodyCount() {
        return ContinuumData.allBodies().stream()
                .filter(body -> body.id().getNamespace().equals(net.phoenix.continuum.data.SystemGenerator.NAMESPACE))
                .count();
    }

    private static int generateSystems(CommandSourceStack source, int count,
                                       @org.jetbrains.annotations.Nullable Long seedArg) {
        var server = source.getServer();
        long seed = seedArg != null ? seedArg : server.overworld().getSeed();
        GeneratedGalaxy data = GeneratedGalaxy.get(server);
        lastGenerate = new GenerateUndo(server, data.setup());
        data.set(new GeneratedGalaxy.Setup(true, seed, count));

        var report = ContinuumData.reloadNow(server);
        int changed = reconcileGeneratedDimensions(server);
        source.sendSuccess(() -> Component.literal("Generated " + count + " systems and " + generatedBodyCount() +
                " worlds from seed " + seed + " for this world (" + report.errors() + " errors). " +
                (changed > 0 ? "Their personal dimensions need a server restart to exist. " : "") +
                "/continuumadmin generate undo puts back what was there before (until you leave this world); " +
                "generate clear removes them.").withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int clearGenerated(CommandSourceStack source) {
        var server = source.getServer();
        GeneratedGalaxy data = GeneratedGalaxy.get(server);
        lastGenerate = new GenerateUndo(server, data.setup());
        data.set(GeneratedGalaxy.Setup.NONE);
        ContinuumData.reloadNow(server);
        reconcileGeneratedDimensions(server);
        source.sendSuccess(() -> Component.literal("Removed this world's generated systems. generate undo brings " +
                "them back (until you leave this world)."), true);
        return 1;
    }

    private static int undoGenerate(CommandSourceStack source) {
        GenerateUndo undo = lastGenerate;
        if (undo == null || undo.server() != source.getServer()) {
            source.sendFailure(Component.literal("Nothing to undo in this world."));
            return 0;
        }
        GeneratedGalaxy.get(source.getServer()).set(undo.previous());
        lastGenerate = null;
        ContinuumData.reloadNow(source.getServer());
        int changed = reconcileGeneratedDimensions(source.getServer());
        source.sendSuccess(() -> Component.literal("Undid the last generate: this world's systems are as they were." +
                (changed > 0 ? " Restart the server to match the personal dimensions." : "")), true);
        return 1;
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("continuumadmin")
                .requires(source -> source.hasPermission(2))
                .then(literal("rocket")
                        .then(literal("give").executes(ctx -> giveRocket(ctx.getSource())))
                        .then(literal("repair").executes(ctx -> setWear(ctx.getSource(), 0.0f)))
                        .then(literal("tier")
                                .then(argument("tier", IntegerArgumentType.integer(1, RocketStats.MAX_TIER))
                                        .executes(ctx -> setTier(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "tier")))))
                        .then(literal("wear")
                                .then(argument("value", FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(ctx -> setWear(ctx.getSource(),
                                                FloatArgumentType.getFloat(ctx, "value")))))
                        .then(literal("upgrade")
                                .then(argument("upgrade", StringArgumentType.word())
                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                                .suggest(List.of(RocketStats.HULL_INTERLOCK, RocketStats.OVERDRIVE,
                                                        RocketStats.REINFORCED_HULL), builder))
                                        .then(argument("level", IntegerArgumentType.integer(0, 10))
                                                .executes(ctx -> upgrade(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "upgrade"),
                                                        IntegerArgumentType.getInteger(ctx, "level")))))))
                .then(literal("research")
                        .then(literal("grant")
                                .then(argument("node", ResourceLocationArgument.id())
                                        .executes(ctx -> research(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "node"), true))))
                        .then(literal("revoke")
                                .then(argument("node", ResourceLocationArgument.id())
                                        .executes(ctx -> research(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "node"), false))))
                        .then(literal("list").executes(ctx -> listResearch(ctx.getSource())))
                        .then(literal("flag")
                                .then(argument("name", StringArgumentType.word())
                                        .then(literal("on").executes(ctx -> flag(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"), true)))
                                        .then(literal("off").executes(ctx -> flag(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"), false)))))
                        .then(literal("discipline")
                                .then(argument("name", StringArgumentType.word())
                                        .executes(ctx -> discipline(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(literal("discover")
                        .then(literal("all")
                                .then(argument("stage", StringArgumentType.word())
                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                                .suggest(List.of("unknown", "detected", "surveyed"), builder))
                                        .executes(ctx -> discoverAll(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "stage")))))
                        .then(argument("target", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> {
                                    ContinuumData.systems().forEach(s -> builder.suggest(s.id().toString()));
                                    ContinuumData.allBodies().forEach(b -> builder.suggest(b.id().toString()));
                                    return builder.buildFuture();
                                })
                                .then(argument("stage", StringArgumentType.word())
                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                                .suggest(List.of("unknown", "detected", "surveyed"), builder))
                                        .executes(ctx -> discover(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "target"),
                                                StringArgumentType.getString(ctx, "stage"))))))
                .then(literal("outpost")
                        .then(literal("grant")
                                .then(argument("body", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> {
                                            ContinuumData.allBodies().forEach(b -> builder.suggest(b.id().toString()));
                                            return builder.buildFuture();
                                        })
                                        .then(argument("probes", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> grantOutpost(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "body"),
                                                        IntegerArgumentType.getInteger(ctx, "probes"))))))
                        .then(literal("fill").executes(ctx -> fillOutposts(ctx.getSource())))
                        .then(literal("damage").executes(ctx -> damageOutposts(ctx.getSource(), true)))
                        .then(literal("repair").executes(ctx -> damageOutposts(ctx.getSource(), false)))
                        .then(literal("clear").executes(ctx -> clearOutposts(ctx.getSource()))))
                .then(literal("swarm")
                        .then(literal("add").then(swarmBody(false, (source, body, levels) ->
                                swarmLevels(source, body, levels, false))))
                        .then(literal("remove").then(swarmBody(false, (source, body, levels) ->
                                swarmLevels(source, body, -levels, false))))
                        .then(literal("set").then(swarmBody(true, (source, body, levels) ->
                                swarmLevels(source, body, levels, true)))))
                .then(literal("comet")
                        .then(literal("spawn")
                                .then(argument("body", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> {
                                            ContinuumData.allBodies().stream()
                                                    .filter(b -> b.transientInfo() != null)
                                                    .forEach(b -> builder.suggest(b.id().toString()));
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> cometSpawn(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "body"), -1))
                                        .then(argument("minutes", IntegerArgumentType.integer(1, 10080))
                                                .executes(ctx -> cometSpawn(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "body"),
                                                        IntegerArgumentType.getInteger(ctx, "minutes"))))))
                        .then(literal("clear")
                                .then(argument("body", ResourceLocationArgument.id())
                                        .executes(ctx -> cometClear(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "body"))))))
                .then(literal("generate")
                        .then(literal("clear").executes(ctx -> clearGenerated(ctx.getSource())))
                        .then(literal("undo").executes(ctx -> undoGenerate(ctx.getSource())))
                        .then(argument("count", IntegerArgumentType.integer(1, MAX_GENERATED))
                                .executes(ctx -> generateSystems(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count"), null))
                                .then(argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                        .executes(ctx -> generateSystems(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx,
                                                        "seed"))))))
                .then(literal("show").then(argument("target", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> suggestTargets(builder))
                        .executes(ctx -> setVisibility(ctx.getSource(),
                                ResourceLocationArgument.getId(ctx, "target"), Boolean.TRUE))))
                .then(literal("hide").then(argument("target", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> suggestTargets(builder))
                        .executes(ctx -> setVisibility(ctx.getSource(),
                                ResourceLocationArgument.getId(ctx, "target"), Boolean.FALSE))))
                .then(literal("unset").then(argument("target", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> suggestTargets(builder))
                        .executes(ctx -> setVisibility(ctx.getSource(),
                                ResourceLocationArgument.getId(ctx, "target"), null))))
                .then(literal("explain").then(argument("body", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                .suggestResource(ContinuumData.allBodies().stream()
                                        .map(net.phoenix.continuum.data.ContinuumBody::id), builder))
                        .executes(ctx -> explain(ctx.getSource(), ResourceLocationArgument.getId(ctx, "body")))))
                .then(literal("validate").executes(ctx -> validate(ctx.getSource(), false))
                        .then(literal("all").executes(ctx -> validate(ctx.getSource(), true))))
                .then(literal("reload").executes(ctx -> reloadData(ctx.getSource())))
                .then(literal("pdimfiles").executes(ctx -> writePdimFiles(ctx.getSource())))
                .then(literal("dump")
                        .then(argument("body", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggestResource(ContinuumData.allBodies().stream()
                                                .map(net.phoenix.continuum.data.ContinuumBody::id), builder))
                                .executes(ctx -> dump(ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "body")))))
                .then(literal("reset").executes(ctx -> reset(ctx.getSource())))
                .then(literal("missions")
                        .then(literal("send")
                                .then(argument("body", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> {
                                            ContinuumData.allBodies().forEach(b -> builder.suggest(b.id().toString()));
                                            return builder.buildFuture();
                                        })
                                        .then(argument("type", StringArgumentType.word())
                                                .suggests((ctx,
                                                           builder) -> net.minecraft.commands.SharedSuggestionProvider
                                                                   .suggest(List.of("survey", "extract", "deploy",
                                                                           "haul", "repair",
                                                                           "rescue", "station"), builder))
                                                .then(argument("seconds", IntegerArgumentType.integer(1, 604800))
                                                        .executes(ctx -> sendMission(ctx.getSource(),
                                                                ResourceLocationArgument.getId(ctx, "body"),
                                                                StringArgumentType.getString(ctx, "type"),
                                                                IntegerArgumentType.getInteger(ctx, "seconds"), true,
                                                                1))
                                                        .then(argument("outcome", StringArgumentType.word())
                                                                .suggests(
                                                                        (ctx,
                                                                         builder) -> net.minecraft.commands.SharedSuggestionProvider
                                                                                 .suggest(List.of("success", "fail"),
                                                                                         builder))
                                                                .executes(ctx -> sendMission(ctx.getSource(),
                                                                        ResourceLocationArgument.getId(ctx, "body"),
                                                                        StringArgumentType.getString(ctx, "type"),
                                                                        IntegerArgumentType.getInteger(ctx, "seconds"),
                                                                        !"fail".equals(StringArgumentType
                                                                                .getString(ctx, "outcome")),
                                                                        1))
                                                                .then(argument("count",
                                                                        IntegerArgumentType.integer(1, 32))
                                                                        .executes(ctx -> sendMission(ctx.getSource(),
                                                                                ResourceLocationArgument.getId(ctx,
                                                                                        "body"),
                                                                                StringArgumentType.getString(ctx,
                                                                                        "type"),
                                                                                IntegerArgumentType.getInteger(ctx,
                                                                                        "seconds"),
                                                                                !"fail".equals(StringArgumentType
                                                                                        .getString(ctx, "outcome")),
                                                                                IntegerArgumentType.getInteger(ctx,
                                                                                        "count")))))))))
                        .then(literal("strand")
                                .then(argument("body", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> {
                                            ContinuumData.allBodies().forEach(b -> builder.suggest(b.id().toString()));
                                            return builder.buildFuture();
                                        })
                                        .then(argument("seconds", IntegerArgumentType.integer(1, 604800))
                                                .executes(ctx -> strandMission(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "body"),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"))))))
                        .then(literal("time")
                                .then(argument("seconds", IntegerArgumentType.integer(0, 604800))
                                        .executes(ctx -> setRemaining(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(literal("finish").executes(ctx -> finishMissions(ctx.getSource())))
                        .then(literal("clear").executes(ctx -> clearMissions(ctx.getSource())))));
    }

    private static UUID team(CommandSourceStack source) throws CommandSyntaxException {
        return net.phoenix.continuum.api.ContinuumHost.teamOf(source.getPlayerOrException().getUUID());
    }

    private static int giveRocket(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ContinuumRegistry.ROCKET.get()));
        source.sendSuccess(() -> Component.literal("Gave a Continuum Rocket."), false);
        return 1;
    }

    private static ItemStack heldRocket(CommandSourceStack source) throws CommandSyntaxException {
        ItemStack stack = source.getPlayerOrException().getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof ContinuumRocketItem)) {
            source.sendFailure(Component.literal("Hold a Continuum Rocket in your main hand."));
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static int cometSpawn(CommandSourceStack source, ResourceLocation bodyId, int minutes) {
        var body = ContinuumData.body(bodyId);
        if (body == null || body.transientInfo() == null) {
            source.sendFailure(Component.literal(bodyId + " is not a comet."));
            return 0;
        }
        int length = minutes > 0 ? minutes : body.transientInfo().durationMinutes();
        ContinuumTransients.get(source.getServer()).open(source.getServer(), body, length);
        return 1;
    }

    private static int cometClear(CommandSourceStack source, ResourceLocation bodyId) {
        var body = ContinuumData.body(bodyId);
        if (body == null || body.transientInfo() == null) {
            source.sendFailure(Component.literal(bodyId + " is not a comet."));
            return 0;
        }
        ContinuumTransients.get(source.getServer()).close(source.getServer(), body);
        source.sendSuccess(() -> Component.literal(body.name() + " has left."), false);
        return 1;
    }

    private static int setTier(CommandSourceStack source, int tier) throws CommandSyntaxException {
        ItemStack rocket = heldRocket(source);
        if (rocket.isEmpty()) return 0;
        RocketStats.setTier(rocket, tier);
        source.sendSuccess(() -> Component.literal("Rocket hull set to " + RocketStats.tierName(tier) + "."), false);
        return 1;
    }

    private static int setWear(CommandSourceStack source, float wear) throws CommandSyntaxException {
        ItemStack rocket = heldRocket(source);
        if (rocket.isEmpty()) return 0;
        RocketStats.setWear(rocket, wear);
        source.sendSuccess(() -> Component.literal("Rocket wear set to " + Math.round(wear * 100) + "%."), false);
        return 1;
    }

    private static int upgrade(CommandSourceStack source, String upgrade, int level) throws CommandSyntaxException {
        ItemStack rocket = heldRocket(source);
        if (rocket.isEmpty()) return 0;

        String id = upgrade.toLowerCase(Locale.ROOT);
        if (!RocketStats.isKnownUpgrade(id)) {
            source.sendFailure(Component.literal("Unknown upgrade. Use hull_interlock, overdrive or reinforced_hull."));
            return 0;
        }
        RocketStats.setLevel(rocket, id, level);
        source.sendSuccess(() -> Component.literal("Set " + id + " to level " + level + "."), false);
        return 1;
    }

    private static int research(CommandSourceStack source, ResourceLocation node, boolean grant)
                                                                                                 throws CommandSyntaxException {
        UUID team = team(source);
        boolean changed = grant ? net.phoenix.continuum.api.ContinuumAPI.grantResearch(team, node.toString()) :
                net.phoenix.continuum.api.ContinuumAPI.revokeResearch(team, node.toString());
        source.sendSuccess(() -> Component.literal(changed ? (grant ? "Unlocked " : "Removed ") + node :
                "Nothing changed for " + node), false);
        return changed ? 1 : 0;
    }

    private static int listResearch(CommandSourceStack source) throws CommandSyntaxException {
        var nodes = net.phoenix.continuum.common.BuiltinResearch.get(source.getServer()).unlockedOf(team(source));
        source.sendSuccess(() -> Component.literal(nodes.isEmpty() ? "No research unlocked." :
                "Unlocked: " + nodes.stream().map(ResourceLocation::toString).collect(
                        java.util.stream.Collectors.joining(", "))), false);
        return nodes.size();
    }

    private static int flag(CommandSourceStack source, String name, boolean on) throws CommandSyntaxException {
        net.phoenix.continuum.api.ContinuumAPI.setResearchFlag(team(source), name, on);
        source.sendSuccess(() -> Component.literal("Flag " + name + " is " + (on ? "on" : "off") + "."), false);
        return 1;
    }

    private static int discipline(CommandSourceStack source, String name) throws CommandSyntaxException {
        net.phoenix.continuum.api.ContinuumAPI.setDiscipline(team(source), name.equals("none") ? "" : name);
        source.sendSuccess(() -> Component.literal("Discipline is now " + name + "."), false);
        return 1;
    }

    private static int discover(CommandSourceStack source, ResourceLocation target, String stageName)
                                                                                                      throws CommandSyntaxException {
        if (ContinuumData.body(target) == null && ContinuumData.system(target) == null) {
            source.sendFailure(Component.literal("No such system or body: " + target));
            return 0;
        }
        DiscoveryStage stage = DiscoveryStage.parse(stageName, null);
        if (stage == null) {
            source.sendFailure(Component.literal("Stage must be unknown, detected or surveyed."));
            return 0;
        }

        UUID team = team(source);
        ContinuumTeamData.get(source.getServer()).force(team, target, stage);
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        source.sendSuccess(() -> Component.literal(target + " is now " + stage.name().toLowerCase(Locale.ROOT) + "."),
                false);
        return 1;
    }

    /** Sets every system and body to one stage for your team (the tedious way to test is one command per target). */
    private static int discoverAll(CommandSourceStack source, String stageName) throws CommandSyntaxException {
        DiscoveryStage stage = DiscoveryStage.parse(stageName, null);
        if (stage == null) {
            source.sendFailure(Component.literal("Stage must be unknown, detected or surveyed."));
            return 0;
        }

        UUID team = team(source);
        ContinuumTeamData data = ContinuumTeamData.get(source.getServer());
        int count = 0;
        for (var system : ContinuumData.systems()) {
            data.force(team, system.id(), stage);
            count++;
        }
        for (var body : ContinuumData.allBodies()) {
            data.force(team, body.id(), stage);
            count++;
        }
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        int total = count;
        source.sendSuccess(() -> Component.literal("All " + total + " systems and bodies are now " +
                stage.name().toLowerCase(Locale.ROOT) + "."), false);
        return total;
    }

    @FunctionalInterface
    private interface SwarmAction {

        int run(CommandSourceStack source, ResourceLocation body, int levels) throws CommandSyntaxException;
    }

    /** The body and level arguments shared by swarm add, remove and set. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> swarmBody(
                                                                                                                      boolean allowZero,
                                                                                                                      SwarmAction action) {
        return argument("body", ResourceLocationArgument.id())
                .suggests((ctx, builder) -> {
                    ContinuumData.allBodies().stream().filter(b -> Swarms.isSwarm(b))
                            .forEach(b -> builder.suggest(b.id().toString()));
                    return builder.buildFuture();
                })
                .then(argument("levels", IntegerArgumentType.integer(allowZero ? 0 : 1, 1000))
                        .executes(ctx -> action.run(ctx.getSource(), ResourceLocationArgument.getId(ctx, "body"),
                                IntegerArgumentType.getInteger(ctx, "levels"))));
    }

    /** Changes the level of your team's swarm (or station) at a body, within what that body allows. */
    private static int swarmLevels(CommandSourceStack source, ResourceLocation id, int levels,
                                   boolean absolute) throws CommandSyntaxException {
        var body = ContinuumData.body(id);
        if (body == null) {
            source.sendFailure(Component.literal("No such body: " + id));
            return 0;
        }
        UUID team = team(source);
        ContinuumTeamData data = ContinuumTeamData.get(source.getServer());
        int max = Swarms.maxLevel(body);
        int now = data.stationLevel(team, id);
        int next = Math.max(0, Math.min(max, absolute ? levels : now + levels));
        data.setStationLevel(team, id, next);
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        ContinuumServerEvents.sendStateToTeam(source.getServer(), team);
        String name = Swarms.kindName(body);
        String power = Swarms.isSwarm(body) && next > 0 ? " (" + Swarms.describe(body, next) + ")" : "";
        source.sendSuccess(() -> Component.literal(name + " at " + body.name() + " is now level " + next + " of " + max +
                power + "."), false);
        return next;
    }

    private static int grantOutpost(CommandSourceStack source, ResourceLocation body, int probes)
                                                                                                  throws CommandSyntaxException {
        if (ContinuumData.body(body) == null) {
            source.sendFailure(Component.literal("No such body: " + body));
            return 0;
        }
        UUID team = team(source);
        ContinuumTeamData.get(source.getServer()).deployProbes(team, body, probes, System.currentTimeMillis());
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        source.sendSuccess(() -> Component.literal("Added " + probes + " probe(s) to the outpost on " + body + "."),
                false);
        return 1;
    }

    private static int fillOutposts(CommandSourceStack source) throws CommandSyntaxException {
        UUID team = team(source);
        ContinuumTeamData data = ContinuumTeamData.get(source.getServer());
        long cycle = RocketStats.outpostCycleMillis();
        int max = RocketStats.outpostMaxReady();
        long longAgo = System.currentTimeMillis() - cycle * max;
        int count = 0;
        for (Outpost outpost : data.outposts(team).values()) {
            outpost.claim(longAgo);
            outpost.settle(System.currentTimeMillis(), cycle, max, Outpost.FREE);
            count++;
        }
        data.setDirty();
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        int filled = count;
        source.sendSuccess(() -> Component.literal("Filled " + filled + " outpost(s)."), false);
        return count;
    }

    private static int damageOutposts(CommandSourceStack source, boolean damage) throws CommandSyntaxException {
        UUID team = team(source);
        ContinuumTeamData data = ContinuumTeamData.get(source.getServer());
        int count = 0;
        for (Outpost outpost : data.outposts(team).values()) {
            if (damage) outpost.damage();
            else outpost.repair(System.currentTimeMillis());
            count++;
        }
        data.setDirty();
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        int changed = count;
        source.sendSuccess(() -> Component.literal((damage ? "Damaged " : "Repaired ") + changed + " outpost(s)."),
                false);
        return count;
    }

    private static int clearOutposts(CommandSourceStack source) throws CommandSyntaxException {
        UUID team = team(source);
        ContinuumTeamData.get(source.getServer()).clearOutposts(team);
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        source.sendSuccess(() -> Component.literal("All outposts removed."), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) throws CommandSyntaxException {
        UUID team = team(source);
        ContinuumTeamData.get(source.getServer()).resetDiscovery(team);
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        source.sendSuccess(() -> Component.literal("Discovery reset to the starting state."), false);
        return 1;
    }

    private static int sendMission(CommandSourceStack source, ResourceLocation bodyId, String typeName, int seconds,
                                   boolean succeeds, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var body = ContinuumData.body(bodyId);
        if (body == null) {
            source.sendFailure(Component.literal("Unknown body " + bodyId));
            return 0;
        }
        Mission.Type type;
        try {
            type = Mission.Type.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown mission type " + typeName));
            return 0;
        }

        UUID team = team(source);
        ContinuumTeamData data = ContinuumTeamData.get(source.getServer());
        long now = System.currentTimeMillis();
        int probes = switch (type) {
            case EXTRACT, DEPLOY -> 3;
            case STATION -> Math.max(1,
                    net.phoenix.continuum.config.ContinuumConfigs.INSTANCE.stationProbesPerLevel);
            case HAUL -> 4;
            case REPAIR, RESCUE -> 1;
            case SURVEY -> 0;
        };
        for (int i = 0; i < count; i++) {

            if (type == Mission.Type.RESCUE) {
                Mission waiting = null;
                for (Mission other : data.missions(team)) {
                    if (other.stranded() && !other.rescuing && other.destination.equals(bodyId) &&
                            (waiting == null || other.distressUntil < waiting.distressUntil)) {
                        waiting = other;
                    }
                }
                if (waiting != null) waiting.rescuing = true;
            }
            java.util.List<ItemStack> rewards = new java.util.ArrayList<>();
            if (succeeds && (type == Mission.Type.EXTRACT || type == Mission.Type.HAUL)) {
                rewards = ContinuumMissions.rollRewards(team, body, type == Mission.Type.HAUL ? 8 : probes,
                        player.getRandom());
            }
            data.addMission(new Mission(UUID.randomUUID(), team, player.getUUID(), player.getGameProfile().getName(),
                    bodyId, type, probes, rewards, now, seconds * 1000L, succeeds, 0.05f,
                    new ItemStack(ContinuumRegistry.ROCKET.get()), Mission.State.ACTIVE, new java.util.ArrayList<>()));
        }
        data.setDirty();
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        source.sendSuccess(() -> Component.literal("Sent " + count + " " + type.label().toLowerCase(Locale.ROOT) +
                (count == 1 ? "" : "s") + " to " + body.name() + " (" + seconds + "s, " +
                (succeeds ? "will succeed" : "will fail") + ")."), false);
        return count;
    }

    private static int strandMission(CommandSourceStack source, ResourceLocation bodyId, int seconds)
                                                                                                      throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var body = ContinuumData.body(bodyId);
        if (body == null) {
            source.sendFailure(Component.literal("Unknown body " + bodyId));
            return 0;
        }
        UUID team = team(source);
        ContinuumTeamData data = ContinuumTeamData.get(source.getServer());
        long now = System.currentTimeMillis();
        Mission mission = new Mission(UUID.randomUUID(), team, player.getUUID(), player.getGameProfile().getName(),
                bodyId, Mission.Type.EXTRACT, 3, new java.util.ArrayList<>(), now, 1L, false, 0.1f,
                new ItemStack(ContinuumRegistry.ROCKET.get()), Mission.State.FAILED, new java.util.ArrayList<>());
        mission.distressUntil = now + seconds * 1000L;
        data.addMission(mission);
        data.setDirty();
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        source.sendSuccess(() -> Component.literal("A rocket is stranded at " + body.name() + "; its signal fades in " +
                seconds + "s."), false);
        return 1;
    }

    private static int setRemaining(CommandSourceStack source, int seconds) throws CommandSyntaxException {
        UUID team = team(source);
        ContinuumTeamData data = ContinuumTeamData.get(source.getServer());
        long now = System.currentTimeMillis();
        int count = 0;
        for (Mission mission : data.missions(team)) {
            if (mission.state != Mission.State.ACTIVE) continue;

            mission.durationMillis = Math.max(1L, now - mission.startMillis + seconds * 1000L);
            count++;
        }
        data.setDirty();
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        int changed = count;
        source.sendSuccess(() -> Component.literal(changed + " running mission(s) will land in " + seconds + "s."),
                false);
        return count;
    }

    private static int finishMissions(CommandSourceStack source) throws CommandSyntaxException {
        UUID team = team(source);
        ContinuumTeamData data = ContinuumTeamData.get(source.getServer());
        int count = 0;
        for (Mission mission : List.copyOf(data.missions(team))) {
            if (mission.state == Mission.State.ACTIVE) {

                mission.startMillis = 0L;
                mission.durationMillis = 1L;
                count++;
            }
        }
        ContinuumMissions.resolveDue(source.getServer());
        int finished = count;
        source.sendSuccess(() -> Component.literal("Landed " + finished + " mission(s)."), false);
        return count;
    }

    private static int clearMissions(CommandSourceStack source) throws CommandSyntaxException {
        UUID team = team(source);
        ContinuumTeamData data = ContinuumTeamData.get(source.getServer());
        int count = data.missions(team).size();
        for (Mission mission : List.copyOf(data.missions(team))) data.removeMission(team, mission.id);
        ContinuumServerEvents.engageTeam(source.getServer(), team);
        source.sendSuccess(() -> Component.literal("Removed " + count + " mission(s) (rockets are gone).")
                .withStyle(ChatFormatting.YELLOW), false);
        return count;
    }
}
