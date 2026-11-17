package rotp.addons.cmoon.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.addons.cmoon.field.GravityField;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The player's build registered this with Gravity Shift's numbers and sound (InitStands CMOON_GRAVITY_FIELD). Its
 * CMoonGravityField asked for 200 stamina only to start: a running field can always be turned off.
 */
public final class GravityFieldAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 200.0F;
    public static final int HOLD_TO_FIRE_TICKS = 25;
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate(
                    "gravity_change", false);

    public GravityFieldAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, FieldAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        standAutoSummonMode(AutoSummonMode.FULL);
        resolveLevelToUnlock(2);
        // cooldown(10, 5): Resolve drops the 5.
        cooldown(10, 5);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        // A Stand-bearing mob does not turn gravity 100 blocks around it: the field is a player's to start.
        if (!(context.getUser() instanceof Player)) {
            return ConditionCheck.NEGATIVE;
        }
        return checkStamina(context);
    }

    private static ConditionCheck checkStamina(Power<?> context) {
        return ConditionCheck.noMessage(
                context instanceof StandPower power
                        && (GravityField.isRunning(power.getUser())
                                || power.getStamina() >= STAMINA_COST));
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return action.getPhase() == ActionPhase.BUTTON_CHARGE
                ? checkStamina(context)
                : ConditionCheck.POSITIVE;
    }

    // The build renamed the slot to its ".param" name while the field is on.
    @Override
    public Component getName(Power<?> context) {
        if (context instanceof StandPower power
                && GravityField.isRunning(power.getUser())) {
            return abilityName(context, ".param");
        }
        return super.getName(context);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }

    public static final class FieldAction
            extends EntityActionInstance {
        public FieldAction(EntityActionType ability) {
            super(ability);
        }

        // holdToFire(25, false): letting go before the charge completes
        // cancels it.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            LivingEntity user = getPowerUser();
            if (newPhase == ActionPhase.PERFORM
                    && !level().isClientSide()
                    && getPerformer() instanceof CMoonStandEntity
                    && user != null
                    && ability instanceof GravityFieldAbility field) {
                field.setCooldownOnUse(StandPower.get(user));
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof CMoonStandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }
            GravityField.toggle(user);
            StandUtil.playStandEntitySound(
                    stand,
                    CMoonAddon.C_MOON_GRAVITY_SHIFT_SOUND,
                    1.0F,
                    1.0F);
            // The cost is paid both ways, and a short bar is drained to 0.
            StandAbilityStamina.consume(
                    ability, StandPower.get(user), STAMINA_COST, false);
        }
    }
}
