package rotp.core.subsystems.time;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.ticks.ScheduledTick;
import net.neoforged.neoforge.event.EventHooks;

/**
 * A server level's share of the time rates that run its non-living things faster or slower (EntityTimeRates'
 * ENTITY_TICK, LevelTimeRates' tick channels): the steps of this tick, the extra ticks it has left, and the entity whose
 * extra ticks follow its own tick. Steps at a rate r are floor((t + 1) r) - floor(t r) at game time t, so a constant
 * rate needs no state per entity or block entity.
 */
@ApiStatus.Internal
public final class LevelTickRateState {

	/** Implemented by ServerLevel. */
	public interface Holder {
		LevelTickRateState jojo_ripples$tickRateState();
	}

	/** Implemented by LevelTicks: the level and channel whose rate scales the delays scheduled in it. */
	public interface ScheduledTicks {
		void jojo_ripples$bindTickRate(ServerLevel level, LevelTimeRates.Channel channel);

		/** The level whose block or fluid ticks these are, once bound; null before. */
		@Nullable
		ServerLevel jojo_ripples$tickLevel();

		/** Whether these are fluid ticks. */
		boolean jojo_ripples$isFluidTicks();
	}

	private long tick = Long.MIN_VALUE;
	private int extraEntityTicks;
	private int extraBlockEntityTicks;
	private int blockEntitySteps = -1;
	private int randomTickSpeedIn = -1;
	private int randomTickSpeedOut;
	@Nullable private Entity pendingEntity;
	private int pendingExtraTicks;

	public static LevelTickRateState of(ServerLevel level) {
		return ((Holder) level).jojo_ripples$tickRateState();
	}

	/** Whole steps at game time {@code time} of a clock that runs {@code perTick} steps a tick at rate 1. */
	public static int stepsAt(long time, float rate, int perTick) {
		if (rate == 1.0F) {
			return perTick;
		}
		double stepsPerTick = (double) Math.min(rate, EntityTimeRates.MAX_RATE) * perTick;
		return (int) (Math.floor((time + 1) * stepsPerTick) - Math.floor(time * stepsPerTick));
	}

	private void roll(ServerLevel level) {
		long time = level.getGameTime();
		if (time != tick) {
			tick = time;
			extraEntityTicks = 0;
			extraBlockEntityTicks = 0;
			blockEntitySteps = -1;
			randomTickSpeedIn = -1;
			pendingEntity = null;
		}
	}

	/**
	 * At the head of a non-passenger entity's server tick, once stopped time let it tick: false if its rate holds it
	 * back this tick; remembers its extra ticks for {@link #endEntityTick}.
	 */
	public static boolean beginEntityTick(ServerLevel level, Entity entity) {
		if (!EntityTimeRates.hasProviders() || entity instanceof LivingEntity || entity.isVehicle()) {
			return true;
		}
		float rate = EntityTimeRates.rate(EntityTimeRates.Channel.ENTITY_TICK, entity);
		if (rate == 1.0F) {
			return true;
		}
		LevelTickRateState state = of(level);
		state.roll(level);
		int steps = stepsAt(state.tick, rate, 1);
		if (steps <= 0) {
			return false;
		}
		if (steps > 1) {
			state.pendingEntity = entity;
			state.pendingExtraTicks = steps - 1;
		}
		return true;
	}

	/** At the tail of its server tick: the extra ticks {@link #beginEntityTick} gave it, within the level's budget. */
	public static void endEntityTick(ServerLevel level, Entity entity) {
		if (!EntityTimeRates.hasProviders()) {
			return;
		}
		LevelTickRateState state = of(level);
		state.roll(level);
		if (state.pendingEntity != entity) {
			return;
		}
		int extra = Math.min(state.pendingExtraTicks, EntityTimeRates.MAX_EXTRA_ENTITY_TICKS - state.extraEntityTicks);
		state.pendingEntity = null;
		state.pendingExtraTicks = 0;
		for (int i = 0; i < extra; i++) {
			if (entity.isRemoved() || entity.level() != level || entity.isPassenger() || entity.isVehicle()
					|| TimeStopState.shouldFreezeOnServer(entity)) {
				return;
			}
			state.extraEntityTicks++;
			entity.setOldPosAndRot();
			entity.tickCount++;
			if (!EventHooks.fireEntityTickPre(entity).isCanceled()) {
				entity.tick();
				EventHooks.fireEntityTickPost(entity);
			}
		}
	}

	/** The random tick speed a chunk of the level gets this tick. */
	public static int randomTickSpeed(ServerLevel level, int randomTickSpeed) {
		if (randomTickSpeed <= 0 || !LevelTimeRates.hasProviders()) {
			return randomTickSpeed;
		}
		LevelTickRateState state = of(level);
		state.roll(level);
		if (randomTickSpeed != state.randomTickSpeedIn) {
			state.randomTickSpeedIn = randomTickSpeed;
			state.randomTickSpeedOut = stepsAt(state.tick,
					LevelTimeRates.rate(LevelTimeRates.Channel.RANDOM_TICKS, level), randomTickSpeed);
		}
		return state.randomTickSpeedOut;
	}

	/** How many times the level's ticking block entities run this tick: 1 at rate 1, 0 while they wait. */
	public static int blockEntitySteps(ServerLevel level) {
		if (!LevelTimeRates.hasProviders()) {
			return 1;
		}
		LevelTickRateState state = of(level);
		state.roll(level);
		if (state.blockEntitySteps < 0) {
			state.blockEntitySteps = stepsAt(state.tick, LevelTimeRates.rate(LevelTimeRates.Channel.BLOCK_ENTITIES, level), 1);
		}
		return state.blockEntitySteps;
	}

	/** Takes up to {@code wanted} extra block entity ticks from the level's budget of this tick. */
	public static int takeExtraBlockEntityTicks(ServerLevel level, int wanted) {
		LevelTickRateState state = of(level);
		state.roll(level);
		int granted = Math.max(0, Math.min(wanted, LevelTimeRates.MAX_EXTRA_BLOCK_ENTITY_TICKS - state.extraBlockEntityTicks));
		state.extraBlockEntityTicks += granted;
		return granted;
	}

	/** A scheduled tick moved to {@code triggerTick}. */
	public static <T> ScheduledTick<T> rescheduled(ScheduledTick<T> tick, long triggerTick) {
		return new ScheduledTick<>(tick.type(), tick.pos(), triggerTick, tick.priority(), tick.subTickOrder());
	}
}
