package rotp.addons.darkbluemoon.client.render;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.client.DarkBlueMoonModels;
import rotp.addons.darkbluemoon.entity.ScaleShotEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 1.16 ScaleShotRenderer: the 2x3x5 scale turned by the projectile's yaw and pitch. */
public class ScaleShotRenderer extends EntityRenderer<ScaleShotEntity> {
    private static final ResourceLocation TEXTURE = DarkBlueMoonAddon.id("textures/entity/scale_shot.png");
    private final ModelPart model;

    public ScaleShotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = context.bakeLayer(DarkBlueMoonModels.SCALE_SHOT);
    }

    @Override
    public void render(ScaleShotEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        model.render(poseStack, buffer.getBuffer(RenderType.entityCutout(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ScaleShotEntity entity) {
        return TEXTURE;
    }
}
