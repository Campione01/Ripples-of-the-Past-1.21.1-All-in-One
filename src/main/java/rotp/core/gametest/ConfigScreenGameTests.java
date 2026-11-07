package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.config.editor.CommonConfigEditor;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.CoreConfigPresets;
import rotp.core.config.editor.client.ClientConfigEditor;
import rotp.core.config.editor.client.ConfigWidgetRules;
import rotp.core.core.JojoMod;

/** Every setting either store offers has a row, a widget and a name, and the screen's own text is in all twelve languages. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigScreenGameTests {
	private static final List<String> LOCALES = List.of("en_us", "en_pt", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br", "ru_ru", "uk_ua", "zh_cn", "zh_tw");
	private static final String SCREEN = "jojo_ripples.config.screen.";

	private static final List<String> NEW_TEXT = Stream.of(
			Stream.of("title", "category", "page.client", "page.client.desc", "page.gameplay", "page.gameplay.desc", "read_only.not_op",
					"read_only.no_world", "loading", "unsupported", "no_answer", "retry", "reset", "reset_section", "reset_section_title",
					"reset_all", "reset_all_title", "confirm_reset", "default", "range", "range_from", "range_above_zero", "needs",
					"flag.sent_to_server", "flag.sent_to_players", "flag.restart_world", "flag.restart_game", "list.empty", "list.values",
					"list.add", "list.remove", "list.keep", "list.discard", "list.discard_question", "preset_shows_first", "preset_read_only",
					"preview.title", "preview.changes", "preview.none", "preview.apply", "preview.restart", "status.saved", "status.reset",
					"status.preset_applied", "status.waiting", "status.no_answer", "status.forbidden", "status.invalid_value",
					"status.unavailable", "status.unknown_key", "status.unknown_group", "status.unknown_preset", "status.save_failed")
					.map(key -> SCREEN + key),
			Stream.of("presets", "effects", "hud", "controls", "your_stand", "stand_display", "hamon_vampire", "stand_aura", "keep_on_death",
					"stand", "arrows", "time_stop", "hamon", "vampirism", "world", "other").map(key -> ConfigWidgetRules.SECTION_TEXT + key),
			Stream.of("standArrowMode.none", "standArrowMode.least_taken", "standArrowMode.not_taken",
					"standBodyThroughWalls.standard", "standBodyThroughWalls.phasing").map(key -> ConfigWidgetRules.OPTION_TEXT + key),
			Stream.of("standStatsTranslucency", "standStatsTranslucency.tooltip", "standStatsInvertBnW", "standStatsInvertBnW.tooltip",
					"toggleDisableHotbars", "toggleDisableHotbars.tooltip").map(key -> ConfigWidgetRules.CLIENT_TEXT + key),
			Stream.of("defaults", "defaults.desc", "defaults_title").map(key -> "jojo_ripples.config.preset." + key)).flatMap(s -> s).toList();

	// words that stay the same in that language, and a name of the world's own
	private static final List<String> SAME_AS_ENGLISH = List.of(
			ConfigWidgetRules.SECTION_TEXT + "hud", ConfigWidgetRules.SECTION_TEXT + "hamon", ConfigWidgetRules.SECTION_TEXT + "stand",
			SCREEN + "page.client", ConfigWidgetRules.OPTION_TEXT + "standBodyThroughWalls.standard");

	private ConfigScreenGameTests() {}

	// the core's own: an add-on's text lives in that add-on and is checked there
	private static List<ConfigEntry> shownEntries() {
		List<ConfigEntry> entries = new ArrayList<>(coreOnly(CommonConfigEditor.entries()));
		coreOnly(ClientConfigEditor.catalog()).stream().filter(ConfigWidgetRules::isShown).forEach(entries::add);
		return entries;
	}

	private static List<ConfigEntry> coreOnly(List<ConfigEntry> entries) {
		return entries.stream().filter(entry -> !ConfigWidgetRules.isAddon(entry)).toList();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void everyCatalogKeyHasAWidgetAndANameInEveryLanguage(GameTestHelper helper) {
		List<ConfigEntry> entries = shownEntries();
		helper.assertTrue(entries.stream().anyMatch(entry -> ConfigWidgetRules.leaf(entry.key()).equals("timeStopChargeSeconds")),
				"timeStopChargeSeconds must be in the catalog");
		helper.assertTrue(entries.size() == coreOnly(CommonConfigEditor.entries()).size() + coreOnly(ClientConfigEditor.catalog()).size()
				- ConfigWidgetRules.hiddenKeys().size(),
				"the hidden keys must name real settings: " + ConfigWidgetRules.hiddenKeys());
		List<String> missing = new ArrayList<>();
		for (String locale : LOCALES) {
			JsonObject lang = readLang(locale);
			JsonObject english = readLang("en_us");
			for (ConfigEntry entry : entries) {
				helper.assertTrue(ConfigWidgetRules.widgetKind(entry) != null, entry.key() + " has no widget");
				String key = ConfigWidgetRules.labelKey(entry);
				String text = stringOr(english, key);
				if (text == null || ConfigWidgetRules.labelWithoutValue(text).isBlank()) {
					missing.add("en_us " + key);
				}
				// a language file may leave a word to English, as Minecraft falls back to it; a missing one in a real translation is a hole
				String own = stringOr(lang, key);
				if (own == null && !locale.startsWith("en_")) {
					missing.add(locale + " " + key);
				}
				else if (own != null && ConfigWidgetRules.labelWithoutValue(own).isBlank()) {
					missing.add(locale + " " + key + " is empty");
				}
			}
		}
		helper.assertTrue(missing.isEmpty(), "settings without a name: " + missing);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void everyListedSectionAndOptionHasItsText(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		JsonObject english = readLang("en_us");
		for (ConfigEntry.Side side : ConfigEntry.Side.values()) {
			for (ConfigWidgetRules.Section section : ConfigWidgetRules.sections(side, shownEntries().stream().filter(entry -> entry.side() == side).toList())) {
				if (stringOr(english, ConfigWidgetRules.SECTION_TEXT + section.id()) == null) {
					missing.add("section " + section.id());
				}
				if (section.id().equals(ConfigWidgetRules.OTHER_SECTION)) {
					missing.add("no section of its own for " + section.entries().stream().map(ConfigEntry::key).toList());
				}
				for (ConfigEntry entry : section.entries()) {
					if (ConfigWidgetRules.widgetKind(entry) == ConfigWidgetRules.WidgetKind.CYCLE) {
						for (String choice : entry.choices()) {
							if (stringOr(english, ConfigWidgetRules.optionKey(entry, choice)) == null) {
								missing.add("option " + ConfigWidgetRules.optionKey(entry, choice));
							}
						}
					}
				}
			}
		}
		helper.assertTrue(missing.isEmpty(), "the screen would show raw names: " + missing);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void presetsNameOnlyRealSettingsAndHaveTheirText(GameTestHelper helper) {
		List<String> keys = CommonConfigEditor.entries().stream().map(ConfigEntry::key).toList();
		List<String> problems = new ArrayList<>();
		for (CoreConfigPresets.Preset preset : CoreConfigPresets.entries()) {
			preset.values().keySet().stream().filter(key -> !keys.contains(key)).forEach(key -> problems.add(preset.id() + " names " + key));
			for (String locale : LOCALES) {
				JsonObject lang = readLang(locale);
				for (String key : List.of("jojo_ripples.config.preset." + preset.id(), "jojo_ripples.config.preset." + preset.id() + ".desc")) {
					if (stringOr(lang, key) == null) {
						problems.add(locale + " " + key);
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), "presets: " + problems);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theScreensTextIsInAllTwelveLanguages(GameTestHelper helper) {
		JsonObject english = readLang("en_us");
		List<String> missing = new ArrayList<>();
		for (String locale : LOCALES) {
			JsonObject lang = readLang(locale);
			for (String key : NEW_TEXT) {
				String text = stringOr(lang, key);
				String base = stringOr(english, key);
				if (text == null || text.isBlank() || base == null) {
					missing.add(locale + " " + key);
				}
				else if (!locale.startsWith("en_") && text.equals(base) && !SAME_AS_ENGLISH.contains(key) && !looksSharedInEveryLanguage(key)) {
					missing.add(locale + " " + key + " is the English text");
				}
				else if (countOf(text, "%s") != countOf(base, "%s")) {
					missing.add(locale + " " + key + " differs from English in its %s");
				}
			}
		}
		helper.assertTrue(missing.isEmpty(), "new text missing: " + missing);
		helper.succeed();
	}

	// "Stand" and "Hamon" are this mod's own words in the Latin-script translations
	private static boolean looksSharedInEveryLanguage(String key) {
		return key.endsWith("section.stand") || key.endsWith("section.hamon") || key.endsWith("section.hud");
	}

	private static int countOf(String text, String part) {
		int count = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
			count++;
		}
		return count;
	}

	private static String stringOr(JsonObject lang, String key) {
		JsonElement value = lang.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static JsonObject readLang(String locale) {
		String path = "assets/" + JojoMod.MOD_ID + "/lang/" + locale + ".json";
		try (InputStream in = JojoModConfig.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new GameTestAssertException(path + " is missing");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (IOException e) {
			throw new GameTestAssertException("Could not read " + path + ": " + e);
		}
	}
}
