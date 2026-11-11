package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.command.commands.JojoConfigCommand;
import rotp.core.config.editor.AddonConfigCatalog;
import rotp.core.config.editor.CommonConfigEditor;
import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEditResult.Change;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEditorPackets;
import rotp.core.config.editor.ConfigEditorPackets.Operation;
import rotp.core.config.editor.ConfigEditorPackets.Reply;
import rotp.core.config.editor.ConfigEditorPackets.Request;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.StandConfigCatalog;
import rotp.core.config.editor.StandConfigKeys;
import rotp.core.config.editor.client.ConfigWidgetRules;
import rotp.core.config.editor.client.ConfigWidgetRules.WidgetKind;
import rotp.core.core.JojoMod;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.standpower.StandSpecials;
import rotp.core.powersystem.standpower.StandStats.StatWithValue;
import rotp.core.powersystem.standpower.StandTuning;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.type.StandType;

/**
 * The Stands page's rows through the editor's own path: one row set per registered Stand, the core's and an add-on's, kept
 * in the files /jojoconfig stand_stats writes and /reload reads, obtaining kept in bannedStands, operators only.
 */
@GameTestHolder(CommonConfigEditorGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class StandConfigEditorGameTests {
	private static final String NAMESPACE = CommonConfigEditorGameTests.NAMESPACE;
	private static final List<String> GENERAL = List.of("obtainable", "randomWeight", "power", "powerMax", "speed", "speedMax",
			"durability", "durabilityMax", "precision", "precisionMax", "rangeEffective", "rangeMax", "damageVsPlayers",
			"damageVsMobs", "cooldowns", "staminaCosts", "miningTier", "miningStrength");
	private static final List<String> LOCALES = List.of("en_us", "en_pt", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br",
			"ru_ru", "uk_ua", "zh_cn", "zh_tw");
	// the mod's own word "Stand" and a mineral keep their English spelling in these
	private static final Set<String> SAME_AS_ENGLISH = Set.of("es_es jojo_ripples.config.screen.page.stands",
			"es_ve jojo_ripples.config.screen.page.stands", "pt_br jojo_ripples.config.screen.page.stands",
			"it_it jojo_ripples.config.screen.page.stands", "it_it jojo_ripples.config.option.miningTier.netherite");

	private StandConfigEditorGameTests() {}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "standcfg_catalog")
	public static void everyRegisteredStandHasItsRowsAndTheirText(GameTestHelper helper) {
		List<ConfigEntry> rows = CommonConfigEditor.entries().stream().filter(ConfigWidgetRules::isStand).toList();
		Set<String> keys = new HashSet<>();
		rows.forEach(row -> helper.assertTrue(keys.add(row.key()), "a row twice: " + row.key()));
		List<StandType> stands = StandConfigCatalog.stands();
		helper.assertTrue(stands.stream().anyMatch(stand -> stand.getId().equals(StandConfigTestKit.ADDON))
				&& stands.stream().anyMatch(stand -> stand.getId().equals(StandConfigTestKit.SP)), "the core's and the add-on's Stands");
		int expected = 0;
		for (StandType stand : stands) {
			for (String field : GENERAL) {
				ConfigEntry row = rowOf(rows, stand.getId(), field);
				helper.assertTrue(row != null && row.group().equals(StandConfigKeys.group(stand.getId())), "no " + field + " row for " + stand.getId());
			}
			expected += GENERAL.size() + StandSpecials.of(stand.getId()).size();
		}
		helper.assertTrue(rows.size() == expected, "rows " + rows.size() + " / " + expected);
		helper.assertTrue(StandSpecials.of(StandConfigTestKit.ADDON).isEmpty() && StandSpecials.of(StandConfigTestKit.SP).size() == 5,
				"special rows are the core Stands' own");
		helper.assertTrue(ConfigWidgetRules.widgetKind(rowOf(rows, StandConfigTestKit.SP, "obtainable")) == WidgetKind.TOGGLE
				&& ConfigWidgetRules.widgetKind(rowOf(rows, StandConfigTestKit.SP, "cooldowns")) == WidgetKind.SLIDER
				&& ConfigWidgetRules.widgetKind(rowOf(rows, StandConfigTestKit.SP, "power")) == WidgetKind.NUMBER
				&& ConfigWidgetRules.widgetKind(rowOf(rows, StandConfigTestKit.SP, "miningTier")) == WidgetKind.CYCLE, "the widgets of the rows");

		JsonObject english = lang("en_us");
		List<String> missing = new ArrayList<>();
		Set<String> texts = new HashSet<>();
		for (ConfigEntry row : rows) {
			texts.add(ConfigWidgetRules.labelKey(row));
			texts.add(ConfigWidgetRules.tooltipKey(row));
			row.choices().forEach(choice -> texts.add(ConfigWidgetRules.optionKey(row, choice)));
		}
		for (String group : ConfigWidgetRules.STAND_GROUPS) {
			texts.add(StandConfigCatalog.TEXT + "group." + group);
		}
		texts.addAll(List.of(StandConfigCatalog.TEXT + "group.addon", StandConfigCatalog.TEXT + "group.addon_shared",
				StandConfigCatalog.TEXT + "no_match", "jojo_ripples.config.screen.page.stands", "jojo_ripples.config.screen.page.stands.desc",
				"jojo_ripples.config.screen.search", "jojo_ripples.config.screen.search.hint", "jojo_ripples.config.screen.parts.all",
				"jojo_ripples.config.screen.parts.none"));
		for (String locale : LOCALES) {
			JsonObject own = lang(locale);
			for (String text : texts) {
				String value = string(own, text);
				String base = string(english, text);
				if (value == null || value.isBlank() || base == null) {
					missing.add(locale + " " + text);
				}
				else if (!locale.startsWith("en_") && value.equals(base) && !SAME_AS_ENGLISH.contains(locale + " " + text)) {
					missing.add(locale + " " + text + " is the English text");
				}
				else if (value.split("%s", -1).length != base.split("%s", -1).length) {
					missing.add(locale + " " + text + " differs from English in its %s");
				}
			}
		}
		helper.assertTrue(missing.isEmpty(), "the Stands page would show raw names: " + missing);
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_stat")
	public static void aStatSetHereIsTheCommandsFileAndReachesTheUser(GameTestHelper helper) throws IOException {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP, StandConfigTestKit.ADDON);
		try {
			Player user = kit.user(StandConfigTestKit.SP, Vec3.atCenterOf(helper.absolutePos(net.minecraft.core.BlockPos.ZERO.above())));
			StandType sp = StandConfigTestKit.type(StandConfigTestKit.SP);
			double before = sp.getStandStats().getValue(StatWithValue.POWER);
			ConfigEditResult result = kit.set(StandConfigTestKit.SP, "power", new JsonPrimitive(21.5));
			helper.assertTrue(changed(result, StandConfigTestKit.key(StandConfigTestKit.SP, "power"), before, 21.5)
					&& changed(result, StandConfigTestKit.key(StandConfigTestKit.SP, "powerMax"),
							sp.getStandStats().getDefaultValue(StatWithValue.POWER_MAX), 21.5),
					"the answer names the stat and the top its start lifted: " + result.changes());
			JsonObject file = kit.read(StandConfigTestKit.SP, StandConfigCatalog.STATS_MEMBER);
			helper.assertTrue(file != null && file.size() == 1 && file.get("power").getAsDouble() == 21.5,
					"the stat is in the command's own file, alone: " + file);
			helper.assertTrue(Files.exists(kit.packRoot().resolve("pack.mcmeta")), "the pack is a data pack");
			var server = kit.server();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					"jojoconfig stand_stats jojo_ripples:star_platinum");
			JsonObject kept = kit.read(StandConfigTestKit.SP, StandConfigCatalog.STATS_MEMBER);
			helper.assertTrue(kept.equals(file), "the command sees the file the page wrote as the admin's and keeps it: " + kept);
			helper.assertTrue(sp.getStandStats().getValue(StatWithValue.POWER) == 21.5 && strength(user) == 21.5,
					"the Stand and its user take the stat at once: " + strength(user));

			StandType addon = StandConfigTestKit.type(StandConfigTestKit.ADDON);
			kit.set(StandConfigTestKit.ADDON, "speed", new JsonPrimitive(3.0));
			JsonObject addonFile = kit.read(StandConfigTestKit.ADDON, StandConfigCatalog.STATS_MEMBER);
			helper.assertTrue(addonFile != null && addonFile.get("speed").getAsDouble() == 3.0
					&& addon.getStandStats().getValue(StatWithValue.SPEED) == 3.0
					&& kit.file(StandConfigTestKit.ADDON, "stats").toString().contains("rotp_backend_config"),
					"an add-on's Stand has its file under its own namespace: " + addonFile);

			ConfigEditResult reset = CommonConfigEditor.reset(StandConfigTestKit.key(StandConfigTestKit.SP, "power"));
			helper.assertTrue(reset.status() == Status.OK && !Files.exists(kit.file(StandConfigTestKit.SP, "stats"))
					&& sp.getStandStats().getValue(StatWithValue.POWER) == before && strength(user) == before,
					"a stat back at its default leaves the file, and the Stand is as before");
		}
		finally {
			kit.close();
		}
		helper.succeed();
	}

	private static double strength(Player user) {
		return user.getAttribute(ModEntityAttributes.STAND_STRENGTH).getBaseValue();
	}

	/** One store: what the page wrote is what a reload reads, and a hand edit a reload took is what the page shows. */
	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 1200, batch = "standcfg_reload")
	public static void aReloadReadsWhatThePageWroteAndThePageReadsAReload(GameTestHelper helper) throws IOException {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP);
		StandType sp = StandConfigTestKit.type(StandConfigTestKit.SP);
		kit.set(StandConfigTestKit.SP, "cooldowns", new JsonPrimitive(45));
		kit.set(StandConfigTestKit.SP, "speedMax", new JsonPrimitive(23.5));
		kit.set(StandConfigTestKit.SP, StandConfigKeys.SPECIAL + "timeStopSeconds", new JsonPrimitive(7.5));
		CompletableFuture<Void> first = reload(kit);
		int[] stage = {0};
		CompletableFuture<?>[] second = {null};
		helper.onEachTick(() -> {
			try {
				if (stage[0] == 0 && first.isDone()) {
					first.join();
					helper.assertTrue(sp.getTuning().percent(StandTuning.Percent.COOLDOWNS) == 45
							&& sp.getStandStats().getValue(StatWithValue.SPEED_MAX) == 23.5
							&& StandSpecials.SP_TIME_STOP_SECONDS.get(sp) == 7.5, "a reload reads the page's files back as they were");
					Files.writeString(kit.file(StandConfigTestKit.SP, "stats"), "{\n  \"speedMax\": 19.25\n}");
					second[0] = reload(kit);
					stage[0] = 1;
				}
				else if (stage[0] == 1 && second[0].isDone()) {
					second[0].join();
					ConfigEntry speedMax = StandConfigTestKit.entry(StandConfigTestKit.key(StandConfigTestKit.SP, "speedMax"));
					helper.assertTrue(speedMax.value().getAsDouble() == 19.25 && sp.getStandStats().getValue(StatWithValue.SPEED_MAX) == 19.25,
							"a hand edit a reload took is the page's value: " + speedMax.value());
					stage[0] = 2;
					kit.close();
					helper.succeed();
				}
			}
			catch (IOException | RuntimeException failure) {
				kit.close();
				throw new GameTestAssertException(failure.toString());
			}
		});
	}

	// what /reload does: the new pack is found and taken, then every data pack is read again
	private static CompletableFuture<Void> reload(StandConfigTestKit kit) {
		var server = kit.server();
		var packs = server.getPackRepository();
		packs.reload();
		List<String> selected = new ArrayList<>(packs.getSelectedIds());
		String pack = "file/" + kit.packRoot().getFileName();
		if (!selected.contains(pack)) {
			selected.add(pack);
		}
		return server.reloadResources(selected);
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_obtain")
	public static void obtainingIsTheBannedListSeenPerStand(GameTestHelper helper) {
		String banned = StandConfigCatalog.BANNED_STANDS;
		JsonElement original = StandConfigTestKit.entry(banned).value();
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP, StandConfigTestKit.ADDON);
		try {
			ConfigEditResult off = kit.set(StandConfigTestKit.SP, "obtainable", new JsonPrimitive(false));
			helper.assertTrue(off.changes().stream().anyMatch(change -> change.key().equals(banned))
					&& changedTo(off, StandConfigTestKit.key(StandConfigTestKit.SP, "obtainable"), false),
					"obtaining is bannedStands: both rows change at once: " + off.changes());
			helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).isStandBanned(StandConfigTestKit.SP)
					&& StandUtil.standsForPlayerArrow().noneMatch(stand -> stand.getId().equals(StandConfigTestKit.SP)),
					"an Arrow never gives it");
			kit.set(StandConfigTestKit.ADDON, "obtainable", new JsonPrimitive(false));
			helper.assertTrue(StandUtil.isStandBanned(StandConfigTestKit.type(StandConfigTestKit.ADDON), false), "an add-on's Stand the same");

			ConfigEditResult cleared = CommonConfigEditor.set(banned, new JsonArray());
			helper.assertTrue(cleared.status() == Status.OK
					&& changedTo(cleared, StandConfigTestKit.key(StandConfigTestKit.SP, "obtainable"), true)
					&& changedTo(cleared, StandConfigTestKit.key(StandConfigTestKit.ADDON, "obtainable"), true),
					"the list emptied on the Gameplay page turns the Stands' rows on in the same answer: " + cleared.changes());
			helper.assertTrue(StandUtil.standsForPlayerArrow().anyMatch(stand -> stand.getId().equals(StandConfigTestKit.SP)),
					"and the Arrow gives it again");
		}
		finally {
			kit.close();
			CommonConfigEditor.set(banned, original);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_op")
	public static void onlyAnOperatorEditsAStand(GameTestHelper helper) throws IOException {
		var kit = new StandConfigTestKit(helper, StandConfigTestKit.SP);
		ServerPlayer player = StandConfigTestKit.serverPlayer(helper, "stand_config_editor");
		var ops = player.server.getPlayerList().getOps();
		String key = StandConfigTestKit.key(StandConfigTestKit.SP, "damageVsMobs");
		try {
			var refused = new StandConfigTestKit.Exchange(player);
			ConfigEditorPackets.handleRequest(new Request(1, Operation.SET, key, new JsonPrimitive(150)), refused.context);
			helper.assertTrue(refused.replies.size() == 1 && refused.replies.getFirst().result().status() == Status.FORBIDDEN
					&& !Files.exists(kit.file(StandConfigTestKit.SP, StandTuning.MEMBER)), "one who may not edit writes nothing");
			ops.add(new ServerOpListEntry(player.getGameProfile(), 2, false));
			ConfigEditorPackets.onPlayerLoggedOut(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
			var taken = new StandConfigTestKit.Exchange(player);
			ConfigEditorPackets.handleRequest(new Request(2, Operation.SET, key, new JsonPrimitive(150)), taken.context);
			helper.assertTrue(taken.replies.getFirst().result().status() == Status.OK
					&& kit.read(StandConfigTestKit.SP, StandTuning.MEMBER).get("damageVsMobs").getAsInt() == 150,
					"an operator's edit is in the Stand's tuning file");
			var read = new StandConfigTestKit.Exchange(player);
			ConfigEditorPackets.handleRequest(new Request(3, Operation.READ, "", null), read.context);
			ConfigEntry row = read.replies.getFirst().entries().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
			helper.assertTrue(row != null && row.value().getAsInt() == 150, "and the page reads it");
		}
		finally {
			ops.remove(player.getGameProfile());
			kit.close();
		}
		helper.succeed();
	}

	/** Today's catalog is about 520 rows and the Stands' 1400: past the old limit of 1024, it must cross the wire whole. */
	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "standcfg_wire")
	public static void aCatalogPastTheOldLimitCrossesTheWire(GameTestHelper helper) throws IOException {
		var builder = new ModConfigSpec.Builder();
		builder.push("King Crimson Projectile Throw Config");
		for (int i = 0; i < 1200; i++) {
			builder.translation("rotp_kingcrimson.configuration.King Crimson Projectile Throw Config.value" + i)
					.defineInRange("impaleGapingWoundLevelTheLongest" + i, 0.5D + i, 0.0D, 2000.0D);
		}
		builder.pop();
		String mod = "rotp_stand_wire_fixture";
		var spec = builder.build();
		var tracker = new net.neoforged.fml.config.ConfigTracker();
		Path folder = Files.createTempDirectory("rotp-stand-wire-");
		var container = net.neoforged.fml.ModList.get().getModContainerById(JojoMod.MOD_ID).orElseThrow();
		tracker.registerConfig(ModConfig.Type.COMMON, spec, container, mod + "-common.toml");
		tracker.loadConfigs(ModConfig.Type.COMMON, folder);
		AddonConfigCatalog.addForTest(mod, ModConfig.Type.COMMON, spec);
		ServerPlayer reader = StandConfigTestKit.serverPlayer(helper, "stand_wire_reader");
		var context = new StandConfigTestKit.Exchange(reader);
		var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			ConfigEditorPackets.handleRequest(new Request(1, Operation.READ, "", null), context.context);
			Reply sent = context.replies.getFirst();
			helper.assertTrue(sent.result().status() == Status.OK && sent.entries().size() > 1024
					&& sent.entries().stream().anyMatch(ConfigWidgetRules::isStand), "a catalog this size must be answered: " + sent.result());
			Reply.CODEC.encode(buf, sent);
			int bytes = buf.readableBytes();
			Reply received = Reply.CODEC.decode(buf);
			helper.assertTrue(received.entries().equals(sent.entries()), "the catalog must arrive whole");
			long standRows = sent.entries().stream().filter(ConfigWidgetRules::isStand).count();
			JojoMod.getLogger().info("Stands page wire measure: {} rows ({} of Stands) in {} bytes", sent.entries().size(), standRows, bytes);
		}
		finally {
			buf.release();
			AddonConfigCatalog.removeForTest(mod);
			tracker.unloadConfigs(ModConfig.Type.COMMON);
			try (var files = Files.list(folder)) {
				for (Path file : files.toList()) {
					Files.deleteIfExists(file);
				}
			}
			Files.deleteIfExists(folder);
		}
		helper.succeed();
	}

	private static ConfigEntry rowOf(List<ConfigEntry> rows, ResourceLocation stand, String field) {
		String key = StandConfigKeys.key(stand, field);
		return rows.stream().filter(row -> row.key().equals(key)).findFirst().orElse(null);
	}

	private static boolean changed(ConfigEditResult result, String key, double before, double after) {
		return result.changes().stream().anyMatch(change -> change.key().equals(key)
				&& change.before().getAsDouble() == before && change.after().getAsDouble() == after);
	}

	private static boolean changedTo(ConfigEditResult result, String key, boolean after) {
		for (Change change : result.changes()) {
			if (change.key().equals(key)) {
				return change.after().getAsBoolean() == after;
			}
		}
		return false;
	}

	private static String string(JsonObject lang, String key) {
		JsonElement value = lang.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static JsonObject lang(String locale) {
		String path = "assets/" + JojoMod.MOD_ID + "/lang/" + locale + ".json";
		try (InputStream in = JojoModConfig.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new GameTestAssertException(path + " is missing");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (IOException e) {
			throw new GameTestAssertException(path + ": " + e);
		}
	}
}
