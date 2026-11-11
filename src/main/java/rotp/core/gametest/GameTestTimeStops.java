package rotp.core.gametest;

import java.util.concurrent.atomic.AtomicInteger;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.level.ChunkPos;

/**
 * For a game test, the core's and an add-on's alike, that needs time stopped over its cells without a Stand user who
 * stops it: a stop put with TimeStopState#tryPutInstance alone is ended by the core at its next lifecycle tick, as no
 * living user of a time stop ability holds it, so tests gave a creature The World to hold one. A stop held here has no
 * user and lasts its ticks, unless released first; it is released when the test passes, fails or times out.
 * <p>
 * A stop covers whole chunks: a test that holds one over later ticks runs in a batch of its own, so that it freezes no
 * other test's cells (the core's own tests: GameTestBatches and GameTestBatchSourcePinGameTests).
 */
public final class GameTestTimeStops {
	// apart from the ids the core's other time stop tests use
	private static final AtomicInteger NEXT_ID = new AtomicInteger(-8_400_000);

	private GameTestTimeStops() {}

	/** Stops time over the chunks within chunkRange of center (0: the whole level) for that many ticks. */
	public static Held hold(GameTestHelper helper, ChunkPos center, int chunkRange, int ticks) {
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		Held held = new Held(state, NEXT_ID.getAndDecrement());
		helper.testInfo.addListener(new GameTestListener() {
			@Override public void testStructureLoaded(GameTestInfo test) {}
			@Override public void testPassed(GameTestInfo test, GameTestRunner runner) { held.release(); }
			@Override public void testFailed(GameTestInfo test, GameTestRunner runner) { held.release(); }
			@Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { held.release(); }
		});
		helper.assertTrue(state.tryPutInstanceHeldWithoutUser(new TimeStopState.Instance(held.id, ticks, ticks, center,
				chunkRange, -1, "jojo_ripples_game_test_held_stop")), "Fixture: the held time stop was not admitted");
		return held;
	}

	public static final class Held {
		private final TimeStopState state;
		public final int id;

		private Held(TimeStopState state, int id) {
			this.state = state;
			this.id = id;
		}

		public boolean isActive() {
			return state.getInstance(id).map(TimeStopState.Instance::isActive).orElse(false);
		}

		public int ticksLeft() {
			return state.getInstance(id).map(TimeStopState.Instance::ticksLeft).orElse(0);
		}

		/** Ends the stop now; nothing when it already ended. */
		public void release() {
			if (state.getInstance(id).isPresent()) {
				state.removeInstance(id);
			}
		}
	}
}
