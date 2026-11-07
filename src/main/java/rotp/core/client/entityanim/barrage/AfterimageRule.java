package rotp.core.client.entityanim.barrage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The rules of a Stand's barrage afterimages that need no client classes, so server gametests can run them: which swing a
 * melee barrage draws, and when in a clip an arm's fist is furthest out, which the clip's own arm keys tell.
 */
public final class AfterimageRule {
	/** The swing of a Stand whose barrage clip names none. */
	public static final String DEFAULT_SWING = "TWO_HANDED";
	private static final float ARM_SEGMENT_PX = 6;
	private static final float MIN_STROKE_PX = 1;
	private static final float TIME_EPS = 0.001F;

	private AfterimageRule() {}

	/** A clip that says "barrage = null" for a section means it; one that never says keeps the barrage's afterimages. */
	@Nullable
	public static String swingName(@Nullable String clipValue, boolean clipHasSwingKey, boolean meleeBarrage) {
		return clipValue == null && !clipHasSwingKey && meleeBarrage ? DEFAULT_SWING : clipValue;
	}

	/** Model pixels the fist of a 6 + 6 pixel arm reaches forward, for angles in radians and an arm offset in pixels. */
	public static float reach(Vector3fc armRot, Vector3fc armPos, @Nullable Vector3fc bendRot) {
		Vector3f arm = new Vector3f(0, ARM_SEGMENT_PX, 0);
		if (bendRot != null) {
			new Quaternionf().rotationZYX(bendRot.z(), bendRot.y(), bendRot.x()).transform(arm);
		}
		arm.add(0, ARM_SEGMENT_PX, 0);
		new Quaternionf().rotationZYX(armRot.z(), armRot.y(), armRot.x()).transform(arm);
		return -arm.z - armPos.z();
	}

	/**
	 * Of an arm's keys over a section (times from the section's start), the tops of its strokes: a key no lower than its
	 * neighbours, in the outer half of the arm's travel. A clip's strokes need not all reach as far. None if it does not strike.
	 */
	public static float[] strokePeaks(float[] times, float[] reach) {
		float lo = Float.POSITIVE_INFINITY;
		float hi = Float.NEGATIVE_INFINITY;
		for (float r : reach) {
			lo = Math.min(lo, r);
			hi = Math.max(hi, r);
		}
		if (times.length == 0 || hi - lo < MIN_STROKE_PX) {
			return new float[0];
		}
		float outerHalf = (lo + hi) / 2;
		float[] peaks = new float[times.length];
		int count = 0;
		for (int i = 0; i < times.length; i++) {
			if (reach[i] >= outerHalf && (i == 0 || reach[i] >= reach[i - 1]) && (i == times.length - 1 || reach[i] >= reach[i + 1])) {
				peaks[count++] = times[i];
			}
		}
		return Arrays.copyOf(peaks, count);
	}

	/** First peak at or after the given time, on through the looping section; NaN for an arm that does not strike. */
	public static float strokeAtOrAfter(float[] peaks, float loopSeconds, float notBefore) {
		if (peaks.length == 0) {
			return Float.NaN;
		}
		for (float peak : peaks) {
			if (peak >= notBefore - TIME_EPS) {
				return peak;
			}
		}
		float wrapped = peaks[0];
		if (loopSeconds > 0) {
			while (wrapped < notBefore - TIME_EPS) {
				wrapped += loopSeconds;
			}
		}
		return wrapped;
	}

	/** Sorted times of the keys inside [from, to), from the section's start. */
	public static float[] sectionKeyTimes(List<float[]> keyTimes, float from, float to) {
		List<Float> times = new ArrayList<>();
		for (float[] channel : keyTimes) {
			for (float time : channel) {
				if (time >= from - TIME_EPS && time < to - TIME_EPS) {
					float fromStart = Math.max(time - from, 0);
					if (times.stream().noneMatch(seen -> Math.abs(seen - fromStart) < TIME_EPS)) {
						times.add(fromStart);
					}
				}
			}
		}
		float[] sorted = new float[times.size()];
		for (int i = 0; i < sorted.length; i++) {
			sorted[i] = times.get(i);
		}
		Arrays.sort(sorted);
		return sorted;
	}
}
