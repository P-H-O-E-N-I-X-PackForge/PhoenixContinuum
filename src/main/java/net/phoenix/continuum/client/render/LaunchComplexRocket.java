package net.phoenix.continuum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.model.data.ModelData;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.integration.gtceu.ContinuumGt;
import net.phoenix.continuum.integration.gtceu.GregTech;
import net.phoenix.continuum.machine.LaunchComplex;

import java.util.ArrayList;
import java.util.List;

/**
 * A rocket standing in the shaft of every formed Launch Complex, so the multiblock looks like a launch site and not
 * just a gantry. Drawn from a plain block model ({@code models/special/launch_rocket.json}) that a resource pack can
 * replace. It finds the complexes by looking through the loaded chunks around the camera once every couple of
 * seconds, so it needs nothing from GregTech's own rendering and works the same on every GregTech version.
 */
@Mod.EventBusSubscriber(modid = PhoenixContinuum.MOD_ID, value = Dist.CLIENT)
public final class LaunchComplexRocket {

    public static final ResourceLocation MODEL = new ResourceLocation(PhoenixContinuum.MOD_ID, "special/launch_rocket");

    /** The model is 16 units tall; the shaft is 1 block wide and about 4 blocks tall. */
    private static final float SCALE = 3.5f;
    private static final int SCAN_EVERY = 40;
    private static final int SCAN_CHUNKS = 8;

    private record Site(BlockPos controller, BlockPos base) {}

    private static List<Site> sites = List.of();
    private static int ticks;

    private LaunchComplexRocket() {}

    /** Mod bus: asks the model manager to bake the rocket model even though no block uses it. */
    public static void registerModel(ModelEvent.RegisterAdditional event) {
        event.register(MODEL);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !GregTech.loaded()) return;
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

    /**
     * The base of the shaft: three blocks out from the controller towards the structure, the one direction where the
     * ring of frames closing the shaft's top is around an empty column.
     */
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
        var model = mc.getModelManager().getModel(MODEL);
        if (model == mc.getModelManager().getMissingModel()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(RenderType.cutout());
        boolean drew = false;
        for (Site site : sites) {
            LaunchComplex complex = ContinuumGt.complexAt(level, site.controller());
            if (complex == null || !complex.isFormed()) continue;
            BlockPos base = site.base();
            pose.pushPose();
            pose.translate(base.getX() + 0.5 - camera.x, base.getY() - camera.y, base.getZ() + 0.5 - camera.z);
            pose.scale(SCALE, SCALE, SCALE);
            pose.translate(-0.5, 0.0, -0.5);
            int light = LevelRenderer.getLightColor(level, base.above(2));
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), consumer, null, model, 1.0f, 1.0f,
                    1.0f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
            pose.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch(RenderType.cutout());
    }
}
