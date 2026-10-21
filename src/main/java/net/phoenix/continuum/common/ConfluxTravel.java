package net.phoenix.continuum.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;

import org.jetbrains.annotations.Nullable;

public final class ConfluxTravel {

    private ConfluxTravel() {}

    private static final String RETURN_KEY = "phoenixcore_conflux_return";

    public static @Nullable String disciplineOf(ContinuumBody body) {
        String discipline = body.discipline();
        if (discipline == null) return null;
        return body.id().getPath().equals("conflux_" + discipline) ? discipline : null;
    }

    public static boolean isConfluxDimension(ResourceKey<Level> dimension) {
        ResourceLocation id = dimension.location();
        return "phoenixcore".equals(id.getNamespace()) && id.getPath().startsWith("conflux/");
    }

    public static @Nullable String travel(ServerPlayer player, ResourceLocation bodyId) {
        MinecraftServer server = player.server;
        ContinuumBody body = ContinuumData.body(bodyId);
        String discipline = body == null ? null : disciplineOf(body);
        if (discipline == null) return "That world is not a Conflux dimension.";

        var team = net.phoenix.continuum.api.ContinuumHost.teamOf(player.getUUID());
        String chosen = net.phoenix.continuum.api.ContinuumHost.research(server.overworld()).getDiscipline(team);
        if (chosen == null || !chosen.equalsIgnoreCase(discipline)) return "That is not your discipline's world.";

        DiscoveryStage stage = ContinuumTeamData.get(server).effectiveStage(server, team, bodyId);
        if (!stage.atLeast(DiscoveryStage.SURVEYED)) return "Survey " + body.name() + " before travelling there.";

        net.phoenix.continuum.api.ContinuumHost.Travel travel = net.phoenix.continuum.api.ContinuumHost.travel();
        if (travel == null) return "Discipline worlds are not available here.";
        ResourceKey<Level> target = travel.dimensionOf(discipline);
        if (target == null || server.getLevel(target) == null) return body.name() + " is not available in this world.";
        if (player.level().dimension().equals(target)) return "You are already on " + body.name() + ".";

        if (!isConfluxDimension(player.level().dimension())) {
            CompoundTag back = new CompoundTag();
            back.putString("Dimension", player.level().dimension().location().toString());
            back.putDouble("X", player.getX());
            back.putDouble("Y", player.getY());
            back.putDouble("Z", player.getZ());
            back.putFloat("Yaw", player.getYRot());
            back.putFloat("Pitch", player.getXRot());
            player.getPersistentData().put(RETURN_KEY, back);
        }
        return travel.visit(player, discipline) ? null :
                body.name() + " is not available in this world.";
    }

    public static @Nullable String back(ServerPlayer player) {
        CompoundTag back = player.getPersistentData().getCompound(RETURN_KEY);
        if (back.isEmpty()) return "You have nowhere to return to.";

        ServerLevel level = player.server.getLevel(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(back.getString("Dimension"))));
        if (level == null) return "The place you left from is gone.";

        player.fallDistance = 0.0f;
        player.teleportTo(level, back.getDouble("X"), back.getDouble("Y"), back.getDouble("Z"), back.getFloat("Yaw"),
                back.getFloat("Pitch"));
        player.getPersistentData().remove(RETURN_KEY);
        return null;
    }
}
