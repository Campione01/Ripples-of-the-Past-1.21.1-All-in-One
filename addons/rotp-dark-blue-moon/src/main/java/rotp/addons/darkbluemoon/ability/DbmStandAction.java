package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A 1.16 Dark Blue Moon StandEntityAction: the Stand stands 1.2 blocks in front of its user
 * (StandRelativeOffset.withYOffset(0, 0, 1.2)) and plays the action's 1.16 pose for its perform, which lasts the
 * action's standPerformDuration with no windup or recovery; standPerform runs on the perform's first tick, then
 * standTickPerform on each of its ticks, and the stamina cost is taken right after standPerform, as 1.16 StandEntityTask
 * did (whatever the bar holds). The cooldown is 1.16 cooldown(n): set at the press, none in Creative, gone under Resolve.
 */
public abstract class DbmStandAction extends StandEntityAbility {
    public enum TargetRequirement {
        NONE,
        ENTITY,
        BLOCK
    }

    private static final Vec3 OFFSET = new Vec3(0.0, 0.0, 1.2);

    private final ActionAnimIdentifier pose;
    private final float staminaCost;
    private final TargetRequirement targetRequirement;

    protected DbmStandAction(AbilityType<?> abilityType, AbilityId abilityId, String poseName,
            int performTicks, float staminaCost, int cooldownTicks, TargetRequirement targetRequirement) {
        super(abilityType, abilityId, DbmAction::new);
        this.pose = ActionAnimIdentifier.getOrCreate(poseName, false);
        this.staminaCost = staminaCost;
        this.targetRequirement = targetRequirement;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, performTicks);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(cooldownTicks);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return pose;
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return staminaCost;
    }

    public TargetRequirement getTargetRequirement() {
        return targetRequirement;
    }

    /** 1.16 checkConditions of the action, after the target requirement. */
    protected ConditionCheck checkDbmConditions(LivingEntity user, @Nullable StandPower power) {
        return ConditionCheck.POSITIVE;
    }

    /** 1.16 standPerform, server side only (the 1.16 bodies all began with !world.isClientSide()). */
    protected abstract void standPerform(DbmAction action, StandEntity stand, LivingEntity user);

    /** 1.16 standTickPerform, server side only; tick is the perform's tick from 0 (1.16 task.getTick()). */
    protected void standTickPerform(DbmAction action, StandEntity stand, LivingEntity user, int tick) {}

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        StandPower power = context instanceof StandPower standPower ? standPower : null;
        if (targetRequirement != TargetRequirement.NONE) {
            ConditionCheck targetCheck = checkAimedTarget(user, power);
            if (!targetCheck.isPositive()) {
                return targetCheck;
            }
        }
        return checkDbmConditions(user, power);
    }

    // 1.16 Action.checkRangeAndTarget for the TargetRequirement: no fitting target was refused silently, an entity past
    // 8 blocks from the performer (4 without a line of sight) or a block past 10 with "target_too_far", and
    // StandEntityAction.checkStandTarget took only a living entity the Stand may attack (silently)
    private ConditionCheck checkAimedTarget(LivingEntity user, @Nullable StandPower power) {
        StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
        LivingEntity performer = stand != null ? stand : user;
        ActionTarget target = getAimedTarget(performer, user);
        switch (targetRequirement) {
            case ENTITY -> {
                Entity aimed = target.getType() == ActionTarget.TargetType.ENTITY ? target.getMainEntity() : null;
                if (aimed == null) {
                    return ConditionCheck.NEGATIVE;
                }
                if (!ActionTargetRange.isEntityWithinRange(performer, aimed, ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
                    return ConditionCheck.createNegative("target_too_far");
                }
                return stand == null ? ConditionCheck.POSITIVE
                        : ConditionCheck.noMessage(aimed instanceof LivingEntity && stand.canAttackEntity(aimed));
            }
            case BLOCK -> {
                if (target.getType() != ActionTarget.TargetType.BLOCK || target.getBlockPos() == null
                        || performer.level().getBlockState(target.getBlockPos()).isAir()) {
                    return ConditionCheck.NEGATIVE;
                }
                if (!ActionTargetRange.isBlockWithinRange(performer, performer.level(), target.getBlockPos(),
                        ActionTargetRange.DEFAULT_BLOCK_RANGE_SQ)) {
                    return ConditionCheck.createNegative("target_too_far");
                }
                return ConditionCheck.POSITIVE;
            }
            default -> {
                return ConditionCheck.POSITIVE;
            }
        }
    }

    private ActionTarget getAimedTarget(LivingEntity performer, LivingEntity user) {
        // the aims go only from the user's client to the server, so that client reads the aims it sends
        ActionTargetAim aim = performer.level().isClientSide() && user == ClientProxy.getClientPlayer()
                ? performer == user ? ClientsideAim.playerAim : ClientsideAim.standAim
                : LivingComponentAction.getAim(performer);
        ActionTarget target = aim != null ? aim.getTarget() : null;
        if (target == null) {
            return ActionTarget.EMPTY;
        }
        return overridePressedTarget(user, target.resolveEntityId(performer.level()));
    }

    @Override
    protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
        return switch (targetRequirement) {
            case ENTITY -> target.getType() == ActionTarget.TargetType.ENTITY;
            case BLOCK -> target.getType() == ActionTarget.TargetType.BLOCK;
            default -> false;
        };
    }

    @Override
    public boolean pressedBlockGivesWayToEntity() {
        return targetRequirement != TargetRequirement.BLOCK;
    }

    public static class DbmAction extends EntityActionInstance {
        private boolean performed;
        /** Free per-action state for the 1.16 action's own field (1.16 kept it on the shared action object). */
        public boolean flag;

        public DbmAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            setStandOffset(OFFSET, StandOffsetFromUser.Rotations.HEAD, false);
            if (ability instanceof DbmStandAction dbm && dbm.targetRequirement != TargetRequirement.NONE
                    && getPerformer() instanceof StandEntity stand) {
                captureActionTargetFromAim(stand);
            }
        }

        // the server's copy of what the Stand aimed at as it was pressed (1.16 StandEntityTask's target)
        @Override
        protected ActionTarget pressedTaskTarget() {
            return getActionTargetSnapshot(level());
        }

        public ActionTarget getPressedTarget() {
            return getActionTargetSnapshot(level());
        }

        @Override
        public void actionTick() {
            Level level = level();
            if (level.isClientSide() || getPhase() != ActionPhase.PERFORM
                    || !(ability instanceof DbmStandAction dbm) || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = stand.getUser();
            if (user == null) {
                return;
            }
            if (!performed) {
                performed = true;
                dbm.standPerform(this, stand, user);
                StandAbilityStamina.consume((Ability) dbm, StandPower.get(user), dbm.staminaCost, false);
            }
            dbm.standTickPerform(this, stand, user, (int) getPhaseTick());
        }

        // 1.16 StandEntityAction (not chainable) refused its own press during its task (canClickDuringTask)
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
