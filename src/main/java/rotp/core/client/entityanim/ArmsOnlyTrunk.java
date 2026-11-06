package rotp.core.client.entityanim;

import java.util.List;

import org.joml.Vector3f;

/**
 * An arms-only Stand draws its arms alone, at its user's place, while the clip it plays may be one made for the whole body:
 * 1.16's own armsOnly clips only turn the trunk, the official clips also lean and shift it, which carried the floating arms up
 * to 0.8 blocks off their shoulders (Crazy Diamond's punch and barrage) and sank them by 0.3 (Star Platinum's barrage). So the
 * trunk of an arms-only Stand keeps only its turn. No client classes here, so server gametests can run it.
 */
public final class ArmsOnlyTrunk {
	public static final List<String> BONES = List.of("stand_pos", "body_rot", "body", "torso", "torso_bend");

	private ArmsOnlyTrunk() {}

	/** A trunk bone's clip offsets (rotation in radians) in place: no shift and no lean, the turn about the vertical stays. */
	public static void keepOnlyTheTurn(Vector3f position, Vector3f rotation) {
		position.zero();
		rotation.x = 0;
		rotation.z = 0;
	}
}
