package net.phoenix.continuum.pdim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.api.PdimWorldgen;
import net.phoenix.continuum.data.ContinuumData;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * What a {@link PdimWorldgen} spec says about the world's rules: whether mobs spawn, whether there is air, how far a
 * player may stray from their plot and whether other people's plots can be built on.
 */
@EventBusSubscriber(modid = PhoenixContinuum.MOD_ID)
public final class PdimRules {

    private PdimRules() {}

    /** The plot area a protected plot covers when the spec sets no radius. */
    public static final int DEFAULT_PROTECT_RADIUS = 256;

    private record Cached(PdimWorldgen.Spec spec, long at) {}

    private static final Map<ResourceKey<Level>, Cached> CACHE = new HashMap<>();
    private static final long CACHE_TICKS = 60L;

    /** The spec of the personal dimension a level is, or null if it is not one. Up to three seconds old. */
    public static synchronized @Nullable PdimWorldgen.Spec specOf(Level level) {
        if (!(level instanceof ServerLevel)) return null;
        ResourceLocation body = PdimDimensions.bodyOf(level.dimension());
        if (body == null) return null;
        long now = level.getGameTime();
        Cached cached = CACHE.get(level.dimension());
        if (cached != null && now >= cached.at() && now - cached.at() < CACHE_TICKS) return cached.spec();
        var spec = PdimWorldgen.resolve(body, ContinuumData.body(body));
        CACHE.put(level.dimension(), new Cached(spec, now));
        return spec;
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION && type != MobSpawnType.SPAWNER &&
                type != MobSpawnType.PATROL && type != MobSpawnType.REINFORCEMENT && type != MobSpawnType.STRUCTURE) {
            return;
        }
        var spec = specOf(event.getLevel().getLevel());
        if (spec != null && !spec.mobs()) event.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % 10 != 0) return;
        var spec = specOf(level);
        if (spec == null) return;

        if (spec.oxygenGear() != null && player.tickCount % 20 == 0 && !player.isCreative() &&
                !player.isSpectator() && !wearing(player, spec.oxygenGear())) {
            player.hurt(player.damageSources().drown(), 1.0f);
            player.displayClientMessage(Component.literal("There is no air here."), true);
        }

        if (spec.plotRadius() > 0 && !player.hasPermissions(2)) {
            ResourceLocation body = PdimDimensions.bodyOf(level.dimension());
            var entry = PdimData.get(level.getServer()).entry(player.getUUID(), body);
            if (entry != null) {
                BlockPos origin = PdimDimensions.plotOrigin(entry.plot());
                double dx = player.getX() - origin.getX();
                double dz = player.getZ() - origin.getZ();
                double reach = spec.plotRadius();
                if (Math.abs(dx) > reach || Math.abs(dz) > reach) {
                    double x = origin.getX() + Math.max(-reach + 1, Math.min(reach - 1, dx));
                    double z = origin.getZ() + Math.max(-reach + 1, Math.min(reach - 1, dz));
                    player.teleportTo(level, x, player.getY(), z, player.getYRot(), player.getXRot());
                    player.displayClientMessage(Component.literal("That is as far from your plot as you can go."), true);
                }
            }
        }
    }

    private static boolean wearing(ServerPlayer player, String gear) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty()) return false;
        if (gear.startsWith("#")) {
            return head.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(gear.substring(1))));
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(head.getItem())
                .equals(ResourceLocation.parse(gear));
    }

    /** Whether the player may change things at this spot of a protected personal dimension. */
    public static boolean mayBuild(ServerPlayer player, BlockPos pos) {
        if (player.isCreative() && player.hasPermissions(2)) return true;
        ServerLevel level = player.serverLevel();
        var spec = specOf(level);
        if (spec == null || !spec.protectPlots()) return true;
        ResourceLocation body = PdimDimensions.bodyOf(level.dimension());
        int reach = spec.plotRadius() > 0 ? spec.plotRadius() : DEFAULT_PROTECT_RADIUS;
        int plot = PdimDimensions.plotAt(pos);
        BlockPos origin = PdimDimensions.plotOrigin(plot);
        if (Math.abs(pos.getX() - origin.getX()) > reach || Math.abs(pos.getZ() - origin.getZ()) > reach) return true;
        var data = PdimData.get(level.getServer());
        var owner = data.ownerOfPlot(body, plot);
        if (owner == null || owner.equals(player.getUUID())) return true;
        return ContinuumHost.teamOf(owner).equals(ContinuumHost.teamOf(player.getUUID()));
    }

    private static void refuse(ServerPlayer player) {
        player.displayClientMessage(Component.literal("That is somebody else's plot."), true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !mayBuild(player, event.getPos())) {
            event.setCanceled(true);
            refuse(player);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !mayBuild(player, event.getPos())) {
            event.setCanceled(true);
            refuse(player);
        }
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !mayBuild(player, event.getPos())) {
            event.setCanceled(true);
            refuse(player);
        }
    }
}
