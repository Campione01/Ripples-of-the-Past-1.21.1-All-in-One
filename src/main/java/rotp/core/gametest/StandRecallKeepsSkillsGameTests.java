package rotp.core.gametest;

import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.impl.stands.hierophant.HGBarrierEntity;
import rotp.core.impl.stands.hierophant.HGBarriersNet;
import rotp.core.impl.stands.hierophant.HGEmeraldEntity;
import rotp.core.impl.stands.hierophant.HierophantGreenEntity;
import rotp.core.impl.stands.silverchariot.SCRapierEntity;
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's rule of 2026-11-05, on a player's report (13.png): no Stand skill ends because the Stand is recalled. What a
 * Stand has sent out goes on and ends by its own rules or with its user; a move of its own body stops, having nothing to
 * perform it. 1.16 ended all of it with the Stand (ModdedProjectileEntity.tick, the owner's isAlive).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandRecallKeepsSkillsGameTests {
	private StandRecallKeepsSkillsGameTests() {}

	private static void tick(Entity entity, int times) {
		for (int i = 0; i < times && !entity.isRemoved(); i++) {
			entity.tick();
		}
	}

	private static HGEmeraldEntity emeraldAt(Scene scene, Vec3 from, Vec3 motion) {
		HGEmeraldEntity emerald = new HGEmeraldEntity(scene.stand(), scene.helper().getLevel());
		emerald.setPos(from.x, from.y, from.z);
		emerald.setDeltaMovement(motion);
		scene.helper().assertTrue(scene.helper().getLevel().addFreshEntity(emerald), "setup: could not add the emerald");
		scene.spawned().add(emerald);
		return emerald;
	}

	private static void recall(Scene scene) {
		scene.power().getPowerType().forceUnsummon(scene.user(), scene.power());
		scene.helper().assertTrue(!scene.power().isSummoned() && scene.stand().isRemoved(), "setup: the Stand was not recalled");
		scene.helper().assertTrue(scene.stand().isRecalled(), "a Stand gone from its living user who still has it is a recalled one");
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aFlyingShotGoesOnAndHitsAfterItsStandIsRecalled(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			Cow cow = scene.mob(EntityType.COW, new Vec3(0.0D, 0.0D, 3.0D));
			float health = cow.getHealth();
			Vec3 aim = cow.getBoundingBox().getCenter();
			HGEmeraldEntity emerald = emeraldAt(scene, aim.add(0.0D, 0.0D, -2.0D), new Vec3(0.0D, 0.0D, 0.5D));
			emerald.setDamageFactor(1.0F);
			recall(scene);
			tick(emerald, 8);
			helper.assertTrue(cow.getHealth() < health, "the emerald flying when its Stand was recalled must still hit: the cow has "
					+ cow.getHealth() + " of " + health + ", the emerald " + (emerald.isRemoved() ? "is gone" : "still flies"));
			helper.assertTrue(cow.getLastHurtByMob() == scene.user(), "the hit is its user's, as with the Stand out: "
					+ cow.getLastHurtByMob());
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aFlyingShotStillEndsByItsLifespan(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			HGEmeraldEntity emerald = emeraldAt(scene, scene.user().position().add(0.0D, 8.0D, 0.0D), Vec3.ZERO);
			recall(scene);
			tick(emerald, 5);
			helper.assertTrue(!emerald.isRemoved(), "the emerald ended with its Stand's recall");
			emerald.tickCount = emerald.ticksLifespan() + 1;
			emerald.tick();
			helper.assertTrue(emerald.isRemoved(), "the emerald outlived its own lifespan");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aFlyingShotEndsWithItsUsersLossOfTheStand(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			HGEmeraldEntity emerald = emeraldAt(scene, scene.user().position().add(0.0D, 8.0D, 0.0D), Vec3.ZERO);
			recall(scene);
			tick(emerald, 2);
			helper.assertTrue(!emerald.isRemoved(), "the emerald ended with its Stand's recall");
			scene.power().setStand(null);
			helper.assertTrue(!scene.stand().isRecalled(), "a Stand its user no longer has is not a recalled one");
			emerald.tick();
			helper.assertTrue(emerald.isRemoved(), "the emerald outlived its user's loss of the Stand");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aFlyingShotEndsWithItsUsersDeathAndLogout(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			HGEmeraldEntity emerald = emeraldAt(scene, scene.user().position().add(0.0D, 8.0D, 0.0D), Vec3.ZERO);
			recall(scene);
			tick(emerald, 2);
			helper.assertTrue(!emerald.isRemoved(), "the emerald ended with its Stand's recall");
			scene.user().setHealth(0.0F);
			helper.assertTrue(!scene.stand().isRecalled(), "the Stand of a dead user is not a recalled one");
			emerald.tick();
			helper.assertTrue(emerald.isRemoved(), "the emerald outlived its user's death");
		});
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			HGEmeraldEntity emerald = emeraldAt(scene, scene.user().position().add(0.0D, 8.0D, 0.0D), Vec3.ZERO);
			recall(scene);
			scene.user().discard();
			helper.assertTrue(!scene.stand().isRecalled(), "the Stand of a user who left is not a recalled one");
			emerald.tick();
			helper.assertTrue(emerald.isRemoved(), "the emerald outlived its user's logout");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aPartOfTheStandsBodyStopsWithIt(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			SPStarFingerEntity finger = new SPStarFingerEntity(scene.stand(), helper.getLevel());
			helper.assertTrue(helper.getLevel().addFreshEntity(finger), "setup: could not add Star Finger");
			scene.spawned().add(finger);
			recall(scene);
			finger.tick();
			helper.assertTrue(finger.isRemoved(), "Star Finger is a part of the Stand's body: it must stop with the Stand");
		});
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			HGBarrierEntity string = new HGBarrierEntity(scene.stand(), helper.getLevel());
			helper.assertTrue(helper.getLevel().addFreshEntity(string), "setup: could not add the string");
			scene.spawned().add(string);
			recall(scene);
			string.tick();
			helper.assertTrue(string.isRemoved(), "a string still tied to the Stand must stop with the Stand");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aMoveOfTheStandsBodyStopsWithIt(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			EntityActionInstance punch = scene.start("punch", ActionPhase.WINDUP);
			recall(scene);
			helper.assertTrue(punch.isOver() || scene.stand().getCurStandAction() == null,
					"the punch of a recalled Stand must not go on, phase " + punch.getPhase());
		});
		helper.succeed();
	}

	private static List<HGBarrierEntity> barriers(Scene scene) {
		return scene.helper().getLevel().getEntitiesOfClass(HGBarrierEntity.class, scene.user().getBoundingBox().inflate(16.0D));
	}

	// two blocks above the user and a third: two barriers between them, and the string the Stand still holds
	private static HierophantGreenEntity placeNet(Scene scene) {
		HierophantGreenEntity hierophant = (HierophantGreenEntity) scene.stand();
		BlockPos above = BlockPos.containing(scene.user().position()).above(4);
		hierophant.attachBarrier(above.west(4));
		hierophant.attachBarrier(above.east(4));
		hierophant.attachBarrier(above.east(4).north(4));
		scene.spawned().addAll(barriers(scene));
		scene.helper().assertTrue(hierophant.getBarriersNet().getSize() == 2,
				"setup: the Stand placed " + hierophant.getBarriersNet().getSize() + " barriers, not 2");
		return hierophant;
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void placedBarriersStayThroughARecallAndTheNextSummonTakesThem(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			placeNet(scene);
			recall(scene);
			List<HGBarrierEntity> placed = barriers(scene).stream().filter(HGBarrierEntity::isAttachedToBlock).toList();
			helper.assertTrue(placed.size() == 2 && barriers(scene).size() == 2,
					"a recall must leave the 2 placed barriers and end the string the Stand held: " + barriers(scene).size()
							+ " left, " + placed.size() + " placed");
			placed.forEach(barrier -> barrier.tick());
			helper.assertTrue(placed.stream().noneMatch(HGBarrierEntity::isRemoved), "a placed barrier ended after its Stand's recall");
			helper.assertTrue(HGBarriersNet.of(scene.user()).getSize() == 2, "the user's net lost its barriers with the recall");

			helper.assertTrue(scene.power().getPowerType().summon(scene.user(), scene.power()), "setup: the next summon was refused");
			StandEntity next = scene.power().getSummonedStandEntity();
			next.tick();
			helper.assertTrue(((HierophantGreenEntity) next).getPlacedBarriersCount() == 2,
					"the next Stand must count the net still placed: " + ((HierophantGreenEntity) next).getPlacedBarriersCount());
			helper.assertTrue(placed.stream().allMatch(barrier -> barrier.getOwner() == next),
					"the next Stand must take over the net still placed");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aPlacedBarrierRippedAfterARecallStillShootsTheNet(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			placeNet(scene);
			recall(scene);
			scene.power().setStamina(scene.power().getMaxStamina());
			Cow cow = scene.mob(EntityType.COW, new Vec3(0.0D, 3.6D, 0.0D));
			float health = cow.getHealth();
			List<HGBarrierEntity> placed = barriers(scene);
			placed.forEach(barrier -> barrier.tick());
			helper.assertTrue(placed.stream().anyMatch(HGBarrierEntity::wasRipped),
					"a cow in a placed barrier must rip it with its Stand recalled");
			helper.assertTrue(cow.getHealth() < health, "the ripped barrier must hurt the cow: " + cow.getHealth() + " of " + health);
			AABB around = scene.user().getBoundingBox().inflate(16.0D);
			List<HGEmeraldEntity> emeralds = helper.getLevel().getEntitiesOfClass(HGEmeraldEntity.class, around);
			scene.spawned().addAll(emeralds);
			helper.assertTrue(!emeralds.isEmpty(), "the net of a recalled Stand shot no emeralds at what ripped it");
			emeralds.forEach(emerald -> {
				emerald.tick();
				helper.assertTrue(!emerald.isRemoved(), "an emerald of the net ended at once, its Stand being recalled");
			});
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void placedBarriersEndWithTheirUsersLossOfTheStandDeathAndLogout(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			placeNet(scene);
			recall(scene);
			List<HGBarrierEntity> placed = barriers(scene);
			scene.power().setStand(null);
			placed.forEach(barrier -> barrier.tick());
			helper.assertTrue(placed.stream().allMatch(HGBarrierEntity::isRemoved), "the net outlived its user's loss of the Stand");
		});
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			placeNet(scene);
			recall(scene);
			List<HGBarrierEntity> placed = barriers(scene);
			scene.user().setHealth(0.0F);
			placed.forEach(barrier -> barrier.tick());
			helper.assertTrue(placed.stream().allMatch(HGBarrierEntity::isRemoved), "the net outlived its user's death");
		});
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			placeNet(scene);
			recall(scene);
			List<HGBarrierEntity> placed = barriers(scene);
			scene.user().discard();
			placed.forEach(barrier -> barrier.tick());
			helper.assertTrue(placed.stream().allMatch(HGBarrierEntity::isRemoved), "the net outlived its user's logout");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aSneakingBarrierPressTakesTheNetDown(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			HierophantGreenEntity hierophant = placeNet(scene);
			List<HGBarrierEntity> strings = barriers(scene);
			helper.assertTrue(strings.size() == 3, "setup: the net has " + strings.size() + " strings, not 3");
			Ability barrier = scene.power().getAbility("barrier");
			helper.assertTrue(barrier != null && !barrier.checkSpecificConditions(scene.power()).isPositive(),
					"setup: with no block aimed at a plain press of Barrier goes through");

			scene.user().setShiftKeyDown(true);
			helper.assertTrue(barrier.checkSpecificConditions(scene.power()).isPositive(),
					"a sneaking press of Barrier must go through with no block aimed at");
			scene.set("barrier");
			LivingComponentAction actions = LivingComponentAction.getComponent(scene.stand());
			for (int tick = 0; tick < 5; tick++) {
				actions.tick();
			}
			scene.user().setShiftKeyDown(false);
			helper.assertTrue(strings.stream().allMatch(HGBarrierEntity::isRemoved) && hierophant.getBarriersNet().getSize() == 0,
					"a sneaking press of Barrier must take down the whole net: " + barriers(scene).size() + " strings are left");
			helper.assertTrue(hierophant.getPlacedBarriersCount() == 0, "the taken down net still counts "
					+ hierophant.getPlacedBarriersCount());
			helper.assertTrue(!hierophant.takeDownPlacedBarriers(), "with no net placed there is nothing to take down");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aRapierFliesOnAfterARecallAndEndsWhereItStops(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			SCRapierEntity rapier = new SCRapierEntity(scene.stand(), helper.getLevel());
			Vec3 from = scene.user().position().add(0.0D, 8.0D, 0.0D);
			rapier.setPos(from.x, from.y, from.z);
			rapier.setDeltaMovement(0.0D, 0.0D, 0.1D);
			helper.assertTrue(helper.getLevel().addFreshEntity(rapier), "setup: could not add the rapier");
			scene.spawned().add(rapier);
			recall(scene);
			tick(rapier, 3);
			helper.assertTrue(!rapier.isRemoved(), "the flying rapier ended with its Stand's recall");
			rapier.setDeltaMovement(Vec3.ZERO);
			rapier.tick();
			helper.assertTrue(rapier.isRemoved(), "with nothing to take it back a rapier that stopped must end");

			SCRapierEntity flying = new SCRapierEntity(scene.stand(), helper.getLevel());
			flying.setPos(from.x, from.y, from.z);
			flying.setDeltaMovement(0.0D, 0.0D, 0.1D);
			helper.assertTrue(helper.getLevel().addFreshEntity(flying), "setup: could not add the second rapier");
			scene.spawned().add(flying);
			tick(flying, 2);
			helper.assertTrue(!flying.isRemoved(), "the second flying rapier ended with its Stand's recall");
			helper.assertTrue(scene.power().getPowerType().summon(scene.user(), scene.power()), "setup: the next summon was refused");
			flying.tick();
			helper.assertTrue(flying.isRemoved(), "the next summon has a new rapier: the old one must end");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aRapierWithNoLifespanDoesNotFlyOnForEver(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			SCRapierEntity rapier = new SCRapierEntity(scene.stand(), helper.getLevel());
			Vec3 from = scene.user().position().add(0.0D, 8.0D, 0.0D);
			rapier.setPos(from.x, from.y, from.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(rapier), "setup: could not add the rapier");
			scene.spawned().add(rapier);
			recall(scene);
			int ticks = 0;
			for (; ticks < 400 && !rapier.isRemoved(); ticks++) {
				rapier.setDeltaMovement(0.0D, 0.0D, 0.001D);
				rapier.setPos(from.x, from.y, from.z);
				rapier.tick();
			}
			helper.assertTrue(rapier.isRemoved() && ticks > 100,
					"a projectile with no lifespan must end some time after its Stand's recall: after " + ticks + " ticks it "
							+ (rapier.isRemoved() ? "ended too soon" : "still flies"));
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aStandItsUserChangedForAnotherIsNotARecalledOne(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			StandPower power = scene.power();
			recall(scene);
			StandType other = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(StandPowerTransitions.replace(power, power.getPowerType().getId(), new StandInstance(other)).applied(),
					"setup: could not change the user's Stand");
			helper.assertTrue(!scene.stand().isRecalled(), "a Stand its user changed for another is not a recalled one");
			helper.assertTrue(PowerClass.STAND.get(scene.user()) == power, "setup: the power changed");
		});
		helper.succeed();
	}
}
