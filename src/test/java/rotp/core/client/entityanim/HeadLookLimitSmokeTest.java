package rotp.core.client.entityanim;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.joml.Vector3f;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.standskin.StandSkin;
import rotp.core.util.functions.MathUtil;

/**
 * 1.16 TuskModel kept Act 4's head within 27.5 degrees up and down and 22.5 sideways. Through the real clip player: the head stops
 * there while the arms read the whole look, the limit comes from skin.json, and a Stand with none follows the whole look. Not the drawn game.
 */
public final class HeadLookLimitSmokeTest {
	private static final String ASSETS = "src/main/resources/assets/jojo_ripples/stand_skins/";
	private static final float[] DONOR_LIMIT = { 27.5F, 22.5F };

	private HeadLookLimitSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		KeyframesMolangEngine.init();
		JsonObject skinInfo = JsonParser.parseString("{\"stand_type\":\"rotp_t:tusk_act_4\",\"head_look_limit\":[27.5,22.5]}").getAsJsonObject();
		float[] limit = StandSkin.headLookLimitFrom(skinInfo);
		check(limit != null && limit[0] == DONOR_LIMIT[0] && limit[1] == DONOR_LIMIT[1], "skin.json's limit is not read as the donor's numbers");
		check(StandSkin.headLookLimitFrom(json(Path.of(ASSETS + "star_platinum/skin.json"))) == null, "Star Platinum has no limit");

		RotpAnimDefinition guard = ParseGeckoAnims.parseAnim(json(Path.of(ASSETS
				+ "hierophant_green/assets/jojo_ripples/animations/hierophant_green.animation.json"))
				.getAsJsonObject("animations").getAsJsonObject("block"));
		float seconds = 0.25F;

		AnimFramePose free = pose(guard, -60, 50, null, seconds);
		near(free, "head_rot", 0, -60, "an ordinary Stand's head up");
		near(free, "head_rot", 1, 50, "an ordinary Stand's head sideways");
		float freeArm = free.getIfPresent("left_arm").rotationOffset.x;

		AnimFramePose up = pose(guard, -60, 50, DONOR_LIMIT, seconds);
		near(up, "head_rot", 0, -27.5F, "the limited head up");
		near(up, "head_rot", 1, 22.5F, "the limited head sideways");
		if (Math.abs(up.getIfPresent("left_arm").rotationOffset.x - freeArm) > 1.0E-4F) {
			throw new AssertionError("the limit reached the arms, which read the whole look in 1.16");
		}

		AnimFramePose down = pose(guard, 60, -50, DONOR_LIMIT, seconds);
		near(down, "head_rot", 0, 27.5F, "the limited head down");
		near(down, "head_rot", 1, -22.5F, "the limited head to the other side");

		AnimFramePose inside = pose(guard, 20, -15, DONOR_LIMIT, seconds);
		near(inside, "head_rot", 0, 20, "a look inside the limit, up and down");
		near(inside, "head_rot", 1, -15, "a look inside the limit, sideways");

		AnimFramePose after = new AnimFramePose();
		guard.calcAnimPose(AnimMolangVariables.set(-60, 50, 0), null, seconds, 1, after, new Vector3f());
		near(after, "head_rot", 0, -60, "the same clip for a Stand with no limit after a limited one");

		if (RotpAnimDefinition.limitedLook(70, null, 0) != 70) {
			throw new AssertionError("a Stand with no limit lost part of its look with no clip");
		}
		if (RotpAnimDefinition.limitedLook(70, DONOR_LIMIT, 1) != 22.5F) {
			throw new AssertionError("a limited head with no clip follows past its limit");
		}
		System.out.println("Head look limit smoke test passed: 27.5 and 22.5 on the head only, an ordinary Stand follows the whole look");
	}

	private static AnimFramePose pose(RotpAnimDefinition anim, float xRot, float yRot, float[] limit, float seconds) {
		AnimFramePose frame = new AnimFramePose();
		AnimMolangVariables variables = AnimMolangVariables.set(xRot, yRot, 0);
		anim.calcAnimPose(variables, null, seconds, 1, frame, new Vector3f());
		if (limit != null) {
			anim.limitHeadLook(frame, variables, limit, null, seconds, 1);
			if (variables.xRot != xRot || variables.yRot != yRot) {
				throw new AssertionError("the limit changed the look the rest of the frame reads");
			}
		}
		return frame;
	}

	private static void near(AnimFramePose frame, String bone, int axis, float degrees, String what) {
		Vector3f rotation = frame.getIfPresent(bone).rotationOffset;
		float got = (axis == 0 ? rotation.x : rotation.y) * MathUtil.RAD_TO_DEG;
		if (Math.abs(got - degrees) > 1.0E-3F) {
			throw new AssertionError(what + ": " + got + " degrees, not " + degrees);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static JsonObject json(Path file) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + file, e);
		}
	}
}
