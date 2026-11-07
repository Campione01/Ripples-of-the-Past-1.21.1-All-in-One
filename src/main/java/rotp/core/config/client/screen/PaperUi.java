package rotp.core.config.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.ModList;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.client.ConfigWidgetRules;
import rotp.core.core.JojoMod;

/** The paper window's colours, glyphs and text fitting, shared by the settings screen and its dialogs. */
final class PaperUi {
	static final int PAPER_LIGHT = 0xFFF8F9FA;
	static final int PANEL = 0xFFD1D6E0;
	static final int LINE = 0xFF989EAB;
	static final int LINE_SOFT = 0xFFC7CAD4;
	static final int INK = 0xFF202020;
	static final int INK_SOFT = 0xFF565C6B;
	static final int INK_FAINT = 0xFF8C92A0;
	static final int HOVER = 0x12000000;
	static final int ACCENT = 0xFF6A4CC4;
	static final int ACCENT_FILL = 0xFFBFB4E8;
	static final int ON = 0xFF3C9A55;
	static final int ERROR = 0xFFC2302B;
	static final int PENDING = 0xFFB97C12;
	static final int ROW_HEIGHT = 22;
	static final int CONTROL_HEIGHT = 18;

	static final ResourceLocation WINDOW = JojoMod.resLoc("jojo_ripples/paper_style/window");
	private static final ResourceLocation GLYPHS = JojoMod.resLoc("textures/gui/settings/glyphs.png");
	private static final int GLYPH_CELL = 9;

	enum Glyph {
		RESET(0, 0), SYNC(1, 0), RESTART(2, 0), LOCK(3, 0), CHECK(4, 0), CHEVRON_RIGHT(5, 0), CHEVRON_DOWN(6, 0), ARROW(7, 0),
		PLUS(0, 1), MINUS(1, 1), WARNING(2, 1);

		final int column;
		final int row;

		Glyph(int column, int row) {
			this.column = column;
			this.row = row;
		}
	}

	private PaperUi() {}

	static void glyph(GuiGraphics graphics, Glyph glyph, int x, int y, int color) {
		RenderSystem.enableBlend();
		graphics.setColor(FastColor.ARGB32.red(color) / 255F, FastColor.ARGB32.green(color) / 255F,
				FastColor.ARGB32.blue(color) / 255F, FastColor.ARGB32.alpha(color) / 255F);
		graphics.blit(GLYPHS, x, y, glyph.column * GLYPH_CELL, glyph.row * GLYPH_CELL, GLYPH_CELL, GLYPH_CELL, 72, 72);
		graphics.setColor(1F, 1F, 1F, 1F);
	}

	static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color, false);
	}

	static void text(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color, false);
	}

	/** One or two lines that fit: a label that needs more than two ends in dots, and the row's tooltip has the whole of it. */
	static List<FormattedCharSequence> fit(Font font, String text, int width, int maxLines) {
		List<FormattedText> lines = font.getSplitter().splitLines(text, Math.max(8, width), Style.EMPTY);
		List<FormattedCharSequence> fitted = new ArrayList<>();
		for (int i = 0; i < lines.size() && i < maxLines; i++) {
			String line = lines.get(i).getString();
			if (i == maxLines - 1 && lines.size() > maxLines) {
				String rest = lines.subList(i, lines.size()).stream().map(FormattedText::getString).collect(Collectors.joining());
				line = font.plainSubstrByWidth(rest, Math.max(8, width - font.width("..."))).stripTrailing() + "...";
			}
			fitted.add(Component.literal(line).getVisualOrderText());
		}
		return fitted;
	}

	static String oneLine(Font font, String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(8, width - font.width("..."))).stripTrailing() + "...";
	}

	static Component label(ConfigEntry entry) {
		String key = ConfigWidgetRules.labelKey(entry);
		Language language = Language.getInstance();
		if (!language.has(key)) {
			return Component.literal(ConfigWidgetRules.fallbackName(entry.key()));
		}
		return Component.literal(ConfigWidgetRules.labelWithoutValue(language.getOrDefault(key)));
	}

	static Component optionText(ConfigEntry entry, String choice) {
		String fallback = ConfigWidgetRules.isAddon(entry) ? ConfigWidgetRules.fallbackChoice(choice) : ConfigWidgetRules.fallbackName(choice);
		return Component.translatableWithFallback(ConfigWidgetRules.optionKey(entry, choice), fallback);
	}

	/** An add-on's name in the list: its config page's title if it has one, else what the mod list calls it. */
	static String blockName(String modId) {
		Language language = Language.getInstance();
		String title = modId + ".configuration.title";
		if (language.has(title)) {
			return ConfigWidgetRules.blockName(language.getOrDefault(title));
		}
		return ModList.get().getModContainerById(modId)
				.map(container -> ConfigWidgetRules.blockNameFromDisplayName(container.getModInfo().getDisplayName()))
				.orElseGet(() -> ConfigWidgetRules.fallbackName(modId));
	}

	/** A group of an add-on, by the same text as its page in the mod list; a nested one names its whole path. */
	static String groupName(String modId, String path) {
		List<String> names = new ArrayList<>();
		for (String segment : path.split("\\.")) {
			names.add(Language.getInstance().getOrDefault(modId + ".configuration." + segment, ConfigWidgetRules.fallbackName(segment)));
		}
		return String.join(" · ", names);
	}

	static Component valueText(ConfigEntry entry, JsonElement value) {
		return switch (entry.kind()) {
			case BOOLEAN -> Component.translatable(value.getAsBoolean() ? "options.on" : "options.off");
			case INTEGER, DECIMAL -> Component.literal(ConfigWidgetRules.formatNumber(entry.kind(), value));
			case ENUM -> optionText(entry, ConfigWidgetRules.shownChoice(entry, value));
			case STRING -> entry.choices().isEmpty() ? Component.literal(value.getAsString())
					: optionText(entry, ConfigWidgetRules.shownChoice(entry, value));
			case LIST -> Component.literal(listText(entry, value));
		};
	}

	private static String listText(ConfigEntry entry, JsonElement value) {
		if (!value.isJsonArray()) {
			return "";
		}
		List<String> items = new ArrayList<>();
		for (JsonElement item : value.getAsJsonArray()) {
			boolean number = entry.elementKind() == ConfigEntry.Kind.INTEGER || entry.elementKind() == ConfigEntry.Kind.DECIMAL;
			items.add(number ? ConfigWidgetRules.formatNumber(entry.elementKind(), item) : item.getAsString());
		}
		return String.join(", ", items);
	}
}
