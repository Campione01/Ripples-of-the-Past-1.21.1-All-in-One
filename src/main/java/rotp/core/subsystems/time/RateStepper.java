package rotp.core.subsystems.time;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.api.time.EntityTimeRates;

/**
 * Turns a time rate into whole steps per tick with the fraction carried, so that a rate of 1/m runs one step every
 * m ticks for any m and a rate of m runs m steps a tick; at rate 1 it is one step a tick and keeps nothing.
 */
@ApiStatus.Internal
public final class RateStepper {
	private float carry;
	private float rate = 1.0F;

	/** The steps this tick at this rate. */
	public int steps(float newRate) {
		float clamped = Math.min(newRate, EntityTimeRates.MAX_RATE);
		rate = clamped;
		if (clamped == 1.0F) {
			carry = 0.0F;
			return 1;
		}
		float total = carry + clamped;
		int steps = (int) total;
		carry = total - steps;
		return steps;
	}

	public float rate() {
		return rate;
	}

	/** The progress past the last step at a frame's partial tick, in steps: the partial tick itself at rate 1. */
	public float partialSteps(float partialTick) {
		return rate == 1.0F ? partialTick : carry + partialTick * rate;
	}

	public void reset() {
		carry = 0.0F;
		rate = 1.0F;
	}
}
