package rotp.core.impl.stands.crazydiamond.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;

/**
 * 1.16 InputHandler's misshapen controls (face: inverted look, legs: inverted walking, arms: attack and use swapped on any
 * device) and the misshapen pose. Every check runs and every failure is listed.
 */
public final class MisshapenControlsSmokeTest {
	private static final List<String> FAILURES = new ArrayList<>();

	private MisshapenControlsSmokeTest() {}

	public static void main(String[] args) {
		face();
		legs();
		arms();
		rawRows();
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

	// "A" is bound to attack, "U" to use, "W" to neither
	private static void arms() {
		MisshapenControls.KeyRouter<String> keys = new MisshapenControls.KeyRouter<>();
		check("U".equals(keys.set("A", true, "U", true)) && "U".equals(keys.click("A", "U", true)),
				"arms: the attack key pressed with misshapen arms must press and click use");
		check("U".equals(keys.set("A", true, "U", true)), "arms: its repeats keep driving use");
		check("U".equals(keys.set("A", false, "U", false)), "arms: its release lets go of use, even once the effect has ended"
				+ " (else use stays held)");
		check(!keys.isHeld("A"), "arms: a released key is forgotten");
		check("A".equals(keys.set("U", true, "A", true)) && "A".equals(keys.set("U", false, "A", true)),
				"arms: the use key drives attack, held actions included");

		check("A".equals(keys.set("A", true, "U", false)), "arms: without the effect the attack key is attack");
		check("A".equals(keys.set("A", true, "U", true)) && "A".equals(keys.click("A", "U", true))
				&& "A".equals(keys.set("A", false, "U", true)), "arms: a key held as the effect begins keeps driving what it"
						+ " pressed until its release, which lets that go");

		check("W".equals(keys.set("W", true, null, true)) && !keys.isHeld("W") && "W".equals(keys.click("W", null, true)),
				"arms: a key bound to neither attack nor use is left alone");

		keys.set("A", true, "U", true);
		keys.set("U", true, "A", false);
		List<String> swapped = keys.takeSwapped();
		check(swapped.equals(List.of("U")), "arms: on logout, respawn or a dimension change the swapped keys are let go"
				+ " (use, driven by the attack key), the unswapped one is not: " + swapped);
		check(!keys.isHeld("A") && !keys.isHeld("U"), "arms: and every held key is forgotten");
		keys.set("A", true, "U", true);
		keys.clear();
		check(!keys.isHeld("A"), "arms: when every key mapping is let go (a screen opens) the held keys are forgotten");
	}

	// the Stand's LMB and RMB rows (raw buttons 0 and 1; actions 1 press, 0 release)
	private static void rawRows() {
		MisshapenControls.KeyRouter<Integer> rows = new MisshapenControls.KeyRouter<>();
		check(MisshapenControls.rawRoute(rows, 0, 1, true) == 1, "rows: a left press with misshapen arms goes to the Stand's RMB row");
		check(MisshapenControls.rawRoute(rows, 0, 0, false) == 1, "rows: its release goes to the RMB row too, even once the"
				+ " effect has ended (else the RMB move stays held)");
		check(MisshapenControls.rawRoute(rows, 1, 1, true) == 0 && MisshapenControls.rawRoute(rows, 1, 0, true) == 0,
				"rows: a right press and release go to the LMB row");
		check(MisshapenControls.rawRoute(rows, 0, 1, false) == 0 && MisshapenControlsSmokeTest.heldThenSwapped(rows),
				"rows: a button held as the effect begins keeps its own row until its release");
		check(MisshapenControls.rawRoute(rows, 2, 1, true) == 2 && !rows.isHeld(2), "rows: the middle button is not swapped");
		MisshapenControls.rawRoute(rows, 1, 1, true);
		rows.keepOnly(button -> false);
		check(!rows.isHeld(1) && MisshapenControls.rawRoute(rows, 1, 0, false) == 1, "rows: a button let go while the player"
				+ " was replaced (respawn, dimension change) is forgotten");

		// one click, both paths: the Stand row and the vanilla action it reaches agree, each swapped once
		MisshapenControls.KeyRouter<Integer> stand = new MisshapenControls.KeyRouter<>();
		MisshapenControls.KeyRouter<String> mappings = new MisshapenControls.KeyRouter<>();
		int row = MisshapenControls.rawRoute(stand, 0, 1, true);
		String vanilla = mappings.set("mouse.0", true, "mouse.1", true);
		check(row == 1 && "mouse.1".equals(vanilla), "rows: a physical left click is the Stand's RMB row and vanilla use,"
				+ " never left again by a second swap: row " + row + ", vanilla " + vanilla);
	}

	// pressed before the effect (no swap), then released after it began: the release keeps the LMB row
	private static boolean heldThenSwapped(MisshapenControls.KeyRouter<Integer> rows) {
		return MisshapenControls.rawRoute(rows, 0, 0, true) == 0;
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
		check(mixins.contains("\"client.controls.KeyMappingMisshapenArmsMixin\""), "arms: the key mapping mixin must be in"
				+ " the client mixin list");
		check(!mixins.contains("MouseHandlerMisshapenArmsMixin"), "arms: one path only, the mouse button path is gone");
		String mixin = read(root.resolve("src/main/java/rotp/core/mixin/client/controls/KeyMappingMisshapenArmsMixin.java"));
		check(mixin.contains("MisshapenControls.routeSet(key, held)") && mixin.contains("MisshapenControls.routeClick(key)"),
				"arms: both KeyMapping.set and KeyMapping.click are routed");
		check(mixin.contains("@Inject(method = \"releaseAll\", at = @At(\"HEAD\"))")
				&& mixin.contains("MisshapenControls.forgetHeldKeys();"), "arms: KeyMapping.releaseAll forgets the held keys");
		String controls = read(root.resolve("src/main/java/rotp/core/impl/stands/crazydiamond/client/MisshapenControls.java"));
		check(controls.contains("mc.player != null && mc.screen == null && mc.player.hasEffect(ModStatusEffects.MISSHAPEN_ARMS)"),
				"arms: keys in a screen (inventory, menus) are never swapped");
		check(controls.contains("ClientPlayerNetworkEvent.LoggingOut event) {\n\t\treleaseSwappedKeys();")
				&& controls.contains("ClientPlayerNetworkEvent.Clone event) {\n\t\treleaseSwappedKeys();"),
				"arms: logout, respawn and dimension change let the swapped keys go");
		String inputHandler = read(root.resolve("src/main/java/rotp/core/client/input/InputHandler.java"));
		check(inputHandler.contains("ClientKey button = ClientKey.make(InputConstants.Type.MOUSE,\n"
				+ "\t\t\t\tMisshapenControls.routeRawMouseButton(event.getButton(), event.getAction()));"),
				"rows: the raw mouse input of the Stand's rows is routed");
		check(controls.contains("RAW_BUTTONS.clear();") && controls.contains("RAW_BUTTONS.keepOnly("),
				"rows: logout forgets the raw buttons, respawn and dimension change keep only those still held");
		check(!controls.contains(".setKey(") && !controls.contains("resetMapping"), "arms: the key bindings are never changed");
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
