package rotp.core.powersystem.standpower;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.mojang.datafixers.util.Either;

import rotp.core.JojoModConfig;
import rotp.core.JojoModLivingVariables;
import rotp.core.api.leap.LeapAccessPolicies;
import rotp.core.api.leap.LeapSource;
import rotp.core.api.stand.StandLeapUnlockProviders;
import rotp.core.api.stand.StandStaminaModifiers;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.command.commands.JojoControlsCommand;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.entityattachment.PostNbtReadEntityData;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.mechanics.resolve.ResolveAdvancements;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.mechanics.standarrow.StandArrowItem;
import rotp.core.network.s2c.SoulSpawnPacket;
import rotp.core.network.s2c.SkippedStandProgressionPacket;
import rotp.core.network.s2c.StandFullClearPacket;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.network.s2c.TrPowerStandInstancePacket;
import rotp.core.network.s2c.TrStandSkinPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.ProgressionSkipHandler;
import rotp.core.powersystem.ability.TrainableAbility;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandAwakening.AwakeningStage;
import rotp.core.powersystem.standpower.effect.UserStandEffects;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.packet.TrStandAbilityCooldownPacket;
import rotp.core.powersystem.standpower.packet.TrStandLeapCooldownPacket;
import rotp.core.powersystem.standpower.packet.TrStaminaPacket;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.powersystem.standpower.type.SummonedStand;
import rotp.core.subsystems.soul.SoulEntity;
import rotp.core.subsystems.time.RateStepper;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.MathUtil;
import rotp.core.util.functions.NBTUtil;
import rotp.core.util.objects_java.Lerp;
import rotp.core.util.sound.MultiSoundEventResolver;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandPower extends Power<StandPower> implements PostNbtReadEntityData {
	private static final double SOUL_DUPLICATE_CHECK_RADIUS = 128.0D;

	protected Optional<StandInstance> standInstance = Optional.empty();
	protected SummonedStand summonedStand;
	
	protected Lerp.FloatValue staminaLerp = new Lerp.FloatValue();
	protected float staminaAddNextTick = 0;
	private int leapCooldown;
	private final Map<String, Integer> abilityCooldowns = new HashMap<>();
	private final Map<String, Integer> abilityCooldownTotals = new HashMap<>();
	private final Map<String, Integer> abilityCooldownSets = new HashMap<>();
	private boolean willSoulSpawn;
	
	public ResolveCounter resolveCounter = new ResolveCounter();
	public UserStandEffects userStandEffects = new UserStandEffects(this);
	public StandAwakening userStandAwakeningState = new StandAwakening();
	public boolean healingDamageFromArrow = false;
	// 1.16 skippedProgression: saved as "Skipped", re-applied when a new non-Stand power is gained
	private boolean skippedProgression = false;
	@Nullable private TemporaryStandLoan temporaryStandLoan;

	public StandPower(LivingEntity user) {
		super(user);
		addPostNbtReadCallback(user); // to update the user's base attribute values after the attributes are read
	}
	
	
	@Override
	public void tick() {
		super.tick();
		// Frozen in stopped time: stamina, cooldowns, Resolve and the Stand's effects wait, as 1.16 skipped the user's tick.
		boolean paused = isPausedInStoppedTime();
		if (!paused) {
			tickStamina();
			int cooldownSteps = cooldownSteps();
			tickLeapCooldown(cooldownSteps);
			tickAbilityCooldowns(cooldownSteps);
			resyncScaledCooldowns();
			tickResolve();
			tickSoulCheck();
			userStandEffects.tick();
		}
		if (hasPower()) {
			standInstance.ifPresent(stand -> stand.syncIfDirty(user));
		}
		if (!user.level().isClientSide()) {
			tickWrongLevelStandEntity();
			if (!paused) {
				tickMissingStandPartEffects();
				if (healingDamageFromArrow && !StandArrowItem.healArrowDamage(user)) {
					healingDamageFromArrow = false;
				}
			}
			if (!canUsePower()) {
				StandType type = getPowerType();
				if (type != null) {
					type.forceUnsummon(user, this);
				}
			}
		}
		if (summonedStand != null) {
			summonedStand.tickStand(getUser(), this);
		}
	}

	private void tickWrongLevelStandEntity() {
		if (getPowerType() instanceof EntityStandType standType) {
			StandEntity standEntity = getSummonedStandEntity();
			if (standEntity != null && standEntity.level() != user.level()) {
				standType.forceUnsummon(user, this);
			}
		}
	}

	private void tickMissingStandPartEffects() {
		if (getPowerType() instanceof EntityStandType) {
			standInstance.ifPresent(standInstance -> {
				if (!standInstance.hasPart(StandPart.ARMS)) {
					user.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 319, 1));
					user.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 319, 1));
				}
				if (!standInstance.hasPart(StandPart.LEGS)) {
					user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 319, 1));
				}
			});
		}
	}
	
	
	public void setStand(@Nullable StandType stand) {
		setStandInstance(stand != null ? Optional.of(new StandInstance(stand)) : Optional.empty());
	}
	
	@ApiStatus.Internal
	public void setStandInstance(Optional<StandInstance> standInstance) {
		setStandInstance(standInstance, StandChange.ORDINARY, false);
	}

	private void setStandInstance(Optional<StandInstance> standInstance, StandChange change, boolean restoredSkipped) {
		Optional<StandInstance> newStandInstance = copyStandInstance(standInstance);
		boolean restoring = change == StandChange.RESTORE_LOAN;
		// a form of the borrowed Stand may equal the reserved one, and the restore still owes its progression and flag
		if (!restoring && !standInstanceChanged(this.standInstance, newStandInstance)) {
			return;
		}
		if (change == StandChange.ORDINARY && temporaryStandLoan != null) {
			temporaryStandLoan = followDirectChange(temporaryStandLoan, newStandInstance);
		}

		StandType oldStand = getPowerType();
		boolean standChanged = newStandInstance.map(newStand -> oldStand != newStand.getStandType())
				.orElseGet(() -> oldStand != null);
		if (oldStand != null) {
			oldStand.forceUnsummon(user, this);
		}
		
		this.standInstance = newStandInstance;
		StandType newStand = getPowerType();
		
		LivingEntity user = getUser();
		if (user != null) {
			StandStats.updateStandStatAttributes(this, user);
		}
		
		if (user != null && !user.level().isClientSide()) {
			userStandEffects.onStandChanged(user);
			if (oldStand == null && newStand != null) {
				// 1.16 onNewPowerGiven: no leftover leap cooldown, half stamina
				applyNewStandDefaults();
			}
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrPowerStandInstancePacket(user.getId(), this.standInstance));
			if (user instanceof ServerPlayer holder) {
				// server-wide taken count for standArrowMode
				StandUtil.trackTakenStand(holder, newStand);
			}
			if (!restoring && newStand != null && user instanceof ServerPlayer player) {
				if (standChanged) {
					JojoControlsCommand.sendPowerControlsHint(player);
				}
				ModCriteriaTriggers.triggerGetPower(player, this);
			}
		}

		if (newStand == null) {
			setStamina(0);
		}
		if (standChanged || restoring) {
			// 1.16 clear() dropped the flag with the Stand
			skippedProgression = restoring && restoredSkipped;
			cachedMovesThisTick = false;
		}
		if (restoring) {
			onSetPowerType(oldStand, newStand, false);
		}
		else {
			onSetPowerType(oldStand, newStand);
		}
		// 1.16 onNewPowerGiven: creative or the config skips at grant
		if (change == StandChange.ORDINARY && standChanged && newStand != null && user != null && !user.level().isClientSide()
				&& playerSkipsActionTraining(user)) {
			skipProgression();
		}
		else if (standChanged || restoring) {
			syncSkippedProgression();
		}
	}

	private enum StandChange {
		ORDINARY, BEGIN_LOAN, RESTORE_LOAN
	}

	/**
	 * Whatever changes the Stand directly during a loan changes the borrowed one: another Stand is a form of it, none means
	 * it is lost. The reservation stays whole and the restore still finds its way back. A form it leaves gets its own
	 * Resolve level back now, as the last one does at the restore.
	 */
	private TemporaryStandLoan followDirectChange(TemporaryStandLoan loan, Optional<StandInstance> replacement) {
		if (replacement.isEmpty()) {
			return loan.withBorrowedLost(true);
		}
		ResourceLocation form = replacement.orElseThrow().getStandId();
		if (form.equals(loan.borrowedId())) {
			return loan.withBorrowedLost(false);
		}
		restoreBorrowedResolveLevel(loan);
		return loan.withBorrowedForm(form, storedResolveLevel(form));
	}

	private int storedResolveLevel(ResourceLocation standId) {
		var data = powerData.get(standId);
		return data == null ? 0 : data.map(stored -> ((StandTypePersistentData) stored).getResolveLevel(),
				nbt -> nbt.getInt("ResolveLevel"));
	}

	@ApiStatus.Internal
	@Nullable
	public UUID temporaryLoanId() {
		return temporaryStandLoan != null ? temporaryStandLoan.token() : null;
	}

	@ApiStatus.Internal
	@Nullable
	public ResourceLocation reservedStandDuringLoan() {
		return temporaryStandLoan != null ? temporaryStandLoan.originalStand().getStandId() : null;
	}

	@ApiStatus.Internal
	@Nullable
	public StandInstance originalStandOfLoan(UUID token) {
		return temporaryStandLoan != null && temporaryStandLoan.token().equals(token)
				? temporaryStandLoan.copyOriginal() : null;
	}

	@ApiStatus.Internal
	public boolean canRestoreTemporaryLoan(UUID token) {
		TemporaryStandLoan loan = temporaryStandLoan;
		return loan != null && loan.token().equals(token)
				&& standInstance.map(stand -> loan.borrowedId().equals(stand.getStandId()))
						.orElse(loan.borrowedLostOnClone());
	}

	/** Commits a preflighted temporary grant. The caller owns the loaned disc. */
	@ApiStatus.Internal
	public void beginTemporaryLoan(UUID token, StandInstance borrowed) {
		requireLoanServerThread();
		Objects.requireNonNull(token, "token");
		StandInstance original = standInstance.orElseThrow();
		StandTypePersistentData originalProgression = getCurTypeData();
		if (temporaryStandLoan != null || originalProgression == null || !borrowed.standExists()
				|| original.getStandId().equals(borrowed.getStandId())) {
			throw new IllegalStateException("Invalid preflighted temporary Stand grant");
		}
		temporaryStandLoan = new TemporaryStandLoan(token, original.copy(),
				originalProgression.serializeNBT(user.registryAccess()).copy(), skippedProgression,
				borrowed.getStandId(), 0, false);
		setStandInstance(Optional.of(borrowed), StandChange.BEGIN_LOAN, false);
		StandTypePersistentData borrowedProgression = getCurTypeData();
		if (borrowedProgression != null) {
			temporaryStandLoan = temporaryStandLoan.withBorrowedResolveLevel(borrowedProgression.getResolveLevel());
		}
		resetTemporaryLoanTransientState();
	}

	/** Commits a preflighted restoration without granting or training the original Stand again. */
	@ApiStatus.Internal
	public void restoreTemporaryLoan(UUID token) {
		requireLoanServerThread();
		StandInstance original = originalStandOfLoan(token);
		if (!canRestoreTemporaryLoan(token) || original == null || !original.standExists()) {
			throw new IllegalStateException("Invalid preflighted temporary Stand restore");
		}
		endTemporaryLoan(temporaryStandLoan, original);
	}

	/** Ends the loan now with no token and no veto. The reserved Stand comes back, as a load keeps one whose add-on is gone. */
	@ApiStatus.Internal
	public void abortTemporaryLoan() {
		requireLoanServerThread();
		TemporaryStandLoan loan = temporaryStandLoan;
		if (loan != null) {
			endTemporaryLoan(loan, loan.copyOriginal());
		}
	}

	/** Whether these parts are the reserved Stand's: they go back to it, as they would have with no loan. */
	@ApiStatus.Internal
	public boolean returnPartsToReservedStand(StandInstance parts) {
		if (temporaryStandLoan == null || !temporaryStandLoan.originalStand().getStandId().equals(parts.getStandId())) {
			return false;
		}
		parts.getAllParts().forEach(temporaryStandLoan.originalStand()::addPart);
		return true;
	}

	private void endTemporaryLoan(TemporaryStandLoan loan, StandInstance original) {
		restoreBorrowedResolveLevel(loan);
		powerData.put(original.getStandId(), Either.right(loan.originalProgression().copy()));
		setStandInstance(Optional.of(original), StandChange.RESTORE_LOAN, loan.originalSkipped());
		resetTemporaryLoanTransientState();
		temporaryStandLoan = null;
	}

	private void resetTemporaryLoanTransientState() {
		resolveCounter.resetResolveValue(this);
		setLeapCooldown(0);
		staminaAddNextTick = 0;
		setStamina(0.0F);
	}

	private void restoreBorrowedResolveLevel(TemporaryStandLoan loan) {
		var data = powerData.get(loan.borrowedId());
		if (data != null) {
			data.ifLeft(borrowed -> ((StandTypePersistentData) borrowed).setResolveLevel(this, loan.borrowedResolveLevel()))
					.ifRight(nbt -> {
						// If the borrowed type is unavailable, its per-type data has not been decoded.
						CompoundTag restored = nbt.copy();
						restored.putInt("ResolveLevel", loan.borrowedResolveLevel());
						powerData.put(loan.borrowedId(), Either.right(restored));
					});
		}
	}

	private void requireLoanServerThread() {
		if (!(user instanceof ServerPlayer) || !(user.level() instanceof ServerLevel level)
				|| !level.getServer().isSameThread()) {
			throw new IllegalStateException("Temporary Stand changes require a server player on the server thread");
		}
	}

	static boolean standInstanceChanged(Optional<StandInstance> current, Optional<StandInstance> replacement) {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(replacement, "replacement");
		if (!current.equals(replacement)) {
			return true;
		}
		if (current.isEmpty()) {
			return false;
		}

		StandInstance currentStand = current.orElseThrow();
		StandInstance replacementStand = replacement.orElseThrow();
		return currentStand.standExists() != replacementStand.standExists()
				|| currentStand.getStandType() != replacementStand.getStandType();
	}

	static Optional<StandInstance> copyStandInstance(Optional<StandInstance> standInstance) {
		return Objects.requireNonNull(standInstance, "standInstance").map(StandInstance::copy);
	}

	@Override
	public StandType getPowerType() {
		return standInstance.map(StandInstance::getStandType).orElse(null);
	}
	
	@Override
	public boolean hasPower() {
		return standInstance.filter(StandInstance::standExists).isPresent();
	}
	
	public Optional<StandInstance> getStandInstance() {
		return standInstance;
	}

	/**
	 * Commits a preflighted destructive Stand transition.
	 */
	@ApiStatus.Internal
	public void applyDestructiveTransition(boolean fullReset) {
		setStandInstance(Optional.empty());
		staminaAddNextTick = 0;
		resetAbilityCooldowns();
		setLeapCooldown(0);
		resolveCounter.resetResolveValue(this);
		willSoulSpawn = false;
		healingDamageFromArrow = false;

		if (user instanceof ServerPlayer player) {
			PacketDistributor.sendToPlayer(
					player, SoulSpawnPacket.spawnFlag(false));
		}
		if (fullReset) {
			clearFullStandProgressionState();
			// 1.16 fullStandClear: standArrowHandler.clear() (item, /stand remove, API)
			if (!user.level().isClientSide()) {
				rotp.core.mechanics.standarrow.StandVirusActualEffect.resetStandsGotFromArrow(user);
				userStandAwakeningState.syncStageToTracking(user);
			}
			if (user instanceof ServerPlayer player) {
				PacketDistributor.sendToPlayer(
						player, new StandFullClearPacket());
			}
		}
	}

	@ApiStatus.Internal
	public void clientApplyFullStandClear() {
		if (user.level().isClientSide()) {
			clearFullStandProgressionState();
		}
	}

	private void clearFullStandProgressionState() {
		powerData.clear();
		moveset = initMoveset(getPowerType());
		_curAvailableMoves = new AvailableAbilities();
		cachedMovesThisTick = false;
		userStandAwakeningState = new StandAwakening();
	}

	@Override
	public PowerClass<StandPower> getPowerClass() {
		return PowerClass.STAND;
	}
	
	
	public SummonedStand getSummonedStand() {
		return summonedStand;
	}
	
	@Nullable
	public StandEntity getSummonedStandEntity() {
		return summonedStand != null ? summonedStand.getStandEntity() : null;
	}
	
	public boolean isSummoned() {
		return summonedStand != null;
	}

	private boolean unsummonedByItsUser;

	/** Server: the unsummon under way is its user's own (StandType#unsummon), not one a skill or a rule forces. */
	@ApiStatus.Internal
	public boolean isUnsummonedByItsUser() {
		return unsummonedByItsUser;
	}

	@ApiStatus.Internal
	public void setUnsummonedByItsUser(boolean byUser) {
		this.unsummonedByItsUser = byUser;
	}
	
	public void setSummonedStand(@Nullable SummonedStand summonedStand) {
		this.summonedStand = summonedStand;
		if (summonedStand != null) {
			summonedStand.setUserAndPower(getUser(), this);
			summonedStand.setSelectedSkin(standInstance.flatMap(StandInstance::getSelectedSkin));
		}
	}
	
	
	@Nullable
	@Override
	public StandTypePersistentData getCurTypeData() {
		// who needs generics, amirite
		return (StandTypePersistentData) super.getCurTypeData();
	}
	
	public static int addExp(LivingEntity user, float exp) {
		if (user != null && !user.level().isClientSide()) {
			StandPower stand = StandPower.get(user);
			return stand != null ? stand.addExp(exp) : null;
		}
		return 0;
	}
	
	public int addExp(float exp) {
		StandTypePersistentData data = getCurTypeData();
		return data != null ? data.gainExp(exp, user) : 0;
	}
	
	public void skipProgression() {
		StandTypePersistentData data = getCurTypeData();
		if (data != null) {
			skippedProgression = true;
			// 1.16 skipProgression set the level on the ResolveCounter directly, so no STAND_MAX
			setResolveLevel(getMaxResolveLevel(), false);
			if (!user.level().isClientSide()) {
				for (String skillName : data.getAllSkills().keySet()) {
					data._setSkillUnlocked(skillName, true, false);
				}
				Set<String> trainedAbilities = new HashSet<>();
				for (Ability ability : getMoveset().abilities.values()) {
					if (ability instanceof TrainableAbility trainable) {
						String learningAbilityName = trainable.getLearningAbilityName();
						if (learningAbilityName != null && trainedAbilities.add(learningAbilityName)
								&& data.getAbilityLearningProgressPoints(learningAbilityName) >= 0.0F) {
							float maxTraining = trainable.getMaxTrainingPoints(this);
							data.setAbilityLearningProgressPoints(learningAbilityName, maxTraining, maxTraining, this);
						}
					}
					if (ability instanceof ProgressionSkipHandler progressionSkipHandler) {
						progressionSkipHandler.onProgressionSkipped(this);
					}
				}
				data.syncOnUpdate(user);
			}
			cachedMovesThisTick = false;
			syncSkippedProgression();
		}
	}

	public boolean wasProgressionSkipped() {
		return skippedProgression;
	}

	/**
	 * The stand_skills reset command (the port's; 1.16 had none): undoes unlock_all (skipProgression) and what earlier
	 * Resolve levels unlocked, so the Stand's skills and Resolve gates follow its current level again; its training and
	 * EXP stay. 1.16's full reset is /stand clear (applyDestructiveTransition(true)).
	 */
	public void resetSkillProgression() {
		StandTypePersistentData data = getCurTypeData();
		if (data == null || user.level().isClientSide()) {
			return;
		}
		if (skippedProgression) {
			skippedProgression = false;
			syncSkippedProgression();
		}
		data.resetHighestResolveLevel(this);
		data.resetUnlockedSkills(this);
		cachedMovesThisTick = false;
	}

	@ApiStatus.Internal
	public void applySyncedProgressionSkipped(boolean skipped) {
		skippedProgression = skipped;
		cachedMovesThisTick = false;
		updateAvailableMoves();
	}

	private void syncSkippedProgression() {
		serverPlayerUser.ifPresent(player -> PacketDistributor.sendToPlayer(
				player, new SkippedStandProgressionPacket(skippedProgression)));
	}

	/** 1.16 StandPower.playerSkipsActionTraining: creative players or the skipStandProgression config. */
	public static boolean playerSkipsActionTraining(@Nullable LivingEntity user) {
		return user != null && (user instanceof Player player && player.getAbilities().instabuild
				|| JojoModConfig.getCommonConfigInstance(user.level().isClientSide()).skipStandProgression.get());
	}

	/**
	 * 1.16 TypeSpecificData.onPowerGiven: a newly gained non-Stand power re-runs a skipped progression,
	 * so power-type limits such as the Time Stop duration (9 s for vampires) rise to the new maximum.
	 */
	public static void reapplySkippedProgression(@Nullable LivingEntity user) {
		if (user == null || user.level().isClientSide() || !playerSkipsActionTraining(user)) {
			return;
		}
		StandPower stand = get(user);
		if (stand != null && stand.hasPower() && stand.wasProgressionSkipped()) {
			stand.skipProgression();
		}
	}


	public boolean usesStamina() {
		return hasPower() ? getPowerType().usesStamina(this) : false;
	}
	
	public float getStamina() {
		if (isStaminaInfinite()) {
			return getMaxStamina();
		}
		return staminaLerp.get();
	}
	
	public float getMaxStamina() {
		// add-on factors on the final value (1.16 mixins at RETURN)
		return hasPower() ? StandStaminaModifiers.applyToMaxStamina(this,
				getPowerType().getMaxStamina(this) * getPlayerPowerStandMaxStaminaFactor()) : 0;
	}
	
	public float getStaminaRatio() {
		float maxStamina = getMaxStamina();
		float stamina = getStamina();
		return MathUtil.ratioSafe(stamina, maxStamina);
	}
	
	public float getStaminaRatio(float partialTick) {
		if (isStaminaInfinite()) {
			return 1;
		}
		float maxStamina = getMaxStamina();
		float stamina = staminaLerp.lerp(partialTick);
		return stamina == maxStamina ? 1 : maxStamina > 0 ? stamina / maxStamina : 0;
	}
	
	/**
	 * Sets the stamina to exactly this amount: never scaled by the Stand's stamina share of the Stands page, so it is the
	 * call that puts the stamina back as it was (a refund, a rollback). A cost goes through {@link #consumeStamina}.
	 */
	public void setStamina(float stamina) {
		boolean clientSide = user.level().isClientSide();
		if (!clientSide) {
			stamina = Mth.clamp(stamina, 0, getMaxStamina());
		}
		if (this.staminaLerp.set(stamina, true)) {
			if (!clientSide) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrStaminaPacket(user.getId(), stamina));
			}
		}
	}
	
	public boolean consumeStamina(float amount) {
		return consumeStamina(amount, false);
	}
	
	/** Every stamina cost of the Stand, the core's and an add-on's, comes through here, in the Stand's stamina share (StandTuning). */
	public boolean consumeStamina(float amount, boolean ticking) {
		if (isStaminaInfinite()) {
			return true;
		}
		amount = getStaminaCostShare(amount);
		float curAmount = getStamina();
		if (curAmount >= amount) {
			if (ticking) {
				staminaAddNextTick -= amount;
			}
			else {
				setStamina(curAmount - amount);
			}
			return true;
		}
		else {
			setStamina(0);
			return ResolveModeEffect.getResolveEffectLvl(getUser()) >= 0;
		}
	}

	/** What {@link #consumeStamina} takes for a cost of this amount, for code that weighs the cost against the bar first. */
	public float getStaminaCostShare(float amount) {
		StandTuning tuning = StandTuning.of(this);
		return tuning != null ? tuning.staminaCost(amount) : amount;
	}

	public boolean isStaminaInfinite() {
		return user == null || isUserCreative()
				|| !JojoModConfig.getCommonConfigInstance(user.level().isClientSide()).standStamina.get();
	}
	
	protected void tickStamina() {
		if (this.usesStamina()) {
			float paidTicking = staminaAddNextTick;
			float staminaRegen = getStaminaTickGain() + paidTicking;
			staminaAddNextTick = 0;
			float stamina = Mth.clamp(staminaLerp.get() + staminaRegen, 0, getMaxStamina());
			// clients follow regeneration on their own, but most ticking payments are booked on the server only
			if (staminaLerp.set(stamina, true) && paidTicking != 0 && !user.level().isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrStaminaPacket(user.getId(), stamina));
			}
		}
	}

	public float getStaminaTickGain() {
		if (!hasPower()) {
			return 0.0F;
		}
		float staminaRegen = getPowerType().getStaminaRegenBeforeExternalModifiers(this);
		LivingEntity user = getUser();
		if (user != null) {
			JojoModLivingVariables vars = JojoModLivingVariables.get(user);
			if (vars.isDyingBody() && vars.getDyingBodyTicksLeft() == 0) {
				staminaRegen -= 3.5F;
			}
		}
		if (staminaRegen > 0.0F) {
			staminaRegen *= getPlayerPowerStandStaminaRegenFactor();
			if (user instanceof Player player && player.getFoodData().getFoodLevel() > 17) {
				staminaRegen *= 1.25F;
			}
			if (isUserInStoppedTime()) {
				staminaRegen *= PlayerClientBroadcastedSettings.getTimeStopStaminaRegenMultiplier(this);
			}
		}
		return StandStaminaModifiers.applyToTickGain(this,
				staminaRegen * getPowerType().getStaminaDurabilityMultiplier(this));
	}

	private boolean isUserInStoppedTime() {
		LivingEntity user = getUser();
		if (user != null && user.level() instanceof ServerLevel serverLevel
				&& serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
			return serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get()).isTimeStopped(user);
		}
		return false;
	}

	private float getPlayerPowerStandMaxStaminaFactor() {
		PlayerPower playerPower = PlayerPower.get(getUser());
		return playerPower != null && playerPower.hasPower()
				? playerPower.getPowerType().getStandMaxStaminaFactor(playerPower, this)
				: 1.0F;
	}

	private float getPlayerPowerStandStaminaRegenFactor() {
		PlayerPower playerPower = PlayerPower.get(getUser());
		return playerPower != null && playerPower.hasPower()
				? playerPower.getPowerType().getStandStaminaRegenFactor(playerPower, this)
				: 1.0F;
	}

	@Nullable private RateStepper cooldownClock;
	private boolean cooldownsScaled;

	// the cooldown ticks of this tick: 1, or by the user's EntityTimeRates STAND_ACTIONS rate
	private int cooldownSteps() {
		if (!EntityTimeRates.hasProviders()) {
			cooldownClock = null;
			return 1;
		}
		float rate = EntityTimeRates.rate(EntityTimeRates.Channel.STAND_ACTIONS, user);
		if (rate == 1.0F && cooldownClock == null) {
			return 1;
		}
		if (cooldownClock == null) {
			cooldownClock = new RateStepper();
		}
		return cooldownClock.steps(rate);
	}

	// the user's client counts its cooldowns down by itself: while they run at another rate, and once when they are back
	// to 1, the server sends what is left, so its HUD keeps up even where only the server knows the rate
	private void resyncScaledCooldowns() {
		boolean scaled = cooldownClock != null && cooldownClock.rate() != 1.0F;
		if (user.level().isClientSide() || serverPlayerUser.isEmpty() || !scaled && !cooldownsScaled) {
			return;
		}
		if (scaled != cooldownsScaled || user.tickCount % 10 == 0) {
			for (Map.Entry<String, Integer> entry : abilityCooldowns.entrySet()) {
				syncAbilityCooldown(entry.getKey(), entry.getValue(),
						abilityCooldownTotals.getOrDefault(entry.getKey(), entry.getValue()));
			}
			serverPlayerUser.ifPresent(player -> PacketDistributor.sendToPlayer(player, new TrStandLeapCooldownPacket(user.getId(), leapCooldown)));
		}
		cooldownsScaled = scaled;
	}

	private void tickLeapCooldown(int steps) {
		if (leapCooldown > 0) {
			leapCooldown = Math.max(leapCooldown - steps, 0);
		}
	}

	private void tickAbilityCooldowns(int steps) {
		if (steps == 0) {
			return;
		}
		for (Iterator<Map.Entry<String, Integer>> iterator = abilityCooldowns.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<String, Integer> entry = iterator.next();
			int cooldown = entry.getValue() - steps;
			if (cooldown > 0) {
				entry.setValue(cooldown);
			}
			else {
				abilityCooldownTotals.remove(entry.getKey());
				iterator.remove();
			}
		}
	}

	public int getAbilityCooldown(String abilityName) {
		return abilityCooldowns.getOrDefault(abilityName, 0);
	}

	@ApiStatus.Internal
	public int getAbilityCooldownTotal(String abilityName) {
		int cooldown = getAbilityCooldown(abilityName);
		return abilityCooldownTotals.getOrDefault(abilityName, cooldown);
	}

	public boolean isAbilityOnCooldown(String abilityName) {
		return getAbilityCooldown(abilityName) > 0;
	}

	/**
	 * How many times the ability's cooldown has been set or cleared in this power: the held cooldown recheck
	 * (EntityActionAbility#checkHeldActionConditions) tells a cooldown set during a hold from one it already saw.
	 */
	@ApiStatus.Internal
	public int getAbilityCooldownSets(String abilityName) {
		return abilityCooldownSets.getOrDefault(abilityName, 0);
	}

	public float getAbilityCooldownRatio(String abilityName, float partialTick) {
		int cooldown = getAbilityCooldown(abilityName);
		if (cooldown <= 0) {
			return 0.0F;
		}
		int totalCooldown = abilityCooldownTotals.getOrDefault(abilityName, cooldown);
		if (totalCooldown <= 0) {
			return 0.0F;
		}
		RateStepper clock = cooldownClock;
		float progress = clock != null ? clock.partialSteps(partialTick) : partialTick;
		return Mth.clamp(((float) cooldown - progress) / (float) totalCooldown, 0.0F, 1.0F);
	}

	public void setAbilityCooldown(String abilityName, int cooldown) {
		setAbilityCooldown(abilityName, cooldown, cooldown);
	}

	/** What {@link #setAbilityCooldown} sets for a cooldown of this length, for code that adds it to one it read back. */
	public int getAbilityCooldownShare(int cooldown) {
		StandTuning tuning = StandTuning.of(this);
		return tuning != null ? tuning.cooldown(cooldown) : cooldown;
	}

	/**
	 * A cooldown the ability's own code sets, in the Stand's cooldown share (StandTuning): every setter of a Stand cooldown
	 * comes through here, the core's and an add-on's. A cooldown put back as it was uses {@link #restoreAbilityCooldown}.
	 */
	public void setAbilityCooldown(String abilityName, int cooldown, int totalCooldown) {
		StandTuning tuning = StandTuning.of(this);
		if (tuning != null) {
			cooldown = tuning.cooldown(cooldown);
			totalCooldown = tuning.cooldown(totalCooldown);
		}
		restoreAbilityCooldown(abilityName, cooldown, totalCooldown);
	}

	/** {@link #restoreAbilityCooldown(String, int, int)} with the cooldown as its own total. */
	public void restoreAbilityCooldown(String abilityName, int cooldown) {
		restoreAbilityCooldown(abilityName, cooldown, cooldown);
	}

	/**
	 * Puts a cooldown back as it was, exactly these ticks, not scaled by the Stand's cooldown share of the Stands page
	 * ({@link #setAbilityCooldown} takes a cooldown in that share). For add-ons and the core alike: a cooldown restored
	 * as it was (a rollback, a refund, the server's word on a client), one worked out from a cooldown read back
	 * ({@link #getAbilityCooldown} is already in the share; add a fresh one with {@link #getAbilityCooldownShare}), or a
	 * lock of fixed length that is no cooldown of the Stand's own (a timer an ability or another Stand sets).
	 */
	public void restoreAbilityCooldown(String abilityName, int cooldown, int totalCooldown) {
		if (abilityName == null || abilityName.isEmpty()) {
			return;
		}
		int newCooldown = Math.max(cooldown, 0);
		int newTotalCooldown = Math.max(totalCooldown, newCooldown);
		abilityCooldownSets.merge(abilityName, 1, Integer::sum);
		if (newCooldown > 0) {
			abilityCooldowns.put(abilityName, newCooldown);
			abilityCooldownTotals.put(abilityName, newTotalCooldown);
		}
		else {
			abilityCooldowns.remove(abilityName);
			abilityCooldownTotals.remove(abilityName);
		}
		syncAbilityCooldown(abilityName, newCooldown, newTotalCooldown);
	}

	public void resetAbilityCooldowns() {
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		if (!user.level().isClientSide()) {
			serverPlayerUser.ifPresent(player -> PacketDistributor.sendToPlayer(player, TrStandAbilityCooldownPacket.resetAll(user.getId())));
		}
	}

	private void syncAbilityCooldown(String abilityName, int cooldown, int totalCooldown) {
		if (!user.level().isClientSide()) {
			serverPlayerUser.ifPresent(player -> PacketDistributor.sendToPlayer(player,
					new TrStandAbilityCooldownPacket(user.getId(), abilityName, cooldown, totalCooldown)));
		}
	}

	private void syncAbilityCooldownsTo(ServerPlayer player) {
		for (Map.Entry<String, Integer> entry : abilityCooldowns.entrySet()) {
			String abilityName = entry.getKey();
			int cooldown = entry.getValue();
			if (cooldown > 0) {
				PacketDistributor.sendToPlayer(player, new TrStandAbilityCooldownPacket(user.getId(), abilityName, cooldown,
						abilityCooldownTotals.getOrDefault(abilityName, cooldown)));
			}
		}
	}

	public boolean isLeapUnlocked() {
		return getStandInstance().map(stand -> stand.hasPart(StandPart.LEGS)).orElse(false) && leapStrength() >= 1.5F;
	}

	public float leapStrength() {
		StandEntity standEntity = getSummonedStandEntity();
		if (standEntity != null && !standEntity.isArmsOnlyMode() && standEntity.isFollowingUser()) {
			return standEntity.getLeapStrength();
		}
		return 0;
	}

	public boolean canLeap() {
		if (!hasPower() || leapCooldown != 0 || !isLeapUnlocked() || leapStrength() <= 0) {
			return false;
		}
		if (!(getPowerType() instanceof EntityStandType standType)
				|| !(standType.canLeap()
						|| StandLeapUnlockProviders.unlocks(
								user, this, standType))) {
			return false;
		}
		StandEntity standEntity = getSummonedStandEntity();
		return standEntity != null
				&& standEntity.getCurStandAction() == null
				&& LeapAccessPolicies.allowsExecution(
						user, LeapSource.STAND);
	}

	public int getLeapCooldownPeriod() {
		StandEntity standEntity = getSummonedStandEntity();
		if (standEntity != null && !standEntity.isArmsOnlyMode() && standEntity.isFollowingUser()) {
			double speed = standEntity.getAttributeValue(Attributes.MOVEMENT_SPEED);
			return StandStatFormulas.leapCooldown(speed);
		}
		return 0;
	}

	public void onLeap() {
		if (!isUserCreative()) {
			setLeapCooldown(getLeapCooldownPeriod());
		}
		consumeStamina(250);
		StandEntity standEntity = getSummonedStandEntity();
		if (standEntity != null && !user.level().isClientSide()) {
			float volume = standEntity.getLeapStrength() / 2.4F;
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(standEntity, new StandEntitySoundPacket(
					standEntity, MultiSoundEventResolver.resolve(ModSoundEvents.STAND_LEAP), volume, 1.0F));
		}
	}

	public int getLeapCooldown() {
		return leapCooldown;
	}

	/**
	 * Called by StandPowerTransitions' legacy replace (1.16 clear + give); applies all of
	 * {@link #applyNewStandDefaults()}, not only the leap cooldown.
	 */
	@ApiStatus.Internal
	public void resetLeapCooldownForNewStand() {
		applyNewStandDefaults();
	}

	/**
	 * 1.16 onNewPowerGiven on the server: setLeapCooldown(getLeapCooldownPeriod()), which is 0 with no Stand
	 * manifested, and setStamina(getMaxStamina() * 0.5F), also in Creative (max stamina is 0 without stamina use).
	 */
	@ApiStatus.Internal
	public void applyNewStandDefaults() {
		setLeapCooldown(getLeapCooldownPeriod());
		setStamina(usesStamina() ? getMaxStamina() * 0.5F : 0);
	}

	public void setLeapCooldown(int cooldown) {
		int newCooldown = Math.max(cooldown, 0);
		boolean changed = this.leapCooldown != newCooldown;
		this.leapCooldown = newCooldown;
		if (changed && !user.level().isClientSide() && user instanceof ServerPlayer player) {
			PacketDistributor.sendToPlayer(player, new TrStandLeapCooldownPacket(user.getId(), leapCooldown));
		}
	}
	
	
	public boolean usesResolve() {
		return hasPower() && getPowerType().usesResolve(this) && userStandAwakeningState.stage == AwakeningStage.FULL_CONTROL;
	}
	
	public ResolveCounter getResolveCounter() {
		return resolveCounter;
	}

	public int getResolveLevel() {
		if (!usesResolve()) {
			return 0;
		}
		StandTypePersistentData data = getCurTypeData();
		return data != null ? data.getResolveLevel() : 0;
	}

	/** The highest Resolve level this Stand has had (StandTypePersistentData#getHighestResolveLevel). */
	public int getHighestResolveLevel() {
		if (!usesResolve()) {
			return 0;
		}
		StandTypePersistentData data = getCurTypeData();
		return data != null ? data.getHighestResolveLevel() : 0;
	}

	public int getMaxResolveLevel() {
		if (!usesResolve()) {
			return 0;
		}
		StandType type = getPowerType();
		return type != null ? type.getMaxResolveLevel() : StandTypePersistentData.MAX_RESOLVE_LEVEL;
	}

	public float getStatsDevelopment() {
		int maxResolveLevel = getMaxResolveLevel();
		return usesResolve() && maxResolveLevel > 0 ? (float) getResolveLevel() / (float) maxResolveLevel : 0;
	}

	public void setResolveLevel(int level) {
		setResolveLevel(level, true);
	}

	private void setResolveLevel(int level, boolean fireStandMax) {
		if (!usesResolve()) {
			return;
		}
		StandTypePersistentData data = getCurTypeData();
		StandType type = getPowerType();
		if (data != null && type != null && data.setResolveLevel(this, level) && !user.level().isClientSide()) {
			StandStats.updateStandStatAttributes(this, user);
			type.onNewResolveLevel(this);
		}
		// 1.16 StandPower.setResolveLevel: STAND_MAX for a level at the maximum, changed or not
		if (fireStandMax && data != null && type != null) {
			ResolveAdvancements.onResolveLevelSet(this, level);
		}
	}
	
	protected void tickResolve() {
		resolveCounter.tick(this);
	}

	public boolean canSpawnSoulOnDeath() {
		if (user == null || user.level().isClientSide() || !(user.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!usesResolve()) {
			return false;
		}
		if (!JojoModConfig.getCommonConfigInstance(false).soulAscension.get()) {
			return false;
		}
		if (getResolveLevel() <= 0) {
			return false;
		}
		if (JojoDefinitions.isUndeadOrVampiric(user)) {
			return false;
		}
		return !(user instanceof Player) || !level.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
	}

	private void tickSoulCheck() {
		if (user != null && user.isAlive() && user instanceof ServerPlayer player) {
			boolean soulCanSpawn = canSpawnSoulOnDeath();
			if (this.willSoulSpawn != soulCanSpawn) {
				this.willSoulSpawn = soulCanSpawn;
				PacketDistributor.sendToPlayer(player, SoulSpawnPacket.spawnFlag(soulCanSpawn));
			}
		}
	}

	public boolean willSoulSpawn() {
		return willSoulSpawn;
	}

	public void clSetSoulSpawnFlag(boolean flag) {
		this.willSoulSpawn = flag;
	}

	public boolean spawnSoulOnDeath() {
		if (!canSpawnSoulOnDeath() || !(user.level() instanceof ServerLevel level)) {
			serverPlayerUser.ifPresent(player -> PacketDistributor.sendToPlayer(player, SoulSpawnPacket.noSoulSpawned()));
			return false;
		}
		if (soulAlreadyAscends(level)) {
			return true;
		}
		int ticks = getSoulLifespanTicks(level);
		boolean resolveCanLvlUp = level.getLevelData().isHardcore()
				|| !JojoModConfig.getCommonConfigInstance(false).keepStandOnDeath.get();
		Runnable spawnSoul = () -> {
			if (soulAlreadyAscends(level)) {
				return;
			}
			serverPlayerUser.ifPresent(ModCriteriaTriggers::triggerSoulAscension);
			SoulEntity soulEntity = new SoulEntity(level, user, ticks, resolveCanLvlUp);
			LivingEntity killer = user.getKillCredit();
			if (killer != null) {
				soulEntity.setNoResolveToEntity(StandUtil.getStandUser(killer));
			}
			level.addFreshEntity(soulEntity);
			if (user instanceof ServerPlayer serverPlayer) {
				serverPlayer.connection.send(new ClientboundSetCameraPacket(soulEntity));
			}
		};
		if (level.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
			TimeStopState timeStopState = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
			timeStopState.queueOnTimeResume(user, spawnSoul);
		}
		else {
			spawnSoul.run();
		}
		return true;
	}

	private int getSoulLifespanTicks(ServerLevel level) {
		float resolveRatio = ResolveModeEffect.getResolveEffectLvl(user) >= 0 ? 1.0F : resolveCounter.getResolveRatio(this);
		int ticks = (int) (60.0F * (getResolveLevel() + resolveRatio));
		if (level.getLevelData().isHardcore()) {
			ticks += ticks / 2;
		}
		return Math.max(ticks, 1);
	}

	private boolean hasActiveSoulEntity(ServerLevel level) {
		return !level.getEntitiesOfClass(SoulEntity.class, user.getBoundingBox().inflate(SOUL_DUPLICATE_CHECK_RADIUS),
				soul -> soul.isAlive() && user.equals(soul.getOriginEntity())).isEmpty();
	}

	/**
	 * A soul of the user still ascends, so no second one spawns; the client, which holds its death screen back for this
	 * death's soul, learns that none comes, as from a failed spawn.
	 */
	private boolean soulAlreadyAscends(ServerLevel level) {
		if (!hasActiveSoulEntity(level)) {
			return false;
		}
		// as the client now has it, so that tickSoulCheck sends the flag again once the user lives
		willSoulSpawn = false;
		serverPlayerUser.ifPresent(player -> PacketDistributor.sendToPlayer(player, SoulSpawnPacket.noSoulSpawned()));
		return true;
	}
	
	
	public void setSelectedSkin(Optional<ResourceLocation> skin) {
		if (standInstance.isPresent()) {
			standInstance.get().setCustomSkin(skin);
			if (!user.level().isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrStandSkinPacket(user.getId(), getSelectedSkin()));
			}
		}
		if (summonedStand != null) {
			summonedStand.setSelectedSkin(skin);
		}
	}
	
	public Optional<ResourceLocation> getSelectedSkin() {
		if (standInstance.isEmpty()) return Optional.empty();
		return standInstance.get().getSelectedSkin();
	}
	
	
	@Override
	public void afterConfigApply() {
		super.afterConfigApply();
		StandStats.updateStandStatAttributes(this, user);
	}

	@Override
	public void syncToPlayer(ServerPlayer user) {
		PacketDistributor.sendToPlayer(user, new TrPowerStandInstancePacket(user.getId(), standInstance));
		super.syncToPlayer(user);
		syncStaminaFixed(user, user);
		resolveCounter.syncToUser(user);
		PacketDistributor.sendToPlayer(user, new TrStandSkinPacket(user.getId(), getSelectedSkin()));
		userStandEffects.syncToPlayer(user);
		userStandAwakeningState.syncToUser(user);
		syncAbilityCooldownsTo(user);
		tickSoulCheck();
		PacketDistributor.sendToPlayer(user, SoulSpawnPacket.spawnFlag(willSoulSpawn));
		PacketDistributor.sendToPlayer(user, new SkippedStandProgressionPacket(skippedProgression));
	}

	@Override
	public void syncToTracking(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new TrPowerStandInstancePacket(user.getId(), standInstance));
		super.syncToTracking(player);
		syncStaminaFixed(player, user);
		resolveCounter.syncToTracking(user, player);
		PacketDistributor.sendToPlayer(player, new TrStandSkinPacket(user.getId(), getSelectedSkin()));
		userStandEffects.syncToTracking(player);
		userStandAwakeningState.syncStageTo(user, player);
	}
	
	protected void syncStaminaFixed(ServerPlayer player, LivingEntity user) {
		var durabilityAttribute = user.getAttribute(ModEntityAttributes.STAND_DURABILITY);
		if (durabilityAttribute != null) {
			player.connection.send(new ClientboundUpdateAttributesPacket(user.getId(), Collections.singletonList(durabilityAttribute)));
		}
		PacketDistributor.sendToPlayer(player, new TrStaminaPacket(user.getId(), staminaLerp.get()));
		PacketDistributor.sendToPlayer(player, new TrStandLeapCooldownPacket(user.getId(), leapCooldown));
	}
	
	@Override
	public void onPlayerClone(Player newPlayer, boolean wasDeath) {
		super.onPlayerClone(newPlayer, wasDeath);
		this.userStandEffects.onPlayerClone(newPlayer, wasDeath);
	}
	
	@Override
	protected void onPlayerCloneData(StandPower newEntityData, boolean wasDeath) {
		super.onPlayerCloneData(newEntityData, wasDeath);
		StandType standType = getPowerType();
		boolean losesStand = standType == null || wasDeath && !standType.keepOnDeath(this);
		newEntityData.temporaryStandLoan = temporaryStandLoan != null ? temporaryStandLoan.copyForClone(losesStand) : null;
		if (losesStand) {
			clearClonedStandData(newEntityData);
			return;
		}
		newEntityData.standInstance = copyStandInstance(this.standInstance);
		// own value, refilled after all powers are cloned (refillStaminaAfterClone)
		newEntityData.staminaLerp = new Lerp.FloatValue();
		newEntityData.leapCooldown = this.leapCooldown;
		newEntityData.abilityCooldowns.clear();
		newEntityData.abilityCooldowns.putAll(this.abilityCooldowns);
		newEntityData.abilityCooldownTotals.clear();
		newEntityData.abilityCooldownTotals.putAll(this.abilityCooldownTotals);
		newEntityData.resolveCounter.copyValues(this.resolveCounter, wasDeath);
		newEntityData.userStandEffects.transferFrom(this.userStandEffects, newEntityData);
		newEntityData.userStandAwakeningState = this.userStandAwakeningState;
		newEntityData.skippedProgression = this.skippedProgression;
	}

	/* 1.16 StandPower.keepPower: a kept Stand starts full after death or End return.
	 * Called after every power is cloned, as max stamina uses the PlayerPower factor.
	 */
	@ApiStatus.Internal
	public void refillStaminaAfterClone() {
		if (hasPower()) {
			staminaLerp.set(getMaxStamina(), false);
		}
	}

	private void clearClonedStandData(StandPower newEntityData) {
		newEntityData.standInstance = Optional.empty();
		newEntityData.summonedStand = null;
		newEntityData.moveset = null;
		newEntityData.powerData = new HashMap<>();
		newEntityData.staminaLerp = new Lerp.FloatValue();
		newEntityData.leapCooldown = 0;
		newEntityData.abilityCooldowns.clear();
		newEntityData.abilityCooldownTotals.clear();
		newEntityData.resolveCounter = new ResolveCounter();
		newEntityData.userStandEffects = new UserStandEffects(newEntityData);
		newEntityData.userStandAwakeningState = new StandAwakening();
		newEntityData.willSoulSpawn = false;
		newEntityData.healingDamageFromArrow = false;
		newEntityData.skippedProgression = false;
	}
	
	
	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = super.serializeNBT(provider);
		standInstance.ifPresent(
				stand -> StandInstance.CODEC.encodeStart(NbtOps.INSTANCE, stand)
				.ifSuccess(standNbt -> nbt.put("StandInstance", standNbt)));
		nbt.putFloat("Stamina", staminaLerp.get());
		nbt.putInt("LeapCd", leapCooldown);
		if (!abilityCooldowns.isEmpty()) {
			CompoundTag cooldowns = new CompoundTag();
			abilityCooldowns.forEach((abilityName, cooldown) -> cooldowns.putIntArray(abilityName,
					new int[] { cooldown, abilityCooldownTotals.getOrDefault(abilityName, cooldown) }));
			nbt.put("AbilityCooldowns", cooldowns);
		}
		nbt.put("Resolve", resolveCounter.writeNBT());
		nbt.put("Effects", userStandEffects.serializeNBT(provider));
		nbt.put("Awakening", userStandAwakeningState.serializeNBT());
		nbt.putBoolean("HealFromArrow", healingDamageFromArrow);
		nbt.putBoolean("Skipped", skippedProgression);
		if (temporaryStandLoan != null) {
			nbt.put("TemporaryStandLoan", temporaryStandLoan.save());
		}
		return nbt;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		super.deserializeNBT(provider, nbt);
		standInstance = NBTUtil.getCompoundOptional(nbt, "StandInstance")
				.flatMap(standNbt -> StandInstance.CODEC.decode(NbtOps.INSTANCE, standNbt).result())
				.map(pair -> pair.getFirst());
		staminaLerp.set(nbt.getFloat("Stamina"), false);
		leapCooldown = nbt.getInt("LeapCd");
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		NBTUtil.getCompoundOptional(nbt, "AbilityCooldowns").ifPresent(cooldowns -> {
			for (String abilityName : cooldowns.getAllKeys()) {
				int cooldown;
				int totalCooldown;
				if (cooldowns.contains(abilityName, Tag.TAG_INT_ARRAY)) {
					int[] values = cooldowns.getIntArray(abilityName);
					cooldown = values.length > 0 ? values[0] : 0;
					totalCooldown = values.length > 1 ? values[1] : cooldown;
				}
				else {
					cooldown = cooldowns.getInt(abilityName);
					totalCooldown = cooldown;
				}
				if (cooldown > 0) {
					abilityCooldowns.put(abilityName, cooldown);
					abilityCooldownTotals.put(abilityName, Math.max(totalCooldown, cooldown));
				}
			}
		});
		NBTUtil.getCompoundOptional(nbt, "Resolve").ifPresent(resolveCounter::readNBT);
		NBTUtil.getCompoundOptional(nbt, "Effects").ifPresent(effectsNbt -> userStandEffects.deserializeNBT(provider, effectsNbt));
		NBTUtil.getCompoundOptional(nbt, "Awakening").ifPresent(userStandAwakeningState::deserializeNBT);
		healingDamageFromArrow = nbt.getBoolean("HealFromArrow");
		skippedProgression = nbt.getBoolean("Skipped");
		temporaryStandLoan = NBTUtil.getCompoundOptional(nbt, "TemporaryStandLoan")
				.flatMap(TemporaryStandLoan::load).orElse(null);
	}
	
	/* unlike deserializeNBT, this is called after the entity attributes are read, 
	 * allowing me to edit their base values from the Stand stats
	 */
	@Override
	public void afterNbtRead() {
		StandStats.updateStandStatAttributes(this, user);
	}
	
	
	@Nullable
	public static StandPower get(LivingEntity entity) {
		return PowerClass.STAND.get(entity);
	}
	
	public static Optional<StandPower> getOptional(LivingEntity entity) {
		return PowerClass.STAND.getOptional(entity);
	}
	
	
	public final boolean isUserCreative() {
		LivingEntity user = getUser();
		return user instanceof Player player && player.getAbilities().instabuild;
	}

}
