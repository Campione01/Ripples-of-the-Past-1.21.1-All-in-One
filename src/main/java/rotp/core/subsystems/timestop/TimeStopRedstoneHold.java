package rotp.core.subsystems.timestop;

import java.util.LinkedHashSet;
import java.util.List;

import rotp.core.init.ModDataAttachmentTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The neighbour updates stopped time keeps from redstone until it resumes (the owner: "連拉桿紅石也等時間恢復"), one queue
 * for one level. Shape updates pass: they move no signal, only what a block looks like and whether it stays on, and an
 * observer's reaction to one is a scheduled tick, which stopped time holds already. Memory only, as the stops are.
 */
final class TimeStopRedstoneHold {
	static final int MAX_HELD = 4096;

	private final ServerLevel level;
	private final LinkedHashSet<HeldUpdate> held = new LinkedHashSet<>();
	private boolean gaveUp;

	TimeStopRedstoneHold(ServerLevel level) {
		this.level = level;
	}

	/** Whether this update waits for time to resume, its position being in stopped time. */
	boolean hold(BlockState receiver, BlockPos pos, Block source, BlockPos sourcePos, boolean movedByPiston) {
		if (gaveUp || !waits(receiver, pos, source)) {
			return false;
		}
		HeldUpdate update = new HeldUpdate(pos.immutable(), source, sourcePos.immutable(), movedByPiston);
		if (held.size() >= MAX_HELD && !held.contains(update)) {
			// A contraption this big reacts now, in order, and goes on live until the stops end: nothing is lost
			gaveUp = true;
			runHeld(false);
			return false;
		}
		held.add(update);
		return true;
	}

	/**
	 * Air and water have nothing to wait for. A component that lost its support comes off now, as breaking under it
	 * must show. A command block keeps running, as 1.16's did. The rest waits if it reacts to redstone, or if the
	 * change is a signal's: a modded component reacts to that one too, and a block that does not has no use for it.
	 */
	private boolean waits(BlockState receiver, BlockPos pos, Block source) {
		Block block = receiver.getBlock();
		if (receiver.isAir() || block instanceof LiquidBlock || block instanceof CommandBlock) {
			return false;
		}
		return (reactsToRedstone(block) || source.defaultBlockState().isSignalSource()) && receiver.canSurvive(level, pos);
	}

	private static boolean reactsToRedstone(Block block) {
		return block instanceof RedStoneWireBlock || block instanceof DiodeBlock || block instanceof RedstoneTorchBlock
				|| block instanceof RedstoneLampBlock || block instanceof DoorBlock || block instanceof TrapDoorBlock
				|| block instanceof FenceGateBlock || block instanceof PistonBaseBlock || block instanceof DispenserBlock
				|| block instanceof TntBlock || block instanceof NoteBlock || block instanceof BaseRailBlock
				|| block instanceof HopperBlock || block instanceof BellBlock || block instanceof CopperBulbBlock
				|| block instanceof CrafterBlock;
	}

	/** At resume: in the order they came, each reading the world as it is now, in this level. */
	void drain() {
		gaveUp = false;
		runHeld(true);
	}

	boolean hasWork() {
		return gaveUp || !held.isEmpty();
	}

	/**
	 * Through the level's own updater, which queues them behind the update it is running when one overflows the queue.
	 * getBlockState loads a chunk that went away in the stop, as any vanilla neighbour update reaching it does.
	 */
	private void runHeld(boolean stopIfTimeStopsAgain) {
		while (!held.isEmpty()) {
			if (stopIfTimeStopsAgain && level.getData(ModDataAttachmentTypes.TIME_STOP.get()).hasActiveStop()) {
				return;
			}
			HeldUpdate update = held.removeFirst();
			level.neighborChanged(level.getBlockState(update.pos()), update.pos(), update.source(), update.sourcePos(),
					update.movedByPiston());
		}
	}

	List<BlockPos> heldPositions() {
		return held.stream().map(HeldUpdate::pos).toList();
	}

	private record HeldUpdate(BlockPos pos, Block source, BlockPos sourcePos, boolean movedByPiston) {}
}
