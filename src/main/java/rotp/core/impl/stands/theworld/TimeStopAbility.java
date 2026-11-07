package rotp.core.impl.stands.theworld;

import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.api.timestop.TimeStopAudioContext;
import rotp.core.api.timestop.TimeStopAudioCue;
import rotp.core.api.timestop.TimeStopAudioDecision;
import rotp.core.api.timestop.TimeStopBehaviorPolicies;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.api.timestop.TimeStopStartupCostDecision;
import rotp.core.client.ui.hud_power.WindupIndicator;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.event.ModEventHooks;
import rotp.core.JojoModConfig;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.TrainableAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.sound.MultiSoundEventResolver;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class TimeStopAbility extends StandEntityAbility implements TrainableAbility {
	private static final ActionAnimIdentifier TIME_STOP_ANIM = ActionAnimIdentifier.getOrCreate("time_stop", false);
	// only says the ability has a charge at all; a press reads its length from the config
	private static final int DEFAULT_CHARGE_PHASE_TICKS = 30;
	private static final int TIME_STOP_SOUND_MIN_TICKS = 40;
	/** A release before this share of the full charge starts nothing (the owner, 2026-11-05). */
	private static final float MIN_RELEASE_CHARGE_SHARE = 0.5F;
	/** A second press this many ticks or fewer after the first fires at once: vanilla's window for a double-tap sprint. */
	public static final int DOUBLE_PRESS_WINDOW_TICKS = 7;
	// a tap is over by then, so it says nothing; a charge let go before its minimum has its line cut off
	private static final int CHARGE_VOICE_DELAY_TICKS = 4;
	// the user's TIME_STOP effect also covers this opening (time_stop_9 checks 180 + 35 ticks)
	public static final int TIME_STOP_OPENING_SETTLE_TICKS = 35;
	private static final int STAR_PLATINUM_RESOLVE_LEVEL_TO_UNLOCK = 4;
	private static final int THE_WORLD_RESOLVE_LEVEL_TO_UNLOCK = 2;
	// 1.16 core time stops had heldWalkSpeed(0); the owner's Batch907 boundary keeps the charge free to walk.
	private float heldWalkSpeed = 1.0F;

	public TimeStopAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, TimeStopAction::new);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, DEFAULT_CHARGE_PHASE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		partsRequired(StandPart.MAIN_BODY);
	}

	/** 1.16 TimeStop.Builder#heldWalkSpeed: the user's walk speed while the time stop charges (Shadow The World 0.7). */
	public TimeStopAbility setHeldWalkSpeed(float heldWalkSpeed) {
		if (!Float.isFinite(heldWalkSpeed) || heldWalkSpeed < 0.0F || heldWalkSpeed > 1.0F) {
			throw new IllegalArgumentException("Time Stop held walk speed must be within 0..1");
		}
		this.heldWalkSpeed = heldWalkSpeed;
		return this;
	}

	public float getHeldWalkSpeed() {
		return heldWalkSpeed;
	}

	@Override
	public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
		return false;
	}

	/** 1.16 TimeStop was a StandAction the user's power performed in the tick its hold fired, the charge's N-th. */
	@Override
	public boolean chargeFiresAsItsLengthEnds(EntityActionInstance action) {
		return true;
	}

	// 1.16 TimeStop was a StandAction its user's power held, its Stand out or not: never the Stand's task
	@Override
	public boolean performsInUsersStandPowerSlot() {
		return true;
	}

	/**
	 * 1.16 TimeStop.getCooldownTechnical: the ticks of the stop this time stop starts; its additional part was 0 and its
	 * cooldown proper is set as the stop ends.
	 */
	@Override
	public int getCooldownTechnical(Power<?> context) {
		return TimeStopLearning.getTimeStopTicks(PowerClass.STAND.cast(context), getLearningAbilityName());
	}

	@Override
	public boolean canBeUsedInStoppedTime(Power<?> context) {
		return true;
	}

	@Override
	public boolean canUserSeeInStoppedTime(LivingEntity user, Power<?> context) {
		return true;
	}

	@Override
	public String getLearningAbilityName() {
		// 1.16 trained each TimeStop action on its own; the core's time stops are all "time_stop"
		return abilityId.powerTypeId() != null ? name() : TimeStopLearning.TIME_STOP;
	}

	@Override
	public float getMaxTrainingPoints(StandPower power) {
		return TimeStopLearning.getMaxTrainingPoints(power);
	}

	@Override
	protected int getRequiredResolveLevelToUnlock(Power<?> context) {
		StandPower power = PowerClass.STAND.cast(context);
		if (power == null) {
			return super.getRequiredResolveLevelToUnlock(context);
		}
		if (power.getPowerType() == ModStands.STAR_PLATINUM.get()) {
			return STAR_PLATINUM_RESOLVE_LEVEL_TO_UNLOCK;
		}
		if (power.getPowerType() == ModStands.THE_WORLD.get()) {
			return THE_WORLD_RESOLVE_LEVEL_TO_UNLOCK;
		}
		return super.getRequiredResolveLevelToUnlock(context);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		StandPower power = PowerClass.STAND.cast(context);
		LivingEntity user = power != null ? power.getUser() : null;
		if (power == null || user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (hasOwnTimeStop(user)) {
			return ConditionCheck.POSITIVE;
		}
		if (!isCurrentlyInStoppedTime(user) && hasUncancellableStandAction(power)) {
			return ConditionCheck.NEGATIVE;
		}
		// 1.16 had no stamina gate here: a start the bar cannot pay empties it and the stop ends on its first tick. A
		// start the startup-cost policy denies is refused without a message, as 1.16 TimeStop refused a start it would
		// not make (an uncancellable Stand action above) and as the release below starts nothing in silence.
		return isTimeStopStartDenied(power)
				? ConditionCheck.NEGATIVE
				: ConditionCheck.POSITIVE;
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		if (hasOwnTimeStop(user)) {
			bufferingState.isActionSuccess = requestManualResume(level, user);
			if (bufferingState.isActionSuccess) {
				noteResumePress(user);
			}
			return null;
		}
		StandPower power = !level.isClientSide() ? PowerClass.STAND.get(user) : null;
		boolean standAlreadySummoned = power != null && power.getSummonedStandEntity() != null;
		// a press held back while the Stand power holds an action leaves that hold to setOrBufferAction's buffer
		if (power != null && !isCurrentlyInStoppedTime(user) && !isHeldPressHeldBack(user)) {
			clearCurrentStandAction(power);
		}

		HeldInput heldInput = power != null
				&& power.getSummonedStandEntity() == null
				&& canStartWithoutStand(power, user)
				? setOrBufferAction(level, user, user, inputMethod, extraClientInput, clickHoldResolveTime, bufferingState)
				: super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
		if (heldInput instanceof TimeStopAction timeStopAction) {
			timeStopAction.setStandAlreadySummoned(standAlreadySummoned);
			// 1.16 TimeStop#getShout: no line inside stopped time, where the charge is 0 and no press is a double press
			if (bufferingState.isActionSuccess && power != null && !isCurrentlyInStoppedTime(user)) {
				timeStopAction.voiceDue = true;
				if (timeStopAction.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) > 0 && isSecondPress(user)) {
					timeStopAction.fireDoublePress(this);
					return null;
				}
			}
		}
		return heldInput;
	}

	/**
	 * The press that began a charge is a double press's first; another within {@link #DOUBLE_PRESS_WINDOW_TICKS} is its
	 * second. A press that close to a resume is neither, so mashing the key to resume a stop starts no new one.
	 */
	private static boolean isSecondPress(LivingEntity user) {
		EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		long now = user.level().getGameTime();
		boolean afterResume = now - input.timeStopResumePressTime <= DOUBLE_PRESS_WINDOW_TICKS;
		boolean second = !afterResume && now - input.timeStopFirstPressTime <= DOUBLE_PRESS_WINDOW_TICKS;
		input.timeStopFirstPressTime = second || afterResume ? EntityActionInputState.NO_PRESS : now;
		return second;
	}

	static void noteResumePress(LivingEntity user) {
		user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).timeStopResumePressTime = user.level().getGameTime();
	}

	/** The ticks a charge of this length must be held for its release to start a stop. */
	public static int getMinReleaseChargeTicks(float chargeLength) {
		return Mth.ceil(chargeLength * MIN_RELEASE_CHARGE_SHARE);
	}

	private void clearCurrentStandAction(StandPower power) {
		StandEntity standEntity = power.getSummonedStandEntity();
		if (standEntity != null) {
			LivingComponentAction actionComponent = LivingComponentAction.getExistingComponent(standEntity);
			EntityActionInstance curAction = actionComponent != null ? actionComponent.getAction() : null;
			if (curAction != null && canStopCurrentStandActionLikeOriginal(curAction)) {
				actionComponent.setAction(null, SyncType.TRACKING_AND_SELF);
			}
		}
	}

	private boolean hasUncancellableStandAction(StandPower power) {
		StandEntity standEntity = power.getSummonedStandEntity();
		if (standEntity == null) {
			return false;
		}
		LivingComponentAction actionComponent = LivingComponentAction.getExistingComponent(standEntity);
		EntityActionInstance curAction = actionComponent != null ? actionComponent.getAction() : null;
		return curAction != null && !canStopCurrentStandActionLikeOriginal(curAction);
	}

	private boolean canStopCurrentStandActionLikeOriginal(EntityActionInstance curAction) {
		if (curAction.isOver() || curAction.canBeCancelledInto(this)) {
			return true;
		}
		if (curAction.ability instanceof EntityActionAbility entityActionAbility) {
			return entityActionAbility.canBeStoppedByOriginalHoldCancel(curAction);
		}
		return false;
	}

	// 1.16 TheWorldTimeStop.autoSummonStand: no Stand summoned from the stored Resolve level 3
	@Override
	protected AutoSummonMode getAutoSummonMode(StandPower standPower, LivingEntity user) {
		if (standPower.getPowerType() == ModStands.THE_WORLD.get() && standPower.getResolveLevel() >= 3) {
			return AutoSummonMode.DISABLED;
		}
		return super.getAutoSummonMode(standPower, user);
	}

	private boolean canStartWithoutStand(StandPower power, LivingEntity user) {
		return power != null
				&& power.getPowerType() == ModStands.THE_WORLD.get()
				&& power.getResolveLevel() >= 3;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		action.phasesLength.put(ActionPhase.BUTTON_CHARGE, getHoldToFireTicks(powerUser));
		action.phasesLength.put(ActionPhase.PERFORM, 1);
		action.phasesLength.put(ActionPhase.RECOVERY, 0);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return TIME_STOP_ANIM;
	}

	@Override
	public WindupIndicator cl_windupIndicator(LivingEntity clientPlayer, WindupIndicator indicator, float partialTick) {
		if (isCurrentlyInStoppedTime(clientPlayer)) {
			return null;
		}

		indicator.maxValue = buttonChargePhase.getAsFloat() > 0 ? 1 : 0;
		indicator.value = -1;
		if (indicator.maxValue <= 0) {
			return super.cl_windupIndicator(clientPlayer, indicator, partialTick);
		}

		EntityActionInstance curAction = getClientTimeStopAction(clientPlayer);
		if (curAction != null) {
			if (curAction.getPhase() == ActionPhase.BUTTON_CHARGE) {
				indicator.maxValue = curAction.getAnimPhaseLength();
				if (indicator.maxValue > 0) {
					indicator.value = curAction.getAnimPhaseTick(partialTick);
				}
			}
			else {
				indicator.maxValue = curAction.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
				indicator.value = indicator.maxValue;
			}
		}
		return indicator;
	}

	private EntityActionInstance getClientTimeStopAction(LivingEntity clientPlayer) {
		EntityActionInstance userAction = LivingComponentAction.getCurEntityAction(clientPlayer, PowerClass.STAND);
		if (isSameAbilityAction(userAction)) {
			return userAction;
		}
		StandEntity stand = StandUtil.getSummonedStand(clientPlayer);
		EntityActionInstance standAction = stand != null ? LivingComponentAction.getCurEntityAction(stand) : null;
		return isSameAbilityAction(standAction) ? standAction : null;
	}

	@Override
	public Component getName(Power<?> context) {
		StandPower power = PowerClass.STAND.cast(context);
		float seconds = (float) TimeStopLearning.getSavedTimeStopTicks(power, getLearningAbilityName()) / 20F;
		String secondsString = String.format(Locale.ROOT, "%.2f", seconds);
		if (power != null && power.getPowerType() == ModStands.STAR_PLATINUM.get()) {
			return Component.translatable("jojo_ripples.ability.star_platinum_time_stop", secondsString);
		}
		if (power != null && power.getPowerType() == ModStands.THE_WORLD.get()) {
			return Component.translatable("jojo_ripples.ability.the_world_time_stop", secondsString);
		}
		return Component.translatable("jojo_ripples.ability.time_stop", secondsString);
	}

	private float getHoldToFireTicks(LivingEntity user) {
		if (isCurrentlyInStoppedTime(user)) {
			return 0;
		}
		int chargeTicks = JojoModConfig.getCommonConfigInstance(user.level().isClientSide()).timeStopChargeTicks();
		StandPower power = PowerClass.STAND.get(user);
		if (power != null && power.getPowerType() != ModStands.STAR_PLATINUM.get()
				&& shortensChargeAtResolveFour(power) && power.getResolveLevel() >= 4) {
			return getResolveFourChargeTicks(chargeTicks);
		}
		return chargeTicks;
	}

	/** Two thirds of the charge, 1.16's 20 of 30, never under the config's minimum. */
	public static int getResolveFourChargeTicks(int chargeTicks) {
		return Math.max(Math.round(chargeTicks * 2.0F / 3.0F),
				Math.round((float) (JojoModConfig.TIME_STOP_CHARGE_SECONDS_MIN * 20.0D)));
	}

	/**
	 * 1.16 TheWorldTimeStop#getHoldDurationToFire: from Resolve level 4 the charge is shorter, now two thirds of the
	 * config's. Add-on time stops that 1.16 built as a TheWorldTimeStop return true.
	 */
	protected boolean shortensChargeAtResolveFour(StandPower power) {
		return power.getPowerType() == ModStands.THE_WORLD.get();
	}

	private boolean isCurrentlyInStoppedTime(LivingEntity user) {
		if (user.level().isClientSide()) {
			return TimeStopState.getClientDisplayInstance(new ChunkPos(user.blockPosition())).isPresent();
		}
		if (user.level() instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.isTimeStopped(new ChunkPos(user.blockPosition()));
		}
		return false;
	}

	private boolean hasOwnTimeStop(LivingEntity user) {
		if (user.level().isClientSide()) {
			return TimeStopState.getClientInstance(user.getId()).filter(TimeStopState.Instance::isActive).isPresent();
		}
		if (user.level() instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.getInstance(user.getId()).filter(TimeStopState.Instance::isActive).isPresent();
		}
		return false;
	}

	private static boolean requestManualResume(Level level, LivingEntity user) {
		if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.requestManualResume(user.getId());
		}
		return false;
	}

	private boolean isTimeStopStartDenied(StandPower power) {
		float defaultCost = TimeStopLearning.getTimeStopStaminaCost(
				power, getLearningAbilityName(), TimeStopLearning.MIN_RELEASE_TIME_STOP_TICKS);
		return TimeStopBehaviorPolicies.resolveStartupCost(
				power, abilityId, null, defaultCost).isDenied();
	}

	private static float effectiveTimeStopStaminaCost(StandPower power, float amount) {
		return amount * PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power);
	}

	/**
	 * The owner's never-refunded unit (2026-10-14): what the shortest stop, a double press's, costs under the same
	 * startup-cost policy and multiplier as the stop that starts.
	 */
	private float getShortestStopStaminaCost(StandPower power, String learningName, TimeStopState.Instance instance,
			float chargeLength) {
		int shortestTicks = TimeStopLearning.getReleasedTimeStopTicks(TimeStopLearning.getSavedTimeStopTicks(power, learningName),
				TimeStopLearning.MIN_RELEASE_TIME_STOP_TICKS, chargeLength);
		float defaultCost = TimeStopLearning.getTimeStopStaminaCost(power, learningName, shortestTicks);
		TimeStopStartupCostDecision decision = TimeStopBehaviorPolicies.resolveStartupCost(
				power, abilityId, instance.withTiming(shortestTicks, shortestTicks), defaultCost);
		return effectiveTimeStopStaminaCost(power, decision.isDenied() ? defaultCost : decision.resolve(defaultCost));
	}

	// the line it said, to cut off a charge that ends without its stop
	@Nullable
	private Holder<SoundEvent> sayTimeStopVoiceLine(LivingEntity user, StandPower power, boolean standAlreadySummoned) {
		TimeStopAudioDecision decision =
				TimeStopBehaviorPolicies.resolveAudio(
						power.getPowerType().getId(),
						new TimeStopAudioContext(
								TimeStopAudioCue.START_VOICE,
								user,
								power,
								abilityId,
								null,
								standAlreadySummoned));
		Holder<SoundEvent> voiceLine = switch (decision.kind()) {
			case PASS -> getTimeStopVoiceLine(
					user, power, standAlreadySummoned);
			case SOUND -> decision.sound();
			case SILENT -> null;
		};
		if (voiceLine == null || skipsShoutWhileSneaking(user)) {
			return null;
		}
		Holder<SoundEvent> resolved = MultiSoundEventResolver.resolve(voiceLine);
		// 1.16 TimeStop: voiceLineDelay 0, a charge let go early must not keep the next one's line back
		return JojoModUtil.sayVoiceLine(user, resolved, 0) ? resolved : null;
	}

	private Holder<SoundEvent> getTimeStopVoiceLine(LivingEntity user, StandPower power, boolean standAlreadySummoned) {
		if (power.getPowerType() == ModStands.STAR_PLATINUM.get()) {
			return ModSoundEvents.JOTARO_STAR_PLATINUM_THE_WORLD;
		}
		if (power.getPowerType() == ModStands.THE_WORLD.get()) {
			if (user.getRandom().nextFloat() < 0.05F && isJonathanHamonUser(user)) {
				return ModSoundEvents.JONATHAN_THE_WORLD;
			}
			return standAlreadySummoned ? ModSoundEvents.DIO_TIME_STOP : ModSoundEvents.DIO_THE_WORLD;
		}
		return null;
	}

	private boolean isJonathanHamonUser(LivingEntity user) {
		return PlayerPower.getPowerData(user, ModPlayerPowers.HAMON)
				.map(hamon -> hamon.characterIs(ModHamonSkills.CHARACTER_JONATHAN.get()))
				.orElse(false);
	}

	private static Holder<SoundEvent> getTimeStopSound(StandPower power) {
		return power != null && power.getPowerType() == ModStands.STAR_PLATINUM.get()
				? ModSoundEvents.STAR_PLATINUM_TIME_STOP
				: ModSoundEvents.THE_WORLD_TIME_STOP;
	}

	private boolean startTimeStopAfterHold(LivingEntity user, boolean standAlreadySummoned) {
		StandPower power = PowerClass.STAND.get(user);
		return startTimeStopAfterHold(user, standAlreadySummoned,
				TimeStopLearning.getTimeStopTicks(power, getLearningAbilityName()));
	}

	private boolean startTimeStopAfterHold(LivingEntity user, boolean standAlreadySummoned, int requestedTimeStopTicks) {
		return startTimeStopAfterHold(user, standAlreadySummoned, requestedTimeStopTicks, getHoldToFireTicks(user));
	}

	private boolean startTimeStopAfterHold(LivingEntity user, boolean standAlreadySummoned, int requestedTimeStopTicks,
			float chargeLength) {
		return startTimeStopAfterHold(user, standAlreadySummoned, requestedTimeStopTicks, chargeLength, false);
	}

	// chargeLength: the charge the press had; it prices the shortest stop, whose cost an early end never gives back
	private boolean startTimeStopAfterHold(LivingEntity user, boolean standAlreadySummoned, int requestedTimeStopTicks,
			float chargeLength, boolean doublePress) {
		Level level = user.level();
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		StandPower power = PowerClass.STAND.get(user);
		if (power == null || power.getSummonedStandEntity() == null && !canStartWithoutStand(power, user)) {
			return false;
		}
		TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
		int instanceId = user.getId();
		if (state.getInstance(instanceId).filter(TimeStopState.Instance::isActive).isPresent()) {
			return false;
		}
		ChunkPos centerPos = new ChunkPos(user.blockPosition());
		// duration and per-tick cost come from this time stop's own training
		String learningName = getLearningAbilityName();
		int timeStopTicks = Mth.clamp(requestedTimeStopTicks, TimeStopLearning.MIN_RELEASE_TIME_STOP_TICKS,
				TimeStopLearning.getTimeStopTicks(power, learningName));
		String visualRoute = power.getPowerType() == ModStands.STAR_PLATINUM.get()
				? "star_platinum_time_stop"
				: "the_world_time_stop";
		TimeStopState.Instance instance = new TimeStopState.Instance(
				instanceId,
				timeStopTicks,
				timeStopTicks,
				centerPos,
				JojoModConfig.getCommonConfigInstance(level.isClientSide()).timeStopChunkRange.get(),
				user.getId(),
				visualRoute,
				instanceId,
				instanceId,
				TimeStopLearning.getTimeStopStaminaCostTick(power, learningName))
				.withStandSkin(power)
				.withStartupDelay(TIME_STOP_OPENING_SETTLE_TICKS);
		TimeStopLifecycleEvent.PreStart startEvent =
				ModEventHooks.onTimeStopPreStart(serverLevel, instance);
		if (startEvent.isCanceled()) {
			return false;
		}
		instance = startEvent.getInstance();
		centerPos = instance.centerPos();
		boolean invadingStoppedTime = state.isTimeStopped(centerPos);
		if (invadingStoppedTime) {
			Optional<TimeStopState.Instance> currentMaxInstance =
					state.getLongestInstanceIn(centerPos);
			if (currentMaxInstance.map(TimeStopState.Instance::ticksLeft)
					.orElse(0) > instance.ticksLeft()) {
				instance = instance.withResumeSoundAndVoiceLineUserIds(
						currentMaxInstance.get().resumeSoundUserId(), -1);
				startEvent.setInstance(instance);
			}
		}
		float defaultStaminaCost =
				TimeStopLearning.getTimeStopStaminaCost(
						power, learningName, instance.totalTicks());
		TimeStopStartupCostDecision startupCost =
				TimeStopBehaviorPolicies.resolveStartupCost(
						power,
						abilityId,
						instance,
						defaultStaminaCost);
		if (startupCost.isDenied()) {
			return false;
		}
		float timeStopStaminaCost = effectiveTimeStopStaminaCost(
				power, startupCost.resolve(defaultStaminaCost));
		float staminaBefore = power.getStamina();
		// 1.16 StandAction.consumeStamina ignored the result: a start the bar cannot pay empties it and still begins
		boolean startPaid = power.consumeStamina(timeStopStaminaCost, false);
		// what the start really took (0 with infinite stamina); the early-resume refund is a share of it
		float chargedStartCost = Math.max(staminaBefore - power.getStamina(), 0.0F);
		if (!startPaid) {
			// 1.16 ended an unpaid stop on its first tick with the bar at 0: no opening to wait out, nothing to refund
			chargedStartCost = 0.0F;
			instance = instance.withStartupDelay(0);
			startEvent.setInstance(instance);
		}
		float unrefundableStartCost = getShortestStopStaminaCost(power, learningName, instance, chargeLength);
		int startupDelay = Math.max(-instance.ticksPassed(), 0);
		int statusDuration = (int) Math.min(
				(long) instance.ticksLeft() + startupDelay,
				Integer.MAX_VALUE);
		user.addEffect(new MobEffectInstance(
				ModStatusEffects.TIME_STOP,
				statusDuration,
				0,
				false,
				false,
				true));
		if (!state.commitPreStart(startEvent, this, chargedStartCost, unrefundableStartCost)) {
			user.removeEffect(ModStatusEffects.TIME_STOP);
			if (!power.isStaminaInfinite()) {
				power.setStamina(power.getStamina() + chargedStartCost);
			}
			return false;
		}
		if (state.getInstance(instance.id()).orElse(null) != instance) {
			return false;
		}
		TimeStopLearning.markUsedTimeStopToday(power);
		ModCriteriaTriggers.triggerTimeAbility(user, power);
		if (!invadingStoppedTime) {
			Holder<SoundEvent> startSound = doublePress
					? getDoublePressSound(user, power, instance, standAlreadySummoned)
					: instance.totalTicks() >= TIME_STOP_SOUND_MIN_TICKS
							? getStartSound(user, power, instance, standAlreadySummoned)
							: null;
			TimeStopState.Instance soundInstance = instance;
			if (startSound != null) {
				StandUtil.broadcastSoundWithCondition(
						serverLevel,
						user.position(),
						startSound,
						false,
						power,
						SoundSource.AMBIENT,
						5.0F,
						1.0F,
						player -> soundInstance.covers(
										new ChunkPos(
												player.blockPosition()))
								&& TimeStopState
										.canPlayerSeeInStoppedTime(player));
			}
		}
		return true;
	}

	@Nullable
	private Holder<SoundEvent> getStartSound(LivingEntity user, StandPower power, TimeStopState.Instance instance,
			boolean standAlreadySummoned) {
		TimeStopAudioDecision soundDecision =
				TimeStopBehaviorPolicies.resolveAudio(
						instance.standTypeId().orElse(null),
						new TimeStopAudioContext(
								TimeStopAudioCue.START_SOUND,
								user,
								power,
								abilityId,
								instance,
								standAlreadySummoned));
		return switch (soundDecision.kind()) {
			case PASS -> getTimeStopSound(power);
			case SOUND -> soundDecision.sound();
			case SILENT -> null;
		};
	}

	// the sound of 1.16's instant stop, TimeStopInstant's blink, or the Stand's stop sound if it has no blink
	@Nullable
	private Holder<SoundEvent> getDoublePressSound(LivingEntity user, StandPower power, TimeStopState.Instance instance,
			boolean standAlreadySummoned) {
		for (Ability ability : power.getMoveset().abilities.values()) {
			if (ability instanceof TimeStopBlinkAbility blink && name().equals(blink.getTimeStopAbilityName())) {
				return blink.getTimeStopBlinkSound(power);
			}
		}
		return getStartSound(user, power, instance, standAlreadySummoned);
	}

	public static class TimeStopAction extends EntityActionInstance {
		private boolean standAlreadySummoned;
		// server: this charge's line, due from its press until it is said
		private boolean voiceDue;
		@Nullable private Holder<SoundEvent> saidVoice;
		private boolean stopStarted;

		public TimeStopAction(EntityActionType ability) {
			super(ability);
		}

		void setStandAlreadySummoned(boolean standAlreadySummoned) {
			this.standAlreadySummoned = standAlreadySummoned;
		}

		// 1.16 TimeStop was a StandAction its user's power performed, which the Stand's summon lock never held back
		@Override
		public boolean isHeldBackBySummonLock() {
			return false;
		}

		// 1.16 StandEntity.getCurPose drew the power's held time stop's windup before the Stand's task, then its perform pose
		@Override
		public boolean posesItsStand() {
			return true;
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = 1.0F;
			if (newPhase == ActionPhase.BUTTON_CHARGE && ability instanceof TimeStopAbility timeStop) {
				userWalkSpeed = timeStop.heldWalkSpeed;
			}
			// 1.16 TimeStop.perform: setStandPose(TimeStop.ANIM) on the Stand
			if (newPhase == ActionPhase.PERFORM && getPerformer() instanceof StandEntity stand) {
				stand.setPerformPose(this);
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && curPhaseTick + 1 >= CHARGE_VOICE_DELAY_TICKS) {
				sayChargeVoice();
			}
		}

		private void sayChargeVoice() {
			if (!voiceDue) {
				return;
			}
			voiceDue = false;
			LivingEntity user = getPowerUser();
			StandPower power = user != null ? PowerClass.STAND.get(user) : null;
			if (power != null && ability instanceof TimeStopAbility timeStop) {
				saidVoice = timeStop.sayTimeStopVoiceLine(user, power, standAlreadySummoned);
			}
		}

		// a charge that ends without its stop, by a release too early, a hit or anything else, stops announcing it
		@Override
		public void onActionCleared(@Nullable EntityActionInstance newAction) {
			LivingEntity user = getPowerUser();
			if (saidVoice != null && !stopStarted && !takesTargetsFromServer() && user != null) {
				JojoModUtil.stopVoiceLine(user, saidVoice);
			}
		}

		@Override
		public void actionPerformStart() {
			if (level().isClientSide()) {
				return;
			}
			sayChargeVoice();
			LivingEntity user = getPowerUser();
			if (ability instanceof TimeStopAbility timeStop && user != null) {
				stopStarted = timeStop.startTimeStopAfterHold(user, standAlreadySummoned,
						TimeStopLearning.getTimeStopTicks(PowerClass.STAND.get(user), timeStop.getLearningAbilityName()),
						phasesLength.getFloat(ActionPhase.BUTTON_CHARGE));
			}
			if (!stopStarted) {
				LivingComponentAction.getComponentForSlot(performer, actionSlot()).setAction(null, SyncType.TRACKING_AND_SELF);
			}
			// the user's own client may have let go of its copy, which this full charge's perform then no longer reaches;
			// a client whose copy performed has the pose already (handleEntityEvent leaves a pose the Stand has)
			else if (getPerformer() instanceof StandEntity stand) {
				level().broadcastEntityEvent(stand, StandEntity.PERFORM_POSE_EVENT);
			}
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE) {
				if (!takesTargetsFromServer()) {
					if (ability instanceof TimeStopAbility timeStop
							&& curPhaseTick >= getMinReleaseChargeTicks(curPhaseLength)) {
						startShorterStop(timeStop, Math.max(curPhaseTick, 1), false);
					}
				}
				// the user's own client lets go of its copy before the server decides the release, which a charge let go too
				// early, a failed recheck or a refused start turns down: 1.16 posed only a time stop that performed, so the
				// Stand waits for the server's event, as on the other clients
				else if (getPerformer() instanceof StandEntity stand) {
					stand.awaitPerformPoseEvent(this);
				}
				LivingComponentAction.getComponentForSlot(performer, actionSlot()).setAction(null, SyncType.TRACKING_AND_SELF);
			}
		}

		// the stop a release at once would give, had the charge's minimum not ruled that out: only the input differs
		private void fireDoublePress(TimeStopAbility timeStop) {
			voiceDue = false;
			startShorterStop(timeStop, 1, true);
			LivingComponentAction.getComponentForSlot(performer, actionSlot()).setAction(null, SyncType.TRACKING_AND_SELF);
		}

		// 1.16 stopHeldAction(true): a release that fails the recheck starts nothing, silently
		private void startShorterStop(TimeStopAbility timeStop, int chargeTicks, boolean doublePress) {
			LivingEntity user = getPowerUser();
			if (user == null || !timeStop.canFireReleasedHold(this)) {
				return;
			}
			sayChargeVoice();
			StandPower power = PowerClass.STAND.get(user);
			String learningName = timeStop.getLearningAbilityName();
			int maxTicks = TimeStopLearning.getSavedTimeStopTicks(power, learningName);
			float chargeRatio = TimeStopLearning.getTimeStopChargeRatio(
					chargeTicks, curPhaseLength);
			int timeStopTicks = TimeStopLearning.getReleasedTimeStopTicks(
					maxTicks, chargeTicks, curPhaseLength);
			float learningPoints = power != null && power.getCurTypeData() != null
					? power.getCurTypeData().getAbilityLearningProgressPoints(learningName)
					: -1.0F;
			JojoMod.getLogger().info(
					"Time stop release resolved: user={}, stand={}, chargeTicks={}, chargeLength={}, ratio={}, learningPoints={}, maxTicks={}, resolvedTicks={}.",
					user.getScoreboardName(),
					power != null && power.getPowerType() != null ? power.getPowerType().getId() : "<none>",
					chargeTicks, curPhaseLength, chargeRatio, learningPoints, maxTicks, timeStopTicks);
			stopStarted = timeStop.startTimeStopAfterHold(user, standAlreadySummoned, timeStopTicks, curPhaseLength, doublePress);
			if (stopStarted && getPerformer() instanceof StandEntity stand) {
				// the port's shorter stop (D-core-91) has no perform: its Stand takes the full stop's perform
				// pose, and every client takes it from the event: the others, whose copy this release reaches
				// only after it was cleared, and the user's own, which let go of its copy before
				stand.setPerformPose(this);
				level().broadcastEntityEvent(stand, StandEntity.PERFORM_POSE_EVENT);
			}
		}
	}

	// 1.16 TimeStop.Builder staminaCost(225), a full charge's start
	@Override
	public float getStaminaCost(Power<?> context) {
		return TimeStopLearning.BASE_STAMINA_COST;
	}
}
