package rotp.addons.kingcrimson.ability;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.network.s2c.PlayVoiceLinePacket;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.sound.MultiSoundEventResolver;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.stand.KingCrimsonStandType;
import rotp.addons.kingcrimson.state.KingCrimsonBloodThrowService;
import rotp.addons.kingcrimson.state.KingCrimsonCreatureErase;
import rotp.addons.kingcrimson.state.KingCrimsonEraseStamina;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KingCrimsonTimeEraseAbility
        extends NoPoseStandEntityAbility {
    public KingCrimsonTimeEraseAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, TimeEraseAction::new);
        partsRequired(StandPart.ARMS);
        standAutoSummonMode(AutoSummonMode.ARMS);
        // The erase's own length is the service's: the hold only lasts as long as the erase does (actionTick).
        setButtonHoldPhase(ActionPhase.PERFORM);
        cooldown(KingCrimsonPhase5Spec.TIME_ERASE_FIXED_COOLDOWN_TICKS);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        // 1.16 kingcrimson_timeerase was a plain StandAction cooldown(timeEraseCooldown) (062 InitStands :81): none in
        // Creative or Resolve, kept with Stand stamina off. Its onTaskStopped computed the dynamic cooldown into a
        // local it never applied (KingCrimsonTimeErase :245-252), so the hold's length never changed it.
        // Owner 2026-11-11: "時停和時刪在正常cd模式下邏輯趨同，包括cd隨發動時間的延長". As the core's time stop
        // (TimeStopCooldowns): by the ticks the erase ran (ticksHeld; the fixed setting where they are not known), a
        // third of that in Resolve, none in Creative; none in the mode with no cooldowns, as for every ability.
        int byLength = KingCrimsonConfig.timeEraseCooldownTicks(ticksHeld);
        if (context instanceof StandPower power && power.getUser() != null && !power.isUserCreative()
                && power.getUser().hasEffect(ModStatusEffects.RESOLVE)
                && !PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(power)) {
            return byLength / 3;
        }
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld),
                byLength);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // EV1105AC section 8: an erase that goes on with its key up is ended by its key, whatever the Stand does.
        if (context.getUser() instanceof Player eraser
                && KingCrimsonTemporalStateService.isKeyUpErase(eraser)) {
            return ConditionCheck.POSITIVE;
        }
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)) {
            // Owner 2026-11-11: a creature that carries King Crimson erases time as a player does, for a player's
            // cost and cooldown. No other creature has the key.
            if (context.getUser() instanceof Mob creature && KingCrimsonCreatureErase.bears(creature)) {
                return KingCrimsonCreatureErase.canBegin(creature)
                        ? ConditionCheck.POSITIVE
                        : negative("temporal_state_active");
            }
            return negative("player_only");
        }
        if (player instanceof ServerPlayer serverPlayer
                && !KingCrimsonTemporalStateService
                        .canStartTimeErase(serverPlayer)) {
            return negative("temporal_state_active");
        }
        if (player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                && !KingCrimsonAddon.temporalState(player).isIdle()) {
            return negative("temporal_state_active");
        }
        return ConditionCheck.POSITIVE;
    }

    // In an erase that goes on with its key up the key ends the erase: nothing is summoned, begun or paid. Else
    // the press is the Stand's (the core brings a Stand along that a teleport left where nothing ticks it).
    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        if (!level.isClientSide() && user instanceof ServerPlayer player) {
            if (KingCrimsonTemporalStateService.isKeyUpErase(player)) {
                KingCrimsonTemporalStateService.terminateOnInteraction(player);
                bufferingState.setActionSuccess();
                return null;
            }
        }
        return super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
    }

    // Full addon key; the String overload would add the jojo prefix.
    private static ConditionCheck negative(String key) {
        return ConditionCheck.createNegative(
                Component.translatable(
                        "rotp_kingcrimson.message." + key));
    }

    public static final class TimeEraseAction
            extends EntityActionInstance {
        private boolean performStarted;
        private boolean handedOffToAttack;
        private boolean recalled;
        // The key was let go for a Blood Throw: the erase goes on without this action.
        private boolean keyUpThrow;
        private long eraseStartedAt = -1L;

        public TimeEraseAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (getPerformer() instanceof StandEntity stand
                    && !stand.level().isClientSide()) {
                stand.summonLockTicks = 0;
            }
        }

        @Override
        public void actionPerformStart() {
            // The erase of a creature is not held: it runs by itself (KingCrimsonCreatureErase), which also takes
            // its cost, and the creature's Stand is free for whatever the creature does next.
            if (!performStarted && !level().isClientSide() && getPowerUser() instanceof Mob creature) {
                performStarted = true;
                KingCrimsonCreatureErase.begin(creature);
                forceStop();
                syncPhaseChanges();
                return;
            }
            if (performStarted
                    || level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer player)
                    || !(ability
                            instanceof KingCrimsonTimeEraseAbility
                            eraseAbility)) {
                return;
            }
            performStarted = true;
            boolean began = KingCrimsonTemporalStateService
                    .beginTimeErase(player);
            // 1.16 StandEntityTask pays once PERFORM starts, after standPerform, and ignores the result: a short
            // bar drops to 0 and the per-tick upkeep then ends the erase.
            float charged = KingCrimsonEraseStamina.payStart(
                    (Ability) eraseAbility,
                    StandPower.get(player),
                    KingCrimsonPhase5Spec
                            .TIME_ERASE_ACTIVATION_STAMINA);
            if (!began) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            // What the start really took: an early end gives the unused share of it back (KingCrimsonEraseStamina).
            KingCrimsonTemporalStateService.noteStartCharged(player, charged);
            // In the mode with no cooldowns an erase begun while the last one's fatigue runs pays the core's repeat
            // now, as a time stop does; its end adds the fatigue (KingCrimsonTemporalStateService).
            StandAbilityFatigue.chargeRepeat(StandPower.get(player), (Ability) eraseAbility);
            // Owner 2026-11-07: nobody around is told that an erase began, so its user alone hears his line. (The
            // core's voice line would be refused anyway: its speaker is invisible by now.)
            if (!((Ability) eraseAbility).skipsShoutWhileSneaking(player)) {
                PacketDistributor.sendToPlayer(player, new PlayVoiceLinePacket(
                        MultiSoundEventResolver.resolve(KingCrimsonAddon.KINGCRIMSON_TIMEERASE),
                        SoundSource.VOICE, player.getId(), 1.0F, 1.0F, false));
            }
            eraseStartedAt = KingCrimsonAddon.temporalState(player).startedAt();
        }

        @Override
        public void actionTick() {
            if (!performStarted && !level().isClientSide() && getPhase() == ActionPhase.PERFORM
                    && getPowerUser() instanceof Mob) {
                actionPerformStart();
                return;
            }
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM
                    || !(getPowerUser()
                            instanceof ServerPlayer player)
                    || !(ability
                             instanceof KingCrimsonTimeEraseAbility
                             eraseAbility)) {
                return;
            }
            if (!performStarted) {
                actionPerformStart();
                return;
            }
            if (!KingCrimsonAddon.temporalState(player)
                    .isErasing(level().getGameTime())) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            // The temporal service owns upkeep even after this action hands off to a windup.
        }

        // 1.16 kingcrimson_timeerase was holdType() with no recovery: another
        // Stand action pressed during the erase replaced it at once
        // (isCancelable), and any other action of the power ended the hold
        // (stopHeldAction(false)); onTaskStopped then ended the erase
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // The port's punches and barrages keep the erase until they reach
        // their perform, where TimeEraseAttackPhaseMixin ends it.
        private boolean handsOffTo(EntityActionType next) {
            return getPhase() == ActionPhase.PERFORM
                    && (next instanceof StandEntityPunchAbility
                            || next instanceof StandEntityBarrageAbility);
        }

        @Override
        public void onKeyRelease(LivingEntity user) {
            if ((handedOffToAttack || recalled) && user instanceof ServerPlayer player
                    && beganRunningErase(player)
                    && !keepsEraseForBloodThrow(player)) {
                KingCrimsonTemporalStateService.terminateOnInteraction(player);
            }
            super.onKeyRelease(user);
        }

        // This action ends the erase it began, never a later one: the passive's erase may follow it at once.
        private boolean beganRunningErase(ServerPlayer player) {
            return KingCrimsonAddon.temporalState(player).startedAt() == eraseStartedAt;
        }

        // EV1105AC section 8 (owner: "通過時刪結束這個鍵的變體操作"): the key let go with the variation key down throws blood
        // at the crosshair, and the erase goes on with its key up. With nothing thrown the release ends the erase
        // as it always did: the variation key's default is also the sprint key.
        private static boolean keepsEraseForBloodThrow(ServerPlayer player) {
            return StandManualInput.isShiftHeld(player)
                    && KingCrimsonBloodThrowService.throwAtAim(player)
                    && KingCrimsonTemporalStateService.keepEraseWithKeyUp(player);
        }

        @Override
        public void onButtonStopHold() {
            if (!level().isClientSide()
                    && getPowerUser()
                            instanceof ServerPlayer player
                    && beganRunningErase(player)) {
                if (keepsEraseForBloodThrow(player)) {
                    keyUpThrow = true;
                } else if (!KingCrimsonTemporalStateService
                        .deferEndWhileFrozen(player)) {
                    KingCrimsonTemporalStateService.terminate(player);
                }
            }
            forceStop();
            syncPhaseChanges();
        }

        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            if (newAction != null && handsOffTo(newAction.ability)) {
                handedOffToAttack = true;
                return;
            }
            // The erase is the user's and not the Stand's body: it goes on from his tick, and its key still ends it.
            if (!level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer recalledUser
                    && KingCrimsonStandType.isRecall(recalledUser, newAction)) {
                recalled = true;
                return;
            }
            if (!keyUpThrow
                    && !level().isClientSide()
                    && getPowerUser()
                            instanceof ServerPlayer player
                    && beganRunningErase(player)
                    && !KingCrimsonTemporalStateService
                            .deferEndWhileFrozen(player)) {
                KingCrimsonTemporalStateService.terminate(player);
            }
        }
    }
}
