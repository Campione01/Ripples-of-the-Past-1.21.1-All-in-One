package rotp.core.client.entityanim.barrage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.molang.AnimMolangQuery;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.molang.animelement.AnimationChannelQuery;
import rotp.core.client.entityanim.molang.animelement.IAnimationChannel;
import rotp.core.client.entityanim.molang.animelement.KeyframeQuery;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * One arm of a Stand's rig as its afterimages draw it, built once per model. The bones from the root to the arm are walked
 * for their transform and never drawn, so a rig with cubes on the way (a torso bone above the shoulder) sends no copy of
 * them into the air; what the arm carries, shoulder plates and spikes included, is drawn as the frame left it shown. A
 * pose touches only these bones, however many the rig has.
 */
public final class AfterimageArm {
	private static final Vector3f SCRATCH = new Vector3f();

	private final ModelPart arm;
	private final ModelPart[] toArm;
	private final ModelPart[] parts;
	private final String[] names;
	private final boolean[] hidden;
	private final Map<RotpAnimDefinition, ClipChannels> clips = new IdentityHashMap<>();

	private AfterimageArm(ModelPart arm, List<ModelPart> toArm, List<ModelPart> parts, List<String> names,
			@Nullable Collection<ModelPart> initiallyHidden) {
		this.arm = arm;
		this.toArm = toArm.toArray(ModelPart[]::new);
		this.parts = parts.toArray(ModelPart[]::new);
		this.names = names.toArray(String[]::new);
		this.hidden = new boolean[this.parts.length];
		for (int i = 0; i < this.parts.length; i++) {
			hidden[i] = initiallyHidden != null && initiallyHidden.contains(this.parts[i]);
		}
	}

	/** Null when the arm is not under the root. */
	@Nullable
	public static AfterimageArm of(ModelPart root, ModelPart arm, @Nullable Collection<ModelPart> initiallyHidden) {
		List<ModelPart> toArm = new ArrayList<>();
		List<String> names = new ArrayList<>();
		if (!findPath(root, "", arm, toArm, names)) {
			return null;
		}
		List<ModelPart> parts = new ArrayList<>(toArm);
		for (Map.Entry<String, ModelPart> child : toArm.get(toArm.size() - 1).children.entrySet()) {
			if (child.getValue() == arm) {
				addTree(arm, child.getKey(), parts, names);
				break;
			}
		}
		return new AfterimageArm(arm, toArm, parts, names, initiallyHidden);
	}

	// root first, the arm's parent last
	private static boolean findPath(ModelPart from, String fromName, ModelPart target, List<ModelPart> path, List<String> names) {
		path.add(from);
		names.add(fromName);
		for (Map.Entry<String, ModelPart> child : from.children.entrySet()) {
			if (child.getValue() == target || findPath(child.getValue(), child.getKey(), target, path, names)) {
				return true;
			}
		}
		path.remove(path.size() - 1);
		names.remove(names.size() - 1);
		return false;
	}

	private static void addTree(ModelPart part, String name, List<ModelPart> parts, List<String> names) {
		parts.add(part);
		names.add(name);
		for (Map.Entry<String, ModelPart> child : part.children.entrySet()) {
			addTree(child.getValue(), child.getKey(), parts, names);
		}
	}

	public ModelPart arm() {
		return arm;
	}

	/** Bones a pose touches. */
	public int partCount() {
		return parts.length;
	}

	/** The clip's pose at this time on these bones alone. */
	public void pose(RotpAnimDefinition clip, @Nullable AnimMolangVariables molang, float seconds) {
		ClipChannels channels = clips.get(clip);
		if (channels == null) {
			channels = new ClipChannels(clip, names);
			clips.put(clip, channels);
		}
		channels.evaluateQueries(molang, seconds);
		for (int i = 0; i < parts.length; i++) {
			ModelPart part = parts[i];
			List<IAnimationChannel> keyed = channels.ofPart[i];
			part.resetPose();
			if (hidden[i]) {
				// HiddenModelPartsUtil: a hidden. bone shows while the clip keys it
				part.visible = keyed != null;
			}
			if (keyed != null) {
				for (int c = 0; c < keyed.size(); c++) {
					IAnimationChannel channel = keyed.get(c);
					Vector3f value = RotpAnimDefinition.calcVec(clip, channel, seconds, 1, SCRATCH);
					AnimationChannel.Target target = channel.target();
					if (target == AnimationChannel.Targets.ROTATION) part.offsetRotation(value);
					else if (target == AnimationChannel.Targets.POSITION) part.offsetPos(value);
					else if (target == AnimationChannel.Targets.SCALE) part.offsetScale(value);
				}
			}
		}
	}

	/** Moves the pose to the arm's parent. Render the arm next, inside the caller's pushed pose. */
	public ModelPart enter(PoseStack poseStack) {
		for (ModelPart part : toArm) {
			part.translateAndRotate(poseStack);
		}
		return arm;
	}

	private static final class ClipChannels {
		final List<IAnimationChannel>[] ofPart;
		final KeyframeQuery[] queries;

		@SuppressWarnings("unchecked")
		ClipChannels(RotpAnimDefinition clip, String[] names) {
			ofPart = new List[names.length];
			List<KeyframeQuery> found = new ArrayList<>();
			for (int i = 0; i < names.length; i++) {
				ofPart[i] = clip.boneChannels(names[i]);
				if (ofPart[i] != null) {
					for (IAnimationChannel channel : ofPart[i]) {
						if (channel instanceof AnimationChannelQuery query) {
							for (KeyframeQuery keyframe : query.rotpKeyframes()) {
								if (!keyframe.isNumericLiteral()) {
									found.add(keyframe);
								}
							}
						}
					}
				}
			}
			queries = found.toArray(KeyframeQuery[]::new);
		}

		void evaluateQueries(@Nullable AnimMolangVariables molang, float seconds) {
			if (queries.length == 0) {
				return;
			}
			if (molang != null) AnimMolangQuery.instance.fillContext(molang, seconds);
			else AnimMolangQuery.instance.reset(seconds);
			for (KeyframeQuery query : queries) {
				query.evaluate();
			}
		}
	}
}
