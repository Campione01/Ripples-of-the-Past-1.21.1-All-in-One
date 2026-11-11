package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.config.editor.StandConfigCatalog;
import rotp.core.config.editor.StandConfigKeys;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceEntityLifeshotAbility;
import rotp.core.impl.stands.hierophant.HierophantGreenEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandSpecials;
import rotp.core.powersystem.standpower.StandSpecials.Special;
import rotp.core.powersystem.standpower.StandStats.StatWithValue;
import rotp.core.powersystem.standpower.StandTuning;
import rotp.core.powersystem.standpower.entity.StandEntity;
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
				helper.assertTrue(base == 80, stand + ": Star Finger's own cooldown " + base);
				kit.set(stand, "cooldowns", new JsonPrimitive(50));
				finger.setCooldownOnUse(power);
				helper.assertTrue(power.getAbilityCooldown("star_finger") == 40, stand + ": half of an ability's own cooldown: "
						+ power.getAbilityCooldown("star_finger"));
				power.setAbilityCooldown("barrage", 30);
				helper.assertTrue(power.getAbilityCooldown("barrage") == 15 && power.getAbilityCooldownTotal("barrage") == 15,
						stand + ": half of one an add-on's code sets itself");
				power.restoreAbilityCooldown("barrage", 30, 30);
				helper.assertTrue(power.getAbilityCooldown("barrage") == 30, stand + ": one put back as it was is not halved again");
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
				kit.set(stand, "miningStrength", new JsonPrimitive(50));
				helper.assertTrue(breaksDirt.get() && !breaksObsidian.get(), stand + ": half its strength's hardness leaves obsidian");
				kit.clean(stand);
				helper.assertTrue(breaksObsidian.get(), stand + ": back as it was");
			}
		}
		finally {
			kit.close();
		}
		helper.succeed();
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
