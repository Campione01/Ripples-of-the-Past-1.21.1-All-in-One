package rotp.addons.ger.ability;

import rotp.core.JojoModConfig;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandMeleeReach;

import rotp.addons.ger.GoldExperienceRequiemGameEvents;
import rotp.addons.ger.damage.GerDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class GerUndiePunchAbility
        extends StandEntityHeavyPunchAbility {
    public static final float LEGACY_STAMINA_COST = 100.0F;
    public static final int LEGACY_COOLDOWN_TICKS = 80;
    public static final int LEGACY_PERFORM_TICKS = 10;

    public GerUndiePunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = GerUndiePunch::new;
        // Its windup and recovery are the heavy punch's, by the Stand's speed.
        setDefaultPhaseLength(
                ActionPhase.PERFORM, LEGACY_PERFORM_TICKS);
        cooldown(LEGACY_COOLDOWN_TICKS);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return GerActionAnimations.resolve(
                getAbilityId(), super.getEntityAnim(action));
    }

    /**
     * What the punch takes from the target: its damage at this reach under
     * the server's Stand damage multiplier, with nothing taken off, as it
     * lands as true damage.
     */
    public static float trueDamage(
            StandEntity stand, Entity target, float damageAmount) {
        return damageAmount
                * StandMeleeReach.strikeFactor(stand, target)
                * JojoModConfig.getCommonConfigInstance(false)
                        .standDamageMultiplier.get().floatValue();
    }

    public static final class GerUndiePunch
            extends StandEntityHeavyPunch {
        public GerUndiePunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            super.actionPerformStart();
            if (!level().isClientSide()) {
                StandPower power = StandPower.get(getPowerUser());
                if (power != null
                        && ability instanceof GerUndiePunchAbility undie) {
                    // The shared heavy action took its own cost already.
                    power.consumeStamina(LEGACY_STAMINA_COST
                            - undie.getStaminaCost(power));
                }
            }
        }

        /**
         * The owner, 2026-11-08: the loop starts only when this hit would
         * kill, and from this hit; a hit that would not lands once as true
         * damage and marks nothing.
         */
        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damageAmount,
                float explosionRadius) {
            if (level.isClientSide()
                    || !(target.getMainEntity()
                            instanceof LivingEntity struck)
                    || !(stand.getUser() instanceof LivingEntity owner)) {
                super.hitEntity(
                        target,
                        level,
                        stand,
                        damageSource,
                        damageAmount,
                        explosionRadius);
                return;
            }
            DamageSource trueHit = GerDamageTypes.retyped(
                    level, damageSource, GerDamageTypes.REQUIEM_TRUE);
            // A Stand passes what it takes on to its user, whose life it is.
            LivingEntity victim = struck instanceof StandEntity struckStand
                            && struckStand.getUser() != null
                    ? struckStand.getUser()
                    : struck;
            boolean kills = stand.canAttackEntity(struck)
                    && JojoModUtil.canHarm(owner, victim)
                    && GoldExperienceRequiemGameEvents
                            .canEnterUndieLoop(victim)
                    && trueDamage(stand, struck, damageAmount)
                            >= victim.getHealth()
                                    + victim.getAbsorptionAmount();
            if (kills) {
                GoldExperienceRequiemGameEvents.armUndieLoop(
                        victim, owner);
            }
            super.hitEntity(
                    target,
                    level,
                    stand,
                    trueHit,
                    damageAmount,
                    explosionRadius);
            if (kills) {
                GoldExperienceRequiemGameEvents.settleUndieKill(
                        victim,
                        trueHit instanceof DamageSourceModified modified
                                && modified.jojo_ripples$isNonLethal());
            }
        }
    }
}
