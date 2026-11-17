package rotp.core.client.ui.hud_power;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.client.input.controlscheme.VariationKey;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * 1.16 ActionsOverlayGui.resolveVisibleActionInSlot: a hotbar slot showed its Shift variation while Shift was held,
 * whatever the power, so a Stand hotbar shows what the variation key + the use key will actually do (the input side
 * already resolves a Stand hotbar with the held modifier). A locked variation leaves the base (HotbarShiftFallbackSmokeTest).
 */
public final class StandHotbarShiftDisplaySmokeTest {
	private StandHotbarShiftDisplaySmokeTest() {}

	public static void run() {
		check(PowerHudControlsElement.hotbarDisplayModifier(true, KeyModifier.CONTROL) == KeyModifier.CONTROL,
				"a Stand hotbar must show its variations while the default variation key is held");
		check(PowerHudControlsElement.hotbarDisplayModifier(true, KeyModifier.NONE) == KeyModifier.NONE
				&& PowerHudControlsElement.hotbarDisplayModifier(true, KeyModifier.SHIFT) == KeyModifier.NONE,
				"any other modifier leaves a Stand hotbar on its base abilities");
		KeyMapping setting = new KeyMapping("test.display_variation_key", KeyConflictContext.IN_GAME,
				InputConstants.Type.KEYSYM, 340, "test.controls");
		VariationKey.useMapping(setting);
		try {
			check(PowerHudControlsElement.hotbarDisplayModifier(true, KeyModifier.SHIFT) == KeyModifier.SHIFT
					&& PowerHudControlsElement.hotbarDisplayModifier(true, KeyModifier.CONTROL) == KeyModifier.NONE,
					"the display must follow the key that is set: Shift");
			setting.setKey(InputConstants.Type.KEYSYM.getOrCreate(86));
			check(PowerHudControlsElement.hotbarDisplayModifier(true, VariationKey.BORROWED_MODIFIER) == VariationKey.BORROWED_MODIFIER
					&& PowerHudControlsElement.hotbarDisplayModifier(true, KeyModifier.SHIFT) == KeyModifier.NONE,
					"the display must follow the key that is set: a key that is no modifier");
		}
		finally {
			VariationKey.useMapping(null);
		}
		check(PowerHudControlsElement.hotbarDisplayModifier(false, KeyModifier.CONTROL) == KeyModifier.CONTROL
				&& PowerHudControlsElement.hotbarDisplayModifier(false, KeyModifier.SHIFT) == KeyModifier.SHIFT,
				"other hotbars keep following the held modifier");

		String hud = squash(read("src/main/java/rotp/core/client/ui/hud_power/PowerHudControlsElement.java"));
		check(hud.contains(squash("KeyModifier hotbarDisplayModifier = hotbarDisplayModifier(standHotbarDisplay, modifier);"))
				&& hud.contains(squash("AbilityControlsEntry abilityEntry = slot.getBinds().getFirst(hotbarDisplayModifier, inputMethod);"))
				&& !hud.contains(squash("slot.getBaseBind(inputMethod)")),
				"the hotbar HUD must resolve every hotbar slot with the display modifier");
	}

	private static String read(String relativePath) {
		Path path = Path.of(System.getProperty("user.dir")).resolve(relativePath);
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static String squash(String source) {
		return source.replaceAll("\\s+", "");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
