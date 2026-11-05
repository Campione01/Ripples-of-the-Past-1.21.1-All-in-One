package rotp.addons.echoes.client;

import java.io.Reader;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.entity.EchoesTailNeedleEntity;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

/**
 * R772 F11: ACT2's flying tail (the master's OnomatopoeiaTailNeedleRenderer): the author's act2_tail model and texture,
 * pointed along its flight.
 */
public final class EchoesTailNeedleRenderer extends EntityRenderer<EchoesTailNeedleEntity> {
    private static final ResourceLocation MODEL = RotpEchoes.id("geo/act2_tail.geo.json");
    private static final ResourceLocation TEXTURE = RotpEchoes.id("textures/entity/act2_tail.png");
    @Nullable private final ModelPart model;

    public EchoesTailNeedleRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart baked = null;
        Resource resource = context.getResourceManager().getResource(MODEL).orElse(null);
        if (resource != null) {
            try (Reader reader = resource.openAsReader()) {
                baked = ParseModEntityModel.parse(JsonParser.parseReader(reader), ModelFormat.GECKO).bakeRoot();
            }
            catch (Exception e) {
                baked = null;
            }
        }
        this.model = baked;
    }

    @Override
    public void render(EchoesTailNeedleEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        if (model != null) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
            // the geo model is built Y-down like an entity model; turned upright as the core's other geo props are
            model.xRot = (float) Math.PI;
            model.render(poseStack, buffer.getBuffer(RenderType.entityCutout(TEXTURE)), packedLight,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EchoesTailNeedleEntity entity) {
        return TEXTURE;
    }
}
