package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * TimeStopState#repinOrRelease: an entity an add-on moves on purpose in stopped time (a rewind, a dodge) stays where
 * it was put, while the freeze still puts back one moved without it; one moved out of every stop's reach is released
 * at once. The stop is put and removed within the test's first tick, so it holds no neighbour cell of its batch.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopRepinGameTests {
	// apart from the other time stop tests' ids: a shared id lets one test's stop replace another's in the batch
	private static int nextStopId = -7_350_000;

	private TimeStopRepinGameTests() {}

	@GameTest(template = "empty", batch = GameTestBatches.TIME_STOP)
	public static void anEntityMovedInStoppedTimeStaysWhereItWasPutOnceRepinnedAndLeavesTheFreezeOutOfItsReach(GameTestHelper helper) {
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		Cow moved = cow(helper, 1);
		Cow repinned = cow(helper, 3);
		int stop = nextStopId--;
		try {
			// range 2: the 3 x 3 chunks around the cows, so that neither move below crosses its edge
			helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(stop, 200, 200,
					new ChunkPos(repinned.blockPosition()), 2, -1, "time_stop_repin_test")), "Could not stop time");
			for (Cow cow : List.of(moved, repinned)) {
				helper.assertTrue(state.interruptTickEarly(cow), "The cow did not freeze");
			}
			Vec3 movedFrozenAt = moved.position();
			Vec3 putAt = repinned.position().add(0, 0, 1.5);
			moved.setPos(movedFrozenAt.add(0, 0, 1.5));
			repinned.setPos(putAt);
			helper.assertTrue(TimeStopState.repinOrReleaseOnServer(repinned),
					"A cow moved inside stopped time did not stay frozen");
			for (Cow cow : List.of(moved, repinned)) {
				helper.assertTrue(state.interruptTickEarly(cow), "A frozen cow ticked");
				state.reconcileFrozenEntity(cow);
			}
			helper.assertTrue(moved.position().distanceToSqr(movedFrozenAt) < 1.0E-12,
					"The freeze did not put back a cow moved without a re-pin: " + moved.position());
			helper.assertTrue(repinned.position().distanceToSqr(putAt) < 1.0E-12,
					"The re-pinned cow was put back where it froze: " + repinned.position());

			// three chunks away, out of this stop; another test of the batch may stop time there in this same tick
			Vec3 away = null;
			for (Vec3 offset : List.of(new Vec3(48, 0, 0), new Vec3(-48, 0, 0), new Vec3(0, 0, 48), new Vec3(0, 0, -48))) {
				Vec3 candidate = putAt.add(offset);
				if (helper.getLevel().hasChunkAt(BlockPos.containing(candidate))) {
					repinned.setPos(candidate);
					if (!state.shouldFreeze(repinned)) {
						away = candidate;
						break;
					}
				}
			}
			if (away != null) {
				helper.assertTrue(!TimeStopState.repinOrReleaseOnServer(repinned) && !repinned.isNoAi(),
						"A cow moved out of stopped time was not released at once (its AI back)");
			}
			else {
				JojoMod.getLogger().info("TIME-STOP-REPIN no loaded spot out of every stop; release half not checked");
			}
		}
		finally {
			state.removeInstance(stop);
			moved.discard();
			repinned.discard();
		}
		helper.succeed();
	}

	private static Cow cow(GameTestHelper helper, int x) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Could not create a cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 0, 2)));
		cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
		cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
		return cow;
	}
}
