package rotp.core.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Skills screen texts that tell the controls: they name the keys the client fills in (StandSkillText) in each of the core's
 * twelve languages, in that language. How the screen lays them out is an in-game check.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillTextRulesGameTests {
	private static final List<String> LANGUAGES = List.of("en_us", "en_pt", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br",
			"ru_ru", "uk_ua", "zh_cn", "zh_tw");

	private SkillTextRulesGameTests() {}

	/*
	 * OQ-C10: Hierophant Green's barrier description says how the net comes down (the variation press), and in each language
	 * the whole line is in that language; ten of them had kept the English first sentence.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theBarrierTellsHowTheNetComesDownInEachLanguage(GameTestHelper helper) {
		String english = value(helper, "en_us", "jojo_ripples.skill.barrier.desc");
		String firstSentence = english.substring(0, english.indexOf('.') + 1);
		for (String language : LANGUAGES) {
			String desc = value(helper, language, "jojo_ripples.skill.barrier.desc");
			helper.assertTrue(count(desc, "%s") == 1, language + "'s barrier description does not name the variation key: " + desc);
			helper.assertTrue(language.startsWith("en_") || !desc.contains(firstSentence),
					language + "'s barrier description is partly English: " + desc);
		}
		helper.succeed();
	}

	private static String value(GameTestHelper helper, String language, String key) {
		JsonObject words = StandClipPoses116GameTests.readJson(helper, "lang/" + language + ".json");
		helper.assertTrue(words.has(key), language + " lacks " + key);
		return words.get(key).getAsString();
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
			n++;
		}
		return n;
	}

	private static String compact(String source) {
		return source.replaceAll("\\s+", "");
	}

	private static String source(String file) {
		for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
			Path path = at.resolve("src/main/java/rotp/core").resolve(file);
			if (Files.isRegularFile(path)) {
				try {
					return Files.readString(path);
				}
				catch (IOException e) {
					throw new IllegalStateException("Could not read " + path, e);
				}
			}
		}
		throw new IllegalStateException("No source of " + file + " above " + Path.of("").toAbsolutePath());
	}
}
