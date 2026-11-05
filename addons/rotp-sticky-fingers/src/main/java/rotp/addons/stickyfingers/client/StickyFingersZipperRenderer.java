package rotp.addons.stickyfingers.client;

import rotp.core.client.ClientGlobals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.block.StickyFingersZipperBlock;
import rotp.addons.stickyfingers.blockentity.StickyFingersZipperBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class StickyFingersZipperRenderer
        implements BlockEntityRenderer<
                StickyFingersZipperBlockEntity> {
    // A zipper sheet is 32x32: 1.16 StickyFingersZipperBlockRenderer drew
    // the face from its top-left 16x16 (texOffs 0,0 of a 16x16x0 box).
    private static final float FACE_UV = 0.5F;
    // Off the zipped block's face, which the chunk now draws: 1.16 put the
    // void 0.001 and the zipper 0.03/16 in front of the wall.
    private static final float VOID_OFFSET = 0.002F;
    private static final float ZIPPER_OFFSET = 0.004F;

    public StickyFingersZipperRenderer(
            BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            StickyFingersZipperBlockEntity zipper,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        Level level = zipper.getLevel();
        if (level == null || !ClientGlobals.canSeeStands) {
            return;
        }
        BlockState state = zipper.getBlockState();
        Direction facing =
                state.getValue(StickyFingersZipperBlock.FACING);
        renderSide(zipper, level, state, facing,
                poseStack, buffer, packedLight);
        // 1.16 drew the linked zipper of a one-block wall on its far face.
        if (state.getValue(StickyFingersZipperBlock.THROUGH)) {
            renderSide(zipper, level, state, facing.getOpposite(),
                    poseStack, buffer, packedLight);
        }
    }

    // 1.16 drew the zipper in the free cell in front of the face, lit as
    // that cell.
    private static int faceLight(
            Level level, BlockPos position, Direction facing,
            int packedLight) {
        BlockPos front = position.relative(facing);
        return level.isLoaded(front)
                ? LevelRenderer.getLightColor(level, front)
                : packedLight;
    }

    private static void renderSide(
            StickyFingersZipperBlockEntity zipper,
            Level level,
            BlockState state,
            Direction facing,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        ZipperFace face = selectFace(
                level, zipper.getBlockPos(), facing);
        boolean open =
                state.getValue(StickyFingersZipperBlock.OPEN);
        if (open) {
            renderVoid(
                    poseStack,
                    buffer.getBuffer(
                            StickyFingersZipperVoidRenderType
                                    .zipperVoid()),
                    facing);
        }
        renderPlane(
                poseStack,
                buffer.getBuffer(
                        RenderType.entityCutoutNoCull(
                                open
                                        ? openTexture(face.texture())
                                        : face.texture())),
                facing,
                ZIPPER_OFFSET,
                FACE_UV,
                faceLight(level, zipper.getBlockPos(), facing,
                        packedLight));
    }

    private static ZipperFace selectFace(
            Level level, BlockPos position, Direction facing) {
        Direction[] neighbors = planeDirections(facing);
        boolean[] connected = new boolean[4];
        int count = 0;
        for (int index = 0; index < neighbors.length; index++) {
            BlockState neighbor =
                    level.getBlockState(
                            position.relative(neighbors[index]));
            connected[index] =
                    StickyFingersZipperBlock.faces(neighbor, facing);
            if (connected[index]) {
                count++;
            }
        }

        if (count == 0) {
            return texture("diagonal", "");
        }
        if (count == 1) {
            return texture(
                    "straight",
                    connected[0] || connected[2]
                            ? "vertical"
                            : "horizontal");
        }
        if (count == 2) {
            if (connected[0] && connected[2]) {
                return texture("straight", "vertical");
            }
            if (connected[1] && connected[3]) {
                return texture("straight", "horizontal");
            }
            int rotation =
                    connected[0] && connected[1] ? 1
                            : connected[1] && connected[2] ? 2
                            : connected[2] && connected[3] ? 3
                            : 4;
            return texture("corner", Integer.toString(rotation));
        }
        if (count == 3) {
            int missing = 0;
            while (connected[missing]) {
                missing++;
            }
            // zipper_t_1..4 lack their bottom, left, top and right arm
            return texture("t", Integer.toString((missing + 2) % 4 + 1));
        }
        return texture("cross", "");
    }

    private static ZipperFace texture(
            String type, String rotation) {
        String suffix = rotation.isEmpty()
                ? ""
                : "_" + rotation;
        return new ZipperFace(
                StickyFingersAddon.id(
                        "textures/zipper/" + type
                                + "/zipper_" + type
                                + suffix + ".png"));
    }

    private static ResourceLocation openTexture(
            ResourceLocation texture) {
        String path = texture.getPath();
        return ResourceLocation.fromNamespaceAndPath(
                texture.getNamespace(),
                path.substring(0, path.length() - ".png".length())
                        + "_open.png");
    }

    private static Direction[] planeDirections(
            Direction facing) {
        return switch (facing) {
            case NORTH ->
                    new Direction[] {
                        Direction.UP,
                        Direction.EAST,
                        Direction.DOWN,
                        Direction.WEST
                    };
            case SOUTH ->
                    new Direction[] {
                        Direction.UP,
                        Direction.WEST,
                        Direction.DOWN,
                        Direction.EAST
                    };
            case EAST ->
                    new Direction[] {
                        Direction.UP,
                        Direction.SOUTH,
                        Direction.DOWN,
                        Direction.NORTH
                    };
            case WEST ->
                    new Direction[] {
                        Direction.UP,
                        Direction.NORTH,
                        Direction.DOWN,
                        Direction.SOUTH
                    };
            case UP ->
                    new Direction[] {
                        Direction.NORTH,
                        Direction.EAST,
                        Direction.SOUTH,
                        Direction.WEST
                    };
            case DOWN ->
                    new Direction[] {
                        Direction.SOUTH,
                        Direction.EAST,
                        Direction.NORTH,
                        Direction.WEST
                    };
        };
    }

    // The quad of a zipped face, pushed off it by offset.
    private static float[][] faceCorners(
            Direction facing, float offset) {
        float low = -offset;
        float high = 1.0F + offset;
        return switch (facing) {
            case NORTH -> new float[][] {
                {0, 0, low}, {1, 0, low}, {1, 1, low}, {0, 1, low}};
            case SOUTH -> new float[][] {
                {1, 0, high}, {0, 0, high}, {0, 1, high}, {1, 1, high}};
            case EAST -> new float[][] {
                {high, 0, 0}, {high, 0, 1}, {high, 1, 1}, {high, 1, 0}};
            case WEST -> new float[][] {
                {low, 0, 1}, {low, 0, 0}, {low, 1, 0}, {low, 1, 1}};
            case UP -> new float[][] {
                {0, high, 1}, {1, high, 1}, {1, high, 0}, {0, high, 0}};
            case DOWN -> new float[][] {
                {0, low, 0}, {1, low, 0}, {1, low, 1}, {0, low, 1}};
        };
    }

    private static void renderVoid(
            PoseStack poseStack,
            VertexConsumer vertices,
            Direction facing) {
        PoseStack.Pose pose = poseStack.last();
        for (float[] corner : faceCorners(facing, VOID_OFFSET)) {
            vertices.addVertex(
                    pose.pose(), corner[0], corner[1], corner[2]);
        }
    }

    private static void renderPlane(
            PoseStack poseStack,
            VertexConsumer vertices,
            Direction facing,
            float offset,
            float uv,
            int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        float[][] corners = faceCorners(facing, offset);
        float[][] uvs = {
            {0.0F, uv}, {uv, uv}, {uv, 0.0F}, {0.0F, 0.0F}};
        for (int index = 0; index < corners.length; index++) {
            vertex(vertices, pose,
                    corners[index][0],
                    corners[index][1],
                    corners[index][2],
                    uvs[index][0],
                    uvs[index][1],
                    facing,
                    packedLight);
        }
    }

    private static void vertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            Direction normal,
            int packedLight) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(
                        pose,
                        normal.getStepX(),
                        normal.getStepY(),
                        normal.getStepZ());
    }

    private record ZipperFace(ResourceLocation texture) {
    }
}
