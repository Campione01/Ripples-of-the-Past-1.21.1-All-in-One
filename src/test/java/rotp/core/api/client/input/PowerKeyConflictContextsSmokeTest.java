package rotp.core.api.client.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * OQ-C05: the key binds screen marks two mappings as sharing a key by KeyMapping#same and #hasKeyModifierConflict, as
 * KeyBindsList.KeyEntry asks them. Keys two powers' HUDs take through their schemes never act on one press and are not
 * marked; against vanilla's keys, and keys that act whatever HUD is open, they are. A context stays active exactly when
 * IN_GAME is, and an in-game key with no modifier still acts with Ctrl held, so presses are unchanged.
 */
public final class PowerKeyConflictContextsSmokeTest {
	private static final String CATEGORY = "key.categories.rotp_test";
	private static final ResourceLocation STAND_A = ResourceLocation.fromNamespaceAndPath("rotp_test", "stand_a");
	private static final ResourceLocation STAND_B = ResourceLocation.fromNamespaceAndPath("rotp_test", "stand_b");
	private static final ResourceLocation LIFEFORM = ResourceLocation.fromNamespaceAndPath("rotp_test", "lifeform");
	private static int names;

	private PowerKeyConflictContextsSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		KeyMapping standA = PowerKeyConflictContexts.standKey(name(), STAND_A, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
		KeyMapping standB = PowerKeyConflictContexts.standKey(name(), STAND_B, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
		KeyMapping lifeform = PowerKeyConflictContexts.nonStandPowerKey(name(), LIFEFORM, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
				CATEGORY);
		KeyMapping anyStand = new PowerKeyConflictContexts.PowerKeyMapping(name(), PowerKeyConflictContexts.stand(), KeyModifier.NONE,
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
		KeyMapping vanilla = new KeyMapping(name(), KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
		KeyMapping vanillaCtrl = new KeyMapping(name(), KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
		KeyMapping standCtrlG = new PowerKeyConflictContexts.PowerKeyMapping(name(), PowerKeyConflictContexts.stand(STAND_A),
				KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
		KeyMapping lifeformCtrl = PowerKeyConflictContexts.nonStandPowerKey(name(), LIFEFORM, InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);

		require(!marked(standA, standB), "two Stands' own keys on one key are marked as sharing it");
		require(!marked(standA, lifeform), "a Stand's key and a non-Stand power's key on one key are marked as sharing it");
		require(!marked(anyStand, lifeform), "a key every Stand's HUD takes and a non-Stand power's key are marked as sharing it");
		require(!marked(standCtrlG, lifeformCtrl), "a Stand's Ctrl+G and a non-Stand power's Left Control are marked");
		require(marked(standA, anyStand), "a Stand's own key and a key every Stand's HUD takes are not marked");
		require(marked(standA, vanilla), "a Stand's key and a vanilla key on one key are not marked");
		require(marked(lifeform, vanilla), "a non-Stand power's key and a vanilla key on one key are not marked");
		require(marked(standCtrlG, vanillaCtrl), "a Stand's Ctrl+G and vanilla's Left Control are not marked");

		require(standA.getKeyConflictContext().conflicts(KeyConflictContext.IN_GAME)
				&& !standA.getKeyConflictContext().conflicts(KeyConflictContext.GUI),
				"a power context is not in-game against vanilla's contexts");
		require(KeyModifier.NONE.isActive(standA.getKeyConflictContext()),
				"an in-game key with no modifier must still act with a modifier held, as NeoForge's IN_GAME does");

		String keybinds = compact(source("client/input/VanillaKeybinds.java"));
		for (String stand : new String[] { "GRAB_CHARGED_HEAVY_KEY_MAPPING_NAME,standHudKey(GRAB_CHARGED_HEAVY_CONFLICT_CONTEXT)",
				"GRAB_THROW_KEY_MAPPING_NAME,standHudKey(whileBound(()->binds.grabThrow))",
				"GRAB_RELEASE_KEY_MAPPING_NAME,standHudKey(whileBound(()->binds.grabRelease))" }) {
			require(keybinds.contains(stand), "a grab key lost its Stand HUD scope: " + stand);
		}
		for (String inGame : new String[] { "\".key.toggle_stand\",KeyConflictContext.IN_GAME", "\".key.stand_mode\",KeyConflictContext.IN_GAME",
				"\".key.non_stand_mode\",KeyConflictContext.IN_GAME", "\".key.hamon_breath\",KeyConflictContext.IN_GAME",
				"SWAP_ITEMS_KEY_MAPPING_NAME,KeyConflictContext.IN_GAME", "\".key.use_special_ability\",KeyConflictContext.IN_GAME",
				"VariationKey.mappingName(),KeyConflictContext.IN_GAME", "\".key.ability_hotbar\",KeyConflictContext.IN_GAME",
				"\".key.disable_hotbars\",KeyConflictContext.IN_GAME" }) {
			require(keybinds.contains(inGame), "a key that acts whatever HUD is open left IN_GAME: " + inGame);
		}
		require(keybinds.contains("publicbooleansame(KeyMappingother){return!PowerKeyConflictContexts.separates(this,other)&&"),
				"the mod's own mappings do not honour the power contexts in the key binds screen");
	}

	// as KeyBindsList.KeyEntry.refreshEntry marks a row, seen from either row
	private static boolean marked(KeyMapping a, KeyMapping b) {
		boolean fromA = a.same(b) || b.hasKeyModifierConflict(a);
		boolean fromB = b.same(a) || a.hasKeyModifierConflict(b);
		require(fromA == fromB, "the two rows disagree: " + a.getName() + " " + fromA + ", " + b.getName() + " " + fromB);
		return fromA;
	}

	private static String name() {
		return "key.rotp_test.power_conflict_" + names++;
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
			throw new IllegalStateException("PowerKeyConflictContextsSmokeTest: cannot read " + path, e);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("PowerKeyConflictContextsSmokeTest: " + message);
		}
	}
}
