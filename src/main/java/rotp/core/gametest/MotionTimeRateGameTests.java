package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import rotp.core.api.time.EntityTimeRates;
import rotp.core.core.JojoMod;
import rotp.core.subsystems.time.MotionTimeScale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * EntityTimeRates' MOTION rate on the paths no attribute scales, for beings slowed as Made in Heaven's mode A slows
 * them (rate 1/m, gravity times 1/m^2 and a jump strength that keeps the plain jump's height, given here by attribute
 * modifiers to tagged pigs), as in a film slowed m times: levitation and a bubble column lift them about m times slower,
 * never not at all or m^2 times slower; at m = 6 they stand on the ground (vanilla's 0.003 rest threshold took their
 * 1/36 gravity for none), fall, and jump, Jump Boost II to its full height, only later; a ladder climbs m times slower;
 * a fall tends to 1/m of vanilla's terminal speed and they sink in water at 1/m of vanilla's speed, not 1/m^2.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MotionTimeRateGameTests {
	private static final String RATE_3 = "jojo_ripples.gametest.motion_rate_3";
	private static final String RATE_6 = "jojo_ripples.gametest.motion_rate_6";
	private static final String RATE_8 = "jojo_ripples.gametest.motion_rate_8";
	private static final ResourceLocation LAG = JojoMod.resLoc("gametest_motion_rate_lag");
	private static final double BASE_GRAVITY = 0.08;
	private static final double BASE_JUMP = 0.42F;
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();

	private MotionTimeRateGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.MOTION_TIME_RATES)
	public static void levitationLiftsASlowedBeingFromRestAtItsRate(GameTestHelper helper) {
		register();
		Fixture fixture = new Fixture(helper);
		try {
			fixture.clearCellBarriers();
			for (int x = 0; x <= 3; x++) {
				fixture.block(new BlockPos(x, 0, 1), Blocks.STONE.defaultBlockState());
			}
			Pig plain = fixture.pig(new Vec3(0.5, 1, 1.5), null, 1);
			Pig third = fixture.pig(new Vec3(2.0, 1, 1.5), RATE_3, 3);
			Pig eighth = fixture.pig(new Vec3(3.5, 1, 1.5), RATE_8, 8);
			Pig[] pigs = { plain, third, eighth };
			double[] start = new double[3];
			helper.runAfterDelay(3, () -> {
				for (int i = 0; i < pigs.length; i++) {
					start[i] = pigs[i].getY();
					pigs[i].addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 0));
				}
			});
			helper.runAfterDelay(43, () -> {
				double plainRise = plain.getY() - start[0];
				double thirdRise = third.getY() - start[1];
				double eighthRise = eighth.getY() - start[2];
				helper.assertTrue(plainRise > 1.4 && plainRise < 1.8 && thirdRise > 0.0 && eighthRise > 0.0
								&& plainRise / thirdRise > 2.6 && plainRise / thirdRise < 3.4
								&& plainRise / eighthRise > 6.5 && plainRise / eighthRise < 9.5,
						"Levitation I lifted pigs in 40 ticks by " + plainRise + " (rate 1), " + thirdRise + " (rate 1/3), "
								+ eighthRise + " (rate 1/8); about 1.6, 0.53 and 0.2 expected (1/m of the rise, not none)");
				fixture.close();
				helper.succeed();
			});
		}
		catch (RuntimeException | Error error) {
			fixture.close();
			throw error;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 320, batch = GameTestBatches.MOTION_TIME_RATES)
	public static void aBubbleColumnLiftsASlowedBeingAtItsRate(GameTestHelper helper) {
		register();
		Fixture fixture = new Fixture(helper);
		try {
			int depth = 12;
			BlockPos[] shafts = { new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), new BlockPos(0, 0, 2) };
			for (int y = 0; y <= depth; y++) {
				for (int x = -1; x <= 3; x++) {
					for (int z = -1; z <= 3; z++) {
						fixture.block(new BlockPos(x, y, z), Blocks.GLASS.defaultBlockState());
					}
				}
			}
			for (BlockPos shaft : shafts) {
				fixture.block(shaft, Blocks.SOUL_SAND.defaultBlockState());
				for (int y = 1; y <= depth; y++) {
					fixture.block(shaft.above(y), Blocks.WATER.defaultBlockState());
				}
				BubbleColumnBlock.updateColumn(helper.getLevel(), helper.absolutePos(shaft.above()),
						Blocks.SOUL_SAND.defaultBlockState());
			}
			BlockState column = helper.getLevel().getBlockState(helper.absolutePos(shafts[2].above(depth)));
			helper.assertTrue(column.is(Blocks.BUBBLE_COLUMN) && !column.getValue(BubbleColumnBlock.DRAG_DOWN),
					"Setup: the soul sand did not raise a bubble column to the top: " + column);
			Pig[] pigs = {
					fixture.pig(Vec3.atBottomCenterOf(shafts[0].above()), null, 1),
					fixture.pig(Vec3.atBottomCenterOf(shafts[1].above()), RATE_3, 3),
					fixture.pig(Vec3.atBottomCenterOf(shafts[2].above()), RATE_8, 8) };
			double[] start = { pigs[0].getY(), pigs[1].getY(), pigs[2].getY() };
			long begun = helper.getTick();
			long[] risen = { -1, -1, -1 };
			helper.onEachTick(() -> {
				for (int i = 0; i < pigs.length; i++) {
					if (risen[i] < 0 && pigs[i].getY() - start[i] >= 8.0) {
						risen[i] = helper.getTick() - begun;
					}
				}
			});
			helper.runAfterDelay(300, () -> {
				long plain = risen[0];
				long third = risen[1];
				long eighth = risen[2];
				helper.assertTrue(plain > 0 && plain <= 40 && third > 0 && eighth > 0 && eighth <= 260
								&& third >= 1.9 * plain && third <= 3.5 * plain
								&& eighth >= 4.0 * plain && eighth <= 8.5 * plain,
						"Ticks for a bubble column to lift pigs 8 blocks: " + plain + " (rate 1), " + third + " (rate 1/3), "
								+ eighth + " (rate 1/8; -1 never); about 25, 62 and 157 expected (pushed r times, not r^2)");
				fixture.close();
				helper.succeed();
			});
		}
		catch (RuntimeException | Error error) {
			fixture.close();
			throw error;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 200, batch = GameTestBatches.MOTION_TIME_RATES)
	public static void atSixTimesSlowerABeingStandsFallsAndJumpsToItsFullHeight(GameTestHelper helper) {
		register();
		Fixture fixture = new Fixture(helper);
		try {
			fixture.clearCellBarriers();
			for (int x = 0; x <= 3; x++) {
				for (int z = 0; z <= 3; z++) {
					fixture.block(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
				}
			}
			double floor = helper.absoluteVec(new Vec3(0, 1, 0)).y;
			Pig plain = fixture.pig(new Vec3(0.5, 1, 0.5), null, 1);
			Pig slowed = fixture.pig(new Vec3(2.5, 1, 0.5), RATE_6, 6);
			Pig plainBoosted = fixture.pig(new Vec3(0.5, 1, 2.5), null, 1);
			Pig slowedBoosted = fixture.pig(new Vec3(2.5, 1, 2.5), RATE_6, 6);
			Pig faller = fixture.pig(new Vec3(1.5, 2.5, 1.5), RATE_6, 6);
			Pig[] jumpers = { plain, slowed, plainBoosted, slowedBoosted };
			double[] peak = new double[4];
			long[] peakTick = new long[4];
			long[] landed = { -1 };
			helper.runAfterDelay(8, () -> {
				helper.assertTrue(plain.onGround() && slowed.onGround() && slowedBoosted.onGround(),
						"A pig standing on stone at rate 1/6 with 1/36 of its gravity is not on the ground: onGround "
								+ slowed.onGround() + "/" + slowedBoosted.onGround() + ", velocity y "
								+ slowed.getDeltaMovement().y + " (the plain pig: " + plain.onGround() + ")");
				plainBoosted.addEffect(new MobEffectInstance(MobEffects.JUMP, 400, 1));
				slowedBoosted.addEffect(new MobEffectInstance(MobEffects.JUMP, 400, 1));
				for (int i = 0; i < jumpers.length; i++) {
					peak[i] = jumpers[i].getY();
					jumpers[i].getJumpControl().jump();
				}
			});
			long begun = helper.getTick();
			helper.onEachTick(() -> {
				if (helper.getTick() > 8) {
					for (int i = 0; i < jumpers.length; i++) {
						if (jumpers[i].getY() > peak[i]) {
							peak[i] = jumpers[i].getY();
							peakTick[i] = helper.getTick() - 8;
						}
					}
				}
				if (landed[0] < 0 && faller.onGround() && Math.abs(faller.getY() - floor) < 0.01) {
					landed[0] = helper.getTick() - begun;
				}
			});
			helper.runAfterDelay(150, () -> {
				double[] height = new double[4];
				for (int i = 0; i < jumpers.length; i++) {
					height[i] = peak[i] - floor;
				}
				helper.assertTrue(landed[0] > 0,
						"A pig at rate 1/6 let go 1.5 blocks above stone did not land in 150 ticks (about 39 expected): y "
								+ (faller.getY() - floor) + " above the floor, velocity y " + faller.getDeltaMovement().y);
				helper.assertTrue(height[0] > 1.1 && Math.abs(height[1] - height[0]) < 0.06,
						"The plain jump at rate 1/6 reached " + height[1] + " against " + height[0] + " at rate 1");
				helper.assertTrue(height[2] > height[0] + 1.0 && Math.abs(height[3] - height[2]) < 0.06
								&& peakTick[3] >= 3 * peakTick[2],
						"A Jump Boost II jump at rate 1/6 peaked at " + height[3] + " after " + peakTick[3] + " ticks, at rate 1 at "
								+ height[2] + " after " + peakTick[2] + " ticks; the same height, about 6 times later, expected");
				fixture.close();
				helper.succeed();
			});
		}
		catch (RuntimeException | Error error) {
			fixture.close();
			throw error;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.MOTION_TIME_RATES)
	public static void aLadderClimbsASlowedBeingAtItsRate(GameTestHelper helper) {
		register();
		Fixture fixture = new Fixture(helper);
		try {
			BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
			for (int x = 0; x <= 3; x++) {
				for (int z = 0; z <= 3; z++) {
					fixture.block(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
				}
				for (int y = 1; y <= 10; y++) {
					fixture.block(new BlockPos(x, y, 0), Blocks.GLASS.defaultBlockState());
				}
			}
			for (int y = 1; y <= 10; y++) {
				fixture.block(new BlockPos(0, y, 1), ladder);
				fixture.block(new BlockPos(2, y, 1), ladder);
			}
			Pig plain = fixture.pig(new Vec3(0.5, 1, 1.65), null, 1);
			Pig third = fixture.pig(new Vec3(2.5, 1, 1.65), RATE_3, 3);
			double[] at = new double[4];
			helper.onEachTick(() -> {
				plain.getJumpControl().jump();
				third.getJumpControl().jump();
				if (helper.getTick() == 12) {
					at[0] = plain.getY();
					at[1] = third.getY();
				}
				if (helper.getTick() == 32) {
					at[2] = plain.getY();
					at[3] = third.getY();
				}
			});
			helper.runAfterDelay(34, () -> {
				double plainClimb = at[2] - at[0];
				double thirdClimb = at[3] - at[1];
				helper.assertTrue(plainClimb > 2.0 && thirdClimb > 0.0 && plainClimb / thirdClimb > 2.7
								&& plainClimb / thirdClimb < 3.3,
						"In 20 ticks on a ladder pigs climbed " + plainClimb + " (rate 1) and " + thirdClimb
								+ " (rate 1/3); about 2.35 and 0.78 expected (3 times slower, not about 2)");
				fixture.close();
				helper.succeed();
			});
		}
		catch (RuntimeException | Error error) {
			fixture.close();
			throw error;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 120, batch = GameTestBatches.MOTION_TIME_RATES)
	public static void aSlowedBeingFallsTowardItsRateOfVanillasTerminalSpeed(GameTestHelper helper) {
		register();
		Fixture fixture = new Fixture(helper);
		try {
			double terminal = BASE_GRAVITY * MotionTimeScale.AIR_DRAG / (1.0 - MotionTimeScale.AIR_DRAG);
			double vanillaAfter10 = -terminal * (1.0 - Math.pow(MotionTimeScale.AIR_DRAG, 10));
			// let go from rest high in the air, and two already falling at a third and a sixth of vanilla's terminal speed
			Pig[] pigs = {
					fixture.pig(new Vec3(0.5, 60, 0.5), null, 1),
					fixture.pig(new Vec3(2.5, 60, 0.5), RATE_3, 3),
					fixture.pig(new Vec3(4.5, 60, 0.5), RATE_6, 6),
					fixture.pig(new Vec3(2.5, 60, 2.5), RATE_3, 3),
					fixture.pig(new Vec3(4.5, 60, 2.5), RATE_6, 6) };
			pigs[3].setDeltaMovement(0.0, -terminal / 3.0, 0.0);
			pigs[4].setDeltaMovement(0.0, -terminal / 6.0, 0.0);
			int[] at = { 10, 30, 60, 10, 10 };
			double[] expected = { vanillaAfter10, vanillaAfter10 / 3.0, vanillaAfter10 / 6.0, -terminal / 3.0, -terminal / 6.0 };
			double[] tolerance = { 0.005, 0.02, 0.02, 0.005, 0.005 };
			double[] velocity = new double[pigs.length];
			boolean[] read = new boolean[pigs.length];
			helper.onEachTick(() -> {
				for (int i = 0; i < pigs.length; i++) {
					if (!read[i] && pigs[i].tickCount == at[i]) {
						velocity[i] = pigs[i].getDeltaMovement().y;
						read[i] = true;
					}
				}
			});
			helper.runAfterDelay(70, () -> {
				List<String> wrong = new ArrayList<>();
				for (int i = 0; i < pigs.length; i++) {
					if (!read[i] || !(Math.abs(velocity[i] / expected[i] - 1.0) < tolerance[i])) {
						wrong.add("pig " + i + " after " + at[i] + " ticks: " + (read[i] ? velocity[i] : "not read") + " against "
								+ expected[i]);
					}
				}
				helper.assertTrue(wrong.isEmpty(), "Falling pigs (rate 1, 1/3 and 1/6 from rest, then 1/3 and 1/6 at 1/m of"
						+ " vanilla's terminal speed " + terminal + "): " + wrong + "; a slowed fall must reach in m times the"
						+ " ticks 1/m of vanilla's velocity and keep 1/m of its terminal speed, not tend to 1/m^2 of it");
				fixture.close();
				helper.succeed();
			});
		}
		catch (RuntimeException | Error error) {
			fixture.close();
			throw error;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 120, batch = GameTestBatches.MOTION_TIME_RATES)
	public static void aSlowedBeingSinksInWaterAtItsRate(GameTestHelper helper) {
		register();
		Fixture fixture = new Fixture(helper);
		try {
			int top = 9;
			int[] shafts = { 0, 2, 4 };
			for (int y = 0; y <= top; y++) {
				for (int x = -1; x <= 5; x++) {
					for (int z = -1; z <= 1; z++) {
						fixture.block(new BlockPos(x, y, z), Blocks.GLASS.defaultBlockState());
					}
				}
			}
			for (int x : shafts) {
				for (int y = 1; y <= top; y++) {
					fixture.block(new BlockPos(x, y, 0), Blocks.WATER.defaultBlockState());
				}
			}
			Pig[] pigs = {
					fixture.pig(new Vec3(0.5, 6.0, 0.5), null, 1),
					fixture.pig(new Vec3(2.5, 6.0, 0.5), RATE_3, 3),
					fixture.pig(new Vec3(4.5, 6.0, 0.5), RATE_6, 6) };
			double[] from = new double[pigs.length];
			double[] to = new double[pigs.length];
			helper.onEachTick(() -> {
				for (int i = 0; i < pigs.length; i++) {
					if (pigs[i].tickCount == 20) {
						from[i] = pigs[i].getY();
					}
					else if (pigs[i].tickCount == 60) {
						to[i] = pigs[i].getY();
					}
				}
			});
			helper.runAfterDelay(70, () -> {
				double plain = from[0] - to[0];
				double third = from[1] - to[1];
				double sixth = from[2] - to[2];
				helper.assertTrue(pigs[0].isInWater() && pigs[1].isInWater() && pigs[2].isInWater(),
						"Setup: a pig is out of the water");
				helper.assertTrue(plain > 0.9 && plain < 1.1 && third > 0.0 && sixth > 0.0 && plain / third > 2.8
								&& plain / third < 3.2 && plain / sixth > 5.6 && plain / sixth < 6.4,
						"Pigs sank in water from tick 20 to 60 by " + plain + " (rate 1), " + third + " (rate 1/3) and " + sixth
								+ " (rate 1/6); about 1.0, 0.33 and 0.17 expected (1/m of vanilla's sinking, not 1/m^2 or"
								+ " vanilla's 0.003 floor)");
				fixture.close();
				helper.succeed();
			});
		}
		catch (RuntimeException | Error error) {
			fixture.close();
			throw error;
		}
	}

	private static void register() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}
		EntityTimeRates.register(JojoMod.resLoc("gametest_motion_rate"), (entity, channel) -> {
			if (channel != EntityTimeRates.Channel.MOTION) {
				return EntityTimeRates.NO_RATE;
			}
			Set<String> tags = entity.getTags();
			return tags.contains(RATE_3) ? 1.0F / 3.0F
					: tags.contains(RATE_6) ? 1.0F / 6.0F
					: tags.contains(RATE_8) ? 1.0F / 8.0F
					: EntityTimeRates.NO_RATE;
		});
	}

	private static final class Fixture implements GameTestListener {
		private final GameTestHelper helper;
		private final List<Pig> pigs = new ArrayList<>();
		private final List<BlockPos> blocks = new ArrayList<>();
		private boolean closed;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			helper.testInfo.addListener(this);
		}

		// the barrier walls and ceiling the test cell gets around its 1-block template stand up to y 2
		void clearCellBarriers() {
			for (int x = -1; x <= 3; x++) {
				for (int z = -1; z <= 3; z++) {
					for (int y = 1; y <= 2; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}

		void block(BlockPos pos, BlockState state) {
			helper.setBlock(pos, state);
			blocks.add(pos);
		}

		/** A pig with no goals; slowed m times as Made in Heaven slows a being when tagged. */
		Pig pig(Vec3 relative, @Nullable String tag, int multiplier) {
			Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, relative);
			pigs.add(pig);
			if (tag != null) {
				double rate = 1.0 / multiplier;
				double gravity = BASE_GRAVITY * rate * rate;
				double jump = MotionTimeScale.powerForHeight(MotionTimeScale.jumpHeight(BASE_JUMP, BASE_GRAVITY), gravity);
				pig.getAttribute(Attributes.GRAVITY).addPermanentModifier(
						new AttributeModifier(LAG, rate * rate - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				pig.getAttribute(Attributes.JUMP_STRENGTH).addPermanentModifier(
						new AttributeModifier(LAG, jump / BASE_JUMP - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				pig.addTag(tag);
			}
			return pig;
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			pigs.forEach(Pig::discard);
			for (int i = blocks.size() - 1; i >= 0; i--) {
				helper.setBlock(blocks.get(i), Blocks.AIR.defaultBlockState());
			}
		}

		@Override
		public void testStructureLoaded(GameTestInfo test) {}

		@Override
		public void testPassed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testFailed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
			close();
		}
	}
}
