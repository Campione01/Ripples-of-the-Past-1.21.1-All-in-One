package rotp.addons.highwaystar.ability;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
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

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarEntity;
import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** 1.16 HighwayStarDrainAction: a held action that drains the creature the Stand reaches, or sends the soles to it. */
public final class HighwayStarDrainAbility extends StandEntityAbility {
    public static final ActionAnimIdentifier DRAIN_ANIM = ActionAnimIdentifier.getOrCreate("Draw", false);
    public static final float STAMINA_COST_TICK = 1.0F;

    public HighwayStarDrainAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DrainAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(40, 0);
        standUserWalkSpeed(0.3F);
        standAutoSummonMode(AutoSummonMode.MAIN_ARM);
        partsRequired(StandPart.ARMS);
    }

    /** The target a press of the user takes: the Stand's own aim under manual control, else the user's crosshair. */
    public static ActionTarget pressTarget(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        return LivingComponentAction.getAimTarget(stand != null && stand.isManuallyControlled() ? stand : user);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        ActionTarget target = pressTarget(user);
        if (target.getType() != ActionTarget.TargetType.ENTITY) {
            return ConditionCheck.createNegative(Component.translatable("action.highwaystar.condition.drain_no_target"));
        }
        Entity entity = target.getEntity();
        return entity instanceof LivingEntity && entity != user && !(entity instanceof HighwayStarSoleEntity)
                ? super.checkSpecificConditions(context)
                : ConditionCheck.NEGATIVE;
    }

    @Override
    protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.ENTITY;
    }

    // 1.16 friendlyFire: allies are drained too
    @Override
    protected boolean keepsTaskTargetEntity(StandEntity standEntity, Entity entity) {
        return entity instanceof LivingEntity && entity != standEntity.getUser()
                && !(entity instanceof HighwayStarSoleEntity);
    }

    @Override
    public boolean takesPressedTarget(StandEntity standEntity, ActionTarget target) {
        return target.getType() != ActionTarget.TargetType.ENTITY || keepsTaskTargetEntity(standEntity, target.getEntity());
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return DRAIN_ANIM;
    }

    public static final class DrainAction extends EntityActionInstance {
        private int performTicks;

        public DrainAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(@Nullable EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            LivingEntity user = getPowerUser();
            if (!level().isClientSide() && user != null && performer instanceof StandEntity stand) {
                ActionTarget target = setActionTargetSnapshot(pressTarget(user));
                keepStandAimedAtTarget(target);
                if (target.getType() == ActionTarget.TargetType.ENTITY) {
                    setStandFrontOffsetFromTarget(stand, target, 0.0, stand.getEffectiveRange());
                }
            }
        }

        // 1.16 getOffsetFromUser: offsetToTarget(0, max effective range) of the task target, else the default offset
        @Override
        public void updateStandOffset(StandEntity stand) {
            ActionTarget target = getTaskTarget(stand.level());
            if (target.getType() == ActionTarget.TargetType.ENTITY) {
                StandEntityBarrageAbility.refreshFrontOffset(this, stand, target, 0.0, stand.getEffectiveRange());
            }
        }

        @Nullable
        private LivingEntity livingTarget() {
            return getTaskTarget(level()).getType() == ActionTarget.TargetType.ENTITY
                    && getTaskTarget(level()).getEntity() instanceof LivingEntity living ? living : null;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide() || !(performer instanceof HighwayStarEntity hs)) {
                return;
            }
            StandUtil.playStandEntitySound(hs, HighwayStarAddon.DRAIN_VOICE, 1.0F, 1.0F);
            LivingEntity user = getPowerUser();
            LivingEntity target = livingTarget();
            if (user == null || target == null || target == user || !target.isAlive()
                    || target instanceof HighwayStarSoleEntity) {
                return;
            }
            if (hs.isSplit()) {
                int i = 0;
                for (HighwayStarSoleEntity sole : HighwayStarEntity.ownedSoles(level(), user, 128.0)) {
                    sole.attachTo(target.getUUID(), i++);
                }
                hs.setTrackingTarget(target.getUUID());
            }
        }

        @Override
        public void actionTick() {
            Level level = level();
            if (level.isClientSide() || getPhase() != ActionPhase.PERFORM
                    || !(performer instanceof HighwayStarEntity hs)) {
                return;
            }
            LivingEntity target = livingTarget();
            // 1.16 standCanTickPerform: no tick and no stamina without a living target
            if (target == null) {
                return;
            }
            int tick = performTicks++;
            LivingEntity user = getPowerUser();
            StandAbilityStamina.consume(ability, user != null ? StandPower.get(user) : null, STAMINA_COST_TICK, true);
            if (hs.isSplit() || user == null || target == user || !target.isAlive()
                    || target instanceof HighwayStarSoleEntity || tick % 10 != 0) {
                return;
            }
            if (target.isUsingItem() && target.getUseItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false));
                return;
            }
            float drainAmount = 2.0F;
            boolean isUndead = target.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD);
            if (target instanceof Player player && player.getFoodData().getFoodLevel() > 0) {
                player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 1));
                if (!isUndead) {
                    feedUser(user, 1.0F);
                }
            }
            else {
                target.hurt(level.damageSources().magic(), drainAmount);
                target.invulnerableTime = 0;
                if (!isUndead) {
                    feedUser(user, drainAmount);
                }
            }
            if (isUndead) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false));
            }
            if (target.isDeadOrDying()) {
                startRecovery();
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        @Override
        public void onActionCleared(@Nullable EntityActionInstance newAction) {
            super.onActionCleared(newAction);
            LivingEntity user = getPowerUser();
            if (level().isClientSide() || user == null || !(performer instanceof HighwayStarEntity hs)) {
                return;
            }
            hs.setTrackingTarget(null);
            for (HighwayStarSoleEntity sole : HighwayStarEntity.ownedSoles(level(), user, 128.0)) {
                sole.detach();
            }
        }

        private static void feedUser(LivingEntity user, float amount) {
            if (user instanceof Player player) {
                if (player.getFoodData().getFoodLevel() < 20) {
                    player.getFoodData().eat((int) amount, 0.5F);
                    return;
                }
                if (player.getHealth() < player.getMaxHealth()) {
                    player.heal(amount);
                    return;
                }
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
            }
            else if (user.getHealth() < user.getMaxHealth()) {
                user.heal(amount);
            }
            else {
                user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
            }
        }
    }
}
