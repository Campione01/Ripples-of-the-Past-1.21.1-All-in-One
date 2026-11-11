package rotp.core.subsystems.timestop;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;

/**
 * Owner M0157: redstone waits for time to resume, then runs as normal. A scheduled block or fluid tick in a stopped chunk
 * keeps the delay it had left: one that comes due in the stop is still put off every tick there (so a chunk saved meanwhile
 * keeps it), and at resume it is replaced by one that delay later; one scheduled in the stop and due after it is moved by
 * the time it spent stopped. A stone button pressed in a long stop so stays pressed its full time after it, where it gave
 * a pulse. What this does not know (a tick from before the stop and due after it, or one in a chunk unloaded meanwhile)
 * runs as before.
 */
final class TimeStopScheduledTickHold {
	private record Key(long pos, Object type, boolean fluid) {}
	private record Scheduled(long scheduledAt, long triggerTick) {}

	private final ServerLevel level;
	private final Map<Key, Scheduled> scheduledInStop = new LinkedHashMap<>();
	private final Map<Key, Integer> delayLeft = new LinkedHashMap<>();

	TimeStopScheduledTickHold(ServerLevel level) {
		this.level = level;
	}

	boolean hasWork() {
		return !scheduledInStop.isEmpty() || !delayLeft.isEmpty();
	}

	void noteScheduled(BlockPos pos, Object type, boolean fluid, long triggerTick) {
		scheduledInStop.putIfAbsent(new Key(pos.asLong(), type, fluid), new Scheduled(level.getGameTime(), triggerTick));
	}

	/** A tick come due in stopped time: the delay it had left when time stopped for it, kept from its first time due. */
	void noteDue(BlockPos pos, Object type, boolean fluid, long stopStart) {
		Key key = new Key(pos.asLong(), type, fluid);
		if (delayLeft.containsKey(key)) {
			return;
		}
		long now = level.getGameTime();
		Scheduled scheduled = scheduledInStop.remove(key);
		long from = scheduled != null ? Math.max(scheduled.scheduledAt(), stopStart) : stopStart;
		delayLeft.put(key, (int) Math.max(now - from, 1));
	}

	@SuppressWarnings("unchecked")
	void drain() {
		long now = level.getGameTime();
		Map<Key, Integer> due = new LinkedHashMap<>(delayLeft);
		delayLeft.clear();
		for (Map.Entry<Key, Scheduled> entry : scheduledInStop.entrySet()) {
			Scheduled scheduled = entry.getValue();
			if (scheduled.triggerTick() > now) {
				due.putIfAbsent(entry.getKey(), (int) Math.max(scheduled.triggerTick() - scheduled.scheduledAt(), 1));
			}
		}
		scheduledInStop.clear();
		for (Map.Entry<Key, Integer> entry : due.entrySet()) {
			Key key = entry.getKey();
			BlockPos pos = BlockPos.of(key.pos());
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (key.fluid()) {
				replace((LevelTicks<Fluid>) level.getFluidTicks(), pos, (Fluid) key.type(), fluid -> level.scheduleTick(pos, fluid, entry.getValue()));
			}
			else {
				replace((LevelTicks<Block>) level.getBlockTicks(), pos, (Block) key.type(), block -> level.scheduleTick(pos, block, entry.getValue()));
			}
		}
	}

	private static <T> void replace(LevelTicks<T> ticks, BlockPos pos, T type, java.util.function.Consumer<T> schedule) {
		if (ticks.hasScheduledTick(pos, type)) {
			ticks.clearArea(BoundingBox.fromCorners(pos, pos));
			schedule.accept(type);
		}
	}
}
