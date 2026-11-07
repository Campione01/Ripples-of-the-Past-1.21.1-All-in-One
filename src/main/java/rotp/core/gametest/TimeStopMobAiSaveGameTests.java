package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A stopped mob's old no-AI flag goes to disk with it, as 1.16 EntityUtilCap's PrevNoAi did. A mob whose chunk is saved
 * in stopped time and read back after the stop is over, or after a crash, does not stay without AI for good. Saving and
 * reading the entity's data is what a chunk unload does to it; every stop here ends within the tick that puts it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopMobAiSaveGameTests {
	private static int nextStopId = -7_330_000;

	private TimeStopMobAiSaveGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void aMobSavedInStoppedTimeGetsItsOldAiBackWhenNoStopHoldsIt(GameTestHelper helper) {
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		for (boolean wasNoAi : new boolean[] { false, true }) {
			String row = wasNoAi ? "a mob with no AI of its own" : "a mob with its AI";
			int stop = nextStopId--;
			Mob mob = spawn(helper, wasNoAi);
			Mob back = null;
			try {
				stopTime(helper, state, stop, mob);
				helper.assertTrue(mob.isNoAi(), row + ": the stop did not take its AI");
				CompoundTag saved = save(mob);
				mob.discard();
				// the stop ends while the chunk is away
				state.removeInstance(stop);
				back = load(helper, saved);
				helper.assertTrue(back.isNoAi() == wasNoAi, row + " came back from disk with no AI " + back.isNoAi()
						+ ", was " + wasNoAi);
				helper.assertTrue(!back.hasData(ModDataAttachmentTypes.TIME_STOP_PREV_NO_AI.get()),
						row + ": the saved old flag outlived the stop");
				helper.assertTrue(!save(back).toString().contains("time_stop_prev_no_ai"),
						row + ": the mob saves the old flag after the stop");
			}
			finally {
				state.removeInstance(stop);
				mob.discard();
				if (back != null) back.discard();
			}
		}
		helper.succeed();
	}

	/** Another player's stop, or the second of two over one mob, is still running when the mob comes back. */
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void aMobBackFromDiskUnderASecondStopWaitsForItAndKeepsItsOldAi(GameTestHelper helper) {
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		for (boolean wasNoAi : new boolean[] { false, true }) {
			String row = wasNoAi ? "a mob with no AI of its own" : "a mob with its AI";
			int first = nextStopId--;
			int second = nextStopId--;
			Mob mob = spawn(helper, wasNoAi);
			Mob back = null;
			try {
				stopTime(helper, state, first, mob);
				stopTime(helper, state, second, mob);
				CompoundTag saved = save(mob);
				mob.discard();
				state.removeInstance(first);
				back = load(helper, saved);
				helper.assertTrue(back.isNoAi() && state.shouldFreeze(back),
						row + ": the second stop did not hold the mob that came back");
				// saved again under the second stop, as an autosave before a crash leaves it
				Mob again = load(helper, resave(back));
				back = again;
				helper.assertTrue(back.isNoAi(), row + ": the second save and read lost the freeze");
				state.removeInstance(second);
				helper.assertTrue(back.isNoAi() == wasNoAi, row + " has no AI " + back.isNoAi() + " after the last stop"
						+ " ended, was " + wasNoAi);
			}
			finally {
				state.removeInstance(first);
				state.removeInstance(second);
				mob.discard();
				if (back != null) back.discard();
			}
		}
		helper.succeed();
	}

	private static Mob spawn(GameTestHelper helper, boolean noAi) {
		Mob mob = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(mob != null, "Could not create the cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		mob.moveTo(pos.x, pos.y, pos.z, 0, 0);
		mob.setNoAi(noAi);
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add the cow");
		return mob;
	}

	private static void stopTime(GameTestHelper helper, TimeStopState state, int id, Mob mob) {
		helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(id, 200, 200,
				new ChunkPos(mob.blockPosition()), 1, -1, "mob_ai_save_test")), "Could not stop time");
	}

	private static CompoundTag save(Mob mob) {
		CompoundTag tag = new CompoundTag();
		mob.save(tag);
		return tag;
	}

	private static CompoundTag resave(Mob mob) {
		CompoundTag tag = save(mob);
		mob.discard();
		return tag;
	}

	// the way a chunk's entities come back: EntityType reads the data, the level's join event sees the mob
	private static Mob load(GameTestHelper helper, CompoundTag saved) {
		ServerLevel level = helper.getLevel();
		Mob mob = (Mob) EntityType.create(saved, level).orElse(null);
		helper.assertTrue(mob != null, "Could not read the mob back");
		helper.assertTrue(level.addFreshEntity(mob), "Could not add the mob that was read back");
		return mob;
	}
}
