package rotp.addons.darkbluemoon.client.render;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.client.DarkBlueMoonModels;
import rotp.addons.darkbluemoon.entity.WhirlpoolEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 DarkBlueMoonWhirlpoolRenderer and DarkBlueMoonWhirlpoolModel: the whirlpool.geo.json rings upside down, turning a
 * full circle every 35 ticks, scaled from 1 to 10 by the water around it (full at 8000 blocks).
 */
public class WhirlpoolRenderer extends EntityRenderer<WhirlpoolEntity> {
    private static final ResourceLocation TEXTURE = DarkBlueMoonAddon.id("textures/entity/whirlpool.png");
    private static final float MAX_WATER = 8000.0F;
    @Nullable
    private final ModelPart root;

    public WhirlpoolRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.root = DarkBlueMoonModels.loadGeoBone(context.getResourceManager(), "whirlpool", "root");
    }

    @Override
    public void render(WhirlpoolEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        if (root != null) {
            poseStack.pushPose();
            float ratio = Math.min(entity.getWaterCount() / MAX_WATER, 1.0F);
            float scale = 1.0F + ratio * 9.0F;
            root.yRot = (float) Math.toRadians(entity.getTickCount() * 360.0 / 35.0 % 360.0);
            root.xRot = (float) Math.toRadians(180.0);
            poseStack.scale(scale, scale, scale);
            root.render(poseStack, buffer.getBuffer(RenderType.entityCutout(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(WhirlpoolEntity entity) {
        return TEXTURE;
    }
}
