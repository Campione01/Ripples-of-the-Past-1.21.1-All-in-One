package rotp.addons.thesun.client;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.thesun.entity.TheSunEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

public final class TheSunRenderer extends StandEntityRenderer<
        TheSunEntity,
        StandEntityRenderState,
        TheSunModel> {
    public static final float LEGACY_RENDER_SCALE = 3.75F;
    // 1.16 has no clip for this model, only the climb and the fade; the sun grows in as it starts to climb.
    public static final int SUMMON_GROW_TICKS = 20;

    public TheSunRenderer(
            EntityRendererProvider.Context context) {
        super(context, 0.0F);
        addLayer(new TheSunCoronaLayer(this));
    }

    @Override
    public TheSunModel createStandModel(
            LayerDefinition definition) {
        return new TheSunModel(definition.bakeRoot());
    }

    @Override
    public void extractRenderState(
            TheSunEntity entity,
            StandEntityRenderState renderState,
            float partialTick) {
        super.extractRenderState(
                entity, renderState, partialTick);
        renderState.resetObstruction();
    }

    @Override
    protected RenderType getRenderType(
            TheSunEntity entity,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing) {
        if (bodyVisible || translucent) {
            return ModRenderTypes.standTranslucentCull(
                    getTextureLocation(entity));
        }
        return glowing
                ? RenderType.outline(getTextureLocation(entity))
                : null;
    }

    @Override
    public void render(
            TheSunEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        poseStack.pushPose();
        float scale = LEGACY_RENDER_SCALE
                * summonGrowth(entity.tickCount + partialTick);
        poseStack.scale(scale, scale, scale);
        try {
            super.render(
                    entity,
                    entityYaw,
                    partialTick,
                    poseStack,
                    bufferSource,
                    LightTexture.FULL_BRIGHT);
        }
        finally {
            poseStack.popPose();
        }
    }

    static float summonGrowth(float ageTicks) {
        float progress = Mth.clamp(
                ageTicks / SUMMON_GROW_TICKS, 0.0F, 1.0F);
        return progress * (2.0F - progress);
    }
}
