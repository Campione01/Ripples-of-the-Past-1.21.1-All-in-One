package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The core's rule for the four-punch combo (MovesetBuilder.addWhatItsMovesBring): a humanoid Stand whose plain left click is a light
 * punch has it, each later punch made from that Stand's own punch, with nothing new for the player to see but the clips.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PunchComboGameTests {
	private static final List<String> IN = List.of("star_platinum", "crazy_diamond", "the_world", "gold_experience", "magicians_red");
	private static final List<String> OUT = List.of("silver_chariot", "hierophant_green", "boy_ii_man", "mr_president");
	private static final short KEY = 131;

	private PunchComboGameTests() {}

	public static final class OwnClassPunch extends StandEntityPunchAbility {
		public OwnClassPunch(AbilityType<?> type, AbilityId id) {
			super(type, id);
			anim = ActionAnimIdentifier.getOrCreate("punch", false);
		}
	}

	private static final AbilityType<OwnClassPunch> OWN_CLASS_PUNCH = new AbilityType<>(JojoMod.resLoc("gametest_own_class_punch"), OwnClassPunch::new);

	private static EntityStandType standOf(MovesetBuilder moveset, String id) {
		return new EntityStandType(new StandStats.Builder().power(1).speed(1).range(1, 2).durability(1).precision(1).build(), moveset, JojoMod.resLoc(id),
				StandControlType.CLOSE_RANGE_DIRECT, 1, 2, true, true);
	}

	private static MovesetBuilder onTheLeftClick(MovesetBuilder moveset, String name) {
		return moveset.makeControlScheme("hotbar").bind(name, InputMethod.CLICK, InputKey.LMB).finalizeControlScheme()
				.makeControlScheme("keybinds").bind(name, InputMethod.CLICK, InputKey.LMB).finalizeControlScheme();
	}

	private static Object field(GameTestHelper helper, Object ability, String name) {
		try {
			Field field = StandEntityPunchAbility.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(ability);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot read " + name, e);
		}
	}

	private static void assertSameSettings(GameTestHelper helper, String stand, Ability punch, Ability member) {
		helper.assertTrue(member != null && member.abilityType == punch.abilityType && member.getClass() == punch.getClass(),
				stand + ": " + member + " is not of " + punch.abilityType.registryKey);
		for (String setting : List.of("punchImpactSound", "standCrySound", "detachedLightCounterKnockback", "laterPhaseEntryTiming")) {
			helper.assertTrue(java.util.Objects.equals(field(helper, punch, setting), field(helper, member, setting)),
					stand + " " + member.name() + " lost its punch's " + setting + ": " + field(helper, member, setting));
		}
		helper.assertTrue(member.usageGroup == punch.usageGroup, stand + " " + member.name() + " is in " + member.usageGroup);
		helper.assertTrue(member.getResolveLevelToUnlock() == punch.getResolveLevelToUnlock(),
				stand + " " + member.name() + " asks another Resolve level: " + member.getResolveLevelToUnlock());
	}

	private static float defaultWindup(GameTestHelper helper, Ability ability, Player user) {
		return ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), user, user, null).phasesLength.getFloat(ActionPhase.WINDUP);
	}

	private static String clipOf(GameTestHelper helper, Ability ability, Player user) {
		EntityActionAbility action = (EntityActionAbility) ability;
		return action.getEntityAnim(action.initActionOnAbilityUse(helper.getLevel(), user, user, null)).toString();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aStandsLeftClickPunchGetsTheComboWithItsOwnSettings(GameTestHelper helper) {
		List<Ability> initialized = new ArrayList<>();
		EntityStandType type = standOf(onTheLeftClick(new MovesetBuilder().addHumanoidStandStuff()
				.addAbility("x_punch", ModStandAbilities.PUNCH, punch -> {
					initialized.add(punch);
					punch.detachedLightCounterKnockback();
					punch.laterPhaseEntryTiming();
					punch.punchImpactSound(ModSoundEvents.THE_WORLD_PUNCH_LIGHT).standCrySound(ModSoundEvents.DIO_MUDA);
					punch.resolveLevelToUnlock(2);
				}), "x_punch"), "gametest_punch_combo");
		Moveset moveset = type.getBaseMoveset();
		Ability punch = moveset.getAbility("x_punch");
		helper.assertTrue(field(helper, punch, "punchImpactSound") == ModSoundEvents.THE_WORLD_PUNCH_LIGHT, "Fixture: the punch has not its sound");
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		for (int i = 2; i <= 4; i++) {
			Ability member = moveset.getAbility("x_punch" + i);
			assertSameSettings(helper, "the fixture", punch, member);
			helper.assertTrue(initialized.contains(member), "The punch's own declaration did not run on " + member.name());
			helper.assertTrue(member.isSubAbility && !member.addToControlSchemeEditing(), member.name() + " would show on the controls screen");
			helper.assertTrue(member.getTranslationKey().equals(punch.getTranslationKey()),
					member.name() + " goes by the name " + member.getTranslationKey() + ", where its language files have " + punch.getTranslationKey());
			helper.assertTrue(member.getSpriteId(null).equals(punch.getSpriteId(null)), member.name() + " has another icon: " + member.getSpriteId(null));
			helper.assertTrue(clipOf(helper, member, user).equals("x_punch" + (i - 1)), member.name() + " asks for the clip " + clipOf(helper, member, user));
		}
		helper.assertTrue(defaultWindup(helper, moveset.getAbility("x_punch3"), user) == 4 && defaultWindup(helper, moveset.getAbility("x_punch4"), user) == 5,
				"The fourth punch's longer windup, as the core declares it: third " + defaultWindup(helper, moveset.getAbility("x_punch3"), user)
						+ ", fourth " + defaultWindup(helper, moveset.getAbility("x_punch4"), user));
		helper.assertTrue(type.getUnlockableSkills().keySet().stream().noneMatch(skill -> skill.startsWith("x_punch")),
				"A combo punch brought a row on the skills screen: " + type.getUnlockableSkills().keySet());
		ControlSchemeTemplate scheme = type.makeDefaultControlSchemeTemplate();
		helper.assertTrue(scheme.defaultGroup.separateBinds.keySet().stream().noneMatch(bind -> bind.matches("x_punch\\d")),
				"A combo punch has a key: " + scheme.defaultGroup.separateBinds.keySet());
		helper.succeed();
	}

	// a constructor that fixes its clip's name (as Killer Queen the Lion's) still plays the four, and an own class is a family as the core's is
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aPunchOfAnOwnClassKeepsItAndItsClipsAndItsTiming(GameTestHelper helper) {
		Moveset moveset = standOf(onTheLeftClick(new MovesetBuilder().addHumanoidStandStuff().addAbility("own_punch", OWN_CLASS_PUNCH), "own_punch"),
				"gametest_own_class_combo").getBaseMoveset();
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		for (int i = 2; i <= 4; i++) {
			Ability member = moveset.getAbility("own_punch" + i);
			helper.assertTrue(member instanceof OwnClassPunch, "own_punch" + i + " is " + member);
			helper.assertTrue(clipOf(helper, member, user).equals("punch" + (i - 1)), member.name() + " asks for the clip " + clipOf(helper, member, user));
		}
		try {
			Method family = StandEntityPunchAbility.class.getDeclaredMethod("isSameLightComboFamily", Moveset.class,
					rotp.core.powersystem.entityaction.type.EntityActionType.class, rotp.core.powersystem.entityaction.type.EntityActionType.class);
			family.setAccessible(true);
			helper.assertTrue((boolean) family.invoke(null, moveset, moveset.getAbility("own_punch"), moveset.getAbility("own_punch3")),
					"The second punch of an own class would start with a first punch's windup");
		}
		catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			throw new IllegalStateException("Fixture: cannot ask the family", e);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theCoreStandsHaveItByTheRule(GameTestHelper helper) {
		for (String id : IN) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
			helper.assertTrue(type != null, "Missing Stand type " + id);
			Moveset moveset = type.getBaseMoveset();
			Ability punch = moveset.getAbility("punch");
			for (int i = 2; i <= 4; i++) {
				assertSameSettings(helper, id, punch, moveset.getAbility("punch" + i));
			}
			for (String skill : type.getUnlockableSkills().keySet()) {
				helper.assertTrue(!skill.matches("punch\\d"), id + " has a row for " + skill);
			}
		}
		for (String id : OUT) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
			helper.assertTrue(type != null, "Missing Stand type " + id);
			helper.assertTrue(type.getBaseMoveset().abilities.keySet().stream().noneMatch(name -> name.matches(".*(punch|attack)[234]")),
					id + " has a combo: " + type.getBaseMoveset().abilities.keySet());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void anythingElseOnTheKeyHasNoCombo(GameTestHelper helper) {
		List<MovesetBuilder> none = List.of(
				onTheLeftClick(new MovesetBuilder().addHumanoidStandStuff().addAbility("punch", ModStandAbilities.HEAVY_PUNCH), "punch"),
				onTheLeftClick(new MovesetBuilder().addAbility("punch", ModStandAbilities.PUNCH), "punch"),
				onTheLeftClick(new MovesetBuilder().addHumanoidStandStuff().noPunchCombo().addAbility("punch", ModStandAbilities.PUNCH), "punch"),
				new MovesetBuilder().addHumanoidStandStuff().addAbility("punch", ModStandAbilities.PUNCH)
						.makeControlScheme("hotbar").bind("punch", InputMethod.CLICK, InputKey.RMB).finalizeControlScheme(),
				new MovesetBuilder().addHumanoidStandStuff().addAbility("punch", ModStandAbilities.PUNCH)
						.makeControlScheme("hotbar").bind("punch", InputMethod.HOLD, InputKey.LMB).finalizeControlScheme(),
				new MovesetBuilder().addHumanoidStandStuff().addAbility("punch", ModStandAbilities.PUNCH)
						.makeControlScheme("hotbar").bind("punch", InputMethod.CLICK, InputKey.LMB.withModifier(InputKey.Modifier.SHIFT)).finalizeControlScheme());
		int i = 0;
		for (MovesetBuilder moveset : none) {
			Moveset built = standOf(moveset, "gametest_no_combo_" + i).getBaseMoveset();
			helper.assertTrue(built.getAbility("punch") != null && built.getAbility("punch2") == null, "Case " + i + " has a combo: " + built.abilities.keySet());
			i++;
		}
		helper.succeed();
	}

	// a moveset's own second punch stays what it declared, and only what is missing comes
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aPunchTheMovesetDeclaresStays(GameTestHelper helper) {
		Moveset built = standOf(onTheLeftClick(new MovesetBuilder().addHumanoidStandStuff()
				.addAbility("punch", ModStandAbilities.PUNCH, punch -> punch.punchImpactSound(ModSoundEvents.DIO_MUDA))
				.addAbility("punch2", ModStandAbilities.PUNCH, punch -> punch.punchImpactSound(ModSoundEvents.THE_WORLD_PUNCH_LIGHT)), "punch"),
				"gametest_own_second_punch").getBaseMoveset();
		helper.assertTrue(field(helper, built.getAbility("punch2"), "punchImpactSound") == ModSoundEvents.THE_WORLD_PUNCH_LIGHT,
				"A moveset's own punch2 was replaced");
		helper.assertTrue(field(helper, built.getAbility("punch3"), "punchImpactSound") == ModSoundEvents.DIO_MUDA, "The third punch is not made from the punch");
		StandType theWorld = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
		helper.assertTrue(field(helper, theWorld.getBaseMoveset().getAbility("punch3"), "standCrySound") == ModSoundEvents.DIO_MUDA,
				"The World's third punch does not cry as its first");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aConfigThatDisablesAPunchKeepsItDisabled(GameTestHelper helper) {
		EntityStandType type = standOf(onTheLeftClick(new MovesetBuilder().addHumanoidStandStuff().addAbility("punch", ModStandAbilities.PUNCH), "punch"),
				"gametest_combo_disabled");
		helper.assertTrue(type.getBaseMoveset().getAbility("punch3") != null, "Fixture: the moveset has no combo");
		JsonArray disable = new JsonArray();
		disable.add("punch3");
		JsonObject moveset = new JsonObject();
		moveset.add("disable", disable);
		JsonObject config = new JsonObject();
		config.add("moveset", moveset);
		type.applyConfig(config);
		helper.assertTrue(type.getBaseMoveset().getAbility("punch3") == null && type.getBaseMoveset().getAbility("punch2") != null,
				"A config could not disable a combo punch: " + type.getBaseMoveset().abilities.keySet());
		type.restoreDefaults();
		helper.assertTrue(type.getBaseMoveset().getAbility("punch3") != null, "The combo punch did not come back with the defaults");
		helper.succeed();
	}

	// what locks the punch locks the whole combo: it has no skill of its own, and a mob asks each member by itself
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aSkillThatLocksThePunchLocksItsCombo(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "magicians_red", scene -> {
			StandPower power = scene.power();
			helper.assertTrue(power.getAbility("punch3").isAbilityAvailable(power), "Fixture: the third punch is not available");
			power.getCurTypeData()._lockedAbilities.add("punch");
			for (String name : List.of("punch", "punch2", "punch3", "punch4")) {
				helper.assertTrue(!power.getAbility(name).isAbilityAvailable(power), name + " is available with the punch locked");
			}
			AvailableAbilities moves = power.updateAvailableMoves();
			helper.assertTrue(moves.getContextVariation("punch") == null && moves.getContextVariation("punch3") == null,
					"The locked punch is on the key: " + moves.getContextVariation("punch") + ", " + moves.getContextVariation("punch3"));
			power.getCurTypeData()._lockedAbilities.remove("punch");
			helper.assertTrue(power.getAbility("punch3").isAbilityAvailable(power), "The lock stayed on the third punch");
		});
		helper.succeed();
	}

	// Made in Heaven's punch has a cooldown(3): the rule's other punches must not walk round it
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aPunchsCooldownHoldsForItsCombo(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "magicians_red", scene -> {
			StandPower power = scene.power();
			Ability punch = power.getAbility("punch");
			Ability third = power.getAbility("punch3");
			punch.cooldown(30);
			third.cooldown(30);
			try {
				third.setCooldownOnUse(power);
				for (String name : List.of("punch", "punch2", "punch3", "punch4")) {
					helper.assertTrue(power.isAbilityOnCooldown(name), name + " is free in the cooldown of the third punch");
				}
				AvailableAbilities moves = power.updateAvailableMoves();
				helper.assertTrue(!moves.getConditionCheck("punch").isPositive(), "The key can still punch during the cooldown: "
						+ moves.getContextVariation("punch"));
			}
			finally {
				punch.cooldown(0);
				third.cooldown(0);
			}
		});
		helper.succeed();
	}

	// stand_default's four punches open on its own stance: in another Stand's file they start and end on that Stand's idle
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theClipsTheRuleBringsMeetTheStandsIdle(GameTestHelper helper) {
		float[] level = new float[2];
		for (String id : List.of("the_world", "gold_experience", "magicians_red")) {
			JsonObject anims = StandClipPoses116GameTests.readJson(helper, "stand_skins/" + id + "/assets/jojo_ripples/animations/" + id + ".animation.json")
					.getAsJsonObject("animations");
			JsonObject idle = anims.getAsJsonObject("idle");
			for (String name : List.of("punch2", "punch3", "punch4")) {
				helper.assertTrue(anims.has(name), id + " has no clip " + name);
				JsonObject clip = anims.getAsJsonObject(name);
				float end = clip.get("animation_length").getAsFloat();
				for (String bone : clip.getAsJsonObject("bones").keySet()) {
					if (bone.equals("head") || bone.equals("head_rot") || !clip.getAsJsonObject("bones").getAsJsonObject(bone).has("rotation")) {
						continue;
					}
					float[] want = StandClipPoses116GameTests.rotationAt(idle, bone, 0, level);
					for (float time : new float[] { 0, end }) {
						float[] at = StandClipPoses116GameTests.rotationAt(clip, bone, time, level);
						for (int axis = 0; axis < 3; axis++) {
							helper.assertTrue(Math.abs(at[axis] - want[axis]) <= 5.05F, id + " " + name + " " + bone + " at " + time + " s is "
									+ java.util.Arrays.toString(at) + ", its idle " + java.util.Arrays.toString(want));
						}
					}
				}
			}
		}
		helper.succeed();
	}

	/*
	 * The real presses of a player: damage, stamina, the phases and the order, on a Stand whose moveset declares one punch and on one that
	 * declares two. The fourth punch's longer windup is the ability's default, and initActionFromConfig replaces it on a Stand as on the others.
	 */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void everyPunchStrikesAsTheSinglePunchMagiciansRed(GameTestHelper helper) {
		strikes(helper, "magicians_red");
	}

	@GameTest(template = "empty", timeoutTicks = 400)
	public static void everyPunchStrikesAsTheSinglePunchTheWorld(GameTestHelper helper) {
		strikes(helper, "the_world");
	}

	private static final class HitCow extends Cow {
		final List<Float> hits = new ArrayList<>();

		HitCow(GameTestHelper helper) {
			super(EntityType.COW, helper.getLevel());
		}

		@Override
		public boolean hurt(DamageSource source, float amount) {
			// what the punch sets alight burns on its own, as it did before the combo
			if (source.getDirectEntity() != null) {
				hits.add(amount);
			}
			invulnerableTime = 0;
			return super.hurt(source, amount);
		}
	}

	private static final class Rig {
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final HitCow cow;
		float staminaBefore;

		Rig(GameTestHelper helper, String id) {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 origin = helper.absoluteVec(new Vec3(2.5D, 40.0D, 2.5D));
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user");
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant " + id);
			power.setResolveLevel(2);
			helper.assertTrue(type.summon(user, power), "Could not summon " + id);
			stand = power.getSummonedStandEntity();
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
			cow = new HitCow(helper);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + 1.5D);
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
			cow.setHealth(1000);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add the target");
		}

		/** One click of the punch key as a player makes it: the move the moveset puts on the key now. */
		EntityActionInstance press(GameTestHelper helper, String id) {
			LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
			user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
			stand.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
			stand.setFinisherMeter(0);
			power.setStamina(power.getMaxStamina());
			staminaBefore = power.getStamina();
			StandEntity.updateStandStatAttributes(stand, user);
			stand.updateStrengthMultipliers();
			AbilityInput.keyRelease(KEY, user);
			Ability base = power.getAbility("punch");
			var selected = power.updateAvailableMoves().getContextVariationContainer(base);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK), "The press was refused on " + id);
			BufferingState state = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, state, base.getAbilityId());
			helper.assertTrue(state.isActionSuccess, "The press did not go off on " + id);
			return stand.getCurStandAction();
		}

		void close() {
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			cow.discard();
			user.discard();
		}
	}

	private static void strikes(GameTestHelper helper, String id) {
		Rig rig = new Rig(helper, id);
		List<String> order = new ArrayList<>();
		List<Float> windups = new ArrayList<>();
		List<Float> performs = new ArrayList<>();
		List<Float> recoveries = new ArrayList<>();
		List<Float> staminaSpent = new ArrayList<>();
		EntityActionInstance[] watched = new EntityActionInstance[1];
		boolean[] finished = new boolean[1];
		helper.onEachTick(() -> {
			if (finished[0]) {
				return;
			}
			EntityActionInstance current = rig.stand.getCurStandAction();
			boolean struck = watched[0] != null && watched[0].getPhase() == ActionPhase.RECOVERY;
			if (struck && staminaSpent.size() < order.size()) {
				staminaSpent.add(rig.staminaBefore - rig.power.getStamina());
			}
			if (watched[0] != null && !struck && current == watched[0]) {
				return;
			}
			if (order.size() == 5 && struck) {
				finished[0] = true;
				try {
					checkStrikes(helper, id, rig.stand, rig.cow, order, windups, performs, recoveries, staminaSpent);
				}
				finally {
					rig.close();
				}
				helper.succeed();
				return;
			}
			watched[0] = rig.press(helper, id);
			order.add(((Ability) watched[0].ability).name());
			windups.add(watched[0].phasesLength.getFloat(ActionPhase.WINDUP));
			performs.add(watched[0].phasesLength.getFloat(ActionPhase.PERFORM));
			recoveries.add(watched[0].phasesLength.getFloat(ActionPhase.RECOVERY));
		});
	}

	/**
	 * Presses by the word, one letter a press: Q within the combo window, in the recovery of the punch before, and S once that punch has
	 * ended and the Stand stands idle. The punches played are checked against want. Before sets the scene up for press n (from 0).
	 */
	private static void presses(GameTestHelper helper, String id, String word, List<String> want, BiConsumer<Rig, Integer> before) {
		Rig rig = new Rig(helper, id);
		List<String> order = new ArrayList<>();
		EntityActionInstance[] watched = new EntityActionInstance[1];
		boolean[] finished = new boolean[1];
		helper.onEachTick(() -> {
			if (finished[0]) {
				return;
			}
			try {
				if (order.size() == word.length()) {
					finished[0] = true;
					helper.assertTrue(order.equals(want), id + " pressed " + word + " and played " + order + ", not " + want);
					rig.close();
					helper.succeed();
					return;
				}
				EntityActionInstance current = rig.stand.getCurStandAction();
				boolean quick = word.charAt(order.size()) == 'Q';
				if (watched[0] != null) {
					helper.assertTrue(!quick || current == watched[0], id + " missed the combo window before press " + (order.size() + 1) + " of " + word);
					if (quick ? watched[0].getPhase() != ActionPhase.RECOVERY : current != null) {
						return;
					}
				}
				before.accept(rig, order.size());
				watched[0] = rig.press(helper, id);
				order.add(((Ability) watched[0].ability).name());
			}
			catch (RuntimeException | Error error) {
				finished[0] = true;
				rig.close();
				throw error;
			}
		});
	}

	// slow presses are one of the first two punches, taking turns, however long the wait; a Stand that declares one punch gets it from the rule
	@GameTest(template = "empty", timeoutTicks = 600)
	public static void slowPressesTakeTurnsOnTheFirstTwoPunchesMagiciansRed(GameTestHelper helper) {
		presses(helper, "magicians_red", "SSSSS", List.of("punch", "punch2", "punch", "punch2", "punch"), (rig, press) -> {});
	}

	@GameTest(template = "empty", timeoutTicks = 600)
	public static void slowPressesTakeTurnsOnTheFirstTwoPunchesTheWorld(GameTestHelper helper) {
		presses(helper, "the_world", "SSSSS", List.of("punch", "punch2", "punch", "punch2", "punch"), (rig, press) -> {});
	}

	// the third and fourth punch come only to quick presses; after the fourth the chain starts again at the first
	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aQuickChainWalksAllFourPunchesAndWraps(GameTestHelper helper) {
		presses(helper, "magicians_red", "SQQQQQQ", List.of("punch", "punch2", "punch3", "punch4", "punch", "punch2", "punch3"), (rig, press) -> {});
	}

	// a quick press after a slow punch gives the second, after a slow second punch the third
	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aQuickPressContinuesWhereTheSlowOneBegan(GameTestHelper helper) {
		presses(helper, "the_world", "SSSQSSQQ", List.of("punch", "punch2", "punch", "punch2", "punch", "punch2", "punch3", "punch4"), (rig, press) -> {});
	}

	// Star Platinum declares all four itself; a chain that ended on the fourth, then slow presses: the second punch turns the arm, then they alternate from it
	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aSlowPressAfterTheFourthPunchPlaysTheSecond(GameTestHelper helper) {
		presses(helper, "star_platinum", "SQQQSSS", List.of("punch", "punch2", "punch3", "punch4", "punch2", "punch", "punch2"), (rig, press) -> {});
	}

	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aSlowPressAfterTheThirdPunchPlaysTheSecond(GameTestHelper helper) {
		presses(helper, "the_world", "SQQSS", List.of("punch", "punch2", "punch3", "punch2", "punch"), (rig, press) -> {});
	}

	// with the second punch locked, a slow press after the third or fourth plays the first
	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aSlowPressAfterTheLaterPunchesPlaysTheFirstWhenTheSecondIsLocked(GameTestHelper helper) {
		presses(helper, "the_world", "SQQSQS", List.of("punch", "punch3", "punch4", "punch", "punch3", "punch"),
				(rig, press) -> rig.power.getCurTypeData()._lockedAbilities.add("punch2"));
	}

	// a locked second punch leaves the slow presses on the first; the third and fourth locked, the Stand has two punches and they alternate
	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aLockedSecondPunchKeepsSlowPressesOnTheFirst(GameTestHelper helper) {
		presses(helper, "the_world", "SSS", List.of("punch", "punch", "punch"),
				(rig, press) -> rig.power.getCurTypeData()._lockedAbilities.add("punch2"));
	}

	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aStandWithTwoPunchesAlternatesThem(GameTestHelper helper) {
		presses(helper, "the_world", "SQQSSQ", List.of("punch", "punch2", "punch", "punch2", "punch", "punch2"), (rig, press) -> {
			rig.power.getCurTypeData()._lockedAbilities.add("punch3");
			rig.power.getCurTypeData()._lockedAbilities.add("punch4");
		});
	}

	// a chain that ended on the third punch with the second locked: once the second is free, the slow press plays it
	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aSlowPressTakesTheOneOfTheFirstTwoNotPlayedLast(GameTestHelper helper) {
		presses(helper, "the_world", "SQS", List.of("punch", "punch3", "punch2"), (rig, press) -> {
			if (press == 0) {
				rig.power.getCurTypeData()._lockedAbilities.add("punch2");
			}
			else if (press == 2) {
				rig.power.getCurTypeData()._lockedAbilities.remove("punch2");
			}
		});
	}

	private static void checkStrikes(GameTestHelper helper, String id, StandEntity stand, HitCow cow, List<String> order, List<Float> windups, List<Float> performs,
			List<Float> recoveries, List<Float> staminaSpent) {
		helper.assertTrue(order.equals(List.of("punch", "punch2", "punch3", "punch4", "punch")), id + " pressed " + order);
		float damage = StandStatFormulas.getLightAttackDamage(stand.getAttackDamage());
		helper.assertTrue(cow.hits.size() == 5 && cow.hits.stream().allMatch(hit -> hit == damage), id + " struck " + cow.hits + " for " + damage + " a click");
		float windup = StandStatFormulas.getLightAttackWindup(stand.getAttackSpeed(), 0, 0, false);
		helper.assertTrue(windups.subList(1, 5).stream().allMatch(length -> length == windup), id + " windups " + windups + " for " + windup);
		helper.assertTrue(performs.stream().distinct().count() == 1, id + " performs " + performs);
		helper.assertTrue(recoveries.stream().distinct().count() == 1, id + " recoveries " + recoveries);
		helper.assertTrue(staminaSpent.size() == 5 && staminaSpent.stream().allMatch(spent -> spent == 10F), id + " stamina a click " + staminaSpent);
	}
}
