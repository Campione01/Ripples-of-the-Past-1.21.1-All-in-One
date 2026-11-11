package rotp.core.client.ui.hud_power;

public class WindupIndicator {
	public float value;
	public float maxValue;
	/** The value from which letting go already succeeds (a time stop's minimum charge), marked on the fill; none at or below 0. */
	public float minValue = -1;

	public void copyFrom(WindupIndicator src) {
		this.value = src.value;
		this.maxValue = src.maxValue;
		this.minValue = src.minValue;
	}

	/** The share of the full value at the mark, or -1 where there is none. */
	public float markRatio() {
		return maxValue > 0 && minValue > 0 && minValue < maxValue ? minValue / maxValue : -1;
	}

	/** The crosshair fill's height, in a crosshair icon of the given height, at this share of the full value. */
	public static float fillHeight(float ratio, float iconHeight) {
		return ratio >= 1 ? iconHeight : 3 + (iconHeight - 6) * ratio;
	}
}
