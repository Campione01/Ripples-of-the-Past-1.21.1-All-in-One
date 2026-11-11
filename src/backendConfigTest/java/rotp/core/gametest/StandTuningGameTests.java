package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.timestop.TimeStopTicksModifiers;
import rotp.core.config.editor.StandConfigCatalog;
import rotp.core.config.editor.StandConfigKeys;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceEntityLifeshotAbility;
import rotp.core.impl.stands.hierophant.HierophantGreenEntity;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandSpecials;
import rotp.core.powersystem.standpower.StandSpecials.Special;
import rotp.core.powersystem.standpower.StandStats.StatWithValue;
import rotp.core.powersystem.standpower.StandTuning;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandLinkDamageSource;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.util.functions.DamageUtil;

/**
 * Each value of a Stand's block does what it says in play, for a core Stand and for an add-on's (the stand-in, which has no
 * code of its own for any of it), and with nothing edited every number is what it was without these rules.
 */
@GameTestHolder(CommonConfigEditorGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class StandTuningGameTests {
	private static final String NAMESPACE = CommonConfigEditorGameTests.NAMESPACE;
	private static final List<ResourceLocation> CORE_AND_ADDON = List.of(StandConfigTestKit.SP, StandConfigTestKit.ADDON);
	private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(JojoMod.resLoc("standcfg_gametest"));

	// the constant each special value took the place of, in its row's unit (ticks read as seconds: 100 ticks are 5 s)
	private static final Map<String, Double> REPLACED_CONSTANTS = Map.ofEntries(
			Map.entry("star_platinum timeStopSeconds", TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS / 20.0),
			Map.entry("star_platinum timeStopSecondsEnhanced", TimeStopLearning.VAMPIRE_MAX_TIME_STOP_TICKS / 20.0),
			Map.entry("star_platinum timeStopGrowth", (double) TimeStopLearning.STAR_PLATINUM_LEARNING_PER_TICK),
			Map.entry("star_platinum timeStopCooldown", (double) TimeStopCooldowns.TIME_STOP_COOLDOWN_PER_TICK),
			Map.entry("star_platinum inhaleReach", 12.0),
			Map.entry("the_world timeStopSeconds", TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS / 20.0),
			Map.entry("the_world timeStopSecondsEnhanced", TimeStopLearning.VAMPIRE_MAX_TIME_STOP_TICKS / 20.0),
			Map.entry("the_world timeStopGrowth", (double) TimeStopLearning.THE_WORLD_LEARNING_PER_TICK),
			Map.entry("the_world timeStopCooldown", (double) TimeStopCooldowns.TIME_STOP_COOLDOWN_PER_TICK),
			Map.entry("the_world kickSweepShare", 0.5),
			Map.entry("magicians_red burnSeconds", 10.0), Map.entry("magicians_red crossfireShots", 8.0),
			Map.entry("magicians_red detectorRadius", 15.0), Map.entry("magicians_red detectorSeconds", 30.0),
			Map.entry("hierophant_green splashSeconds", 1.5), Map.entry("hierophant_green stringCount", 7.0),
			Map.entry("hierophant_green barriers", 15.0), Map.entry("hierophant_green barriersAtResolve4", 100.0),
			Map.entry("silver_chariot rapierRicochets", 100.0), Map.entry("silver_chariot sweepShare", 0.5),
			Map.entry("silver_chariot armorOffAfterimages", 10.0),
			Map.entry("crazy_diamond restoreReach", 12.0), Map.entry("crazy_diamond healSpeed", 1.0),
			Map.entry("crazy_diamond misshapenSeconds", 10.0),
			Map.entry("gold_experience lifeshotSeconds", 5.0), Map.entry("gold_experience lifeshotReach", 8.0),
			Map.entry("gold_experience healRegenSeconds", 300.0));

	private StandTuningGameTests() {}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_damage")
	public static void damageSharesScaleTheHitsOnPlayersAndOnMobs(GameTestHelper helper) {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP, StandConfigTestKit.ADDON);
		try {
			int row = 0;
			for (ResourceLocation stand : CORE_AND_ADDON) {
				Vec3 at = at(helper, 1, 2, 1 + 4 * row++);
				Player user = kit.user(stand, at);
				StandPower power = StandPower.get(user);
				StandEntity body = power.getSummonedStandEntity();
				helper.assertTrue(body != null, stand + " must have its body out");
				Cow cow = kit.cow(at.add(2, 0, 0), 100);
				Player player = kit.target(at.add(-2, 0, 0), 100);
				helper.assertTrue(punch(body, power, cow, 4) == 4 && punch(body, power, player, 4) == 4, "as it is: " + stand);
				kit.set(stand, "damageVsMobs", new JsonPrimitive(200));
				kit.set(stand, "damageVsPlayers", new JsonPrimitive(50));
				helper.assertTrue(punch(body, power, cow, 4) == 8, stand + ": twice on a mob");
				helper.assertTrue(punch(body, power, player, 4) == 2, stand + ": half on a player");
				helper.assertTrue(shot(user, cow, 3) == 6 && shot(user, player, 3) == 1.5F, stand + ": its shots the same");
				kit.clean(stand);
				helper.assertTrue(punch(body, power, cow, 4) == 4 && punch(body, power, player, 4) == 4, stand + ": back as it was");
			}
		}
		finally {
			kit.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_cooldown")
	public static void theCooldownShareScalesEveryCooldownSet(GameTestHelper helper) {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP, StandConfigTestKit.ADDON);
		try {
			int row = 0;
			for (ResourceLocation stand : CORE_AND_ADDON) {
				StandPower power = StandPower.get(kit.user(stand, at(helper, 1, 2, 1 + 4 * row++)));
				Ability finger = power.getMoveset().getAbility("star_finger");
				finger.setCooldownOnUse(power);
				int base = power.getAbilityCooldown("star_finger");
				power.restoreAbilityCooldown("star_finger", 0, 0);
				helper.assertTrue(base > 1, stand + ": Star Finger has a cooldown of its own: " + base);
				kit.set(stand, "cooldowns", new JsonPrimitive(50));
				finger.setCooldownOnUse(power);
				helper.assertTrue(power.getAbilityCooldown("star_finger") == Math.round(base * 0.5), stand + ": half of an ability's own cooldown: "
						+ power.getAbilityCooldown("star_finger"));
				power.setAbilityCooldown("barrage", 30);
				helper.assertTrue(power.getAbilityCooldown("barrage") == 15 && power.getAbilityCooldownTotal("barrage") == 15,
						stand + ": half of one an add-on's code sets itself");
				power.restoreAbilityCooldown("barrage", 30, 30);
				helper.assertTrue(power.getAbilityCooldown("barrage") == 30, stand + ": one put back as it was is not halved again");
				// an add-on that stacks a fresh cooldown on one it read back (RHCP's electric dash) adds the fresh one's share
				int stacked = power.getAbilityCooldown("barrage") + power.getAbilityCooldownShare(40);
				power.restoreAbilityCooldown("barrage", stacked, stacked);
				helper.assertTrue(power.getAbilityCooldownShare(40) == 20 && power.getAbilityCooldown("barrage") == 50,
						stand + ": a stacked cooldown takes the share once: " + power.getAbilityCooldown("barrage"));
				power.resetAbilityCooldowns();
			}
		}
		finally {
			kit.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_stamina")
	public static void theStaminaShareScalesEveryCost(GameTestHelper helper) {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP, StandConfigTestKit.ADDON);
		try {
			int row = 0;
			for (ResourceLocation stand : CORE_AND_ADDON) {
				StandPower power = StandPower.get(kit.user(stand, at(helper, 1, 2, 1 + 4 * row++)));
				Ability finger = power.getMoveset().getAbility("star_finger");
				power.setStamina(500);
				StandAbilityStamina.consume(finger, power, 100, false);
				helper.assertTrue(power.getStamina() == 400 && !StandAbilityStamina.canPay(power, 900), stand + ": as it is");
				kit.set(stand, "staminaCosts", new JsonPrimitive(50));
				power.setStamina(500);
				StandAbilityStamina.consume(finger, power, 100, false);
				helper.assertTrue(power.getStamina() == 450, stand + ": half of an ability's cost: " + power.getStamina());
				power.consumeStamina(250);
				helper.assertTrue(power.getStamina() == 325, stand + ": half of a cost paid without an ability (a leap): " + power.getStamina());
				power.setStamina(500);
				helper.assertTrue(StandAbilityStamina.canPay(power, 900), stand + ": a check weighs the halved cost");
			}
		}
		finally {
			kit.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_mining")
	public static void theBlockRulesDecideWhatItsBlowsBreak(GameTestHelper helper) {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP, StandConfigTestKit.ADDON);
		BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
		try {
			int row = 0;
			for (ResourceLocation stand : CORE_AND_ADDON) {
				StandEntity body = StandPower.get(kit.user(stand, at(helper, 1, 2, 1 + 4 * row++))).getSummonedStandEntity();
				Supplier<Boolean> breaksDirt = () -> StandEntityPunch.canStandBreakBlockByStrength(body, dirt, helper.getLevel(), pos);
				Supplier<Boolean> breaksObsidian = () -> StandEntityPunch.canStandBreakBlockByStrength(body, obsidian, helper.getLevel(), pos);
				helper.assertTrue(breaksDirt.get() && breaksObsidian.get(), stand + " at its strength " + body.getAttackDamage()
						+ " breaks dirt and obsidian as it is");
				kit.set(stand, StandTuning.MINING_TIER, new JsonPrimitive(StandTuning.MiningTier.NONE.id()));
				helper.assertTrue(!breaksDirt.get(), stand + ": none breaks nothing");
				kit.set(stand, StandTuning.MINING_TIER, new JsonPrimitive(StandTuning.MiningTier.WOOD.id()));
				helper.assertTrue(breaksDirt.get() && !breaksObsidian.get(), stand + ": a wooden tier leaves obsidian");
				kit.set(stand, StandTuning.MINING_TIER, new JsonPrimitive(StandTuning.MiningTier.BY_STRENGTH.id()));
				// from the Stand's own limit, never its shipped strength: the largest share that keeps the limit at obsidian's hardness
				double limit = StandStatFormulas.maxBreakableHardness(body.getAttackDamage());
				int share = (int) Math.floor(100 * obsidian.getDestroySpeed(helper.getLevel(), pos) / limit);
				kit.set(stand, "miningStrength", new JsonPrimitive(share));
				helper.assertTrue(breaksDirt.get() && !breaksObsidian.get(), stand + ": a share " + share
						+ " that brings its limit " + limit + " to obsidian's hardness leaves obsidian");
				kit.clean(stand);
				helper.assertTrue(breaksObsidian.get(), stand + ": back as it was");
			}
		}
		finally {
			kit.close();
		}
		helper.succeed();
	}

	/** The blink weighs its reach in the stamina share consumeStamina charges, so what it promises is what it takes. */
	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_blink")
	public static void aBlinkWeighsItsReachInTheShareItIsCharged(GameTestHelper helper) {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.TW);
		try {
			Player user = kit.user(StandConfigTestKit.TW, at(helper, 1, 2, 1));
			StandPower power = StandPower.get(user);
			power.setResolveLevel(3);
			UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
			power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, 95,
					TimeStopLearning.getMaxTrainingPoints(power), power);
			helper.assertTrue(power.getAbility("time_stop_blink") instanceof TimeStopBlinkAbility, "The World has its blink");
			TimeStopBlinkAbility blink = (TimeStopBlinkAbility) power.getAbility("time_stop_blink");
			int longest = TimeStopLearning.getTimeStopTicks(power, TimeStopLearning.TIME_STOP);
			float start = blink.getBlinkStaminaCost(power);
			float tick = blink.getBlinkStaminaCostTicking(power, TimeStopLearning.TIME_STOP);
			helper.assertTrue(start > 0 && tick > 0 && longest > 20 && !StandUtil.standIgnoresStaminaDebuff(power),
					"a trained blink that costs stamina: start " + start + ", tick " + tick + ", longest " + longest);
			// a bar that pays the start and ten ticks at the full costs
			float bar = start + 10 * tick;
			power.setStamina(bar);
			int asItIs = blink.getMaxImpliedTicks(power);
			helper.assertTrue(asItIs == blinkReach(bar, start, tick, longest), "as it is the reach is the bar's: " + asItIs);

			kit.set(StandConfigTestKit.TW, "staminaCosts", new JsonPrimitive(50));
			float halfStart = power.getStaminaCostShare(start);
			float halfTick = power.getStaminaCostShare(tick);
			helper.assertTrue(halfStart == start * 50 / 100F && halfTick == tick * 50 / 100F, "the share is half: " + halfStart);
			power.setStamina(bar);
			int reach = blink.getMaxImpliedTicks(power);
			helper.assertTrue(reach == blinkReach(bar, halfStart, halfTick, longest) && reach > asItIs,
					"the reach is weighed in the costs it is charged: " + reach + ", at the full costs " + asItIs);
			BufferingState state = BufferingState.clickOnly();
			int age = user.tickCount;
			blink.onKeyPress(helper.getLevel(), user, null, InputMethod.CLICK, 0, state);
			int implied = user.tickCount - age;
			float charged = bar - power.getStamina();
			helper.assertTrue(state.isActionSuccess && implied > 0 && Math.abs(charged - (halfStart + implied * halfTick)) < 0.01F,
					"the blink takes the half costs it weighed: " + implied + " ticks, " + charged + " taken");
		}
		finally {
			kit.close();
		}
		helper.succeed();
	}

	private static int blinkReach(float bar, float start, float tick, int longest) {
		return Mth.clamp(Mth.floor((bar - start) / tick), TimeStopLearning.MIN_TIME_STOP_TICKS, longest);
	}

	/** What a Stand's body and its projectiles hit with any damage type is its, and its body's link passes a hit on as it is. */
	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_attrib_body")
	public static void aStandsBodyTakesItsShareWhateverItsDamageType(GameTestHelper helper) {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP, StandConfigTestKit.ADDON);
		try {
			Player spUser = kit.user(StandConfigTestKit.SP, at(helper, 1, 2, 1));
			Player addonUser = kit.user(StandConfigTestKit.ADDON, at(helper, 1, 2, 5));
			StandEntity spBody = StandPower.get(spUser).getSummonedStandEntity();
			StandEntity addonBody = StandPower.get(addonUser).getSummonedStandEntity();
			helper.assertTrue(spBody != null && addonBody != null, "both Stands have their bodies out");
			Cow cow = kit.cow(at(helper, 6, 2, 1), 100);
			kit.set(StandConfigTestKit.ADDON, "damageVsPlayers", new JsonPrimitive(50));
			for (StandEntity body : List.of(spBody, addonBody)) {
				kit.set(body == spBody ? StandConfigTestKit.SP : StandConfigTestKit.ADDON, "damageVsMobs", new JsonPrimitive(200));
				helper.assertTrue(lost(cow, body.damageSources().mobAttack(body), 4) == 8,
						body + ": a hit of its body with a mob's attack type is the Stand's");
				Snowball shot = new Snowball(helper.getLevel(), body);
				helper.assertTrue(lost(cow, cow.damageSources().thrown(shot, body), 4) == 8, body + ": and so is what it throws");
			}
			float[] link = linkedHit(spBody, spUser, addonBody);
			helper.assertTrue(link[0] > 0 && link[1] == link[0],
					"the body's link passes on what the add-on's Stand dealt, not its share of it again: " + link[1] + " of " + link[0]);
		}
		finally {
			kit.close();
		}
		helper.succeed();
	}

	/** The link hit a Stand user takes when his body takes a Stand hit of 4 from another Stand: as it came, and as handled. */
	private static float[] linkedHit(StandEntity body, Player user, StandEntity attacker) {
		float[] link = {-1, -1};
		Consumer<LivingIncomingDamageEvent> first = event -> {
			if (event.getEntity() == user && event.getSource() instanceof StandLinkDamageSource) link[0] = event.getAmount();
		};
		Consumer<LivingIncomingDamageEvent> last = event -> {
			if (event.getEntity() == user && event.getSource() instanceof StandLinkDamageSource) link[1] = event.getAmount();
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingIncomingDamageEvent.class, first);
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, last);
		try {
			body.invulnerableTime = 0;
			user.invulnerableTime = 0;
			DamageSource source = DamageUtil.make(attacker.level(), ModDamageTypes.STAND_ATTACK, attacker);
			((DamageSourceModified) source).jojo_ripples$setStandPower(attacker.getUserPower());
			body.hurt(source, 4);
		}
		finally {
			NeoForge.EVENT_BUS.unregister(first);
			NeoForge.EVENT_BUS.unregister(last);
			user.setHealth(user.getMaxHealth());
			body.setHealth(body.getMaxHealth());
		}
		return link;
	}

	private static final Map<UUID, Runnable> WHILE_BLINKING = new ConcurrentHashMap<>();

	static {
		// an add-on's code that runs while the Stand's ability runs (1.16 add-ons hooked the blink's reach)
		TimeStopTicksModifiers.register(JojoMod.resLoc("standcfg_gametest"), (user, ticks) -> {
			Runnable once = WHILE_BLINKING.remove(user.getUUID());
			if (once != null) {
				once.run();
			}
			return ticks;
		});
	}

	/**
	 * What a Stand's ability does with no other attacker than its user is the Stand's, and so is what the entities and
	 * effects made while it runs do, the damage of a ticking entity with no attacker at all too; its own user is spared.
	 */
	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 200, batch = "standcfg_attrib_ability")
	public static void whatAStandsAbilityMakesTakesItsShare(GameTestHelper helper) {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.TW);
		Player user = kit.user(StandConfigTestKit.TW, at(helper, 1, 2, 1));
		StandPower power = StandPower.get(user);
		power.setResolveLevel(3);
		UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
		power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, 95,
				TimeStopLearning.getMaxTrainingPoints(power), power);
		helper.assertTrue(power.getAbility("time_stop_blink") instanceof TimeStopBlinkAbility, "The World has its blink");
		TimeStopBlinkAbility blink = (TimeStopBlinkAbility) power.getAbility("time_stop_blink");
		Cow hurtInIt = kit.cow(at(helper, 8, 2, 1), 100);
		// the cows hurt as they tick stand in the chunk of the test's block, the one chunk the test loads, in its corners
		Cow poisonedInIt = kit.cow(inOwnChunk(helper, 1, 1), 100);
		Cow poisonedApart = kit.cow(inOwnChunk(helper, 1, 14), 100);
		Cow struckInIt = kit.cow(inOwnChunk(helper, 14, 1), 100);
		Cow struckApart = kit.cow(inOwnChunk(helper, 14, 14), 100);
		Cow shotAt = kit.cow(at(helper, 8, 2, 25), 100);
		List<net.minecraft.world.entity.Entity> made = new ArrayList<>();
		Snowball[] shots = new Snowball[2];
		float[] inIt = {-1, -1};
		try {
			kit.set(StandConfigTestKit.TW, "damageVsMobs", new JsonPrimitive(200));
			kit.set(StandConfigTestKit.TW, "damageVsPlayers", new JsonPrimitive(50));
			var selected = power.updateAvailableMoves().getContextVariationContainer(blink);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK), "the blink may be used");
			// only now: the check before the press reads the reach too, outside the ability
			WHILE_BLINKING.put(user.getUUID(), () -> {
				inIt[0] = lost(hurtInIt, hurtInIt.damageSources().magic(), 4);
				inIt[1] = lost(user, user.damageSources().magic(), 2);
				shots[0] = snowball(helper, user, at(helper, 4, 8, 4), made);
				poisonedInIt.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 4));
				made.add(lightning(helper, struckInIt.position()));
			});
			BufferingState buffer = BufferingState.clickOnly();
			AbilityInput.keyPress(BLINK_KEY, selected.ability, user, null, InputMethod.CLICK, 0, buffer, blink.getAbilityId());
			AbilityInput.keyRelease(BLINK_KEY, user);
			helper.assertTrue(!WHILE_BLINKING.containsKey(user.getUUID()) && buffer.isActionSuccess, "the add-on's code ran in the blink");
			helper.assertTrue(inIt[0] == 8, "a hit with no attacker while the Stand's ability runs is the Stand's: " + inIt[0]);
			helper.assertTrue(inIt[1] == 2, "the Stand's own user takes its hit as it is: " + inIt[1]);
			shots[1] = snowball(helper, user, at(helper, 4, 8, 8), made);
			helper.assertTrue(lost(shotAt, shotAt.damageSources().thrown(shots[0], user), 4) == 8,
					"what an entity made while the ability ran hits is the Stand's");
			helper.assertTrue(lost(shotAt, shotAt.damageSources().thrown(shots[1], user), 4) == 4
					&& lost(shotAt, shotAt.damageSources().magic(), 4) == 4, "what is made or hurt apart from it is not");
			poisonedApart.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 4));
			made.add(lightning(helper, struckApart.position()));
		}
		catch (RuntimeException failure) {
			made.forEach(net.minecraft.world.entity.Entity::discard);
			kit.close();
			throw failure;
		}
		// the first hit each cow takes, as the core's handlers leave it: the fire a lightning lights and the hurt cooldown
		// would blur a reading of health
		Map<LivingEntity, Float> firstHit = new ConcurrentHashMap<>();
		List<String> hits = new java.util.concurrent.CopyOnWriteArrayList<>();
		Consumer<LivingIncomingDamageEvent> record = event -> {
			LivingEntity victim = event.getEntity();
			if (victim == poisonedInIt || victim == poisonedApart || victim == struckInIt || victim == struckApart) {
				hits.add(victim.getId() + " " + event.getSource().getMsgId() + " " + event.getAmount());
			}
			// the poison hurts with NeoForge's own poison type; the struck cows burn after the strike
			if (victim == poisonedInIt || victim == poisonedApart
					|| (victim == struckInIt || victim == struckApart) && event.getSource().is(DamageTypes.LIGHTNING_BOLT)) {
				firstHit.putIfAbsent(victim, event.getAmount());
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, record);
		int[] waited = {0};
		helper.onEachTick(() -> {
			if (firstHit.size() == 4 || ++waited[0] > 100) {
				try {
					Float poisonIn = firstHit.get(poisonedInIt), poisonApart = firstHit.get(poisonedApart);
					Float struckIn = firstHit.get(struckInIt), struckOut = firstHit.get(struckApart);
					helper.assertTrue(poisonIn != null && poisonIn == 2 && poisonApart != null && poisonApart == 1,
							"an effect the Stand gave hurts in its share: " + poisonIn + ", one given apart " + poisonApart);
					helper.assertTrue(struckIn != null && struckIn == 10 && struckOut != null && struckOut == 5,
							"a lightning the Stand made strikes in its share: " + struckIn + ", one made apart " + struckOut);
				}
				catch (RuntimeException failure) {
					throw new GameTestAssertException(failure.getMessage() + "; the cows' first hits "
							+ hits.subList(0, Math.min(12, hits.size())) + " (cows " + poisonedInIt.getId() + " " + poisonedApart.getId()
							+ " " + struckInIt.getId() + " " + struckApart.getId() + ") after " + waited[0] + " ticks");
				}
				finally {
					NeoForge.EVENT_BUS.unregister(record);
					made.forEach(net.minecraft.world.entity.Entity::discard);
					kit.close();
				}
				helper.succeed();
			}
		});
	}

	private static final short BLINK_KEY = 172;

	/** A place in the chunk of the test's own block, from the chunk's corner, two blocks above the block. */
	private static Vec3 inOwnChunk(GameTestHelper helper, int x, int z) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ChunkPos chunk = new ChunkPos(origin);
		return new Vec3(chunk.getMinBlockX() + x + 0.5, origin.getY() + 2, chunk.getMinBlockZ() + z + 0.5);
	}


	private static float lost(LivingEntity target, DamageSource source, float amount) {
		target.invulnerableTime = 0;
		float before = target.getHealth();
		target.hurt(source, amount);
		float lost = before - target.getHealth();
		target.setHealth(target.getMaxHealth());
		return lost;
	}

	private static Snowball snowball(GameTestHelper helper, LivingEntity owner, Vec3 at, List<net.minecraft.world.entity.Entity> made) {
		Snowball shot = new Snowball(helper.getLevel(), owner);
		shot.moveTo(at.x, at.y, at.z, 0, 0);
		shot.setNoGravity(true);
		shot.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(shot);
		made.add(shot);
		return shot;
	}

	private static LightningBolt lightning(GameTestHelper helper, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
		bolt.moveTo(at.x, at.y, at.z, 0, 0);
		helper.getLevel().addFreshEntity(bolt);
		return bolt;
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_special")
	public static void specialRowsReachTheCodeThatReadsThem(GameTestHelper helper) {
		ResourceLocation hg = JojoMod.resLoc("hierophant_green"), cd = JojoMod.resLoc("crazy_diamond"), ge = JojoMod.resLoc("gold_experience");
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP, StandConfigTestKit.TW, hg, cd, ge,
				JojoMod.resLoc("magicians_red"), JojoMod.resLoc("silver_chariot"));
		try {
			StandPower sp = StandPower.get(kit.user(StandConfigTestKit.SP, at(helper, 1, 2, 1)));
			StandPower tw = StandPower.get(kit.user(StandConfigTestKit.TW, at(helper, 1, 2, 4)));
			StandPower hgPower = StandPower.get(kit.user(hg, at(helper, 4, 2, 1)));
			Player cdUser = kit.user(cd, at(helper, 4, 2, 4));
			Player geUser = kit.user(ge, at(helper, 7, 2, 1));
			kit.set(StandConfigTestKit.SP, StandConfigKeys.SPECIAL + "timeStopSeconds", new JsonPrimitive(10.0));
			kit.set(StandConfigTestKit.SP, StandConfigKeys.SPECIAL + "timeStopGrowth", new JsonPrimitive(1.0));
			kit.set(StandConfigTestKit.SP, StandConfigKeys.SPECIAL + "timeStopCooldown", new JsonPrimitive(6.0));
			helper.assertTrue(TimeStopLearning.getMaxTimeStopTicks(sp) == 200 && TimeStopLearning.getMaxTimeStopTicks(tw) == 100,
					"Star Platinum's longest stop, not The World's: " + TimeStopLearning.getMaxTimeStopTicks(sp));
			helper.assertTrue(TimeStopLearning.learningPerTick(sp) == 1.0F && TimeStopLearning.learningPerTick(tw) == 0.1F, "its growth");
			helper.assertTrue(TimeStopCooldowns.timeStopCooldown(sp, 20) == 120 && TimeStopCooldowns.timeStopCooldown(tw, 20) == 60,
					"its cooldown per stopped second");
			kit.set(hg, StandConfigKeys.SPECIAL + "barriers", new JsonPrimitive(7));
			helper.assertTrue(HierophantGreenEntity.getMaxBarriersPlaceable(hgPower) == 7, "Hierophant Green's barriers");
			kit.set(cd, StandConfigKeys.SPECIAL + "restoreReach", new JsonPrimitive(20));
			helper.assertTrue(CrazyDRestoreTerrainAbility.restorationDistManhattan(cdUser, false) == 20, "Crazy Diamond's reach");
			kit.set(ge, StandConfigKeys.SPECIAL + "lifeshotSeconds", new JsonPrimitive(2.0));
			// a mob without its AI takes no Life Shot (GELifeshotEffect.isApplicable): a player does
			Player shot = kit.target(at(helper, 7, 2, 4), 20);
			GoldExperienceEntityLifeshotAbility.giveEffectWithResist(shot, geUser);
			var overload = shot.getEffect(ModStatusEffects.SENSORY_OVERLOAD);
			helper.assertTrue(overload != null && overload.getDuration() == 40, "Gold Experience's Life Shot: " + overload);

			// every special row is the Stand's own value, and only its own
			for (StandType stand : StandConfigCatalog.stands()) {
				for (Special special : StandSpecials.of(stand.getId())) {
					double other = special.integer() ? Math.max(special.min(), special.defaultValue() - 1)
							: Math.min(special.max(), special.defaultValue() + 0.25);
					kit.set(stand.getId(), StandConfigKeys.SPECIAL + special.key(),
							special.integer() ? new JsonPrimitive((int) other) : new JsonPrimitive(other));
					helper.assertTrue(special.get(stand) == other && special.get(StandConfigTestKit.type(StandConfigTestKit.ADDON))
							== special.defaultValue(), stand.getId() + " " + special.key());
				}
			}
		}
		finally {
			kit.close();
		}
		helper.succeed();
	}

	/**
	 * With nothing edited, every number these rules touch is what it is without them: every ability of every registered
	 * Stand sets the same cooldown, every cost takes the same stamina, every hit deals the same damage, every block breaks or
	 * stays as before, every special value and stat is the Stand's own, read in the code that reads it.
	 */
	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 200, batch = "standcfg_defaults")
	public static void withNothingEditedEveryNumberIsAsItWas(GameTestHelper helper) {
		List<ResourceLocation> stands = StandConfigCatalog.stands().stream().map(StandType::getId).toList();
		var kit = new StandConfigTestKit(helper, stands.toArray(ResourceLocation[]::new));
		List<String> differences = new ArrayList<>();
		int compared = 0;
		BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
		List<BlockState> blocks = List.of(Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(),
				Blocks.OBSIDIAN.defaultBlockState(), Blocks.DIAMOND_ORE.defaultBlockState(), Blocks.ANCIENT_DEBRIS.defaultBlockState(),
				Blocks.GLASS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.BEDROCK.defaultBlockState(),
				Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
		try {
			int row = 0;
			for (ResourceLocation id : stands) {
				Vec3 at = at(helper, 1 + 3 * (row % 4), 2, 1 + 3 * (row / 4));
				row++;
				Player user = kit.user(id, at);
				StandPower power = StandPower.get(user);
				StandType type = power.getPowerType();
				for (StatWithValue stat : StatWithValue.values()) {
					compared++;
					if (type.getStandStats().getValue(stat) != type.getStandStats().getDefaultValue(stat)) {
						differences.add(id + " stat " + stat);
					}
				}
				for (Ability ability : new ArrayList<>(power.getMoveset().abilities.values())) {
					compared++;
					compare(differences, id + " cooldown of " + ability.name(), () -> {
						power.resetAbilityCooldowns();
						try {
							ability.setCooldownOnUse(power);
						}
						catch (RuntimeException refused) {
							return "throws " + refused.getClass().getSimpleName();
						}
						Map<String, Integer> set = new LinkedHashMap<>();
						power.getMoveset().abilities.keySet().forEach(name -> {
							if (power.getAbilityCooldown(name) > 0) {
								set.put(name, power.getAbilityCooldown(name) * 100000 + power.getAbilityCooldownTotal(name));
							}
						});
						return set.toString();
					});
				}
				compared++;
				compare(differences, id + " time stop cooldowns", () -> {
					power.resetAbilityCooldowns();
					TimeStopCooldowns.setTimeStopCooldownsOnTimeStopEnd(power, 37);
					return power.getAbilityCooldown("time_stop") + "/" + power.getAbilityCooldown("time_stop_blink");
				});
				power.resetAbilityCooldowns();
				Ability any = power.getMoveset().abilities.values().stream().findFirst().orElse(null);
				for (float cost : new float[] {10, 50, 4, 225.5F, 375, 999}) {
					for (boolean ticking : new boolean[] {false, true}) {
						compared++;
						compare(differences, id + " stamina " + cost + (ticking ? " a tick" : ""), () -> {
							power.setStamina(500);
							boolean paid = StandAbilityStamina.consume(any, power, cost, ticking);
							boolean direct = power.consumeStamina(cost / 3, ticking);
							return paid + " " + direct + " " + Float.floatToIntBits(power.getStamina()) + " "
									+ StandAbilityStamina.canPay(power, cost) + " " + Float.floatToIntBits(power.getStaminaCostShare(cost));
						});
					}
				}
				compared++;
				compare(differences, id + " readers", () -> TimeStopLearning.getMaxTimeStopTicks(power) + " "
						+ Float.floatToIntBits(TimeStopLearning.learningPerTick(power)) + " " + TimeStopCooldowns.timeStopCooldown(power, 33)
						+ " " + HierophantGreenEntity.getMaxBarriersPlaceable(power) + " " + CrazyDRestoreTerrainAbility.restorationDistManhattan(user, false)
						+ " " + TimeStopLearning.getAffordableTsPunchTimeStopTicks(power));
				for (Special special : StandSpecials.of(id)) {
					compared++;
					compare(differences, id + " " + special.key(), () -> Double.toString(special.get(type)));
					Double constant = REPLACED_CONSTANTS.get(id.getPath() + " " + special.key());
					// float: the time stop constants are floats and their readers take the value as one
					if (constant == null || (float) special.get(type) != constant.floatValue()) {
						differences.add(id + " " + special.key() + " is " + special.get(type) + ", the constant it replaced " + constant);
					}
				}
				StandEntity body = power.getSummonedStandEntity();
				if (body == null) {
					continue;
				}
				for (BlockState block : blocks) {
					compared++;
					compare(differences, id + " breaks " + block, () -> Boolean.toString(
							StandEntityPunch.canStandBreakBlockByStrength(body, block, helper.getLevel(), pos)));
				}
				Cow cow = kit.cow(at.add(1.5, 0, 0), 1000);
				Player player = kit.target(at.add(-1.5, 0, 0), 1000);
				for (LivingEntity target : List.<LivingEntity>of(cow, player)) {
					for (float amount : new float[] {0.75F, 5.5F, 19.3F}) {
						compared += 3;
						String what = id + " on a " + target.getType().toShortString() + " " + amount;
						compare(differences, what + " punch", () -> Integer.toString(Float.floatToIntBits(punch(body, power, target, amount))));
						compare(differences, what + " shot", () -> Integer.toString(Float.floatToIntBits(shot(user, target, amount))));
						compare(differences, what + " blast", () -> {
							target.invulnerableTime = 0;
							float before = target.getHealth();
							target.hurt(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_EXPLOSION, body), amount);
							float lost = before - target.getHealth();
							target.setHealth(target.getMaxHealth());
							return Integer.toString(Float.floatToIntBits(lost));
						});
					}
				}
			}
		}
		finally {
			StandTuning.bypassForTest(false);
			kit.close();
		}
		JojoMod.getLogger().info("Stands page defaults: {} numbers compared with and without the rules, {} differ", compared, differences.size());
		helper.assertTrue(compared > 500 && differences.isEmpty(), compared + " compared, these differ: " + differences);
		helper.succeed();
	}

	// what the code gives without the rules (as before them) and with them at their defaults
	private static void compare(List<String> differences, String what, Supplier<String> measure) {
		StandTuning.bypassForTest(true);
		String without;
		try {
			without = measure.get();
		}
		finally {
			StandTuning.bypassForTest(false);
		}
		String with = measure.get();
		if (!without.equals(with)) {
			differences.add(what + ": " + without + " / " + with);
		}
	}

	/** The Stand's own melee path: its hit with its punch's source, through the target's hurt cooldown. */
	private static float punch(StandEntity body, StandPower power, LivingEntity target, float amount) {
		target.invulnerableTime = 0;
		float before = target.getHealth();
		DamageSource source = DamageUtil.make(body.level(), ModDamageTypes.STAND_ATTACK, body);
		((DamageSourceModified) source).jojo_ripples$setStandPower(power);
		body.hurtWithStandAttack(target, source, amount);
		float lost = before - target.getHealth();
		target.setHealth(target.getMaxHealth());
		return lost;
	}

	/** A Stand shot's source, owned by the user, as a Stand's projectile deals it. */
	private static float shot(Player user, LivingEntity target, float amount) {
		target.invulnerableTime = 0;
		float before = target.getHealth();
		target.hurt(DamageUtil.make(user.level(), ModDamageTypes.STAND_PROJECTILE, user, user), amount);
		float lost = before - target.getHealth();
		target.setHealth(target.getMaxHealth());
		return lost;
	}

	private static Vec3 at(GameTestHelper helper, int x, int y, int z) {
		return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, y, z)));
	}
}
