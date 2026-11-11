package rotp.core.client.standskin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * OQ-C20: the skins tab's tooltip names the hovered skin above its authors line: the Stand's name as the skin's language
 * files give it (Gold Experience Requiem's skins name themselves so), the Stand's own name for its default skin, else a
 * name made of the skin's id. The tooltip's look is an in-game check.
 */
public final class StandSkinNamesSmokeTest {
	private StandSkinNamesSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		ResourceLocation original = ResourceLocation.fromNamespaceAndPath("geraddon", "ger_original");
		require("Gold Experience Requiem (Original)".equals(StandSkinNames.displayName("Gold Experience Requiem (Original)",
				original, false, () -> Component.literal("Gold Experience Requiem")).getString()),
				"a skin's own translation of its Stand's name is its name");
		require("Gold Experience Requiem".equals(StandSkinNames.displayName(null,
				ResourceLocation.fromNamespaceAndPath("geraddon", "ger_stand"), true, () -> Component.literal("Gold Experience Requiem"))
				.getString()), "a default skin with no name of its own takes its Stand's");
		require("The Grateful Dead Purple Repaint".equals(StandSkinNames.displayName(" ",
				ResourceLocation.fromNamespaceAndPath("rotp_tgd", "the_grateful_dead_purple_repaint"), false,
				() -> Component.literal("The Grateful Dead")).getString()), "another skin is named after its id");

		String screen = compact(source("client/standskin/StandSkinsScreen.java"));
		int name = screen.indexOf("lines.add(view.skin.displayName()");
		int authors = screen.indexOf("view.skin.authorsLine().ifPresent(line->lines.add(");
		require(name >= 0 && authors > name && screen.contains("setTooltipForNextRenderPass(lines);"),
				"the skins tab's tooltip puts the skin's name above its authors");
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
			throw new IllegalStateException("StandSkinNamesSmokeTest: cannot read " + path, e);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("StandSkinNamesSmokeTest: " + message);
		}
	}
}
