package rotp.addons.darkbluemoon.client.render;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.client.DarkBlueMoonModels;
import rotp.addons.darkbluemoon.entity.BarnacleEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** 1.16 BarnacleRenderer: a 2x2x2 barnacle where the entity sits on its target. */
public class BarnacleRenderer extends EntityRenderer<BarnacleEntity> {
    private static final ResourceLocation TEXTURE = DarkBlueMoonAddon.id("textures/entity/barnacle.png");
    private final ModelPart model;

    public BarnacleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = context.bakeLayer(DarkBlueMoonModels.BARNACLE);
    }

    @Override
    public void render(BarnacleEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        model.render(poseStack, buffer.getBuffer(RenderType.entityCutout(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BarnacleEntity entity) {
        return TEXTURE;
    }
}
