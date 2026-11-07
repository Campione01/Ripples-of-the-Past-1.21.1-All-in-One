package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import rotp.core.JojoModConfig;
import rotp.core.JojoModLivingVariables;
import rotp.core.ServerSavedData;
import rotp.core.adventure.SpawnArrowsInSusBlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.vampirism.SunsetClock;
import rotp.core.init.ModBlocks;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModItems;
import rotp.core.item.CassetteRecordedItem;
import rotp.core.item.cassette.CassetteData;
import rotp.core.item.cassette.CassetteSide;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.GainStandExpOnEnemyDefeat;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.powersystem.unlockableskill.UnlockableSkill;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The features of the official port taken over in R772.1: what a server can show of them. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OfficialParityGameTests {
	private static final float EPS = 1.0E-3F;

	private OfficialParityGameTests() {}

	private record Scene(Player user, StandPower power) {}

	private static Scene starPlatinumUser(GameTestHelper helper, BlockPos at) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		user.setNoGravity(true);
		user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
		return new Scene(user, power);
	}

	private static void remove(StandPower power, LivingEntity... entities) {
		StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
		if (stand != null) {
			stand.discard();
		}
		for (LivingEntity entity : entities) {
			if (entity != null) {
				entity.discard();
			}
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theNewSkillsNoticeListsNoSkillAlreadyOwned(GameTestHelper helper) {
		Scene scene = starPlatinumUser(helper, new BlockPos(1, 2, 1));
		try {
			StandPower power = scene.power();
			power.setResolveLevel(4);
			StandTypePersistentData data = power.getCurTypeData();
			UnlockableSkill owned = data.getAllSkills().get("star_finger");
			UnlockableSkill notOwned = data.getAllSkills().get("inhale");
			// the Resolve level unlocks a skill without its exp ever being paid: the way an owned skill comes to pass this test
			data._setSkillUnlocked(owned, true, false);
			data._setSkillUnlocked(notOwned, false, false);

			data.setExpAndListNewSkills(0, power);
			Collection<? extends UnlockableSkill> listed = data.setExpAndListNewSkills(100000, power);
			helper.assertTrue(listed.contains(notOwned), "A skill the exp opens must be listed: " + listed);
			helper.assertFalse(listed.contains(owned), "A skill already owned must not be listed: " + owned.skillName);
			helper.succeed();
		}
		finally {
			remove(scene.power(), scene.user());
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void standExpMultiplierScalesExpEarnedInPlayAndNotACommandsAmount(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<Double> multiplier = JojoModConfig.COMMON_SPEC.getValues().get(
				List.of("Stand settings", "Stand Progression", "standExpMultiplier"));
		helper.assertTrue(multiplier != null && multiplier.getDefault() == 1.0D, "standExpMultiplier must default to 1");
		double previous = multiplier.get();
		Scene scene = starPlatinumUser(helper, new BlockPos(1, 2, 1));
		Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);
		try {
			StandPower power = scene.power();
			helper.assertTrue(power.getPowerType().summon(scene.user(), power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			StandTypePersistentData data = power.getCurTypeData();

			multiplier.set(1.0D);
			data.setExp(0, power);
			power.addExp(10);
			helper.assertTrue(data.getExp() == 10, "At 1 the exp stays as it is: " + data.getExp());

			multiplier.set(2.5D);
			data.setExp(0, power);
			power.addExp(10);
			helper.assertTrue(data.getExp() == 25, "At 2.5, 10 exp earned must be 25: " + data.getExp());

			data.setExp(0, power);
			data.addExp(10, scene.user());
			helper.assertTrue(data.getExp() == 10, "The amount a command gives is not scaled: " + data.getExp());

			data.setExp(0, power);
			float earned = zombie.getMaxHealth() / 20;
			DamageSource hit = DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, stand);
			GainStandExpOnEnemyDefeat.onLivingDeath(new LivingDeathEvent(zombie, hit));
			helper.assertTrue(data.getExp() == (int) (earned * 2.5F),
					"A defeat is exp earned in play: " + data.getExp() + ", " + earned + " before the multiplier");
			helper.succeed();
		}
		finally {
			multiplier.set(previous);
			remove(scene.power(), scene.user(), zombie);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void anArrowStreakStopsAtFive(GameTestHelper helper) {
		Scene scene = starPlatinumUser(helper, new BlockPos(1, 2, 1));
		ServerLevel level = helper.getLevel();
		ServerSavedData saved = ServerSavedData.get(level.getServer());
		boolean foundFirst = saved.foundFirstArrows;
		try {
			saved.foundFirstArrows = false;
			JojoModLivingVariables vars = JojoModLivingVariables.get(scene.user());
			int arrows = 0;
			for (int i = 0; i < 2000; i++) {
				if (!SpawnArrowsInSusBlocks.standArrowToPut(scene.user(), level, helper.absolutePos(new BlockPos(1, 1, 1))).isEmpty()) {
					arrows++;
				}
			}
			helper.assertTrue(arrows == SpawnArrowsInSusBlocks.MAX_ARROWS_IN_STREAK,
					"2000 brushes in one window must find exactly 5 arrows, found " + arrows);
			helper.assertTrue(vars.findMoreArrowsTimer > 0, "Fixture: the window must still be open");

			CompoundTag saveData = vars.serializeNBT(level.registryAccess());
			vars.arrowsInStreak = 0;
			vars.deserializeNBT(level.registryAccess(), saveData);
			helper.assertTrue(vars.arrowsInStreak == SpawnArrowsInSusBlocks.MAX_ARROWS_IN_STREAK,
					"A relog must not start the count over: " + vars.arrowsInStreak);

			vars.findMoreArrowsTimer = -1;
			vars.foundAnArrow = false;
			saved.foundFirstArrows = false;
			int next = 0;
			for (int i = 0; i < 2000 && next == 0; i++) {
				next += SpawnArrowsInSusBlocks.standArrowToPut(scene.user(), level, helper.absolutePos(new BlockPos(1, 1, 1))).isEmpty() ? 0 : 1;
			}
			helper.assertTrue(next == 1 && vars.arrowsInStreak == 1, "A new streak starts its own count: " + vars.arrowsInStreak);
			helper.succeed();
		}
		finally {
			saved.foundFirstArrows = foundFirst;
			remove(scene.power(), scene.user());
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theSunsetClockKnowsWhenTheSunStopsBurning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		float rain = level.getRainLevel(1.0F);
		float thunder = level.getThunderLevel(1.0F);
		try {
			level.setRainLevel(0);
			level.setThunderLevel(0);
			// the whole day, tick by tick: the two constants are the only two places where the sun comes and goes
			List<Integer> flips = new ArrayList<>();
			boolean wasDay = false;
			for (int t = 0; t < SunsetClock.DAY_LENGTH; t++) {
				level.setDayTime(t);
				level.updateSkyBrightness();
				boolean day = level.isDay();
				if (t > 0 && day != wasDay) {
					flips.add(t);
				}
				wasDay = day;
				helper.assertTrue(SunsetClock.ticksToSunset(t) >= 0 == day, "The clock disagrees with Level#isDay at " + t + ": day " + day);
			}
			helper.assertTrue(flips.equals(List.of(SunsetClock.SUN_DOWN_TIME, SunsetClock.SUN_UP_TIME)), "The sun comes and goes at " + flips);
		}
		finally {
			level.setRainLevel(rain);
			level.setThunderLevel(thunder);
			level.setDayTime(time);
			level.updateSkyBrightness();
		}
		helper.assertTrue(SunsetClock.ticksToSunset(0) == 12542 && SunsetClock.ticksToSunset(12541) == 1,
				"Ticks to sunset in the morning and at its last tick: " + SunsetClock.ticksToSunset(0));
		helper.assertTrue(SunsetClock.ticksToSunset(23999) == 12543 && SunsetClock.ticksToSunset(24000 + 6000) == 6542,
				"Across midnight and on later days: " + SunsetClock.ticksToSunset(23999));
		helper.assertTrue(SunsetClock.ticksToSunset(18000) == -1, "At night there is no sunset to wait for");

		DamageSource burn = DamageUtil.make(level, ModDamageTypes.ULTRAVIOLET);
		Player victim = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		helper.assertTrue(burn.getLocalizedDeathMessage(victim).getContents() instanceof TranslatableContents contents
				&& "death.attack.ultraviolet".equals(contents.getKey()),
				"A sun burn's death message must be the ultraviolet line: " + burn.getLocalizedDeathMessage(victim));
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theItemHintsAreOnTheirItems(GameTestHelper helper) {
		ItemStack recorded = CassetteRecordedItem.withDyeRecording(DyeColor.RED);
		assertHint(helper, ModItems.KNIFE.get().getDefaultInstance(), "item.jojo_ripples.knife.hint", true);
		assertHint(helper, ModItems.GLOVES.get().getDefaultInstance(), "item.jojo_ripples.gloves.hint", true);
		assertHint(helper, ModItems.BUBBLE_GLOVES.get().getDefaultInstance(), "item.jojo_ripples.gloves.hint", true);
		assertHint(helper, ModItems.WALKMAN.get().getDefaultInstance(), "item.jojo_ripples.walkman.hint", true);
		assertHint(helper, recorded, "item.jojo_ripples.cassette_recorded.hint.play", true);
		assertHint(helper, recorded, "item.jojo_ripples.cassette_recorded.hint.copy", true);
		ItemStack lastCopy = recorded.copy();
		CassetteRecordedItem.editCassetteData(lastCopy, data -> new CassetteData(data.tracks(), CassetteData.MAX_GENERATION,
				data.dye(), data.dyeCraftHint(), CassetteSide.SIDE_A, 0));
		assertHint(helper, lastCopy, "item.jojo_ripples.cassette_recorded.hint.copy", false);
		ItemStack coffin = new ItemStack(ModBlocks.WOODEN_COFFIN_OAK.get(DyeColor.RED).get());
		assertHint(helper, coffin, "block.jojo_ripples.wooden_coffin.hint", true);
		assertHint(helper, coffin, "block.jojo_ripples.wooden_coffin.hint2", true);
		assertHint(helper, new ItemStack(Items.STICK), "item.jojo_ripples.knife.hint", false);
		helper.succeed();
	}

	private static void assertHint(GameTestHelper helper, ItemStack stack, String key, boolean wanted) {
		List<Component> tooltip = new ArrayList<>();
		stack.getItem().appendHoverText(stack, Item.TooltipContext.of(helper.getLevel()), tooltip, TooltipFlag.NORMAL);
		boolean found = tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
				&& key.equals(contents.getKey()));
		helper.assertTrue(found == wanted, stack.getItem() + (wanted ? " must show " : " must not show ") + key + ": " + tooltip);
	}

	/**
	 * Player#attack knocks each mob its sweep meets back before it hurts it. A Stand that plain damage cannot touch took
	 * the knockback all the same and handed it to its user (StandEntity#knockback). The pig beside it proves the sweep ran.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aSwordSweepKnocksBackNoStandAndNoUserThroughIt(GameTestHelper helper) {
		Scene scene = starPlatinumUser(helper, new BlockPos(2, 2, 1));
		Player attacker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 2, 4);
		Pig beside = helper.spawnWithNoFreeWill(EntityType.PIG, 3, 2, 4);
		try {
			StandPower power = scene.power();
			helper.assertTrue(power.getPowerType().summon(scene.user(), power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
			attacker.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
			attacker.setOnGround(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(attacker), "Could not add the attacker");
			attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
			setFullAttackStrength(attacker);

			Vec3 targetPos = target.position();
			stand.setPos(targetPos.x - 1.0D, targetPos.y, targetPos.z);
			beside.setPos(targetPos.x + 1.0D, targetPos.y, targetPos.z);
			stand.setDeltaMovement(Vec3.ZERO);
			scene.user().setDeltaMovement(Vec3.ZERO);
			beside.setDeltaMovement(Vec3.ZERO);
			float besideHealth = beside.getHealth();
			attacker.attack(target);

			helper.assertTrue(beside.getHealth() < besideHealth, "Fixture: the sweep did not reach the pig beside the target");
			helper.assertTrue(beside.getDeltaMovement().horizontalDistance() > EPS,
					"The sweep must still knock an ordinary mob back");
			helper.assertTrue(stand.getDeltaMovement().horizontalDistance() < EPS,
					"The sweep knocked the Stand back: " + stand.getDeltaMovement());
			helper.assertTrue(scene.user().getDeltaMovement().horizontalDistance() < EPS,
					"The sweep knocked the Stand's user back through it: " + scene.user().getDeltaMovement());
			helper.succeed();
		}
		finally {
			remove(scene.power(), scene.user(), attacker, target, beside);
		}
	}

	private static void setFullAttackStrength(LivingEntity entity) {
		try {
			Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			field.setAccessible(true);
			field.setInt(entity, 1000);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError("LivingEntity.attackStrengthTicker is missing", e);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aStandFadesInBrieflyAndReddensWithItsUser(GameTestHelper helper) {
		helper.assertTrue(StandEntity.fadeInAlpha(0, false) == 0F, "A Stand just out is not yet seen");
		helper.assertTrue(StandEntity.fadeInAlpha(StandEntity.FADE_IN_TICKS / 2, false) == 0.5F, "Half way through the fade");
		helper.assertTrue(StandEntity.fadeInAlpha(StandEntity.FADE_IN_TICKS, false) == 1F, "After it, the Stand is whole");
		helper.assertTrue(StandEntity.fadeInAlpha(1000, false) == 1F, "And stays so");
		helper.assertTrue(StandEntity.fadeInAlpha(0, true) == 1F, "Arms out for a punch do not fade in");
		helper.assertTrue(StandEntity.hurtTimeWithUser(0, 7) == 7, "A hurt user reddens the Stand");
		helper.assertTrue(StandEntity.hurtTimeWithUser(9, 3) == 9, "A Stand's own hit keeps its own count");
		helper.assertTrue(StandEntity.hurtTimeWithUser(0, 0) == 0, "Nobody hurt, no red");
		helper.succeed();
	}
}
