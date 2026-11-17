package rotp.addons.nutkingcall.client;

import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import rotp.core.util.functions.MathUtil;

import rotp.addons.nutkingcall.entity.NutKingCallEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;

public final class NutKingCallModel extends StandEntityModel<
        NutKingCallEntity, StandEntityRenderState> {
    // the core's tilt reads the clip pose by the core's bone names; this rig keeps the 1.16 names. As 1.16's
    // HumanoidStandModel.motionTilt: no head counter-tilt (the head is not under the body here) and no upperPart;
    // the rig has no leg x-rotation bones, so 1.16's legs took no tilt of their own either.
    private static final String[][] TILT_BONES = {
            {"body_rot", "body"},
            {"left_arm_xrot", "leftArmXRot"}, {"left_arm", "leftArm"}, {"left_arm_bend", "leftForeArm"},
            {"right_arm_xrot", "rightArmXRot"}, {"right_arm", "rightArm"}, {"right_arm_bend", "rightForeArm"},
            {"left_leg", "leftLeg"}, {"left_leg_bend", "leftLowerLeg"},
            {"right_leg", "rightLeg"}, {"right_leg_bend", "rightLowerLeg"}};
    private final AnimFramePose tiltView = new AnimFramePose();
    // false on the core's stand_default, which the renderer bakes through this class when a skin has no model
    private final boolean donorRig;

    public NutKingCallModel(ModelPart root) {
        super(root);
        Model_1_21_2plus model = (Model_1_21_2plus) (Object) this;
        donorRig = part(model, "leftArm") != null;
        if (donorRig) {
            body_rot = part(model, "body");
            torso_bend = part(model, "upperPart");
            torso_no_arms = part(model, "torso");
            torso_lower = torso_no_arms;
            left_arm_xrot = part(model, "leftArmXRot");
            left_arm = part(model, "leftArm");
            left_arm_bend = part(model, "leftForeArm");
            right_arm_xrot = part(model, "rightArmXRot");
            right_arm = part(model, "rightArm");
            right_arm_bend = part(model, "rightForeArm");
            left_leg = part(model, "leftLeg");
            left_leg_bend = part(model, "leftLowerLeg");
            right_leg = part(model, "rightLeg");
            right_leg_bend = part(model, "rightLowerLeg");
            // the core's constructor found no arm bends under these names
            addMissingItemHoldPoints();
        }
    }

    @Override
    public void doMotionTilt(Vec3 tiltVec, AnimFramePose targetPose, boolean idlePose) {
        if (!donorRig) {
            super.doMotionTilt(tiltVec, targetPose, idlePose);
            return;
        }
        tiltView.pose.clear();
        for (String[] bone : TILT_BONES) {
            // bones the tilt adds to get a frame even when the clip leaves them at rest
            boolean added = bone[0].equals("body_rot") || (!idlePose && bone[0].endsWith("_arm_xrot"));
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
        if (donorRig && head != null) {
            // 1.16 NutKingCallModel.setupAnim: after every pose the look replaces the clip's head rotation
            head.xRot = state.xRot * MathUtil.DEG_TO_RAD;
            head.yRot = state.yRot * MathUtil.DEG_TO_RAD;
            head.zRot = 0;
        }
    }

    private static ModelPart part(Model_1_21_2plus model, String name) {
        return model.jojo_ripples$getAnyDescendantWithName(name).orElse(null);
    }
}
