package rotp.core.gametest;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import rotp.core.core.JojoMod;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.OOPMoment;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What more than one thread reaches on purpose. Pack log of 2026-11-06: a LegacyRandomSource throws when two threads
 * collide on it, and a HashMap iterated from a sound's listener throws when the client thread changes it meanwhile.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OffThreadSharedStateGameTests {
	private static final int RANDOM_DRAWS = 2_000_000;
	private static final int MAP_ROUNDS = 20_000;
	private static final int MAP_ENTRIES = 32;
	private static final int FIRST_STOP_ID = 1_500_000_000;
	private static final long JOIN_MILLIS = 10_000L;

	private OffThreadSharedStateGameTests() {}

	// In single player the client and server threads both draw from this.
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void sharedRandomHoldsUnderTwoThreads(GameTestHelper helper) throws InterruptedException {
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Runnable draws = () -> {
			try {
				for (int i = 0; i < RANDOM_DRAWS && thrown.get() == null; i++) {
					OOPMoment.RANDOM.nextInt();
				}
			}
			catch (Throwable failure) {
				thrown.compareAndSet(null, failure);
			}
		};
		Thread first = new Thread(draws, "shared-random-test-1");
		Thread second = new Thread(draws, "shared-random-test-2");
		first.start();
		second.start();
		first.join(JOIN_MILLIS);
		second.join(JOIN_MILLIS);
		helper.assertTrue(!first.isAlive() && !second.isAlive(), "the shared random's draws did not end");
		helper.assertTrue(thrown.get() == null, "two threads collided on the shared random: " + thrown.get());
		helper.succeed();
	}

	// PlaySoundEvent asks whether time is stopped here, from whatever thread played the sound.
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void clientTimeStopReadHoldsWhileTheClientThreadChangesIt(GameTestHelper helper)
			throws InterruptedException {
		ChunkPos far = new ChunkPos(1_000_000, 1_000_000);
		AtomicBoolean done = new AtomicBoolean();
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread reader = new Thread(() -> {
			try {
				while (!done.get()) {
					TimeStopState.getClientDisplayInstance(far);
				}
			}
			catch (Throwable failure) {
				thrown.compareAndSet(null, failure);
			}
		}, "client-time-stop-read-test");
		reader.start();
		try {
			for (int round = 0; round < MAP_ROUNDS && thrown.get() == null; round++) {
				for (int id = FIRST_STOP_ID; id < FIRST_STOP_ID + MAP_ENTRIES; id++) {
					TimeStopState.putClientInstance(new TimeStopState.Instance(id, 200, 200, far, 1, -1,
							"off_thread_read_gametest"));
				}
				for (int id = FIRST_STOP_ID; id < FIRST_STOP_ID + MAP_ENTRIES; id++) {
					TimeStopState.removeClientInstance(id);
				}
			}
		}
		finally {
			done.set(true);
			reader.join(JOIN_MILLIS);
			for (int id = FIRST_STOP_ID; id < FIRST_STOP_ID + MAP_ENTRIES; id++) {
				TimeStopState.removeClientInstance(id);
			}
		}
		helper.assertTrue(!reader.isAlive(), "the reader did not end");
		helper.assertTrue(thrown.get() == null,
				"reading the client's time stops failed while the client thread changed them: " + thrown.get());
		helper.succeed();
	}
}
