package net.phoenix.continuum.common;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.phoenix.continuum.api.ContinuumEvents;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;
import net.phoenix.continuum.network.S2CAnomalyStatePacket;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Investigating an anomaly: something a body's definition says is worth a closer look once a team has found it. Each one
 * can be investigated once per team and pays out items and/or research.
 */
public final class AnomalyActions {

    private AnomalyActions() {}

    /** Returns an error message for the player, or null when it worked. */
    public static @Nullable String investigate(ServerPlayer player, ResourceLocation bodyId, String anomalyId) {
        ContinuumBody body = ContinuumData.body(bodyId);
        if (body == null) return "That body does not exist.";
        ContinuumBody.Anomaly anomaly = null;
        for (ContinuumBody.Anomaly candidate : body.anomalies()) {
            if (candidate.id().equals(anomalyId)) anomaly = candidate;
        }
        if (anomaly == null) return "There is nothing like that on " + body.name() + ".";

        UUID team = ContinuumHost.teamOf(player.getUUID());
        ContinuumTeamData data = ContinuumTeamData.get(player.server);
        DiscoveryStage stage = data.effectiveStage(player.server, team, bodyId);
        if (!stage.atLeast(anomaly.reveal())) return "Your team has not found that yet.";
        if (data.anomalyInvestigated(team, bodyId, anomalyId)) return "Your team has already investigated " + anomaly.name() + ".";

        var cost = anomaly.cost();
        if (cost != null && !player.isCreative()) {
            Item costItem = BuiltInRegistries.ITEM.get(cost.item());
            int have = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(costItem)) have += stack.getCount();
            }
            if (costItem == Items.AIR || have < cost.count()) {
                return "Investigating " + anomaly.name() + " takes " + cost.count() + " x " + cost.item() + " (you have " + have + ").";
            }
            int left = cost.count();
            for (ItemStack stack : player.getInventory().items) {
                if (left <= 0) break;
                if (!stack.is(costItem)) continue;
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }

        if (!data.markAnomalyInvestigated(team, bodyId, anomalyId)) return "Your team has already investigated " + anomaly.name() + ".";

        StringBuilder found = new StringBuilder();
        for (ContinuumBody.Yield yield : anomaly.rewards()) {
            if (player.getRandom().nextFloat() >= yield.chance()) continue;
            Item item = BuiltInRegistries.ITEM.get(yield.item());
            if (item == Items.AIR) continue;
            int count = yield.min() + (yield.max() > yield.min() ? player.getRandom().nextInt(yield.max() - yield.min() + 1) : 0);
            ItemStack stack = new ItemStack(item, count);
            Component name = stack.getHoverName();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            if (found.length() > 0) found.append(", ");
            found.append(count).append(" x ").append(name.getString());
        }

        if (anomaly.research() != null && ContinuumHost.research() == BuiltinResearch.PROVIDER) {
            if (BuiltinResearch.get(player.server).grant(team, anomaly.research())) {
                player.sendSystemMessage(Component.literal("\u00a7bResearch unlocked: \u00a7f" +
                        ResearchGrants.pretty(anomaly.research())));
                ResearchGrants.sweep(player.server, team);
            }
        }

        String text = anomaly.message().isEmpty() ? anomaly.name() + " investigated." : anomaly.message();
        player.sendSystemMessage(Component.literal(anomaly.name() + ": ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
        if (found.length() > 0) {
            player.sendSystemMessage(Component.literal("Recovered: " + found).withStyle(ChatFormatting.GRAY));
        }
        data.log(team, MissionLog.Kind.ANOMALY, bodyId, anomaly.name() + " investigated on " + body.name() + ".");

        ContinuumEvents.postAnomaly(player, team, bodyId, anomalyId);
        sendToTeam(player.server, team);
        ContinuumServerEvents.sendStateToTeam(player.server, team);
        return null;
    }

    public static void send(ServerPlayer player) {
        UUID team = ContinuumHost.teamOf(player.getUUID());
        net.phoenix.continuum.network.ContinuumNetwork.sendToPlayer(player,
                new S2CAnomalyStatePacket(ContinuumTeamData.get(player.server).investigatedAnomalies(team)));
    }

    public static void sendToTeam(net.minecraft.server.MinecraftServer server, UUID team) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ContinuumHost.teams().onTeam(player, team)) send(player);
        }
    }
}
