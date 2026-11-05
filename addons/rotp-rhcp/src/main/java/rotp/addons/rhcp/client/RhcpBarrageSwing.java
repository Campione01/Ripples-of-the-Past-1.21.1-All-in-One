package rotp.addons.rhcp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.barrage.BarrageSwings;
import rotp.core.client.util.functions.RGBUtil;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;

import net.minecraft.client.model.EntityModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * RHCP's barrage afterimages as in 1.16 (StandTwoHandedBarrageAnimation with StandArmBarrageSwing):
 * each arm is RHCP's own two-handed loop at the swing's completion, so it sits where the fists do.
 * The core's TWO_HANDED swing poses the arm with the generic humanoid clip instead.
 */
public final class RhcpBarrageSwing extends BarrageSwings.BarrageSwing {
    /** The skin's barrage clip names it in its timeline (barrage = RHCP_TWO_HANDED). */
    public static final String TYPE = "RHCP_TWO_HANDED";
    // 1.16 TwoHandedBarrageAnimation: loopLen 4.
    private static final float LOOP_TICKS = 4.0F;

    private final HumanoidArm side;
    private final Vec3 offset;
    private final float zRot;

    private RhcpBarrageSwing(RotpAnimDefinition barrageAnim, float ticks, float ticksMax,
            HumanoidArm side, double maxOffset) {
        super(barrageAnim, ticks, ticksMax);
        this.side = side;
        // 1.16 ArmBarrageSwing
        double upOffset = (RANDOM.nextDouble() - 0.5D) * maxOffset;
        double leftOffset = RANDOM.nextDouble() * maxOffset / 2.0D;
        double frontOffset = RANDOM.nextDouble() * 0.5D;
        if (side == HumanoidArm.RIGHT) {
            leftOffset *= -1.0D;
        }
        double atan = Mth.atan2(upOffset, leftOffset);
        zRot = maxOffset == 0.0D ? 0.0F : Mth.wrapDegrees(
                (float) (Math.PI / 2.0D - atan) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        offset = new Vec3(leftOffset, upOffset, frontOffset);
    }

    // 1.16 ArmsBarrageAnimation.addSwings / doAddSwings with the Stand's hit rate and precision.
    public static void addSwings(BarrageSwings swings, RotpAnimDefinition barrageAnim,
            LivingEntity entity, float entityTicks, float animTimeSecs) {
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(entity);
        if (action == null || action.getPhase() != ActionPhase.PERFORM) {
            return;
        }
        float lastLoop = swings.loopLast;
        float loop = entityTicks / LOOP_TICKS;
        if (swings.isBarragingAnim && loop > lastLoop) {
            float hits = swings.barrageSwingsPerSecond / 20.0F
                    * Math.min(loop - lastLoop, 1.0F) * LOOP_TICKS;
            int swingsToAdd = (int) hits;
            if (RANDOM.nextFloat() <= hits - swingsToAdd) {
                swingsToAdd++;
            }
            double maxOffset = 1.0D - swings.barragePrecision / 40.0D;
            HumanoidArm side = RANDOM.nextBoolean() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            for (int i = 0; i < swingsToAdd; i++) {
                float start = ((float) i / swingsToAdd
                        + (RANDOM.nextFloat() - 0.5F) * 0.4F / swingsToAdd)
                        * LOOP_TICKS * 0.5F;
                side = side.getOpposite();
                swings.barrageSwings.add(new RhcpBarrageSwing(
                        barrageAnim, start, LOOP_TICKS, side, maxOffset));
            }
        }
        swings.loopLast = loop;
    }

    @Override
    public void poseAndRender(EntityModel<?> model, PoseStack poseStack, VertexConsumer buffer,
            int packedLight, int packedOverlay, int color, float xRot) {
        if (!(model instanceof RhcpModel rhcp)) {
            return;
        }
        BarrageSwings.resetStandPoseForBarrage(model);
        if (!BarrageSwings.setOnlyOneArmVisible(model, side)) {
            return;
        }
        float loopCompletion = ticks / ticksMax;
        double zAdditional = 0.5F - Math.abs(0.5F - loopCompletion);
        Vec3 offsetRot = new Vec3(offset.x, -offset.y, offset.z + zAdditional)
                .xRot(xRot * Mth.DEG_TO_RAD);
        poseStack.pushPose();
        poseStack.translate(offsetRot.x, offsetRot.y, -offsetRot.z);
        rhcp.poseBarrageAfterimage(loopCompletion, side, xRot, zRot);
        ((Model_1_21_2plus) model).jojo_ripples$root().render(poseStack, buffer,
                packedLight, packedOverlay, RGBUtil.scaleAlpha(color, 0.75F));
        poseStack.popPose();
    }
}
