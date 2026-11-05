package rotp.addons.beachboy.ability;

import rotp.addons.beachboy.util.BeachBoyCombatUtil;
import rotp.addons.beachboy.util.BeachBoySoundUtil;
import rotp.addons.beachboy.util.BeachBoyStaminaPause;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 BeachBoyHeartCrushAction (Shift variation of the tear): held for 5 s (holdToFire(100), heldWalkSpeed 0.2) it
 * crushes the hooked creature's heart: 18 damage through armour, bleeding, Collapse for 8 s, the hook torn out; 110
 * stamina as it fires. Pesci shouts as the hold starts; letting go earlier, or losing the hooked creature or the rod,
 * cancels it.
 */
public class BeachBoyHeartCrushAbility extends EntityActionAbility {
    public static final int HOLD_TICKS = BeachBoyCombatUtil.HEART_CRUSH_HOLD_TICKS;
    private static final float STAMINA_COST = 110.0F;
    private static final float HELD_WALK_SPEED = 0.2F;

    public BeachBoyHeartCrushAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, HeartCrushAction::new);
        setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, HOLD_TICKS);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(0);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return STAMINA_COST;
    }

    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 StandAction.getPerformer: the summoned Stand, else the user
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        return stand != null ? stand : user;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        if (level.isClientSide()) {
            return null;
        }
        BeachBoyAbility.autoSummon(user, StandPower.get(user));
        return setOrBufferAction(level, user, getPerformer(user), inputMethod, extraClientInput,
                clickHoldResolveTime, bufferingState);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null || !BeachBoyStandUtil.isBeachBoyUser(user) || !BeachBoyStandUtil.isHoldingRod(user)) {
            return ConditionCheck.NEGATIVE;
        }
        return BeachBoyCombatUtil.hookedLiving(BeachBoyStandUtil.findHook(user)) == null
                ? ConditionCheck.createNegative("beach_boy_need_hooked") : ConditionCheck.POSITIVE;
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
        ConditionCheck base = super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        return ConditionCheck.noMessage(user != null && BeachBoyStandUtil.isHoldingRod(user)
                && BeachBoyCombatUtil.hookedLiving(BeachBoyStandUtil.findHook(user)) != null);
    }

    @Override
    public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (!level.isClientSide() && powerUser != null) {
            BeachBoySoundUtil.sayKillYou(powerUser);
        }
    }

    public static final class HeartCrushAction extends EntityActionInstance {
        public HeartCrushAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            userWalkSpeed = newPhase == ActionPhase.BUTTON_CHARGE ? HELD_WALK_SPEED : 1.0F;
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
            }
        }

        @Override
        public void actionPerformStart() {
            LivingEntity user = getPowerUser();
            if (level().isClientSide() || user == null || !(ability instanceof BeachBoyHeartCrushAbility crush)) {
                return;
            }
            StandPower power = StandPower.get(user);
            if (BeachBoyCombatUtil.hookedLiving(BeachBoyStandUtil.findHook(user)) == null) {
                return;
            }
            StandAbilityStamina.consume((rotp.core.powersystem.ability.Ability) crush, power, STAMINA_COST, false);
            BeachBoyStaminaPause.pause(user);
            BeachBoyCombatUtil.performRip(level(), user, power, true);
        }
    }
}
