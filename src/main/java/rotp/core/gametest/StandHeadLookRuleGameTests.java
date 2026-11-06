package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * G20, the head rule on the core's clips: the head's net orientation follows the look at every sample, through its chain of bones, within 3 degrees.
 * The add-ons' clips have the same rule in the root build's verifyStandCrossCuttingRules. Each clip left out says why.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandHeadLookRuleGameTests {
	private static final String[] HEAD_CHAIN = { "stand_pos", "body_rot", "body", "torso", "torso_bend", "head", "head_rot" };
	private static final float[][] LOOKS = { { 0, 0 }, { 30, 45 } };
	private static final double TOLERANCE = 3;
	private static final String DEFAULT_CLIPS = "animations/stand_default.animation.json";
	private static final List<String> STANDS = List.of("star_platinum", "the_world", "crazy_diamond", "gold_experience", "magicians_red", "silver_chariot",
			"hierophant_green");
	/** Arms only: no head is drawn. unused#, _: played by nothing. A summon, a time stop and the inhale key the head on purpose in 1.16 and the official. */
	private static final Pattern LEFT_OUT = Pattern.compile("^(armsOnly_.*|unused#.*|_.*|summon\\d*|inhale|time_stop|timeStop)$");

	private static final String TURNS_OVER = "the whole body turns over by more than 100 degrees: the head goes with it, or the neck would break";
	private static final String KICK_POSE = "1.16 MagiciansRedModel's kickPose1-3 pose the head with the body's lean on purpose";
	private static final String SPLASH_POSE = "1.16 HierophantGreenModel's esPose3 turns the head for a moment of the windup on purpose";
	private static final Map<String, Map<String, String>> EXCEPTIONS = Map.of(
			"crazy_diamond", Map.of("finisher", TURNS_OVER, "grab_finisher", TURNS_OVER, "heavy_charged", TURNS_OVER),
			"magicians_red", Map.of("kick", KICK_POSE),
			"hierophant_green", Map.of("emerald_splash", SPLASH_POSE, "rangedAttack", SPLASH_POSE),
			"silver_chariot", Map.of("sweeping_attack", TURNS_OVER, "no_armor_sweeping_attack", TURNS_OVER),
			"star_platinum", Map.of("block_toss", TURNS_OVER, "grab_throw", TURNS_OVER),
			"the_world", Map.of("kick", TURNS_OVER),
			"stand_default", Map.of("grab_throw", TURNS_OVER));

	private StandHeadLookRuleGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void theCoreClipsKeepTheHeadOnTheLook(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int measured = 0;
		for (String stand : STANDS) {
			measured += measure(helper, stand, "stand_skins/" + stand + "/assets/jojo_ripples/animations/" + stand + ".animation.json", wrong);
		}
		measured += measure(helper, "stand_default", DEFAULT_CLIPS, wrong);
		helper.assertTrue(measured > 120, "setup: only " + measured + " clips were measured");
		helper.assertTrue(wrong.isEmpty(), "The head leaves the look: " + wrong);
		helper.succeed();
	}

	private static int measure(GameTestHelper helper, String stand, String asset, List<String> wrong) {
		JsonObject clips = StandClipPoses116GameTests.readJson(helper, asset).getAsJsonObject("animations");
		Map<String, String> listed = EXCEPTIONS.getOrDefault(stand, Map.of());
		Set<String> names = clips.keySet();
		for (String clip : listed.keySet()) {
			if (!names.contains(clip)) {
				wrong.add(stand + " " + clip + " is listed (" + listed.get(clip) + ") but is no clip of it");
			}
		}
		int measured = 0;
		for (Map.Entry<String, JsonElement> entry : clips.entrySet()) {
			String name = entry.getKey();
			if (LEFT_OUT.matcher(name).matches()) {
				continue;
			}
			double off = worstOff(entry.getValue().getAsJsonObject());
			measured++;
			if (listed.containsKey(name)) {
				if (off <= TOLERANCE) {
					wrong.add(stand + " " + name + " is listed (" + listed.get(name) + ") but stays on the look");
				}
			}
			else if (off > TOLERANCE) {
				wrong.add(stand + " " + name + ": " + off + " degrees");
			}
			else {
				String hand = handItsMirrorCannotHold(entry.getValue().getAsJsonObject());
				if (hand != null && !names.contains(name + hand)) {
					wrong.add(stand + " " + name + " is mirrored over a window, and the file does not write " + name + hand);
				}
			}
		}
		return measured;
	}

	// keys outside the window stay on their side, so around it the mirrored trunk does not meet the head written for the clip's own hand
	private static String handItsMirrorCannotHold(JsonObject clip) {
		if (!clip.has("timeline")) {
			return null;
		}
		String hand = null;
		boolean window = false;
		for (Map.Entry<String, JsonElement> mark : clip.getAsJsonObject("timeline").entrySet()) {
			String lines = mark.getValue().toString().replace(" ", "");
			if (lines.contains("mirror.default=LEFT")) {
				hand = "_right";
			}
			else if (lines.contains("mirror.default=RIGHT")) {
				hand = "_left";
			}
			window |= lines.contains("mirror.end") || lines.contains("mirror.start") && Float.parseFloat(mark.getKey()) > 0;
		}
		return window ? hand : null;
	}

	private static double worstOff(JsonObject clip) {
		float end = clip.has("animation_length") ? clip.get("animation_length").getAsFloat() : 0;
		double worst = 0;
		for (float[] look : LOOKS) {
			double[][] want = StandClipPoses116GameTests.rotationMatrix(new float[] { look[0], look[1], 0 });
			for (float time = 0; time <= end; time += 0.01F) {
				double[][] head = StandClipPoses116GameTests.rotationMatrix(new float[3]);
				for (String bone : HEAD_CHAIN) {
					head = StandClipPoses116GameTests.multiply(head,
							StandClipPoses116GameTests.rotationMatrix(StandClipPoses116GameTests.rotationAt(clip, bone, time, look)));
				}
				worst = Math.max(worst, StandClipPoses116GameTests.angleBetween(want, head));
			}
		}
		return worst;
	}
}
