package rotp.core.client.entityrender.stand;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A block a Stand holds stays out of the middle of its user's first-person view: raised for Block Toss right by the camera,
 * it covered a third of the screen. The block is lowered on the screen until no part of it is within
 * {@link #CLEAR_HALF_ANGLE_DEGREES} of the crosshair. No client classes here, so the smoke test can load it.
 */
public final class HeldBlockViewClearance {
	/** Half-angle of the cone around the crosshair that a held block keeps out of. */
	public static final float CLEAR_HALF_ANGLE_DEGREES = 15.0F;
	/** A held block item's reach from the hand's item origin, in model units: its 0.375 cube's half-diagonal plus its lift. */
	public static final float BLOCK_RADIUS = 0.48F;
	private static final int SEARCH_STEPS = 24;

	private HeldBlockViewClearance() {}

	/**
	 * The camera-relative shift, along the camera's down direction, that moves a sphere of the given radius at the
	 * camera-relative {@code center} out of the clear cone around {@code look}; zero where it is out already or behind the
	 * camera. {@code look} and {@code up} are unit vectors at right angles.
	 */
	public static Vector3f shift(Vector3fc center, float radius, Vector3fc look, Vector3fc up) {
		float forward = center.dot(look);
		float height = center.dot(up);
		if (forward <= 0.0F) {
			return new Vector3f();
		}
		float sidewaysSqr = Math.max(center.lengthSquared() - forward * forward - height * height, 0.0F);
		if (isClear(forward, sidewaysSqr, height, radius)) {
			return new Vector3f();
		}
		// lower on the screen: the clear heights below the crosshair are one interval, found by halving
		float clear = -forward * 4.0F - radius * 4.0F;
		float blocked = Math.min(height, 0.0F);
		for (int i = 0; i < SEARCH_STEPS; i++) {
			float mid = (clear + blocked) * 0.5F;
			if (isClear(forward, sidewaysSqr, mid, radius)) {
				clear = mid;
			}
			else {
				blocked = mid;
			}
		}
		return new Vector3f(up).mul(clear - height);
	}

	static boolean isClear(float forward, float sidewaysSqr, float height, float radius) {
		float offAxis = (float) Math.sqrt(sidewaysSqr + height * height);
		float distance = (float) Math.sqrt(forward * forward + offAxis * offAxis);
		if (radius >= distance) {
			return false;
		}
		double angle = Math.atan2(offAxis, forward);
		double angularRadius = Math.asin(radius / distance);
		return angle >= Math.toRadians(CLEAR_HALF_ANGLE_DEGREES) + angularRadius;
	}
}
