package rotp.addons.emperor.ability;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.emperor.EmperorAddon;
import rotp.addons.emperor.entity.EmperorBulletEntity;
import rotp.addons.emperor.entity.EmperorStandEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Emperor 1.3 ShootEntity / BarrageShootEntityAction: the shots of a Stand-bearing mob, which holds the gun in its
 * main hand (EmperorStandEntity gives it). A player fires the gun item instead (1.3 isLegalInHud false for players).
 */
public final class EmperorMobShotAbility extends EntityActionAbility {
    public static final int SHOT_COOLDOWN = 10;
    public static final int BARRAGE_COOLDOWN = 65;
    public static final float SHOT_STAMINA = 35.0F;
    public static final int BARRAGE_HOLD_TICKS = 11;

    private final boolean barrage;

    public EmperorMobShotAbility(
            AbilityType<?> abilityType, AbilityId abilityId, boolean barrage) {
        super(abilityType, abilityId, ShotAction::new);
        this.barrage = barrage;
        resolveLevelToUnlock(0);
        cooldown(barrage ? BARRAGE_COOLDOWN : SHOT_COOLDOWN);
        if (barrage) {
            setButtonHoldPhase(ActionPhase.PERFORM);
            setDefaultPhaseLength(ActionPhase.PERFORM, BARRAGE_HOLD_TICKS);
        }
    }

    public static EmperorMobShotAbility single(
            AbilityType<?> abilityType, AbilityId abilityId) {
        return new EmperorMobShotAbility(abilityType, abilityId, false);
    }

    public static EmperorMobShotAbility burst(
            AbilityType<?> abilityType, AbilityId abilityId) {
        return new EmperorMobShotAbility(abilityType, abilityId, true);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null || user instanceof Player) {
            return ConditionCheck.NEGATIVE;
        }
        return holdsGun(user)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("no_emperor");
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        if (barrage) {
            return 0.0F;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        return user != null && user.hasEffect(ModStatusEffects.RESOLVE)
                ? SHOT_STAMINA * 0.5F
                : SHOT_STAMINA;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    public static boolean holdsGun(LivingEntity user) {
        return user.getItemInHand(InteractionHand.MAIN_HAND)
                        .is(EmperorAddon.EMPEROR_ITEM.get())
                || user.getItemInHand(InteractionHand.OFF_HAND)
                        .is(EmperorAddon.EMPEROR_ITEM.get());
    }

    private void fire(LivingEntity user, StandPower power, boolean burstShot) {
        if (!(power.getSummonedStandEntity()
                instanceof EmperorStandEntity controller)
                || !holdsGun(user)) {
            return;
        }
        boolean resolve = user.hasEffect(ModStatusEffects.RESOLVE);
        float speed;
        float damage;
        if (burstShot) {
            speed = (float) (EmperorAddon.SPEED / 6.0D);
            damage = (float) (resolve
                    ? EmperorAddon.POWER
                    : EmperorAddon.POWER / 2.0D);
        }
        else {
            speed = (float) (controller.getAttackSpeed() / 6.0D);
            damage = (float) controller.getAttackDamage();
        }
        EmperorBulletEntity bullet =
                new EmperorBulletEntity(user, user.level());
        bullet.setBaseDamage(damage);
        bullet.setTargetEntity(controller.getTargetEntity());
        bullet.shootFromRotation(user, speed, 0.0F);
        user.level().addFreshEntity(bullet);
        user.level().playSound(
                null,
                user.blockPosition(),
                EmperorAddon.EMP_SHOT.get(),
                user.getSoundSource(),
                1.0F,
                1.0F);
    }

    public static final class ShotAction extends EntityActionInstance {
        public ShotAction(EntityActionType ability) {
            super(ability);
        }

        private EmperorMobShotAbility shot() {
            return ability instanceof EmperorMobShotAbility shot
                    ? shot
                    : null;
        }

        @Override
        public void actionPerformStart() {
            EmperorMobShotAbility shot = shot();
            LivingEntity user = getPowerUser();
            if (shot == null || shot.barrage || user == null
                    || level().isClientSide()) {
                return;
            }
            StandPower power = StandPower.get(user);
            if (power == null) {
                return;
            }
            StandAbilityStamina.consume(
                    (Ability) shot, power, shot.getStaminaCost(power), false);
            shot.fire(user, power, false);
            power.setAbilityCooldown(
                    EmperorAddon.MOB_BARRAGE_NAME, SHOT_COOLDOWN);
        }

        @Override
        public void actionTick() {
            EmperorMobShotAbility shot = shot();
            LivingEntity user = getPowerUser();
            if (shot == null || !shot.barrage || user == null
                    || level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM) {
                return;
            }
            StandPower power = StandPower.get(user);
            if (power == null) {
                return;
            }
            // 1.16 holdTick fired on held ticks 1, 3, 5, 7, 9 and 11
            int ticksHeld = (int) getPhaseTick() + 1;
            if (ticksHeld % 2 == 1 && ticksHeld <= BARRAGE_HOLD_TICKS) {
                shot.fire(user, power, true);
            }
            if (ticksHeld >= BARRAGE_HOLD_TICKS) {
                startRecovery();
            }
        }

        @Override
        public void onButtonStopHold() {
            EmperorMobShotAbility shot = shot();
            if (shot != null && shot.barrage
                    && getPhase() == ActionPhase.PERFORM) {
                startRecovery();
            }
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            EmperorMobShotAbility shot = shot();
            LivingEntity user = getPowerUser();
            if (shot != null && shot.barrage && user != null
                    && !level().isClientSide()) {
                StandPower power = StandPower.get(user);
                if (power != null) {
                    power.setAbilityCooldown(
                            EmperorAddon.MOB_SHOT_NAME,
                            BARRAGE_COOLDOWN);
                }
            }
        }
    }
}
