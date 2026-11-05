package rotp.addons.whitesnake.ability;

import java.util.Iterator;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.util.functions.ItemUtil;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.buffdisc.DiscItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * PATCH1 InsertDisc (holdType, holdToFire(15, false), staminaCost 30, resolveLevelToUnlock 2, REMOVE_DISC pose,
 * PERFORM sound whitesnake_remove_disc, ENTITY target, arms): a released full hold on a living target pulls one of
 * its effects out as a Buff disc for the user; 20-tick cooldown after a disc came out. PATCH1 names it
 * Insert/Extract but only extracts.
 */
public final class WhitesnakeInsertDiscAbility extends StandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 15;
    public static final int COOLDOWN_TICKS = 20;
    public static final float STAMINA_COST = 30.0F;
    public static final double TARGET_RANGE = 20.0D;
    // PATCH1 standPose(REMOVE_DISC), the pose of the Stand disc removal
    private static final ActionAnimIdentifier REMOVE_DISC_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "whitesnake_remove_stand_disc", false);

    public WhitesnakeInsertDiscAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, InsertDiscAction::new);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(
                ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
        // PATCH1 cancelHeldOnGettingAttacked: a hit during the hold stops it
        setCancelHeldOnGettingAttacked();
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return REMOVE_DISC_ANIM;
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return HOLD_TO_FIRE_TICKS;
    }

    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    @Override
    public boolean isHoldAction() {
        return true;
    }

    @Override
    public void stopHeldActionOnGettingAttacked(
            EntityActionInstance action) {
        action.forceStop();
        action.syncPhaseChanges();
    }

    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return true;
    }

    // PATCH1 standKeepsTarget: a living entity target
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == TargetType.ENTITY;
    }

    @Override
    public boolean canTargetEntityForAiming(
            StandEntity standEntity, Entity target) {
        return canUseAsTarget(target, standEntity.getUser());
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        return checkTarget(context.getUser());
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return checkTarget(context.getUser());
    }

    private ConditionCheck checkTarget(LivingEntity user) {
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        ActionTarget aimed = findCurrentTarget(user, user.level());
        if (asTarget(aimed, user) == null) {
            return ConditionCheck.createNegative("whitesnake_disc_target");
        }
        // 1.16 ENTITY requirement: checkRangeAndTarget answered a target
        // past the Stand's reach with its message
        if (!isInStandRange(user, aimed)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (!WhitesnakeRemoveMemoryDiscAbility.standMayTarget(
                user, aimed.getEntity())) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (action instanceof InsertDiscAction insertAction) {
            ActionTarget target = findCurrentTarget(powerUser, level);
            if (!isInStandRange(powerUser, target)) {
                target = ActionTarget.EMPTY;
            }
            insertAction.setTarget(target);
            if (!target.isEmpty(level)) {
                action.standRotationTarget = target.copy();
                action.aimAs = AimingEntity.STAND;
            }
        }
    }

    private ActionTarget findCurrentTarget(
            LivingEntity user, Level level) {
        if (user == null) {
            return ActionTarget.EMPTY;
        }
        ActionTargetAim aim = level.isClientSide()
                        && user == ClientProxy.getClientPlayer()
                ? ClientsideAim.playerAim
                : LivingComponentAction.getAim(user);
        ActionTarget target =
                aim != null ? aim.getTarget() : ActionTarget.EMPTY;
        target = target != null
                ? target.resolveEntityId(level)
                : ActionTarget.EMPTY;
        boolean pressedBlock = target.getType() == TargetType.BLOCK;
        target = overridePressedTarget(user, target);
        if (isValidTarget(user, target, level)) {
            return target;
        }
        if (pressedBlock) {
            return ActionTarget.EMPTY;
        }
        target = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                TARGET_RANGE,
                TARGET_RANGE,
                level,
                entity -> canUseAsTarget(entity, user),
                user,
                0);
        return isValidTarget(user, target, level)
                ? target
                : ActionTarget.EMPTY;
    }

    // PATCH1 took the effects of the living entity hit itself, not of its Stand user
    private static boolean canUseAsTarget(
            Entity entity, LivingEntity user) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && living != user
                && StandUtil.getStandUser(living) != user;
    }

    private static LivingEntity asTarget(
            ActionTarget target, LivingEntity user) {
        return target.getEntity() instanceof LivingEntity living
                        && canUseAsTarget(living, user)
                ? living
                : null;
    }

    private static boolean isValidTarget(
            LivingEntity user, ActionTarget target, Level level) {
        if (target.getType() != TargetType.ENTITY
                || target.isEmpty(level)
                || asTarget(target, user) == null) {
            return false;
        }
        return target.getEntity().getBoundingBox().distanceToSqr(
                user.getEyePosition()) <= TARGET_RANGE * TARGET_RANGE;
    }

    private static boolean isInStandRange(
            LivingEntity user, ActionTarget target) {
        return user != null
                && target.getType() == TargetType.ENTITY
                && target.getEntity() != null
                && ActionTargetRange.isEntityWithinRange(
                        ActionTargetRange.standPerformer(user),
                        target.getEntity(),
                        ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ);
    }

    public static final class InsertDiscAction
            extends EntityActionInstance {
        private ActionTarget target = ActionTarget.EMPTY;

        public InsertDiscAction(EntityActionType ability) {
            super(ability);
        }

        private void setTarget(ActionTarget target) {
            this.target = target != null
                    ? target.copy()
                    : ActionTarget.EMPTY;
        }

        private ActionTarget getTarget(Level level) {
            target = target.resolveEntityId(level);
            return target;
        }

        // PATCH1 onTaskSet(BUTTON_HOLD): the Stand, which keeps no offset here, stands one block before the target
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (getPerformer() instanceof StandEntity stand) {
                WhitesnakeRemoveMemoryDiscAbility.standBeforeTarget(
                        stand, getPowerUser(), getTarget(level()));
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() == ActionPhase.WINDUP) {
                keepStandAimedAtTarget(getTarget(level()));
            }
        }

        // holdType() + holdToFire(15, false): a full charge fires when the key is let go; letting go earlier cancels
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.WINDUP;
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                if (getPhaseTick() >= getCurPhaseLength()
                        && ability instanceof WhitesnakeInsertDiscAbility held
                        && held.canFireReleasedHold(this)) {
                    setPhaseStart(ActionPhase.PERFORM);
                }
                else {
                    forceStop();
                }
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            // standSound(PERFORM, WHITESNAKE_REMOVE_DISC): at every perform
            if (getPerformer() instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        WhitesnakeAddon.WHITESNAKE_REMOVE_DISC.get(),
                        1.0F,
                        1.0F);
            }
            LivingEntity user = getPowerUser();
            StandPower userPower =
                    user != null ? StandPower.get(user) : null;
            ActionTarget currentTarget = getTarget(level());
            LivingEntity victim = user != null
                    ? asTarget(currentTarget, user)
                    : null;
            if (user == null
                    || userPower == null
                    || victim == null
                    || !isValidTarget(user, currentTarget, level())
                    || !isInStandRange(user, currentTarget)) {
                fail(user, "whitesnake_disc_target");
            }
            else {
                extractEffect(user, userPower, victim);
            }
            // 1.16 StandEntityTask paid the cost after every perform
            if (userPower != null) {
                StandAbilityStamina.consume(
                        ability, userPower, STAMINA_COST, false);
            }
        }

        private void extractEffect(
                LivingEntity user,
                StandPower userPower,
                LivingEntity victim) {
            Iterator<MobEffectInstance> effects =
                    victim.getActiveEffects().iterator();
            if (!effects.hasNext()) {
                return;
            }
            MobEffectInstance effect = effects.next();
            int amplifier = effect.getAmplifier();
            int duration = effect.getDuration();
            victim.removeEffect(effect.getEffect());
            ItemUtil.giveItemTo(
                    user,
                    DiscItem.createEffect(
                            effect.getEffect(), amplifier, duration),
                    true);
            level().playSound(
                    null,
                    victim.getX(),
                    victim.getY(),
                    victim.getZ(),
                    WhitesnakeAddon.WHITESNAKE_REMOVE_DISC.get(),
                    victim.getSoundSource(),
                    1.0F,
                    1.0F);
            if (ability instanceof Ability insert) {
                userPower.setAbilityCooldown(
                        insert.name(), COOLDOWN_TICKS);
            }
        }

        private void fail(LivingEntity user, String reason) {
            if (user == null) {
                return;
            }
            ConditionCheck.sendActionFailedMessage(
                    ability instanceof Ability abilityObject
                            ? abilityObject
                            : null,
                    ConditionCheck.createNegative(reason),
                    user);
        }
    }
}
