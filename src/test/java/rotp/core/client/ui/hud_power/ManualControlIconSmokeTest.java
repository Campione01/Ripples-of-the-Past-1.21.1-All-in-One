package rotp.core.client.ui.hud_power;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import rotp.core.client.ui.hud_power.ManualControlIcon.Head;
import rotp.core.client.ui.hud_power.ManualControlIcon.Layer;

/**
 * The remote control icon's two heads: where each goes in the 16 by 16 square, in which order, and that the colour the
 * HUD passes in (the unusable look, the fade while the controls are disabled) reaches every layer. The picture itself
 * can only be seen in the game.
 */
public final class ManualControlIconSmokeTest {
	private ManualControlIconSmokeTest() {}

	public static void main(String[] args) {
		List<Layer> outOfControl = ManualControlIcon.layers(false);
		check(outOfControl.size() == 2, "out of control has " + outOfControl.size() + " layers");
		assertLayer(new Layer(Head.USER, 8, 0, 8, 8, 0, 0, 1, 1, 0x80FFFFFF), outOfControl.get(0),
				"out of control the user is behind, half size and half transparent, upper right");
		assertLayer(new Layer(Head.STAND, 0, 0, 14, 16, 2 / 16F, 0, 1, 1, 0xFFFFFFFF), outOfControl.get(1),
				"out of control the Stand is in front, opaque, 2 px left and cut at the square's edge");

		List<Layer> underControl = ManualControlIcon.layers(true);
		check(underControl.size() == 2, "under control has " + underControl.size() + " layers");
		assertLayer(new Layer(Head.STAND, 4, 0, 12, 12, 0, 4 / 16F, 12 / 16F, 1, 0x40FFFFFF), underControl.get(0),
				"under control the Stand is behind, faint, 4 px right and up and cut at the square's edges");
		assertLayer(new Layer(Head.USER, 2, 5, 8, 8, 0, 0, 1, 1, 0xFFFFFFFF), underControl.get(1),
				"under control the user is in front, half size and opaque, lower left");

		for (boolean state : new boolean[] { false, true }) {
			for (Layer layer : ManualControlIcon.layers(state)) {
				check(layer.x() >= 0 && layer.y() >= 0 && layer.x() + layer.width() <= ManualControlIcon.SIZE
						&& layer.y() + layer.height() <= ManualControlIcon.SIZE, layer + " leaves the square");
				float headWidth = layer.width() / (layer.maxU() - layer.minU());
				float headHeight = layer.height() / (layer.maxV() - layer.minV());
				check(headWidth == headHeight, layer + " stretches its head");
			}
		}

		int unusable = PowerHudControlsElement.UNUSABLE_ICON_TINT;
		assertColors(unusable, outOfControl, 0x40333333, 0x80333333, "unusable, out of control (an arms-only Stand)");
		assertColors(unusable, underControl, 0x20333333, 0x80333333, "unusable, under control");
		assertColors(0xFFFFFFFF, outOfControl, 0x80FFFFFF, 0xFFFFFFFF, "usable, out of control");
		assertColors(0xFFFFFFFF, underControl, 0x40FFFFFF, 0xFFFFFFFF, "usable, under control");
		assertColors(0x40FFFFFF, outOfControl, 0x20FFFFFF, 0x40FFFFFF, "controls disabled, out of control");

		Path java = Path.of(System.getProperty("user.dir"), "src/main/java/rotp/core");
		String wheel = read(java.resolve("client/ui/AbilitySelectionWheel.java"));
		check(wheel.contains("ability.ability.renderAbilityIcon(abilityCtx, guiGraphics, abilitySprite,")
				&& wheel.contains("iconPos[1] - iconHeight / 2, iconColor);"),
				"the selection wheel must draw an ability's icon through the ability, with the slot's colour, as the HUD does");
		String hud = read(java.resolve("client/ui/hud_power/PowerHudControlsElement.java"));
		check(hud.contains("ability.renderAbilityIcon(power, guiGraphics, abilityUi.sprite,"),
				"the HUD must draw an ability's icon through the ability");
		String toggle = read(java.resolve("impl/stands/_entitybase/StandEntityManualControlToggle.java"));
		check(toggle.contains("ManualControlIcon.render(standPower, guiGraphics, underControl, x, y, color)")
				&& toggle.contains("super.renderAbilityIcon(context, guiGraphics, sprite, x, y, color);"),
				"the ability must draw the two heads and keep the skin's picture as its fallback");
		String icon = read(java.resolve("client/ui/hud_power/ManualControlIcon.java"));
		check(icon.contains("layer.width(), player, layerColor, false);"), "the icon must leave out the user's hat layer");

		System.out.println("Manual control icon smoke test passed");
	}

	private static void assertColors(int color, List<Layer> layers, int back, int front, String state) {
		int actualBack = ManualControlIcon.layerColor(color, layers.get(0));
		int actualFront = ManualControlIcon.layerColor(color, layers.get(1));
		check(actualBack == back && actualFront == front, state + ": layer colours are 0x"
				+ Integer.toHexString(actualBack) + " and 0x" + Integer.toHexString(actualFront));
	}

	private static void assertLayer(Layer expected, Layer actual, String message) {
		check(expected.equals(actual), message + ": was " + actual);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
