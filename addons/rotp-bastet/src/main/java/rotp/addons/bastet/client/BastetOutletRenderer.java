package rotp.addons.bastet.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.bastet.BastetAddon;
import rotp.addons.bastet.block.BastetOutletBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** 1.16 BastetOutletRenderer: the outlet texture on a full-block quad flat against the wall it hangs on. */
public final class BastetOutletRenderer implements BlockEntityRenderer<BastetOutletBlockEntity> {
    private static final ResourceLocation TEXTURE = BastetAddon.id("textures/block/bastet_outlet.png");

    public BastetOutletRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BastetOutletBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        switch (blockEntity.getFacing()) {
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            default -> {}
        }
        poseStack.translate(0.0D, 0.0D, -0.499D);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        vertex(vertices, matrix, -0.5F, -0.5F, 0.0F, 1.0F, packedLight);
        vertex(vertices, matrix, 0.5F, -0.5F, 1.0F, 1.0F, packedLight);
        vertex(vertices, matrix, 0.5F, 0.5F, 1.0F, 0.0F, packedLight);
        vertex(vertices, matrix, -0.5F, 0.5F, 0.0F, 0.0F, packedLight);
        poseStack.popPose();
    }

    // 1.16 gave the untransformed normal (0, 0, 1)
    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float u, float v,
            int packedLight) {
        vertices.addVertex(matrix, x, y, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}
