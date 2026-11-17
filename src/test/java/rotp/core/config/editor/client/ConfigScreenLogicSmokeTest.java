package rotp.core.config.editor.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEditResult.Change;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEditorPackets.Operation;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.ConfigEntry.Restart;
import rotp.core.config.editor.ConfigEntry.Side;
import rotp.core.config.editor.client.ConfigEditSession.Answer;
import rotp.core.config.editor.client.ConfigStore.Access;
import rotp.core.config.editor.client.ConfigWidgetRules.Section;
import rotp.core.config.editor.client.ConfigWidgetRules.WidgetKind;

/**
 * What the settings screen does with the server's answers, against a stand-in for the network: who may write, that a
 * preview changes nothing, that a value counts only once accepted, and that there is one request at a time.
 */
public final class ConfigScreenLogicSmokeTest {
	private static final String RANGE = "Stand settings.Time Stop.timeStopRangeChunks";
	private static final String DAMAGE = "Stand settings.Time Stop.timeStopDamageMultiplier";

	private ConfigScreenLogicSmokeTest() {}

	public static void main(String[] args) {
		aPlayerWithoutTheRightSendsNothing();
		aPreviewAsksAndChangesNothing();
		aValueCountsOnlyOnceAccepted();
		aRefusalLeavesTheOldValueAndSaysSo();
		aForbiddenReplyTurnsThePageReadOnly();
		oneRequestAtATime();
		silenceReleasesTheQueueAndAsksAgain();
		noWorldShowsTheFileReadOnly();
		aServerWithoutTheChannelSaysSo();
		theRightWidgetForEachKind();
		numbersSnapAndParseWithinTheirBounds();
		labelsLoseTheirValuePlaceholder();
		everyKnownKeyHasItsSectionAndAnUnknownOneStillShows();
		deadSwitchesAreNotShown();
		aPresetIsNamedCurrentOnlyByItsValues();
		anAddonGetsABlockAndNeverTheCoresRules();
		addonBlocksSortByNameAndKeepTheirGroupsInFileOrder();
		addonTitlesReadAsNames();
		hiddenAddonValuesAreNotShownNorResetByResetAll();
		aStandsRowsAndItsAddonsGoToTheStandsPageOnly();
		aStandsBlockHasItsGroupsAndItsAddonsSettings();
		aStandsShareSlidesInFivesWithItsSign();
		resetAllResetsOnlyItsOwnPage();
		aListWithOfferedItemsIsAChecklistAndOneWithoutStaysAList();
		aChecklistKeepsItsOrderAndFindsByNameOrId();
		aChecklistWritesItsTicksInTheServersOrder();
		aColorIsAnRgbNumberNamedSoAndReadsAsHex();
		System.out.println("Config screen logic smoke test passed");
	}

	private static ConfigEntry integer(String key, int value, double min, double max) {
		return new ConfigEntry(key, rotp.core.config.editor.AddonConfigKeys.group(key), Side.CLIENT, Kind.INTEGER, null,
				new JsonPrimitive(value), new JsonPrimitive(value), new Bounds(min, max), null, List.of(), Restart.NONE, false, null);
	}

	private static void aColorIsAnRgbNumberNamedSoAndReadsAsHex() {
		// Time Stop Countdown's own value, as the add-on defines it: no code of its own for the screen
		ConfigEntry text = integer("rotp_time_stop_number/timestop_bar.textColor", 0xFFFFFF, 0, 0xFFFFFF);
		check(ConfigWidgetRules.isColor(text) && ConfigWidgetRules.widgetKind(text) == WidgetKind.COLOR, "an RGB range named colour is a colour");
		check(ConfigWidgetRules.isColor(integer("rotp_x/a.auraColour", 0, 0, 0xFFFFFF)), "the British spelling too");
		check(!ConfigWidgetRules.isColor(integer("rotp_x/a.textColor", 7, 0, 255)) && !ConfigWidgetRules.isColor(integer("rotp_x/a.count", 7, 0, 0xFFFFFF)),
				"a colour needs both the RGB range and the name");
		check(ConfigWidgetRules.valueNumber(text, new JsonPrimitive(16777215)).equals("#FFFFFF")
				&& ConfigWidgetRules.formatColor(new JsonPrimitive(255)).equals("#0000FF"), "a colour reads as #RRGGBB, never 16777215");
		check(ConfigWidgetRules.parseColor("#ff8800").getAsInt() == 0xFF8800 && ConfigWidgetRules.parseColor(" FF8800 ").getAsInt() == 0xFF8800
				&& ConfigWidgetRules.parseColor("#ff880") == null && ConfigWidgetRules.parseColor("#gg0000") == null
				&& ConfigWidgetRules.parseColor("16777215") == null, "#RRGGBB or RRGGBB is taken, nothing else");
	}

	private static ConfigEntry checklist(JsonElement value, JsonElement defaultValue) {
		List<rotp.core.config.editor.ConfigChoices.Choice> offered = List.of(
				rotp.core.config.editor.ConfigChoices.Choice.of("minecraft:overworld", "Overworld"),
				rotp.core.config.editor.ConfigChoices.Choice.of("minecraft:the_nether", "The Nether"),
				rotp.core.config.editor.ConfigChoices.Choice.of("minecraft:the_end", "The End"),
				rotp.core.config.editor.ConfigChoices.Choice.of("twilightforest:twilight_forest", "Twilight Forest"));
		return new ConfigEntry("rotp_d4c/D4C.parallelDimensions", "rotp_d4c/D4C", Side.SERVER, Kind.LIST, Kind.STRING, defaultValue,
				value, null, null, List.of(), Restart.WORLD, false, null, offered);
	}

	private static JsonArray ids(String... ids) {
		JsonArray array = new JsonArray();
		for (String id : ids) {
			array.add(id);
		}
		return array;
	}

	private static void aListWithOfferedItemsIsAChecklistAndOneWithoutStaysAList() {
		ConfigEntry ticks = checklist(ids("minecraft:overworld"), ids("minecraft:overworld"));
		ConfigEntry plain = new ConfigEntry("rotp_d4c/D4C.names", "rotp_d4c/D4C", Side.SERVER, Kind.LIST, Kind.STRING, ids("a"), ids("a"),
				null, null, List.of(), Restart.NONE, false, null);
		check(ConfigWidgetRules.widgetKind(ticks) == WidgetKind.CHECKLIST && ConfigWidgetRules.widgetKind(plain) == WidgetKind.LIST,
				"a list the server offers items for is tick boxes; one without stays the plain list");
		check(ConfigChecklist.tickedCount(ticks) == 1 && ConfigChecklist.offeredCount(ticks) == 4, "its row reads 1 of 4 ticked");
	}

	private static void aChecklistKeepsItsOrderAndFindsByNameOrId() {
		// the End ticked, and a world of a mod no longer there
		ConfigChecklist list = new ConfigChecklist(checklist(ids("minecraft:the_end", "gone:world"), ids("minecraft:overworld")));
		List<String> order = list.items().stream().map(ConfigChecklist.Item::id).toList();
		check(order.equals(List.of("gone:world", "minecraft:the_end", "minecraft:overworld", "minecraft:the_nether", "twilightforest:twilight_forest")),
				"what the server no longer offers first, then the ticked, then the rest in the server's order: " + order);
		check(!list.items().getFirst().offered() && list.items().get(1).offered(), "the gone item is marked so");
		list.toggle("minecraft:overworld");
		check(list.items().stream().map(ConfigChecklist.Item::id).toList().equals(order), "a tick moves no row");
		check(list.shown("twil", ConfigChecklist.Item::name).size() == 1 && list.shown("THE_NETHER", ConfigChecklist.Item::name).size() == 1
				&& list.shown("Nether", ConfigChecklist.Item::name).size() == 1 && list.shown("", ConfigChecklist.Item::name).size() == 5,
				"the filter finds by shown name or by id, any case");
		java.util.function.Function<ConfigChecklist.Item, String> german = item -> item.id().equals("minecraft:the_nether") ? "Unterwelt" : item.name();
		check(list.shown("unterwelt", german).size() == 1 && list.shown("The Nether", german).isEmpty(),
				"and by the name shown, not the server's");
	}

	private static void aChecklistWritesItsTicksInTheServersOrder() {
		ConfigEntry entry = checklist(ids("minecraft:the_end", "gone:world"), ids("minecraft:overworld"));
		ConfigChecklist list = new ConfigChecklist(entry);
		check(list.sameAs(ids("gone:world", "minecraft:the_end")), "a stored list in another order is no change");
		list.toggle("minecraft:overworld");
		check(list.value().equals(ids("minecraft:overworld", "minecraft:the_end", "gone:world")) && !list.sameAs(entry.value()),
				"ticked items go out in the server's order, a gone one after: " + list.value());
		list.toggle("gone:world");
		check(list.value().equals(ids("minecraft:overworld", "minecraft:the_end")) && list.tickedCount() == 2,
				"the gone item can be unticked: " + list.value());
		list.toggle("nobody:offers_this");
		check(list.value().equals(ids("minecraft:overworld", "minecraft:the_end")), "an item not on the card cannot be ticked");
		list.setTo(entry.defaultValue());
		check(list.value().equals(ids("minecraft:overworld")), "Reset to default ticks the default alone: " + list.value());
	}

	private static void aPlayerWithoutTheRightSendsNothing() {
		FakeServer server = new FakeServer();
		ConfigEditSession session = opened(server, false);
		check(session.access() == Access.READ_ONLY, "a player the server says cannot edit must see the page read-only");
		int sent = server.sent.size();
		check(!session.set(RANGE, new JsonPrimitive(4)), "a read-only page must refuse a set");
		check(!session.reset(RANGE), "a read-only page must refuse a reset");
		check(!session.resetAll(null), "a read-only page must refuse a reset of the tab");
		check(!session.previewPreset("pvp", result -> {}), "a read-only page must not ask for a preview: the server would refuse it");
		check(!session.applyPreset("pvp", result -> {}), "a read-only page must refuse a preset");
		check(!session.resetPreset("pvp", null), "a read-only page must refuse a preset's reset");
		check(server.sent.size() == sent, "a read-only page sent a write: " + server.sent);
		check(session.pending(RANGE) == null, "a refused edit must leave nothing pending");
		check(session.entry(RANGE).value().getAsInt() == 12, "a read-only page must still show the value");
	}

	private static void aPreviewAsksAndChangesNothing() {
		FakeServer server = new FakeServer();
		ConfigEditSession session = opened(server, true);
		List<ConfigEditResult> seen = new ArrayList<>();
		check(session.previewPreset("pvp", seen::add), "an editor may preview");
		check(server.last().operation == Operation.PREVIEW_PRESET, "the preview must be sent as a preview, not " + server.last().operation);
		server.answer(session, true, ConfigEditResult.ok(List.of(change(RANGE, 12, 4))), List.of());
		check(seen.size() == 1 && seen.getFirst().changes().size() == 1, "the preview's diff must reach the dialog");
		check(session.entry(RANGE).value().getAsInt() == 12, "a preview changed the shown value");
		check(session.pending(RANGE) == null, "a preview left a value pending");
		check(server.sent.stream().noneMatch(request -> request.operation == Operation.APPLY_PRESET), "a preview applied the preset");
		check(session.applyPreset("pvp", seen::add), "the confirm applies");
		check(server.last().operation == Operation.APPLY_PRESET, "the confirm must apply");
		server.answer(session, true, ConfigEditResult.ok(List.of(change(RANGE, 12, 4))), List.of());
		check(session.entry(RANGE).value().getAsInt() == 4, "an applied preset's accepted change must show");
	}

	private static void aValueCountsOnlyOnceAccepted() {
		FakeServer server = new FakeServer();
		ConfigEditSession session = opened(server, true);
		check(session.set(RANGE, new JsonPrimitive(4)), "an editor may set");
		check(session.entry(RANGE).value().getAsInt() == 12, "the widget's value moved before the server accepted it");
		check(session.pending(RANGE) != null && session.pending(RANGE).getAsInt() == 4, "the request must show as pending");
		check(session.busy(), "the store must say a request is out");
		server.answer(session, true, ConfigEditResult.ok(List.of(change(RANGE, 12, 4))), List.of());
		check(session.entry(RANGE).value().getAsInt() == 4 && session.pending(RANGE) == null && !session.busy(),
				"the accepted value must show and nothing stay pending");
		ConfigStore.Notice notice = session.takeNotice();
		check(notice != null && notice.severity() == ConfigStore.Severity.OK, "an accepted edit must be reported");
	}

	private static void aRefusalLeavesTheOldValueAndSaysSo() {
		FakeServer server = new FakeServer();
		ConfigEditSession session = opened(server, true);
		session.set(RANGE, new JsonPrimitive(-1));
		server.answer(session, true, ConfigEditResult.error(Status.INVALID_VALUE, null), List.of());
		check(session.entry(RANGE).value().getAsInt() == 12, "a refused value must not show");
		check(session.pending(RANGE) == null, "a refused value must not stay pending");
		ConfigStore.Notice notice = session.takeNotice();
		check(notice != null && notice.severity() == ConfigStore.Severity.ERROR && notice.textKey().endsWith("status.invalid_value"),
				"a refusal must show, and say why: " + notice);
		check(RANGE.equals(notice.flashKey()), "the refusal must name the row it is about");
		check(session.access() == Access.EDITABLE, "a refused value does not take away the right to edit");
	}

	private static void aForbiddenReplyTurnsThePageReadOnly() {
		FakeServer server = new FakeServer();
		ConfigEditSession session = opened(server, true);
		session.set(RANGE, new JsonPrimitive(4));
		session.set(DAMAGE, new JsonPrimitive(0.5));
		server.answer(session, false, ConfigEditResult.error(Status.FORBIDDEN, null), List.of());
		check(session.access() == Access.READ_ONLY, "an operator who lost the right must see the page turn read-only");
		check(session.pending(DAMAGE) == null, "what waited behind the refused request must not stay pending");
		check(!server.wasSent(DAMAGE), "what waited behind the refused request must not be sent");
	}

	private static void oneRequestAtATime() {
		FakeServer server = new FakeServer();
		ConfigEditSession session = opened(server, true);
		int before = server.sent.size();
		session.set(RANGE, new JsonPrimitive(4));
		session.set(DAMAGE, new JsonPrimitive(0.5));
		check(server.sent.size() == before + 1, "a second request went out before the first was answered");
		session.tick(100);
		check(server.sent.size() == before + 1, "a tick sent the second request before the first was answered");
		server.answer(session, true, ConfigEditResult.ok(List.of(change(RANGE, 12, 4))), List.of());
		check(server.sent.size() == before + 2 && server.last().key.equals(DAMAGE), "the second request must go out after the first's answer");
	}

	private static void silenceReleasesTheQueueAndAsksAgain() {
		FakeServer server = new FakeServer();
		ConfigEditSession session = opened(server, true);
		session.set(RANGE, new JsonPrimitive(4));
		session.tick(ConfigEditSession.ANSWER_TIMEOUT_MILLIS + 1);
		check(session.pending(RANGE) == null, "a request nobody answered must not stay pending");
		check(session.entry(RANGE).value().getAsInt() == 12, "a request nobody answered must not show as accepted");
		check(server.last().operation == Operation.READ, "after a silence the screen must ask what the server holds");
		check(session.takeNotice() != null, "a silence must show");
	}

	private static void noWorldShowsTheFileReadOnly() {
		FakeServer server = new FakeServer();
		server.world = false;
		ConfigEditSession session = new ConfigEditSession(server, ConfigScreenLogicSmokeTest::catalog);
		session.open(0);
		check(session.access() == Access.NO_WORLD, "with no world the page must say so");
		check(session.entry(RANGE) != null, "with no world the local values must still show");
		check(!session.set(RANGE, new JsonPrimitive(4)) && server.sent.isEmpty(), "with no world nothing may be sent");
	}

	private static void aServerWithoutTheChannelSaysSo() {
		FakeServer server = new FakeServer();
		server.channel = false;
		ConfigEditSession session = new ConfigEditSession(server, ConfigScreenLogicSmokeTest::catalog);
		session.open(0);
		check(session.access() == Access.UNSUPPORTED && session.entries().isEmpty(), "a server without the editor must not show values it did not give");
	}

	private static void theRightWidgetForEachKind() {
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.BOOLEAN, new JsonPrimitive(true), null, List.of())) == WidgetKind.TOGGLE, "a boolean is a toggle");
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.ENUM, new JsonPrimitive("A"), null, List.of("A", "B"))) == WidgetKind.CYCLE, "an enum cycles");
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.STRING, new JsonPrimitive("A"), null, List.of("A", "B"))) == WidgetKind.CYCLE, "a string with choices cycles");
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.STRING, new JsonPrimitive("A"), null, List.of())) == WidgetKind.TEXT, "a free string is typed");
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.LIST, new JsonArray(), null, List.of())) == WidgetKind.LIST, "a list opens its editor");
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.DECIMAL, new JsonPrimitive(1.0), new Bounds(0, 1), List.of())) == WidgetKind.SLIDER, "a small decimal range slides");
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.DECIMAL, new JsonPrimitive(1.0), new Bounds(0, 5000), List.of())) == WidgetKind.NUMBER, "a multiplier up to 5000 is typed");
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.INTEGER, new JsonPrimitive(12), new Bounds(0, Integer.MAX_VALUE), List.of())) == WidgetKind.NUMBER, "an unbounded integer is typed");
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.INTEGER, new JsonPrimitive(1), new Bounds(-1, 100), List.of())) == WidgetKind.SLIDER, "a small integer range slides");
		check(ConfigWidgetRules.widgetKind(entry("a", Kind.DECIMAL, new JsonPrimitive(1.0), null, List.of())) == WidgetKind.NUMBER, "a number with no bounds is typed");
	}

	private static void numbersSnapAndParseWithinTheirBounds() {
		ConfigEntry charge = entry("timeStopChargeSeconds", Kind.DECIMAL, new JsonPrimitive(1.5), new Bounds(0.5, 5.0), List.of());
		check(ConfigWidgetRules.sliderValue(charge, 0).getAsDouble() == 0.5, "a slider's left end is the minimum");
		check(ConfigWidgetRules.sliderValue(charge, 1).getAsDouble() == 5.0, "a slider's right end is the maximum");
		check(Math.abs(ConfigWidgetRules.sliderFraction(charge, new JsonPrimitive(2.75)) - 0.5) < 1e-9, "a value sits at its place on the slider");
		check(ConfigWidgetRules.sliderText(charge, new JsonPrimitive(1.5)).equals("1.5"), "a decimal reads short");
		check(ConfigWidgetRules.sliderValue(charge, 0.12).getAsDouble() == 1.05, "a rule's slider lands on round numbers");
		ConfigEntry percent = new ConfigEntry("standTransparency", "standDisplay", Side.CLIENT, Kind.DECIMAL, null, new JsonPrimitive(25F),
				new JsonPrimitive(25F), new Bounds(0, 100), null, List.of(), Restart.NONE, false, null);
		check(ConfigWidgetRules.sliderText(percent, new JsonPrimitive(25F)).equals("25"), "a percentage reads without decimals");
		check(ConfigWidgetRules.parseScalar(Kind.DECIMAL, new Bounds(0.5, 5.0), "0.25") == null, "a typed value under the minimum must not pass");
		check(ConfigWidgetRules.parseScalar(Kind.DECIMAL, new Bounds(0.5, 5.0), "5.5") == null, "a typed value over the maximum must not pass");
		check(ConfigWidgetRules.parseScalar(Kind.INTEGER, new Bounds(0, 10), "1.5") == null, "a fraction must not pass as an integer");
		check(ConfigWidgetRules.parseScalar(Kind.INTEGER, new Bounds(0, 10), "abc") == null, "words must not pass as a number");
		check(ConfigWidgetRules.parseScalar(Kind.INTEGER, new Bounds(0, 10), " 7 ").getAsInt() == 7, "a clean integer passes");
		check(ConfigWidgetRules.parseScalar(Kind.STRING, null, "  ") == null, "a blank text must not pass");
		check(ConfigWidgetRules.formatNumber(Kind.DECIMAL, new JsonPrimitive(1.0)).equals("1.0"), "a decimal keeps one decimal");
		check(ConfigWidgetRules.formatNumber(Kind.INTEGER, new JsonPrimitive(48000)).equals("48000"), "an integer keeps its digits");
	}

	private static void labelsLoseTheirValuePlaceholder() {
		check(ConfigWidgetRules.labelWithoutValue("Aim assist: %s").equals("Aim assist"), "an English label with a value");
		check(ConfigWidgetRules.labelWithoutValue("Own stand transparency: %s%%").equals("Own stand transparency"), "a percentage label");
		check(ConfigWidgetRules.labelWithoutValue("瞄准辅助：%s").equals("瞄准辅助"), "a full-width colon");
		check(ConfigWidgetRules.labelWithoutValue("Stand side").equals("Stand side"), "a plain label stays");
		check(ConfigWidgetRules.fallbackName("Stand settings.Time Stop.timeStopChunkRange").equals("Time Stop Chunk Range"), "a key with no text still reads");
	}

	private static void everyKnownKeyHasItsSectionAndAnUnknownOneStillShows() {
		List<ConfigEntry> entries = new ArrayList<>(catalog());
		entries.add(entry("Stand settings.someFutureKey", Kind.BOOLEAN, new JsonPrimitive(true), null, List.of()));
		List<Section> sections = ConfigWidgetRules.sections(Side.SERVER, entries);
		check(sections.stream().anyMatch(section -> section.id().equals("time_stop")
				&& section.entries().stream().anyMatch(row -> row.key().equals(RANGE))), "a known key has its section");
		check(sections.getLast().id().equals(ConfigWidgetRules.OTHER_SECTION)
				&& sections.getLast().entries().getFirst().key().equals("Stand settings.someFutureKey"),
				"a key added later must appear without code");
		long rows = sections.stream().mapToLong(section -> section.entries().size()).sum();
		check(rows == entries.size(), "every entry appears exactly once: " + rows + " of " + entries.size());
	}

	private static void deadSwitchesAreNotShown() {
		ConfigEntry dead = new ConfigEntry("poseOnLmbRmb", "controls", Side.CLIENT, Kind.BOOLEAN, null, new JsonPrimitive(false),
				new JsonPrimitive(false), null, null, List.of(), Restart.NONE, false, null);
		ConfigEntry live = new ConfigEntry("standOutline", "standDisplay", Side.CLIENT, Kind.BOOLEAN, null, new JsonPrimitive(true),
				new JsonPrimitive(true), null, null, List.of(), Restart.NONE, false, null);
		List<Section> sections = ConfigWidgetRules.sections(Side.CLIENT, List.of(dead, live));
		check(sections.size() == 1 && sections.getFirst().entries().size() == 1 && sections.getFirst().entries().getFirst() == live,
				"a switch nothing reads must not be offered");
	}

	private static void aPresetIsNamedCurrentOnlyByItsValues() {
		List<ConfigEntry> entries = catalog();
		check(ConfigWidgetRules.holdsValues(Map.of(RANGE, new JsonPrimitive(12)), entries), "the values match, so it is the current one");
		check(!ConfigWidgetRules.holdsValues(Map.of(RANGE, new JsonPrimitive(4)), entries), "another value, so it is not");
		check(!ConfigWidgetRules.holdsValues(Map.of("no.such.key", new JsonPrimitive(4)), entries), "an unknown key cannot be held");
		check(!ConfigWidgetRules.holdsValues(Map.of(), entries), "an empty preset is nobody's current");
	}

	private static void anAddonGetsABlockAndNeverTheCoresRules() {
		List<ConfigEntry> entries = new ArrayList<>(catalog());
		ConfigEntry power = addon("rotp_x/Stand settings.standPowerMultiplier", Kind.DECIMAL, new JsonPrimitive(1.0), new Bounds(0, 5000));
		ConfigEntry blood = addon("rotp_x/maxBloodMultiplier", Kind.LIST, new JsonArray(), null);
		ConfigEntry banned = addon("rotp_x/bannedStands", Kind.LIST, new JsonArray(), null);
		ConfigEntry mode = new ConfigEntry("rotp_x/accelerationMode", "rotp_x/", Side.SERVER, Kind.ENUM, null, new JsonPrimitive("TICK_RATE"),
				new JsonPrimitive("TICK_RATE"), null, null, List.of("TICK_RATE", "SPEED"), Restart.NONE, false, "rotp_x.configuration.accelerationMode");
		entries.addAll(List.of(power, blood, banned, mode));
		List<Section> sections = ConfigWidgetRules.sections(Side.SERVER, entries);
		check(sections.stream().flatMap(section -> section.entries().stream()).noneMatch(ConfigWidgetRules::isAddon),
				"an add-on's value must never land in a core section or in Other: " + sections);
		check(sections.stream().noneMatch(section -> section.id().equals(ConfigWidgetRules.OTHER_SECTION)), "no add-on in Other");
		List<ConfigWidgetRules.Block> blocks = ConfigWidgetRules.blocks(entries, mod -> mod);
		check(blocks.size() == 1 && blocks.getFirst().keys().size() == 4, "the add-on's values must be its block: " + blocks);
		check(!ConfigWidgetRules.isByDifficulty(blood) && !ConfigWidgetRules.listItemsAreIds(banned),
				"a leaf named as a core one must not take the core's rule");
		check(ConfigWidgetRules.optionKey(mode, "TICK_RATE").equals("rotp_x.configuration.accelerationMode.tick_rate"),
				"an add-on's choice has its own text: " + ConfigWidgetRules.optionKey(mode, "TICK_RATE"));
		check(ConfigWidgetRules.fallbackChoice("TICK_RATE").equals("Tick rate"), "a choice without text still reads");
		check(ConfigWidgetRules.leaf("rotp_x/top").equals("top") && ConfigWidgetRules.fallbackName("rotp_x/top").equals("Top"),
				"a top-level add-on key reads by its leaf");
		check(ConfigWidgetRules.fallbackName("rotp_x/King Crimson Time Erase Config").equals("King Crimson Time Erase Config"),
				"a name with spaces keeps one: " + ConfigWidgetRules.fallbackName("rotp_x/King Crimson Time Erase Config"));
		check(ConfigWidgetRules.fallbackName("maxHP").equals("Max HP"), "an acronym stays whole: " + ConfigWidgetRules.fallbackName("maxHP"));
		ConfigEntry wide = new ConfigEntry("rotp_x/wide", "rotp_x/", Side.CLIENT, Kind.DECIMAL, null, new JsonPrimitive(1.0),
				new JsonPrimitive(1.0), new Bounds(0, 5000), null, List.of(), Restart.NONE, false, null);
		check(ConfigWidgetRules.widgetKind(wide) == WidgetKind.NUMBER, "an add-on's wide client range is typed, not a slider");
	}

	private static void addonBlocksSortByNameAndKeepTheirGroupsInFileOrder() {
		// the catalog's order and the ids' both put rotp_a first; only the names put it last
		List<ConfigEntry> entries = List.of(
				addon("rotp_a/only.one", Kind.BOOLEAN, new JsonPrimitive(true), null),
				addon("rotp_z/b.one", Kind.BOOLEAN, new JsonPrimitive(true), null),
				addon("rotp_z/a.two", Kind.BOOLEAN, new JsonPrimitive(true), null),
				addon("rotp_z/top", Kind.BOOLEAN, new JsonPrimitive(true), null));
		Map<String, String> names = Map.of("rotp_z", "Alpha", "rotp_a", "Zulu");
		List<ConfigWidgetRules.Block> blocks = ConfigWidgetRules.blocks(entries, names::get);
		check(blocks.size() == 2 && blocks.getFirst().modId().equals("rotp_z"), "blocks sort by the name shown, not the id");
		List<String> groups = blocks.getFirst().groups().stream().map(ConfigWidgetRules.Group::path).toList();
		check(groups.equals(List.of("", "b", "a")), "the top of the file first, then the file's own order: " + groups);
	}

	private static void addonTitlesReadAsNames() {
		Map<String, String> titles = Map.ofEntries(Map.entry("RotP King Crimson Configuration", "King Crimson"),
				Map.entry("RotP - Lovers Configuration", "Lovers"), Map.entry("RotP: Sex Pistols Configuration", "Sex Pistols"),
				Map.entry("The Grateful Dead", "The Grateful Dead"), Map.entry("NextAlbum", "NextAlbum"),
				Map.entry("Configuración de RotP Made in Heaven", "Made in Heaven"), Map.entry("Настройки RotP King Crimson", "King Crimson"),
				Map.entry("Настройки бутылочки с кровью", "Бутылочки с кровью"), Map.entry("RotP：性感手枪配置", "性感手枪"),
				Map.entry("天堂製造設定", "天堂製造"), Map.entry("暗藍之月之設", "暗藍之月"), Map.entry("Tournament 附属配置", "Tournament"),
				Map.entry("Ripples of the Past Reworked 配置", "Ripples of the Past Reworked"), Map.entry("Configuration", "Configuration"));
		titles.forEach((title, name) -> check(ConfigWidgetRules.blockName(title).equals(name),
				title + " must read " + name + ", not " + ConfigWidgetRules.blockName(title)));
		check(ConfigWidgetRules.blockNameFromDisplayName("RotP Spin").equals("Spin")
				&& ConfigWidgetRules.blockNameFromDisplayName("Blood Bottle RotP Addon").equals("Blood Bottle"), "a display name reads as a name too");
	}

	private static void hiddenAddonValuesAreNotShownNorResetByResetAll() {
		String hidden = "rotp_kingcrimson/King Crimson Time Erase Config.timeEraseDynamicCooldown";
		ConfigEntry dead = new ConfigEntry(hidden, "rotp_kingcrimson/King Crimson Time Erase Config", Side.SERVER, Kind.BOOLEAN, null,
				new JsonPrimitive(true), new JsonPrimitive(false), null, null, List.of(), Restart.NONE, false, null);
		check(!ConfigWidgetRules.isShown(dead) && ConfigWidgetRules.blocks(List.of(dead), mod -> mod).isEmpty(), "a dead add-on switch must not show");
		FakeServer server = new FakeServer();
		ConfigEditSession session = new ConfigEditSession(server, List::of);
		session.open(0);
		server.answer(session, true, ConfigEditResult.ok(List.of()), List.of(dead));
		check(!session.resetAll(null) && server.sent.size() == 1, "Reset All must not send a reset for what it never listed");
	}

	private static ConfigEntry stand(String stand, String field, Kind kind, JsonElement value, @Nullable Bounds bounds) {
		return new ConfigEntry("@" + stand + "/" + field, "@" + stand, Side.SERVER, kind, null, value, value, bounds, null,
				field.equals("miningTier") ? List.of("by_strength", "none") : List.of(), Restart.NONE, false,
				"jojo_ripples.config.stand." + field);
	}

	private static List<ConfigEntry> standsAndAddons() {
		List<ConfigEntry> entries = new ArrayList<>(catalog());
		entries.add(stand("rotp_kc:king_crimson", "obtainable", Kind.BOOLEAN, new JsonPrimitive(true), null));
		entries.add(stand("rotp_kc:king_crimson", "power", Kind.DECIMAL, new JsonPrimitive(16.0), new Bounds(0, 1000)));
		entries.add(stand("rotp_kc:king_crimson", "cooldowns", Kind.INTEGER, new JsonPrimitive(100), new Bounds(0, 500)));
		entries.add(stand("rotp_kc:king_crimson", "miningTier", Kind.STRING, new JsonPrimitive("by_strength"), null));
		entries.add(stand("rotp_echoes:echoes_act1", "speed", Kind.DECIMAL, new JsonPrimitive(8.0), new Bounds(0, 1000)));
		entries.add(stand("rotp_echoes:echoes_act2", "speed", Kind.DECIMAL, new JsonPrimitive(9.0), new Bounds(0, 1000)));
		entries.add(stand("jojo_ripples:star_platinum", "special.timeStopSeconds", Kind.DECIMAL, new JsonPrimitive(5.0), new Bounds(0.25, 120)));
		entries.add(addon("rotp_kc/King Crimson Time Erase Config.eraseTicks", Kind.INTEGER, new JsonPrimitive(100), new Bounds(0, 1000)));
		entries.add(addon("rotp_echoes/actSwitchCooldown", Kind.INTEGER, new JsonPrimitive(20), new Bounds(0, 100)));
		entries.add(addon("rotp_bb/bottleSize", Kind.INTEGER, new JsonPrimitive(3), new Bounds(0, 10)));
		entries.add(new ConfigEntry("rotp_kc/client.voice", "rotp_kc/client", Side.CLIENT, Kind.BOOLEAN, null, new JsonPrimitive(true),
				new JsonPrimitive(true), null, null, List.of(), Restart.NONE, false, null));
		return entries;
	}

	private static void aStandsRowsAndItsAddonsGoToTheStandsPageOnly() {
		List<ConfigEntry> entries = standsAndAddons();
		java.util.Set<String> standMods = ConfigWidgetRules.standNamespaces(entries);
		check(standMods.equals(java.util.Set.of("rotp_kc", "rotp_echoes", "jojo_ripples")), "the mods of the listed Stands: " + standMods);
		List<Section> sections = ConfigWidgetRules.sections(Side.SERVER, entries);
		check(sections.stream().flatMap(section -> section.entries().stream()).noneMatch(ConfigWidgetRules::isStand),
				"a Stand's row is never in a core section or in Other");
		List<ConfigEntry> gameplay = entries.stream().filter(entry -> !ConfigWidgetRules.isOnStandsPage(entry, standMods)).toList();
		List<ConfigWidgetRules.Block> blocks = ConfigWidgetRules.blocks(gameplay, mod -> mod);
		check(blocks.size() == 2 && blocks.stream().map(ConfigWidgetRules.Block::modId).toList().containsAll(List.of("rotp_bb", "rotp_kc")),
				"an add-on without Stands stays on Gameplay; one with Stands leaves only its client values: " + blocks);
		check(blocks.stream().filter(block -> block.modId().equals("rotp_kc")).allMatch(block -> block.keys().equals(List.of("rotp_kc/client.voice"))),
				"an add-on's CLIENT value is not the server's to move");
		check(!ConfigWidgetRules.isAddon(entries.stream().filter(ConfigWidgetRules::isStand).findFirst().orElseThrow()),
				"a Stand's key is no add-on's");
	}

	private static void aStandsBlockHasItsGroupsAndItsAddonsSettings() {
		List<ConfigWidgetRules.StandBlock> stands = ConfigWidgetRules.standBlocks(standsAndAddons());
		ConfigWidgetRules.StandBlock kc = stands.stream().filter(block -> block.standId().toString().equals("rotp_kc:king_crimson")).findFirst().orElseThrow();
		check(kc.groups().stream().map(ConfigWidgetRules.Group::path).toList().equals(List.of("obtaining", "stats", "shares", "blocks")),
				"a Stand's rows in the page's groups and order: " + kc.groups());
		check(kc.addon() != null && kc.addon().keys().equals(List.of("rotp_kc/King Crimson Time Erase Config.eraseTicks")) && kc.addonStands() == 1,
				"its add-on's server settings join its block, its client one does not: " + kc.addon());
		check(kc.keys().size() == 5, "the block's reset names its own rows and its add-on's: " + kc.keys());
		List<ConfigWidgetRules.StandBlock> echoes = stands.stream().filter(block -> block.standId().getNamespace().equals("rotp_echoes")).toList();
		check(echoes.size() == 2 && echoes.stream().allMatch(block -> block.addon() != null && block.addonStands() == 2),
				"an add-on with two Stands shows its settings in both, saying it shares them");
		ConfigWidgetRules.StandBlock sp = stands.stream().filter(block -> block.standId().getPath().equals("star_platinum")).findFirst().orElseThrow();
		check(sp.groups().getFirst().path().equals("special") && sp.addon() == null, "a core Stand's own values; the core is no add-on");
		check(ConfigWidgetRules.matchesSearch("crim", "King Crimson", kc.standId(), "King Crimson")
				&& ConfigWidgetRules.matchesSearch("ROTP_KC", "Some name", kc.standId(), null)
				&& ConfigWidgetRules.matchesSearch("spin", "Ball Breaker", kc.standId(), "Spin")
				&& !ConfigWidgetRules.matchesSearch("platinum", "King Crimson", kc.standId(), null),
				"a search finds a Stand by its name, its id or its add-on's name");
	}

	private static void aStandsShareSlidesInFivesWithItsSign() {
		ConfigEntry share = stand("rotp_kc:king_crimson", "cooldowns", Kind.INTEGER, new JsonPrimitive(100), new Bounds(0, 500));
		check(ConfigWidgetRules.widgetKind(share) == WidgetKind.SLIDER && ConfigWidgetRules.sliderStep(share).intValue() == 5,
				"a share is a slider in fives");
		check(ConfigWidgetRules.sliderValue(share, 0.303).getAsInt() == 150 && ConfigWidgetRules.sliderText(share, new JsonPrimitive(150)).equals("150%"),
				"a share snaps to fives and reads with its sign: " + ConfigWidgetRules.sliderValue(share, 0.303));
		ConfigEntry power = stand("rotp_kc:king_crimson", "power", Kind.DECIMAL, new JsonPrimitive(16.0), new Bounds(0, 1000));
		check(ConfigWidgetRules.widgetKind(power) == WidgetKind.NUMBER && ConfigWidgetRules.valueNumber(power, new JsonPrimitive(16.0)).equals("16.0"),
				"a stat is typed, with no sign");
		check(ConfigWidgetRules.optionKey(stand("rotp_kc:king_crimson", "miningTier", Kind.STRING, new JsonPrimitive("none"), null), "none")
				.equals("jojo_ripples.config.option.miningTier.none"), "a tier's choice has the core's text");
	}

	private static void resetAllResetsOnlyItsOwnPage() {
		List<ConfigEntry> entries = new ArrayList<>(standsAndAddons());
		entries.replaceAll(entry -> entry.key().endsWith("cooldowns") || entry.key().equals(RANGE)
				? new ConfigEntry(entry.key(), entry.group(), entry.side(), entry.kind(), entry.elementKind(), entry.defaultValue(),
						entry.kind() == Kind.INTEGER && entry.key().startsWith("@") ? new JsonPrimitive(50) : new JsonPrimitive(3),
						entry.bounds(), entry.listSize(), entry.choices(), entry.restart(), entry.synced(), entry.translationKey())
				: entry);
		FakeServer server = new FakeServer();
		ConfigEditSession session = new ConfigEditSession(server, List::of);
		session.open(0);
		server.answer(session, true, ConfigEditResult.ok(List.of()), entries);
		java.util.Set<String> standMods = ConfigWidgetRules.standNamespaces(entries);
		check(session.resetAll(entry -> ConfigWidgetRules.isOnStandsPage(entry, standMods), null), "the Stands page has a change to reset");
		check(server.sent.size() == 2 && server.last().key().equals("@rotp_kc:king_crimson") && session.pending(RANGE) == null
				&& session.pending("@rotp_kc:king_crimson/cooldowns") != null,
				"Reset All on the Stands page resets the Stands' groups alone: " + server.sent);
	}

	private static ConfigEntry addon(String key, Kind kind, JsonElement value, @Nullable Bounds bounds) {
		return new ConfigEntry(key, rotp.core.config.editor.AddonConfigKeys.group(key), Side.SERVER, kind, kind == Kind.LIST ? Kind.STRING : null,
				value, value, bounds, null, List.of(), Restart.NONE, false, null);
	}

	private static ConfigEditSession opened(FakeServer server, boolean canEdit) {
		ConfigEditSession session = new ConfigEditSession(server, List::of);
		session.open(0);
		check(session.access() == Access.LOADING && server.last().operation == Operation.READ, "opening must ask the server");
		server.answer(session, canEdit, ConfigEditResult.ok(List.of()), catalog());
		return session;
	}

	private static List<ConfigEntry> catalog() {
		return List.of(
				entry(RANGE, Kind.INTEGER, new JsonPrimitive(12), new Bounds(0, Integer.MAX_VALUE), List.of()),
				entry(DAMAGE, Kind.DECIMAL, new JsonPrimitive(1.0), new Bounds(0, 1), List.of()));
	}

	private static ConfigEntry entry(String key, Kind kind, JsonElement value, @Nullable Bounds bounds, List<String> choices) {
		int dot = key.lastIndexOf('.');
		return new ConfigEntry(key, dot < 0 ? "" : key.substring(0, dot), Side.SERVER, kind, kind == Kind.LIST ? Kind.STRING : null,
				value, value, bounds, null, choices, Restart.NONE, false, null);
	}

	private static Change change(String key, int before, int after) {
		return new Change(key, new JsonPrimitive(before), new JsonPrimitive(after), Restart.NONE);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class FakeServer implements ConfigEditSession.Transport {
		private record Sent(Operation operation, String key, @Nullable JsonElement value) {}

		private final List<Sent> sent = new ArrayList<>();
		private boolean world = true;
		private boolean channel = true;
		private long nextId;
		private long waitingId;
		private @Nullable Answer waiting;

		@Override
		public boolean hasWorld() {
			return world;
		}

		@Override
		public boolean supported() {
			return channel;
		}

		@Override
		public long send(Operation operation, String key, @Nullable JsonElement value) {
			sent.add(new Sent(operation, key, value));
			return ++nextId;
		}

		@Override
		public @Nullable Answer answer(long id) {
			return waitingId == id ? waiting : null;
		}

		Sent last() {
			return sent.getLast();
		}

		boolean wasSent(String key) {
			return sent.stream().anyMatch(request -> request.key.equals(key));
		}

		void answer(ConfigEditSession session, boolean canEdit, ConfigEditResult result, List<ConfigEntry> entries) {
			waitingId = nextId;
			waiting = new Answer(canEdit, result, entries);
			session.tick(1);
			waiting = null;
		}
	}
}
