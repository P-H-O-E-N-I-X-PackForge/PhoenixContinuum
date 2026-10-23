package net.phoenix.continuum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.integration.gtceu.ContinuumGt;
import net.phoenix.continuum.integration.gtceu.GregTech;
import net.phoenix.continuum.machine.LaunchComplex;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class LaunchComplexRocket {

    private static final float SCALE = 3.0f;
    private static final float DEGREES_PER_TICK = 1.2f;
    private static final int SCAN_EVERY = 40;
    private static final int SCAN_CHUNKS = 8;

    private record Site(BlockPos controller, BlockPos base) {}

    private static List<Site> sites = List.of();
    private static int ticks;
    private static ItemStack rocket;

    private LaunchComplexRocket() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!GregTech.loaded()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            sites = List.of();
            return;
        }
        if (++ticks < SCAN_EVERY) return;
        ticks = 0;
        sites = scan(mc.level, mc.player.blockPosition());
    }

    private static List<Site> scan(Level level, BlockPos around) {
        List<Site> found = new ArrayList<>();
        ChunkPos center = new ChunkPos(around);
        for (int dx = -SCAN_CHUNKS; dx <= SCAN_CHUNKS; dx++) {
            for (int dz = -SCAN_CHUNKS; dz <= SCAN_CHUNKS; dz++) {
                if (!level.hasChunk(center.x + dx, center.z + dz)) continue;
                LevelChunk chunk = level.getChunk(center.x + dx, center.z + dz);
                for (BlockPos pos : new ArrayList<>(chunk.getBlockEntitiesPos())) {
                    LaunchComplex complex = ContinuumGt.complexAt(level, pos);
                    if (complex == null) continue;
                    BlockPos shaft = shaftOf(level, pos);
                    if (shaft != null) found.add(new Site(pos.immutable(), shaft));
                }
            }
        }
        return found;
    }

    private static BlockPos shaftOf(Level level, BlockPos controller) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos column = controller.relative(d, 3);
            BlockPos ringCenter = column.above(4);
            if (!level.getBlockState(column.above(2)).isAir()) continue;
            boolean ring = true;
            for (int dx = -1; dx <= 1 && ring; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (level.getBlockState(ringCenter.offset(dx, 0, dz)).isAir()) {
                        ring = false;
                        break;
                    }
                }
            }
            if (ring) return column.above();
        }
        return null;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || sites.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        if (rocket == null) rocket = ContinuumRegistry.ROCKET.asStack();

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        float angle = (level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * DEGREES_PER_TICK;
        boolean drew = false;
        for (Site site : sites) {
            LaunchComplex complex = ContinuumGt.complexAt(level, site.controller());
            if (complex == null || !complex.isFormed()) continue;
            BlockPos base = site.base();
            pose.pushPose();
            
            pose.translate(base.getX() + 0.5 - camera.x, base.getY() + SCALE * 0.5 - camera.y,
                    base.getZ() + 0.5 - camera.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
            pose.scale(SCALE, SCALE, SCALE);
            int light = LevelRenderer.getLightColor(level, base.above(2));
            mc.getItemRenderer().renderStatic(rocket, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose,
                    buffers, level, 0);
            pose.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
    }
}
