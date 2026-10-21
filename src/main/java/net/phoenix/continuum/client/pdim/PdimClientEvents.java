package net.phoenix.continuum.client.pdim;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.pdim.PdimDimensions;

@Mod.EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class PdimClientEvents {

    private PdimClientEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !event.player.level().isClientSide) return;
        Player player = event.player;
        if (player != Minecraft.getInstance().player) return;
        if (!PdimDimensions.isPersonal(player.level().dimension())) return;

        float gravity = PdimClientState.gravity();
        if (Math.abs(gravity - 1.0f) < 0.001f) return;
        if (player.onGround() || player.getAbilities().flying || player.isFallFlying() || player.isInWater() ||
                player.isInLava() || player.onClimbable()) {
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, motion.y - (gravity - 1.0) * 0.08, motion.z);
    }
}
