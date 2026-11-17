package rotp.core.client.entityanim;

import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The bones of the official player rig ({@code geo_rotp/player_anim.geo.json}, its user template
 * {@code gecko player anim.bbmodel}) that our player model has no part for, and how a clip keyed on them is read: a
 * bone between two of ours is folded into the one we keep, as the rig would have stacked their transforms, and two
 * item bones only differ in name. The other bones are the same as ours: body, torso, torso_bend, head, left_arm,
 * right_arm, left_leg, right_leg, their _bend bones, cape and cape_bend. Bones of the official rig that are neither
 * (waist, neck, torso_no_arms, torso_lower, the shoulders, hips) move nothing here.
 * <p>
 * No client classes, so game tests can check the fold on the server.
 */
public final class OfficialPlayerBones {
	private OfficialPlayerBones() {}

	/**
	 * {@code outer} is the parent bone and {@code inner} its child in the official rig; {@code kept} is the one of them
	 * our model has. {@code innerPivotFromOuter}: the child's pivot less the parent's, in model part units (y down).
	 */
	public record Fold(String outer, String inner, String kept, Vector3f innerPivotFromOuter) {}

	public static final List<Fold> FOLDS = List.of(
			// body_rot, pivot (0, 24, 0) in Blockbench, turns the whole body about its feet; body's pivot is (0, 12, 0)
			new Fold("body_rot", "body", "body", new Vector3f(0, 12, 0)),
			new Fold("head", "head_rot", "head", new Vector3f()),
			new Fold("left_arm_xrot", "left_arm", "left_arm", new Vector3f()),
			new Fold("right_arm_xrot", "right_arm", "right_arm", new Vector3f()),
			new Fold("left_leg_xrot", "left_leg", "left_leg", new Vector3f()),
			new Fold("right_leg_xrot", "right_leg", "right_leg", new Vector3f()));

	public static final Map<String, String> RENAMED = Map.of(
			"left_item", "leftItem",
			"right_item", "rightItem");

	public static boolean isOfficialOnly(String bone) {
		if (RENAMED.containsKey(bone)) {
			return true;
		}
		for (Fold fold : FOLDS) {
			if (fold.outer.equals(bone) && !fold.kept.equals(bone) || fold.inner.equals(bone) && !fold.kept.equals(bone)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * One part that does what a parent part and its child did together. A part draws as translate(position), then
	 * rotate(Z, Y, X), so parent then child is translate(tP) R_P translate(c + tC) R_C, where c is the child's pivot
	 * from the parent's: one part with the child's pivot does the same with R = R_P R_C and position offset
	 * tP + R_P (c + tC) - c. Rotations are model part angles in radians, positions offsets in model part units.
	 */
	public static void fold(Vector3f outerRot, Vector3f outerPos, Vector3f innerRot, Vector3f innerPos,
			Vector3f innerPivotFromOuter, Vector3f destRot, Vector3f destPos) {
		Quaternionf outer = new Quaternionf().rotationZYX(outerRot.z, outerRot.y, outerRot.x);
		Quaternionf inner = new Quaternionf().rotationZYX(innerRot.z, innerRot.y, innerRot.x);
		Vector3f pos = new Vector3f(innerPivotFromOuter).add(innerPos);
		outer.transform(pos);
		pos.add(outerPos).sub(innerPivotFromOuter);
		// JOML 1.10.5's getEulerAnglesZYX does not invert rotationZYX; Rz Ry Rx inverted is Rx Ry Rz of the negated angles
		outer.mul(inner).conjugate().getEulerAnglesXYZ(destRot);
		destRot.negate();
		destPos.set(pos);
	}
}
