package rotp.addons.novemberrain.ability;

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
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * A StandEntityAction of the 1.0.4 remake (RotP-NovemberRain-Addon-1.0.4, com.ptitboo83.nr.action), as the 1.16
 * StandEntityTask ran it: a holdType action performs from its press for as long as its key is held and then recovers;
 * a holdToFire(n, false) one charges n held ticks, performs once and is dropped if let go sooner; any other performs for
 * its standPerformDuration (1 by default). staminaCost is taken after the perform's first tick of standPerform,
 * staminaCostTick after every standTickPerform. Its cooldown is 1.16 cooldown(n): none in Creative, gone under Resolve.
 */
public abstract class NovemberRainStandAbility extends StandEntityAbility {
    public enum TargetRequirement {
        NONE,
        ENTITY,
        ANY
    }

    /** The remake's Rain.RAIN_ANIM pose, which light_rain, heavy_rain, rain_shield and rain_barrier hold. */
    public static final ActionAnimIdentifier RAIN_ANIM = ActionAnimIdentifier.getOrCreate("light_rain", false);
    /** StandPose.IDLE, the 1.16 builder's default pose. */
    public static final ActionAnimIdentifier IDLE_ANIM = ActionAnimIdentifier.getOrCreate("idle", true);

    private final ActionAnimIdentifier pose;
    private final boolean holdType;
    private final float staminaCost;
    private final float staminaCostTick;
    private final TargetRequirement targetRequirement;

    protected NovemberRainStandAbility(AbilityType<?> abilityType, AbilityId abilityId, Spec spec) {
        super(abilityType, abilityId, NovemberRainAction::new);
        this.pose = spec.pose;
        this.holdType = spec.holdType;
        this.staminaCost = spec.staminaCost;
        this.staminaCostTick = spec.staminaCostTick;
        this.targetRequirement = spec.targetRequirement;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        if (spec.holdType) {
            setButtonHoldPhase(ActionPhase.PERFORM);
        }
        else {
            setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, spec.holdToFireTicks);
            setDefaultPhaseLength(ActionPhase.PERFORM, spec.performTicks);
        }
        setDefaultPhaseLength(ActionPhase.RECOVERY, spec.recoveryTicks);
        if (spec.cooldownTicks > 0) {
            cooldown(spec.cooldownTicks);
        }
        if (spec.resolveLevel > 0) {
            resolveLevelToUnlock(spec.resolveLevel);
        }
    }

    public boolean isHoldType() {
        return holdType;
    }

    public float getStaminaCostTick() {
        return staminaCostTick;
    }

    public TargetRequirement getTargetRequirement() {
        return targetRequirement;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return pose;
    }

    // 1.16 StandAction.getStaminaCost: the one-off cost, none for a cost that only ticks
    @Override
    public float getStaminaCost(Power<?> context) {
        return staminaCost;
    }

    /** 1.16 standPerform and preTaskInit, on the server, on the perform's first tick. */
    protected void standPerform(NovemberRainAction action, Level level, StandEntity stand, LivingEntity user) {}

    /** 1.16 standTickPerform, on both sides, on every tick of the perform. */
    protected void standTickPerform(NovemberRainAction action, Level level, StandEntity stand, LivingEntity user) {}

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive() || targetRequirement == TargetRequirement.NONE) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        return checkAimedTarget(user, context instanceof StandPower standPower ? standPower : null);
    }

    // 1.16 PowerBaseImpl.tickHeldAction checked the target again on every held tick of a charge
    @Override
    protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
        return action.getPhase() == ActionPhase.BUTTON_CHARGE ? checkSpecificConditions(context) : ConditionCheck.POSITIVE;
    }

    /*
     * 1.16 PowerBaseImpl.checkTarget and Action.checkRangeAndTarget: a target of a kind the requirement does not take
     * (ENTITY: an entity, ANY: an entity or a block) is refused silently, an entity past 8 blocks from the performer (4
     * without a line of sight) or a block past 10 with "target_too_far", air silently; StandEntityAction.checkStandTarget
     * took only a living entity the Stand may attack.
     */
    private ConditionCheck checkAimedTarget(LivingEntity user, @Nullable StandPower power) {
        StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
        LivingEntity performer = stand != null ? stand : user;
        ActionTarget target = getAimedTarget(performer, user);
        if (target.getType() == ActionTarget.TargetType.ENTITY) {
            Entity aimed = target.getMainEntity();
            if (aimed == null) {
                return ConditionCheck.NEGATIVE;
            }
            if (!ActionTargetRange.isEntityWithinRange(performer, aimed, ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
                return ConditionCheck.createNegative("target_too_far");
            }
            return stand == null ? ConditionCheck.POSITIVE
                    : ConditionCheck.noMessage(aimed instanceof LivingEntity && stand.canAttackEntity(aimed));
        }
        if (targetRequirement == TargetRequirement.ANY && target.getType() == ActionTarget.TargetType.BLOCK
                && target.getBlockPos() != null) {
            if (performer.level().getBlockState(target.getBlockPos()).isAir()) {
                return ConditionCheck.NEGATIVE;
            }
            return ActionTargetRange.isBlockWithinRange(performer, performer.level(), target.getBlockPos(),
                    ActionTargetRange.DEFAULT_BLOCK_RANGE_SQ)
                    ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("target_too_far");
        }
        return ConditionCheck.NEGATIVE;
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

    // 1.16 StandEntityTask.keepTarget kept a target of the kind the requirement takes, and the Stand turned to it
    @Override
    protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
        return switch (targetRequirement) {
            case ENTITY -> target.getType() == ActionTarget.TargetType.ENTITY;
            case ANY -> target.getType() == ActionTarget.TargetType.ENTITY
                    || target.getType() == ActionTarget.TargetType.BLOCK;
            default -> false;
        };
    }

    public static final class Spec {
        private ActionAnimIdentifier pose = IDLE_ANIM;
        private boolean holdType;
        private int holdToFireTicks;
        private int performTicks = 1;
        private int recoveryTicks;
        private float staminaCost;
        private float staminaCostTick;
        private int cooldownTicks;
        private int resolveLevel;
        private TargetRequirement targetRequirement = TargetRequirement.NONE;

        public Spec pose(ActionAnimIdentifier pose) {
            this.pose = pose;
            return this;
        }

        public Spec holdType() {
            this.holdType = true;
            return this;
        }

        public Spec holdToFire(int ticks) {
            this.holdToFireTicks = ticks;
            return this;
        }

        public Spec standPerformDuration(int ticks) {
            this.performTicks = Math.max(ticks, 1);
            return this;
        }

        public Spec standRecoveryTicks(int ticks) {
            this.recoveryTicks = ticks;
            return this;
        }

        public Spec staminaCost(float cost) {
            this.staminaCost = cost;
            return this;
        }

        public Spec staminaCostTick(float cost) {
            this.staminaCostTick = cost;
            return this;
        }

        public Spec cooldown(int ticks) {
            this.cooldownTicks = ticks;
            return this;
        }

        public Spec resolveLevelToUnlock(int level) {
            this.resolveLevel = level;
            return this;
        }

        public Spec targetRequirement(TargetRequirement requirement) {
            this.targetRequirement = requirement;
            return this;
        }
    }

    public static final class NovemberRainAction extends EntityActionInstance {
        private boolean performed;

        public NovemberRainAction(EntityActionType ability) {
            super(ability);
        }

        private NovemberRainStandAbility rainAbility() {
            return (NovemberRainStandAbility) ability;
        }

        @Override
        public void onActionSet(@Nullable EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            if (rainAbility().targetRequirement != TargetRequirement.NONE && getPerformer() instanceof StandEntity stand) {
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
            if (getPhase() != ActionPhase.PERFORM || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = stand.getUser();
            if (user == null) {
                return;
            }
            Level level = level();
            NovemberRainStandAbility rain = rainAbility();
            StandPower power = level.isClientSide() ? null : StandPower.get(user);
            if (!performed) {
                performed = true;
                if (!level.isClientSide()) {
                    rain.standPerform(this, level, stand, user);
                    if (rain.staminaCost > 0) {
                        StandAbilityStamina.consume((Ability) rain, power, rain.staminaCost, false);
                    }
                }
            }
            rain.standTickPerform(this, level, stand, user);
            if (!level.isClientSide() && rain.staminaCostTick > 0) {
                StandAbilityStamina.consume((Ability) rain, power, rain.staminaCostTick, true);
            }
        }

        // a holdToFire(n, false) charge let go early fires nothing; a holdType perform goes to its recovery
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
            else if (getPhase() == ActionPhase.PERFORM && rainAbility().holdType) {
                startRecovery();
            }
        }

        // 1.16 holdType StandEntityActions let every other action of the power through their hold; a pressed Stand
        // action waits through their recovery (isCancelable), as the default canBeCancelledInto does
        @Override
        public boolean isHoldTypeStandAction() {
            return rainAbility().holdType;
        }

        // 1.16 StandEntityAction (not chainable) refused its own press during its task (canClickDuringTask)
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
