package rotp.addons.echoes.client;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.block.EchoesOnomatopoeiaMarkBlock;
import rotp.addons.echoes.block.EchoesOnomatopoeiaMarkBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R772 F11: ACT2's word on a block face (the master's OnomatopoeiaMarkRenderer): one of the author's four mark
 * pictures flat on the face, seen only by Stand users. The master turned east and west marks (and ACT1's sound marks)
 * the wrong way, which put them on the far side of their cell facing the wall; here each lies on its own face.
 */
public final class EchoesOnomatopoeiaMarkRenderer implements BlockEntityRenderer<EchoesOnomatopoeiaMarkBlockEntity> {
    private static final ResourceLocation[] TEXTURES = {
            RotpEchoes.id("textures/entity/onomatopoeia/onomatopoeia_0.png"),
            RotpEchoes.id("textures/entity/onomatopoeia/onomatopoeia_1.png"),
            RotpEchoes.id("textures/entity/onomatopoeia/onomatopoeia_2.png"),
            RotpEchoes.id("textures/entity/onomatopoeia/onomatopoeia_3.png")
    };

    public EchoesOnomatopoeiaMarkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(EchoesOnomatopoeiaMarkBlockEntity mark, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (mark.word() == null || !viewerHasStand()) {
            return;
        }
        BlockState state = mark.getBlockState();
        drawOnFace(poseStack, buffer, state.getValue(EchoesOnomatopoeiaMarkBlock.FACING),
                TEXTURES[state.getValue(EchoesOnomatopoeiaMarkBlock.VARIANT) % TEXTURES.length], packedLight);
    }

    static boolean viewerHasStand() {
        Minecraft mc = Minecraft.getInstance();
        StandPower viewer = mc.player != null ? StandPower.get(mc.player) : null;
        return viewer != null && viewer.hasPower();
    }

    /** A picture flat on the face of the block behind a mark's cell, facing out of it. */
    static void drawOnFace(PoseStack poseStack, MultiBufferSource buffer, Direction facing, ResourceLocation texture,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        switch (facing) {
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            case SOUTH -> {}
        }
        poseStack.translate(0.0D, 0.0D, -0.499D);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(texture));
        vertex(consumer, pose, -0.5F, -0.5F, 0.0F, 1.0F, packedLight);
        vertex(consumer, pose, 0.5F, -0.5F, 1.0F, 1.0F, packedLight);
        vertex(consumer, pose, 0.5F, 0.5F, 1.0F, 0.0F, packedLight);
        vertex(consumer, pose, -0.5F, 0.5F, 0.0F, 0.0F, packedLight);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v,
            int light) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
