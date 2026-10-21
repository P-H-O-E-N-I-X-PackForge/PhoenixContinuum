package net.phoenix.continuum.pdim;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.phoenix.continuum.common.ContinuumTeamData;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;
import net.phoenix.continuum.network.S2CPdimStatePacket;
import net.phoenix.core.utils.TeamUtils;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class PdimActions {

    private PdimActions() {}

    public static @Nullable String createAndEnter(ServerPlayer player, ResourceLocation bodyId, float gravity) {
        MinecraftServer server = player.server;
        ContinuumBody body = ContinuumData.body(bodyId);
        if (body == null) return "That body does not exist.";

        ServerLevel target = server.getLevel(PdimDimensions.keyFor(bodyId));
        if (target == null) return "There is no personal dimension for " + body.name() + " in this world.";

        var team = TeamUtils.getTeamIdOrPlayerFallback(player.getUUID());
        DiscoveryStage stage = ContinuumTeamData.get(server).effectiveStage(server, team, bodyId);
        if (!stage.atLeast(DiscoveryStage.SURVEYED))
            return "Survey " + body.name() + " before making a dimension of it.";

        PdimData data = PdimData.get(server);

        PdimCosts.Cost cost = PdimCosts.costFor(body);
        if (!player.isCreative() && !cost.free() && PdimCosts.charges(data.entry(player.getUUID(), bodyId) != null)) {
            int have = PdimCosts.count(player, cost);
            if (have < cost.count()) {
                return "Going to " + body.name() + " costs " + cost.describe().getString() + " (you have " + have +
                        ").";
            }
            PdimCosts.take(player, cost);
        }

        PdimData.Entry entry = data.create(player.getUUID(), bodyId, gravity);
        BlockPos origin = PdimDimensions.plotOrigin(entry.plot());
        buildPlatform(target, origin);

        if (!PdimDimensions.isPersonal(player.level().dimension())) {
            data.setReturn(player.getUUID(), new PdimData.Return(player.level().dimension().location(), player.getX(),
                    player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        }

        player.fallDistance = 0.0f;
        player.teleportTo(target, origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5, player.getYRot(),
                player.getXRot());
        sendState(player);
        return null;
    }

    public static @Nullable String leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!PdimDimensions.isPersonal(player.level().dimension())) return "You are not in a personal dimension.";

        PdimData data = PdimData.get(server);
        PdimData.Return back = data.returnPoint(player.getUUID());
        ServerLevel level = back == null ? null : server.getLevel(net.minecraft.resources.ResourceKey
                .create(net.minecraft.core.registries.Registries.DIMENSION, back.dimension()));

        player.fallDistance = 0.0f;
        if (level != null) {
            player.teleportTo(level, back.x(), back.y(), back.z(), back.yaw(), back.pitch());
        } else {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, 0.0f, 0.0f);
        }
        data.clearReturn(player.getUUID());
        sendState(player);
        return null;
    }

    public static boolean delete(ServerPlayer player, ResourceLocation bodyId) {
        boolean removed = PdimData.get(player.server).delete(player.getUUID(), bodyId);
        if (removed) sendState(player);
        return removed;
    }

    private static void buildPlatform(ServerLevel level, BlockPos origin) {
        level.getChunk(origin);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                BlockPos floor = origin.offset(dx, 0, dz);
                level.setBlockAndUpdate(floor, corner ? Blocks.CRYING_OBSIDIAN.defaultBlockState() :
                        Blocks.OBSIDIAN.defaultBlockState());
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos above = floor.above(dy);
                    if (!level.getBlockState(above).isAir())
                        level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** Changes the gravity and sky clock of a dimension the player already has. */
    public static @Nullable String adjust(ServerPlayer player, ResourceLocation bodyId, float gravity, float daySpeed,
                                          float lockedTime) {
        var changed = PdimData.get(player.server).update(player.getUUID(), bodyId,
                e -> new PdimData.Entry(PdimDimensions.clampGravity(gravity), e.plot(),
                        PdimDimensions.clampDaySpeed(daySpeed), lockedTime < 0 ? -1.0f : lockedTime % 24000.0f));
        if (changed == null) return "You have no personal dimension there.";
        sendState(player);
        return null;
    }

    public static void sendState(ServerPlayer player) {
        PdimData data = PdimData.get(player.server);
        Map<ResourceLocation, Float> mine = new HashMap<>();
        data.entries(player.getUUID()).forEach((body, entry) -> mine.put(body, entry.gravity()));

        float gravity = 1.0f;
        float daySpeed = 1.0f;
        float lockedTime = -1.0f;
        ResourceLocation here = PdimDimensions.bodyOf(player.level().dimension());
        if (here != null) {
            PdimData.Entry entry = data.entry(player.getUUID(), here);
            if (entry != null) {
                gravity = entry.gravity();
                daySpeed = entry.daySpeed();
                lockedTime = entry.lockedTime();
            }
        }
        Map<ResourceLocation, PdimCosts.Cost> costs = new HashMap<>();
        for (ContinuumBody body : ContinuumData.allBodies()) costs.put(body.id(), PdimCosts.costFor(body));
        boolean everyVisit = PdimCosts.charges(true);

        net.phoenix.continuum.network.ContinuumNetwork.sendToPlayer(player, new S2CPdimStatePacket(here != null, gravity, daySpeed, lockedTime, mine, costs, everyVisit));
    }

    public static boolean isIn(Level level) {
        return PdimDimensions.isPersonal(level.dimension());
    }
}
