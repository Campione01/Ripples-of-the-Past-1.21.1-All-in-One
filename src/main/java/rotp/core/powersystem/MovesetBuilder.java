package rotp.core.powersystem;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.config.ConfigAbilityFactory;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.unlockableskill.UnlockableSkill;
import rotp.core.subsystems.entity_useitem.StandItemUse;
import com.mojang.datafixers.util.Pair;

import net.minecraft.resources.ResourceLocation;

@ApiStatus.NonExtendable
public class MovesetBuilder {
	public static final String GRAB_CHARGED_HEAVY_KEY_MAPPING_NAME = "jojo_ripples.key.grab_charged_heavy";
	private static final InputUseVanillaMapping GRAB_CHARGED_HEAVY_INPUT =
			new InputUseVanillaMapping(GRAB_CHARGED_HEAVY_KEY_MAPPING_NAME);
	public static final InputBindTemplate DEFAULT_GRAB_INPUT = GRAB_CHARGED_HEAVY_INPUT;
	public static final InputBindTemplate DEFAULT_CHARGED_HEAVY_INPUT = GRAB_CHARGED_HEAVY_INPUT;
	public static final String GRAB_THROW_KEY_MAPPING_NAME = "jojo_ripples.key.grab_throw";
	public static final String GRAB_RELEASE_KEY_MAPPING_NAME = "jojo_ripples.key.grab_release";
	public static final String SWAP_ITEMS_KEY_MAPPING_NAME = "jojo_ripples.key.swap_items";
	public static final InputBindTemplate DEFAULT_GRAB_THROW_INPUT = new InputUseVanillaMapping(GRAB_THROW_KEY_MAPPING_NAME);
	public static final InputBindTemplate DEFAULT_GRAB_RELEASE_INPUT = new InputUseVanillaMapping(GRAB_RELEASE_KEY_MAPPING_NAME);
	private static final String BLOCK_TOSS = "block_toss";
	private static final int PUNCH_COMBO_LENGTH = 4;
	private static final int LAST_COMBO_PUNCH_WINDUP = 5;

	public final Map<String, ConfigAbilityFactory<?>> abilities = new LinkedHashMap<>();
	public final Map<String, UnlockableSkill> unlockableSkills = new LinkedHashMap<>();

	@ApiStatus.Internal public Map<String, ControlSchemeTemplate> controlSchemes = new LinkedHashMap<>();
	@ApiStatus.Internal public ControlSchemeTemplate curControlScheme = new ControlSchemeTemplate();
	private boolean humanoidStand;
	private boolean noPunchCombo;
	private final Set<String> disabledByConfig = new LinkedHashSet<>();
	private final Set<ResourceLocation> appliedStandMovesetExtensions = new LinkedHashSet<>();
	private final Set<ResourceLocation> appliedPlayerPowerMovesetExtensions = new LinkedHashSet<>();
	private final Map<PlayerPowerHotbarAnchor, String>
			playerPowerHotbarInsertionTails = new LinkedHashMap<>();
//	protected final Set<String> disable = new HashSet<>();
	
	
	public MovesetBuilder deepCopy() {
		MovesetBuilder copy = new MovesetBuilder();
		for (var abilityEntry : abilities.entrySet()) {
			copy.abilities.put(abilityEntry.getKey(), abilityEntry.getValue().copy());
		}
		copy.unlockableSkills.putAll(this.unlockableSkills);
		this.controlSchemes.forEach((name, scheme) -> copy.controlSchemes.put(name, scheme.deepCopy()));
		copy.curControlScheme = this.curControlScheme.deepCopy();
		copy.lastAbility = this.lastAbility;
		copy.humanoidStand = this.humanoidStand;
		copy.noPunchCombo = this.noPunchCombo;
		copy.disabledByConfig.addAll(this.disabledByConfig);
		copy.appliedStandMovesetExtensions.addAll(this.appliedStandMovesetExtensions);
		copy.appliedPlayerPowerMovesetExtensions.addAll(
				this.appliedPlayerPowerMovesetExtensions);
		copy.playerPowerHotbarInsertionTails.putAll(
				this.playerPowerHotbarInsertionTails);
		return copy;
	}

	@ApiStatus.Internal
	public boolean hasStandMovesetExtension(ResourceLocation extensionId) {
		return appliedStandMovesetExtensions.contains(extensionId);
	}

	@ApiStatus.Internal
	public void markStandMovesetExtension(ResourceLocation extensionId) {
		appliedStandMovesetExtensions.add(extensionId);
	}

	@ApiStatus.Internal
	public void commitStandMovesetExtension(
			MovesetBuilder staged,
			ResourceLocation extensionId) {
		abilities.clear();
		abilities.putAll(staged.abilities);
		unlockableSkills.clear();
		unlockableSkills.putAll(staged.unlockableSkills);
		controlSchemes.clear();
		controlSchemes.putAll(staged.controlSchemes);
		curControlScheme = staged.curControlScheme;
		lastAbility = staged.lastAbility;
		humanoidStand = staged.humanoidStand;
		noPunchCombo = staged.noPunchCombo;
		disabledByConfig.clear();
		disabledByConfig.addAll(staged.disabledByConfig);
		appliedStandMovesetExtensions.clear();
		appliedStandMovesetExtensions.addAll(
				staged.appliedStandMovesetExtensions);
		appliedStandMovesetExtensions.add(extensionId);
		appliedPlayerPowerMovesetExtensions.clear();
		appliedPlayerPowerMovesetExtensions.addAll(
				staged.appliedPlayerPowerMovesetExtensions);
		playerPowerHotbarInsertionTails.clear();
		playerPowerHotbarInsertionTails.putAll(
				staged.playerPowerHotbarInsertionTails);
	}

	@ApiStatus.Internal
	public boolean hasPlayerPowerMovesetExtension(
			ResourceLocation extensionId) {
		return appliedPlayerPowerMovesetExtensions.contains(
				extensionId);
	}

	@ApiStatus.Internal
	public void markPlayerPowerMovesetExtension(
			ResourceLocation extensionId) {
		appliedPlayerPowerMovesetExtensions.add(extensionId);
	}

	@ApiStatus.Internal
	public void commitPlayerPowerMovesetExtension(
			MovesetBuilder staged,
			ResourceLocation extensionId) {
		abilities.clear();
		abilities.putAll(staged.abilities);
		unlockableSkills.clear();
		unlockableSkills.putAll(staged.unlockableSkills);
		controlSchemes.clear();
		controlSchemes.putAll(staged.controlSchemes);
		curControlScheme = staged.curControlScheme;
		lastAbility = staged.lastAbility;
		humanoidStand = staged.humanoidStand;
		noPunchCombo = staged.noPunchCombo;
		disabledByConfig.clear();
		disabledByConfig.addAll(staged.disabledByConfig);
		appliedStandMovesetExtensions.clear();
		appliedStandMovesetExtensions.addAll(
				staged.appliedStandMovesetExtensions);
		appliedPlayerPowerMovesetExtensions.clear();
		appliedPlayerPowerMovesetExtensions.addAll(
				staged.appliedPlayerPowerMovesetExtensions);
		appliedPlayerPowerMovesetExtensions.add(extensionId);
		playerPowerHotbarInsertionTails.clear();
		playerPowerHotbarInsertionTails.putAll(
				staged.playerPowerHotbarInsertionTails);
	}

	@ApiStatus.Internal
	public void appendStandMovesetExtensionHotbar(
			String controlSchemeName, int hotbarId,
			String abilityName, InputMethod inputMethod) {
		if (!abilities.containsKey(abilityName)) {
			throw new IllegalStateException(
					"hotbar entry references missing ability: "
							+ abilityName);
		}
		ControlSchemeTemplate controlScheme =
				controlSchemes.get(controlSchemeName);
		if (controlScheme == null) {
			throw new IllegalStateException(
					"control scheme does not exist: "
							+ controlSchemeName);
		}
		controlScheme.appendToExistingHotbar(
				abilityName, hotbarId, inputMethod);
	}

	@ApiStatus.Internal
	public void addStandMovesetExtensionHotbarVariation(
			String controlSchemeName, int hotbarId,
			String baseAbilityName, String abilityName,
			InputKey.Modifier modifier, InputMethod inputMethod) {
		addPlayerPowerMovesetExtensionHotbarVariation(
				controlSchemeName, hotbarId, baseAbilityName,
				abilityName, modifier, inputMethod);
	}

	@ApiStatus.Internal
	public void insertPlayerPowerMovesetExtensionHotbar(
			String controlSchemeName,
			int hotbarId,
			String anchorAbilityName,
			String abilityName,
			InputMethod inputMethod) {
		requirePlayerPowerExtensionAbility(abilityName);
		requirePlayerPowerExtensionAbility(anchorAbilityName);
		ControlSchemeTemplate controlScheme =
				requirePlayerPowerExtensionControlScheme(
						controlSchemeName);
		PlayerPowerHotbarAnchor anchor =
				new PlayerPowerHotbarAnchor(
						controlSchemeName,
						hotbarId,
						anchorAbilityName);
		String insertionTail =
				playerPowerHotbarInsertionTails.getOrDefault(
						anchor, anchorAbilityName);
		controlScheme.insertAfterExistingHotbarSlot(
				abilityName,
				hotbarId,
				insertionTail,
				inputMethod);
		playerPowerHotbarInsertionTails.put(
				anchor, abilityName);
	}

	@ApiStatus.Internal
	public void addPlayerPowerMovesetExtensionHotbarVariation(
			String controlSchemeName,
			int hotbarId,
			String baseAbilityName,
			String abilityName,
			InputKey.Modifier modifier,
			InputMethod inputMethod) {
		requirePlayerPowerExtensionAbility(abilityName);
		requirePlayerPowerExtensionAbility(baseAbilityName);
		requirePlayerPowerExtensionControlScheme(controlSchemeName)
				.addExistingHotbarSlotVariation(
						abilityName,
						hotbarId,
						baseAbilityName,
						modifier,
						inputMethod);
	}

	@ApiStatus.Internal
	public void bindPlayerPowerMovesetExtensionGroup(
			String controlSchemeName,
			String movesetGroupName,
			String abilityName,
			InputMethod inputMethod,
			InputBindTemplate input) {
		requirePlayerPowerExtensionAbility(abilityName);
		requirePlayerPowerExtensionControlScheme(controlSchemeName)
				.bindToExistingGroup(
						movesetGroupName,
						abilityName,
						inputMethod,
						input);
	}

	private void requirePlayerPowerExtensionAbility(
			String abilityName) {
		if (!abilities.containsKey(abilityName)) {
			throw new IllegalStateException(
					"hotbar entry references missing ability: "
							+ abilityName);
		}
	}

	private ControlSchemeTemplate
			requirePlayerPowerExtensionControlScheme(
					String controlSchemeName) {
		ControlSchemeTemplate controlScheme =
				controlSchemes.get(controlSchemeName);
		if (controlScheme == null) {
			throw new IllegalStateException(
					"control scheme does not exist: "
							+ controlSchemeName);
		}
		return controlScheme;
	}

	private record PlayerPowerHotbarAnchor(
			String controlSchemeName,
			int hotbarId,
			String anchorAbilityName) {}
	
	public Moveset build(PowerClass<?> powerClass, ResourceLocation powerTypeId) {
		prepareControlSchemes();
		
		Map<String, Ability> abilities = new LinkedHashMap<>();
		for (var abilityEntry : this.abilities.entrySet()) {
			String abilityName = abilityEntry.getKey();
			var abilityFactory = abilityEntry.getValue();
//			if (!disable.contains(key)) {
				var ability = abilityFactory.makeAbility(new AbilityId(powerClass, powerTypeId, abilityName));
				abilities.put(abilityName, ability);
//			}
		}
		Moveset moveset = new Moveset(abilities, powerClass, powerTypeId);
		moveset.controlScheme = this.curControlScheme;
		return moveset;
	}

	void prepareControlSchemes() {
		if (this.controlSchemes.isEmpty()) {
			this.controlSchemes.put("default", curControlScheme);
		}
		postInitControls();
	}
	
	
	public <A extends Ability> MovesetBuilder addAbility(String abilityName, AbilityType<A> abilityType) {
		return addAbility(abilityName, abilityType, null);
	}

	public final <A extends Ability> MovesetBuilder addAbility(String abilityName, AbilityType<A> abilityType, 
			@Nullable Consumer<A> init) {
		abilities.put(abilityName, new ConfigAbilityFactory<>(abilityType, init));
		lastAbility = abilityName;
		return this;
	}
	
	public <A extends Ability> MovesetBuilder addAbility(String abilityName, Supplier<? extends AbilityType<A>> abilityType) {
		return addAbility(abilityName, abilityType.get());
	}
	
	public final <A extends Ability> MovesetBuilder addAbility(String abilityName, Supplier<? extends AbilityType<A>> abilityType, 
			Consumer<A> init) {
		return addAbility(abilityName, abilityType.get(), init);
	}
	
	
//	public MovesetBuilder disableAbility(String abilityName) {
//		disable.add(abilityName);
//		return this;
//	}
	
	
	// Control scheme stuff
	
	@ApiStatus.Internal public String lastAbility;
	
	public ControlSchemeTemplate makeControlScheme(String name) {
		curControlScheme = controlSchemes.computeIfAbsent(name, __ -> new ControlSchemeTemplate());
		curControlScheme.curMovesetBuilder = this;
		return curControlScheme;
	}
	
	@Deprecated
	public MovesetBuilder makeMovesetGroup(String name, InputBindTemplate toggleHudKey) {
		curControlScheme.makeMovesetGroup(name, toggleHudKey);
		return this;
	}

	@Deprecated
	public MovesetBuilder withBind(InputMethod inputMethod, InputBindTemplate key) {
		return withBind(null, inputMethod, key);
	}

	@Deprecated
	public MovesetBuilder withBind(String movesetGroupName, InputMethod inputMethod, InputBindTemplate key) {
		curControlScheme.setMovesetGroup(movesetGroupName);
		curControlScheme.bind(lastAbility, inputMethod, key);
		return this;
	}

	@Deprecated
	public MovesetBuilder makeHotbar(int hotbarId, InputBindTemplate useAbilityKey, InputBindTemplate switchAbilityKey) {
		return makeHotbar(null, hotbarId, useAbilityKey, switchAbilityKey);
	}

	@Deprecated
	public MovesetBuilder makeHotbar(String movesetGroupName, int hotbarId, InputBindTemplate useAbilityKey, InputBindTemplate switchAbilityKey) {
		curControlScheme.setMovesetGroup(movesetGroupName);
		curControlScheme.makeHotbar(hotbarId, useAbilityKey, switchAbilityKey);
		return this;
	}

	@Deprecated
	public MovesetBuilder inHotbar(int hotbarId, InputMethod inputMethod) {
		curControlScheme.addToHotbar(lastAbility, hotbarId, inputMethod);
		return this;
	}

	@Deprecated
	public MovesetBuilder inHotbarSlotVariation(String baseAbility, @Nullable InputKey.Modifier modifier, InputMethod inputMethod) {
		curControlScheme.addHotbarSlotVariation(lastAbility, baseAbility, modifier, inputMethod);
		return this;
	}
	
	// Unlockable skill stuff
	
	public MovesetBuilder addSkill(UnlockableSkill skill) {
		unlockableSkills.put(skill.skillName, skill);
		return this;
	}

	
	// shortcuts for some common abilities
	
	public MovesetBuilder addHumanoidStandStuff() {
		humanoidStand = true;
		addManualControl();
		return this;
	}
	
	public MovesetBuilder addManualControl() {
		addAbility("manual_control", ModStandAbilities.MANUAL_CONTROL);
		return this;
	}

	/**
	 * For a humanoid Stand whose left click is a {@link StandEntityPunchAbility} and still no four-punch combo: a move of its own
	 * under the punch's class, or a decision that its one punch stays. Say why at the call.
	 */
	public MovesetBuilder noPunchCombo() {
		noPunchCombo = true;
		return this;
	}

	@Deprecated
	public MovesetBuilder addItemUsage() {
		return this;
	}
	
	public MovesetBuilder addHumanoidStandSkills() {
		return this
		.addSkill(StandUnlockableSkill.startingAbility("guard"))
		.addSkill(StandUnlockableSkill.startingAbility("manual_control"))
		.addSkill(new StandUnlockableSkill(StandItemUse.SKILL).setIsStartingSkill());
	}

	/**
	 * What a moveset has by the rules of the core, for add-ons built against any version of it: a Stand with the grab can toss a
	 * block, a humanoid Stand can use what it holds, and a humanoid Stand whose plain left click is a light punch has the four-punch
	 * combo. Skills are read without building the moveset, so this runs on every copy PowerType makes, after the extensions. The
	 * skill's name stands for a moveset's own choice: one that names it, or that a config has taken the ability from, keeps what it has.
	 */
	@ApiStatus.Internal
	public void addWhatItsMovesBring() {
		if (abilities.containsKey("grab") && !abilities.containsKey(BLOCK_TOSS) && !unlockableSkills.containsKey(BLOCK_TOSS)) {
			addAbility(BLOCK_TOSS, ModStandAbilities.BLOCK_TOSS, Ability::useAbilityTypeResourceNamespace);
			addSkill(StandUnlockableSkill.startingAbility(BLOCK_TOSS));
		}
		if (humanoidStand && !unlockableSkills.containsKey(StandItemUse.SKILL)) {
			addSkill(new StandUnlockableSkill(StandItemUse.SKILL).setIsStartingSkill());
		}
		if (humanoidStand && !noPunchCombo) {
			leftClickPunches().forEach(this::addPunchCombo);
		}
	}

	private Set<String> leftClickPunches() {
		Set<String> punches = new LinkedHashSet<>();
		for (ControlSchemeTemplate scheme : controlSchemes.isEmpty() ? Set.of(curControlScheme) : controlSchemes.values()) {
			scheme.defaultGroup.separateBinds.forEach((name, bind) -> {
				ConfigAbilityFactory<?> ability = abilities.get(name);
				if (ability != null && ability.abilityType()._defaultAbilityInstance instanceof StandEntityPunchAbility
						&& sameBinding(bind, Pair.of(InputMethod.CLICK, InputKey.LMB))) {
					punches.add(name);
				}
			});
		}
		return punches;
	}

	// A copy of the punch's own factory, so its sounds, knockback and whatever else its declaration set come along. No skill of their own:
	// no row on the skills screen, and no bind, so no slot on the HUD or the controls screen.
	private void addPunchCombo(String punch) {
		for (int i = 2; i <= PUNCH_COMBO_LENGTH; i++) {
			String member = punch + i;
			if (!abilities.containsKey(member) && !disabledByConfig.contains(member)) {
				abilities.put(member, comboMember(abilities.get(punch), punch, i));
			}
		}
	}

	private static <A extends Ability> ConfigAbilityFactory<A> comboMember(ConfigAbilityFactory<A> punch, String punchName, int number) {
		ConfigAbilityFactory<A> member = punch.copy();
		member.addInitBehavior(ability -> {
			((StandEntityPunchAbility) ability).initIsComboMemberOf(punchName, number - 1);
			if (number == PUNCH_COMBO_LENGTH) {
				((StandEntityPunchAbility) ability).setDefaultPhaseLength(ActionPhase.WINDUP, LAST_COMBO_PUNCH_WINDUP);
			}
		});
		return member;
	}

	/** A config's "disable": the rules above do not bring the ability back on the copies PowerType makes from it. */
	@ApiStatus.Internal
	public void disableByConfig(String abilityName) {
		abilities.remove(abilityName);
		disabledByConfig.add(abilityName);
	}

	protected void postInitControls() {
		if (abilities.containsKey("grab")) {
			if (!abilities.containsKey("grab_release")) {
				addAbility("grab_release", ModStandAbilities.GRAB_RELEASE);
			}
			addBindToAllCtrlSchemesIfMissing("grab_release", InputMethod.CLICK, DEFAULT_GRAB_RELEASE_INPUT);
		}
		addBindToAllCtrlSchemesIfMissing(BLOCK_TOSS, InputMethod.HOLD, InputKey.LMB);
		addBindToAllCtrlSchemesIfMissing("manual_control", InputMethod.CLICK, InputKey.O);
		addBindToAllCtrlSchemesIfMissing("items_swap_w_user", InputMethod.CLICK, new InputUseVanillaMapping(SWAP_ITEMS_KEY_MAPPING_NAME));
		addBindToAllCtrlSchemesIfMissing("items_swap_hands", InputMethod.CLICK, new InputUseVanillaMapping("key.swapOffhand"));
		addBindToAllCtrlSchemesIfMissing("item_toss", InputMethod.CLICK, new InputUseVanillaMapping("key.drop"));
	}
	
	protected void addBindToAllCtrlSchemesIfMissing(String abilityName, InputMethod inputMethod, InputBindTemplate key) {
		if (abilities.containsKey(abilityName)) {
			Pair<InputMethod, InputBindTemplate> resolvedBinding = null;
			String resolvedSchemeName = null;
			for (var entry : controlSchemes.entrySet()) {
				ControlSchemeTemplate ctrlScheme = entry.getValue();
				ctrlScheme.setMovesetGroup(null);
				Pair<InputMethod, InputBindTemplate> existing =
						ctrlScheme._curGroup.separateBinds.get(abilityName);
				if (existing != null) {
					if (resolvedBinding == null) {
						resolvedBinding = existing;
						resolvedSchemeName = entry.getKey();
					}
					else if (!sameBinding(resolvedBinding, existing)) {
						throw new IllegalStateException(
								"Conflicting explicit bindings for automatic ability "
										+ abilityName + " in control schemes "
										+ resolvedSchemeName + " and " + entry.getKey());
					}
				}
			}
			if (resolvedBinding == null) {
				resolvedBinding = Pair.of(inputMethod, key);
			}
			for (ControlSchemeTemplate ctrlScheme : controlSchemes.values()) {
				ctrlScheme.setMovesetGroup(null);
				if (!ctrlScheme._curGroup.separateBinds.containsKey(abilityName)) {
					ctrlScheme.bind(
							abilityName,
							resolvedBinding.getFirst(),
							resolvedBinding.getSecond());
				}
			}
		}
	}

	private static boolean sameBinding(
			Pair<InputMethod, InputBindTemplate> first,
			Pair<InputMethod, InputBindTemplate> second) {
		if (first.getFirst() != second.getFirst()) {
			return false;
		}
		InputBindTemplate firstInput = first.getSecond();
		InputBindTemplate secondInput = second.getSecond();
		if (firstInput == secondInput) {
			return true;
		}
		if (firstInput instanceof InputKey firstKey
				&& secondInput instanceof InputKey secondKey) {
			return firstKey.device == secondKey.device
					&& firstKey.keyCode == secondKey.keyCode
					&& firstKey.modifier == secondKey.modifier;
		}
		if (firstInput instanceof InputUseVanillaMapping firstMapping
				&& secondInput instanceof InputUseVanillaMapping secondMapping) {
			return firstMapping.keyMappingName.equals(
					secondMapping.keyMappingName);
		}
		return false;
	}
	
	
	
	/**
	 * @deprecated Use {@link #withBind(InputMethod, InputBindTemplate)} (just swap the parameters)
	 */
	@Deprecated
	public MovesetBuilder withBind(InputBindTemplate key, InputMethod inputMethod) {
		return withBind(inputMethod, key);
	}

	/**
	 * @deprecated. Use {@link #withBind(String, InputMethod, InputBindTemplate)} (just swap the 2nd and 3rd parameters)
	 */
	@Deprecated
	public MovesetBuilder withBind(String movesetGroupName, InputBindTemplate key, InputMethod inputMethod) {
		return withBind(movesetGroupName, inputMethod, key);
	}
	
}
