package rotp.addons.rhcp.client;

import org.joml.Vector3f;

import rotp.core.client.entityanim.playerbend.ArmsFollowPitch;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;

import rotp.addons.rhcp.entity.RhcpStandEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Native 1.21.1 translation of the donor 128x128 RHCP model (RotP-RHCP Beta1 and
 * Beta2 RHCPModel over the 1.16 HumanoidStandModel) and of the 1.16 poses it plays.
 */
public final class RhcpModel extends StandEntityModel<
        RhcpStandEntity, RhcpRenderState> {
    private final ModelPart tail;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart tail3;
    private final ModelPart tail4;
    private final ModelPart beakLower;
    private final ModelPart[] actionParts;
    private final ModelPart[] joints;
    // 1.16 HumanoidStandModel.setSecondXRot: the look pitch the arms take on top of a pose.
    private float armPitch;
    private final Frame blockScratch = new Frame();

    private static final int BODY = 0;
    private static final int TORSO = 1;
    private static final int HEAD = 2;
    private static final int BEAK = 3;
    private static final int LEFT_ARM = 4;
    private static final int LEFT_FOREARM = 5;
    private static final int RIGHT_ARM = 6;
    private static final int RIGHT_FOREARM = 7;
    private static final int LEFT_LEG = 8;
    private static final int LEFT_LOWER_LEG = 9;
    private static final int RIGHT_LEG = 10;
    private static final int RIGHT_LOWER_LEG = 11;
    private static final int TAIL = 12;
    private static final int PART_COUNT = 17;
    private static final int[] MIRRORED_PART = {
            BODY, TORSO, HEAD, BEAK,
            RIGHT_ARM, RIGHT_FOREARM,
            LEFT_ARM, LEFT_FOREARM,
            RIGHT_LEG, RIGHT_LOWER_LEG,
            LEFT_LEG, LEFT_LOWER_LEG,
            TAIL, TAIL + 1, TAIL + 2, TAIL + 3, TAIL + 4
    };

    // 1.16 ModelPose.setEasing of the pose a transition goes to.
    private static final int LINEAR = 0;
    private static final int SQUARE = 1;
    private static final int CUBE = 2;

    // 1.16 HumanoidStandModel armsRotation, armsRotationFull and armsRotationBack.
    private static final int PITCH_NONE = 0;
    private static final int PITCH_TURN = 1;
    private static final int PITCH_FULL = 2;
    private static final int PITCH_BACK = 3;

    private static final Frame EMPTY = frame();

    private static final Frame FLOATING_IDLE = frame()
            .beak(0, 0, 0).body(0, 0, 0).torso(0, 0, 0)
            .tail(0, -15, 0, 0)
            .tail(1, -5, 10, 0)
            .tail(2, 10, 15, 0)
            .tail(3, 10, 15, 0)
            .tail(4, 10, 20, 0)
            .leftArm(-107.5F, 37.5F, -90)
            .leftForearm(-81.6905F, -19.4555F, 49.077F)
            .rightArm(-107.5F, -37.5F, 90)
            .rightForearm(-78.1822F, 16.4737F, -49.5249F)
            .leftLeg(0, 0, -2.5F).leftLowerLeg(25, 0, 0)
            .rightLeg(-45, 0, 2.5F).rightLowerLeg(90, 0, 0);
    private static final Frame GROUNDED_IDLE = groundedIdle();
    private static final Frame MOVING_IDLE = groundedIdle()
            .leftLeg(10, 0, -5).leftLowerLeg(0, 0, 2.5F)
            .rightLeg(10, 0, 5).rightLowerLeg(0, 0, -2.5F);
    private static final Frame FLOATING_IDLE_LOOP = tailIdleLoop();
    private static final Frame GROUNDED_IDLE_LOOP = tailIdleLoop()
            .leftArm(15, 0, -35).leftForearm(-90, 50, -57.5F)
            .rightArm(15, 0, 35).rightForearm(-90, -50, 57.5F);

    // RHCP donor summon poses (RHCPModel.initSummonPoseRotations).
    private static final Frame SUMMON_1 = frame()
            .head(10.0F, 0.0F, 5.0F)
            .body(40.0F, 15.0F, 10.0F)
            .torso(0.0F, 0.0F, 0.0F)
            .tail(0, -26.5F, 30.0F, 0.0F)
            .tail(1, -10.0F, 10.0F, 0.0F)
            .tail(2, 0.0F, 15.0F, 0.0F)
            .tail(3, 0.0F, 15.0F, 0.0F)
            .tail(4, 0.0F, 20.0F, 0.0F)
            .leftArm(-90.0F, 55.0F, -110.0F)
            .leftForearm(-90.0F, -67.5F, 90.0F)
            .rightArm(-90.0F, -55.0F, 110.0F)
            .rightForearm(-90.0F, 67.5F, -90.0F)
            .leftLeg(-75.0F, -30.0F, 0.0F)
            .leftLowerLeg(110.0F, 0.0F, 0.0F)
            .rightLeg(-75.0F, 30.0F, 0.0F)
            .rightLowerLeg(110.0F, 0.0F, 0.0F);
    private static final Frame SUMMON_2 = frame()
            .head(-7.5F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 2.5F)
            .torso(0.0F, 0.0F, 0.0F)
            .tail(0, 10.0F, 40.0F, 0.0F)
            .tail(1, 5.0F, 10.0F, 0.0F)
            .tail(2, 0.0F, 15.0F, 0.0F)
            .tail(3, -5.0F, 15.0F, 0.0F)
            .tail(4, -5.0F, 20.0F, 0.0F)
            .leftArm(-110.0F, 77.5F, -95.0F)
            .leftForearm(-98.2468F, -10.5378F, 6.8048F)
            .rightArm(-110.0F, -77.5F, 95.0F)
            .rightForearm(-102.0559F, 4.0043F, -13.027F)
            .leftLeg(-5.0F, 0.0F, -2.5F)
            .leftLowerLeg(10.0F, 0.0F, 0.0F)
            .rightLeg(-5.0F, 0.0F, 2.5F)
            .rightLowerLeg(20.0F, 0.0F, 0.0F);
    private static final Frame SUMMON_3 = frame()
            .head(5.0F, -5.0F, 10.0F)
            .body(15.0F, -5.0F, 10.0F)
            .torso(0.0F, 15.0F, 0.0F)
            .tail(0, 0.0F, -40.0F, 0.0F)
            .tail(1, 0.0F, -15.0F, 0.0F)
            .tail(2, 0.0F, -17.5F, 0.0F)
            .tail(3, -5.0F, -20.0F, 0.0F)
            .tail(4, -7.5F, -20.0F, 0.0F)
            .leftArm(-56.0297F, 24.0929F, -83.1183F)
            .leftForearm(-32.5F, 0.0F, 0.0F)
            .rightArm(100.0F, -40.0F, 0.0F)
            .rightForearm(-80.0F, 0.0F, 0.0F)
            .leftLeg(-120.0F, -5.0F, 0.0F)
            .leftLowerLeg(130.0F, 0.0F, 0.0F)
            .rightLeg(-25.0F, 30.0F, 0.0F)
            .rightLowerLeg(90.0F, 0.0F, 0.0F);
    private static final Frame[] SUMMON_POSES = {SUMMON_1, SUMMON_2, SUMMON_3};
    // 1.16 LegacyStandAnimator SUMMON_ANIMATION_LENGTH and its reverse point.
    private static final float SUMMON_TICKS = 20.0F;
    private static final float SUMMON_REVERSE_POINT = 0.75F;

    // The inherited 1.16 HumanoidStandModel light-jab timeline.
    private static final Frame JAB_1 = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, -15.0F, 0.0F)
            .leftArm(-7.5F, 0.0F, -15.0F)
            .leftForearm(-100.0F, 15.0F, 7.5F)
            .rightArm(22.5F, 0.0F, 22.5F)
            .rightForearm(-105.0F, 0.0F, -15.0F);
    private static final Frame JAB_2 = frame()
            .body(0.0F, -5.0F, 0.0F)
            .torso(0.0F, -20.0F, 0.0F)
            .leftArm(30.0F, 0.0F, -15.0F)
            .leftForearm(-107.5F, 15.0F, 7.5F)
            .rightArm(5.941F, 8.4211F, 69.059F)
            .rightForearm(-75.0F, 0.0F, 0.0F)
            .pitch(PITCH_TURN);
    private static final Frame JAB_3 = frame()
            .body(0.0F, -12.5F, 0.0F)
            .torso(0.0F, -17.5F, 0.0F)
            .leftArm(37.5F, 0.0F, -15.0F)
            .leftForearm(-115.0F, 15.0F, 7.5F)
            .rightArm(-81.9244F, 11.0311F, 70.2661F)
            .rightForearm(0.0F, 0.0F, 0.0F)
            .pitch(PITCH_FULL).ease(CUBE);
    private static final Frame JAB_4 = frame()
            .body(0.0F, -3.75F, 0.0F)
            .torso(0.0F, -3.75F, 0.0F)
            .leftArm(5.63F, 0.0F, -20.62F)
            .leftForearm(-103.75F, 3.75F, 13.13F)
            .rightArm(5.941F, 8.4211F, 69.059F)
            .rightForearm(-75.0F, 0.0F, 0.0F)
            .pitch(PITCH_BACK).ease(CUBE);

    // RHCP donor regular heavy-punch timeline.
    private static final Frame HEAVY_1 = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 0.0F, 0.0F)
            .leftArm(22.5F, 0.0F, 0.0F)
            .leftForearm(-45.0F, 0.0F, 0.0F)
            .rightArm(22.5F, 0.0F, 0.0F)
            .rightForearm(-95.0F, 0.0F, 0.0F);
    private static final Frame HEAVY_2 = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 30.0F, 0.0F)
            .leftArm(22.5F, 0.0F, 0.0F)
            .leftForearm(-45.0F, 0.0F, 0.0F)
            .rightArm(22.5F, 0.0F, 0.0F)
            .rightForearm(-95.0F, 0.0F, 0.0F);
    private static final Frame HEAVY_3 = frame()
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -11.25F, 0.0F)
            .leftArm(22.5F, 0.0F, -30.0F)
            .leftForearm(-7.5F, 0.0F, 0.0F)
            .rightArm(-6.25F, 5.0F, 0.0F)
            .rightForearm(-51.0618F, -0.612F, -0.5875F);
    private static final Frame HEAVY_4 = frame()
            .body(10.0F, -12.5F, 0.0F)
            .torso(0.0F, -40.0F, 0.0F)
            .leftArm(12.5F, 0.0F, -60.0F)
            .leftForearm(-15.0F, 0.0F, 0.0F)
            .rightArm(-25.0F, -25.0F, 50.0F)
            .rightForearm(-50.0F, 90.0F, 0.0F);
    private static final Frame HEAVY_5 = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 0.0F, 0.0F)
            .leftArm(22.5F, 0.0F, 0.0F)
            .leftForearm(-45.0F, 0.0F, 0.0F)
            .rightArm(-90.0F, -37.5F, 90.0F)
            .rightForearm(0.0F, 90.0F, 37.5F);

    // RHCP donor target/chase alternate heavy timeline.
    private static final Frame HEAVY_ALT_1 = frame()
            .beak(17.5F, 0.0F, 0.0F)
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -15.0F, 0.0F)
            .leftArm(0.0F, -5.0F, -90.0F)
            .leftForearm(-90.0F, 0.0F, 90.0F)
            .rightArm(25.0F, 0.0F, 60.0F)
            .rightForearm(-60.0F, -10.0F, -10.0F);
    private static final Frame HEAVY_ALT_2 = frame()
            .beak(10.0F, 0.0F, 0.0F)
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -20.0F, 0.0F)
            .leftArm(3.33F, -5.0F, -90.0F)
            .leftForearm(-90.0F, 3.33F, 90.0F)
            .rightArm(25.0F, 0.0F, 60.0F)
            .rightForearm(-53.33F, -6.66F, -6.66F)
            .ease(SQUARE);
    private static final Frame HEAVY_ALT_3 = frame()
            .beak(20.0F, 0.0F, 0.0F)
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -25.0F, 0.0F)
            .leftArm(6.66F, -5.0F, -90.0F)
            .leftForearm(-90.0F, 6.66F, 90.0F)
            .rightArm(25.0F, 0.0F, 60.0F)
            .rightForearm(-46.67F, -3.33F, -3.33F)
            .ease(SQUARE);
    private static final Frame HEAVY_ALT_4 = frame()
            .beak(25.0F, 0.0F, 0.0F)
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -30.0F, 0.0F)
            .leftArm(10.0F, -5.0F, -90.0F)
            .leftForearm(-90.0F, 10.0F, 90.0F)
            .rightArm(25.0F, 0.0F, 60.0F)
            .rightForearm(-40.0F, 0.0F, 0.0F)
            .ease(SQUARE);
    private static final Frame HEAVY_ALT_5 = frame()
            .beak(25.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 40.0F, 0.0F)
            .leftArm(-59.9053F, -4.3288F, -92.5048F)
            .leftForearm(-90.0F, -80.0F, 90.0F)
            .rightArm(20.0F, 0.0F, 30.0F)
            .rightForearm(-90.0F, 0.0F, 0.0F)
            .ease(CUBE);

    // RHCP donor normal double-hit combo.
    private static final Frame DOUBLE_HIT_1 = frame()
            .head(0.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .tail(0, -20.45F, -6.82F, 0.0F)
            .tail(1, -7.27F, -1.36F, 0.0F)
            .tail(2, 0.91F, 1.36F, 0.0F)
            .tail(3, 0.91F, 1.36F, 0.0F)
            .tail(4, 0.91F, 1.82F, 0.0F)
            .leftArm(-122.3122F, 37.7555F, -111.1693F)
            .leftForearm(-90.0F, -22.5F, 0.0F)
            .rightArm(-122.3122F, -37.7555F, 111.1693F)
            .rightForearm(-90.0F, 27.5F, 0.0F)
            .leftLeg(-20.0F, 0.0F, -2.5F)
            .leftLowerLeg(25.0F, 0.0F, 0.0F)
            .rightLeg(-5.0F, 0.0F, 1.0F)
            .rightLowerLeg(10.0F, 0.0F, 0.0F);
    private static final Frame DOUBLE_HIT_2 = frame()
            .head(0.0F, 0.0F, 0.0F)
            .beak(0.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .tail(0, -23.72F, -10.91F, 0.0F)
            .tail(1, -8.64F, -8.18F, 0.0F)
            .tail(2, -4.54F, -6.82F, 0.0F)
            .tail(3, -4.54F, -6.82F, 0.0F)
            .tail(4, -4.54F, -9.09F, 0.0F)
            .leftArm(-99.2643F, 11.8491F, -128.4635F)
            .leftForearm(-85.6899F, -33.881F, 92.0179F)
            .rightArm(-99.2643F, -11.8491F, 128.4635F)
            .rightForearm(-85.6899F, 33.881F, -92.0179F)
            .leftLeg(-20.0F, 0.0F, -2.5F)
            .leftLowerLeg(25.0F, 0.0F, 0.0F)
            .rightLeg(-5.0F, 0.0F, 1.0F)
            .rightLowerLeg(10.0F, 0.0F, 0.0F);
    private static final Frame DOUBLE_HIT_3 = frame()
            .head(0.0F, 0.0F, 0.0F)
            .beak(25.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .tail(0, -27.0F, -15.0F, 0.0F)
            .tail(1, -10.0F, -15.0F, 0.0F)
            .tail(2, -10.0F, 15.0F, 0.0F)
            .tail(3, -10.0F, 15.0F, 0.0F)
            .tail(4, -10.0F, 20.0F, 0.0F)
            .leftArm(0.0F, 0.0F, -120.0F)
            .leftForearm(-90.0F, -75.0F, 90.0F)
            .rightArm(0.0F, 0.0F, 120.0F)
            .rightForearm(-90.0F, 75.0F, -90.0F)
            .leftLeg(-20.0F, 0.0F, -2.5F)
            .leftLowerLeg(25.0F, 0.0F, 0.0F)
            .rightLeg(-5.0F, 0.0F, 1.0F)
            .rightLowerLeg(10.0F, 0.0F, 0.0F);

    // RHCP donor potency-four combo alternate.
    private static final Frame COMBO_ALT_1 = frame()
            .head(0.0F, 0.0F, 0.0F)
            .body(18.0F, 0.0F, -18.0F)
            .torso(0.0F, 10.0F, 0.0F)
            .tail(0, -14.36F, 18.0F, 0.0F)
            .tail(1, -6.0F, 10.0F, 0.0F)
            .tail(2, 4.0F, 15.0F, 0.0F)
            .tail(3, 4.0F, 15.0F, 0.0F)
            .tail(4, 4.0F, 20.0F, 0.0F)
            .leftArm(-3.0F, 15.0F, -54.0F)
            .leftForearm(-90.5F, 0.0F, 0.0F)
            .rightArm(-5.0F, 5.0F, 90.0F)
            .rightForearm(-98.735F, 9.9319F, -75.5183F)
            .leftLeg(-60.0F, 0.0F, -10.0F)
            .leftLowerLeg(80.0F, 0.0F, 0.0F)
            .rightLeg(10.0F, 0.0F, 10.0F)
            .rightLowerLeg(27.0F, 0.0F, 0.0F);
    private static final Frame COMBO_ALT_2 = frame()
            .head(8.33F, 0.0F, -8.33F)
            .beak(0.0F, 0.0F, 0.0F)
            .body(45.0F, 0.0F, -45.0F)
            .torso(0.0F, 30.0F, 0.0F)
            .tail(0, -13.4F, 45.0F, 0.0F)
            .tail(1, -7.5F, 10.0F, 0.0F)
            .tail(2, -5.0F, 15.0F, 0.0F)
            .tail(3, -5.0F, 15.0F, 0.0F)
            .tail(4, -5.0F, 20.0F, 0.0F)
            .leftArm(0.0F, 0.0F, -90.0F)
            .leftForearm(-95.0F, 0.0F, 0.0F)
            .rightArm(23.0292F, 21.9344F, 133.1226F)
            .rightForearm(-90.0F, 45.0F, -90.0F)
            .leftLeg(-60.0F, 0.0F, -10.0F)
            .leftLowerLeg(80.0F, 0.0F, 0.0F)
            .rightLeg(10.0F, 0.0F, 10.0F)
            .rightLowerLeg(27.0F, 0.0F, 0.0F);
    private static final Frame COMBO_ALT_3 = frame()
            .head(10.0F, 0.0F, -10.0F)
            .beak(25.0F, 0.0F, 0.0F)
            .body(45.0F, 0.0F, -45.0F)
            .torso(0.0F, -20.0F, 0.0F)
            .tail(0, -13.4F, 45.0F, 0.0F)
            .tail(1, -7.5F, 10.0F, 0.0F)
            .tail(2, -5.0F, 15.0F, 0.0F)
            .tail(3, -5.0F, 15.0F, 0.0F)
            .tail(4, -5.0F, 20.0F, 0.0F)
            .leftArm(0.0F, 90.0F, -120.0F)
            .leftForearm(-90.0F, 0.0F, 90.0F)
            .rightArm(-52.5F, -35.0F, 105.0F)
            .rightForearm(-90.0F, 72.5F, -90.0F)
            .leftLeg(-60.0F, 0.0F, -10.0F)
            .leftLowerLeg(120.0F, 0.0F, 0.0F)
            .rightLeg(-50.0F, 30.0F, 10.0F)
            .rightLowerLeg(100.0F, 0.0F, 0.0F);

    // RHCP donor dodge taunt loop (TAUNT_POSE), where both counters start.
    private static final Frame TAUNT_1 = counterStance()
            .head(25.0F, 110.0F, 25.5F)
            .body(0.0F, 45.0F, 0.0F)
            .tail(0, -27.0F, 0.0F, 0.0F)
            .tail(1, -5.0F, 10.0F, 0.0F)
            .tail(2, -5.0F, 15.0F, 0.0F)
            .tail(3, 0.0F, 15.0F, 0.0F)
            .tail(4, 2.5F, 20.0F, 0.0F)
            .leftArm(-68.5365F, 3.9942F, -58.1966F)
            .leftForearm(-90.0F, -20.0F, 90.0F)
            .rightArm(40.0458F, -17.6444F, 7.1442F)
            .rightForearm(-57.5F, 0.0F, 0.0F);
    private static final Frame TAUNT_2 = counterStance()
            .head(25.0F, 110.0F, 25.5F)
            .body(0.0F, 45.0F, 0.0F)
            .tail(0, -27.0F, 0.0F, 0.0F)
            .tail(1, -5.0F, -10.0F, 0.0F)
            .tail(2, -5.0F, -15.0F, 0.0F)
            .tail(3, 0.0F, -15.0F, 0.0F)
            .tail(4, 2.5F, -20.0F, 0.0F)
            .leftArm(-68.5365F, 3.9942F, -58.1966F)
            .leftForearm(-90.0F, 0.0F, 90.0F)
            .rightArm(40.0458F, -17.6444F, 7.1442F)
            .rightForearm(-57.5F, 0.0F, 0.0F);
    private static final float TAUNT_LOOP_TICKS = 40.0F;

    // RHCP donor recharge loop (RECHARGE_POSE).
    private static final Frame RECHARGE_1 = frame()
            .tail(0, -27.0F, 0.0F, 0.0F)
            .tail(1, -12.5F, 10.0F, 0.0F)
            .tail(2, 15.0F, 15.0F, 0.0F)
            .tail(3, 15.0F, 15.0F, 0.0F)
            .tail(4, 15.0F, 20.0F, 0.0F)
            .leftArm(-40.8764F, -57.2995F, 13.8593F)
            .leftForearm(-90.0F, -10.0F, 90.0F)
            .rightArm(-40.8764F, 57.2995F, -13.8593F)
            .rightForearm(-90.0F, 10.0F, -90.0F)
            .leftLeg(-60.0F, -45.0F, 0.0F)
            .leftLowerLeg(90.0F, 0.0F, 0.0F)
            .rightLeg(-60.0F, 45.0F, 0.0F)
            .rightLowerLeg(90.0F, 0.0F, 0.0F);
    private static final Frame RECHARGE_2 = frame()
            .tail(0, -27.0F, 0.0F, 0.0F)
            .tail(1, -12.5F, -10.0F, 0.0F)
            .tail(2, 15.0F, -15.0F, 0.0F)
            .tail(3, 15.0F, -15.0F, 0.0F)
            .tail(4, 15.0F, -20.0F, 0.0F)
            .leftArm(-38.3764F, -57.2995F, 13.8593F)
            .leftForearm(-90.0F, -7.5F, 90.0F)
            .rightArm(-38.3764F, 57.2995F, -13.8593F)
            .rightForearm(-90.0F, 7.5F, -90.0F)
            .leftLeg(-62.5F, -45.0F, 0.0F)
            .leftLowerLeg(92.5F, 0.0F, 0.0F)
            .rightLeg(-62.5F, 45.0F, 0.0F)
            .rightLowerLeg(92.5F, 0.0F, 0.0F);
    private static final float RECHARGE_LOOP_TICKS = 30.0F;

    // The inherited 1.16 HumanoidStandModel two-handed barrage (StandTwoHandedBarrageAnimation).
    private static final Frame BARRAGE_IMPACT = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, -30.0F, 0.0F)
            .leftArm(22.5F, 0.0F, -60.0F)
            .leftForearm(-135.0F, 0.0F, 0.0F)
            .rightArm(-90.0F, 0.0F, 90.0F)
            .rightForearm(0.0F, 0.0F, 0.0F)
            .pitch(PITCH_FULL);
    private static final Frame BARRAGE_READY = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 0.0F, 0.0F)
            .leftArm(-33.75F, 0.0F, -75.0F)
            .leftForearm(-67.5F, 0.0F, 0.0F)
            .rightArm(-33.75F, 0.0F, 75.0F)
            .rightForearm(-67.5F, 0.0F, 0.0F)
            .pitch(PITCH_FULL);
    private static final Frame BARRAGE_RECOVERY = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 0.0F, 0.0F)
            .leftArm(22.5F, 0.0F, -22.5F)
            .leftForearm(-75.0F, 7.5F, 22.5F)
            .rightArm(22.5F, 0.0F, 22.5F)
            .rightForearm(-75.0F, -7.5F, -22.5F);
    // TwoHandedBarrageAnimation: one punch of each fist every 4 ticks.
    private static final float BARRAGE_LOOP_TICKS = 4.0F;

    // The inherited 1.16 HumanoidStandModel guard; the arms follow the look (block()).
    private static final Frame BLOCK = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 0.0F, 0.0F)
            .rightForearm(-90.0F, 30.0F, -90.0F)
            .leftForearm(-90.0F, -30.0F, 90.0F);

    // RHCP donor counterattack timeline.
    private static final Frame COUNTER_1 = counterStance()
            .head(0.0F, 0.0F, 0.0F)
            .body(0.0F, -15.0F, 0.0F)
            .leftArm(-68.5365F, 3.9942F, -58.1966F)
            .leftForearm(-90.0F, -20.0F, 90.0F)
            .rightArm(-22.5F, 0.0F, 22.5F)
            .rightForearm(-105.0F, 0.0F, -15.0F);
    private static final Frame COUNTER_2 = counterStance()
            .body(0.0F, -25.0F, 0.0F)
            .leftArm(-15.52F, 2.0F, -36.6F)
            .leftForearm(-102.5F, -2.5F, 48.75F)
            .rightArm(5.941F, 8.421F, 69.059F)
            .rightForearm(-75.0F, 0.0F, 0.0F);
    private static final Frame COUNTER_3 = counterStance()
            .body(0.0F, -30.0F, 0.0F)
            .leftArm(37.5F, 0.0F, -15.0F)
            .leftForearm(-115.0F, 15.0F, 7.5F)
            .rightArm(-85.7155F, 0.1901F, 89.8426F)
            .rightForearm(-10.0F, 0.0F, 0.0F);

    // RHCP donor blitz slash timeline.
    private static final Frame BLITZ_1 = counterStance()
            .head(0.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .leftArm(-64.4695F, 11.3125F, 5.3525F)
            .leftForearm(-80.0F, 20.0F, 0.0F)
            .rightArm(0.0F, -30.0F, 25.0F)
            .rightForearm(-61.1136F, 15.5839F, -25.9629F);
    private static final Frame BLITZ_2 = counterStance()
            .head(0.0F, -10.0F, 0.0F)
            .body(0.0F, -17.5F, 0.0F)
            .leftArm(-65.0F, 0.0F, 0.0F)
            .leftForearm(-80.0F, 0.0F, 0.0F)
            .rightArm(-39.2315F, -66.7163F, 88.4349F)
            .rightForearm(-148.8864F, 47.9366F, -140.8895F);
    private static final Frame BLITZ_3 = counterStance()
            .head(0.0F, -15.0F, 0.0F)
            .body(0.0F, -26.25F, 0.0F)
            .leftArm(3.54F, 3.5333F, -89.8908F)
            .leftForearm(-85.0F, -40.0F, 45.0F)
            .rightArm(-59.02F, -53.62F, 125.78F)
            .rightForearm(-90.0F, 80.0F, -90.0F);
    private static final Frame BLITZ_4 = counterStance()
            .head(0.0F, -20.0F, 0.0F)
            .body(0.0F, -35.0F, 0.0F)
            .leftArm(75.0F, 0.0F, -90.0F)
            .leftForearm(-90.0F, -80.0F, 90.0F)
            .rightArm(-78.8136F, -40.5276F, 163.1287F)
            .rightForearm(-180.0F, 0.0F, -180.0F);
    private static final Frame BLITZ_5 = counterStance()
            .head(0.0F, -5.0F, 0.0F)
            .body(0.0F, 2.5F, 0.0F)
            .leftArm(31.25F, 0.0F, -90.0F)
            .leftForearm(-90.0F, -80.0F, 90.0F)
            .rightArm(9.2665F, -5.1306F, 93.6587F)
            .rightForearm(-92.0259F, 29.2531F, -90.963F);
    private static final Frame BLITZ_6 = counterStance()
            .head(0.0F, 10.0F, 0.0F)
            .body(0.0F, 40.0F, 0.0F)
            .leftArm(-12.5F, 0.0F, -90.0F)
            .leftForearm(-90.0F, -80.0F, 90.0F)
            .rightArm(79.1282F, 19.3597F, 144.9927F)
            .rightForearm(-90.0F, 5.0F, -90.0F);
    private static final Frame BLITZ_7 = counterStance()
            .head(0.0F, 60.0F, 0.0F)
            .body(0.0F, 50.0F, 0.0F)
            .leftArm(-95.0F, 0.0F, -90.0F)
            .leftForearm(-90.0F, -32.5F, 90.0F)
            .rightArm(64.2845F, 0.1901F, 89.8426F)
            .rightForearm(-90.0F, 80.0F, -90.0F);
    private static final Frame BLITZ_8 = counterStance()
            .head(0.0F, -2.5F, 0.0F)
            .body(0.0F, 60.0F, 0.0F)
            .leftArm(-70.0F, 20.0F, 0.0F)
            .leftForearm(-90.0F, -20.0F, 90.0F)
            .rightArm(26.6322F, 3.1053F, 15.4394F)
            .rightForearm(-90.0F, 32.5F, -90.0F);

    private static final Frame[] JAB_WINDUP =
            {JAB_1, JAB_2, JAB_3, JAB_3};
    private static final float[] JAB_WINDUP_MARKS =
            {0.0F, 0.5F, 0.75F, 1.0F};
    // jabEnd is the other hand's jabStart.
    private static final Frame[] JAB_PERFORM =
            {JAB_3, JAB_3, JAB_4, JAB_1};
    private static final boolean[] JAB_PERFORM_OTHER_HAND =
            {false, false, false, true};
    private static final float[] JAB_PERFORM_MARKS =
            {0.0F, 0.25F, 0.5F, 1.0F};
    private static final Frame[] JAB_RECOVERY =
            {JAB_1, JAB_1, EMPTY};
    private static final boolean[] JAB_RECOVERY_OTHER_HAND =
            {true, true, false};
    private static final float[] JAB_RECOVERY_MARKS =
            {0.0F, 0.75F, 1.0F};
    private static final Frame[] HEAVY_WINDUP =
            {EMPTY, HEAVY_1, HEAVY_2, HEAVY_3, HEAVY_4};
    private static final float[] HEAVY_WINDUP_MARKS =
            {0.0F, 0.5F, 0.9F, 0.95F, 1.0F};
    private static final Frame[] HEAVY_RECOVERY =
            {HEAVY_4, HEAVY_4, HEAVY_5, HEAVY_5, EMPTY};
    private static final float[] HEAVY_RECOVERY_MARKS =
            {0.0F, 0.5F, 0.7F, 0.8F, 1.0F};
    private static final Frame[] HEAVY_ALT_WINDUP =
            {HEAVY_ALT_1, HEAVY_ALT_2, HEAVY_ALT_3,
                    HEAVY_ALT_4, HEAVY_ALT_5};
    private static final float[] HEAVY_ALT_WINDUP_MARKS =
            {0.0F, 0.316F, 0.622F, 0.95F, 1.0F};
    private static final Frame[] HEAVY_ALT_RECOVERY =
            {HEAVY_ALT_5, HEAVY_ALT_5, EMPTY};
    private static final float[] HEAVY_ALT_RECOVERY_MARKS =
            {0.0F, 0.5F, 1.0F};
    private static final Frame[] DOUBLE_HIT_PERFORM =
            {DOUBLE_HIT_1, DOUBLE_HIT_2, DOUBLE_HIT_3};
    private static final float[] DOUBLE_HIT_PERFORM_MARKS =
            {0.0F, 0.5F, 1.0F};
    // The finisher has no recovery pose: PosedActionAnimation eases its last pose to idle.
    private static final Frame[] DOUBLE_HIT_RECOVERY =
            {DOUBLE_HIT_3, EMPTY};
    private static final float[] DOUBLE_HIT_RECOVERY_MARKS =
            {0.0F, 1.0F};
    private static final Frame[] COMBO_ALT_WINDUP =
            {EMPTY, COMBO_ALT_1, COMBO_ALT_2, COMBO_ALT_3};
    private static final float[] COMBO_ALT_WINDUP_MARKS =
            {0.0F, 0.33335F, 0.9F, 1.0F};
    private static final Frame[] COMBO_ALT_RECOVERY =
            {COMBO_ALT_3, COMBO_ALT_3, COMBO_ALT_3, EMPTY};
    private static final float[] COMBO_ALT_RECOVERY_MARKS =
            {0.0F, 0.5F, 0.7F, 1.0F};
    private static final Frame[] BARRAGE_RECOVERY_FRAMES =
            {BARRAGE_READY, BARRAGE_RECOVERY, BARRAGE_RECOVERY, EMPTY};
    private static final float[] BARRAGE_RECOVERY_MARKS =
            {0.0F, 0.25F, 0.5F, 1.0F};
    private static final Frame[] COUNTER_WINDUP =
            {TAUNT_1, COUNTER_1, COUNTER_2, COUNTER_3, COUNTER_3};
    private static final float[] COUNTER_WINDUP_MARKS =
            {0.0F, 0.42857143F, 0.5714286F, 0.85714287F, 1.0F};
    private static final Frame[] COUNTER_RECOVERY =
            {COUNTER_3, EMPTY};
    private static final float[] COUNTER_RECOVERY_MARKS =
            {0.0F, 1.0F};
    private static final Frame[] BLITZ_WINDUP =
            {TAUNT_1, BLITZ_1, BLITZ_2, BLITZ_3, BLITZ_4,
                    BLITZ_5, BLITZ_6, BLITZ_7, BLITZ_7};
    private static final float[] BLITZ_WINDUP_MARKS =
            {0.0F, 0.23076923F, 0.3846154F, 0.46153846F, 0.53846157F,
                    0.61538464F, 0.6923077F, 0.7692308F, 1.0F};
    private static final Frame[] BLITZ_RECOVERY =
            {BLITZ_7, BLITZ_8, BLITZ_8, EMPTY};
    private static final float[] BLITZ_RECOVERY_MARKS =
            {0.0F, 0.05F, 0.8F, 1.0F};

    public RhcpModel(ModelPart root) {
        super(root);
        Model_1_21_2plus model = (Model_1_21_2plus) (Object) this;
        tail = part(model, "tail");
        tail1 = part(model, "tail_1");
        tail2 = part(model, "tail_2");
        tail3 = part(model, "tail_3");
        tail4 = part(model, "tail_4");
        beakLower = part(model, "beak_lower");
        // 1.16 posed leftArm under its leftArmXRot, which only carried the look pitch.
        actionParts = new ModelPart[] {
                body_rot, torso_bend, head, beakLower,
                left_arm, left_arm_bend,
                right_arm, right_arm_bend,
                left_leg_xrot, left_leg_bend,
                right_leg_xrot, right_leg_bend,
                tail, tail1, tail2, tail3, tail4
        };
        joints = new ModelPart[] {
                part(model, "left_arm_joint"), part(model, "right_arm_joint"),
                part(model, "left_leg_joint"), part(model, "right_leg_joint")
        };
    }

    // RHCPModel: HumanoidStandModel's base boxes without the torso, and RHCP's own cubes.
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition standPos = empty(root, "stand_pos", PartPose.ZERO);
        PartDefinition bodyRot = empty(standPos, "body_rot", PartPose.ZERO);
        PartDefinition body = empty(bodyRot, "body", PartPose.ZERO);
        PartDefinition torso = empty(
                body, "torso", PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition torsoBend = empty(
                torso, "torso_bend", PartPose.ZERO);

        // 1.16 HumanoidStandModel: the head is not a child of the body.
        PartDefinition head = empty(
                standPos,
                "head",
                PartPose.ZERO);
        PartDefinition headRot = head.addOrReplaceChild(
                "head_rot",
                box(0, 0)
                        .addBox(-4, -8, -4, 8, 8, 8)
                        .texOffs(32, 0)
                        .addBox(
                                -4, -8, -4, 8, 3, 8,
                                new CubeDeformation(0.1F))
                        .texOffs(32, 11)
                        .addBox(
                                -2, -5.25F, -4.75F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(36, 11)
                        .addBox(
                                1, -5.25F, -4.75F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(44, 11)
                        .addBox(
                                3.75F, -5.25F, -2, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(48, 11)
                        .addBox(
                                3.75F, -5.25F, 1, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(56, 11)
                        .addBox(
                                1, -5.25F, 3.75F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(60, 11)
                        .addBox(
                                -2, -5.25F, 3.75F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(36, 13)
                        .addBox(
                                -4.75F, -5.25F, 1, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(40, 13)
                        .addBox(
                                -4.75F, -5.25F, -2, 1, 1, 1,
                                new CubeDeformation(-0.1F)),
                PartPose.ZERO);

        PartDefinition cornerA = empty(
                headRot,
                "corner_a",
                PartPose.offsetAndRotation(
                        4.5F, 0.0F, -4.5F,
                        0.0F, -0.7854F, 0.0F));
        cornerA.addOrReplaceChild(
                "cube",
                box(40, 11)
                        .addBox(
                                -0.5F, -5.25F, 0.25F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(32, 13)
                        .addBox(
                                -0.5F, -5.25F, 11.5F, 1, 1, 1,
                                new CubeDeformation(-0.1F)),
                PartPose.ZERO);
        PartDefinition cornerB = empty(
                headRot,
                "corner_b",
                PartPose.offsetAndRotation(
                        -4.5F, 0.0F, -4.5F,
                        0.0F, -0.7854F, 0.0F));
        cornerB.addOrReplaceChild(
                "cube",
                box(44, 13)
                        .addBox(
                                0.25F, -5.25F, -0.5F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(52, 11)
                        .addBox(
                                11.5F, -5.25F, -0.5F, 1, 1, 1,
                                new CubeDeformation(-0.1F)),
                PartPose.ZERO);

        PartDefinition beak = headRot.addOrReplaceChild(
                "beak_upper",
                box(0, 16).addBox(-0.5F, -2, -6, 1, 1, 2),
                PartPose.offset(0.0F, -0.05F, 0.0F));
        beak.addOrReplaceChild(
                "beak_tip",
                box(3, 16).addBox(-0.5F, 0, 0, 1, 1, 3),
                PartPose.offsetAndRotation(
                        0, -2, -6, 0.3491F, 0, 0));
        beak.addOrReplaceChild(
                "beak_left",
                box(8, 19).addBox(-1, -1.05F, 0, 1, 1, 3),
                PartPose.offsetAndRotation(
                        0.5F, -0.95F, -6, 0, 0.2618F, 0));
        beak.addOrReplaceChild(
                "beak_right",
                box(0, 20).addBox(0, -1.05F, 0, 1, 1, 3),
                PartPose.offsetAndRotation(
                        -0.5F, -0.95F, -6, 0, -0.2618F, 0));
        PartDefinition lowerBeak = headRot.addOrReplaceChild(
                "beak_lower",
                box(21, 16)
                        .addBox(-0.5F, -0.3F, -2, 1, 1, 2)
                        .texOffs(21, 20)
                        .addBox(-0.5F, -0.45F, -1.5F, 1, 1, 2),
                PartPose.offset(0, -0.75F, -4));
        lowerBeak.addOrReplaceChild(
                "beak_lower_left",
                box(24, 16).addBox(-1, -1.05F, 0, 1, 1, 3),
                PartPose.offsetAndRotation(
                        0.5F, 0.75F, -2, 0, 0.2618F, 0));
        lowerBeak.addOrReplaceChild(
                "beak_lower_right",
                box(16, 16).addBox(0, -1.05F, 0, 1, 1, 3),
                PartPose.offsetAndRotation(
                        -0.5F, 0.75F, -2, 0, -0.2618F, 0));

        PartDefinition torsoNoArms = torsoBend.addOrReplaceChild(
                "torso_no_arms",
                box(0, 64)
                        .addBox(-4, 0, -2, 8, 6, 4)
                        .texOffs(20, 64)
                        .addBox(
                                -3.5F, 1.1F, -2, 7, 3, 1,
                                new CubeDeformation(0.4F))
                        .texOffs(15, 74)
                        .addBox(-0.5F, 2.5F, 2, 1, 1, 1)
                        .texOffs(19, 74)
                        .addBox(-0.5F, 4, 1.9F, 1, 1, 1)
                        .texOffs(18, 76)
                        .addBox(-0.5F, 5.25F, 0.65F, 1, 1, 2)
                        .texOffs(0, 74)
                        .addBox(-3, 6, -1.5F, 6, 4, 3)
                        .texOffs(0, 81)
                        .addBox(-3.5F, 10, -2, 7, 2, 4)
                        .texOffs(0, 87)
                        .addBox(
                                -3.5F, 10, -2, 7, 1, 4,
                                new CubeDeformation(0.1F))
                        .texOffs(18, 79)
                        .addBox(-0.5F, 6.5F, 1.5F, 1, 1, 1)
                        .texOffs(18, 88)
                        .addBox(-0.5F, 11.1F, -2.1F, 1, 2, 1)
                        .texOffs(0, 88)
                        .addBox(-0.5F, 11.1F, -2.6F, 1, 2, 1),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        torsoNoArms.addOrReplaceChild(
                "chest_diamond",
                box(24, 68).addBox(
                        -0.5F, -0.5F, -2.3F, 2, 2, 1),
                PartPose.offsetAndRotation(
                        0.0F, 3.8F, 0.0F,
                        0.0F, 0.0F, 0.7854F));
        empty(torso, "torso_lower", PartPose.ZERO);

        PartDefinition tail = torsoNoArms.addOrReplaceChild(
                "tail",
                box(22, 81)
                        .addBox(-1, -1, -1, 2, 2, 4)
                        .texOffs(22, 83)
                        .addBox(-0.5F, -2, 0.45F, 1, 1, 1)
                        .texOffs(22, 81)
                        .addBox(-0.5F, -1.85F, 1.7F, 1, 1, 1),
                PartPose.offset(0, 11.25F, 2));
        PartDefinition tail1 = tail.addOrReplaceChild(
                "tail_1",
                box(34, 81).addBox(
                        -1, -1, -0.1F, 2, 2, 4,
                        new CubeDeformation(-0.1F))
                        .texOffs(34, 81)
                        .addBox(-0.5F, -1.75F, 1.25F, 1, 1, 1)
                        .texOffs(34, 83)
                        .addBox(-0.5F, -1.9F, 0, 1, 1, 1),
                PartPose.offset(0, 0, 3));
        PartDefinition tail2 = tail1.addOrReplaceChild(
                "tail_2",
                box(46, 81).addBox(
                        -1, -1, -0.2F, 2, 2, 4,
                        new CubeDeformation(-0.2F)),
                PartPose.offset(0, 0, 3.8F));
        PartDefinition tail3 = tail2.addOrReplaceChild(
                "tail_3",
                box(22, 87).addBox(
                        -1, -1, -0.3F, 2, 2, 4,
                        new CubeDeformation(-0.3F)),
                PartPose.offset(0, 0, 3.6F));
        tail3.addOrReplaceChild(
                "tail_4",
                box(34, 87).addBox(
                        -1, -1, -0.4F, 2, 2, 4,
                        new CubeDeformation(-0.4F)),
                PartPose.offset(0, 0, 3.4F));

        arm(torsoBend, true);
        arm(torsoBend, false);
        leg(body, true);
        leg(body, false);

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(RhcpRenderState state) {
        super.setupAnim(state);
        if (state.action.pose == null) {
            setupDonorAnim(state);
        }
        updateJoint(joints[0], left_arm_bend);
        updateJoint(joints[1], right_arm_bend);
        updateJoint(joints[2], left_leg_bend);
        updateJoint(joints[3], right_leg_bend);
    }

    private void setupDonorAnim(RhcpRenderState state) {
        armPitch = state.xRot * Mth.DEG_TO_RAD;
        String actionName = state.actionName;
        boolean idle = actionName == null || actionName.isEmpty();
        boolean summon = idle && state.summonPose >= 0;
        // 1.16 kept the idle loop running through an action that has no pose of its own.
        boolean loop = !summon && (idle || !hasDonorPose(actionName));
        applyIdlePose(actionParts, state.floating, state.moving, state.idleLoopTick, loop);
        if (summon) {
            applySummon(state);
        }
        else if (!idle) {
            switch (actionName) {
                case "rhcp_punch_off", "rhcp_punch" -> applyLight(state);
                case "rhcp_barrage" -> applyBarrage(state);
                case "rhcp_combo_punch" -> applyCombo(state);
                case "rhcp_heavy_punch" -> applyHeavy(state, state.attackAlternate);
                case "rhcp_electrocute_target" -> applyHeavy(state, false);
                case "rhcp_chase_target" -> applyHeavy(state, true);
                case "rhcp_counterattack" -> applyCounter(actionParts, false,
                        state.actionPhase, state.actionPhaseRatio, !state.mirrorAction);
                case "rhcp_blitz_attack" -> applyCounter(actionParts, true,
                        state.actionPhase, state.actionPhaseRatio, !state.mirrorAction);
                case "rhcp_block" -> applyBlock(state);
                case "rhcp_recharge" -> applyLoopPose(state, RECHARGE_1, RECHARGE_2, RECHARGE_LOOP_TICKS);
                case "rhcp_dodge" -> applyLoopPose(state, TAUNT_1, TAUNT_2, TAUNT_LOOP_TICKS);
                default -> {
                }
            }
        }
        if (!state.floating && state.moving
                && !("rhcp_combo_punch".equals(actionName) && state.potency >= 4)
                && !dodging(actionName, state.actionPhase)) {
            applyWalkingPose(actionParts, state.walkAnimationPos, state.walkAnimationSpeed,
                    state.walkSpeedFactor, state.userCrouching);
        }
    }

    // The moves RHCPModel and HumanoidStandModel have a StandPose animation for.
    static boolean hasDonorPose(String actionName) {
        return switch (actionName) {
            case "rhcp_punch_off", "rhcp_punch", "rhcp_barrage", "rhcp_combo_punch",
                    "rhcp_heavy_punch", "rhcp_electrocute_target", "rhcp_chase_target",
                    "rhcp_counterattack", "rhcp_blitz_attack", "rhcp_block",
                    "rhcp_recharge", "rhcp_dodge" -> true;
            default -> false;
        };
    }

    // 1.16 RHCPDodge.dodge, which RHCPModel.walkAnim reads: the dodge before
    // its recovery, and both counters in every phase.
    private static boolean dodging(String actionName, ActionPhase phase) {
        return "rhcp_dodge".equals(actionName) && phase != ActionPhase.RECOVERY
                || "rhcp_counterattack".equals(actionName)
                || "rhcp_blitz_attack".equals(actionName);
    }

    // 1.16 RHCPModel COUNTERATTACK_POSE and BLITZ_ATTACK_POSE: the windup
    // starts from the dodge taunt of the punching hand's side (ModelPoseSided)
    // and its last pose is held through PERFORM (PosedActionAnimation).
    private static void applyCounter(ModelPart[] parts, boolean blitz,
            ActionPhase phase, float progress, boolean mirrorTaunt) {
        if (phase == ActionPhase.WINDUP) {
            applyTimeline(parts,
                    blitz ? BLITZ_WINDUP : COUNTER_WINDUP,
                    blitz ? BLITZ_WINDUP_MARKS : COUNTER_WINDUP_MARKS,
                    progress, mirrorTaunt, false);
        }
        else if (phase == ActionPhase.PERFORM) {
            Frame hit = blitz ? BLITZ_7 : COUNTER_3;
            applyTransition(parts, hit, false, hit, false, 1.0F);
        }
        else if (phase == ActionPhase.RECOVERY) {
            applyTimeline(parts,
                    blitz ? BLITZ_RECOVERY : COUNTER_RECOVERY,
                    blitz ? BLITZ_RECOVERY_MARKS : COUNTER_RECOVERY_MARKS,
                    progress, false, false);
        }
    }

    // The dodge stance legs every taunt and counter pose shares.
    private static Frame counterStance() {
        return frame()
                .leftLeg(-10.0F, 0.0F, -2.5F)
                .leftLowerLeg(25.0F, 0.0F, 0.0F)
                .rightLeg(-35.0F, 0.0F, 2.5F)
                .rightLowerLeg(70.0F, 0.0F, 0.0F);
    }

    private static Frame groundedIdle() {
        return frame().beak(0, 0, 0).body(10, 0, 0).torso(0, 0, 0)
                .tail(0, -27, 0, 0)
                .tail(1, -5, 10, 0)
                .tail(2, 10, 15, 0)
                .tail(3, 10, 15, 0)
                .tail(4, 10, 20, 0)
                .leftArm(15, 0, -32.5F).leftForearm(-90, 50, -60)
                .rightArm(15, 0, 32.5F).rightForearm(-90, -50, 60)
                .leftLeg(-20, 0, -10).leftLowerLeg(25, 0, 0)
                .rightLeg(-20, 0, 10).rightLowerLeg(25, 0, 0);
    }

    private static Frame tailIdleLoop() {
        return frame().tail(1, -5, 30, 0)
                .tail(2, 10, 45, 0)
                .tail(3, 10, 45, 0)
                .tail(4, 10, 60, 0);
    }

    private static void applyIdlePose(ModelPart[] parts, boolean floating, boolean moving,
            float loopTicks, boolean animateLoop) {
        Frame base = floating ? FLOATING_IDLE : moving ? MOVING_IDLE : GROUNDED_IDLE;
        for (int index = 0; index < PART_COUNT; index++) {
            if (base.defined(index, false)) {
                rotate(parts[index], base.angle(index, 0, false),
                        base.angle(index, 1, false), base.angle(index, 2, false));
            }
        }
        if (animateLoop) {
            Frame loop = floating ? FLOATING_IDLE_LOOP : GROUNDED_IDLE_LOOP;
            // The donor uses an unclamped [-1, 0] easing, not a [0, 1] blend.
            float weight = (Mth.sin((float) Math.PI * (loopTicks / 40.0F - 0.5F)) - 1.0F) / 2.0F;
            for (int index = 0; index < PART_COUNT; index++) {
                ModelPart part = parts[index];
                if (part != null && loop.defined(index, false)) {
                    rotate(part, Mth.lerp(weight, part.xRot, loop.angle(index, 0, false)),
                            Mth.lerp(weight, part.yRot, loop.angle(index, 1, false)),
                            Mth.lerp(weight, part.zRot, loop.angle(index, 2, false)));
                }
            }
        }
    }

    private static void applyWalkingPose(ModelPart[] parts, float position, float speed,
            float speedFactor, boolean crouching) {
        float bodyX = parts[BODY].xRot;
        float rightRot = Mth.cos(position * 0.6662F) * 1.4F * speed / speedFactor;
        float leftRot = Mth.cos(position * 0.6662F + (float) Math.PI)
                * 1.4F * speed / speedFactor - bodyX;
        if (crouching) {
            rightRot /= 2.0F;
            leftRot /= 2.0F;
        }
        parts[RIGHT_LEG].xRot = rightRot - bodyX;
        parts[RIGHT_LOWER_LEG].xRot = rightRot < 0 ? 0 : rightRot / 4.0F;
        parts[LEFT_LEG].xRot = leftRot - bodyX;
        parts[LEFT_LOWER_LEG].xRot = leftRot < 0 ? 0 : leftRot / 4.0F;
    }

    private static void updateJoint(ModelPart joint, ModelPart limb) {
        if (joint != null && limb != null) {
            rotate(joint, limb.xRot / 2.0F, limb.yRot / 2.0F, limb.zRot / 2.0F);
        }
    }

    // 1.16 LegacyStandAnimator.poseSummon: the summon pose, back to idle over its last quarter.
    private void applySummon(RhcpRenderState state) {
        Frame summon = SUMMON_POSES[state.summonPose % SUMMON_POSES.length];
        float back = Mth.clamp((state.ageInTicks - SUMMON_TICKS)
                / (SUMMON_TICKS * (1.0F - SUMMON_REVERSE_POINT)) + 1.0F, 0.0F, 1.0F);
        transition(summon, false, EMPTY, false, back);
    }

    // 1.16 HumanoidStandModel LIGHT_ATTACK: a jab of the punching hand (ModelPoseSided),
    // which ends in the other hand's start pose.
    private void applyLight(RhcpRenderState state) {
        float progress = state.actionPhaseRatio;
        boolean mirror = state.mirrorAction;
        if (state.actionPhase == ActionPhase.WINDUP) {
            timeline(JAB_WINDUP, null, JAB_WINDUP_MARKS, progress, mirror);
        }
        else if (state.actionPhase == ActionPhase.PERFORM) {
            timeline(JAB_PERFORM, JAB_PERFORM_OTHER_HAND, JAB_PERFORM_MARKS, progress, mirror);
        }
        else if (state.actionPhase == ActionPhase.RECOVERY) {
            timeline(JAB_RECOVERY, JAB_RECOVERY_OTHER_HAND, JAB_RECOVERY_MARKS, progress, mirror);
        }
    }

    private void applyHeavy(
            RhcpRenderState state, boolean alternate) {
        float progress = state.actionPhaseRatio;
        if (alternate) {
            if (state.actionPhase == ActionPhase.WINDUP) {
                timeline(HEAVY_ALT_WINDUP, null,
                        HEAVY_ALT_WINDUP_MARKS,
                        progress, state.mirrorAction);
            }
            else if (state.actionPhase == ActionPhase.RECOVERY) {
                timeline(HEAVY_ALT_RECOVERY, null,
                        HEAVY_ALT_RECOVERY_MARKS,
                        progress, state.mirrorAction);
            }
            else if (state.actionPhase == ActionPhase.PERFORM) {
                applyFrame(HEAVY_ALT_5, state.mirrorAction);
            }
            return;
        }

        if (state.actionPhase == ActionPhase.WINDUP) {
            timeline(HEAVY_WINDUP, null, HEAVY_WINDUP_MARKS, progress, false);
        }
        else if (state.actionPhase == ActionPhase.RECOVERY) {
            timeline(HEAVY_RECOVERY, null,
                    HEAVY_RECOVERY_MARKS,
                    progress, false);
        }
        else if (state.actionPhase == ActionPhase.PERFORM) {
            applyFrame(HEAVY_4, false);
        }
    }

    private void applyCombo(RhcpRenderState state) {
        float progress = state.actionPhaseRatio;
        if (state.comboAlternate) {
            if (state.actionPhase == ActionPhase.WINDUP) {
                timeline(COMBO_ALT_WINDUP, null,
                        COMBO_ALT_WINDUP_MARKS,
                        progress, false);
            }
            else if (state.actionPhase == ActionPhase.RECOVERY) {
                timeline(COMBO_ALT_RECOVERY, null,
                        COMBO_ALT_RECOVERY_MARKS,
                        progress, false);
            }
            else if (state.actionPhase == ActionPhase.PERFORM) {
                applyFrame(COMBO_ALT_3, false);
            }
            return;
        }

        if (state.actionPhase == ActionPhase.WINDUP) {
            transition(EMPTY, false, DOUBLE_HIT_1, false, progress);
        }
        else if (state.actionPhase == ActionPhase.PERFORM) {
            timeline(DOUBLE_HIT_PERFORM, null,
                    DOUBLE_HIT_PERFORM_MARKS,
                    progress, false);
        }
        else if (state.actionPhase == ActionPhase.RECOVERY) {
            timeline(DOUBLE_HIT_RECOVERY, null,
                    DOUBLE_HIT_RECOVERY_MARKS,
                    progress, false);
        }
    }

    // 1.16 HumanoidStandModel BARRAGE (StandTwoHandedBarrageAnimation): in PERFORM the
    // right side's loop from the left fist out to the right one and back, idle in the windup.
    private void applyBarrage(RhcpRenderState state) {
        if (state.actionPhase == ActionPhase.PERFORM) {
            float loop = state.ageInTicks / BARRAGE_LOOP_TICKS;
            transition(BARRAGE_IMPACT, true, BARRAGE_IMPACT, false,
                    barrageHitEasing(loop - Mth.floor(loop)));
        }
        else if (state.actionPhase == ActionPhase.RECOVERY) {
            timeline(BARRAGE_RECOVERY_FRAMES, null, BARRAGE_RECOVERY_MARKS,
                    state.actionPhaseRatio, false);
        }
    }

    // 1.16 StandTwoHandedBarrageAnimation.animateSwing and beforeSwingAfterimageRender: an
    // afterimage arm takes the same loop at its own completion, rolled out by its offset,
    // then the arms' look pitch is turned on top of that roll (applyXRotation).
    void poseBarrageAfterimage(float loopCompletion, HumanoidArm side, float xRotDeg, float zRotOffset) {
        armPitch = xRotDeg * Mth.DEG_TO_RAD;
        boolean left = side == HumanoidArm.LEFT;
        float swing = barrageHitEasing(loopCompletion);
        applyTransition(actionParts, BARRAGE_IMPACT, !left, BARRAGE_IMPACT, left,
                BARRAGE_IMPACT.eased(swing));
        ModelPart arm = left ? left_arm_xrot : right_arm_xrot;
        if (arm != null) {
            Vector3f angles = ArmsFollowPitch.rotateAngles(
                    new Vector3f(0.0F, 0.0F, swing * zRotOffset), armPitch);
            arm.xRot = angles.x;
            arm.yRot = angles.y;
            arm.zRot = angles.z;
        }
    }

    // HumanoidStandModel.barrageHitEasing
    private static float barrageHitEasing(float loopProgress) {
        if (loopProgress < 0.5F) {
            return loopProgress * loopProgress * loopProgress * 8.0F;
        }
        if (loopProgress < 1.0F) {
            float halfSwing = 2.0F * loopProgress - 1.0F;
            return 1.0F - halfSwing * halfSwing * halfSwing;
        }
        return 0.0F;
    }

    // 1.16 HumanoidStandModel BLOCK, held from the button hold on and eased to idle in the recovery.
    private void applyBlock(RhcpRenderState state) {
        Frame block = blockFrame();
        if (state.actionPhase == ActionPhase.RECOVERY) {
            transition(block, false, EMPTY, false, state.actionPhaseRatio);
        }
        else {
            transition(block, false, block, false, 1.0F);
        }
    }

    private Frame blockFrame() {
        float blockXRot = Mth.clamp(armPitch, -60.0F * Mth.DEG_TO_RAD, 60.0F * Mth.DEG_TO_RAD) / 2.0F;
        float rightZ = -Math.abs(blockXRot) / 2.0F + 0.7854F;
        return blockScratch.copyFrom(BLOCK)
                .radians(RIGHT_ARM, -1.5708F + blockXRot, -blockXRot / 2.0F, rightZ)
                .radians(LEFT_ARM, -1.5708F + blockXRot, blockXRot / 2.0F, -rightZ);
    }

    // RHCP donor StandPoseAnimation (recharge, dodge): the off-hand side's loop while
    // the button is held, eased to idle in the recovery.
    private void applyLoopPose(RhcpRenderState state, Frame first, Frame second, float loopTicks) {
        boolean mirror = state.mainArmLeft;
        if (state.actionPhase == ActionPhase.PERFORM) {
            float loop = state.ageInTicks / loopTicks;
            transition(first, mirror, second, mirror, loopSwing(loop - Mth.floor(loop)));
        }
        else if (state.actionPhase == ActionPhase.RECOVERY) {
            transition(first, mirror, EMPTY, false, state.actionPhaseRatio);
        }
    }

    private static float loopSwing(float swing) {
        float halfSwing = swing < 0.4F ? swing * 20.0F / 8.0F
                : swing > 0.6F ? (1.0F - swing) * 20.0F / 8.0F : 1.0F;
        return halfSwing * halfSwing * halfSwing;
    }

    // A 1.16 ModelPoseTransitionMultiple; otherHand marks the frames of the other hand's side.
    private void timeline(
            Frame[] frames,
            boolean[] otherHand,
            float[] marks,
            float progress,
            boolean mirror) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        for (int index = 1; index < marks.length; index++) {
            if (clamped <= marks[index]) {
                transition(
                        frames[index - 1], mirror ^ otherHand(otherHand, index - 1),
                        frames[index], mirror ^ otherHand(otherHand, index),
                        normalize(clamped, marks[index - 1], marks[index]));
                return;
            }
        }
        int last = frames.length - 1;
        boolean lastMirror = mirror ^ otherHand(otherHand, last);
        transition(frames[last], lastMirror, frames[last], lastMirror, 1.0F);
    }

    private static boolean otherHand(boolean[] otherHand, int index) {
        return otherHand != null && otherHand[index];
    }

    // mirrorFirst: a 1.16 ModelPoseSided start pose (the dodge taunt) is the
    // only sided pose of its timeline.
    private static void applyTimeline(
            ModelPart[] parts,
            Frame[] frames,
            float[] marks,
            float progress,
            boolean mirrorFirst,
            boolean mirror) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        for (int index = 1; index < marks.length; index++) {
            if (clamped <= marks[index]) {
                Frame to = frames[index];
                applyTransition(
                        parts,
                        frames[index - 1], index == 1 ? mirrorFirst : mirror,
                        to, mirror,
                        to.eased(normalize(clamped, marks[index - 1], marks[index])));
                return;
            }
        }
        Frame last = frames[frames.length - 1];
        applyTransition(parts, last, mirror, last, mirror, 1.0F);
    }

    private void applyFrame(Frame frame, boolean mirror) {
        transition(frame, mirror, frame, mirror, 1.0F);
    }

    // 1.16 ModelPoseTransition: the first pose in full, then the second by its
    // eased progress, with the arms' look pitch of whichever pose sets one.
    private void transition(
            Frame from,
            boolean mirrorFrom,
            Frame to,
            boolean mirrorTo,
            float progress) {
        float eased = to.eased(Mth.clamp(progress, 0.0F, 1.0F));
        applyTransition(actionParts, from, mirrorFrom, to, mirrorTo, eased);
        applyArmPitch(from, to, eased);
    }

    private void applyArmPitch(Frame from, Frame to, float amount) {
        boolean ownPitch = to.pitch != PITCH_NONE;
        int mode = ownPitch ? to.pitch : from.pitch;
        float weight = ownPitch ? amount : 1.0F;
        float pitch = switch (mode) {
            case PITCH_TURN -> armPitch * weight;
            case PITCH_FULL -> armPitch;
            case PITCH_BACK -> armPitch * (1.0F - weight);
            default -> 0.0F;
        };
        if (left_arm_xrot != null) {
            left_arm_xrot.xRot = pitch;
        }
        if (right_arm_xrot != null) {
            right_arm_xrot.xRot = pitch;
        }
    }

    // 1.16 ModelPoseTransition: the first pose in full, then the second by
    // progress. A part only the first pose names keeps its angle, except on
    // the way to the idle pose (EMPTY), which names every part.
    private static void applyTransition(
            ModelPart[] parts,
            Frame from,
            boolean mirrorFrom,
            Frame to,
            boolean mirrorTo,
            float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        boolean toIdle = to == EMPTY;
        for (int partIndex = 0; partIndex < PART_COUNT; partIndex++) {
            boolean fromDefined = from.defined(partIndex, mirrorFrom);
            boolean toDefined = to.defined(partIndex, mirrorTo);
            if (!fromDefined && !toDefined) {
                continue;
            }
            ModelPart part = parts[partIndex];
            if (part == null) {
                continue;
            }
            float fromX = fromDefined
                    ? from.angle(partIndex, 0, mirrorFrom) : part.xRot;
            float fromY = fromDefined
                    ? from.angle(partIndex, 1, mirrorFrom) : part.yRot;
            float fromZ = fromDefined
                    ? from.angle(partIndex, 2, mirrorFrom) : part.zRot;
            float toX = toDefined
                    ? to.angle(partIndex, 0, mirrorTo) : toIdle ? part.xRot : fromX;
            float toY = toDefined
                    ? to.angle(partIndex, 1, mirrorTo) : toIdle ? part.yRot : fromY;
            float toZ = toDefined
                    ? to.angle(partIndex, 2, mirrorTo) : toIdle ? part.zRot : fromZ;
            part.xRot = rotLerp(clamped, fromX, toX);
            part.yRot = rotLerp(clamped, fromY, toY);
            part.zRot = rotLerp(clamped, fromZ, toZ);
        }
    }

    // 1.16 RotationAngle.applyRotation (MathUtil.rotLerpRad): the shorter way round.
    private static float rotLerp(float progress, float from, float to) {
        float difference = to - from;
        float fullTurn = (float) (Math.PI * 2.0D);
        while (difference < -Math.PI) {
            difference += fullTurn;
        }
        while (difference >= Math.PI) {
            difference -= fullTurn;
        }
        return from + progress * difference;
    }

    private static float normalize(
            float value, float start, float end) {
        return end > start
                ? Mth.clamp((value - start) / (end - start), 0.0F, 1.0F)
                : 1.0F;
    }

    private static void arm(
            PartDefinition torso, boolean left) {
        String side = left ? "left" : "right";
        float x = left ? 6.0F : -6.0F;
        int baseU = left ? 32 : 0;
        int armorU = left ? 48 : 16;
        PartDefinition xrot = empty(
                torso,
                side + "_arm_xrot",
                PartPose.offset(x, -10.0F, 0.0F));
        PartDefinition arm = xrot.addOrReplaceChild(
                side + "_arm",
                box(baseU, 108)
                        .addBox(
                                -2, -2, -2, 4, 6, 4)
                        .texOffs(armorU, 108)
                        .addBox(
                                -2, -2, -2, 4, 3, 4,
                                new CubeDeformation(0.1F)),
                PartPose.ZERO);
        arm.addOrReplaceChild(
                side + "_arm_bend",
                box(baseU, 118)
                        .addBox(-2, 0, -2, 4, 6, 4, new CubeDeformation(-0.001F))
                        .texOffs(armorU, 118)
                        .addBox(
                                -2, 1.4F, -2, 4, 2, 4,
                                new CubeDeformation(0.099F)),
                PartPose.offset(0, 4, 0));
        joint(arm, side + "_arm_joint", baseU, 4, left);
        arm.addOrReplaceChild(
                side + "_arm_spike",
                box(left ? 44 : 12, 108).mirror().addBox(
                        -0.5F, -0.5F, 0.75F, 2, 2, 2,
                        new CubeDeformation(-0.3F)),
                PartPose.offsetAndRotation(
                        0.0F, 2.85F, 0.0F,
                        0.0F, 0.0F, 0.7854F));
    }

    private static void leg(
            PartDefinition body, boolean left) {
        String side = left ? "left" : "right";
        float x = left ? 2.0F : -2.0F;
        int u = left ? 96 : 64;
        PartDefinition xrot = empty(
                body,
                side + "_leg_xrot",
                PartPose.offset(x, 12.0F, 0.0F));
        PartDefinition leg = xrot.addOrReplaceChild(
                side + "_leg",
                box(u, 108).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.ZERO);
        leg.addOrReplaceChild(
                side + "_leg_bend",
                box(u, 118)
                        .addBox(-2, 0, -2, 4, 6, 4, new CubeDeformation(-0.001F))
                        .texOffs(left ? 112 : 80, 123)
                        .addBox(
                                -2, 5, -2, 4, 1, 4,
                                new CubeDeformation(0.099F)),
                PartPose.offset(0, 6, 0));
        joint(leg, side + "_leg_joint", u, 6, left);
        leg.addOrReplaceChild(
                side + "_leg_spike",
                box(left ? 108 : 76, 118).addBox(
                        -1, -1, -2.5F, 2, 2, 2,
                        new CubeDeformation(-0.2F)),
                PartPose.offsetAndRotation(
                        0.0F, 5.5F, 0.0F,
                        0.0F, 0.0F, 0.7854F));
    }

    private static void joint(PartDefinition parent, String name, int u, float y, boolean mirror) {
        parent.addOrReplaceChild(name, box(u, 102).mirror(mirror)
                .addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3, new CubeDeformation(-0.125F)),
                PartPose.offset(0, y, 0));
    }

    private static Frame frame() {
        return new Frame();
    }

    private static final class Frame {
        private static final float TO_RADIANS =
                (float) Math.PI / 180.0F;
        private final float[][] rotations = new float[PART_COUNT][3];
        private final boolean[] defined = new boolean[PART_COUNT];
        private int easing = LINEAR;
        private int pitch = PITCH_NONE;

        private Frame body(float x, float y, float z) {
            return part(BODY, x, y, z);
        }

        private Frame torso(float x, float y, float z) {
            return part(TORSO, x, y, z);
        }

        private Frame head(float x, float y, float z) {
            return part(HEAD, x, y, z);
        }

        private Frame beak(float x, float y, float z) {
            return part(BEAK, x, y, z);
        }

        private Frame leftArm(float x, float y, float z) {
            return part(LEFT_ARM, x, y, z);
        }

        private Frame leftForearm(float x, float y, float z) {
            return part(LEFT_FOREARM, x, y, z);
        }

        private Frame rightArm(float x, float y, float z) {
            return part(RIGHT_ARM, x, y, z);
        }

        private Frame rightForearm(float x, float y, float z) {
            return part(RIGHT_FOREARM, x, y, z);
        }

        private Frame leftLeg(float x, float y, float z) {
            return part(LEFT_LEG, x, y, z);
        }

        private Frame leftLowerLeg(float x, float y, float z) {
            return part(LEFT_LOWER_LEG, x, y, z);
        }

        private Frame rightLeg(float x, float y, float z) {
            return part(RIGHT_LEG, x, y, z);
        }

        private Frame rightLowerLeg(float x, float y, float z) {
            return part(RIGHT_LOWER_LEG, x, y, z);
        }

        private Frame tail(
                int segment, float x, float y, float z) {
            return part(TAIL + segment, x, y, z);
        }

        private Frame ease(int easing) {
            this.easing = easing;
            return this;
        }

        private Frame pitch(int pitch) {
            this.pitch = pitch;
            return this;
        }

        private Frame part(
                int index, float x, float y, float z) {
            return radians(index, x * TO_RADIANS, y * TO_RADIANS, z * TO_RADIANS);
        }

        private Frame radians(
                int index, float x, float y, float z) {
            defined[index] = true;
            rotations[index][0] = x;
            rotations[index][1] = y;
            rotations[index][2] = z;
            return this;
        }

        private Frame copyFrom(Frame other) {
            for (int index = 0; index < PART_COUNT; index++) {
                defined[index] = other.defined[index];
                System.arraycopy(other.rotations[index], 0, rotations[index], 0, 3);
            }
            easing = other.easing;
            pitch = other.pitch;
            return this;
        }

        private float eased(float progress) {
            return switch (easing) {
                case SQUARE -> progress * progress;
                case CUBE -> progress * progress * progress;
                default -> progress;
            };
        }

        private boolean defined(
                int outputPart, boolean mirror) {
            int sourcePart = mirror
                    ? MIRRORED_PART[outputPart] : outputPart;
            return defined[sourcePart];
        }

        private float angle(
                int outputPart, int axis, boolean mirror) {
            int sourcePart = mirror
                    ? MIRRORED_PART[outputPart] : outputPart;
            float value = rotations[sourcePart][axis];
            return mirror && axis > 0 ? -value : value;
        }
    }

    private static PartDefinition empty(
            PartDefinition parent, String name, PartPose pose) {
        return parent.addOrReplaceChild(
                name, CubeListBuilder.create(), pose);
    }

    private static CubeListBuilder box(int u, int v) {
        return CubeListBuilder.create().texOffs(u, v);
    }

    private static ModelPart part(
            Model_1_21_2plus model, String name) {
        return model.jojo_ripples$getAnyDescendantWithName(name)
                .orElse(null);
    }

    private static void rotate(
            ModelPart part, float x, float y, float z) {
        if (part != null) {
            part.xRot = x;
            part.yRot = y;
            part.zRot = z;
        }
    }
}
