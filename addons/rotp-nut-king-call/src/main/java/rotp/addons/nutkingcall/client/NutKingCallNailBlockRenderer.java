package rotp.addons.nutkingcall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.nutkingcall.block.NutKingCallNailBlock;
import rotp.addons.nutkingcall.block.NutKingCallNailBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

/** 1.16 NutKingCallNailBlockRenderer: the nail drawn out of the face it sits on. */
public final class NutKingCallNailBlockRenderer implements BlockEntityRenderer<NutKingCallNailBlockEntity> {
    private final ModelPart bone;

    public NutKingCallNailBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.bone = context.bakeLayer(NutKingCallNailModel.BLOCK_LAYER).getChild("bone");
    }

    @Override
    public void render(NutKingCallNailBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Direction facing = blockEntity.getBlockState().getValue(NutKingCallNailBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        switch (facing) {
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            default -> {}
        }
        poseStack.translate(0.0D, 0.5D, 0.0D);
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutout(NutKingCallProjectileRenderers.NAIL_TEXTURE));
        bone.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
