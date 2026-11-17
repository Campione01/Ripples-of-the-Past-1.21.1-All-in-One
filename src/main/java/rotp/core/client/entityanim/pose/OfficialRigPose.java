package rotp.core.client.entityanim.pose;

import java.util.Map;

import org.joml.Vector3f;

import rotp.core.client.entityanim.OfficialPlayerBones;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;

/** A player pose keyed on the official rig's bones, put onto ours (OfficialPlayerBones). */
public final class OfficialRigPose {
	private OfficialRigPose() {}

	public static void remap(AnimFramePose pose) {
		if (pose.pose.isEmpty() || pose.pose.keySet().stream().noneMatch(OfficialPlayerBones::isOfficialOnly)) {
			return;
		}
		for (Map.Entry<String, String> renamed : OfficialPlayerBones.RENAMED.entrySet()) {
			ModelPartFrame official = pose.pose.remove(renamed.getKey());
			if (official != null) {
				official.copyTo(pose.getForModelPart(renamed.getValue()));
				// the pose keeps its removed frames for reuse, and a clip may key only some channels of one
				official.clear();
			}
		}
		for (OfficialPlayerBones.Fold fold : OfficialPlayerBones.FOLDS) {
			String gone = fold.kept().equals(fold.outer()) ? fold.inner() : fold.outer();
			ModelPartFrame goneFrame = pose.pose.remove(gone);
			if (goneFrame == null) {
				continue;
			}
			ModelPartFrame kept = pose.getForModelPart(fold.kept());
			ModelPartFrame outer = gone.equals(fold.outer()) ? goneFrame : kept;
			ModelPartFrame inner = gone.equals(fold.inner()) ? goneFrame : kept;
			Vector3f rot = new Vector3f();
			Vector3f pos = new Vector3f();
			OfficialPlayerBones.fold(outer.rotationOffset, outer.positionOffset, inner.rotationOffset, inner.positionOffset,
					fold.innerPivotFromOuter(), rot, pos);
			kept.rotationOffset.set(rot);
			kept.positionOffset.set(pos);
			goneFrame.clear();
		}
	}
}
