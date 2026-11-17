package rotp.core.client.input.controlscheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Predicate;

import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.InputsByKeyModifier;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings;
import rotp.core.powersystem.ability.controls.InputMethod;

import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * The player's report: "几乎所有替身切换技能的时候都有一个空技能栏". While C was held the row drew a frame for every slot with
 * nothing to show, the wheel a sector, and the selection could rest on one (the first slot locked, the selected ability
 * gone with its nugget, or a slot only for container menus, which the old check counted as shown).
 */
public final class HotbarSelectionRulesSmokeTest {
	private static final AbilityControlsEntry BASE = new AbilityControlsEntry(null, "base");
	private static final AbilityControlsEntry VARIATION = new AbilityControlsEntry(null, "variation");
	private static final Predicate<AbilityControlsEntry> VARIATION_AVAILABLE = entry -> entry == VARIATION;
	private static final Predicate<AbilityControlsEntry> LOCKED = entry -> false;

	private HotbarSelectionRulesSmokeTest() {}

	public static void run() {
		verifySelectionKeepsToUsableSlots();
		verifyNumberKeysCountUsableSlots();
		verifyLockedVariationOnASeparateKey();
		verifySavedLayoutOfAnOldSlot();
		verifySourceWiring();
	}

	/** A layout saved while the blink had its own slot still loads: the name is dropped, the slot's place is the base's. */
	private static void verifySavedLayoutOfAnOldSlot() {
		check(ControlSchemeSettings.mergeOrder(List.of("inhale", "time_stop"), List.of("time_stop", "time_stop_blink", "inhale"))
						.equals(List.of("time_stop", "inhale")),
				"a saved layout that names a slot the template no longer has must load without it");
	}

	private static void verifySelectionKeepsToUsableSlots() {
		boolean[] firstLocked = { false, true, true };
		checkEquals(1, HotbarSelectionRules.keepOnUsableSlot(0, -1, firstLocked), "a locked first slot must not hold the selection");
		checkEquals(2, HotbarSelectionRules.keepOnUsableSlot(2, -1, firstLocked), "a usable selection must stay");
		boolean[] selectedGone = { true, false, true };
		checkEquals(2, HotbarSelectionRules.keepOnUsableSlot(1, -1, selectedGone), "the selection moves on to the next usable slot");
		checkEquals(0, HotbarSelectionRules.keepOnUsableSlot(2, -1, new boolean[] { true, false, false }), "the next usable slot wraps around");
		checkEquals(1, HotbarSelectionRules.keepOnUsableSlot(2, 1, new boolean[] { true, true, true }),
				"the selection comes back to the player's pick once it can be used again");
		checkEquals(0, HotbarSelectionRules.keepOnUsableSlot(0, 1, selectedGone), "a pick that cannot be used must not take it back");
		checkEquals(1, HotbarSelectionRules.keepOnUsableSlot(1, -1, new boolean[] { false, false }), "nothing to select leaves the index");
		checkEquals(0, HotbarSelectionRules.keepOnUsableSlot(0, -1, new boolean[0]), "an empty bar leaves the index");
		checkEquals(0, HotbarSelectionRules.keepOnUsableSlot(-1, -1, new boolean[] { true }), "no selection takes the first usable slot");
		checkEquals(0, HotbarSelectionRules.keepOnUsableSlot(9, 9, new boolean[] { true }), "a stale index is taken back");
	}

	private static void verifyNumberKeysCountUsableSlots() {
		boolean[] gaps = { false, true, false, true, true };
		checkEquals(1, HotbarSelectionRules.slotOfNumberKey(0, gaps), "the first key picks the first usable slot");
		checkEquals(4, HotbarSelectionRules.slotOfNumberKey(2, gaps), "the third key picks the third usable slot");
		checkEquals(-1, HotbarSelectionRules.slotOfNumberKey(3, gaps), "a key past the usable slots picks nothing");
		checkEquals(-1, HotbarSelectionRules.numberKeyOfSlot(0, gaps), "a slot that cannot be selected has no key");
		checkEquals(1, HotbarSelectionRules.numberKeyOfSlot(3, gaps), "the digit under a slot is the key that picks it");
		for (int key = 0; key < 3; key++) {
			checkEquals(key, HotbarSelectionRules.numberKeyOfSlot(HotbarSelectionRules.slotOfNumberKey(key, gaps), gaps),
					"a digit and its key must agree");
		}
		checkEquals(-1, HotbarSelectionRules.numberKeyOfSlot(7, gaps), "a slot outside the bar has no key");
	}

	/** Shift + LMB on Hierophant Green: locked, the key stays on the base; the grab key does not ask this rule. */
	private static void verifyLockedVariationOnASeparateKey() {
		InputsByKeyModifier marked = new InputsByKeyModifier();
		bind(marked, KeyModifier.NONE, InputMethod.HOLD, BASE);
		bind(marked, VariationKey.DEFAULT_MODIFIER, InputMethod.HOLD, VARIATION);
		marked.markLockedShiftUsesBase(InputMethod.HOLD);
		check(marked.getAll(VariationKey.DEFAULT_MODIFIER, InputMethod.HOLD, VARIATION_AVAILABLE).equals(List.of(VARIATION)),
				"an unlocked variation takes the variation key + its key");
		check(marked.getAll(VariationKey.DEFAULT_MODIFIER, InputMethod.HOLD, LOCKED).equals(List.of(BASE)),
				"a locked variation leaves the variation key + its key on the base");
		check(marked.getAll(KeyModifier.NONE, InputMethod.HOLD, VARIATION_AVAILABLE).equals(List.of(BASE)),
				"the plain key stays the base");

		InputsByKeyModifier grabKey = new InputsByKeyModifier();
		bind(grabKey, KeyModifier.NONE, InputMethod.CLICK, BASE);
		bind(grabKey, VariationKey.DEFAULT_MODIFIER, InputMethod.CLICK, VARIATION);
		check(grabKey.getAll(VariationKey.DEFAULT_MODIFIER, InputMethod.CLICK, LOCKED).equals(List.of(VARIATION)),
				"a grabbing Stand's key must not turn into its heavy punch");
	}

	private static void verifySourceWiring() {
		Path root = Path.of(System.getProperty("user.dir")).resolve("src/main/java/rotp/core/client");
		String hud = squash(read(root.resolve("ui/hud_power/PowerHudControlsElement.java")));
		check(!hud.contains(squash("if (hotbarUI.isSelectingAbility || !slotUI.abilities.isEmpty())"))
				&& hud.contains(squash("if (!slotUI.abilities.isEmpty()) {")),
				"the row must not draw a frame for a slot with nothing to show while selecting");
		check(hud.contains(squash("slotUI.numberKey = HotbarSelectionRules.numberKeyOfSlot(slot.index, selectable);")),
				"the digits under the row must be the number keys");

		String scheme = read(root.resolve("input/controlscheme/ClientControlScheme.java"));
		String show = between(scheme, "public AbilityConditionCheck showAbility()", "public AbilityConditionCheck selectableAbility()");
		check(show.contains("PowerHud.isInContainerScreen() ? TriState.TRUE : TriState.FALSE")
						&& show.contains("showAbilityInHUD(ability, forContainerMenu)"),
				"a slot only for container menus must not count as shown outside one");
		String selectable = between(scheme, "public AbilityConditionCheck selectableAbility()", "public static int numberKey(");
		check(selectable.contains("abilityEntry.isAvailable()"), "a locked slot drawn by the setting must not be selectable");

		String input = squash(read(root.resolve("input/InputHandler.java")));
		check(input.contains(squash("keepHotbarSelectionsOnUsableSlots();\n\t\tvanillaKeybinds.handleTick();")),
				"the selection must be kept on a usable slot every client tick");
		check(input.contains(squash("&& abilityHotbar.hasSelectableSlot()")), "C must not open a bar with nothing to select");
		check(input.contains(squash("ControlSchemeEditorInput.cycleSlot(hotbar.slotIndex, hotbar.selectableSlots(), backwards)")),
				"scrolling must only stop on what can be selected");
		check(input.contains(squash("HotbarSelectionRules.slotOfNumberKey(newIndex, hotbar.selectableSlots())")),
				"a number key must pick by what can be selected");

		String wheel = read(root.resolve("ui/AbilitySelectionWheel.java"));
		String shown = between(wheel, "private List<HotbarSlot> shownSlots()", "public void render(");
		check(shown.contains("slot.showAbility() != null"), "the wheel must give a sector only to a slot that draws something");
		check(wheel.contains("int n = sectors.size();") && !wheel.contains("int n = abilities.slots.size();"),
				"the wheel must divide itself among those slots");
		check(squash(wheel).contains(squash("abilities.slots.get(clickedIndex).selectableAbility() != null")),
				"a click on a locked sector must not select it");
	}

	private static void bind(InputsByKeyModifier binds, KeyModifier modifier, InputMethod inputMethod, AbilityControlsEntry entry) {
		binds.movesByModifier.computeIfAbsent(modifier, m -> new EnumMap<>(InputMethod.class)).put(inputMethod, List.of(entry));
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static String between(String source, String start, String end) {
		int from = source.indexOf(start);
		int to = source.indexOf(end, from + start.length());
		if (from < 0 || to < 0) {
			throw new AssertionError("failed to locate " + start + " .. " + end);
		}
		return source.substring(from, to);
	}

	private static String squash(String source) {
		return source.replaceAll("\\s+", "");
	}

	private static void checkEquals(int expected, int actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
