package rotp.core.init;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rotp.core.init.CreativeTabSections.Header;
import rotp.core.init.CreativeTabSections.Layout;
import rotp.core.init.CreativeTabSections.Section;

/**
 * The creative tab's layout on its 9-wide grid, and that every key this round added has no default: a key that shipped
 * with one would change what an existing key does (the owner's words on these keys in R772.1).
 */
public final class CreativeTabSectionsSmokeTest {
	private static final String BLANK = "-";

	private CreativeTabSectionsSmokeTest() {}

	public static void main(String[] args) throws IOException {
		namedSectionsStartOnTheirOwnRowUnderARowForTheName();
		anEmptySectionTakesNoRows();
		aGroupThatDoesNotFitGoesToTheNextRowAndOneThatDoesGetsAGap();
		aLongGroupRunsOverRows();
		theFirstVisibleRowCountsAsTheCreativeMenuDoes();
		everyNewKeyIsUnbound();
		System.out.println("Creative tab sections smoke test passed");
	}

	private static void namedSectionsStartOnTheirOwnRowUnderARowForTheName() {
		Layout<String, String> layout = CreativeTabSections.layout(List.of(
				new Section<>(null, List.of(List.of("a", "b"))),
				new Section<>("discs", List.of(List.of("d"))),
				new Section<>("clothes", List.of(List.of("c")))), BLANK);
		check(layout.headers().equals(List.of(new Header<>(1, "discs"), new Header<>(3, "clothes"))),
				"section name rows are " + layout.headers());
		check(layout.slots().get(0).equals("a") && layout.slots().get(1).equals("b"), "the items do not start the grid");
		for (int i = 2; i < 18; i++) {
			check(layout.slots().get(i).equals(BLANK), "slot " + i + " is not blank before the discs: " + layout.slots().get(i));
		}
		check(layout.slots().get(18).equals("d"), "the discs do not start right under their name: " + layout.slots());
		check(layout.slots().get(36).equals("c"), "the clothes do not start right under their name: " + layout.slots());
	}

	private static void anEmptySectionTakesNoRows() {
		Layout<String, String> layout = CreativeTabSections.layout(List.of(
				new Section<>(null, List.of(List.of("a"))),
				new Section<>("discs", List.of(List.of())),
				new Section<>("clothes", List.of(List.of("c")))), BLANK);
		check(layout.headers().equals(List.of(new Header<>(1, "clothes"))), "an empty section left a name: " + layout.headers());
	}

	private static void aGroupThatDoesNotFitGoesToTheNextRowAndOneThatDoesGetsAGap() {
		List<String> four = List.of("1", "2", "3", "4");
		List<String> six = List.of("5", "6", "7", "8", "9", "10");
		Layout<String, String> layout = CreativeTabSections.layout(List.of(
				new Section<>(null, List.of(four, List.of("x", "y"), six))), BLANK);
		check(layout.slots().subList(0, 4).equals(four), "the first group moved");
		check(layout.slots().get(4).equals(BLANK), "no gap between two groups on a row");
		check(layout.slots().subList(5, 7).equals(List.of("x", "y")), "the group that fits left the row: " + layout.slots());
		check(layout.slots().get(7).equals(BLANK) && layout.slots().get(8).equals(BLANK), "the row was not closed: " + layout.slots());
		check(layout.slots().subList(9, 15).equals(six), "the group that does not fit split over rows: " + layout.slots());
	}

	private static void aLongGroupRunsOverRows() {
		List<String> twelve = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			twelve.add("d" + i);
		}
		Layout<String, String> layout = CreativeTabSections.layout(List.of(new Section<>("discs", List.of(twelve))), BLANK);
		check(layout.slots().subList(9, 21).equals(twelve), "a long group has a hole in it: " + layout.slots());
	}

	private static void theFirstVisibleRowCountsAsTheCreativeMenuDoes() {
		check(CreativeTabSections.firstVisibleRow(0F, 90) == 0, "no scroll is not the first row");
		check(CreativeTabSections.firstVisibleRow(1F, 90) == 5, "full scroll of 10 rows must show row 5 at the top");
		check(CreativeTabSections.firstVisibleRow(0.5F, 91) == 3, "half scroll of 11 rows must round to row 3");
		check(CreativeTabSections.firstVisibleRow(1F, 9) == 0, "a short tab must not scroll");
	}

	private static void everyNewKeyIsUnbound() throws IOException {
		String source = Files.readString(Path.of("src/main/java/rotp/core/client/input/VanillaKeybinds.java"), StandardCharsets.UTF_8);
		List<String> names = List.of("stand_cancel_action", "stand_item_toss", "stand_item_swap_hands",
				"stand_toggle_break_blocks", "stand_toggle_pick_up_items", "stand_toggle_beyond_effective_range");
		for (String name : names) {
			check(source.contains("unboundKey(JojoMod.MOD_ID + \".key." + name + "\")"), name + " is not registered with no default");
		}
		Matcher helper = Pattern.compile("private static KeyMapping unboundKey\\(String name\\) \\{(.*?)\\n\\t\\}", Pattern.DOTALL).matcher(source);
		check(helper.find(), "unboundKey is gone");
		check(helper.group(1).contains("GLFW.GLFW_KEY_UNKNOWN") && !helper.group(1).contains("KeyModifier"),
				"unboundKey gives a key a default: " + helper.group(1));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
