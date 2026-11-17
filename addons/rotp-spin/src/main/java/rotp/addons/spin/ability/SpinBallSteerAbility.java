package rotp.addons.spin.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.SteelBallEntity;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

public final class SpinBallSteerAbility extends SpinActionAbility {
    public SpinBallSteerAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, 3, SteerAction::new);
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        return check.isPositive() ? ballCondition(context.getUser()) : check;
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
        return ballCondition(context.getUser());
    }

    private static ConditionCheck ballCondition(LivingEntity user) {
        if (user == null || user.isSpectator()) {
            return ConditionCheck.NEGATIVE;
        }
        double range = SpinConfig.STEER_SEARCH_RANGE.get();
        return SteelBallEntity.findSteerable(user, range) != null || SteelBallEntity.findRecallable(user, range) != null
                ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("rotp_spin.no_ball_in_flight");
    }

    public static final class SteerAction extends EntityActionInstance {
        public SteerAction(EntityActionType type) {
            super(type);
        }

        @Override
        public void actionTick() {
            if (!(level() instanceof ServerLevel) || getPhase() != ActionPhase.PERFORM) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null || !spend(user, SpinConfig.STEER_ENERGY_PER_TICK.get())) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            double range = SpinConfig.STEER_SEARCH_RANGE.get();
            SteelBallEntity flight = SteelBallEntity.findSteerable(user, range);
            if (flight != null) {
                Vec3 aim = user.getEyePosition().add(user.getLookAngle().scale(SpinConfig.STEER_AIM_DISTANCE.get()));
                flight.steerTowards(aim);
            }
            else {
                SteelBallEntity dropped = SteelBallEntity.findRecallable(user, range);
                if (dropped != null && spend(user, SpinConfig.BALL_SPIN_COST.get())) {
                    dropped.respinAndReturn();
                }
            }
        }

        @Override
        public void onButtonStopHold() {
            forceStop();
            syncPhaseChanges();
        }
    }
}
