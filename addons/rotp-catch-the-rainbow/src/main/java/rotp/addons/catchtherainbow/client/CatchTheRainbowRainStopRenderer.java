package rotp.addons.catchtherainbow.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Rain Stop's raindrops hanging in mid-air around each user who has it on.
 * The drops sit on a world-fixed lattice, so they stay where they stopped
 * while the user walks among them. A physical sight, not a Stand's, so it
 * is drawn for everyone.
 */
@EventBusSubscriber(
        modid = CatchTheRainbowAddon.MOD_ID,
        value = Dist.CLIENT)
public final class CatchTheRainbowRainStopRenderer {
    // The droplet texels the rain layer already draws its drops from.
    private static final ResourceLocation TEXTURE =
            CatchTheRainbowAddon.id(
                    "textures/entity/projectiles/"
                            + "catch_the_rainbow_rain_blade.png");
    private static final float U0 = 10.0F / 16.0F;
    private static final float U1 = 11.0F / 16.0F;
    private static final float V0 = 0.0F;
    private static final float V1 = 3.0F / 16.0F;
    private static final int RADIUS =
            (int) CatchTheRainbowEntity.RAIN_STOP_RADIUS;
    private static final int BLOCKS_BELOW = 2;
    private static final int BLOCKS_ABOVE = 7;
    private static final double MAX_VIEW_DISTANCE = 64.0D;
    private static final float HALF_WIDTH = 0.025F;
    private static final float HALF_HEIGHT = 0.12F;
    private static final int ALPHA = 190;

    private CatchTheRainbowRainStopRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.isRaining()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        List<CatchTheRainbowEntity> active = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof CatchTheRainbowEntity controller
                    && !controller.isRemoved()
                    && controller.isRainStopActive()
                    && controller.position().distanceToSqr(camera)
                            < MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE) {
                active.add(controller);
            }
        }
        if (active.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();
        RenderType renderType = RenderType.entityTranslucent(TEXTURE);
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack.Pose pose = event.getPoseStack().last();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Set<Long> drawn = new HashSet<>();
        for (CatchTheRainbowEntity controller : active) {
            BlockPos center = controller.blockPosition();
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dz * dz > RADIUS * RADIUS) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    int surface = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING, x, z);
                    int yMin = Math.max(
                            center.getY() - BLOCKS_BELOW, surface);
                    int yMax = center.getY() + BLOCKS_ABOVE;
                    if (yMin > yMax) {
                        continue;
                    }
                    cursor.set(x, yMin, z);
                    if (level.getBiome(cursor).value()
                            .getPrecipitationAt(cursor)
                            != Biome.Precipitation.RAIN) {
                        continue;
                    }
                    for (int y = yMin; y <= yMax; y++) {
                        long seed = Mth.getSeed(x, y, z);
                        if ((seed & 3L) != 0L
                                || !drawn.add(BlockPos.asLong(x, y, z))) {
                            continue;
                        }
                        double px = x + ((seed >> 8) & 15L) / 16.0D;
                        double py = y + ((seed >> 12) & 15L) / 16.0D;
                        double pz = z + ((seed >> 16) & 15L) / 16.0D;
                        cursor.set(x, y, z);
                        drop(vertices,
                                pose,
                                camera,
                                px,
                                py,
                                pz,
                                LevelRenderer.getLightColor(
                                        level, cursor));
                    }
                }
            }
        }
        buffers.endBatch(renderType);
    }

    private static void drop(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 camera,
            double x,
            double y,
            double z,
            int light) {
        double viewX = camera.x - x;
        double viewZ = camera.z - z;
        double length = Math.sqrt(viewX * viewX + viewZ * viewZ);
        if (length < 1.0E-4D) {
            return;
        }
        float sideX = (float) (viewZ / length) * HALF_WIDTH;
        float sideZ = (float) (-viewX / length) * HALF_WIDTH;
        float cx = (float) (x - camera.x);
        float cy = (float) (y - camera.y);
        float cz = (float) (z - camera.z);
        vertex(vertices, pose, cx - sideX, cy - HALF_HEIGHT, cz - sideZ,
                U0, V1, light);
        vertex(vertices, pose, cx + sideX, cy - HALF_HEIGHT, cz + sideZ,
                U1, V1, light);
        vertex(vertices, pose, cx + sideX, cy + HALF_HEIGHT, cz + sideZ,
                U1, V0, light);
        vertex(vertices, pose, cx - sideX, cy + HALF_HEIGHT, cz - sideZ,
                U0, V0, light);
    }

    private static void vertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            int light) {
        vertices.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, ALPHA)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
