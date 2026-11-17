package rotp.addons.speedking.client;

import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;

import rotp.addons.speedking.entity.SpeedKingEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;

public final class SpeedKingModel extends StandEntityModel<
        SpeedKingEntity, StandEntityRenderState> {
    public SpeedKingModel(ModelPart root) {
        super(root);
        Model_1_21_2plus model = (Model_1_21_2plus) (Object) this;
        body_rot = part(model, "body");
        head = part(model, "head");
        head_rot = head;
        torso_bend = part(model, "upperPart");
        torso_no_arms = part(model, "torso");
        torso_lower = torso_no_arms;
        left_arm_xrot = part(model, "leftArmXRot");
        left_arm = part(model, "leftArm");
        left_arm_bend = part(model, "leftForeArm");
        right_arm_xrot = part(model, "rightArmXRot");
        right_arm = part(model, "rightArm");
        right_arm_bend = part(model, "rightForeArm");
        left_leg_xrot = part(model, "leftLegXRot");
        left_leg = part(model, "leftLeg");
        left_leg_bend = part(model, "leftLowerLeg");
        right_leg_xrot = part(model, "rightLegXRot");
        right_leg = part(model, "rightLeg");
        right_leg_bend = part(model, "rightLowerLeg");
    }

    // the core's tilt reads the clip pose by the core's bone names; this rig keeps the 1.16 names. As 1.16's
    // HumanoidStandModel.motionTilt: no head counter-tilt (the head is not under the body here) and no upperPart.
    private static final String[][] TILT_BONES = {
            {"body_rot", "body"},
            {"left_arm_xrot", "leftArmXRot"}, {"left_arm", "leftArm"}, {"left_arm_bend", "leftForeArm"},
            {"right_arm_xrot", "rightArmXRot"}, {"right_arm", "rightArm"}, {"right_arm_bend", "rightForeArm"},
            {"left_leg_xrot", "leftLegXRot"}, {"left_leg", "leftLeg"}, {"left_leg_bend", "leftLowerLeg"},
            {"right_leg_xrot", "rightLegXRot"}, {"right_leg", "rightLeg"}, {"right_leg_bend", "rightLowerLeg"}};
    private final AnimFramePose tiltView = new AnimFramePose();

    @Override
    public void doMotionTilt(Vec3 tiltVec, AnimFramePose targetPose, boolean idlePose) {
        tiltView.pose.clear();
        for (String[] bone : TILT_BONES) {
            // bones the tilt adds to get a frame even when the clip leaves them at rest
            boolean added = bone[0].equals("body_rot") || bone[0].endsWith("_leg_xrot")
                    || (!idlePose && bone[0].endsWith("_arm_xrot"));
            AnimFramePose.ModelPartFrame frame = added
                    ? targetPose.getForModelPart(bone[1]) : targetPose.getIfPresent(bone[1]);
            if (frame != null) {
                tiltView.pose.put(bone[0], frame);
            }
        }
        super.doMotionTilt(tiltVec, tiltView, idlePose);
        tiltView.pose.clear();
    }

    @Override
    public void setupAnim(StandEntityRenderState state) {
        super.setupAnim(state);
        String animation = state.action.animId != null
                ? state.action.animId.name() : "";
        switch (animation) {
            case "pickaxeThrow" -> poseArms(-0.5F, -1.1F, -0.78F, 1.57F);
            case "heat_injection" ->
                    poseArms(-0.3F, -1.3F, -0.4F, 0.2F);
            case "heat_vision" ->
                    poseArms(-0.8F, -0.8F, -0.35F, 0.35F);
            case "heat_cook" ->
                    poseArms(-1.3F, 0.1F, -0.1F, 0.25F);
            case "heat_grab" ->
                    poseArms(-1.4F, -1.4F, -0.1F, 0.1F);
            case "heat_trap" ->
                    poseArms(-0.2F, -0.3F, -0.3F, 0.2F);
            default -> {
            }
        }
    }

    private void poseArms(
            float leftX, float rightX, float leftZ, float rightZ) {
        if (left_arm != null) {
            left_arm.xRot = leftX;
            left_arm.zRot = leftZ;
        }
        if (right_arm != null) {
            right_arm.xRot = rightX;
            right_arm.zRot = rightZ;
        }
    }

    private static ModelPart part(
            Model_1_21_2plus model, String name) {
        return model.jojo_ripples$getAnyDescendantWithName(name)
                .orElse(null);
    }
}
