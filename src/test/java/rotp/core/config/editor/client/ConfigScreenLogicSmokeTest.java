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
		System.out.println("Config screen logic smoke test passed");
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
		ConfigEntry dead = new ConfigEntry("toggleLmbHotbar", "controls", Side.CLIENT, Kind.BOOLEAN, null, new JsonPrimitive(false),
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
		List<ConfigEntry> entries = List.of(
				addon("rotp_z/b.one", Kind.BOOLEAN, new JsonPrimitive(true), null),
				addon("rotp_z/a.two", Kind.BOOLEAN, new JsonPrimitive(true), null),
				addon("rotp_z/top", Kind.BOOLEAN, new JsonPrimitive(true), null),
				addon("rotp_a/only.one", Kind.BOOLEAN, new JsonPrimitive(true), null));
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
