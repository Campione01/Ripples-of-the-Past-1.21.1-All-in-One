package rotp.addons.ger.ability;

import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.state.GerRtzRewindService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class GerReturnToZeroAbility
        extends StandEntityAbility {
    public static final int COOLDOWN_TICKS = 160;
    public static final float STAMINA_COST = 200.0F;

    public GerReturnToZeroAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ReturnToZeroAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        cooldown(COOLDOWN_TICKS);
        standAutoSummonMode(AutoSummonMode.FULL);
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
        if (!base.isPositive()
                || !(context instanceof StandPower power)) {
            return base;
        }
        LivingEntity user = power.getUser();
        if (!(user instanceof net.minecraft.world.entity.player.Player player)) {
            return ConditionCheck.createNegative(
                    "ger_rtz_no_mark");
        }
        boolean hasMark = GoldExperienceRequiemAddon
                .playerState(player)
                .returnToZeroMarkAvailable();
        boolean canForceClose = GoldExperienceRequiemAddon
                .playerState(player)
                .returnToZeroPresentationActive()
                || player instanceof ServerPlayer serverPlayer
                        && GerRtzRewindService
                                .hasActivePresentation(serverPlayer);
        if (!hasMark && !canForceClose) {
            return ConditionCheck.createNegative(
                    "ger_rtz_no_mark");
        }
        // 1.16 ReturnToZero.checkSpecificConditions, which Resolve does not lift.
        return power.usesStamina() && power.getStamina() < STAMINA_COST
                ? ConditionCheck.createNegative("no_stamina")
                : ConditionCheck.POSITIVE;
    }

    /**
     * Owner Q9: its holder may press it while frozen in someone else's stopped
     * time. He still does not see in it: canUserSeeInStoppedTime stays false.
     */
    @Override
    public boolean canBeUsedInStoppedTime(Power<?> context) {
        return true;
    }

    @Override
    public HeldInput onKeyPress(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod,
            float clickHoldResolveTime,
            BufferingState bufferingState) {
        HeldInput initiated = super.onKeyPress(
                level,
                user,
                extraClientInput,
                inputMethod,
                clickHoldResolveTime,
                bufferingState);
        if (initiated instanceof ReturnToZeroAction action
                && action.getPerformer() != null
                && cannotWaitForItsTick(user, action.getPerformer())) {
            performAtOnce(action);
            return null;
        }
        return initiated;
    }

    // The tick of a frozen performer does not advance a Stand's summon, and
    // another's lockdown sends every action into recovery on its next tick.
    private static boolean cannotWaitForItsTick(
            LivingEntity user, LivingEntity performer) {
        return TimeStopState.shouldFreezeOnServer(user)
                || GoldExperienceRequiemAddon.livingState(user)
                        .returnToZeroDisabled()
                || GoldExperienceRequiemAddon.livingState(performer)
                        .returnToZeroDisabled();
    }

    private static boolean performAtOnce(ReturnToZeroAction action) {
        LivingComponentAction actionComponent =
                LivingComponentAction.getComponent(
                        action.getPerformer());
        try {
            action.setPhase(ActionPhase.PERFORM, 0);
            action.syncPhaseChanges();
            action.actionPerformStart();
            return action.result
                    == GerRtzRewindService.Result.SUCCESS;
        }
        finally {
            if (actionComponent.getAction() == action) {
                actionComponent.setAction(
                        null, SyncType.TRACKING_AND_SELF);
            }
        }
    }

    /**
     * Runs the normal RTZ action once for the narrow server-side response to
     * a newly covering time stop, past the core's checks of a press; every
     * other normal availability check remains.
     */
    public boolean tryAutomaticTimeStopResponse(
            ServerLevel level,
            ServerPlayer player,
            StandPower power) {
        if (player.serverLevel() != level
                || !player.isAlive()
                || power == null
                || !power.hasPower()
                || power.getUser() != player
                || power.getPowerType()
                        != GoldExperienceRequiemAddon.GER_STAND.get()
                || !power.canUsePower()
                || power.getMoveset().getAbility(
                        GoldExperienceRequiemAddon
                                .GER_RETURN_TO_ZERO_NAME) != this
                || !isAbilityAvailable(power)
                || (!PlayerClientBroadcastedSettings
                                .isNoStandAbilityCooldownEnabled(power)
                        && power.isAbilityOnCooldown(name()))) {
            return false;
        }

        StandEntity summoned = power.getSummonedStandEntity();
        if (summoned != null
                && (!summoned.isAlive()
                        || ModStatusEffects.isStunned(summoned))) {
            return false;
        }
        if (!checkSpecificConditions(power).isPositive()) {
            return false;
        }

        HeldInput initiated = super.onKeyPress(
                level,
                player,
                null,
                InputMethod.CLICK,
                0.0F,
                BufferingState.clickOnly());
        if (!(initiated instanceof ReturnToZeroAction action)
                || action.getPerformer() == null) {
            return false;
        }
        action.automatic = true;
        return performAtOnce(action);
    }

    public static final class ReturnToZeroAction
            extends EntityActionInstance {
        private GerRtzRewindService.Result result;
        // The coordinator, R772.1: only a key press breaks a prediction; the answer to a stop answers the stop.
        private boolean automatic;

        public ReturnToZeroAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (!(user instanceof ServerPlayer player)) {
                return;
            }
            // EPITAPH-ARCH 3.7: before the mark and the stamina are looked at.
            if (!automatic
                    && GerRtzRewindService.breaksEpitaphPrediction(player)) {
                result = GerRtzRewindService.Result.PREDICTION_BROKEN;
                return;
            }
            StandPower power = StandPower.get(player);
            result = GerRtzRewindService.rewind(
                    serverLevel,
                    player,
                    getPerformer(),
                    power,
                    STAMINA_COST);
            if (result == GerRtzRewindService.Result.FORCE_CLOSED) {
                return;
            }
            if (result != GerRtzRewindService.Result.SUCCESS) {
                player.displayClientMessage(
                        Component.translatable(
                                result.translationKey()),
                        true);
                return;
            }
            if (ability instanceof GerReturnToZeroAbility rtz) {
                rtz.setCooldownOnUse(power);
            }
        }
    }
}
