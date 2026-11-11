package rotp.core.gametest;

import rotp.core.api.stand.BlinkLanding;
import rotp.core.core.JojoMod;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * BlinkLanding, the time-stop blink's landing rules as a public API (for King Crimson's Time Erase step): the walk per
 * tick and the reach, the landing on a block, before and behind an entity, and the ticks a trip implies. The blink's own
 * behaviour stays pinned by TimeStopBlinkParityGameTests and TimeStopBlinkTargetGameTests.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlinkLandingGameTests {
	private static final double EPS = 1.0E-6;

	private BlinkLandingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theBlinksLandingRulesAreTheApis(GameTestHelper helper) {
		Zombie user = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5, 2, 1.5));
		Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(1.5, 2, 1.5));
		try {
			user.setNoGravity(true);
			pig.setNoGravity(true);
			double speed = user.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.1585;
			helper.assertTrue(Math.abs(BlinkLanding.distancePerTick(user) - speed) < EPS
					&& Math.abs(BlinkLanding.reach(user, 10, BlinkLanding.MAX_DISTANCE) - speed * 10) < EPS
					&& BlinkLanding.reach(user, 100000, 64) == 64, "The walk per tick or the reach: " + BlinkLanding.distancePerTick(user)
							+ " a tick, " + BlinkLanding.reach(user, 10, BlinkLanding.MAX_DISTANCE) + " in 10");

			BlockPos floor = new BlockPos(1, 0, 1);
			helper.setBlock(floor, Blocks.STONE.defaultBlockState());
			helper.setBlock(floor.above(), Blocks.AIR.defaultBlockState());
			BlockPos absolute = helper.absolutePos(floor);
			Vec3 onBlock = BlinkLanding.landingPos(helper.getLevel(), user,
					new ActionTarget(absolute, Direction.EAST), 10, false);
			helper.assertTrue(onBlock.equals(Vec3.atBottomCenterOf(absolute.above())), "A blink at a block with air above landed at "
					+ onBlock + ", not on top of it");

			Vec3 at = user.position();
			pig.moveTo(at.x, at.y, at.z + 5, 0.0F, 0.0F);
			user.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
			double gap = pig.getBbWidth() + user.getBbWidth();
			Vec3 before = BlinkLanding.landingPos(helper.getLevel(), user, new ActionTarget(pig), 10, false);
			Vec3 behind = BlinkLanding.landingPos(helper.getLevel(), user, new ActionTarget(pig), 10, true);
			helper.assertTrue(before.distanceTo(pig.position().subtract(user.getLookAngle().scale(gap))) < EPS,
					"A blink at an entity did not land in front of it on the user's line: " + before);
			helper.assertTrue(behind.distanceTo(pig.position().add(0, 0, -gap)) < EPS,
					"A blink behind an entity facing +Z did not land behind its back: " + behind + " for the pig at " + pig.position());

			Vec3 threeAway = at.add(3, 0, 0);
			int ticks = BlinkLanding.impliedTicks(user, threeAway, 1000);
			helper.assertTrue(ticks == (int) Math.ceil(3 / speed) && BlinkLanding.impliedTicks(user, threeAway, 2) == 2,
					"Three blocks implied " + ticks + " ticks, " + (int) Math.ceil(3 / speed) + " expected (and at most the cap)");
		}
		finally {
			user.discard();
			pig.discard();
			helper.setBlock(new BlockPos(1, 0, 1), Blocks.AIR.defaultBlockState());
		}
		helper.succeed();
	}
}
