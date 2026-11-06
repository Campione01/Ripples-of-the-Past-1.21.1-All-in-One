package rotp.core.client.entityanim;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;
import rotp.core.client.entityrender.parsemodel.gecko.GeckoModelFormat;

import net.minecraft.client.model.geom.ModelPart;

/**
 * G10, on the real rigs and clips through the real clip player: an arms-only Stand's shoulders stay at their height whatever the clip's
 * trunk does. Written as they are, the clips made for the whole body carry them off, which is what the player saw. Not the drawn game.
 */
public final class ArmsOnlyTrunkSmokeTest {
	private static final String[] CHAIN = { "stand_pos", "body_rot", "body", "torso", "torso_bend", "right_arm_xrot", "right_arm" };
	private static final float MOST_HEIGHT_OFF = 0.03F;

	private ArmsOnlyTrunkSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		KeyframesMolangEngine.init();
		Path root = Path.of(System.getProperty("user.dir"));
		// the clips that carried the arms furthest: the fault's size as written, in blocks of the model at scale 1
		check(root, "star_platinum", "barrage", 0.2F);
		check(root, "crazy_diamond", "barrage", 0.03F);
		check(root, "crazy_diamond", "heavy_punch", 0.1F);
		check(root, "gold_experience", "heavy_punch", 0.3F);
		System.out.println("Arms-only trunk smoke test passed: shoulders keep their height under the rule on 4 full-body clips");
	}

	private static void check(Path root, String stand, String clipName, float heightOffAsWritten) {
		String assets = "src/main/resources/assets/jojo_ripples/stand_skins/" + stand + "/assets/jojo_ripples/";
		JsonObject clip = read(root.resolve(assets + "animations/" + stand + ".animation.json"))
				.getAsJsonObject("animations").getAsJsonObject(clipName);
		JsonObject geo = read(root.resolve(assets + "geo/" + stand + ".geo.json"));
		RotpAnimDefinition anim = ParseGeckoAnims.parseAnim(clip);
		float end = clip.get("animation_length").getAsFloat();
		float rest = shoulderHeight(geo, null);
		float written = 0;
		float ruled = 0;
		for (float time = 0; time <= end; time += 0.02F) {
			AnimFramePose pose = anim.calcAnimPose(AnimMolangVariables.set(0, 0, 0), null, time, 1, new AnimFramePose(), new Vector3f());
			written = Math.max(written, Math.abs(shoulderHeight(geo, pose) - rest));
			for (String bone : ArmsOnlyTrunk.BONES) {
				ModelPartFrame trunk = pose.getIfPresent(bone);
				if (trunk != null) {
					ArmsOnlyTrunk.keepOnlyTheTurn(trunk.positionOffset, trunk.rotationOffset);
				}
			}
			ruled = Math.max(ruled, Math.abs(shoulderHeight(geo, pose) - rest));
		}
		String what = stand + "'s " + clipName;
		if (written < heightOffAsWritten) {
			throw new AssertionError("Fixture: " + what + " as written moves the shoulder only " + written + ", expected at least " + heightOffAsWritten);
		}
		if (ruled > MOST_HEIGHT_OFF) {
			throw new AssertionError(what + " moves an arms-only Stand's shoulder " + ruled + " blocks off its height under the trunk rule");
		}
	}

	private static float shoulderHeight(JsonObject geo, AnimFramePose pose) {
		ModelPart part = GeckoModelFormat.parseGeckoModel(geo).bakeRoot();
		PoseStack stack = new PoseStack();
		for (String bone : CHAIN) {
			part = part.getChild(bone);
			if (pose != null) {
				ModelPartFrame frame = pose.getIfPresent(bone);
				if (frame != null) {
					frame.apply(part);
				}
			}
			part.translateAndRotate(stack);
		}
		Matrix4f matrix = stack.last().pose();
		return matrix.transformPosition(new Vector3f()).y;
	}

	private static JsonObject read(Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
		catch (IOException e) {
			throw new AssertionError("Could not read " + path, e);
		}
	}
}
