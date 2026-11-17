package rotp.core.gametest;

/** Gametest batch names. */
final class GameTestBatches {
	/**
	 * A batch runs its tests side by side (8 per row, 6 blocks apart, up to 50) and batches run one after another.
	 * A time stop started by another test of the same batch (1-2 chunks around it, held for several ticks) freezes
	 * every test cell it covers: a frozen user's action does not tick and a frozen target does not tick. Tests that
	 * need their user, action or target to tick in normal time run in this batch, which starts no time stop.
	 * The default (unnamed) batch is time-stop-free: every test that puts a TimeStopState instance or starts
	 * TimeStopAbility (via tryPutInstance, startTimeStopAfterHold or new TimeStopState.Instance) must instead
	 * carry batch = GameTestBatches.TIME_STOP or its specifically pinned isolated batch, so it cannot freeze a
	 * neighbour cell in the default batch.
	 * GameTestBatchSourcePinGameTests checks this rule.
	 */
	static final String NO_TIME_STOP = "jojo_ripples_no_time_stop";

	/**
	 * Batch for tests that put a TimeStopState instance or start TimeStopAbility; keeps them off the default batch.
	 * Its tests share the level's time stops, so none of them may keep a stop over its test cells past the tick that
	 * put it: a stop held there through later ticks froze the neighbour cells' mobs and beams, which then failed
	 * depending on where the random test grid fell. A test that holds its stop in place runs in an exact batch of its
	 * own; one that only needs a stop to run its course moves it far away (TimeStopState.Instance.withArea).
	 */
	static final String TIME_STOP = "jojo_ripples_time_stop";

	/** The heavy-pulse suite needs normal-time inputs between its own stopped-target cases. */
	static final String DETACHED_HEAVY_TIME_STOP = "jojo_ripples_detached_heavy_time_stop";

	/** The frozen boat's stop stays over its cell until The World's heavy punch lands. */
	static final String HELD_TIME_STOP_BOAT = "jojo_ripples_held_time_stop_boat";

	/** The eye beam scenes hold their stops over their cells for several ticks, one scene at a time. */
	static final String HELD_TIME_STOP_EYE_BEAM = "jojo_ripples_held_time_stop_eye_beam";

	// What stopped time does to the world: a level's held redstone waits for its last stop, so each test runs alone.
	static final String WORLD_RANDOM_TICKS = "jojo_ripples_time_stop_world_random_ticks";
	static final String WORLD_RANGE_COST = "jojo_ripples_time_stop_range_cost";
	static final String WORLD_DIRTY_DATA = "jojo_ripples_time_stop_dirty_data";
	static final String WORLD_VIEWER_LEFT = "jojo_ripples_time_stop_viewer_left";
	static final String WORLD_START_END_COST = "jojo_ripples_time_stop_start_end_cost";
	static final String WORLD_PISTONS = "jojo_ripples_time_stop_world_pistons";
	static final String WORLD_LEVER = "jojo_ripples_time_stop_world_lever";
	static final String WORLD_BUTTON = "jojo_ripples_time_stop_world_button";
	static final String WORLD_BUILDING = "jojo_ripples_time_stop_world_building";
	static final String WORLD_ENDS = "jojo_ripples_time_stop_world_ends";
	static final String WORLD_QUEUE = "jojo_ripples_time_stop_world_queue";

	/** The stop GameTestTimeStops holds with no user stays over its cells for several ticks. */
	static final String HELD_TIME_STOP_NO_USER = "jojo_ripples_held_time_stop_no_user";

	/** A level's tick rates act on the whole level, so the test that sets them runs alone. */
	static final String LEVEL_TICK_RATES = "jojo_ripples_level_tick_rates";

	/** The motion-rate scenes build columns and ladders above their cells and time their pigs to the tick. */
	static final String MOTION_TIME_RATES = "jojo_ripples_motion_time_rates";

	private GameTestBatches() {}
}
