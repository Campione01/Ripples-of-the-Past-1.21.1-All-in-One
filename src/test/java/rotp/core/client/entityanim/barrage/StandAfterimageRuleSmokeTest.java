package rotp.core.client.entityanim.barrage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Every melee humanoid Stand with a barrage draws afterimages of its own arm. The rigs and clips go through the production
 * parsers, the same ones for the core's skins and the add-on tree's (skipped when the tree is not there). What this cannot
 * see is the picture: that stays with the in-game pass.
 */
public final class StandAfterimageRuleSmokeTest {
	private static final Path CORE_ASSETS = Path.of("src/main/resources/assets");
	private static final String CORE_NS = "jojo_ripples";
	private static final float EPS = 1.0E-3F;

	/** Skin, module and clip its barrage plays (null for stand_default's), and the swing that clip names. */
	private record Row(String module, String skin, String stand, String clip, String swing) {}

	private static Row row(String module, String skin, String stand, String swing) {
		return new Row(module, skin, stand, "barrage", swing);
	}

	private static final Row[] SKINS = {
			row(null, "jojo_ripples:star_platinum", "jojo_ripples:star_platinum", "TWO_HANDED"),
			row(null, "jojo_ripples:the_world", "jojo_ripples:the_world", "TWO_HANDED"),
			row(null, "jojo_ripples:crazy_diamond", "jojo_ripples:crazy_diamond", "TWO_HANDED"),
			row(null, "jojo_ripples:gold_experience", "jojo_ripples:gold_experience", "TWO_HANDED"),
			new Row(null, "jojo_ripples:silver_chariot", "jojo_ripples:silver_chariot", "melee_barrage", "ONE_HANDED_MAIN"),
			row("rotp-c-moon", "rotp_cm:cmoon", "rotp_cm:cmoon", "TWO_HANDED"),
			row("rotp-c-moon", "rotp_cm:cmoon_classic", "rotp_cm:cmoon", "TWO_HANDED"),
			row("rotp-civil-war", "rotp_civilwar:civilwar", "rotp_civilwar:civilwar", "TWO_HANDED"),
			row("rotp-cmoon-reimagined", "cmoon_rm:cmoon", "cmoon_rm:cmoon", "TWO_HANDED"),
			row("rotp-cream", "rotp_cream:cream", "rotp_cream:cream", "TWO_HANDED"),
			row("rotp-d4c", "rotp_d4c:d4c", "rotp_d4c:d4c", "TWO_HANDED"),
			row("rotp-dark-blue-moon", "darkbluemoon:dark_blue_moon", "darkbluemoon:dark_blue_moon", "TWO_HANDED"),
			row("rotp-diegos-the-world", "rotp_theworld:the_world", "rotp_theworld:the_world", "TWO_HANDED"),
			row("rotp-diver-down", "rotp_dd:diver_down", "rotp_dd:diver_down", "TWO_HANDED"),
			row("rotp-echoes", "rotp_echoes:echoes_act3", "rotp_echoes:echoes_act3", "TWO_HANDED"),
			row("rotp-gold-experience-requiem", "geraddon:ger_stand", "geraddon:ger_stand", "TWO_HANDED"),
			row("rotp-grateful-dead", "rotp_nextalbum:the_grateful_dead", "rotp_nextalbum:the_grateful_dead", "GRATEFUL_DEAD_RIGHT_HAND"),
			row("rotp-green-day", "rotp_zgd:green_day", "rotp_zgd:green_day", "TWO_HANDED"),
			row("rotp-heavens-door", "rotp_hd:heavens_door", "rotp_hd:heavens_door", "TWO_HANDED"),
			row("rotp-highway-star", "highwaystar:highway_star", "highwaystar:highway_star", "TWO_HANDED"),
			row("rotp-jumping-jack-flash", "jumping_jack_flash:jumping_jack_flash", "jumping_jack_flash:jumping_jack_flash", "TWO_HANDED"),
			row("rotp-killer-queen", "rotp_zkq:killer_queen", "rotp_zkq:killer_queen", "TWO_HANDED"),
			row("rotp-killer-queen-lion", "rotp_zkq8:killer_queen", "rotp_zkq8:killer_queen", "TWO_HANDED"),
			row("rotp-king-crimson", "rotp_kingcrimson:kingcrimson", "rotp_kingcrimson:kingcrimson", "TWO_HANDED"),
			row("rotp-kiss", "kiss:kiss", "kiss:kiss", "TWO_HANDED"),
			new Row("rotp-kraft-work", "rotp_kraft_work:kraft_work", "rotp_kraft_work:kraft_work", null, "TWO_HANDED"),
			row("rotp-little-feet", "rotp_littlefeet:little_feet", "rotp_littlefeet:little_feet", "TWO_HANDED"),
			row("rotp-made-in-heaven", "rotp_mih:made_in_heaven", "rotp_mih:made_in_heaven", "TWO_HANDED"),
			row("rotp-nut-king-call", "nutkingcall:nut_king_call", "nutkingcall:nut_king_call", "TWO_HANDED"),
			row("rotp-ova-the-world", "rotp_ova_addon:the_world_ova", "rotp_ova_addon:the_world_ova", "TWO_HANDED"),
			row("rotp-planet-waves", "rotp_pw:pw", "rotp_pw:pw", "TWO_HANDED"),
			row("rotp-purple-haze", "rotp_ph:purple_haze", "rotp_ph:purple_haze", "TWO_HANDED"),
			row("rotp-shadow-the-world", "rotp_shadowtheworld:shadowtheworld", "rotp_shadowtheworld:shadowtheworld", "TWO_HANDED"),
			new Row("rotp-silver-chariot-requiem", "rotp_nextalbum:silver_chariot_requiem", "rotp_nextalbum:silver_chariot_requiem", "chariot_requiem_barrage", "TWO_HANDED"),
			row("rotp-soft-and-wet", "rotp_sw:soft_and_wet", "rotp_sw:soft_and_wet", "TWO_HANDED"),
			row("rotp-speed-king", "speedking:speedking_stand", "speedking:speedking_stand", "TWO_HANDED"),
			row("rotp-spice-girl", "rotp_sg:spice_girl", "rotp_sg:spice_girl", "TWO_HANDED"),
			row("rotp-sticky-fingers", "rotp_stfn:sticky_fingers", "rotp_stfn:sticky_fingers", "TWO_HANDED"),
			row("rotp-stone-free", "rotp_stone_free:stone_free", "rotp_stone_free:stone_free", "TWO_HANDED"),
			row("rotp-the-hand", "rotp_the_hand:the_hand", "rotp_the_hand:the_hand", "TWO_HANDED"),
			new Row("rotp-tusk", "rotp_t:tusk_act_4", "rotp_t:tusk_act_4", "tusk_barrage", "TWO_HANDED"),
			row("rotp-weather-report", "rotp_wr:weather_report", "rotp_wr:weather_report", "TWO_HANDED"),
			row("rotp-whitesnake", "rotp_whitesnake:whitesnake", "rotp_whitesnake:whitesnake", "TWO_HANDED"),
	};
	private static final int SKIN_COUNT = 43;
	private static final int LARGE_RIG = 80;
	// no clip of its own moves its arms and none names a swing: the rule's shared swing, posed by stand_default
	private static final Row GUANGGUN = row("rotp-guanggun", "guanggun:guanggun", "guanggun:guanggun", null);

	private static final Set<String> SWINGS = Set.of("TWO_HANDED", "ONE_HANDED_MAIN", "GRAB_RIGHT", "GRATEFUL_DEAD_RIGHT_HAND");

	private StandAfterimageRuleSmokeTest() {}

	public static void main(String[] args) throws IOException {
		KeyframesMolangEngine.init();
		verifyOnlyTheArmIsDrawnWhereItStands();
		verifyAPoseTouchesTheArmsBonesAlone();
		verifyHiddenBonesShowOnlyWhileKeyed();
		verifyStrokeTimingComesFromTheClip();
		verifySwingRule();
		verifyRollRule();
		verifyWiring();
		verifySkinTable(args.length > 0 ? Path.of(args[0]) : null);
		System.out.println("Stand afterimage rule checks passed; the picture itself is for the in-game pass");
	}

	// ---- the arm draws itself alone, in the place the whole model would draw it

	private static ModelPart rig() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", cube(), PartPose.offsetAndRotation(0, 12, 0, 0.1F, 0.2F, 0));
		PartDefinition torso = body.addOrReplaceChild("torso", cube(), PartPose.offset(0, -6, 0));
		PartDefinition xrot = torso.addOrReplaceChild("right_arm_xrot", CubeListBuilder.create(), PartPose.offsetAndRotation(-5, -4, 0, 0.3F, 0, 0));
		PartDefinition arm = xrot.addOrReplaceChild("right_arm", cube(), PartPose.offset(-1, 0, 0));
		PartDefinition bend = arm.addOrReplaceChild("right_arm_bend", cube(), PartPose.offset(0, 6, 0));
		bend.addOrReplaceChild("spike", cube(), PartPose.offset(1, 1, 0));
		arm.addOrReplaceChild("hidden.flame", cube(), PartPose.offset(0, 2, 0));
		torso.addOrReplaceChild("left_arm_xrot", CubeListBuilder.create(), PartPose.offset(5, -4, 0))
				.addOrReplaceChild("left_arm", cube(), PartPose.offset(1, 0, 0));
		root.addOrReplaceChild("right_leg", cube(), PartPose.offset(-2, 12, 0));
		return LayerDefinition.create(mesh, 64, 64).bakeRoot();
	}

	private static CubeListBuilder cube() {
		return CubeListBuilder.create().texOffs(0, 0).addBox(-1, -1, -1, 2, 2, 2);
	}

	private static ModelPart named(ModelPart root, String name) {
		ModelPart found = find(root, name);
		check(found != null, "the rig has no " + name);
		return found;
	}

	private static ModelPart find(ModelPart part, String name) {
		ModelPart direct = part.children.get(name);
		if (direct != null) {
			return direct;
		}
		for (ModelPart child : part.children.values()) {
			ModelPart found = find(child, name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static void verifyOnlyTheArmIsDrawnWhereItStands() {
		ModelPart root = rig();
		ModelPart arm = named(root, "right_arm");
		named(arm, "hidden.flame").visible = false;
		AfterimageArm afterimage = AfterimageArm.of(root, arm, null);
		check(afterimage != null, "a rig's own arm must resolve");

		Recorder drawn = new Recorder();
		PoseStack pose = new PoseStack();
		pose.pushPose();
		afterimage.enter(pose).render(pose, drawn, 0, 0, -1);
		pose.popPose();
		check(drawn.vertices.size() == 3 * 24, "an afterimage draws the arm, its forearm and its spike, 3 cubes, got "
				+ drawn.vertices.size() / 24 + ": the torso bones above the shoulder stay out of the air");

		// the whole model with everything but the arm hidden: where 1.16 and the path before this one drew it
		ModelPart whole = rig();
		ModelPart wholeArm = named(whole, "right_arm");
		named(wholeArm, "hidden.flame").visible = false;
		hideCubesOfAllBut(whole, wholeArm);
		Recorder expected = new Recorder();
		whole.render(new PoseStack(), expected, 0, 0, -1);
		check(expected.vertices.size() == drawn.vertices.size(), "fixture: the hidden model draws " + expected.vertices.size() / 24 + " cubes");
		for (int i = 0; i < drawn.vertices.size(); i++) {
			check(drawn.vertices.get(i).distance(expected.vertices.get(i)) < EPS, "the afterimage arm must stand where the model's arm stands, vertex "
					+ i + " is at " + drawn.vertices.get(i) + " against " + expected.vertices.get(i));
		}
	}

	private static void hideCubesOfAllBut(ModelPart part, ModelPart keep) {
		if (part == keep) {
			return;
		}
		part.skipDraw = true;
		for (ModelPart child : part.children.values()) {
			hideCubesOfAllBut(child, keep);
		}
	}

	private static final String CLIP_HEAD = "{\"animation_length\":1.0,\"timeline\":{\"0.0\":[\"phase = PERFORM;\",\"phase.loopBack = 0;\","
			+ "\"barrage = TWO_HANDED;\"],\"1.0\":\"phase = RECOVERY;\"},\"bones\":{";

	private static RotpAnimDefinition clip(String bones) {
		return ParseGeckoAnims.parseAnim(JsonParser.parseString(CLIP_HEAD + bones + "}}").getAsJsonObject());
	}

	private static void verifyAPoseTouchesTheArmsBonesAlone() {
		ModelPart root = rig();
		ModelPart arm = named(root, "right_arm");
		ModelPart leg = named(root, "right_leg");
		ModelPart torso = named(root, "torso");
		float legRest = leg.xRot;
		AfterimageArm afterimage = AfterimageArm.of(root, arm, null);
		RotpAnimDefinition clip = clip("\"right_arm\":{\"rotation\":{\"0.0\":[-90,0,0],\"1.0\":[-90,0,0]}},"
				+ "\"torso\":{\"rotation\":{\"0.0\":[0,10,0],\"1.0\":[0,10,0]}},"
				+ "\"right_leg\":{\"rotation\":{\"0.0\":[45,0,0],\"1.0\":[45,0,0]}}");
		afterimage.pose(clip, null, 0.5F);
		check(Math.abs(arm.xRot + 90 * net.minecraft.util.Mth.DEG_TO_RAD) < EPS, "the clip poses the arm, got " + arm.xRot);
		check(Math.abs(torso.yRot - 10 * net.minecraft.util.Mth.DEG_TO_RAD) < EPS, "the clip poses the bones the arm hangs from, got " + torso.yRot);
		check(leg.xRot == legRest, "a pose must leave the rest of the rig alone, the leg went to " + leg.xRot);

		arm.xRot = 1.5F;
		afterimage.pose(clip("\"right_leg\":{\"rotation\":{\"0.0\":[45,0,0],\"1.0\":[45,0,0]}}"), null, 0.5F);
		check(arm.xRot == 0, "a clip that leaves a bone out shows it at rest, not in the last afterimage's pose: " + arm.xRot);
	}

	private static void verifyHiddenBonesShowOnlyWhileKeyed() {
		ModelPart root = rig();
		ModelPart arm = named(root, "right_arm");
		ModelPart flame = named(arm, "hidden.flame");
		AfterimageArm afterimage = AfterimageArm.of(root, arm, List.of(flame));
		afterimage.pose(clip("\"right_arm\":{\"rotation\":{\"0.0\":[0,0,0],\"1.0\":[0,0,0]}}"), null, 0.5F);
		check(!flame.visible, "a hidden. bone stays hidden on a clip that does not key it");
		afterimage.pose(clip("\"hidden.flame\":{\"rotation\":{\"0.0\":[0,0,0],\"1.0\":[0,0,0]}}"), null, 0.5F);
		check(flame.visible, "a hidden. bone shows on a clip that keys it");
	}

	// ---- the stroke

	private static String strokeClip(float leftFirst, float rightFirst) {
		return "\"right_arm\":{\"rotation\":" + strokes(rightFirst) + "},\"left_arm\":{\"rotation\":" + strokes(leftFirst) + "}";
	}

	// a stroke out every 0.2 s from the given time, folded between
	private static String strokes(float first) {
		StringBuilder keys = new StringBuilder("{");
		for (int i = 0; i < 5; i++) {
			float out = first + i * 0.2F;
			float back = out + 0.1F;
			if (i > 0) keys.append(',');
			keys.append('"').append(out).append("\":[-90,0,0],\"").append(back).append("\":[30,0,0]");
		}
		return keys.append('}').toString();
	}

	private static void verifyStrokeTimingComesFromTheClip() {
		RotpAnimDefinition asTheDefault = clip(strokeClip(0.0F, 0.1F));
		near(asTheDefault.armStrokeSeconds(HumanoidArm.RIGHT, 0.1F), 0.1F, "right arm out at the swing's middle on the usual clip");
		near(asTheDefault.armStrokeSeconds(HumanoidArm.LEFT, 0.1F), 0.2F, "left arm out at the swing's middle on the usual clip");
		RotpAnimDefinition shifted = clip(strokeClip(0.05F, 0.16F));
		near(shifted.armStrokeSeconds(HumanoidArm.RIGHT, 0.1F), 0.16F, "a reworked clip's right stroke must be read from it");
		near(shifted.armStrokeSeconds(HumanoidArm.LEFT, 0.1F), 0.25F, "a reworked clip's left stroke must be read from it");
		RotpAnimDefinition oneArm = clip("\"right_arm\":{\"rotation\":" + strokes(0.1F) + "}");
		check(Float.isNaN(oneArm.armStrokeSeconds(HumanoidArm.LEFT, 0.1F)) && oneArm.strokesArms(),
				"an arm the clip does not strike with has no stroke, the other has");
		check(!clip("\"head\":{\"rotation\":{\"0.0\":[0,0,0]}}").strokesArms(), "a clip that moves no arm has no stroke");
		RotpAnimDefinition oldNames = clip("\"rightArm\":{\"rotation\":" + strokes(0.1F) + "},\"leftArm\":{\"rotation\":" + strokes(0.0F) + "}");
		near(oldNames.armStrokeSeconds(HumanoidArm.RIGHT, 0.1F), 0.1F, "a rig on 1.16's bone names strikes as well");
		check(Float.isNaN(ParseGeckoAnims.parseAnim(JsonParser.parseString("{\"animation_length\":1,\"bones\":{\"right_arm\":{\"rotation\":"
				+ strokes(0.1F) + "}}}").getAsJsonObject()).armStrokeSeconds(HumanoidArm.RIGHT, 0.1F)), "a clip without a looping PERFORM keeps the old reckoning");
	}

	private static void near(float got, float expected, String what) {
		check(Math.abs(got - expected) < EPS, what + ": expected " + expected + ", got " + got);
	}

	// ---- the rules

	private static void verifySwingRule() {
		check("TWO_HANDED".equals(AfterimageRule.swingName(null, false, true)), "a melee barrage whose clip names no swing draws the shared one");
		check(AfterimageRule.swingName(null, false, false) == null, "another action's clip names none and draws none");
		check(AfterimageRule.swingName(null, true, true) == null, "a clip that names its swing later draws none before it");
		check("null".equals(AfterimageRule.swingName("null", true, true)), "a clip's own barrage = null stands");
		check("ONE_HANDED_MAIN".equals(AfterimageRule.swingName("ONE_HANDED_MAIN", true, true)), "a one-handed clip stays one-handed");
	}

	private static void verifyRollRule() {
		for (String[] accepted : new String[][] { { "rotp_cream", "cream" }, { "highwaystar", "highway_star" } }) {
			check(AfterimageArmRoll.keptBy(ResourceLocation.fromNamespaceAndPath(accepted[0], accepted[1])),
					accepted[1] + " was accepted on video and keeps its look");
		}
		check(!AfterimageArmRoll.keptBy(ResourceLocation.fromNamespaceAndPath("rotp_zkq", "killer_queen")),
				"the other Stands' arms thrust straight, as the Ultimate Lifeform's");
		check(!AfterimageArmRoll.keptBy(null), "no Stand, no roll");
		check(AfterimageArmRoll.of(true, 1, 2) == 0 && AfterimageArmRoll.of(false, 0.5F, 2) == 1, "the roll's numbers");
	}

	// ---- the wiring, from the class files: the render itself has no test

	private static void verifyWiring() throws IOException {
		for (String swing : new String[] { "TwoHandedBarrageLoopSwing", "GrabBarrageLoopSwing" }) {
			MethodNode pose = method("rotp/core/client/entityanim/barrage/" + swing, "poseAndRender");
			check(calls(pose, "rotp/core/client/entityanim/barrage/BarrageSwings", "standArm"), swing + " must ask for the Stand's arm");
			check(calls(pose, "rotp/core/client/entityanim/barrage/AfterimageArm", "pose"), swing + " must pose that arm alone");
			check(calls(pose, "rotp/core/client/entityanim/barrage/AfterimageArm", "enter"), swing + " must draw that arm alone");
		}
		MethodNode add = method("rotp/core/client/entityanim/barrage/TwoHandedBarrageLoopSwing", "strokeAtMiddle");
		check(calls(add, "rotp/core/client/entityanim/RotpAnimDefinition", "armStrokeSeconds"), "a Stand's swing must take its time from the clip's stroke");
		MethodNode frame = method("rotp/core/client/entityanim/barrage/BarrageSwings", "frameStandBarrage");
		check(calls(frame, "rotp/core/client/entityanim/barrage/AfterimageRule", "swingName"), "the swing name must go through the rule");
		check(calls(frame, "rotp/core/client/entityanim/PreFrameEntityAnimCalc", "genericBarrageClip"), "arms no clip moves must take the generic clip");
	}

	private static boolean calls(MethodNode method, String owner, String name) {
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static MethodNode method(String owner, String name) throws IOException {
		try (var in = StandAfterimageRuleSmokeTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
			check(in != null, "no class file for " + owner);
			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			for (MethodNode method : node.methods) {
				if (method.name.equals(name)) {
					return method;
				}
			}
		}
		throw new IllegalStateException("no method " + owner + "." + name);
	}

	// ---- the table

	private static void verifySkinTable(Path addons) throws IOException {
		check(SKINS.length == SKIN_COUNT, "the table has " + SKINS.length + " skins, pinned at " + SKIN_COUNT);
		JsonObject generic = readJson(CORE_ASSETS.resolve(CORE_NS).resolve("animations/stand_default.animation.json"))
				.getAsJsonObject("animations");
		boolean tree = addons != null && Files.isDirectory(addons);
		int checked = 0;
		Map<String, String> cost = new HashMap<>();
		for (Row row : SKINS) {
			if (row.module != null && !tree) {
				continue;
			}
			RigAndClip found = load(row, addons, generic);
			check(SWINGS.contains(row.swing), row.skin + ": " + row.swing + " is no swing");
			check(row.swing.equals(found.swing()), row.skin + ": its barrage clip names " + found.swing() + ", the table says " + row.swing);
			for (HumanoidArm side : HumanoidArm.values()) {
				ModelPart arm = armOf(found.root(), side);
				check(arm != null, row.skin + ": no " + side + " arm in its rig");
				AfterimageArm afterimage = AfterimageArm.of(found.root(), arm, null);
				check(afterimage != null, row.skin + ": the " + side + " arm is not under the rig's root");
				Recorder drawn = new Recorder();
				PoseStack pose = new PoseStack();
				pose.pushPose();
				afterimage.enter(pose).render(pose, drawn, 0, 0, -1);
				pose.popPose();
				check(!drawn.vertices.isEmpty(), row.skin + ": the " + side + " arm has no cubes to draw");
				if (found.bones() >= LARGE_RIG) {
					cost.merge(row.skin, found.bones() + " bones, " + side + " arm's pose " + afterimage.partCount(), (a, b) -> a + ", " + b);
				}
				check(afterimage.partCount() < found.bones(), row.skin + ": a pose must not touch the whole rig");
			}
			checkAcceptedTiming(row, found.clip());
			checked++;
		}
		if (tree) {
			RigAndClip guanggun = load(GUANGGUN, addons, generic);
			check(guanggun.swing() == null, "guanggun: its clip now names " + guanggun.swing() + ": take it out of the rule's case");
			check(!guanggun.clip().strokesArms(), "guanggun: its clip now moves its arms");
			check(armOf(guanggun.root(), HumanoidArm.RIGHT) != null && armOf(guanggun.root(), HumanoidArm.LEFT) != null, "guanggun: arms lost");
		}
		check(!tree || checked == SKIN_COUNT, "checked " + checked + " of " + SKIN_COUNT);
		System.out.println("Skins with their own arms checked: " + checked + " of " + SKIN_COUNT + (tree ? "" : " (no add-on tree)"));
		cost.forEach((skin, line) -> System.out.println("Large rig " + skin + ": " + line));
	}

	// the two the owner accepted must come out at the times the fixed reckoning gave them: right 0, left 2 ticks ahead
	private static void checkAcceptedTiming(Row row, RotpAnimDefinition clip) {
		if ("rotp_cream:cream".equals(row.skin) || "highwaystar:highway_star".equals(row.skin)) {
			float middle = clip.clock.elapsed(2);
			near(clip.armStrokeSeconds(HumanoidArm.RIGHT, middle) * 20 - 2, 0, row.skin + " right arm's offset must not change");
			near(clip.armStrokeSeconds(HumanoidArm.LEFT, middle) * 20 - 2, 2, row.skin + " left arm's offset must not change");
		}
	}

	private record RigAndClip(ModelPart root, RotpAnimDefinition clip, String swing, int bones) {}

	private static RigAndClip load(Row row, Path addons, JsonObject generic) throws IOException {
		Path assets = row.module == null ? CORE_ASSETS : addons.resolve(row.module).resolve("src/main/resources/assets");
		String[] skin = row.skin.split(":");
		String[] stand = row.stand.split(":");
		Path base = assets.resolve(skin[0]).resolve("stand_skins").resolve(skin[1]).resolve("assets").resolve(stand[0]);
		ModelPart root = null;
		Path geo = base.resolve("geo").resolve(stand[1] + ".geo.json");
		if (Files.isRegularFile(geo)) {
			root = ParseModEntityModel.parse(readJson(geo), ModelFormat.GECKO).bakeRoot();
		}
		else {
			for (String extension : new String[] { ".bbmodel", ".bb.json" }) {
				Path bb = base.resolve("bb").resolve(stand[1] + extension);
				if (Files.isRegularFile(bb)) {
					root = ParseModEntityModel.parse(readJson(bb), ModelFormat.GENERIC).bakeRoot();
					break;
				}
			}
		}
		check(root != null, row.skin + ": no model file for " + row.stand);
		Path anim = base.resolve("animations").resolve(stand[1] + ".animation.json");
		String clipName = row.clip != null ? row.clip : "barrage";
		JsonObject clips = Files.isRegularFile(anim) ? readJson(anim).getAsJsonObject("animations") : null;
		JsonObject clipJson = clips != null && clips.has(clipName) ? clips.getAsJsonObject(clipName) : null;
		check(clipJson != null || row.clip == null, row.skin + ": no clip " + clipName);
		RotpAnimDefinition clip = ParseGeckoAnims.parseAnim(clipJson != null ? clipJson : generic.getAsJsonObject("barrage"));
		String swing = null;
		if (clip.instructionTimelines.hasStringTimeline(RotpAnimDefinition.TimelineKeys.BARRAGE)) {
			swing = firstSwing(clip);
		}
		int[] bones = { 0 };
		countBones(root, bones);
		return new RigAndClip(root, clip, swing, bones[0]);
	}

	private static String firstSwing(RotpAnimDefinition clip) {
		for (float t = 0; t <= clip.lengthInSeconds; t += 0.01F) {
			String value = clip.instructionTimelines.getStringTimelineVal(RotpAnimDefinition.TimelineKeys.BARRAGE, t);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static void countBones(ModelPart part, int[] bones) {
		bones[0]++;
		for (ModelPart child : part.children.values()) {
			countBones(child, bones);
		}
	}

	private static ModelPart armOf(ModelPart root, HumanoidArm side) {
		ModelPart arm = find(root, side == HumanoidArm.LEFT ? "left_arm" : "right_arm");
		return arm != null ? arm : find(root, side == HumanoidArm.LEFT ? "leftArm" : "rightArm");
	}

	private static JsonObject readJson(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private static final class Recorder implements VertexConsumer {
		final List<Vector3f> vertices = new ArrayList<>();

		@Override public VertexConsumer addVertex(float x, float y, float z) { vertices.add(new Vector3f(x, y, z)); return this; }
		@Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
		@Override public VertexConsumer setUv(float u, float v) { return this; }
		@Override public VertexConsumer setUv1(int u, int v) { return this; }
		@Override public VertexConsumer setUv2(int u, int v) { return this; }
		@Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
	}
}
