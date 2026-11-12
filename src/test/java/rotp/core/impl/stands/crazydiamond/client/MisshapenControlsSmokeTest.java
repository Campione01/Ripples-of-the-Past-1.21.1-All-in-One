package rotp.core.impl.stands.crazydiamond.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;

/**
 * 1.16 InputHandler's misshapen controls (face: inverted look, legs: inverted walking, arms: swapped mouse buttons) and the
 * misshapen pose. Every check runs and every failure is listed.
 */
public final class MisshapenControlsSmokeTest {
	private static final List<String> FAILURES = new ArrayList<>();

	private MisshapenControlsSmokeTest() {}

	public static void main(String[] args) {
		face();
		legs();
		arms();
		pose();
		sources();
		if (!FAILURES.isEmpty()) {
			throw new AssertionError(FAILURES.size() + " misshapen control check(s) failed:\n" + String.join("\n", FAILURES));
		}
		System.out.println("MisshapenControlsSmokeTest: all checks passed");
	}

	// vanilla turnPlayer: d = 0.6 * s + 0.2, turned by d^3 * 8; the inverted sensitivity gives -d
	private static void face() {
		for (double s : new double[] { 0.0, 0.25, 0.5, 1.0 }) {
			double turn = 0.6 * s + 0.2;
			double inverted = 0.6 * MisshapenControls.invertedSensitivity(s) + 0.2;
			check(Math.abs(inverted + turn) < 1.0E-9, "face: sensitivity " + s + " must turn the camera the other way at the same"
					+ " speed (factor " + turn + "), it gives " + inverted);
		}
	}

	private static void legs() {
		Input input = new Input();
		input.up = true;
		input.left = true;
		input.jumping = true;
		input.forwardImpulse = 1.0F;
		input.leftImpulse = 0.5F;
		MisshapenControls.invertMovement(input);
		check(!input.up && input.down, "legs: forward must walk back");
		check(!input.left && input.right, "legs: left must walk right");
		check(!input.jumping && input.shiftKeyDown, "legs: jump must sneak");
		check(input.forwardImpulse == -1.0F && input.leftImpulse == -0.5F, "legs: the impulses must be negated: forward "
				+ input.forwardImpulse + ", left " + input.leftImpulse);
		Input sneaking = new Input();
		sneaking.shiftKeyDown = true;
		MisshapenControls.invertMovement(sneaking);
		check(sneaking.jumping && !sneaking.shiftKeyDown, "legs: sneak must jump");
	}

	private static void arms() {
		int left = GLFW.GLFW_MOUSE_BUTTON_LEFT;
		int right = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
		int middle = GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
		MisshapenControls.ClickRouter router = new MisshapenControls.ClickRouter();
		check(router.route(left, GLFW.GLFW_PRESS, true) == right, "arms: a left press must arrive as the right button");
		check(router.route(left, GLFW.GLFW_RELEASE, false) == right, "arms: its release must arrive as the right button too, even"
				+ " once the effect has ended (else the use key stays held)");
		check(router.route(left, GLFW.GLFW_PRESS, false) == left && router.route(left, GLFW.GLFW_RELEASE, false) == left,
				"arms: with the arms put right the left button is the left button again");
		check(router.route(right, GLFW.GLFW_PRESS, true) == left && router.route(right, GLFW.GLFW_RELEASE, true) == left,
				"arms: a right press and release must arrive as the left button");
		check(router.route(middle, GLFW.GLFW_PRESS, true) == middle, "arms: the middle button is not swapped");
		router.route(right, GLFW.GLFW_PRESS, true);
		router.keepOnly(button -> false);
		check(!router.isSwappedHeld(right) && router.route(right, GLFW.GLFW_RELEASE, false) == right, "arms: a button let go while"
				+ " the player was replaced (respawn, dimension change) is forgotten");
		router.route(left, GLFW.GLFW_PRESS, true);
		router.clear();
		check(!router.isSwappedHeld(left), "arms: logging out forgets every swapped button");
	}

	private static void pose() {
		ModelPart[] parts = new ModelPart[6];
		for (int i = 0; i < parts.length; i++) {
			parts[i] = new ModelPart(List.of(), Map.of());
		}
		MisshapenBodyPose.pose(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], false, false, false);
		for (ModelPart part : parts) {
			check(part.xRot == 0 && part.yRot == 0 && part.zRot == 0, "pose: nothing misshapen must leave the model alone");
		}
		MisshapenBodyPose.pose(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], true, false, false);
		check(parts[0].zRot == Mth.PI && parts[1].zRot == Mth.PI, "pose: a misshapen face turns the head and its hat upside down");
		check(parts[2].yRot == 0 && parts[4].yRot == 0, "pose: the face alone leaves the arms and legs");
		MisshapenBodyPose.pose(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], false, true, false);
		check(parts[2].yRot == Mth.PI && parts[3].yRot == Mth.PI, "pose: misshapen arms are turned round at the shoulder");
		MisshapenBodyPose.pose(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], false, false, true);
		check(parts[4].yRot == Mth.PI && parts[5].yRot == Mth.PI, "pose: misshapen legs are turned round at the hip");
	}

	private static void sources() {
		Path root = Path.of(System.getProperty("user.dir"));
		String mixins = read(root.resolve("src/main/resources/jojo_ripples.mixins.json"));
		check(mixins.contains("\"client.controls.MouseHandlerMisshapenArmsMixin\""), "arms: the mouse button mixin must be in"
				+ " the client mixin list");
		String controls = read(root.resolve("src/main/java/rotp/core/impl/stands/crazydiamond/client/MisshapenControls.java"));
		check(controls.contains("mc.player != null && mc.screen == null && mc.player.hasEffect(ModStatusEffects.MISSHAPEN_ARMS)"),
				"arms: clicks in a screen (inventory, menus) are never swapped");
		String pose = read(root.resolve("src/main/java/rotp/core/impl/stands/crazydiamond/client/MisshapenBodyPose.java"));
		check(pose.contains("entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()"), "pose: the"
				+ " first-person hand keeps its shape");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			FAILURES.add("cannot read " + path + ": " + e);
			return "";
		}
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			FAILURES.add(message);
		}
	}
}
