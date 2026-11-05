package rotp.addons.highwaystar.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;

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
    }

    @Override
    public void render(HighwayStarEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int light) {
        if (!entity.isSplit()) {
            super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, light);
        }
    }

    public static final class State extends StandEntityRenderState {
        public boolean armMissing;
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
    }
}
