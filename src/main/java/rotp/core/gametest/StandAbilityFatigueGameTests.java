package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.JojoModConfig;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.AbilityFatigueOverrides;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fatigue of the no-cooldown mode (the owner, 2026-11-11; StandAbilityFatigue): in a player's no-cooldown mode an
 * ability used again before the cooldown it skipped would have run out costs extra stamina, derived from its normal
 * cooldown, gentle for the first repeats and gentler still for a small practical skill, and enough under spam to
 * outweigh the regeneration meanwhile; only the same ability counts; the fatigue runs down at its user's rate of Stand
 * actions; a Stand acting keeps a share of its user's regeneration; creatures keep their cooldowns. The users are fake
 * players, whose Stand power still ticks once a game tick (the entity tick event): a test reads the fatigue left in the
 * same tick as the use it prices, and ticks the power itself where more time has to pass.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAbilityFatigueGameTests {
	private static final float EPS = 0.01F;
	private static final String BIND = "string_bind";
	private static final String SPLASH = "emerald_splash_concentrated";
	private static final String FAST = "jojo_ripples.gametest.fatigue_fast";
	private static final String SLOW = "jojo_ripples.gametest.fatigue_slow";
	private static final AtomicBoolean RATES_REGISTERED = new AtomicBoolean();

	private StandAbilityFatigueGameTests() {}

	// F2: the extra cost and the count belong to the ability repeated; another ability's use leaves them alone
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void onlyARepeatOfTheSameAbilityPays(GameTestHelper helper) {
		Users users = new Users(helper);
		StandPower power = users.player("hierophant_green", true);
		Ability bind = users.ability(power, BIND);
		Ability splash = users.ability(power, SPLASH);
		float[] bar = new float[1];
		users.run(
				() -> {
					bar[0] = power.getStamina();
					bind.setCooldownOnUse(power);
					helper.assertTrue(!power.isAbilityOnCooldown(BIND),
							"the no-cooldown mode set the string bind a cooldown of " + power.getAbilityCooldown(BIND));
					helper.assertTrue(near(power.getStamina(), bar[0]), "a first use paid " + (bar[0] - power.getStamina()));
					checkFatigue(helper, power, BIND, 125.0F, 0);
				},
				() -> {
					float bindLeft = StandAbilityFatigue.getFatigueTicks(power, BIND);
					splash.setCooldownOnUse(power);
					helper.assertTrue(near(power.getStamina(), bar[0]),
							"another ability's first use paid " + (bar[0] - power.getStamina())
									+ " while the string bind's fatigue ran: the owner counts the same ability only");
					checkFatigue(helper, power, SPLASH, 65.0F, 0);
					checkFatigue(helper, power, BIND, bindLeft, 0);
				},
				() -> {
					float bindLeft = StandAbilityFatigue.getFatigueTicks(power, BIND);
					float splashLeft = StandAbilityFatigue.getFatigueTicks(power, SPLASH);
					bind.setCooldownOnUse(power);
					float expected = power.getStaminaCostShare(7.5F * Math.min(bindLeft, 125.0F) / 16.0F);
					helper.assertTrue(bindLeft > 0.0F && near(bar[0] - power.getStamina(), expected), "the string bind's first repeat"
							+ " with " + bindLeft + " ticks of fatigue left paid " + (bar[0] - power.getStamina()) + ", not " + expected);
					checkFatigue(helper, power, BIND, bindLeft + 125.0F, 1);
					checkFatigue(helper, power, SPLASH, splashLeft, 0);
					CompoundTag saved = power.serializeNBT(helper.getLevel().registryAccess())
							.getCompound("AbilityFatigue").getCompound(BIND);
					helper.assertTrue(near(saved.getFloat("Ticks"), bindLeft + 125.0F) && saved.getInt("Cooldown") == 125
							&& saved.getInt("Repeats") == 1, "the Stand power saved the string bind's fatigue as " + saved);
				});
	}

	// F3: the fatigue a use adds is the cooldown the normal mode sets, and a repeat's price follows it
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void fatigueAndItsRepeatCostFollowTheNormalCooldown(GameTestHelper helper) {
		Users users = new Users(helper);
		List<Use> uses = List.of(
				new Use(users.player("crazy_diamond", true), "blood_cutter", 300),
				new Use(users.player("hierophant_green", true), BIND, 125),
				new Use(users.player("star_platinum", true), "star_finger", 80),
				new Use(users.player("hierophant_green", true), SPLASH, 65),
				new Use(users.player("the_world", true), "ts_punch", 50));
		StandPower plain = users.player("hierophant_green", true);
		// the owner's decision of 2026-11-11: no x3 on the time stop in the mode, a stop costs its normal price, and a full
		// repeat that skips a whole full stop's cooldown pays what the former x3 added to that stop
		float normalStop = TimeStopLearning.BASE_STAMINA_COST
				+ TimeStopLearning.BASE_STAMINA_COST_TICK * TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS;
		float formerSurcharge = (3.0F - 1.0F) * normalStop;
		float calibration = StandAbilityFatigue.repeatCost(3 * TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS,
				3 * TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS, StandAbilityFatigue.FULL_COST_REPEAT, false);
		users.run(
				() -> {
					float multiplier = PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(uses.get(0).power());
					helper.assertTrue(multiplier == 1.0F && near(calibration, formerSurcharge), "in the no-cooldown mode a time stop"
							+ " costs x" + multiplier + " (the normal " + normalStop + " for a full one) and a full repeat that skips a"
							+ " full stop's cooldown " + calibration + ", the former x3's " + formerSurcharge);
					for (Use use : uses) {
						int normal = use.ability(users).getNormalCooldown(use.power(), -1);
						helper.assertTrue(normal == use.cooldown(), "setup: " + use.name() + "'s normal cooldown is " + normal);
						use.ability(users).setCooldownOnUse(use.power());
						checkFatigue(helper, use.power(), use.name(), use.cooldown(), 0);
					}
					for (String none : new String[] { "emerald_splash", "string_attack" }) {
						users.ability(plain, none).setCooldownOnUse(plain);
						helper.assertTrue(StandAbilityFatigue.getFatigueTicks(plain, none) == 0.0F,
								none + ", which has no cooldown, took fatigue " + StandAbilityFatigue.getFatigueTicks(plain, none));
					}
				},
				() -> {
					float previous = Float.MAX_VALUE;
					for (Use use : uses) {
						float bar = use.power().getStamina();
						float left = StandAbilityFatigue.getFatigueTicks(use.power(), use.name());
						use.ability(users).setCooldownOnUse(use.power());
						float paid = bar - use.power().getStamina();
						float expected = use.power().getStaminaCostShare(7.5F * Math.min(left, use.cooldown()) / 16.0F);
						helper.assertTrue(left > use.cooldown() - 3 && near(paid, expected) && paid < previous, use.name()
								+ "'s first repeat with " + left + " ticks left paid " + paid + ", not 7.5 x min(left, its "
								+ use.cooldown() + "-tick cooldown) / 16 = " + expected + " below the stronger ability before it ("
								+ previous + ")");
						previous = paid;
					}
				});
	}

	// F4: the first repeats cost 1/16, 1/8, 1/4 and 1/2 of the full repeat; a practical skill's a quarter of that
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theFirstRepeatsCostLittleAndAPracticalSkillLess(GameTestHelper helper) {
		Users users = new Users(helper);
		StandPower power = users.player("hierophant_green", true);
		StandPower normal = users.player("hierophant_green", true);
		StandPower practical = users.player("hierophant_green", true);
		Ability bind = users.ability(power, BIND);
		Ability splash = users.ability(normal, SPLASH);
		float full = power.getStaminaCostShare(7.5F * 125.0F);
		List<Float> paid = new ArrayList<>();
		List<Float> skipped = new ArrayList<>();
		List<Runnable> steps = new ArrayList<>();
		for (int use = 0; use <= StandAbilityFatigue.FULL_COST_REPEAT; use++) {
			steps.add(() -> {
				power.setStamina(power.getMaxStamina());
				skipped.add(Math.min(StandAbilityFatigue.getFatigueTicks(power, BIND), 125.0F));
				bind.setCooldownOnUse(power);
				paid.add(power.getMaxStamina() - power.getStamina());
			});
		}
		steps.add(() -> {
			float[] shares = { 0.0F, 1.0F / 16.0F, 1.0F / 8.0F, 1.0F / 4.0F, 1.0F / 2.0F, 1.0F };
			for (int repeat = 0; repeat < shares.length; repeat++) {
				float expected = power.getStaminaCostShare(7.5F * skipped.get(repeat) * shares[repeat]);
				helper.assertTrue(near(paid.get(repeat), expected), "repeat " + repeat + " of the string bind skipping "
						+ skipped.get(repeat) + " ticks paid " + paid.get(repeat) + ", not " + shares[repeat] + " of the full "
						+ full + ": " + paid);
			}
			helper.assertTrue(paid.get(1) < 0.05F * power.getMaxStamina() && paid.get(1) + paid.get(2) + paid.get(3) < full / 2.0F,
					"the first three repeats " + paid.subList(1, 4) + " are no small penalty next to the bar "
							+ power.getMaxStamina() + " and the full repeat " + full);
			splash.setCooldownOnUse(normal);
			splash.setCooldownOnUse(practical);
		});
		steps.add(() -> {
			float left = StandAbilityFatigue.getFatigueTicks(normal, SPLASH);
			float normalPaid = StandAbilityFatigue.chargeRepeat(normal, splash, false);
			float practicalPaid = StandAbilityFatigue.chargeRepeat(practical, splash, true);
			helper.assertTrue(left > 0.0F && near(normalPaid, 7.5F * Math.min(left, 65.0F) / 16.0F)
					&& near(practicalPaid, normalPaid / 4.0F), "a practical skill's first repeat paid " + practicalPaid
							+ ", a plain one's " + normalPaid + " with " + left + " ticks left: the practical one pays a quarter");
		});
		users.run(steps.toArray(Runnable[]::new));
	}

	/*
	 * F5: a cheap ability (the concentrated Emerald Splash, 65 ticks) and a strong one (Crazy Diamond's Blood Cutter,
	 * 300) used at twice their normal pace, 8 uses: from the 4th repeat on each repeat costs more than the user regains
	 * meanwhile (his Stand put away, the fastest regeneration), and all repeats together more than all he regains.
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void spammingOutweighsTheRegenerationMeanwhile(GameTestHelper helper) {
		Users users = new Users(helper);
		List<Spam> spams = List.of(new Spam(users, users.player("hierophant_green", true), SPLASH, 65),
				new Spam(users, users.player("crazy_diamond", true), "blood_cutter", 300));
		List<Runnable> steps = new ArrayList<>();
		for (int use = 0; use < 8; use++) {
			steps.add(() -> spams.forEach(Spam::next));
		}
		steps.add(() -> spams.forEach(spam -> spam.check(helper)));
		users.run(steps.toArray(Runnable[]::new));
	}

	// F6: in the no-cooldown mode a Stand acting leaves its user a share of his regeneration, where 1.16 left him none
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aStandActingLeavesAShareOfTheRegenerationInTheNoCooldownMode(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		LivingComponentAction component = null;
		try {
			Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
			PlayerClientBroadcastedSettings settings = user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			settings.noStandAbilityCooldown = true;
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give Star Platinum");
			helper.assertTrue(type.summon(user, power), "setup: could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the summoned Star Platinum is missing");
			stand.summonLockTicks = 0;
			float idle = power.getStaminaTickGain();
			component = LivingComponentAction.getComponent(stand);
			EntityActionInstance barrage = ((EntityActionAbility) power.getAbility("barrage"))
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			component.setAction(barrage, user, SyncType.NO_SYNC);
			helper.assertTrue(idle > 0.0F && LivingComponentAction.getCurEntityAction(stand) == barrage,
					"setup: an idle Stand regenerates " + idle + " and its barrage is " + LivingComponentAction.getCurEntityAction(stand));
			float acting = power.getStaminaTickGain();
			settings.noStandAbilityCooldown = false;
			float actingNormal = power.getStaminaTickGain();
			helper.assertTrue(near(acting, idle * StandAbilityFatigue.SPECIAL_STATE_REGEN_FACTOR) && actingNormal == 0.0F,
					"a barraging Star Platinum regenerates " + acting + " a tick in the no-cooldown mode, not "
							+ StandAbilityFatigue.SPECIAL_STATE_REGEN_FACTOR + " of its idle " + idle + ", and " + actingNormal
							+ " in the normal mode, not 0 as in 1.16");
		}
		finally {
			if (component != null) {
				component.setAction(null, user, SyncType.NO_SYNC);
			}
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.succeed();
	}

	// F7: fatigue runs down by the user's STAND_ACTIONS rate, as his cooldowns do; run out, the count starts again
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void fatigueRunsDownAtItsUsersRateOfStandActions(GameTestHelper helper) {
		registerRates();
		Users users = new Users(helper);
		StandPower fast = users.player("hierophant_green", true);
		StandPower slow = users.player("hierophant_green", true);
		StandPower plain = users.player("hierophant_green", true);
		fast.getUser().addTag(FAST);
		slow.getUser().addTag(SLOW);
		Ability bind = users.ability(plain, BIND);
		users.run(
				() -> {
					for (StandPower power : List.of(fast, slow, plain)) {
						bind.setCooldownOnUse(power);
						for (int tick = 0; tick < 20; tick++) {
							power.tick();
						}
					}
					float fastLeft = StandAbilityFatigue.getFatigueTicks(fast, BIND);
					float slowLeft = StandAbilityFatigue.getFatigueTicks(slow, BIND);
					float plainLeft = StandAbilityFatigue.getFatigueTicks(plain, BIND);
					helper.assertTrue(near(plainLeft, 105.0F) && near(fastLeft, 85.0F) && near(slowLeft, 115.0F),
							"20 ticks after a 125-tick string bind its fatigue left " + plainLeft + " at rate 1 (105), "
									+ fastLeft + " at rate 2 (85), " + slowLeft + " at rate 0.5 (115)");
					for (int tick = 0; tick < 43; tick++) {
						fast.tick();
					}
					helper.assertTrue(StandAbilityFatigue.getFatigueTicks(fast, BIND) == 0.0F,
							"at rate 2 the fatigue lasted past 63 ticks: " + StandAbilityFatigue.getFatigueTicks(fast, BIND));
					fast.setStamina(fast.getMaxStamina());
				},
				() -> {
					bind.setCooldownOnUse(fast);
					helper.assertTrue(fast.getStamina() == fast.getMaxStamina() && StandAbilityFatigue.getRepeats(fast, BIND) == 0,
							"a use after the fatigue ran out paid " + (fast.getMaxStamina() - fast.getStamina()) + " as repeat "
									+ StandAbilityFatigue.getRepeats(fast, BIND));
				});
	}

	// F9 (the owner's C9): a creature's Stand keeps its cooldowns and takes no fatigue; a player in the mode the reverse
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void creaturesKeepTheirCooldownsAndTakeNoFatigue(GameTestHelper helper) {
		Users users = new Users(helper);
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create the zombie");
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		users.add(zombie);
		StandPower creature = users.give(zombie, "star_platinum");
		StandPower player = users.player("star_platinum", true);
		Ability finger = users.ability(player, "star_finger");
		users.run(() -> {
			finger.setCooldownOnUse(creature);
			finger.setCooldownOnUse(player);
			helper.assertTrue(!PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(creature)
					&& creature.getAbilityCooldown("star_finger") == 80
					&& StandAbilityFatigue.getFatigueTicks(creature, "star_finger") == 0.0F,
					"a zombie's Star Finger took cooldown " + creature.getAbilityCooldown("star_finger") + " and fatigue "
							+ StandAbilityFatigue.getFatigueTicks(creature, "star_finger") + ": creatures keep the cooldown mode");
			helper.assertTrue(!player.isAbilityOnCooldown("star_finger")
					&& near(StandAbilityFatigue.getFatigueTicks(player, "star_finger"), 80.0F),
					"a player in the no-cooldown mode took cooldown " + player.getAbilityCooldown("star_finger") + " and fatigue "
							+ StandAbilityFatigue.getFatigueTicks(player, "star_finger"));
		});
	}

	// item 3 of the review: the server's table hand-sets an ability's tier or makes it practical, by Stand id and name
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theServersTableSetsAnAbilitysTierOrMakesItPractical(GameTestHelper helper) {
		Users users = new Users(helper);
		StandPower power = users.player("star_platinum", true);
		Ability finger = users.ability(power, "star_finger");
		String key = power.getPowerType().getId() + "/star_finger";
		List<String> table = List.of(key + "=300", key + "=" + AbilityFatigueOverrides.PRACTICAL);
		users.run(
				() -> {
					List<? extends String> configured = JojoModConfig.getCommonConfigInstance(false).standAbilityFatigueOverrides.get();
					helper.assertTrue(AbilityFatigueOverrides.parse(AbilityFatigueOverrides.DEFAULTS).size()
							== AbilityFatigueOverrides.DEFAULTS.size() && configured.equals(AbilityFatigueOverrides.DEFAULTS),
							"the server's table does not carry the defaults, each a valid line: " + configured);
					AbilityFatigueOverrides.runWithTable(table, () -> {
						finger.setCooldownOnUse(power);
						checkFatigue(helper, power, "star_finger", 300.0F, 0);
					});
				},
				() -> AbilityFatigueOverrides.runWithTable(table, () -> {
					float left = StandAbilityFatigue.getFatigueTicks(power, "star_finger");
					float bar = power.getStamina();
					finger.setCooldownOnUse(power);
					float paid = bar - power.getStamina();
					float expected = power.getStaminaCostShare(7.5F * Math.min(left, 300.0F) / 64.0F);
					helper.assertTrue(near(paid, expected), "Star Finger, set to 300 ticks and practical, paid " + paid
							+ " for its first repeat with " + left + " ticks left, not 7.5 x min(left, 300) / 64 = " + expected);
				}));
	}

	/*
	 * Item 4 of the review, in the shape of Made in Heaven's Universe Reset, whose action sets the time system's cooldown
	 * for 60 s: a cooldown one ability's action sets on another is a lock, no use of it; its own counts; a cooldown set
	 * from no ability's use (an entity, an effect) still counts for the ability it is set on.
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aCooldownAnotherAbilitysActionSetsIsNoUseOfIt(GameTestHelper helper) {
		Users users = new Users(helper);
		StandPower power = users.player("star_platinum", true);
		LivingEntity user = power.getUser();
		EntityActionAbility reset = new EntityActionAbility(ModStandAbilities.GUARD.get(),
				new AbilityId(PowerClass.STAND, power.getPowerType().getId(), RESET), ResetAction::new) {
			{
				setDefaultPhaseLength(ActionPhase.WINDUP, 0);
				setDefaultPhaseLength(ActionPhase.PERFORM, 1);
				setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
			}
		};
		users.run(() -> {
			LivingComponentAction slot = LivingComponentAction.getComponent(user, PowerClass.STAND);
			EntityActionInstance action = reset.initActionOnAbilityUse(helper.getLevel(), user, user, null);
			slot.setAction(action, user, SyncType.NO_SYNC);
			try {
				for (int tick = 0; tick < 10 && !((ResetAction) action).performed; tick++) {
					slot.tick();
				}
				helper.assertTrue(((ResetAction) action).performed, "setup: the reset's action did not perform: " + action.getPhase());
			}
			finally {
				slot.setAction(null, user, SyncType.NO_SYNC);
			}
			float locked = StandAbilityFatigue.getFatigueTicks(power, TIME_SYSTEM);
			helper.assertTrue(power.getAbilityCooldown(TIME_SYSTEM) == 1200 && locked == 0.0F,
					"the reset's action set the time system's cooldown " + power.getAbilityCooldown(TIME_SYSTEM)
							+ " and fatigued it " + locked + " ticks: a lock another ability sets is no use of it");
			checkFatigue(helper, power, RESET, 200.0F, 0);
			power.setAbilityCooldown(TIME_SYSTEM, 1300);
			checkFatigue(helper, power, TIME_SYSTEM, 1300.0F, 0);
		});
	}

	private static final String RESET = "universe_reset";
	private static final String TIME_SYSTEM = "time_system";

	private static final class ResetAction extends EntityActionInstance {
		private boolean performed;

		ResetAction(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			performed = true;
			StandPower power = StandPower.get(getPowerUser());
			power.setAbilityCooldown(TIME_SYSTEM, 1200);
			power.setAbilityCooldown(RESET, 200);
		}
	}

	private static void registerRates() {
		if (!RATES_REGISTERED.compareAndSet(false, true)) {
			return;
		}
		EntityTimeRates.register(JojoMod.resLoc("gametest_fatigue_rate"), (entity, channel) -> {
			if (channel != EntityTimeRates.Channel.STAND_ACTIONS) {
				return EntityTimeRates.NO_RATE;
			}
			return entity.getTags().contains(FAST) ? 2.0F : entity.getTags().contains(SLOW) ? 0.5F : EntityTimeRates.NO_RATE;
		});
	}

	private static void checkFatigue(GameTestHelper helper, StandPower power, String name, float ticks, int repeats) {
		float fatigue = StandAbilityFatigue.getFatigueTicks(power, name);
		int counted = StandAbilityFatigue.getRepeats(power, name);
		helper.assertTrue(near(fatigue, ticks) && counted == repeats, name + "'s fatigue is " + fatigue + " ticks after "
				+ counted + " repeats, not " + ticks + " after " + repeats);
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < EPS;
	}

	private record Use(StandPower power, String name, int cooldown) {
		Ability ability(Users users) {
			return users.ability(power, name);
		}
	}

	// one use every half cooldown, the power ticked in between
	private static final class Spam {
		private final Users users;
		private final StandPower power;
		private final String name;
		private final int interval;
		private final List<Float> extras = new ArrayList<>();
		private final List<Float> regained = new ArrayList<>();
		private long lastUse;

		Spam(Users users, StandPower power, String name, int cooldown) {
			this.users = users;
			this.power = power;
			this.name = name;
			this.interval = cooldown / 2;
		}

		// the power's own tick of each game tick counts with the ticks given here
		void next() {
			long now = power.getUser().level().getGameTime();
			if (!extras.isEmpty()) {
				float gain = power.getStaminaTickGain();
				for (int tick = 0; tick < interval; tick++) {
					power.tick();
				}
				regained.add(gain * (interval + now - lastUse));
			}
			lastUse = now;
			power.setStamina(power.getMaxStamina());
			users.ability(power, name).setCooldownOnUse(power);
			extras.add(power.getMaxStamina() - power.getStamina());
		}

		void check(GameTestHelper helper) {
			float paid = 0.0F;
			float gained = 0.0F;
			for (int repeat = 1; repeat < extras.size(); repeat++) {
				paid += extras.get(repeat);
				gained += regained.get(repeat - 1);
				helper.assertTrue(repeat < 4 || extras.get(repeat) > regained.get(repeat - 1),
						name + "'s repeat " + repeat + " every " + interval + " ticks cost " + extras.get(repeat) + ", less than the "
								+ regained.get(repeat - 1) + " regained meanwhile: " + extras + " vs " + regained);
			}
			String numbers = String.format(Locale.ROOT, "%s every %d ticks: extra %s, regained %s, total %.1f vs %.1f", name,
					interval, extras, regained, paid, gained);
			JojoMod.getLogger().info("Fatigue spam: {}", numbers);
			helper.assertTrue(paid > gained, "spamming did not outweigh the regeneration: " + numbers);
		}
	}

	private static final class Users {
		private final GameTestHelper helper;
		private final List<Entity> entities = new ArrayList<>();

		Users(GameTestHelper helper) {
			this.helper = helper;
		}

		StandPower player(String stand, boolean noCooldown) {
			FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FatigueTest"));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
			entities.add(user);
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = noCooldown;
			StandPower power = give(user, stand);
			helper.assertTrue(StandAbilityFatigue.applies(power) == noCooldown,
					"setup: the no-cooldown setting " + noCooldown + " did not reach the fatigue");
			return power;
		}

		void add(LivingEntity entity) {
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			entity.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(entity), "setup: could not add " + entity);
			entities.add(entity);
		}

		StandPower give(LivingEntity user, String stand) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(stand));
			StandPower power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give " + stand);
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(!power.isUserCreative() && !power.isStaminaInfinite() && power.getStamina() > 0.0F,
					"setup: the " + stand + " user's stamina is not counted");
			return power;
		}

		Ability ability(StandPower power, String name) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability != null, "setup: " + power.getPowerType().getId() + " has no " + name);
			return ability;
		}

		// one step a tick, so that each step's uses fall in a game tick of their own
		void run(Runnable... steps) {
			step(steps, 0);
		}

		private void step(Runnable[] steps, int index) {
			try {
				steps[index].run();
			}
			catch (RuntimeException | Error error) {
				close();
				throw error;
			}
			if (index + 1 < steps.length) {
				helper.runAfterDelay(1, () -> step(steps, index + 1));
			}
			else {
				close();
				helper.succeed();
			}
		}

		void close() {
			entities.forEach(Entity::discard);
			entities.clear();
		}
	}
}
