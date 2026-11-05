package rotp.addons.darkbluemoon.client.render;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.client.DarkBlueMoonModels;
import rotp.addons.darkbluemoon.entity.MiniTornadoEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 DarkBlueMoonMiniTornadoRenderer turned its model upside down and a full circle every 10 ticks the other way, but
 * pointed at textures/entity/deep_sea_water_tornado.png, which the 1.16 jar does not ship. The port draws the jar's
 * deep_sea_water_tornado.geo.json, whose UV layout is whirlpool.geo.json's, with the whirlpool texture.
 */
public class MiniTornadoRenderer extends EntityRenderer<MiniTornadoEntity> {
    private static final ResourceLocation TEXTURE = DarkBlueMoonAddon.id("textures/entity/whirlpool.png");
    @Nullable
    private final ModelPart root;

    public MiniTornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.root = DarkBlueMoonModels.loadGeoBone(context.getResourceManager(), "deep_sea_water_tornado", "root");
    }

    @Override
    public void render(MiniTornadoEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        if (root != null) {
            poseStack.pushPose();
            root.yRot = (float) Math.toRadians(-entity.getTickCount() * 360.0 / 10.0 % 360.0);
            root.xRot = (float) Math.toRadians(180.0);
            root.render(poseStack, buffer.getBuffer(RenderType.entityCutout(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MiniTornadoEntity entity) {
        return TEXTURE;
    }
}
