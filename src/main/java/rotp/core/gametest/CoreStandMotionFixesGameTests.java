package rotp.core.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.joml.Vector3f;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.client.entityanim.ArmsOnlyTrunk;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;
import rotp.core.impl.stands.starplatinum.StarInhaleAbility.InhaleAbilityInstance;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the player's list of 2026-11-05 said of the core Stands' motions: G1 and G21 the barrages, G2 the finger's start, G3 the mouth of
 * Star Inhale (its place: StarSpecialtyOffsetGameTests), G10 the arms-only trunk (its numbers on the real rigs: ArmsOnlyTrunkSmokeTest),
 * G12 and G21 the head icons. The drawn look stays for the in-game pass.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreStandMotionFixesGameTests {
	private static final String SP_ANIMS = "stand_skins/star_platinum/assets/jojo_ripples/animations/star_platinum.animation.json";
	private static final String CD_ANIMS = "stand_skins/crazy_diamond/assets/jojo_ripples/animations/crazy_diamond.animation.json";
	/** Star Platinum's hair is not symmetric in 1.16's icon or in the official's. */
	private static final List<String> SYMMETRIC_ICONS = List.of("the_world", "crazy_diamond", "gold_experience", "magicians_red",
			"silver_chariot", "hierophant_green");

	private CoreStandMotionFixesGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theBarragesKeepTheHeadOnTheLook(GameTestHelper helper) {
		for (String asset : List.of(SP_ANIMS, CD_ANIMS)) {
			JsonObject clip = StandClipPoses116GameTests.readJson(helper, asset).getAsJsonObject("animations").getAsJsonObject("barrage");
			// the owner lowered this rule while the trunk moves (為了動作流暢性優先級可以下調): the head may leave the look by its band, as StandHeadLookRuleGameTests holds it
			double past = StandHeadLookRuleGameTests.worstPastItsBand(clip);
			helper.assertTrue(past <= 0, asset + ": the head is " + past + " degrees past its band");
		}
		helper.succeed();
	}

	// G1: 731e5d41 had stilled them: OfficialClips1211GameTests pins the keys, this says what they are for
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void starPlatinumsBarrageMovesItsLegs(GameTestHelper helper) {
		JsonObject clip = StandClipPoses116GameTests.readJson(helper, SP_ANIMS).getAsJsonObject("animations").getAsJsonObject("barrage");
		float[] level = new float[2];
		for (String leg : List.of("left_leg", "right_leg", "left_leg_bend", "right_leg_bend")) {
			float[] first = StandClipPoses116GameTests.rotationAt(clip, leg, 0.4F, level);
			float[] second = StandClipPoses116GameTests.rotationAt(clip, leg, 0.5F, level);
			float apart = 0;
			for (int i = 0; i < 3; i++) {
				apart = Math.max(apart, Math.abs(first[i] - second[i]));
			}
			helper.assertTrue(apart >= 1, leg + " stands still between the barrage's beats at 0.4 and 0.5 s: " + apart + " degrees");
		}
		helper.succeed();
	}

	/** G21: Crazy Diamond's barrage had numbers on head_rot and no pitch on the arms, the only ordinary barrage of the core so. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void crazyDiamondsBarrageFollowsTheLook(GameTestHelper helper) {
		JsonObject clip = StandClipPoses116GameTests.readJson(helper, CD_ANIMS).getAsJsonObject("animations").getAsJsonObject("barrage");
		float end = clip.get("animation_length").getAsFloat();
		float[] look = { 30, 45 };
		for (float time = 0; time <= end; time += 0.01F) {
			float[] headRot = StandClipPoses116GameTests.rotationAt(clip, "head_rot", time, look);
			helper.assertTrue(headRot[0] == 30 && headRot[1] == 45, "head_rot leaves the look at " + time + " s: " + Arrays.toString(headRot));
		}
		for (String arm : List.of("left_arm_xrot", "right_arm_xrot")) {
			for (float time = 0.3F; time <= 2.3F; time += 0.05F) {
				float[] pitch = StandClipPoses116GameTests.rotationAt(clip, arm, time, look);
				helper.assertTrue(pitch[0] == 30, arm + " does not take the look's pitch at " + time + " s: " + Arrays.toString(pitch));
			}
		}
		// the same forearm direction as the clip this came from, with no turn of the fist about the forearm
		for (Map.Entry<String, JsonElement> key : clip.getAsJsonObject("bones").getAsJsonObject("left_arm_bend").getAsJsonObject("rotation").entrySet()) {
			float time = Float.parseFloat(key.getKey());
			if (time > 0.25F && time < 2.35F) {
				helper.assertTrue(key.getValue().getAsJsonObject().getAsJsonArray("vector").get(1).getAsFloat() == 0,
						"left_arm_bend turns the fist about the forearm at " + time + " s");
			}
		}
		for (String[] held : new String[][] { { "right_arm", "2" }, { "right_leg", "0" } }) {
			int axis = Integer.parseInt(held[1]);
			float[] atWindupEnd = StandClipPoses116GameTests.channelAt(clip, held[0], "position", 0.3F, look);
			float[] atRecovery = StandClipPoses116GameTests.channelAt(clip, held[0], "position", 2.3F, look);
			helper.assertTrue(atWindupEnd[axis] != 0 && atWindupEnd[axis] == atRecovery[axis], held[0]
					+ " does not hold its loop's place from the end of the windup to the recovery: " + atWindupEnd[axis] + ", " + atRecovery[axis]);
		}
		helper.succeed();
	}

	// G2: 1.16's finger left from the fist of its pose, and its origin did not pitch
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theFingerStartsAtTheHeightOfThe116Fist(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "StarFingerStart")) {
			StandEntity stand = scene.stand;
			helper.assertTrue(Math.abs(stand.getEyeHeight() - 1.62F) < 1.0E-4F, "Fixture: the Stand's eyes are at " + stand.getEyeHeight());
			for (float pitch : new float[] { 0, 30, -60 }) {
				stand.setYRot(0);
				stand.setXRot(pitch);
				stand.yRotO = 0;
				stand.xRotO = pitch;
				stand.yBodyRot = 0;
				stand.yBodyRotO = 0;
				SPStarFingerEntity finger = new SPStarFingerEntity(stand, helper.getLevel());
				Vec3 start = finger.position().subtract(stand.position());
				// 1.16: its eyes at 0.85 x 2.1 = 1.785, its offset -0.2 down, 0.3 to the right, 0.75 ahead
				helper.assertTrue(Math.abs(start.y - 1.585) < 1.0E-4 && Math.abs(start.x + 0.3) < 1.0E-4 && Math.abs(start.z - 0.75) < 1.0E-4,
						"The finger starts at " + start + " from the Stand at pitch " + pitch + ", 1.16's at (-0.3, 1.585, 0.75)");
				Vec3 origin = finger.getOriginPoint(1.0F).subtract(stand.position());
				helper.assertTrue(origin.distanceTo(start) < 1.0E-4, "The drawn finger's origin " + origin + " is not its start " + start);
				finger.discard();
			}
		}
		helper.succeed();
	}

	// G3: the mouth the air flows to is the drawn one of a Stand drawn 2.1 high on a box 1.8 high
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void starInhaleDrawsToTheMouth(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "StarInhaleMouth")) {
			StandEntity stand = scene.stand;
			stand.setYRot(0);
			stand.setXRot(0);
			Vec3 mouth = InhaleAbilityInstance.mouthPosition(stand).subtract(stand.position());
			double height = stand.getBbHeight();
			helper.assertTrue(height == 1.8F && Math.abs(mouth.y - (height * 0.9 + height / 16)) < 1.0E-4 && mouth.y > 1.7 && mouth.y < 1.8
					&& Math.abs(mouth.z - stand.getBbWidth() * 0.5) < 1.0E-4, "The mouth is at " + mouth + " on a Stand " + height + " high");
		}
		helper.succeed();
	}

	// G10
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void anArmsOnlyTrunkKeepsOnlyItsTurn(GameTestHelper helper) {
		Vector3f position = new Vector3f(1, 2, 3);
		Vector3f rotation = new Vector3f(0.1F, 0.2F, 0.3F);
		ArmsOnlyTrunk.keepOnlyTheTurn(position, rotation);
		helper.assertTrue(position.equals(0, 0, 0) && rotation.x == 0 && rotation.y == 0.2F && rotation.z == 0,
				"The trunk kept " + position + " and " + rotation);
		helper.assertTrue(ArmsOnlyTrunk.BONES.equals(List.of("stand_pos", "body_rot", "body", "torso", "torso_bend")),
				"The trunk's bones are " + ArmsOnlyTrunk.BONES);
		String calc = source("client/entityanim/PreFrameEntityAnimCalc.java");
		int asked = calc.indexOf("if (stand != null && stand.isArmsOnlyMode()) {\n\t\t\t\t\tfor (String trunkBone : ArmsOnlyTrunk.BONES) {");
		helper.assertTrue(asked >= 0 && calc.indexOf("ArmsOnlyTrunk.keepOnlyTheTurn(trunk.positionOffset, trunk.rotationOffset);", asked) > asked,
				"The clip's pose does not go through the trunk rule, or not only in arms-only mode");
		helper.succeed();
	}

	// G12, G21: the 16 x 16 reductions lost a column of the head; 32 x 32 is the size of 1.16's and of the official's
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theCoreStandsIconsAreWholeHeads(GameTestHelper helper) {
		for (String stand : List.of("star_platinum", "the_world", "crazy_diamond", "gold_experience", "magicians_red", "silver_chariot", "hierophant_green")) {
			String root = "/assets/jojo_ripples/stand_skins/" + stand + "/assets/jojo_ripples/textures/";
			byte[] manual = bytes(helper, root + "ability/manual_control.png");
			helper.assertTrue(Arrays.equals(manual, bytes(helper, root + "stand_icon.png")), stand + ": the O icon and the Stand icon differ");
			BufferedImage image = image(manual);
			helper.assertTrue(image.getWidth() == 32 && image.getHeight() == 32, stand + "'s icon is " + image.getWidth() + "x" + image.getHeight());
			if (SYMMETRIC_ICONS.contains(stand)) {
				int off = silhouetteOffAxis(image);
				helper.assertTrue(off == 0, stand + "'s icon lacks " + off + " pixels of its mirror image");
			}
		}
		helper.succeed();
	}

	private static int silhouetteOffAxis(BufferedImage image) {
		int low = image.getWidth();
		int high = -1;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (opaque(image, x, y)) {
					low = Math.min(low, x);
					high = Math.max(high, x);
				}
			}
		}
		int off = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = low; x <= high; x++) {
				if (opaque(image, x, y) != opaque(image, low + high - x, y)) {
					off++;
				}
			}
		}
		return off / 2;
	}

	private static boolean opaque(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) >>> 24 > 0;
	}

	private static byte[] bytes(GameTestHelper helper, String resource) {
		try (InputStream in = CoreStandMotionFixesGameTests.class.getResourceAsStream(resource)) {
			helper.assertTrue(in != null, "Missing " + resource);
			return in.readAllBytes();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static BufferedImage image(byte[] png) {
		try {
			return ImageIO.read(new java.io.ByteArrayInputStream(png));
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String source(String file) {
		for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
			Path path = at.resolve("src/main/java/rotp/core").resolve(file);
			if (Files.isRegularFile(path)) {
				try {
					return Files.readString(path).replace("\r\n", "\n");
				}
				catch (IOException e) {
					throw new IllegalStateException("Could not read " + path, e);
				}
			}
		}
		throw new IllegalStateException("No source of " + file + " above " + Path.of("").toAbsolutePath());
	}

	private static final class Scene implements AutoCloseable {
		final FakePlayer user;
		final StandPower power;
		final StandEntity stand;

		Scene(GameTestHelper helper, String name) {
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(at.x, at.y, at.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand's user");
			try {
				StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
				helper.assertTrue(type != null, "Missing Star Platinum");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not give the Stand");
				helper.assertTrue(type.summon(user, power), "Could not summon the Stand");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "The summoned Stand is missing");
			}
			catch (RuntimeException | AssertionError e) {
				GameTestPlayers.discardAfterFailedSetup(user);
				throw e;
			}
		}

		@Override
		public void close() {
			if (power.isSummoned()) power.getPowerType().forceUnsummon(user, power);
			user.discard();
		}
	}
}
