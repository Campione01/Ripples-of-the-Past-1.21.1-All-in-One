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
 * G20, the head rule on the core's clips: the head's net orientation follows the look at every sample, through its chain of bones, within 3 degrees at rest and on held keys.
 * While the trunk moves fast the head may stay off by about a third of the trunk's turn, and it is back on the look by the clip's end.
 * The add-ons' clips have the same rule in the root build's verifyStandCrossCuttingRules. Each clip left out says why.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandHeadLookRuleGameTests {
	private static final String[] HEAD_CHAIN = { "stand_pos", "body_rot", "body", "torso", "torso_bend", "head", "head_rot" };
	private static final String[] TRUNK_CHAIN = { "stand_pos", "body_rot", "body", "torso", "torso_bend" };
	private static final String[] HIPS = { "stand_pos", "body_rot", "body" };
	private static final String[] OVER_HIPS = { "torso", "torso_bend" };
	private static final double SWAY_CAP = 15;
	private static final float[][] LOOKS = { { 0, 0 }, { 30, 45 } };
	private static final double TOLERANCE = 3;
	// gen_unify.py and gradle/stand-head-look-rule.groovy of the add-on tree: while the trunk moves, the head takes back 0.65 of its turn and may be off by the rest
	private static final double MOVING_LEAVES = 0.37;
	private static final double MOVING_SPEED = 150;
	private static final float MOVING_WINDOW = 0.05F;
	private static final int MOVING_STEPS = 4;
	private static final double MOVING_ENDS = 0.1;
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

	/** The owner's rule on the barrage's lower body: the sway goes through the hips into the legs, the chest turning over the hips by the cap at most (gen_unify.py rule 8). */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void theCoreBarragesSwayThroughTheHips(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String stand : List.of("star_platinum", "the_world", "crazy_diamond", "gold_experience")) {
			sway(helper, stand, "stand_skins/" + stand + "/assets/jojo_ripples/animations/" + stand + ".animation.json", wrong);
		}
		sway(helper, "stand_default", DEFAULT_CLIPS, wrong);
		helper.assertTrue(wrong.isEmpty(), "The sway does not reach the legs: " + wrong);
		helper.succeed();
	}

	private static void sway(GameTestHelper helper, String stand, String asset, List<String> wrong) {
		JsonObject clip = StandClipPoses116GameTests.readJson(helper, asset).getAsJsonObject("animations").getAsJsonObject("barrage");
		if (clip == null) {
			wrong.add(stand + " has no barrage clip");
			return;
		}
		float from = 0;
		float to = clip.get("animation_length").getAsFloat();
		for (Map.Entry<String, JsonElement> mark : clip.getAsJsonObject("timeline").entrySet()) {
			String lines = mark.getValue().toString().replace(" ", "");
			if (lines.contains("phase=PERFORM")) {
				from = Float.parseFloat(mark.getKey());
			}
			else if (lines.contains("phase=RECOVERY")) {
				to = Float.parseFloat(mark.getKey());
			}
		}
		double overHips = widest(clip, OVER_HIPS, from, to);
		double ofHips = widest(clip, HIPS, from, to);
		double ofChest = widest(clip, TRUNK_CHAIN, from, to);
		if (overHips > 2 * SWAY_CAP + 1) {
			wrong.add(stand + ": the chest turns " + overHips + " degrees over its hips");
		}
		if (ofChest > 10 && ofHips < 0.4 * ofChest) {
			wrong.add(stand + ": the chest turns " + ofChest + " degrees and the hips " + ofHips);
		}
	}

	private static double widest(JsonObject clip, String[] chain, float from, float to) {
		float[] look = { 0, 0 };
		int samples = 24;
		double[][][] at = new double[samples + 1][][];
		for (int i = 0; i <= samples; i++) {
			at[i] = net(clip, chain, from + (to - from) * i / samples, look);
		}
		double best = 0;
		for (int i = 0; i <= samples; i++) {
			for (int j = i + 1; j <= samples; j++) {
				best = Math.max(best, StandClipPoses116GameTests.angleBetween(at[i], at[j]));
			}
		}
		return best;
	}

	/**
	 * Rule 11 (the owner, 2026-11-12: 推廣，視線固定，頭部轉動範圍不能超出): on a blow the gaze stays on head_rot's look and the head nods and tilts as
	 * the official Star Platinum's and Crazy Diamond's do, the neck within the widest those clips turn it (gen_unify.py, official_head.py).
	 */
	private static final Pattern RULE_11 = Pattern.compile("^(barrage|punch[234]?|attack[12]|heavy_punch|heavyPunch|heavy_charged|finisher|grab_heavy_punch)$");
	private static final double[] NECK = { 57.5, 72.9, 46.2 };
	/** Blows rule 11 leaves as they were, with official-head-scales.json's reason. */
	/** Stands rule 11 leaves alone (official_head.STAND_OUT). */
	private static final Map<String, String> RULE_11_OUT = Map.of("magicians_red", "1.16 MagiciansRedModel's punch and heavy poses key the head in root space, pinned by the 1.16 pose tests");
	private static final String APART = "the body comes apart more than before even with the gaze held and no nod or tilt";
	private static final Map<String, Map<String, String>> RULE_11_KEPT = Map.of(
			"crazy_diamond", Map.of("heavy_punch", APART, "grab_heavy_punch", APART),
			"gold_experience", Map.of("heavy_punch", APART, "heavyPunch", APART));

	/** Where a clip's head keys turn the neck past NECK, or its head_rot does not read the look: null if neither. */
	static String neckPastItsRange(JsonObject clip) {
		JsonObject bones = clip.getAsJsonObject("bones");
		JsonElement look = bones.has("head_rot") ? bones.getAsJsonObject("head_rot").get("rotation") : null;
		if (look == null || !look.toString().contains("query.head_x_rotation") || !look.toString().contains("query.head_y_rotation")) {
			return "head_rot does not read the look";
		}
		JsonElement head = bones.has("head") ? bones.getAsJsonObject("head").get("rotation") : null;
		if (head == null || !head.isJsonObject()) {
			return null;
		}
		List<JsonElement> keys = new ArrayList<>();
		if (head.getAsJsonObject().has("vector")) {
			keys.add(head);
		}
		else {
			head.getAsJsonObject().entrySet().forEach(e -> keys.add(e.getValue()));
		}
		for (JsonElement key : keys) {
			JsonElement vector = key.isJsonObject() ? key.getAsJsonObject().get("vector") : key;
			for (int axis = 0; axis < 3; axis++) {
				JsonElement cell = vector.getAsJsonArray().get(axis);
				if (cell.isJsonPrimitive() && cell.getAsJsonPrimitive().isNumber() && Math.abs(cell.getAsDouble()) > NECK[axis] + 1e-3) {
					return "the neck turns " + cell.getAsDouble() + " degrees on axis " + axis + ", past " + NECK[axis];
				}
			}
		}
		return null;
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
			if (RULE_11.matcher(name).matches() && !listed.containsKey(name) && !RULE_11_KEPT.getOrDefault(stand, Map.of()).containsKey(name)
					&& !RULE_11_OUT.containsKey(stand)) {
				measured++;
				String neck = neckPastItsRange(entry.getValue().getAsJsonObject());
				if (neck != null) {
					wrong.add(stand + " " + name + ": " + neck);
				}
				continue;
			}
			double past = worstPastItsBand(entry.getValue().getAsJsonObject());
			measured++;
			if (listed.containsKey(name)) {
				if (past <= 0) {
					wrong.add(stand + " " + name + " is listed (" + listed.get(name) + ") but stays on the look");
				}
			}
			else if (past > 0) {
				wrong.add(stand + " " + name + ": " + past + " degrees past its band");
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

	static double worstPastItsBand(JsonObject clip) {
		float end = clip.has("animation_length") ? clip.get("animation_length").getAsFloat() : 0;
		double worst = -TOLERANCE;
		double[][] restTrunk = StandClipPoses116GameTests.rotationMatrix(new float[3]);
		for (float[] look : LOOKS) {
			double[][] want = StandClipPoses116GameTests.rotationMatrix(new float[] { look[0], look[1], 0 });
			for (float time = 0; time <= end; time += 0.01F) {
				double[][] head = net(clip, HEAD_CHAIN, time, look);
				double[][] trunk = net(clip, TRUNK_CHAIN, time, look);
				double path = 0;
				double[][] last = net(clip, TRUNK_CHAIN, Math.max(0, time - MOVING_WINDOW), look);
				for (int i = 1; i <= MOVING_STEPS; i++) {
					double[][] step = net(clip, TRUNK_CHAIN, Math.min(end, Math.max(0, time - MOVING_WINDOW + 2 * MOVING_WINDOW * i / MOVING_STEPS)), look);
					path += StandClipPoses116GameTests.angleBetween(last, step);
					last = step;
				}
				double speed = path / (2 * MOVING_WINDOW);
				double moving = Math.min(1, speed / MOVING_SPEED) * Math.max(0, Math.min(1, Math.min(time, end - time) / MOVING_ENDS));
				double allowed = TOLERANCE + MOVING_LEAVES * StandClipPoses116GameTests.angleBetween(trunk, restTrunk) * moving;
				worst = Math.max(worst, StandClipPoses116GameTests.angleBetween(want, head) - allowed);
			}
		}
		return worst;
	}

	private static double[][] net(JsonObject clip, String[] chain, float time, float[] look) {
		double[][] net = StandClipPoses116GameTests.rotationMatrix(new float[3]);
		for (String bone : chain) {
			net = StandClipPoses116GameTests.multiply(net, StandClipPoses116GameTests.rotationMatrix(StandClipPoses116GameTests.rotationAt(clip, bone, time, look)));
		}
		return net;
	}
}
