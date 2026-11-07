package rotp.core.client.ui.hud_power;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rotp.core.client.input.controlscheme.HotbarSelectionRules;
import rotp.core.client.ui.hud_misc.StandExpBar;
import rotp.core.config.client.ClientModSettings;
import rotp.core.impl.powers.vampirism.SunsetClock;
import rotp.core.powersystem.unlockableskill.UnlockableSkill.DevStatus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** What the official port's HUD items come to, off screen: the rules, the source that uses them and the text in every language. */
public final class OfficialParitySmokeTest {
	private static final Path ROOT = Path.of(System.getProperty("user.dir"));
	private static final Path CLIENT = ROOT.resolve("src/main/java/rotp/core/client");
	private static final List<String> LANGS = List.of("en_pt", "en_us", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br",
			"ru_ru", "uk_ua", "zh_cn", "zh_tw");
	private static final List<String> NEW_KEYS = List.of("jojo.config.standExpMultiplier", "jojo.config.standExpMultiplier.tooltip",
			"jojo_ripples.config.client.standExpBar", "jojo_ripples.config.client.standExpBar.tooltip",
			"jojo_ripples.clock.sunset_in", "jojo_ripples.stand_skins.authors",
			"item.jojo_ripples.knife.hint", "item.jojo_ripples.gloves.hint",
			"block.jojo_ripples.wooden_coffin.hint", "block.jojo_ripples.wooden_coffin.hint2",
			"item.jojo_ripples.walkman.hint",
			"item.jojo_ripples.cassette_recorded.hint.play", "item.jojo_ripples.cassette_recorded.hint.copy");
	private static final Pattern REBINDABLE_KEY_NAME = Pattern.compile(
			"Shift|Ctrl|Control|LMB|RMB|Right.?click|Left.?click|右键|右鍵|左键|左鍵|ПКМ|ЛКМ|Clic", Pattern.CASE_INSENSITIVE);
	private static final Pattern PLACEHOLDER = Pattern.compile("%s");

	private OfficialParitySmokeTest() {}

	public static void main(String[] args) throws IOException {
		verifySkillRows();
		verifyExpBar();
		verifyStaminaMiniBar();
		verifyEmptyBar();
		verifyWheelKeeps();
		verifySunsetClock();
		verifyLanguages();
		System.out.println("Official parity smoke test passed");
	}

	private static void verifySkillRows() throws IOException {
		check(DevStatus.WIP.rowTint == 0x80FFFF00, "a work-in-progress skill's row is yellow: " + Integer.toHexString(DevStatus.WIP.rowTint));
		check(DevStatus.NYI.rowTint == 0x80FF0000, "a skill not yet implemented keeps its red row");
		check(DevStatus.IMPLEMENTED.rowTint == 0, "an ordinary skill has no row colour");
		String screen = read(ROOT.resolve("src/main/java/rotp/core/powersystem/standpower/client_screens/StandSkillsScreen.java"));
		check(screen.contains("skill.implemented.rowTint != 0") && screen.contains("spriteY + 17, skill.implemented.rowTint)"),
				"the skills screen must draw the row in its status's colour");
	}

	private static void verifyExpBar() throws IOException {
		check(!new ClientModSettings.Settings().standExpBar, "the Stand EXP bar is off until the player turns it on");
		check(StandExpBar.replacesVanillaBar(true, true, true, 100), "on, with the Stand out, it takes the bar's place");
		check(!StandExpBar.replacesVanillaBar(false, true, true, 100), "off, the vanilla bar stays");
		check(!StandExpBar.replacesVanillaBar(true, false, true, 100), "with the Stand recalled, the vanilla bar comes back");
		check(!StandExpBar.replacesVanillaBar(true, true, false, 100), "no bar where the game shows none (creative, a jumping mount)");
		check(!StandExpBar.replacesVanillaBar(true, true, true, 0), "a Stand with nothing bought with exp leaves the vanilla bar");

		check(StandExpBar.filledPixels(0, 0, 100) == 0, "nothing earned, nothing filled");
		check(StandExpBar.filledPixels(50, 0, 100) == 91, "what was spent counts: half of 182 is 91, was " + StandExpBar.filledPixels(50, 0, 100));
		check(StandExpBar.filledPixels(25, 25, 100) == 91, "as does what is there to spend");
		check(StandExpBar.filledPixels(60, 90, 100) == StandExpBar.WIDTH, "exp past the last skill stops at a full bar");
		check(StandExpBar.filledPixels(0, 5, 0) == 0, "no total, no fill and no division by it");
		check(StandExpBar.filledPixels(0, -5, 100) == 0, "a negative exp does not draw backwards");

		String bar = read(CLIENT.resolve("ui/hud_misc/StandExpBar.java"));
		check(bar.contains("getSettingsReadOnly().standExpBar"), "the bar must read its client setting");
		check(bar.contains("VanillaGuiLayers.EXPERIENCE_BAR") && bar.contains("VanillaGuiLayers.EXPERIENCE_LEVEL") && bar.contains("event.setCanceled(true)"),
				"the bar must replace both vanilla layers");
		check(bar.contains("mc.options.hideGui"), "the HUD hidden hides the bar too: the event comes with the HUD hidden as well");
		String settings = read(ROOT.resolve("src/main/java/rotp/core/config/client/ClientModSettingsScreen.java"));
		check(settings.contains("jojo_ripples.config.client.standExpBar\""), "the setting must have a button");
	}

	private static void verifyStaminaMiniBar() throws IOException {
		check(StaminaMiniBar.shows(true, false, false, 0.5F), "stamina filling up with its own bar out of sight shows");
		check(!StaminaMiniBar.shows(true, false, false, 1F), "full stamina shows nothing");
		check(!StaminaMiniBar.shows(true, false, true, 0.5F), "the full bar on screen leaves no room for it");
		check(!StaminaMiniBar.shows(true, true, false, 0.5F), "a creative player has no stamina to watch");
		check(!StaminaMiniBar.shows(false, false, false, 0.5F), "no stamina mechanic, no bar");
		check(read(CLIENT.resolve("ui/hud_power/PowerHud.java")).contains("addElement(new StaminaMiniBar("), "the HUD must have the bar");
	}

	private static void verifyEmptyBar() throws IOException {
		String hud = squash(read(CLIENT.resolve("ui/hud_power/PowerHudControlsElement.java")));
		check(hud.contains(squash("if (hotbarUI.slots.isEmpty()) {\n\t\t\t\t\tcontinue;\n\t\t\t\t}\n\n\t\t\t\tClientKey hotbarKey = input.getKey();")),
				"a bar with nothing to draw takes no key, no hint and no room");
	}

	private static void verifyWheelKeeps() throws IOException {
		check(HotbarSelectionRules.keyReachesAbilities(false, false), "no wheel, every key as ever");
		check(HotbarSelectionRules.keyReachesAbilities(true, true), "the wheel's own use key reaches its ability");
		check(!HotbarSelectionRules.keyReachesAbilities(true, false), "any other key or click stays the wheel's");
		String input = read(CLIENT.resolve("input/InputHandler.java"));
		check(input.contains("inputState -> reachesAbilities && AbilityInputState.isInputActive"), "a key press must ask the rule");
		check(input.contains("return screen != null && !(screen instanceof AbilitySelectionWheel);"),
				"only the wheel leaves abilities active under a screen");
		String ability = read(ROOT.resolve("src/main/java/rotp/core/powersystem/ability/Ability.java"));
		check(ability.contains("InputHandler.inputsDisabled || InputHandler.screenHoldsAbilities()"), "an ability's input state must use it");
	}

	private static void verifySunsetClock() throws IOException {
		check(SunsetClock.ticksToSunset(0) == 12542, "sunset from the start of the day: " + SunsetClock.ticksToSunset(0));
		check(SunsetClock.ticksToSunset(-100L) == 12642, "a negative time of day counts too: " + SunsetClock.ticksToSunset(-100L));
		String hud = read(ROOT.resolve("src/main/java/rotp/core/impl/powers/vampirism/client/SunsetClockHud.java"));
		check(hud.contains("isHolding(Items.CLOCK)") && hud.contains("JojoDefinitions.isUndeadOrVampiric(player)"),
				"only a clock in the hand of someone the sun burns tells it: the clock of everyone else stays as it is");
		check(hud.contains("dimensionType().natural()"), "where there is no sun to set it says nothing");
	}

	private static void verifyLanguages() throws IOException {
		JsonObject english = language("en_us");
		for (String lang : LANGS) {
			JsonObject texts = language(lang);
			for (String key : NEW_KEYS) {
				check(texts.has(key) && !texts.get(key).getAsString().isBlank(), lang + " must hold " + key);
				String text = texts.get(key).getAsString();
				check(placeholders(text) == placeholders(english.get(key).getAsString()),
						lang + ": " + key + " must take as many arguments as the English: " + text);
				check(!REBINDABLE_KEY_NAME.matcher(text).find(), lang + ": " + key + " must name no key that can be rebound: " + text);
				if (!"en_us".equals(lang) && !"en_pt".equals(lang)) {
					check(!text.equals(english.get(key).getAsString()), lang + ": " + key + " is still the English");
				}
			}
			check(texts.has("death.attack.ultraviolet"), lang + " must hold the sun burn's death message");
		}
	}

	private static int placeholders(String text) {
		int count = 0;
		Matcher matcher = PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	private static JsonObject language(String lang) throws IOException {
		return JsonParser.parseString(read(ROOT.resolve("src/main/resources/assets/jojo_ripples/lang/" + lang + ".json"))).getAsJsonObject();
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path);
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
