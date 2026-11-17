package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 vampire_claws.json as the Hamon and Pillar Man clips were ported (HamonLegacyTorsoClipsGameTests): the KosmX "torso"
 * part is the whole body, and the swing lives in its bends: without them the raised left leg is straight, which reads as a
 * kick. Expected values are the raw 1.16 keys (radians; limb positions are absolute model pivots).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampireClawClip116GameTests {
	private VampireClawClip116GameTests() {}

	private static final float[] RIGHT_ARM_REST = {-5F, 2F, 0F};
	private static final float[] LEFT_ARM_REST = {5F, 2F, 0F};
	private static final float[] RIGHT_LEG_REST = {-1.9F, 12F, 0.1F};
	private static final float[] LEFT_LEG_REST = {1.9F, 12F, -0.1F};

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void clawSwipeTurnsAndLungesTheWholeBodyLike116(GameTestHelper helper) {
		JsonObject bones = bones(helper);
		// tick 6: the wind-up, turned left; tick 12: the swipe, turned right and 0.54 block forward
		body(helper, bones, 0.3F, 0F, -0.10563063621520996F, -0.42252248525619507F,
				0.33104798197746277F, 0.7113263010978699F, 0.4808143377304077F);
		body(helper, bones, 0.6F, -0.10222315788269043F, -0.010222330689430237F, -0.5429186820983887F,
				0F, -1.7945476770401F, 0F);
		helper.assertTrue(!bones.has("torso"), "1.16 moved the whole body, not the port's upper-body 'torso' bone");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void clawSwipeBendsItsLimbsLike116(GameTestHelper helper) {
		JsonObject bones = bones(helper);
		// tick 2: the raised left leg has its knee bent 72 degrees
		bend(helper, bones, "left_leg_bend", 0.1F, 1.2516417503356934F);
		bend(helper, bones, "right_leg_bend", 0.1F, 0.11270752549171448F);
		// tick 6: both elbows bent for the claws; tick 12: the swiping arm's elbow at its most
		bend(helper, bones, "left_arm_bend", 0.3F, -1.7951456308364868F);
		bend(helper, bones, "right_arm_bend", 0.3F, -1.5424649715423584F);
		bend(helper, bones, "right_arm_bend", 0.6F, -2.158273458480835F);
		bend(helper, bones, "torso_bend", 0.3F, 0.33092761039733887F);
		bend(helper, bones, "torso_bend", 0.6F, -0.3385557234287262F);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void clawSwipeKeepsItsLimbAnglesAndPivotsLike116(GameTestHelper helper) {
		JsonObject bones = bones(helper);
		limb(helper, bones, "left_leg", 0.1F, -1.02133047580719F, 0.1852886974811554F, -0.17400415241718292F);
		limb(helper, bones, "right_arm", 0.6F, 1.0408977270126343F, 0.6867650151252747F, 2.013140916824341F);
		limb(helper, bones, "head", 0.6F, 0.03294925019145012F, -1.309116244316101F, 0.06501010805368423F);
		pivot(helper, bones, "right_arm", 0.3F, RIGHT_ARM_REST, -6.699014663696289F, 2.5680341720581055F, -0.7597832083702087F);
		pivot(helper, bones, "left_arm", 0.6F, LEFT_ARM_REST, 6.7874674797058105F, 1.5739965438842773F, -1.5459554195404053F);
		pivot(helper, bones, "right_leg", 0.3F, RIGHT_LEG_REST, -2.4710961818695067F, 12.211948737502098F, 0.6933591723442077F);
		pivot(helper, bones, "left_leg", 0.1F, LEFT_LEG_REST, 2.1423584461212157F, 11.909659959375858F, -0.21169222742319108F);
		helper.succeed();
	}

	private static void body(GameTestHelper helper, JsonObject bones, float time,
			float x, float y, float z, float pitch, float yaw, float roll) {
		float[] rotation = key(helper, bones, "body", "rotation", time);
		near(helper, rotation[0], (float) -Math.toDegrees(pitch), 0.0005F, "body pitch at " + time);
		near(helper, rotation[1], (float) -Math.toDegrees(yaw), 0.0005F, "body yaw at " + time);
		near(helper, rotation[2], (float) Math.toDegrees(roll), 0.0005F, "body roll at " + time);
		float[] position = key(helper, bones, "body", "position", time);
		near(helper, position[0], -16 * x, 0.0005F, "body x at " + time);
		near(helper, position[1], 16 * y, 0.0005F, "body y at " + time);
		near(helper, position[2], 16 * z, 0.0005F, "body z at " + time);
	}

	private static void bend(GameTestHelper helper, JsonObject bones, String bone, float time, float radians) {
		float[] rotation = key(helper, bones, bone, "rotation", time);
		near(helper, rotation[0], (float) Math.toDegrees(radians), 0.0005F, bone + " at " + time);
		near(helper, rotation[1], 0F, 0F, bone + " keeps to its one axis at " + time);
		near(helper, rotation[2], 0F, 0F, bone + " keeps to its one axis at " + time);
	}

	private static void limb(GameTestHelper helper, JsonObject bones, String bone, float time, float pitch, float yaw, float roll) {
		float[] rotation = key(helper, bones, bone, "rotation", time);
		near(helper, rotation[0], (float) Math.toDegrees(pitch), 0.0005F, bone + " pitch at " + time);
		near(helper, rotation[1], (float) Math.toDegrees(yaw), 0.0005F, bone + " yaw at " + time);
		near(helper, rotation[2], (float) Math.toDegrees(roll), 0.0005F, bone + " roll at " + time);
	}

	// KosmX limb positions are absolute pivots; the port keys the offset from rest, with y flipped by the JSON loader
	private static void pivot(GameTestHelper helper, JsonObject bones, String bone, float time, float[] rest, float x, float y, float z) {
		float[] position = key(helper, bones, bone, "position", time);
		near(helper, position[0], x - rest[0], 0.0005F, bone + " x at " + time);
		near(helper, position[1], -(y - rest[1]), 0.0005F, bone + " y at " + time);
		near(helper, position[2], z - rest[2], 0.0005F, bone + " z at " + time);
	}

	private static void near(GameTestHelper helper, float actual, float expected, float tolerance, String label) {
		helper.assertTrue(Math.abs(actual - expected) <= tolerance, "vampire_claws " + label + " must be " + expected + ", got " + actual);
	}

	private static float[] key(GameTestHelper helper, JsonObject bones, String bone, String channel, float time) {
		JsonObject part = bones.getAsJsonObject(bone);
		helper.assertTrue(part != null && part.has(channel), "vampire_claws lost the 1.16 " + bone + " " + channel);
		for (var entry : part.getAsJsonObject(channel).entrySet()) {
			if (Math.abs(Float.parseFloat(entry.getKey()) - time) < 0.001F) {
				JsonArray vector = entry.getValue().getAsJsonObject().getAsJsonArray("vector");
				return new float[] {vector.get(0).getAsFloat(), vector.get(1).getAsFloat(), vector.get(2).getAsFloat()};
			}
		}
		throw new IllegalStateException("vampire_claws has no " + bone + " " + channel + " key at " + time);
	}

	private static JsonObject bones(GameTestHelper helper) {
		try (InputStream in = VampireClawClip116GameTests.class.getResourceAsStream("/assets/jojo_ripples/animations/vampire.animation.json")) {
			helper.assertTrue(in != null, "Missing vampire.animation.json");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("animations").getAsJsonObject("vampire_claws").getAsJsonObject("bones");
		} catch (IOException e) {
			throw new IllegalStateException("Could not read vampire.animation.json", e);
		}
	}
}
