package rotp.core.powersystem.standpower.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.core.client.ClientProxy;
import rotp.core.core.JojoMod;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModSpecialActions;
import rotp.core.network.s2c.TrSetStandEntityPacket;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.datapack.StandTypeClass;
import rotp.core.powersystem.standpower.type.StandActionCancel;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import rotp.core.util.objects_java.DefaultedValue;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.impl.stands._entitybase.StandEntityUnsummonAction;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

// TODO stand hitbox size parameter (+the size to stretch the model to)
public class EntityStandType extends StandType {
	static {
		StandTypeClass.registerStandClass(EntityStandType.class, "entity", EntityStandType::new);
	}
	
	protected DefaultedValue<EntityType<? extends StandEntity>> entityType;
	public EntityDimensions standDimensions = ModEntityTypes.HUMANOID_STAND.get().getDimensions();
	protected StandControlType standControlType;
	protected StandControlType standControlTypeDefault;
	protected boolean manualControlEnabled;
	protected boolean manualControlEnabledDefault;
	protected boolean manualControlConfigured;
	protected boolean manualControlConfiguredDefault;
	protected boolean standLeapEnabled = true;
	protected final DefaultedValue.Bool distanceStrengthDecayEnabled = new DefaultedValue.Bool(false);
	protected boolean distanceStrengthDecayConfigured;
	protected boolean distanceStrengthDecayConfiguredDefault;
	@Nullable
	private volatile String loggedStandControlPolicyProblem;
	
	public EntityStandType(StandStats stats, MovesetBuilder moveset, 
			ResourceLocation id) {
		this(stats, moveset, ModEntityTypes.HUMANOID_STAND.get(), id);
	}

	public EntityStandType(
			StandStats stats,
			MovesetBuilder moveset,
			ResourceLocation id,
			StandControlType standControlType,
			double effectiveRange,
			double rangeMax,
			boolean manualControl,
			boolean distanceStrengthDecay) {
		this(stats, moveset, ModEntityTypes.HUMANOID_STAND.get(), id,
				standControlType, effectiveRange, rangeMax,
				manualControl, distanceStrengthDecay);
	}
	
	public EntityStandType(StandStats stats, MovesetBuilder moveset, 
			EntityType<? extends StandEntity> standEntityType, 
			ResourceLocation id) {
		super(stats, moveset, id);
		Objects.requireNonNull(standEntityType);
		this.entityType = new DefaultedValue<>(standEntityType);
	}

	public EntityStandType(
			StandStats stats,
			MovesetBuilder moveset,
			EntityType<? extends StandEntity> standEntityType,
			ResourceLocation id,
			StandControlType standControlType,
			double effectiveRange,
			double rangeMax,
			boolean manualControl,
			boolean distanceStrengthDecay) {
		this(stats, moveset, standEntityType, id);
		configureStandControlPolicy(
				standControlType, effectiveRange, rangeMax,
				manualControl, distanceStrengthDecay);
	}

	public <T extends EntityStandType> T standDimensions(float width, float height) {
		return init(stand -> stand.standDimensions = EntityDimensions.scalable(width, height));
	}

	public <T extends EntityStandType> T standControlType(
			StandControlType standControlType) {
		return init(stand -> {
			stand.standControlTypeDefault = Objects.requireNonNull(
					standControlType, "standControlType");
			stand.standControlType = standControlType;
			stand.validateStandControlPolicyIfComplete();
		});
	}

	public <T extends EntityStandType> T manualControl(boolean enabled) {
		return init(stand -> {
			stand.manualControlEnabledDefault = enabled;
			stand.manualControlEnabled = enabled;
			stand.manualControlConfiguredDefault = true;
			stand.manualControlConfigured = true;
			stand.validateStandControlPolicyIfComplete();
		});
	}

	public <T extends EntityStandType> T distanceStrengthDecay(boolean enabled) {
		return init(stand -> {
			stand.distanceStrengthDecayEnabled.defaultValue = enabled;
			stand.distanceStrengthDecayEnabled.value = enabled;
			stand.distanceStrengthDecayConfiguredDefault = true;
			stand.distanceStrengthDecayConfigured = true;
			stand.validateStandControlPolicyIfComplete();
		});
	}

	private void configureStandControlPolicy(
			StandControlType standControlType,
			double effectiveRange,
			double rangeMax,
			boolean manualControl,
			boolean distanceStrengthDecay) {
		try {
			StandControlType.validate(
					standControlType, effectiveRange, rangeMax,
					manualControl, distanceStrengthDecay,
					supportsColonyCoordinatorManualControl());
		}
		catch (IllegalStateException e) {
			throw new IllegalStateException("Stand " + getId() + " (" + getClass().getName() + "): " + e.getMessage(), e);
		}
		if (Double.compare(stats.rangeEffective(), effectiveRange) != 0
				|| Double.compare(stats.rangeMax(), rangeMax) != 0) {
			throw new IllegalStateException(
					"Explicit Stand control ranges must match StandStats for "
							+ getId());
		}
		this.standControlTypeDefault = standControlType;
		this.standControlType = standControlType;
		this.manualControlEnabledDefault = manualControl;
		this.manualControlEnabled = manualControl;
		this.manualControlConfiguredDefault = true;
		this.manualControlConfigured = true;
		this.distanceStrengthDecayEnabled.defaultValue = distanceStrengthDecay;
		this.distanceStrengthDecayEnabled.value = distanceStrengthDecay;
		this.distanceStrengthDecayConfiguredDefault = true;
		this.distanceStrengthDecayConfigured = true;
	}

	private void validateStandControlPolicy() {
		String problem = standControlPolicyProblem();
		if (problem != null) {
			throw new IllegalStateException(problem);
		}
	}

	/**
	 * What this Stand's control policy lacks or breaks, naming the Stand and the fields; null when it is complete and
	 * valid. The core's common setup refuses to load with any registered Stand that has one.
	 */
	@Nullable
	@Override
	public String standControlPolicyProblem() {
		String problem;
		if (standControlType == null || !manualControlConfigured || !distanceStrengthDecayConfigured) {
			List<String> missing = new ArrayList<>(3);
			if (standControlType == null) {
				missing.add("standControlType");
			}
			if (!manualControlConfigured) {
				missing.add("manualControl");
			}
			if (!distanceStrengthDecayConfigured) {
				missing.add("distanceStrengthDecay");
			}
			problem = String.join(", ", missing) + (missing.size() == 1 ? " is" : " are") + " required";
		}
		else {
			problem = StandControlType.problem(
					standControlType,
					stats.rangeEffective(), stats.rangeMax(),
					manualControlEnabled,
					distanceStrengthDecayEnabled.value,
					supportsColonyCoordinatorManualControl());
		}
		return problem != null ? "Stand " + getId() + " (" + getClass().getName() + "): " + problem : null;
	}

	// Logged again only if the problem changes (a data pack or config changed the policy).
	@Override
	public boolean reportStandControlPolicyProblem() {
		String problem = standControlPolicyProblem();
		if (problem == null || problem.equals(loggedStandControlPolicyProblem)) {
			return false;
		}
		loggedStandControlPolicyProblem = problem;
		JojoMod.getLogger().error("{}; using standControlType {}, manualControl {}, distanceStrengthDecay {}",
				problem, effectiveStandControlType(), effectiveManualControl(), effectiveDistanceStrengthDecay());
		return true;
	}

	// At run time a broken policy is logged once and the core's defaults stand in for the missing fields.
	private void checkStandControlPolicyAtRunTime() {
		reportStandControlPolicyProblem();
	}

	// A Stand that names no type: as 1.16's StandEntity, manual control and strength decaying with distance, unless it
	// says otherwise.
	private StandControlType effectiveStandControlType() {
		if (standControlType != null) {
			return standControlType;
		}
		boolean manual = !manualControlConfigured || manualControlEnabled;
		boolean decay = distanceStrengthDecayConfigured ? distanceStrengthDecayEnabled.value : manual;
		return !manual ? StandControlType.AUTOMATIC
				: decay ? StandControlType.CLOSE_RANGE_DIRECT : StandControlType.LONG_DISTANCE_OPERATION;
	}

	private boolean effectiveManualControl() {
		return manualControlConfigured ? manualControlEnabled : effectiveStandControlType().defaultManualControl();
	}

	private boolean effectiveDistanceStrengthDecay() {
		return distanceStrengthDecayConfigured
				? distanceStrengthDecayEnabled.value
				: effectiveManualControl() && effectiveStandControlType().defaultDistanceStrengthDecay();
	}

	/** Dedicated colony coordinators must own their movement authorization and control cleanup. */
	protected boolean supportsColonyCoordinatorManualControl() {
		return false;
	}

	final void validateStandControlPolicyIfComplete() {
		if (standControlType != null
				&& manualControlConfigured
				&& distanceStrengthDecayConfigured) {
			validateStandControlPolicy();
		}
	}
	
	@Override
	public JsonObject makeConfigTemplate() {
		checkStandControlPolicyAtRunTime();
		JsonObject json = super.makeConfigTemplate();
		json.addProperty("entityType", EntityType.getKey(this.entityType.defaultValue).toString());
		json.addProperty("standControlType", effectiveStandControlType().name());
		json.addProperty("manualControlEnabled", effectiveManualControl());
		json.addProperty("standLeapEnabled", this.standLeapEnabled);
		json.addProperty("distanceStrengthDecayEnabled", effectiveDistanceStrengthDecay());
		return json;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void applyConfig(JsonElement json) {
		super.applyConfig(json);
		JsonObject config = json.getAsJsonObject();
		Optional.ofNullable(config.get("entityType"))
			.map(JsonElement::getAsString)
			.flatMap(id -> EntityType.byString(id))
			.ifPresent(entityType -> {
				this.entityType.value = (EntityType<? extends StandEntity>) entityType;
			});
		Optional.ofNullable(config.get("standControlType"))
				.map(JsonElement::getAsString)
				.map(StandControlType::valueOf)
				.ifPresent(value -> this.standControlType = value);
		Optional.ofNullable(config.get("manualControlEnabled"))
				.map(JsonElement::getAsBoolean)
				.ifPresent(value -> {
					this.manualControlEnabled = value;
					this.manualControlConfigured = true;
				});
		Optional.ofNullable(config.get("standLeapEnabled"))
				.map(JsonElement::getAsBoolean)
				.ifPresent(value -> this.standLeapEnabled = value);
		Optional.ofNullable(config.get("distanceStrengthDecayEnabled"))
				.map(JsonElement::getAsBoolean)
				.ifPresent(value -> {
					this.distanceStrengthDecayEnabled.value = value;
					this.distanceStrengthDecayConfigured = true;
				});
		checkStandControlPolicyAtRunTime();
	}

	@Override
	public void restoreDefaults() {
		super.restoreDefaults();
		entityType.reset();
		standControlType = standControlTypeDefault;
		manualControlEnabled = manualControlEnabledDefault;
		manualControlConfigured = manualControlConfiguredDefault;
		standLeapEnabled = true;
		distanceStrengthDecayEnabled.reset();
		distanceStrengthDecayConfigured =
				distanceStrengthDecayConfiguredDefault;
	}
	
	@Override
	public void onUserSummonCommand(LivingEntity user, StandPower standPower) {
		if (!standPower.isSummoned()) {
			if (onTrySummon(user, standPower)) {
				summon(user, standPower);
			}
			return;
		}
		
		StandEntity standEntity = standPower.getSummonedStandEntity();
		if (standEntity != null && standEntity.isArmsOnlyMode()) {
			standEntity.fullSummonFromArms();
			triggerFullSummonAdvancement(user, standEntity);
		}
		else {
			unsummon(user, standPower);
		}
	}

	@Override
	public void onUserCancelCommand(LivingEntity user, StandPower standPower) {
		StandEntity standEntity = standPower.getSummonedStandEntity();
		if (standEntity == null) {
			super.onUserCancelCommand(user, standPower);
			return;
		}
		// a time stop's windup and the like, in its user's own slot, come before the Stand's
		if (StandActionCancel.cancelWindup(LivingComponentAction.getExistingComponent(user, PowerClass.STAND), standPower)) {
			return;
		}
		LivingComponentAction standSlot = LivingComponentAction.getExistingComponent(standEntity);
		EntityActionInstance action = standSlot != null ? standSlot.getAction() : null;
		if (action == null || action.isOver()) {
			unsummon(user, standPower);
		}
		else if (action instanceof StandEntityUnsummonAction.StandUnsummonInstance) {
			action.actionPerformEnd();
		}
		else {
			StandActionCancel.cancelWindup(standSlot, standPower);
		}
	}

	@Override
	public boolean summon(LivingEntity user, StandPower standPower) {
		return summon(user, standPower, entity -> {}, true);
	}

	public boolean summon(LivingEntity user, StandPower standPower, Consumer<StandEntity> beforeTheSummon, boolean addToWorld) {
		if (!standPower.canUsePower()) {
			return false;
		}
//		if (!withoutNameVoiceLine && !user.isShiftKeyDown()) {
//			SoundEvent shout = summonShoutSupplier.get();
//			if (shout != null) {
//				JojoModUtil.sayVoiceLine(user, shout);
//			}
//		}
//		triggerAdvancement(standPower, standPower.getStandManifestation());
		
		Level level = user.level();
		if (!level.isClientSide()) {
			StandEntity standEntity = entityType.value.create(level/*, EntitySpawnReason.NATURAL*/)
					.withStandType(this);
			standEntity.refreshDimensions();
			standEntity.copyPosition(user);
			standEntity.copyStandUserRotation(user);
			standEntity.setCustomName(standPower.getName());
			standPower.setSummonedStand(standEntity);
			standEntity.prepareFreshSummonState(user);
			standEntity.placeForFreshSummon(user);
			beforeTheSummon.accept(standEntity);
			
			if (addToWorld) {
				playSummonShout(user);
				finalizeStandSummonFromAction(user, standPower, standEntity, true);
			}
			
//			standEntity.onStandSummonServerSide();
		}
		return true;
	}
	
	public void finalizeStandSummonFromAction(LivingEntity user, StandPower standPower, StandEntity standEntity, boolean addToWorld) {
		Level level = user.level();
		if (!level.isClientSide() && !standEntity.isAddedToLevel()) {
			if (addToWorld) {
				standEntity.placeForSummonUnlessPositioned(user);
				level.addFreshEntity(standEntity);
				standEntity.playStandSummonSound();
				// action sounds played before the auto-summoned Stand was added (1.16 phase standSound)
				StandUtil.flushPendingStandEntitySounds(standEntity);
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrSetStandEntityPacket(user.getId(), standEntity.getId()));
				triggerFullSummonAdvancement(user, standEntity);
			}
			else {
				forceUnsummon(user, standPower);
				StandUtil.flushPendingStandEntitySounds(standEntity);
			}
		}
	}
	
	public void triggerFullSummonAdvancement(LivingEntity user, StandEntity standEntity) {
		if (user instanceof ServerPlayer player && standEntity != null && !standEntity.isArmsOnlyMode()) {
			ModCriteriaTriggers.triggerSummonStand(player);
		}
	}

	@Override
	public void unsummon(LivingEntity user, StandPower standPower) {
		if (!user.level().isClientSide()) {
			StandEntity standEntity = ((StandEntity) standPower.getSummonedStand());
			if (standEntity != null) {
				standEntity.onUnsummonUserInput();
			}
		}
	}

	@Override
	public void forceUnsummon(LivingEntity user, StandPower standPower) {
		if (!user.level().isClientSide()) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null) {
				if (keepsUsersStandHud(user, standPower, standEntity)) {
					keepUsersStandHud(user);
				}
				clearStandActionBeforeRemoval(standEntity);
				settleStandBeforeForcedUnsummon(standEntity);
				standPower.setSummonedStand(null);
				standEntity.remove(Entity.RemovalReason.DISCARDED);
			}
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrSetStandEntityPacket(user.getId(), 0));
		}
		else if (user.is(ClientProxy.getClientPlayer())) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null) {
				settleStandBeforeForcedUnsummon(standEntity);
			}
		}
	}

	/*
	 * Every unsummon of a whole Stand in its user's level but the one its unsummon action ends with (the summon key, the
	 * retraction after an auto-summon), as 1.16's StandEntityUnsummon.onTaskSet alone closed the HUD; a Stand that a press
	 * made and never added was never shown, and the arms an action took out leave the HUD as it was before them.
	 */
	private static boolean keepsUsersStandHud(LivingEntity user, StandPower standPower, StandEntity standEntity) {
		EntityActionInstance action = standEntity.getCurStandAction();
		return standEntity.isAddedToLevel() && !standEntity.isArmsOnlyMode() && standEntity.level() == user.level()
				&& !standPower.isUnsummonedByItsUser()
				&& (action == null || action.ability != ModSpecialActions.STAND_UNSUMMON.get());
	}

	/**
	 * 1.16 ended the held action of a Stand that was taken away (a portal, another dimension, the power lost) through
	 * stopHeldAction, so it still ended as actions end: its onActionCleared runs, where a held action sets its cooldown.
	 * A Stand made for a press that did not start was never in the level and has nothing to end.
	 */
	private static void clearStandActionBeforeRemoval(StandEntity standEntity) {
		LivingComponentAction actions = LivingComponentAction.getExistingComponent(standEntity);
		if (standEntity.isAddedToLevel() && actions != null && actions.getAction() != null) {
			actions.setAction(null, SyncType.TRACKING_AND_SELF);
		}
	}

	public static void settleStandBeforeForcedUnsummon(StandEntity standEntity) {
		LivingComponentPossession.setPossessionTarget(standEntity, null, null);
		if (standEntity.getStandFlag(StandEntity.StandFlag.MANUAL_CONTROL)) {
			StandEntityManualControlToggle.off(
					standEntity.level(), standEntity, false);
		}
		standEntity.setCanFollowUser(false);
		standEntity.setManuallyControlled(false);
		standEntity.setCanFollowUser(true);
		if (!standEntity.level().isClientSide()) {
			standEntity.stopRetraction();
		}
		standEntity.setNoPhysics(false);
	}

	@Override
	public boolean canBeManuallyControlled() {
		checkStandControlPolicyAtRunTime();
		return effectiveManualControl();
	}

	@Override
	public StandControlType getStandControlType() {
		checkStandControlPolicyAtRunTime();
		return effectiveStandControlType();
	}

	public boolean canLeap() {
		return standLeapEnabled;
	}

	/**
	 * Client: whether the view of this type's Stand inside a view-blocking block is covered with that block's in-wall
	 * texture (StandEntity#coversCameraInsideBlocks, which an entity class can override instead).
	 */
	public boolean coversCameraInsideBlocks(StandEntity standEntity) {
		return true;
	}

	@Override
	public boolean usesDistanceStrengthDecay() {
		checkStandControlPolicyAtRunTime();
		return effectiveDistanceStrengthDecay();
	}
	
	public EntityType<?> getEntityType() {
		return entityType.value;
	}
	
}
