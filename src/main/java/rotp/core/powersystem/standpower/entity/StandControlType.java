package rotp.core.powersystem.standpower.entity;

import javax.annotation.Nullable;

public enum StandControlType {
	CLOSE_RANGE_DIRECT,
	LONG_DISTANCE_OPERATION,
	AUTOMATIC,
	COLONY,
	PHENOMENON,
	HYBRID_FORM;

	/**
	 * The core's manual control for a Stand of this type that leaves the field out: as 1.16's StandEntity, which any
	 * user could take over, for the types that allow it.
	 */
	public boolean defaultManualControl() {
		return this == CLOSE_RANGE_DIRECT || this == LONG_DISTANCE_OPERATION || this == HYBRID_FORM;
	}

	/** The core's distance strength decay for a Stand of this type that leaves the field out. */
	public boolean defaultDistanceStrengthDecay() {
		return this == CLOSE_RANGE_DIRECT;
	}

	static void validate(
			StandControlType controlType,
			double effectiveRange,
			double rangeMax,
			boolean manualControl,
			boolean distanceStrengthDecay) {
		validate(controlType, effectiveRange, rangeMax,
				manualControl, distanceStrengthDecay, false);
	}

	static void validate(
			StandControlType controlType,
			double effectiveRange,
			double rangeMax,
			boolean manualControl,
			boolean distanceStrengthDecay,
			boolean colonyCoordinatorManualControl) {
		String problem = problem(controlType, effectiveRange, rangeMax,
				manualControl, distanceStrengthDecay, colonyCoordinatorManualControl);
		if (problem != null) {
			throw new IllegalStateException(problem);
		}
	}

	/** What {@link #validate} refuses, null for a valid policy. */
	@Nullable
	static String problem(
			StandControlType controlType,
			double effectiveRange,
			double rangeMax,
			boolean manualControl,
			boolean distanceStrengthDecay,
			boolean colonyCoordinatorManualControl) {
		if (controlType == null) {
			return "standControlType is required";
		}
		if (colonyCoordinatorManualControl && controlType != COLONY) {
			return "Dedicated colony coordinator control requires COLONY";
		}
		if (!Double.isFinite(effectiveRange) || effectiveRange <= 0.0D) {
			return "effectiveRange must be finite and positive";
		}
		if (!Double.isFinite(rangeMax) || rangeMax < effectiveRange) {
			return "rangeMax must be finite and at least effectiveRange";
		}
		if (!manualControl && distanceStrengthDecay) {
			return "distanceStrengthDecay requires manualControl";
		}

		switch (controlType) {
		case CLOSE_RANGE_DIRECT -> {
			if (!manualControl || !distanceStrengthDecay) {
				return "CLOSE_RANGE_DIRECT requires manualControl and distanceStrengthDecay";
			}
			if (effectiveRange >= rangeMax) {
				return "CLOSE_RANGE_DIRECT requires a non-empty decay interval";
			}
		}
		case LONG_DISTANCE_OPERATION -> {
			if (!manualControl || distanceStrengthDecay) {
				return "LONG_DISTANCE_OPERATION requires manualControl without distanceStrengthDecay";
			}
		}
		case AUTOMATIC, PHENOMENON -> {
			if (manualControl || distanceStrengthDecay) {
				return controlType + " cannot use generic manual control or distance decay";
			}
		}
		case COLONY -> {
			if (distanceStrengthDecay || manualControl && !colonyCoordinatorManualControl) {
				return "COLONY cannot use generic manual control or distance decay";
			}
		}
		case HYBRID_FORM -> {
			// Hybrid forms own their action-specific policy outside this generic entity-body gate.
		}
		}
		return null;
	}
}
