package rotp.core.client.entityrender.stand;

import org.joml.Vector3f;

/**
 * OQ-C07: a block the Stand holds right by its user's first-person camera (Block Toss's raised block) is lowered on the
 * screen until it keeps out of the cone around the crosshair; one already out of it, or behind the camera, stays.
 * StandItemInHandLayer applies the shift in the user's own first-person view only; what the screen shows is an in-game check.
 */
public final class HeldBlockViewClearanceSmokeTest {
	private static final Vector3f LOOK = new Vector3f(0, 0, -1);
	private static final Vector3f UP = new Vector3f(0, 1, 0);
	private static final float RADIUS = HeldBlockViewClearance.BLOCK_RADIUS * 0.9375F;

	private HeldBlockViewClearanceSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		// raised by the hand half a block ahead, a little above and to the right of the crosshair
		Vector3f raised = new Vector3f(0.25F, 0.1F, -0.5F);
		Vector3f shift = HeldBlockViewClearance.shift(raised, RADIUS, LOOK, UP);
		require(shift.y() < -0.1F && shift.x() == 0 && shift.z() == 0,
				"a block by the crosshair is lowered on the screen only: " + shift);
		Vector3f lowered = new Vector3f(raised).add(shift);
		require(edgeOffCrosshairDegrees(lowered) >= HeldBlockViewClearance.CLEAR_HALF_ANGLE_DEGREES - 0.05F,
				"the lowered block keeps out of the clear cone: its edge is " + edgeOffCrosshairDegrees(lowered) + " degrees off");
		require(edgeOffCrosshairDegrees(new Vector3f(lowered).add(0, 0.05F, 0)) < HeldBlockViewClearance.CLEAR_HALF_ANGLE_DEGREES,
				"and is lowered no further than that");

		require(HeldBlockViewClearance.shift(new Vector3f(0.4F, -1.2F, -0.8F), RADIUS, LOOK, UP).lengthSquared() == 0,
				"a block low in the view stays where its hand holds it");
		require(HeldBlockViewClearance.shift(new Vector3f(0, 0, 0.6F), RADIUS, LOOK, UP).lengthSquared() == 0,
				"a block behind the camera stays");

		// looking straight down, "lower on the screen" is along the camera's own up vector, not the world's
		Vector3f down = new Vector3f(0, -1, 0);
		Vector3f upLookingDown = new Vector3f(0, 0, -1);
		Vector3f fromAbove = HeldBlockViewClearance.shift(new Vector3f(0, -0.6F, 0), RADIUS, down, upLookingDown);
		require(fromAbove.z() > 0.1F && fromAbove.y() == 0, "looking down, the block moves toward the screen's bottom: " + fromAbove);
	}

	private static float edgeOffCrosshairDegrees(Vector3f center) {
		float forward = center.dot(LOOK);
		float offAxis = (float) Math.sqrt(Math.max(center.lengthSquared() - forward * forward, 0));
		double angle = Math.atan2(offAxis, forward);
		return (float) Math.toDegrees(angle - Math.asin(RADIUS / center.length()));
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("HeldBlockViewClearanceSmokeTest: " + message);
		}
	}
}
