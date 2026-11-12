package rotp.core.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.client.ui.hud_power.ResolvePips;
import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.ResolveHudLayout;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.client.ClientConfigEditor;
import rotp.core.config.editor.client.ConfigWidgetRules;
import rotp.core.core.JojoMod;

/**
 * The official version's finished HUD and menu pieces, rewritten into this core's settings (lane OFFMENUS). The HUD is
 * client-only, so its wiring is read from its bytecode instead of being loaded.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OfficialMenusGameTests {
	private static final String HUD = "rotp/core/client/ui/hud_power/PowerHud";
	private static final String PIPS = "rotp/core/client/ui/hud_power/ResolvePips";
	private static final String SETTINGS = "rotp/core/config/client/ClientModSettings";
	private static final String LAYOUT = "rotp/core/config/client/ResolveHudLayout";

	private OfficialMenusGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void resolvePipsShowTheLevelTheHighestLevelAndTheMode(GameTestHelper helper) {
		expectPips(helper, ResolvePips.of(0, 0, 4, -1), 0, 0, 0, "level 0 shows four empty pips");
		expectPips(helper, ResolvePips.of(2, 3, 4, -1), 2, 3, 0, "level 2 of a highest 3 fills two and outlines three");
		expectPips(helper, ResolvePips.of(3, 1, 4, -1), 3, 3, 0, "the highest level is never shown below the level");
		expectPips(helper, ResolvePips.of(4, 4, 4, -1), 4, 4, 0, "the max level fills all four");
		expectPips(helper, ResolvePips.of(9, 9, 4, -1), 4, 4, 0, "a level past four is clamped to the pips");
		expectPips(helper, ResolvePips.of(2, 3, 4, 2), 0, 0, 3, "Resolve mode started at level 2 shows its level 3");
		expectPips(helper, ResolvePips.of(4, 4, 4, 4), 0, 0, 4, "Resolve mode at the max level shows four");
		expectPips(helper, ResolvePips.of(0, 0, 4, 0), 0, 0, 1, "Resolve mode from level 0 shows one");
		helper.assertTrue(ResolvePips.of(2, 2, 5, -1) == null && ResolvePips.of(1, 1, 3, -1) == null,
				"A Stand whose level count is not four must keep the strip");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void resolveLayoutSettingRoundTripsAndDefaultsToTheStrip(GameTestHelper helper) {
		helper.assertTrue(new ClientModSettings.Settings().resolveHudLayout == ResolveHudLayout.STRIP,
				"Today's strip must stay the default layout");
		helper.assertTrue(readSettings(helper, "{\"resolveHudLayout\": \"PIPS\", \"standExpBar\": true}").resolveHudLayout == ResolveHudLayout.PIPS,
				"A saved PIPS layout must load back");
		ClientModSettings.Settings unknown = readSettings(helper, "{\"resolveHudLayout\": \"SIDEWAYS\", \"standExpBar\": true}");
		helper.assertTrue(unknown.resolveHudLayout == ResolveHudLayout.STRIP && unknown.standExpBar,
				"An unknown layout must fall back to the strip and keep the other settings");
		helper.assertTrue(readSettings(helper, "{}").resolveHudLayout == ResolveHudLayout.STRIP, "A file without the key must load the strip");

		ConfigEntry entry = clientEntry(helper, "resolveHudLayout");
		helper.assertTrue(entry.kind() == ConfigEntry.Kind.ENUM && entry.choices().equals(List.of("STRIP", "PIPS"))
				&& "STRIP".equals(entry.defaultValue().getAsString()), "The settings row must offer STRIP and PIPS, STRIP by default: " + entry);
		helper.assertTrue(inSection(ConfigEntry.Side.CLIENT, "hud", "resolveHudLayout"), "The layout row must sit in the HUD section");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void resolveHudDrawsTheOfficialPipsWhenChosen(GameTestHelper helper) {
		Set<String> render = refs(helper, HUD + "$Resolve", "renderElement");
		helper.assertTrue(render.contains(SETTINGS + ".getSettingsReadOnly") && render.contains(LAYOUT + ".PIPS")
				&& render.contains(PIPS + ".of") && render.contains(HUD + "$Resolve.renderPips"),
				"The Resolve icon no longer draws the pips when the PIPS layout is chosen");
		helper.assertTrue(render.contains("rotp/core/powersystem/standpower/StandUtil.resolveLevelFill"),
				"The Resolve icon lost the strip, which stays the default");
		Set<String> pips = refs(helper, HUD + "$Resolve", "renderPips");
		for (String sprite : List.of("PIPS_MODE", "PIPS_EMPTY", "PIPS_OUTLINED", "PIPS_FILLED")) {
			helper.assertTrue(pips.contains(HUD + "$Resolve." + sprite), "The pips no longer draw " + sprite);
		}
		Set<String> sprites = refs(helper, HUD + "$Resolve", "<clinit>");
		for (String name : List.of("\"stages_h\"", "\"stage_%d_h\"", "\"stage_unlocked%d_h\"", "\"lvl_%d_h\"")) {
			helper.assertTrue(sprites.contains(name), "The pip sprites no longer include " + name);
		}
		List<String> textures = new ArrayList<>(List.of("stages_h"));
		for (int i = 1; i <= ResolvePips.PIPS; i++) {
			textures.addAll(List.of("stage_" + i + "_h", "stage_unlocked" + i + "_h", "lvl_" + i + "_h"));
		}
		for (String name : textures) {
			expectPng(helper, "/assets/" + JojoMod.MOD_ID + "/textures/hud/stand_resolve/stand_resolve_" + name + ".png", 32, 16);
		}
		helper.succeed();
	}

	private static void expectPips(GameTestHelper helper, ResolvePips.Shown shown, int filled, int outlined, int mode, String what) {
		helper.assertTrue(shown != null && shown.filled() == filled && shown.outlined() == outlined && shown.modeLevel() == mode,
				what + " (got " + shown + ")");
	}

	static ClientModSettings.Settings readSettings(GameTestHelper helper, String json) {
		Path dir = FMLPaths.GAMEDIR.get().resolve("offmenus-tests");
		Path file = dir.resolve("client_settings-" + System.nanoTime() + ".json");
		try {
			Files.createDirectories(dir);
			Files.writeString(file, json, StandardCharsets.UTF_8);
			return ClientModSettings.readDetached(file.toFile());
		}
		catch (IOException e) {
			helper.fail("Cannot write " + file + ": " + e);
			return null;
		}
		finally {
			try {
				Files.deleteIfExists(file);
			}
			catch (IOException ignored) {
				// a leftover file in the test run folder harms nothing
			}
		}
	}

	static ConfigEntry clientEntry(GameTestHelper helper, String key) {
		ConfigEntry found = ClientConfigEditor.catalog().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
		helper.assertTrue(found != null, "No client settings row " + key);
		return found;
	}

	static boolean inSection(ConfigEntry.Side side, String section, String key) {
		List<ConfigEntry> shown = ClientConfigEditor.catalog().stream().filter(ConfigWidgetRules::isShown).toList();
		return ConfigWidgetRules.sections(side, shown).stream().anyMatch(s -> s.id().equals(section)
				&& s.entries().stream().anyMatch(entry -> entry.key().equals(key)));
	}

	static void expectPng(GameTestHelper helper, String path, int width, int height) {
		try (InputStream in = OfficialMenusGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing " + path);
			BufferedImage image = ImageIO.read(in);
			helper.assertTrue(image != null && image.getWidth() == width && image.getHeight() == height,
					path + " must be a " + width + "x" + height + " PNG");
		}
		catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
		}
	}

	// owner.name of every call, field and method reference, and "text" of every string, in each method of that name
	static Set<String> refs(GameTestHelper helper, String className, String methodName) {
		ClassNode node = new ClassNode();
		try (InputStream in = OfficialMenusGameTests.class.getResourceAsStream("/" + className + ".class")) {
			helper.assertTrue(in != null, "Missing class file " + className);
			new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}
		catch (IOException e) {
			helper.fail("Cannot read " + className + ": " + e);
		}
		Set<String> found = new HashSet<>();
		boolean seen = false;
		for (MethodNode method : node.methods) {
			if (!method.name.equals(methodName)) {
				continue;
			}
			seen = true;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call) {
					found.add(call.owner + "." + call.name);
				}
				else if (insn instanceof FieldInsnNode field) {
					found.add(field.owner + "." + field.name);
				}
				else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String text) {
					found.add("\"" + text + "\"");
				}
				else if (insn instanceof InvokeDynamicInsnNode indy) {
					for (Object arg : indy.bsmArgs) {
						if (arg instanceof Handle handle) {
							found.add(handle.getOwner() + "." + handle.getName());
						}
					}
				}
			}
		}
		helper.assertTrue(seen, "Missing method " + className + "." + methodName);
		return found;
	}
}
