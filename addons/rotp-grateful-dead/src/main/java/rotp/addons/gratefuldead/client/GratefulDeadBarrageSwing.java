package rotp.addons.gratefuldead.client;

import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.barrage.BarrageSwings;
import rotp.core.client.entityanim.barrage.TwoHandedBarrageLoopSwing;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.util.functions.MathUtil;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 TheGratefulDeadModel.RightHandOnlyBarrageAnimation: every afterimage is the right arm. The clip keys only that
 * arm, so the core's TWO_HANDED swing draws the other half of its afterimages on a left arm that hangs at its side.
 */
public final class GratefulDeadBarrageSwing {
    /** The skin's barrage clip names it in its timeline. */
    public static final String TYPE = "GRATEFUL_DEAD_RIGHT_HAND";
    // 1.16 TwoHandedBarrageAnimation: loopLen 4.
    private static final float LOOP_TICKS = 4.0F;

    private GratefulDeadBarrageSwing() {}

    // 1.16 ArmsBarrageAnimation.addSwings and doAddSwings with switchesArms() false.
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
            int swingsToAdd = MathUtil.fractionRandomInc(hits);
            double maxOffset = 1.0D - swings.barragePrecision / 40.0D;
            float addTime = animTimeSecs - animTimeSecs % LOOP_TICKS;
            for (int i = 0; i < swingsToAdd; i++) {
                float start = ((float) i + (entity.getRandom().nextFloat() - 0.5F) * 0.4F)
                        / swingsToAdd * LOOP_TICKS * 0.5F;
                swings.barrageSwings.add(new TwoHandedBarrageLoopSwing(
                        barrageAnim, entity, start, LOOP_TICKS, HumanoidArm.RIGHT, maxOffset, addTime));
            }
        }
        swings.loopLast = loop;
    }
}
