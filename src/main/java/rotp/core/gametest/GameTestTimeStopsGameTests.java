package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTestTimeStops: a stop a test holds with no Stand user lasts over its cells, where a stop put with no living user
 * of a time stop ability ends at its next lifecycle tick; it ends when released. Own batch.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameTestTimeStopsGameTests {
	private GameTestTimeStopsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.HELD_TIME_STOP_NO_USER)
	public static void aStopHeldWithNoUserLastsUntilReleased(GameTestHelper helper) {
		ChunkPos chunk = new ChunkPos(helper.absolutePos(new BlockPos(1, 2, 1)));
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		GameTestTimeStops.Held held = GameTestTimeStops.hold(helper, chunk, 1, 100);
		helper.assertTrue(held.isActive() && state.isTimeStopped(chunk), "Fixture: the held stop is not over its chunk");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(held.isActive() && state.isTimeStopped(chunk), "Five ticks on, a stop held with no user ended: "
					+ "active " + held.isActive() + ", " + held.ticksLeft() + " ticks left");
			helper.assertTrue(held.ticksLeft() <= 96, "The held stop's time does not run: " + held.ticksLeft() + " ticks left");
			held.release();
			helper.assertTrue(!held.isActive() && !state.isTimeStopped(chunk), "A released stop still holds its chunk");
			helper.succeed();
		});
	}
}
