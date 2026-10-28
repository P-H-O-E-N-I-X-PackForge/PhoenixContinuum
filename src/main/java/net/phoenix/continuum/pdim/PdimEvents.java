package net.phoenix.continuum.pdim;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.phoenix.continuum.PhoenixContinuum;

@EventBusSubscriber(modid = PhoenixContinuum.MOD_ID)
public final class PdimEvents {

    private PdimEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level) || !PdimDimensions.isPersonal(level.dimension())) return;

        if (player.getY() < level.getMinBuildHeight() - 16) {
            Vec3 motion = player.getDeltaMovement();
            player.fallDistance = 0.0f;
            player.connection.teleport(player.getX(), level.getMaxBuildHeight() - 4.0, player.getZ(), player.getYRot(),
                    player.getXRot());
            player.setDeltaMovement(motion.x, Math.max(motion.y, -0.6), motion.z);
            player.hurtMarked = true;
        }
    }

    /** Dimensions read from files at startup get their GregTech ores once the world is up. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (var spec : net.phoenix.continuum.api.PdimWorldgen.all()) {
            if (spec.gtOresFrom() != null) PdimRuntime.mirrorOres(event.getServer(), spec.body());
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!PdimDimensions.isPersonal(event.getEntity().level().dimension())) return;
        var spec = PdimRules.specOf(event.getEntity().level());
        if (spec == null || !spec.fallDamage()) event.setDamageMultiplier(0.0f);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PdimActions.sendState(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PdimActions.sendState(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PdimActions.sendState(player);
    }
}
