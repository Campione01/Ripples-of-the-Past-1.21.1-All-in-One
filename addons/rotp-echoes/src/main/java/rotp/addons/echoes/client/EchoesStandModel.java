package rotp.addons.echoes.client;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;

import rotp.addons.echoes.stand.EchoesStandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The author's 1.21.1 rigs and clips drive the head themselves (the core applies the user's look only when no clip
 * pose is playing), so nothing is re-applied after the clip, as in the author's own 1.21.1 build.
 * R772 F11: ACT2's tail tip (bone8, the word it carries) is left out while the tail is out, as the master's
 * EchoesACT2Model hid it; the core resets every part's visibility before each entity is posed.
 */
public final class EchoesStandModel extends StandEntityModel<
        EchoesStandEntity, EchoesStandRenderState> {
    @Nullable private final ModelPart tailTip;

    public EchoesStandModel(ModelPart root) {
        super(root);
        tailTip = ((Model_1_21_2plus) (Object) this).jojo_ripples$getAnyDescendantWithName("bone8").orElse(null);
    }

    @Override
    public void renderGeometryToBuffer(PoseStack poseStack, VertexConsumer buffer,
            int packedLight, int packedOverlay, int color) {
        hideTailWhileOut();
        super.renderGeometryToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
    }

    @Override
    public void renderClassicLayerToBuffer(PoseStack poseStack, VertexConsumer buffer,
            int packedLight, int packedOverlay, int color) {
        hideTailWhileOut();
        super.renderClassicLayerToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
    }

    private void hideTailWhileOut() {
        if (tailTip != null
                && RenderStateCrutches.currentStandEntityRenderState instanceof EchoesStandRenderState state
                && state.tailHidden) {
            tailTip.visible = false;
        }
    }
}
