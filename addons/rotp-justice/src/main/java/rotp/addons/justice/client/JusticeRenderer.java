package rotp.addons.justice.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import rotp.addons.justice.JusticeAddon;
import rotp.addons.justice.JusticeEntity;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import rotp.core.config.client.ClientModSettings;

@EventBusSubscriber(modid = JusticeAddon.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class JusticeRenderer extends StandEntityRenderer<JusticeEntity, JusticeRenderer.State, JusticeRenderer.Model> {
    private static final float LEGACY_SUMMON_POSE_TICKS = 20.0F;

    public JusticeRenderer(EntityRendererProvider.Context context) {
        super(context, 0.0F);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(JusticeAddon.ENTITY.get(), JusticeRenderer::new);
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
    public void extractRenderState(JusticeEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.handTilt = Vec3.ZERO;
        state.idle = entity.getCurStandAction() == null && entity.getPerformPose() == null;
        // The old empty summon pose still suppressed tilt until the legacy animator switched to idle.
        boolean inSummonPose = state.idle && !entity.isArmsOnlyMode()
                && entity.tickCount + partialTick <= LEGACY_SUMMON_POSE_TICKS;
        if (!inSummonPose && ClientModSettings.getSettingsReadOnly().standMotionTilt) {
            Model model = getEntityModel(state);
            if (model != null && model.legacyBody != null) {
                state.handTilt = model.prepareMotionTilt(entity, partialTick);
            }
        }
    }

    public static final class State extends StandEntityRenderState {
        private Vec3 handTilt = Vec3.ZERO;
        private boolean idle;
    }

    public static final class Model extends StandEntityModel<JusticeEntity, State> {
        private final ModelPart legacyBody;

        private Model(ModelPart root) {
            super(root);
            Model_1_21_2plus parts = (Model_1_21_2plus) (Object) this;
            ModelPart body = parts.jojo_ripples$getAnyDescendantWithName("body").orElse(null);
            if (body_rot == null && body != null && body.hasChild("leftHand") && body.hasChild("rightHand")) {
                legacyBody = body;
                // Bind the donor hands to the native visibility masks without changing the rig or its pivots.
                left_arm = body.getChild("leftHand");
                right_arm = body.getChild("rightHand");
            }
            else {
                legacyBody = null;
            }
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            double tiltSqr = state.handTilt.lengthSqr();
            if (legacyBody != null && tiltSqr > 1.0E-4) {
                legacyBody.xRot += (float) state.handTilt.x * 0.75F;
                if (state.idle) {
                    legacyBody.zRot = (float) (legacyBody.zRot + state.handTilt.z);
                    double yawScale = Mth.clamp(1.0 - Math.sqrt(tiltSqr) / Math.PI * 4.0, 0.0, 1.0);
                    legacyBody.yRot = (float) (legacyBody.yRot * yawScale);
                }
            }
        }
    }
}
