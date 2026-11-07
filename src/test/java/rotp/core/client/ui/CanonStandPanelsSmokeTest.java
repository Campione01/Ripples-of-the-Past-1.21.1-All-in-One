package rotp.core.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonParseException;

import net.minecraft.resources.ResourceLocation;
import rotp.core.client.ui.StandStatsRenderer.CosmeticStandStats;
import rotp.core.client.ui.StandStatsRenderer.HexagonStandStat;
import rotp.core.powersystem.standpower.StandStats;

public final class CanonStandPanelsSmokeTest {
	private static final String D4C = "rotp_d4c:d4c";
	private static final String MANDOM = "rotp_mandom:mandom";
	private static final StandStats NUMBERS = new StandStats(0, 1, 1, 2, 0, 0);

	private CanonStandPanelsSmokeTest() {}

	public static void run() {
		bundledPanelsDriveTheLettersAndChart();
		absentRowsAndExplicitOverridesKeepTheirBehavior();
		malformedRowsAreRefused();
	}

	private static void bundledPanelsDriveTheLettersAndChart() {
		InputStream input = CanonStandPanelsSmokeTest.class.getResourceAsStream("/assets/jojo_ripples/stand_panel_ranks.json");
		check(input != null, "canonical Stand panel table must be bundled");
		try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
			check(CanonStandPanels.read(reader).size() == 74, "canonical table must contain 74 unique Stand IDs");
		}
		catch (IOException exception) {
			throw new AssertionError("could not read bundled canonical panels", exception);
		}

		assertPanel(D4C, "A A C A A A", 5, 5, 3, 5, 5, 5);
		assertPanel("rotp_echoes:echoes_act1", "E E B B C A", 1, 1, 4, 4, 3, 5);
		assertPanel("rotp_echoes:echoes_act2", "C D B B C A", 3, 2, 4, 4, 3, 5);
		assertPanel("rotp_echoes:echoes_act3", "B B C B C A", 4, 4, 3, 4, 3, 5);
		assertPanel("rotp_t:tusk", "E E E B E A", 1, 1, 1, 4, 1, 5);
		assertPanel("rotp_t:tusk_act_4", "A B C A B E", 5, 4, 3, 5, 4, 1);
		assertPanel("rotp_cm:cmoon", "\u2205 B ? ? ? ?", 0, 4, 0, 0, 0, 0);
		assertPanel("geraddon:ger_stand", "\u2205 \u2205 \u2205 \u2205 \u2205 \u2205", 0, 0, 0, 0, 0, 0);
		assertPanel("rotp_mih:made_in_heaven", "B \u221e C A C A", 4, 6, 3, 5, 3, 5);
		assertPanel("rotp_whitesnake:whitesnake", "? D ? A ? ?", 0, 2, 0, 5, 0, 0);
		assertPanel(MANDOM, "\u2205 A \u2205 E \u2205 C", 0, 5, 0, 1, 0, 3);
		check(NUMBERS.power() == 0 && NUMBERS.speed() == 1 && NUMBERS.rangeEffective() == 1 && NUMBERS.rangeMax() == 2
				&& NUMBERS.durability() == 0 && NUMBERS.precision() == 0, "cosmetic panel lookup must not change gameplay numbers");
	}

	private static void assertPanel(String id, String letters, float... chartValues) {
		CosmeticStandStats panel = CosmeticStandStats.getHandlerForStandId(ResourceLocation.parse(id));
		String[] expected = letters.split(" ");
		for (HexagonStandStat stat : HexagonStandStat.values()) {
			int index = stat.ordinal();
			float converted = panel.statConvertedValue(stat, null, NUMBERS, 0.75F);
			check(expected[index].equals(panel.statRankLetter(stat, null, converted)), id + " wrong canonical rank for " + stat);
			check(chartValues[index] == converted, id + " wrong canonical chart value for " + stat);
		}
	}

	private static void absentRowsAndExplicitOverridesKeepTheirBehavior() {
		for (String id : List.of("rotp_theworld:the_world", "rotp_echoes:echoes_act0", "speedking:speedking_stand",
				"rotp_november_rain:november_rain", "rotp_wou:wonder_of_you", "guanggun:guanggun", "rotp_mwp:debug_stand",
				"missing_addon:missing_stand")) {
			check(CanonStandPanels.find(ResourceLocation.parse(id)) == null
					&& CosmeticStandStats.getHandlerForStandId(ResourceLocation.parse(id)) == CosmeticStandStats.DEFAULT,
					id + " must retain its normal fallback, with no implicit canon row");
		}

		ResourceLocation id = ResourceLocation.parse(MANDOM);
		CosmeticStandStats original = StandStatsRenderer.OVERRIDE_STAT.get(id);
		CosmeticStandStats selectedForm = new CosmeticStandStats();
		CosmeticStandStats explicit = new CosmeticStandStats() {
			@Override
			public CosmeticStandStats getSubStandStats() {
				return selectedForm;
			}
		};
		try {
			StandStatsRenderer.overrideCosmeticStats(id, explicit);
			CosmeticStandStats handler = CosmeticStandStats.getHandlerForStandId(id);
			check(handler == explicit && handler.getSubStandStats() == selectedForm,
					"the registered cosmetic handler and its selected form must take precedence over canon");
		}
		finally {
			if (original == null) {
				StandStatsRenderer.OVERRIDE_STAT.remove(id);
			}
			else {
				StandStatsRenderer.overrideCosmeticStats(id, original);
			}
		}
	}

	private static void malformedRowsAreRefused() {
		reject("{\"test:stand\":[\"A\",\"A\",\"C\",\"A\",\"A\",\"Z\"]}", "Invalid rank Z");
		reject("{\"test:stand\":[\"A\"]}", "Expected six rank strings");
		reject("{\"test:stand\":[\"A\",1,\"C\",\"A\",\"A\",\"A\"]}", "Expected six rank strings");
		reject("{\"stand\":[\"A\",\"A\",\"C\",\"A\",\"A\",\"A\"]}", "Invalid canonical Stand id");
		String row = "\"test:stand\":[\"A\",\"A\",\"C\",\"A\",\"A\",\"A\"]";
		reject("{" + row + "," + row + "}", "Duplicate canonical Stand panel");
		reject("[]", "Invalid canonical Stand panel table");
	}

	private static void reject(String json, String reason) {
		try {
			CanonStandPanels.read(new StringReader(json));
			throw new AssertionError("canonical panel parser accepted malformed data: " + reason);
		}
		catch (JsonParseException exception) {
			check(exception.getMessage().contains(reason), "wrong panel validation error: " + exception.getMessage());
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
