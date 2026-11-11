package rotp.core.client.ui.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The Stand skills screen's passive skills: under the skills, a header and a row each, hovered and clicked by the same
 * rule as the skills' rows; a row's cooldown in whole seconds; the header's label and a passive's controls text fit
 * their places in every language the core ships. Widths are estimated at 6 pixels a character and 9 for a full-width
 * one, the default font's widest common glyphs; the look is an in-game check.
 */
public final class SkillListLayoutSmokeTest {
	private static final String LANG = "src/main/resources/assets/jojo_ripples/lang/";
	private static final List<String> LANGUAGES = List.of("en_us", "en_pt", "es_es", "es_ve", "it_it", "pt_br", "ru_ru",
			"uk_ua", "ja_jp", "zh_cn", "zh_tw", "lzh");
	// the screen's controls box (StandSkillsScreen: 101 wide text, ScrollingText 31 high, 9 a line, 3 of padding)
	private static final int CONTROLS_WIDTH = 101;
	private static final int CONTROLS_HEIGHT = 31;
	// the room right of a row's icon, to the list's scissor edge (skill list x + 60, icon at + 2, text at icon + 18)
	private static final int ROW_TEXT_ROOM = 40;

	private SkillListLayoutSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		require(SkillListLayout.height(5, 0) == 102, "without passives the list is as tall as before: 5 skills, 102");
		require(SkillListLayout.height(5, 2) == 5 * 20 + 14 + 2 * 20 + 2, "two passives add their header and rows");
		for (int pos = 0; pos < 100; pos++) {
			int before = pos / 20 < 5 && pos % 20 > 3 ? pos / 20 : -1;
			require(SkillListLayout.skillAt(pos, 5) == before, "a skill row is hovered as before at " + pos);
		}
		require(SkillListLayout.skillAt(100, 5) == -1 && SkillListLayout.skillAt(-1, 5) == -1, "nothing past the skills");
		for (int pos = 100; pos < 114; pos++) {
			require(SkillListLayout.passiveAt(pos, 5, 2) == -1, "the passives' header is no row: " + pos);
		}
		require(SkillListLayout.passiveAt(114 + 3, 5, 2) == -1 && SkillListLayout.passiveAt(114 + 4, 5, 2) == 0
				&& SkillListLayout.passiveAt(114 + 20 + 4, 5, 2) == 1 && SkillListLayout.passiveAt(114 + 40 + 4, 5, 2) == -1,
				"each passive's row starts four pixels in, as a skill's");
		require(SkillListLayout.passiveRowTop(5, 1) + SkillListLayout.ROW_GAP == 114 + 24, "a passive's icon is where its row is hovered");

		require(SkillListLayout.secondsShown(0) == 0 && SkillListLayout.secondsShown(1) == 1 && SkillListLayout.secondsShown(20) == 1
				&& SkillListLayout.secondsShown(21) == 2 && SkillListLayout.secondsShown(1_000_000) == SkillListLayout.MAX_SECONDS_SHOWN,
				"a cooldown shows its seconds rounded up, at most " + SkillListLayout.MAX_SECONDS_SHOWN);

		require(TextBoxFit.labelScale(50, 56) == 1.0F && TextBoxFit.labelScale(60, 56) == 0.85F
				&& TextBoxFit.labelScale(70, 56) == 0.75F && TextBoxFit.labelScale(100, 56) == TextBoxFit.SMALLEST_SCALE,
				"a label too wide for its room is drawn smaller");

		for (String lang : LANGUAGES) {
			JsonObject texts = read(lang);
			String label = text(texts, lang, "jojo_ripples.stand_skills.passives");
			require(width(label) * TextBoxFit.labelScale(width(label), SkillListLayout.LABEL_ROOM) <= SkillListLayout.LABEL_ROOM,
					lang + ": the passives' label \"" + label + "\" is wider than the list at any size");
			String seconds = text(texts, lang, "jojo_ripples.stand_skills.passive.seconds")
					.replace("%s", String.valueOf(SkillListLayout.MAX_SECONDS_SHOWN));
			require(width(seconds) <= ROW_TEXT_ROOM, lang + ": a row's cooldown \"" + seconds + "\" runs past the list");
			String passive = text(texts, lang, "jojo_ripples.stand_skills.passive");
			for (String state : List.of(text(texts, lang, "jojo_ripples.stand_skills.passive.ready"),
					text(texts, lang, "jojo_ripples.stand_skills.passive.cooldown").replace("%s", "999.9"),
					text(texts, lang, "jojo.message.action_condition.resolve_level").replace("%s", "4"))) {
				float scale = TextBoxFit.scaleToFit(splitWidth -> lines(passive, splitWidth) + lines(state, splitWidth),
						CONTROLS_WIDTH, CONTROLS_HEIGHT, 9, 3);
				int lines = lines(passive, TextBoxFit.splitWidth(CONTROLS_WIDTH, scale))
						+ lines(state, TextBoxFit.splitWidth(CONTROLS_WIDTH, scale));
				require(TextBoxFit.fitsWhole(lines, scale, CONTROLS_HEIGHT, 9, 3), lang + ": \"" + passive + "\" and \"" + state
						+ "\" do not fit the controls box whole, " + lines + " lines at " + scale);
			}
		}
	}

	// lines a text splits into at a width, a word wrap leaving up to a sixth of a line empty
	private static int lines(String text, int width) {
		int usable = width * 5 / 6;
		return Math.max(1, (width(text) + usable - 1) / usable);
	}

	private static int width(String text) {
		int width = 0;
		for (int i = 0; i < text.length(); i++) {
			width += text.charAt(i) >= 0x2E80 ? 9 : 6;
		}
		return width;
	}

	private static JsonObject read(String lang) {
		try {
			return JsonParser.parseString(Files.readString(Path.of(LANG + lang + ".json"))).getAsJsonObject();
		}
		catch (IOException e) {
			throw new IllegalStateException("SkillListLayoutSmokeTest: cannot read " + lang, e);
		}
	}

	private static String text(JsonObject texts, String lang, String key) {
		require(texts.has(key), lang + " has no " + key);
		return texts.get(key).getAsString();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("SkillListLayoutSmokeTest: " + message);
		}
	}
}
