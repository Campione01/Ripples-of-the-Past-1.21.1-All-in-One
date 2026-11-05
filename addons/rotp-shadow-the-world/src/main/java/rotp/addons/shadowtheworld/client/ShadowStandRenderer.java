package rotp.addons.shadowtheworld.client;

import java.util.Random;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import rotp.addons.shadowtheworld.entity.ShadowStandEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.player.Player;

/**
 * The 1.16 {@code SDworldRenderer}: the Stand is lit at block and sky
 * light 12 wherever it stands, and until the user has consumed the Nether
 * Star its body is skewed along an axis seeded by the entity id and
 * stretched on slow sine waves, the unstable look of the shadow.
 */
public final class ShadowStandRenderer extends StandEntityRenderer<
        ShadowStandEntity,
        StandEntityRenderState,
        StandEntityModel<ShadowStandEntity, StandEntityRenderState>> {
    private static final int ENHANCED_LIGHT = LightTexture.pack(12, 12);
    private static final float INTENSITY = 0.15F;
    private static final Random RANDOM = new Random();

    public ShadowStandRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void doRender(
            ShadowStandEntity stand,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light) {
        poseStack.pushPose();
        try {
            if (!(stand.getUser() instanceof Player user)
                    || !ShadowTheWorldAddon.state(user).usedNetherStar()) {
                applyUnstableDistortion(stand, partialTicks, poseStack);
            }
            super.doRender(
                    stand, entityYaw, partialTicks, poseStack,
                    bufferSource, ENHANCED_LIGHT);
        }
        finally {
            poseStack.popPose();
        }
    }

    // Same draws, order and axes as 1.16 (Vector3f.YP, ZP, XP).
    private static void applyUnstableDistortion(
            ShadowStandEntity stand, float partialTicks,
            PoseStack poseStack) {
        float time = (stand.tickCount + partialTicks) * 0.25F;
        RANDOM.setSeed(stand.getId());
        float diagonalAngle1 = 30.0F + RANDOM.nextFloat() * 30.0F;
        float diagonalAngle2 = 15.0F + RANDOM.nextFloat() * 45.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(diagonalAngle1));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                RANDOM.nextFloat() * 10.0F - 5.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(diagonalAngle2));
        poseStack.mulPose(Axis.YP.rotationDegrees(
                RANDOM.nextFloat() * 8.0F - 4.0F));
        float xScale = 1.0F + (float) Math.sin(
                time * 0.5D + RANDOM.nextFloat()) * INTENSITY * 1.8F;
        float yScale = 1.0F + (float) Math.cos(
                time * 0.2D + RANDOM.nextFloat()) * INTENSITY * 0.7F;
        float zScale = 1.0F + (float) Math.sin(
                time * 0.3D + RANDOM.nextFloat()) * INTENSITY * 1.2F;
        poseStack.scale(xScale, yScale, zScale);
        poseStack.mulPose(Axis.XP.rotationDegrees(-diagonalAngle2));
        poseStack.mulPose(Axis.YP.rotationDegrees(-diagonalAngle1));
    }
}
