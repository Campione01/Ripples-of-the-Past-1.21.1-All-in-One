package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.core.JojoMod;
import rotp.core.subsystems.time.LevelTickRateState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The tick rates of Made in Heaven's mode B: a non-living entity's ENTITY_TICK rate ticks it several times a server
 * tick or holds it back, never a living one; a level's BLOCK_TICKS rate shortens a scheduled tick's delay (a stone
 * button lets go sooner), its BLOCK_ENTITIES rate runs a furnace several times a tick, and its RANDOM_TICKS rate scales
 * the random tick speed. The entity rates are given to tagged entities only; the level rates to this test's level for
 * a few ticks, in a batch of its own.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TickRateGameTests {
	private static final String FAST = "jojo_ripples.gametest.tick_rate_fast";
	private static final String SLOW = "jojo_ripples.gametest.tick_rate_slow";
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();
	@Nullable private static volatile ServerLevel ratedLevel;
	private static volatile long ratedUntil = Long.MIN_VALUE;

	private TickRateGameTests() {}

	@GameTest(template = "empty", batch = GameTestBatches.LEVEL_TICK_RATES)
	public static void nonLivingThingsAndTheLevelsTicksRunAtTheirRates(GameTestHelper helper) {
		register();
		ServerLevel level = helper.getLevel();
		Fixture fixture = new Fixture(helper);
		helper.testInfo.addListener(fixture);
		try {
			Marker fast = fixture.marker(new Vec3(0.5, 1, 3.5), FAST);
			Marker slow = fixture.marker(new Vec3(1.5, 1, 3.5), SLOW);
			Marker plain = fixture.marker(new Vec3(2.5, 1, 3.5), null);
			Cow livingFast = EntityType.COW.create(level);
			helper.assertTrue(livingFast != null, "Could not create the cow");
			livingFast.setNoAi(true);
			livingFast.setNoGravity(true);
			Vec3 cowPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3)));
			livingFast.moveTo(cowPos.x, cowPos.y, cowPos.z, 0.0F, 0.0F);
			livingFast.addTag(FAST);
			helper.assertTrue(level.addFreshEntity(livingFast), "Could not add the cow");
			fixture.entities.add(livingFast);

			helper.setBlock(Fixture.FURNACE, Blocks.FURNACE);
			FurnaceBlockEntity furnace = helper.getBlockEntity(Fixture.FURNACE);
			furnace.setItem(0, new ItemStack(Items.BEEF, 4));
			furnace.setItem(1, new ItemStack(Items.COAL, 2));
			helper.setBlock(Fixture.STONE, Blocks.STONE);
			helper.setBlock(Fixture.BUTTON, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR));

			// from the next level tick on: the core asks a level's rates once per level tick
			ratedLevel = level;
			ratedUntil = level.getGameTime() + 20;
			helper.runAfterDelay(1, () -> {
				BlockPos buttonPos = helper.absolutePos(Fixture.BUTTON);
				BlockState button = level.getBlockState(buttonPos);
				helper.assertTrue(button.getBlock() instanceof ButtonBlock && !button.getValue(ButtonBlock.POWERED),
						"Setup: no released stone button: " + button);
				((ButtonBlock) button.getBlock()).press(button, level, buttonPos, null);
				int randomTickSpeed = LevelTickRateState.randomTickSpeed(level, 3);
				helper.assertTrue(randomTickSpeed == 6, "A random tick speed of 3 at rate 2 gave " + randomTickSpeed);
				int cookBefore = cookTime(helper, furnace);
				int[] before = { fast.tickCount, slow.tickCount, plain.tickCount, livingFast.tickCount };
				helper.runAfterDelay(6, () -> {
					int cooked = cookTime(helper, furnace) - cookBefore;
					int fastTicks = fast.tickCount - before[0];
					int slowTicks = slow.tickCount - before[1];
					int plainTicks = plain.tickCount - before[2];
					int livingTicks = livingFast.tickCount - before[3];
					BlockState released = level.getBlockState(buttonPos);
					helper.assertTrue(plainTicks == 6 && fastTicks == 3 * plainTicks && 2 * slowTicks == plainTicks
							&& livingTicks == plainTicks, "Over 6 ticks a marker at rate 3 ticked " + fastTicks + " times, one at"
									+ " 0.5 " + slowTicks + ", one at 1 " + plainTicks + ", a cow given rate 3 " + livingTicks
									+ " (18, 3, 6, 6)");
					helper.assertTrue(released.getBlock() instanceof ButtonBlock && !released.getValue(ButtonBlock.POWERED),
							"A stone button pressed at a block tick rate of 5 was still pressed 6 ticks later (20 at rate 1)");
					helper.assertTrue(cooked >= 18, "A furnace at a block entity rate of 4 cooked " + cooked
							+ " ticks in 6 (24 expected, 6 at rate 1)");
					fixture.close();
					helper.succeed();
				});
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
		EntityTimeRates.register(JojoMod.resLoc("gametest_tick_rate"), (entity, channel) -> {
			if (channel != EntityTimeRates.Channel.ENTITY_TICK) {
				return EntityTimeRates.NO_RATE;
			}
			return entity.getTags().contains(FAST) ? 3.0F : entity.getTags().contains(SLOW) ? 0.5F : EntityTimeRates.NO_RATE;
		});
		LevelTimeRates.register(JojoMod.resLoc("gametest_tick_rate"), (level, channel) -> {
			if (level != ratedLevel || level.getGameTime() > ratedUntil) {
				return LevelTimeRates.NO_RATE;
			}
			return switch (channel) {
				case BLOCK_TICKS -> 5.0F;
				case BLOCK_ENTITIES -> 4.0F;
				case RANDOM_TICKS -> 2.0F;
				default -> LevelTimeRates.NO_RATE;
			};
		});
	}

	private static int cookTime(GameTestHelper helper, FurnaceBlockEntity furnace) {
		return furnace.saveWithoutMetadata(helper.getLevel().registryAccess()).getInt("CookTime");
	}

	private static final class Fixture implements GameTestListener {
		static final BlockPos FURNACE = new BlockPos(1, 1, 1);
		static final BlockPos STONE = new BlockPos(3, 1, 1);
		static final BlockPos BUTTON = new BlockPos(3, 2, 1);
		private final GameTestHelper helper;
		private final List<Entity> entities = new ArrayList<>();
		private boolean closed;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
		}

		Marker marker(Vec3 relative, @Nullable String tag) {
			Marker marker = EntityType.MARKER.create(helper.getLevel());
			helper.assertTrue(marker != null, "Could not create a marker");
			Vec3 pos = helper.absoluteVec(relative);
			marker.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			if (tag != null) {
				marker.addTag(tag);
			}
			helper.assertTrue(helper.getLevel().addFreshEntity(marker), "Could not add a marker");
			entities.add(marker);
			return marker;
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			ratedLevel = null;
			ratedUntil = Long.MIN_VALUE;
			entities.forEach(Entity::discard);
			if (helper.getLevel().getBlockEntity(helper.absolutePos(FURNACE)) instanceof FurnaceBlockEntity furnace) {
				furnace.clearContent();
			}
			helper.setBlock(BUTTON, Blocks.AIR);
			helper.setBlock(STONE, Blocks.AIR);
			helper.setBlock(FURNACE, Blocks.AIR);
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
