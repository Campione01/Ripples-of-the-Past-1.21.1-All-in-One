package rotp.core.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.config.editor.CommonConfigEditor;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.core.JojoMod;

@GameTestHolder(CommonConfigEditorGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CommonConfigEditorGameTests {
	static final String NAMESPACE = "rotp_backend_config";
	private static final String RANGE = "Stand settings.Time Stop.timeStopChunkRange";
	private static final String DAMAGE = "Stand settings.Time Stop.timeStopDamageMultiplier";
	private static final String PROGRESSION = "Stand settings.Stand Progression.standExpMultiplier";
	private static final String BODY = "Stand settings.standBodyThroughWalls";

	private CommonConfigEditorGameTests() {}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void schemaReadsLegacyConfigWithoutWriting(GameTestHelper helper) throws IOException {
		ModConfigSpec.ConfigValue<String> body = raw(BODY);
		String previous = body.get();
		try {
			body.set(JojoModConfig.LEGACY_BODY_MODE_MANGA);
			JojoModConfig.COMMON_SPEC.save();
			Path path = configPath();
			byte[] before = Files.readAllBytes(path);
			var entries = CommonConfigEditor.entries();
			long expected = Arrays.stream(JojoModConfig.Common.class.getFields())
					.filter(field -> field.getType() == JojoModConfig.ConfigValue.class).count();
			helper.assertTrue(entries.size() == expected && entries.stream().map(ConfigEntry::key).distinct().count() == expected,
					"catalog must include each real COMMON entry once: " + entries.size() + " / " + expected);
			var describedBody = entry(entries, BODY);
			var describedRange = entry(entries, RANGE);
			var banned = entry(entries, "Stand settings.bannedStands");
			helper.assertTrue(describedBody.value().getAsString().equals(JojoModConfig.LEGACY_BODY_MODE_MANGA)
							&& describedBody.defaultValue().getAsString().equals(JojoModConfig.BODY_MODE_STANDARD)
							&& JojoModConfig.getCommonConfigInstance(false).manualStandPassesThroughBlocks(),
					"editor must read the legacy value without normalizing its bytes or gameplay");
			helper.assertTrue(describedRange.kind() == ConfigEntry.Kind.INTEGER && describedRange.bounds().minimum() == 0
							&& describedRange.defaultValue().getAsInt() == 12 && describedRange.synced()
							&& describedRange.side() == ConfigEntry.Side.SERVER,
					"metadata must describe the actual Time Stop setting");
			helper.assertTrue(banned.kind() == ConfigEntry.Kind.LIST && banned.elementKind() == ConfigEntry.Kind.STRING,
					"empty bannedStands default must still have a string element type");
			helper.assertTrue(Arrays.equals(before, Files.readAllBytes(path)), "reading the catalog must not write the TOML");
			helper.succeed();
		}
		finally {
			body.set(previous);
			JojoModConfig.COMMON_SPEC.save();
		}
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void invalidBatchLeavesBothRuntimeAndFileUnchanged(GameTestHelper helper) throws IOException {
		ModConfigSpec.ConfigValue<Integer> range = raw(RANGE);
		ModConfigSpec.ConfigValue<Double> damage = raw(DAMAGE);
		int previousRange = range.get();
		double previousDamage = damage.get();
		try {
			byte[] before = Files.readAllBytes(configPath());
			var batch = new LinkedHashMap<String, com.google.gson.JsonElement>();
			batch.put(DAMAGE, new JsonPrimitive(previousDamage == 0.5D ? 0.75D : 0.5D));
			batch.put(RANGE, new JsonPrimitive(-1));
			var rejected = CommonConfigEditor.apply(batch);
			helper.assertTrue(rejected.status() == Status.INVALID_VALUE && RANGE.equals(rejected.key()),
					"negative chunk range must be rejected by the actual spec");
			helper.assertTrue(range.get() == previousRange && damage.get() == previousDamage,
					"valid prefix of rejected batch was applied");
			helper.assertTrue(CommonConfigEditor.set(RANGE, new JsonPrimitive(1.5)).status() == Status.INVALID_VALUE
							&& CommonConfigEditor.set(RANGE, new JsonPrimitive("4")).status() == Status.INVALID_VALUE
							&& CommonConfigEditor.set(DAMAGE, new JsonPrimitive(Double.NaN)).status() == Status.INVALID_VALUE,
					"the editor must reject coercion, fractional integers and non-finite values");
			helper.assertTrue(CommonConfigEditor.set("Stand settings.Stand Progression.resolveLvlPoints",
							new JsonArray()).status() == Status.INVALID_VALUE,
					"list length must obey the NeoForge spec, not only its element validator");
			helper.assertTrue(Arrays.equals(before, Files.readAllBytes(configPath())), "invalid edit changed the TOML");
			helper.succeed();
		}
		finally {
			if (range.get() != previousRange || damage.get() != previousDamage) {
				range.set(previousRange);
				damage.set(previousDamage);
				JojoModConfig.COMMON_SPEC.save();
			}
		}
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void previewSetAndGroupResetUseTheLiveCommonFile(GameTestHelper helper) throws IOException {
		ModConfigSpec.ConfigValue<Integer> range = raw(RANGE);
		ModConfigSpec.ConfigValue<Double> damage = raw(DAMAGE);
		ModConfigSpec.ConfigValue<Double> progression = raw(PROGRESSION);
		int previousRange = range.get();
		double previousDamage = damage.get(), previousProgression = progression.get();
		try {
			Path path = configPath();
			byte[] before = Files.readAllBytes(path);
			var values = Map.<String, com.google.gson.JsonElement>of(RANGE, new JsonPrimitive(7), DAMAGE,
					new JsonPrimitive(0.625D), PROGRESSION, new JsonPrimitive(1.25D));
			var preview = CommonConfigEditor.preview(values);
			helper.assertTrue(preview.status() == Status.OK && Arrays.equals(before, Files.readAllBytes(path))
							&& range.get() == previousRange && damage.get() == previousDamage,
					"preview is read-only");
			helper.assertTrue(CommonConfigEditor.apply(values).status() == Status.OK
							&& JojoModConfig.getCommonConfigInstance(false).timeStopChunkRange.get() == 7,
					"editor did not reach the existing gameplay reader");
			try (var reader = Files.newBufferedReader(path)) {
				helper.assertTrue(new TomlParser().parse(reader).<Integer>get(List.of(RANGE.split("\\."))) == 7,
						"edit was not persisted to the existing TOML");
			}
			helper.assertTrue(CommonConfigEditor.resetGroup("Stand settings.Time Stop").status() == Status.OK
							&& range.get().equals(range.getDefault()) && damage.get().equals(damage.getDefault())
							&& progression.get() == 1.25D,
					"group reset must use the real defaults and leave a neighboring group unchanged");
			helper.assertTrue(CommonConfigEditor.reset(PROGRESSION).status() == Status.OK
							&& progression.get().equals(progression.getDefault()), "single reset did not use the spec default");
			helper.succeed();
		}
		finally {
			range.set(previousRange);
			damage.set(previousDamage);
			progression.set(previousProgression);
			JojoModConfig.COMMON_SPEC.save();
		}
	}

	private static ConfigEntry entry(List<ConfigEntry> entries, String key) {
		return entries.stream().filter(entry -> entry.key().equals(key)).findFirst().orElseThrow();
	}

	private static Path configPath() {
		return ModConfigs.getModConfigs(JojoMod.MOD_ID).stream()
				.filter(config -> config.getSpec() == JojoModConfig.COMMON_SPEC).findFirst().orElseThrow().getFullPath();
	}

	private static <T> ModConfigSpec.ConfigValue<T> raw(String key) {
		return JojoModConfig.COMMON_SPEC.getValues().get(List.of(key.split("\\.")));
	}
}
