package rotp.core.client.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * OQ-C05, the coordinator's ruling: R (summon) and Ctrl+R (Stand HUD mode), and Ctrl+F (swap with the Stand) and vanilla's F
 * (swap with the offhand), are read apart on every press, so the mod's rows do not mark them; bound alike they are marked
 * again, and other pairs keep NeoForge's in-game rule. Vanilla's own F row still marks Ctrl+F by vanilla's check.
 */
public final class KeyPairsToldApartSmokeTest {
	private static final String CATEGORY = "key.categories.rotp_test";

	private KeyPairsToldApartSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		KeyMapping summon = ours("jojo_ripples.key.toggle_stand", KeyModifier.NONE, GLFW.GLFW_KEY_R);
		KeyMapping standMode = ours("jojo_ripples.key.stand_mode", KeyModifier.CONTROL, GLFW.GLFW_KEY_R);
		require(!summon.same(standMode) && !standMode.same(summon) && !summon.hasKeyModifierConflict(standMode)
				&& !standMode.hasKeyModifierConflict(summon), "R and Ctrl+R are marked as sharing a key");

		KeyMapping swapItems = ours("jojo_ripples.key.swap_items", KeyModifier.CONTROL, GLFW.GLFW_KEY_F);
		KeyMapping offhand = new KeyMapping("key.swapOffhand", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F, "key.categories.inventory");
		require(!swapItems.same(offhand) && !offhand.hasKeyModifierConflict(swapItems),
				"the swap's row marks vanilla's F");
		require(offhand.same(swapItems), "vanilla's own F row no longer marks Ctrl+F: the rule reached beyond the mod's mappings");

		KeyMapping plainStandMode = ours("jojo_ripples.key.stand_mode", KeyModifier.NONE, GLFW.GLFW_KEY_R);
		require(summon.same(plainStandMode), "R and a Stand HUD mode rebound to plain R are not marked");
		KeyMapping otherCtrlR = new KeyMapping("key.rotp_test.other_ctrl_r", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
		require(summon.same(otherCtrlR), "another mapping on Ctrl+R lost NeoForge's in-game rule against R");

		// what reads the keys apart: consumeClick through NeoForge's lookup, and a scheme bind that needs its modifier
		String keybinds = compact(source("client/input/VanillaKeybinds.java"));
		require(keybinds.contains("if(standArmsOnlyHUD.consumeClick()){") && keybinds.contains("if(summonStand.consumeClick()){"),
				"the summon and the Stand HUD mode are no longer read by consumeClick: check that a press still fires only one");
		require(compact(source("subsystems/entity_externalcontainer/_stand/input/ClientStandItemInputs.java"))
				.contains("while(input.vanillaKeybinds.swapItems.consumeClick()){"), "the swap is no longer read by consumeClick");
		require(compact(source("client/input/controlscheme/ClientInputBind.java"))
				.contains("returnkeyModifier==KeyModifier.NONE||keyModifier==curModifier;"),
				"a scheme bind with a modifier may now match without it: the swap's bind could fire with plain F");
	}

	private static KeyMapping ours(String name, KeyModifier modifier, int key) {
		return new VanillaKeybinds.Jokerge(name, KeyConflictContext.IN_GAME, modifier, InputConstants.Type.KEYSYM, key, CATEGORY);
	}

	private static String compact(String text) {
		return text.replaceAll("\\s+", "");
	}

	private static String source(String file) {
		Path path = Path.of(System.getProperty("user.dir")).resolve("src/main/java/rotp/core").resolve(file);
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new IllegalStateException("KeyPairsToldApartSmokeTest: cannot read " + path, e);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("KeyPairsToldApartSmokeTest: " + message);
		}
	}
}
