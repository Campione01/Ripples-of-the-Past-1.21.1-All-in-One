package rotp.core.gametest;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.StandAuraSettings;
import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.client.ClientConfigEditor;

@GameTestHolder(ClientConfigEditorGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class ClientConfigEditorGameTests {

	static final String NAMESPACE = "rotp_backend_config";
	private static final Gson GSON = new Gson();
	private static final String LEGACY_JSON = """
			{
			  "standOpacity": 65.0,
			  "standOthersOpacity": 83.0,
			  "standOthersTransparency": 62.0,
			  "standObstructionMode": "OFFICIAL_1_21_1",
			  "standAura": {"enabled": false, "framebufferScale": 2.0},
			  "broadcasted": {"standSide": "LEFT", "standAimAssist": false},
			  "futureSetting": {"keepOnRead": true}
			}
			""";

	private ClientConfigEditorGameTests() {}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void schemaReadsLegacyConfigWithoutWriting(GameTestHelper helper) throws Exception {
		helper.assertTrue(ClientModSettings.getInstance() == null && ClientConfigEditor.entries().isEmpty(),
				"An uninitialized store must not provide live settings");
		assertFailure(helper, ClientConfigEditor.set("standOutline", new JsonPrimitive(false)), Status.UNAVAILABLE);
		assertFailure(helper, ClientConfigEditor.reset("standOutline"), Status.UNAVAILABLE);
		assertFailure(helper, ClientConfigEditor.resetGroup("standDisplay"), Status.UNAVAILABLE);
		withSettings(helper, LEGACY_JSON, file -> {
			Map<String, ConfigEntry> entries = entriesByKey();
			Map<String, JsonElement> defaults = flatten(GSON.toJsonTree(new ClientModSettings.Settings()).getAsJsonObject());
			helper.assertTrue(entries.keySet().equals(defaults.keySet()), "Schema must cover exactly the persisted JSON leaves");
			for (ConfigEntry entry : entries.values()) {
				helper.assertTrue(entry.defaultValue().equals(defaults.get(entry.key())), "Default mismatch: " + entry.key());
				helper.assertTrue(entry.side() == ConfigEntry.Side.CLIENT && entry.restart() == ConfigEntry.Restart.NONE,
						"Client setting must apply live: " + entry.key());
				helper.assertTrue(entry.synced() == entry.key().startsWith("broadcasted."), "Sync metadata: " + entry.key());
			}
			assertValue(helper, entries, "standTransparency", new JsonPrimitive(35.0F));
			assertValue(helper, entries, "standOthersTransparency", new JsonPrimitive(62.0F));
			assertValue(helper, entries, "classicStandObstruction", new JsonPrimitive(false));
			assertValue(helper, entries, "standAura.framebufferScale", new JsonPrimitive(1.0F));
			assertValue(helper, entries, "standAura.mode", new JsonPrimitive("AUTO"));
			assertValue(helper, entries, "broadcasted.standSide", new JsonPrimitive("LEFT"));
			assertValue(helper, entries, "broadcasted.standAimAssist", new JsonPrimitive(false));
			helper.assertTrue(Files.readString(file).equals(LEGACY_JSON), "Reading legacy data must not rewrite it");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void invalidValuesLeaveMemoryAndFileUntouched(GameTestHelper helper) throws Exception {
		withSettings(helper, LEGACY_JSON, file -> {
			Map<String, ConfigEntry> before = entriesByKey();
			for (JsonElement invalid : List.of(new JsonPrimitive("false"), new JsonPrimitive(0), JsonNull.INSTANCE)) {
				assertFailure(helper, ClientConfigEditor.set("standOutline", invalid), Status.INVALID_VALUE);
			}
			for (JsonElement invalid : List.of(new JsonPrimitive("33"), new JsonPrimitive(-0.01), new JsonPrimitive(Double.NaN),
					JsonParser.parseString("100.00000001"), JsonParser.parseString("1e1000"))) {
				assertFailure(helper, ClientConfigEditor.set("standTransparency", invalid), Status.INVALID_VALUE);
			}
			assertFailure(helper, ClientConfigEditor.set("standStatsTranslucency", new JsonPrimitive(0.09)), Status.INVALID_VALUE);
			assertFailure(helper, ClientConfigEditor.set("standAura.shapeScaleX", new JsonPrimitive(0.049)), Status.INVALID_VALUE);
			assertFailure(helper, ClientConfigEditor.set("standAura.framebufferScale", new JsonPrimitive(1.01)), Status.INVALID_VALUE);
			assertFailure(helper, ClientConfigEditor.set("standAura.mode", new JsonPrimitive("open")), Status.INVALID_VALUE);
			assertFailure(helper, ClientConfigEditor.set("broadcasted.standSide", new JsonPrimitive("CENTER")), Status.INVALID_VALUE);
			assertFailure(helper, ClientConfigEditor.set("broadcasted.owner", new JsonObject()), Status.UNKNOWN_KEY);
			assertFailure(helper, ClientConfigEditor.reset("standOpacity"), Status.UNKNOWN_KEY);
			assertFailure(helper, ClientConfigEditor.resetGroup("owner"), Status.UNKNOWN_GROUP);
			assertChanged(helper, ClientConfigEditor.set("standAura.mode", new JsonPrimitive("AUTO")), Set.of());
			helper.assertTrue(Files.readString(file).equals(LEGACY_JSON), "Rejected edits and no-ops must not rewrite legacy JSON");
			helper.assertTrue(entriesByKey().equals(before), "Rejected edits must not change any live value");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
	public static void everyEntryWritesItsOwnFieldAndGroupResetIsIsolated(GameTestHelper helper) throws Exception {
		withSettings(helper, "{}", file -> {
			ClientModSettings.Settings active = ClientModSettings.getSettingsReadOnly();
			Map<String, JsonElement> expected = flatten(JsonParser.parseString(GSON.toJson(active)).getAsJsonObject());
			List<ConfigEntry> entries = ClientConfigEditor.entries();
			for (ConfigEntry entry : entries) {
				JsonElement value = otherValue(entry);
				ConfigEditResult result = ClientConfigEditor.set(entry.key(), value);
				assertChanged(helper, result, Set.of(entry.key()));
				var change = result.changes().getFirst();
				helper.assertTrue(change.before().equals(entry.defaultValue()) && change.after().equals(value),
						"Edit result must describe the persisted change: " + entry.key());
				expected.put(entry.key(), JsonParser.parseString(value.toString()));
				helper.assertTrue(readSaved(file).equals(expected), "Edit must write only its own JSON field: " + entry.key());
			}
			helper.assertTrue(ClientModSettings.getSettingsReadOnly() == active && active.standAura.mode == StandAuraSettings.Mode.OPEN
					&& active.broadcasted.standSide == HumanoidArm.LEFT, "Edits must update the existing live settings object");
			ClientModSettings.getInstance().load();
			for (ConfigEntry entry : ClientConfigEditor.entries()) {
				helper.assertTrue(JsonParser.parseString(entry.value().toString()).equals(expected.get(entry.key())),
						"Edit must survive the production loader: " + entry.key());
			}

			for (String group : entries.stream().map(ConfigEntry::group).distinct().toList()) {
				List<ConfigEntry> members = entries.stream().filter(entry -> entry.group().equals(group)).toList();
				assertChanged(helper, ClientConfigEditor.resetGroup(group), members.stream().map(ConfigEntry::key).collect(Collectors.toSet()));
				for (ConfigEntry member : members) {
					expected.put(member.key(), JsonParser.parseString(member.defaultValue().toString()));
				}
				helper.assertTrue(readSaved(file).equals(expected), "Group reset changed an unrelated JSON field: " + group);
			}
			assertChanged(helper, ClientConfigEditor.set("standAura.mode", new JsonPrimitive("OPEN")), Set.of("standAura.mode"));
			assertChanged(helper, ClientConfigEditor.set("standAura.enabled", new JsonPrimitive(false)), Set.of("standAura.enabled"));
			assertChanged(helper, ClientConfigEditor.reset("standAura.mode"), Set.of("standAura.mode"));
			expected.put("standAura.enabled", new JsonPrimitive(false));
			helper.assertTrue(readSaved(file).equals(expected), "Single reset must preserve another value in the same group");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void failedSaveDoesNotPublishLiveChanges(GameTestHelper helper) throws Exception {
		withSettings(helper, LEGACY_JSON, file -> {
			ClientModSettings.Settings live = ClientModSettings.getSettingsReadOnly();
			Map<String, ConfigEntry> before = entriesByKey();
			Files.delete(file);
			Files.createDirectory(file);
			Path blocker = file.resolve("blocker");
			try {
				Files.writeString(blocker, "keep");
				assertFailure(helper, ClientConfigEditor.resetGroup("standAura"), Status.SAVE_FAILED);
				assertFailure(helper, ClientConfigEditor.set("broadcasted.standAimAssist", new JsonPrimitive(true)), Status.SAVE_FAILED);
				helper.assertTrue(entriesByKey().equals(before) && ClientModSettings.getSettingsReadOnly() == live,
						"Failed persistence must leave the live settings and their identity unchanged");
				helper.assertTrue(Files.readString(blocker).equals("keep"), "Failed persistence must not replace the blocking destination");
				try (var contents = Files.list(file.getParent())) {
					helper.assertTrue(contents.toList().equals(List.of(file)), "Failed persistence must clean up its temporary file");
				}
			}
			finally {
				Files.deleteIfExists(blocker);
			}
		});
		helper.succeed();
	}

	private static JsonElement otherValue(ConfigEntry entry) {
		return switch (entry.kind()) {
			case BOOLEAN -> new JsonPrimitive(!entry.defaultValue().getAsBoolean());
			case ENUM -> new JsonPrimitive(entry.choices().stream().filter(choice -> !choice.equals(entry.defaultValue().getAsString())).findFirst().orElseThrow());
			case DECIMAL -> {
				double bound = entry.defaultValue().getAsFloat() == (float) entry.bounds().minimum()
						? entry.bounds().maximum() : entry.bounds().minimum();
				yield new JsonPrimitive((float) bound);
			}
			default -> throw new AssertionError("Unexpected client entry kind: " + entry.kind());
		};
	}

	private static Map<String, JsonElement> readSaved(Path file) throws Exception {
		return flatten(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
	}

	private static Map<String, JsonElement> flatten(JsonObject root) {
		Map<String, JsonElement> entries = new LinkedHashMap<>();
		flatten(root, "", entries);
		return entries;
	}

	private static void flatten(JsonObject object, String prefix, Map<String, JsonElement> entries) {
		object.entrySet().forEach(entry -> {
			String key = prefix + entry.getKey();
			JsonElement value = entry.getValue();
			if (value.isJsonObject()) {
				flatten(value.getAsJsonObject(), key + ".", entries);
			}
			else {
				entries.put(key, value);
			}
		});
	}

	private static Map<String, ConfigEntry> entriesByKey() {
		return ClientConfigEditor.entries().stream().collect(Collectors.toMap(ConfigEntry::key, entry -> entry));
	}

	private static void assertValue(GameTestHelper helper, Map<String, ConfigEntry> entries, String key, JsonElement expected) {
		helper.assertTrue(entries.containsKey(key) && entries.get(key).value().equals(expected), "Unexpected value: " + key);
	}

	private static void assertChanged(GameTestHelper helper, ConfigEditResult result, Set<String> keys) {
		helper.assertTrue(result.status() == Status.OK && result.changes().size() == keys.size()
				&& result.changes().stream().map(ConfigEditResult.Change::key).collect(Collectors.toSet()).equals(keys),
				"Expected changed keys " + keys + ", got " + result);
	}

	private static void assertFailure(GameTestHelper helper, ConfigEditResult result, Status status) {
		helper.assertTrue(result.status() == status && result.changes().isEmpty(), "Expected " + status + ", got " + result);
	}

	private static void withSettings(GameTestHelper helper, String json, SettingsCheck check) throws Exception {
		helper.assertTrue(FMLEnvironment.dist == Dist.DEDICATED_SERVER && ClientModSettings.getInstance() == null,
				"Fixture requires a dedicated GameTest process without an initialized client store");
		Field singleton = ClientModSettings.class.getDeclaredField("instance");
		singleton.setAccessible(true);
		// under the run directory in build, as no temp file may go to drive C:
		Path directory = Files.createTempDirectory(Files.createDirectories(Path.of("tmp").toAbsolutePath()), "rotp-client-config-editor-");
		Path file = directory.resolve("client_settings.json");
		try {
			Files.writeString(file, json);
			ClientModSettings.init(file.toFile());
			check.run(file);
		}
		finally {
			singleton.set(null, null);
			Files.deleteIfExists(file);
			Files.delete(directory);
		}
	}

	@FunctionalInterface
	private interface SettingsCheck {
		void run(Path file) throws Exception;
	}
}
