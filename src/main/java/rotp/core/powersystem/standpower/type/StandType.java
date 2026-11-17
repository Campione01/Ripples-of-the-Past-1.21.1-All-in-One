package rotp.core.powersystem.standpower.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.JojoModConfig;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsScreen;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.SimpleTagKey;
import rotp.core.network.s2c.StandSkinSoundPacket;
import rotp.core.network.s2c.TrNonEntityStandSummonPacket;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandTuning;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.StandAwakening.AwakeningStage;
import rotp.core.powersystem.standpower.datapack.DataDrivenStandsLoader;
import rotp.core.powersystem.standpower.datapack.StandTypeClass;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.SummonedStand.BlankSummonedStand;
import rotp.core.util.constants.EntityEvents;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.functions.AttributeUtil;
import rotp.core.util.sound.MultiSoundEventResolver;
import rotp.core.util.sound.OstSoundList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforgespi.language.IConfigurable;

public class StandType extends PowerType {
	static {
		SimpleTagKey.SimpleTagType.registerType(StandType.class, StandType::getId, "stands");
	}

	protected final ResourceLocation standTypeId;
	protected StandStats stats;
	private final StandTuning tuning = new StandTuning();
	protected boolean isEnabled;
	protected boolean playSummonSound = true;
	protected boolean playUnsummonSound = true;
	protected Supplier<? extends Holder<SoundEvent>> summonSoundSupplier = () -> ModSoundEvents.STAND_SUMMON;
	protected Supplier<? extends Holder<SoundEvent>> unsummonSoundSupplier = () -> ModSoundEvents.STAND_UNSUMMON;
	protected Supplier<? extends Holder<SoundEvent>> summonShoutSupplier = () -> null;
	protected Supplier<OstSoundList> ostSupplier = () -> null;
	@Nullable
	private StandControlType nonEntityStandControlType;
	private boolean nonEntityManualControl;
	private boolean nonEntityDistanceStrengthDecay;
	private boolean nonEntityStandControlPolicyConfigured;
	private volatile boolean nonEntityStandControlPolicyLogged;
	
	public StandCreationSource createdIn = StandCreationSource.REGISTRY;
	public List<Component> discExtraTooltip = new ArrayList<>();
	/** The lesser this field is, the earlier the disc will appear in the creative tab. Default is 100. */
	public int discCategoryPriority = 100;
	public boolean translucentDisc = false;
	public int discStoryPartPriority = 100;
	
	public StandType(StandStats stats, MovesetBuilder moveset, 
			ResourceLocation id) {
		super(moveset);
		this.standTypeId = id;
		if (stats == null) stats = new StandStats(0, 0, 0, 0, 0, 0);
		this.stats = stats;
		this.isEnabled = true;
		addAddonCredits();
	}
	
	public <T extends StandType> T init(Consumer<T> init) {
		@SuppressWarnings("unchecked")
		T cast = (T) this;
		init.accept(cast);
		return cast;
	}

	/**
	 * Declares the control topology of a Stand that does not use a live
	 * {@link StandEntity}. Entity-backed Stands must use
	 * {@link EntityStandType#standControlType(StandControlType)} instead.
	 */
	public <T extends StandType> T nonEntityStandControlPolicy(
			StandControlType controlType,
			boolean manualControl,
			boolean distanceStrengthDecay) {
		if (this instanceof EntityStandType) {
			throw new IllegalStateException(
					"Entity Stands must use the entity control policy");
		}
		if (controlType != StandControlType.AUTOMATIC
				&& controlType != StandControlType.COLONY
				&& controlType != StandControlType.PHENOMENON) {
			throw new IllegalStateException(
					"Non-entity Stands require an automatic, colony, or phenomenon control type");
		}
		if (manualControl || distanceStrengthDecay) {
			throw new IllegalStateException(
					"Non-entity Stands cannot use generic manual control or distance decay");
		}
		this.nonEntityStandControlType = controlType;
		this.nonEntityManualControl = false;
		this.nonEntityDistanceStrengthDecay = false;
		this.nonEntityStandControlPolicyConfigured = true;
		@SuppressWarnings("unchecked")
		T cast = (T) this;
		return cast;
	}

	/**
	 * What this Stand's control policy lacks, naming the Stand and the field; null when it is complete. The core's
	 * common setup refuses to load with any registered Stand that has one.
	 */
	@Nullable
	public String standControlPolicyProblem() {
		return !nonEntityStandControlPolicyConfigured || nonEntityStandControlType == null
				? "Stand " + getId() + " (" + getClass().getName() + "): standControlType is required"
						+ " (StandType.nonEntityStandControlPolicy)"
				: null;
	}

	/**
	 * Logs this Stand's control policy problem with the defaults the core uses for it in play, once: the core's common
	 * setup in a player's game, then the policy getters, report it here. False when it has none or it was logged before.
	 */
	public boolean reportStandControlPolicyProblem() {
		String problem = standControlPolicyProblem();
		if (problem == null || nonEntityStandControlPolicyLogged) {
			return false;
		}
		nonEntityStandControlPolicyLogged = true;
		JojoMod.getLogger().error("{}; using standControlType AUTOMATIC without manual control or distance decay", problem);
		return true;
	}

	// At run time a missing policy is logged once and the core's default stands in: an automatic Stand.
	private boolean checkNonEntityStandControlPolicy() {
		if (standControlPolicyProblem() == null) {
			return true;
		}
		reportStandControlPolicyProblem();
		return false;
	}

	public StandControlType getStandControlType() {
		return checkNonEntityStandControlPolicy() ? nonEntityStandControlType : StandControlType.AUTOMATIC;
	}

	public boolean canBeManuallyControlled() {
		return checkNonEntityStandControlPolicy() && nonEntityManualControl;
	}

	public boolean usesDistanceStrengthDecay() {
		return checkNonEntityStandControlPolicy() && nonEntityDistanceStrengthDecay;
	}

	public <T extends StandType> T summonShout(Supplier<? extends Holder<SoundEvent>> summonShoutSupplier) {
		return init(stand -> stand.summonShoutSupplier = summonShoutSupplier != null ? summonShoutSupplier : () -> null);
	}

	public <T extends StandType> T summonSound(Supplier<? extends Holder<SoundEvent>> summonSoundSupplier) {
		return init(stand -> stand.summonSoundSupplier = summonSoundSupplier != null ? summonSoundSupplier : () -> ModSoundEvents.STAND_SUMMON);
	}

	public <T extends StandType> T unsummonSound(Supplier<? extends Holder<SoundEvent>> unsummonSoundSupplier) {
		return init(stand -> stand.unsummonSoundSupplier = unsummonSoundSupplier != null ? unsummonSoundSupplier : () -> ModSoundEvents.STAND_UNSUMMON);
	}

	public <T extends StandType> T ost(Supplier<OstSoundList> ostSupplier) {
		return init(stand -> stand.ostSupplier = ostSupplier != null ? ostSupplier : () -> null);
	}

	public Holder<SoundEvent> getSummonSound() {
		Holder<SoundEvent> sound = summonSoundSupplier.get();
		return sound != null ? sound : ModSoundEvents.STAND_SUMMON;
	}

	public Holder<SoundEvent> getUnsummonSound() {
		Holder<SoundEvent> sound = unsummonSoundSupplier.get();
		return sound != null ? sound : ModSoundEvents.STAND_UNSUMMON;
	}

	@Nullable
	public OstSoundList getOst(@Nullable LivingEntity user) {
		return ostSupplier.get();
	}
	
	public <T extends StandType> T discTooltipWIP() { return discTooltipWIP(false); }
	public <T extends StandType> T discTooltipWIP(boolean translucentDisc) { 
		return init(stand -> {
			stand.discExtraTooltip.add(
					Component.translatable("item.jojo_ripples.stand_disc.wip")
					.withStyle(ChatFormatting.ITALIC).withColor(0x808000));
			stand.discCategoryPriority = 200;
			stand.translucentDisc = translucentDisc;
		});
	}
	
	public <T extends StandType> T discTooltipOld() { 
		return init(stand -> {
			stand.discExtraTooltip.add(
					Component.translatable("item.jojo_ripples.stand_disc.old")
					.withStyle(ChatFormatting.ITALIC).withColor(0x808000));
			stand.discCategoryPriority = 201;
			stand.translucentDisc = true;
		});
	}
	
	public <T extends StandType> T discTooltipExperimental() { 
		return init(stand -> {
			stand.discExtraTooltip.add(
					Component.translatable("item.jojo_ripples.stand_disc.experimental")
					.withStyle(ChatFormatting.ITALIC).withColor(0x800000));
			stand.discCategoryPriority = 300;
			stand.translucentDisc = true;
		});
	}
	
	public void discTooltipDatapack() { 
		this.createdIn = StandCreationSource.DATAPACK;
		this.discExtraTooltip.add(
				Component.translatable("item.jojo_ripples.stand_disc.data_pack")
				.withStyle(ChatFormatting.ITALIC).withColor(0x6060ff));
		this.discCategoryPriority = 400;
	}

	public void addAddonCredits() {
		if (createdIn == StandCreationSource.REGISTRY) {
			String modId = standTypeId.getNamespace();
			if (!modId.equals(JojoMod.MOD_ID)) {
				ModList.get().getModContainerById(modId)
				.map(mod -> mod.getModInfo())
				.flatMap(modInfo -> modInfo instanceof IConfigurable ? ((IConfigurable) modInfo).getConfigElement("authors") : Optional.empty())
				.map(authorsString -> authorsString instanceof String ? (String) authorsString : null)
				.ifPresent(authors -> {
					authors = authors.replace(", StandoByte", "").replace("StandoByte, ", "");
					discExtraTooltip.add(
							Component.translatable("item.jojo_ripples.stand_disc.addon_author", authors)
							.withStyle(ChatFormatting.ITALIC).withColor(0x00b0b0));
				});
			}
		}
	}
	
	
	@Override
	public JsonObject makeConfigTemplate() {
		StandControlType controlType = getStandControlType();
		JsonObject json = super.makeConfigTemplate();
		StandTypeClass.addClassToJson(json, this.getClass());
		json.add("stats", stats.makeConfigTemplate());
		json.addProperty("standControlType", controlType.name());
		json.addProperty(
				"manualControlEnabled",
				canBeManuallyControlled());
		json.addProperty(
				"distanceStrengthDecayEnabled",
				usesDistanceStrengthDecay());
		return json;
	}
	
	@Override
	public void applyConfig(JsonElement json) {
		super.applyConfig(json);
		JsonObject config = json.getAsJsonObject();
		Optional.ofNullable(config.get("stats")).ifPresent(stats::applyConfig);
		Optional.ofNullable(config.get(StandTuning.MEMBER)).ifPresent(tuning::applyConfig);
	}

	@Override
	public void restoreDefaults() {
		super.restoreDefaults();
		stats.restoreDefaults();
		tuning.restoreDefaults();
	}
	
	
	@Override
	public boolean isEnabled() {
		return isEnabled;
	}
	
	public void setEnabled(boolean isEnabled) {
		this.isEnabled = isEnabled;
	}
	
	
	public StandStats getStandStats() {
		return stats;
	}

	/** The server's rules for this Stand beside its stats (the settings screen's Stands page). */
	public StandTuning getTuning() {
		return tuning;
	}
	
	
	@Nonnull
	@Override
	public StandTypePersistentData newDataInstance() {
		return new StandTypePersistentData(this);
	}
	
	
	public void onUserSummonCommand(LivingEntity user, StandPower standPower) {
		if (!standPower.isSummoned() && onTrySummon(user, standPower)) {
			summon(user, standPower);
		}
		else {
			unsummon(user, standPower);
		}
	}
	
	/**
	 * The key a player binds for it, which never summons: an action still in its windup or charge ends with nothing done,
	 * and a Stand with no action goes back as with the summon key. A press during the Stand's own unsummon ends that at
	 * once ({@link rotp.core.powersystem.standpower.entity.EntityStandType}). An action already performing or recovering
	 * goes on.
	 */
	public void onUserCancelCommand(LivingEntity user, StandPower standPower) {
		if (!StandActionCancel.cancelWindup(LivingComponentAction.getExistingComponent(user, PowerClass.STAND), standPower)
				&& standPower.isSummoned() && LivingComponentAction.getCurEntityAction(user, PowerClass.STAND) == null) {
			unsummon(user, standPower);
		}
	}

	public boolean summon(LivingEntity user, StandPower standPower) {
		if (!standPower.isSummoned() && standPower.canUsePower()) {
			SummonedStand summonedStand = makeSummonedStand();
			if (summonedStand == null) return false;
			
			standPower.setSummonedStand(summonedStand);
			if (user != null && !user.level().isClientSide()) {
				playSummonShout(user);
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrNonEntityStandSummonPacket(user.getId(), true));
				if (playSummonSound) {
					PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, StandSkinSoundPacket.play(
							user.position(), MultiSoundEventResolver.resolve(getSummonSound()),
							standPower, user.getSoundSource(), 1, 1));
				}
				if (user instanceof net.minecraft.server.level.ServerPlayer player) {
					ModCriteriaTriggers.triggerSummonStand(player);
				}
			}
			return true;
		}
		return false;
	}

	protected void playSummonShout(LivingEntity user) {
		if (user != null && !user.level().isClientSide() && !user.isShiftKeyDown()) {
			Holder<SoundEvent> shout = summonShoutSupplier.get();
			JojoModUtil.sayVoiceLine(user, shout, ModSoundEvents.VOICELINE_STAND_SUMMON, 1.0F, 1.0F, 200, false);
		}
	}
	
	public boolean onTrySummon(LivingEntity user, StandPower standPower) {
		AwakeningStage awakeningStage = standPower.userStandAwakeningState.stage;
		return switch (awakeningStage) {
			case FULL_CONTROL -> true;
			case PARTIALLY_AWAKENED -> {
				user.sendSystemMessage(Component.translatable("stand_summon.not_in_full_control"));
				yield false;
			}
			case AWAKENING_PASSIVE -> {
				user.sendSystemMessage(Component.translatable("stand_summon.dormant"));
				yield false;
			}
		};
	}
	
	/**
	 * If this is overriden to return null, the Stand type will have no summon/unsummon mechanic.
	 */
	protected SummonedStand makeSummonedStand() {
		return new BlankSummonedStand();
	}
	
	public void unsummon(LivingEntity user, StandPower standPower) {
		boolean wasByUser = standPower.isUnsummonedByItsUser();
		standPower.setUnsummonedByItsUser(true);
		try {
			forceUnsummon(user, standPower);
		}
		finally {
			standPower.setUnsummonedByItsUser(wasByUser);
		}
	}

	public void forceUnsummon(LivingEntity user, StandPower standPower) {
		if (standPower.isSummoned()) {
			if (user != null && !user.level().isClientSide() && !standPower.isUnsummonedByItsUser()) {
				keepUsersStandHud(user);
			}
			standPower.setSummonedStand(null);
			if (user != null && !user.level().isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrNonEntityStandSummonPacket(user.getId(), false));
				if (playUnsummonSound) {
					PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, StandSkinSoundPacket.play(
							user.position(), MultiSoundEventResolver.resolve(getUnsummonSound()), 
							standPower, user.getSoundSource(), 1, 1));
				}
			}
		}
	}
	
	
	/**
	 * 1.16 left its user's HUD in the Stand mode through every unsummon but the user's own (ActionsOverlayGui.onStandUnsummon,
	 * from the unsummon task only), so a skill or a rule that unsummons the Stand leaves the bar to use it with again; a
	 * dimension change and a respawn reset the HUD (UpdateClientCapCachePacket), and a dead user keeps nothing. Tells the
	 * user's client, before the Stand is gone, to keep the Stand HUD its summoned Stand showed (InputHandler).
	 */
	protected static void keepUsersStandHud(LivingEntity user) {
		if (user instanceof ServerPlayer player && player.isAlive() && player.connection != null) {
			player.connection.send(new ClientboundEntityEventPacket(player, EntityEvents.KEEP_STAND_HUD));
		}
	}

	public boolean usesStamina(StandPower standPower) {
		return true;
	}
	
	public float getMaxStamina(StandPower standPower) {
		return getBaseMaxStamina(standPower) * getStaminaMultiplier(standPower);
	}
	
	protected float getBaseMaxStamina(StandPower standPower) {
		return 1000;
	}
	
	public float getStaminaRegen(StandPower standPower) {
		return getBaseStaminaRegen(standPower) * getStaminaMultiplier(standPower);
	}

	public float getStaminaRegenBeforeExternalModifiers(StandPower standPower) {
		return getBaseStaminaRegen(standPower);
	}

	public float getStaminaDurabilityMultiplier(StandPower standPower) {
		return getStaminaMultiplier(standPower);
	}
	
	protected float getBaseStaminaRegen(StandPower standPower) {
		if (standPower.isSummoned()) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null) {
				EntityActionInstance action = LivingComponentAction.getCurEntityAction(standEntity);
				if (action != null && !action.canStaminaRegen(standPower, standEntity)) {
					// none while the Stand acts (1.16), a share of it in the no-cooldown mode (the owner, 2026-11-11)
					return 1.5f * StandAbilityFatigue.regenKeptDuringUse(standPower);
				}
			}
			return 1.5f;
		}
		return 3;
	}
	
	protected static float getStaminaMultiplier(StandPower standPower) {
		double durability = AttributeUtil.getValueOrDefault(standPower.getUser(), ModEntityAttributes.STAND_DURABILITY);
		return StandStatFormulas.getStaminaMultiplier(durability);
	}
	
	
	public boolean usesResolve(StandPower standPower) {
		return true;
	}

	public boolean keepOnDeath(StandPower standPower) {
		return JojoModConfig.getCommonConfigInstance(false).keepStandOnDeath.get();
	}

	public int getMaxResolveLevel() {
		return StandTypePersistentData.MAX_RESOLVE_LEVEL;
	}

	public void onNewResolveLevel(StandPower standPower) {
		StandTypePersistentData data = standPower != null ? standPower.getCurTypeData() : null;
		LivingEntity user = standPower != null ? standPower.getUser() : null;
		if (data != null && user != null && !user.level().isClientSide()
				&& data.unlockOriginalResolveSkills(standPower)) {
			data.syncOnUpdate(user);
		}
	}
	
	
	@Override
	public Map<String, StandUnlockableSkill> getUnlockableSkills() {
		return (Map<String, StandUnlockableSkill>) super.getUnlockableSkills();
	}
	
	
	@Override
	public ResourceLocation getId() {
		return standTypeId;
	}

	@Override
	protected ResourceLocation getMovesetExtensionTargetId() {
		return standTypeId;
	}
	
	@Nullable
	public static StandType fromId(ResourceLocation id) {
		StandType stand = DataDrivenStandsLoader.getDatapackStand(id);
		if (stand == null) {
			stand = JojoRegistries.DEFAULT_STANDS_REG.get(id);
		}
		if (stand != null && !stand.isEnabled()) {
			stand = null;
		}
		return stand;
	}
	
	public static Stream<StandType> getAllEnabledStands() {
		return Stream.concat(
				JojoRegistries.DEFAULT_STANDS_REG.entrySet().stream().map(Map.Entry::getValue).filter(StandType::isEnabled), 
				DataDrivenStandsLoader.getAllDatapackStands());
	}
	
//	public static final StreamCodec<ByteBuf, StandType> SYNC_VIA_ID = 
//			ResourceLocation.STREAM_CODEC.map(StandType::fromId, StandType::getId);
//	
	
	
	@Override
	public PowerClass<StandPower> getPowerClass() {
		return PowerClass.STAND;
	}
	
	public Lazy<Component> name = Lazy.of(() -> Component.translatable(Util.makeDescriptionId("stand", this.getId())));
	@Override
	public Component getName(Power<?> playerPowerData) {
		return ((StandPower) playerPowerData).getStandInstance()
				.map(stand -> stand.getStandName(playerPowerData.getUser().level().isClientSide()))
				.orElseGet(this.name::get);
	}
	
	
	public StandSkinsScreen.SkinView makeSkinUIElement(StandSkin skin, StandSkinsScreen screen, int x, int y, int standY, int row, int column, boolean isBottomRow) {
		return new StandSkinsScreen.SkinView(this, skin, screen, x, y, standY, row, column, isBottomRow);
	}
	
}
