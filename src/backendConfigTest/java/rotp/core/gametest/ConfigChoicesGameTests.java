package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.config.editor.AddonConfigCatalog;
import rotp.core.config.editor.AddonConfigKeys;
import rotp.core.config.editor.ConfigChoices;
import rotp.core.config.editor.ConfigChoices.Choice;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEditorPackets;
import rotp.core.config.editor.ConfigEditorPackets.Operation;
import rotp.core.config.editor.ConfigEditorPackets.Reply;
import rotp.core.config.editor.ConfigEditorPackets.Request;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.client.ConfigWidgetRules;
import rotp.core.config.editor.client.ConfigWidgetRules.WidgetKind;
import rotp.core.core.JojoMod;

/**
 * A list setting whose items a source on the server offers: the items cross with the entry (more than the old 32), a write
 * keeps to them, an item the server stopped offering stays shown and can be unticked, and a list without a source is the
 * plain list it was. The add-on here is a stand-in spec with a synthetic source, through the catalog's own path.
 */
@GameTestHolder(CommonConfigEditorGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class ConfigChoicesGameTests {
	private static final String NAMESPACE = CommonConfigEditorGameTests.NAMESPACE;
	private static final List<String> LOCALES = List.of("en_us", "en_pt", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br",
			"ru_ru", "uk_ua", "zh_cn", "zh_tw");

	private ConfigChoicesGameTests() {}

	/** A stand-in add-on: its spec on a file of its own, found by the catalog the way a real one is. */
	private record Fixture(String mod, ConfigTracker tracker, Path folder) {
		static Fixture of(String mod, ModConfigSpec spec) throws IOException {
			var tracker = new ConfigTracker();
			Path folder = Files.createTempDirectory("rotp-choices-");
			var container = ModList.get().getModContainerById(JojoMod.MOD_ID).orElseThrow();
			tracker.registerConfig(ModConfig.Type.COMMON, spec, container, mod + "-common.toml");
			tracker.loadConfigs(ModConfig.Type.COMMON, folder);
			AddonConfigCatalog.addForTest(mod, ModConfig.Type.COMMON, spec);
			return new Fixture(mod, tracker, folder);
		}

		String key(String path) {
			return AddonConfigKeys.key(mod, List.of("worlds", path));
		}

		void close() throws IOException {
			AddonConfigCatalog.removeForTest(mod);
			tracker.unloadConfigs(ModConfig.Type.COMMON);
			try (var files = Files.list(folder)) {
				for (Path file : files.toList()) {
					Files.deleteIfExists(file);
				}
			}
			Files.deleteIfExists(folder);
		}
	}

	private static ModConfigSpec.ConfigValue<List<? extends String>> list(ModConfigSpec.Builder builder, String name, List<String> defaults) {
		return builder.defineListAllowEmpty(name, defaults, () -> "", item -> item instanceof String);
	}

	private static List<Choice> worlds(int count) {
		List<Choice> choices = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			choices.add(i == 1 ? new Choice("test:w1", "World one", "jojo_ripples.config.dimension.the_nether") : Choice.of("test:w" + i, "World " + i));
		}
		return choices;
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "choices_read")
	public static void aListWithASourceCarriesItsItemsAndAPlainListDoesNot(GameTestHelper helper) throws IOException {
		var builder = new ModConfigSpec.Builder();
		builder.push("worlds");
		var parallel = list(builder.worldRestart(), "parallel", List.of("test:w0"));
		list(builder, "plain", List.of("a"));
		var huge = list(builder, "huge", List.of());
		var broken = list(builder, "broken", List.of("test:kept"));
		builder.pop();
		ModConfigSpec spec = builder.build();
		ConfigChoices.register(parallel, server -> worlds(70));
		// more than a list may hold, each id near the longest a list item may be
		ConfigChoices.register(huge, server -> {
			List<Choice> many = new ArrayList<>();
			for (int i = 0; i < 1500; i++) {
				many.add(Choice.of(("test:" + "w".repeat(140) + i), "A world with a long name " + i));
			}
			return many;
		});
		ConfigChoices.register(broken, server -> {
			throw new IllegalStateException("the add-on's source is down");
		});
		Fixture fixture = Fixture.of("rotp_choices_read", spec);
		ServerPlayer reader = StandConfigTestKit.serverPlayer(helper, "choices_reader");
		var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			var exchange = new StandConfigTestKit.Exchange(reader);
			ConfigEditorPackets.handleRequest(new Request(1, Operation.READ, "", null), exchange.context);
			Reply sent = exchange.replies.getFirst();
			helper.assertTrue(sent.result().status() == Status.OK, "the page is read: " + sent.result());
			ConfigEntry worlds = entry(sent, fixture.key("parallel"));
			helper.assertTrue(worlds != null && worlds.listChoices() != null && worlds.listChoices().size() == 70,
					"seventy offered items cross with their entry: " + (worlds == null ? null : worlds.listChoices()));
			helper.assertTrue(worlds.listChoices().get(5).equals(Choice.of("test:w5", "World 5"))
					&& "jojo_ripples.config.dimension.the_nether".equals(worlds.listChoices().get(1).translationKey()),
					"each with its id, its name and its key: " + worlds.listChoices().subList(0, 6));
			helper.assertTrue(worlds.restart() == ConfigEntry.Restart.WORLD && ConfigWidgetRules.widgetKind(worlds) == WidgetKind.CHECKLIST,
					"a checklist that names its world restart");
			ConfigEntry plain = entry(sent, fixture.key("plain"));
			helper.assertTrue(plain != null && plain.listChoices() == null && ConfigWidgetRules.widgetKind(plain) == WidgetKind.LIST,
					"a list without a source stays the plain list");
			ConfigEntry many = entry(sent, fixture.key("huge"));
			helper.assertTrue(many != null && many.listChoices().size() == ConfigChoices.LIMIT,
					"a source that offers more than a list holds is cut, not dropped: " + (many == null ? null : many.listChoices().size()));
			ConfigEntry down = entry(sent, fixture.key("broken"));
			helper.assertTrue(down != null && down.listChoices() != null && down.listChoices().isEmpty()
					&& down.value().getAsJsonArray().get(0).getAsString().equals("test:kept"),
					"a failing source costs its items, never the page: what is stored stays shown");
			Reply.CODEC.encode(buf, sent);
			int bytes = buf.readableBytes();
			Reply received = Reply.CODEC.decode(buf);
			helper.assertTrue(received.entries().equals(sent.entries()), "the items arrive as sent");
			JojoMod.getLogger().info("Checklist wire measure: {} rows, {} offered items, {} bytes", sent.entries().size(),
					sent.entries().stream().filter(row -> row.listChoices() != null).mapToInt(row -> row.listChoices().size()).sum(), bytes);
		}
		finally {
			buf.release();
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "choices_write")
	public static void aWriteKeepsToWhatTheServerOffers(GameTestHelper helper) throws IOException {
		var builder = new ModConfigSpec.Builder();
		builder.push("worlds");
		var parallel = list(builder.worldRestart(), "parallel", List.of("test:a"));
		builder.pop();
		ModConfigSpec spec = builder.build();
		List<Choice> offered = new ArrayList<>(List.of(Choice.of("test:a", "A"), Choice.of("test:b", "B"), Choice.of("test:c", "C")));
		ConfigChoices.register(parallel, server -> List.copyOf(offered));
		Fixture fixture = Fixture.of("rotp_choices_write", spec);
		String key = fixture.key("parallel");
		ServerPlayer player = StandConfigTestKit.serverPlayer(helper, "choices_writer");
		var ops = player.server.getPlayerList().getOps();
		try {
			// getRaw() is what is stored; a value with a restart may keep its old one in get() until then
			helper.assertTrue(set(player, key, "test:a", "test:b") == Status.FORBIDDEN && parallel.getRaw().equals(List.of("test:a")),
					"one who may not edit writes nothing");
			ops.add(new ServerOpListEntry(player.getGameProfile(), 2, false));
			ConfigEditorPackets.onPlayerLoggedOut(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
			// the running world has read it, as the add-on does when the world starts
			List<? extends String> inPlay = parallel.get();
			Status taken = set(player, key, "test:a", "test:b");
			helper.assertTrue(taken == Status.OK && parallel.getRaw().equals(List.of("test:a", "test:b")),
					"offered items are taken: " + lastResult + " stored " + parallel.getRaw());
			helper.assertTrue(read(player, key).value().equals(ids("test:a", "test:b")) && parallel.get().equals(inPlay),
					"the page shows what is stored, while play keeps " + inPlay + " until the world restarts: " + read(player, key).value());
			helper.assertTrue(set(player, key, "test:a", "test:zzz") == Status.INVALID_VALUE && parallel.getRaw().equals(List.of("test:a", "test:b")),
					"an item the server does not offer is refused, and nothing is written");
			helper.assertTrue(set(player, key, "test:c", "test:c") == Status.INVALID_VALUE, "an item twice is refused");

			// the add-on that offered B is gone: B stays stored, is shown as such, and the rest stays editable
			offered.removeIf(choice -> choice.id().equals("test:b"));
			ConfigEntry read = read(player, key);
			helper.assertTrue(read.value().getAsJsonArray().contains(new com.google.gson.JsonPrimitive("test:b"))
					&& read.listChoices().stream().noneMatch(choice -> choice.id().equals("test:b")),
					"a stored item no longer offered is shown with the value, not among the items: " + read);
			helper.assertTrue(set(player, key, "test:a", "test:b", "test:c") == Status.OK,
					"ticking another while it is still ticked is taken");
			helper.assertTrue(set(player, key, "test:a", "test:c") == Status.OK && parallel.getRaw().equals(List.of("test:a", "test:c")),
					"and it can be unticked: " + parallel.getRaw());
			helper.assertTrue(set(player, key, "test:a", "test:b") == Status.INVALID_VALUE,
					"once unticked it cannot come back while the server does not offer it");
			var reset = new StandConfigTestKit.Exchange(player);
			ConfigEditorPackets.handleRequest(new Request(9, Operation.RESET, key, null), reset.context);
			helper.assertTrue(reset.replies.getFirst().result().status() == Status.OK && parallel.getRaw().equals(List.of("test:a"))
					&& reset.replies.getFirst().result().changes().getFirst().restart() == ConfigEntry.Restart.WORLD,
					"Reset to default, with the restart it needs: " + reset.replies.getFirst().result());
		}
		finally {
			ops.remove(player.getGameProfile());
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "choices_dimensions")
	public static void theServersDimensionsAreASourceWithTheirNames(GameTestHelper helper) {
		List<Choice> dimensions = ConfigChoices.dimensions().choices(helper.getLevel().getServer());
		helper.assertTrue(!dimensions.isEmpty() && dimensions.getFirst().id().equals("minecraft:overworld"), "the Overworld first: " + dimensions);
		for (String path : List.of("overworld", "the_nether", "the_end")) {
			Choice choice = dimensions.stream().filter(candidate -> candidate.id().equals("minecraft:" + path)).findFirst().orElse(null);
			helper.assertTrue(choice != null && ("jojo_ripples.config.dimension." + path).equals(choice.translationKey()),
					"the base game's " + path + " with the core's name for it: " + choice);
		}
		helper.assertTrue(dimensions.stream().map(Choice::id).distinct().count() == dimensions.size()
				&& dimensions.size() == helper.getLevel().getServer().levelKeys().size(), "every dimension of the server, once");
		JsonObject english = lang("en_us");
		List<String> missing = new ArrayList<>();
		List<String> keys = List.of("jojo_ripples.config.screen.checklist.summary", "jojo_ripples.config.screen.checklist.filter",
				"jojo_ripples.config.screen.checklist.gone", "jojo_ripples.config.screen.checklist.no_match",
				"jojo_ripples.config.dimension.overworld", "jojo_ripples.config.dimension.the_nether", "jojo_ripples.config.dimension.the_end",
				// and the colour card's two lines, the screen's other new text
				"jojo_ripples.config.screen.color.hex", "jojo_ripples.config.screen.color.invalid");
		for (String locale : LOCALES) {
			JsonObject own = lang(locale);
			for (String key : keys) {
				JsonElement value = own.get(key);
				if (value == null || value.getAsString().isBlank()) {
					missing.add(locale + " " + key);
				}
				else if (!locale.startsWith("en_") && value.getAsString().equals(english.get(key).getAsString())) {
					missing.add(locale + " " + key + " is the English text");
				}
				else if (value.getAsString().split("%s", -1).length != english.get(key).getAsString().split("%s", -1).length) {
					missing.add(locale + " " + key + " differs from English in its %s");
				}
			}
		}
		helper.assertTrue(missing.isEmpty(), "the checklist card would show raw names: " + missing);
		helper.succeed();
	}

	private static Status set(ServerPlayer player, String key, String... items) {
		JsonArray array = new JsonArray();
		for (String item : items) {
			array.add(item);
		}
		var exchange = new StandConfigTestKit.Exchange(player);
		ConfigEditorPackets.handleRequest(new Request(2, Operation.SET, key, array), exchange.context);
		lastResult = exchange.replies.getFirst().result().toString();
		return exchange.replies.getFirst().result().status();
	}

	private static String lastResult = "";

	private static JsonArray ids(String... items) {
		JsonArray array = new JsonArray();
		for (String item : items) {
			array.add(item);
		}
		return array;
	}

	private static ConfigEntry read(ServerPlayer player, String key) {
		var exchange = new StandConfigTestKit.Exchange(player);
		ConfigEditorPackets.handleRequest(new Request(3, Operation.READ, "", null), exchange.context);
		ConfigEntry entry = entry(exchange.replies.getFirst(), key);
		if (entry == null) {
			throw new GameTestAssertException(key + " is not in the page");
		}
		return entry;
	}

	private static ConfigEntry entry(Reply reply, String key) {
		return reply.entries().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
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
