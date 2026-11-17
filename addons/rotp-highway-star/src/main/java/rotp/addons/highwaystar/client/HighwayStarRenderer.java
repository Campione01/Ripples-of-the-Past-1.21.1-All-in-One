package rotp.addons.highwaystar.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.highwaystar.entity.HighwayStarEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** 1.16 HighwayStarRenderer and HighwayStarModel: nothing is drawn in the split form, the left forearm is gone while seized. */
public final class HighwayStarRenderer extends StandEntityRenderer<
        HighwayStarEntity, HighwayStarRenderer.State, HighwayStarRenderer.Model> {

    public HighwayStarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public Model createStandModel(LayerDefinition definition) {
        return new Model(definition.bakeRoot());
    }

    @Override
    public void extractRenderState(HighwayStarEntity entity, State renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.armMissing = entity.isArmMissing();
        renderState.modelScale = pendingSplitScale(
                entity.isPendingSplit(), entity.isSplit(), entity.getSplitSwitchTicks(), partialTick);
    }

    /**
     * 1.16 HighwayStarModel.setupAnim's 1.0 to 0.7, the model's own origin fixed, over the ticks the model is still drawn:
     * its 10 ticks to the split would outlast the squash, which ends the Stand well before them.
     */
    static float pendingSplitScale(boolean pendingSplit, boolean split, int switchTicks, float partialTick) {
        if (!pendingSplit || split) {
            return 1.0F;
        }
        float progress = (10 - switchTicks + partialTick) / (10.0F - HighwayStarEntity.GONE_AT_SWITCH_TICKS);
        return 1.0F - Math.clamp(progress, 0.0F, 1.0F) * 0.3F;
    }

    @Override
    public void render(HighwayStarEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int light) {
        if (!entity.isGoneFromSight()) {
            super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, light);
        }
    }

    public static final class State extends StandEntityRenderState {
        public boolean armMissing;
        public float modelScale = 1.0F;
    }

    public static final class Model extends StandEntityModel<HighwayStarEntity, State> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(State renderState) {
            super.setupAnim(renderState);
            if (renderState.armMissing) {
                // 1.16 leftForeArm.visible = !isArmMissing()
                setVisible(left_arm_bend, false);
            }
        }

        @Override
        public void renderToBuffer(
                PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
            float scale = RenderStateCrutches.currentStandEntityRenderState instanceof State state
                    ? state.modelScale : 1.0F;
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
            super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
            poseStack.popPose();
        }
    }
}
