package rotp.core.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.config.editor.CommonConfigEditor;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.CoreConfigPresets;
import rotp.core.core.JojoMod;

@GameTestHolder("rotp_backend_config")
@PrefixGameTestTemplate(false)
public final class CoreConfigPresetsGameTests {
	private static final String NAMESPACE = "rotp_backend_config";
	private static final String RANGE = "Stand settings.Time Stop.timeStopChunkRange";
	private static final String DAMAGE = "Stand settings.Time Stop.timeStopDamageMultiplier";
	private static final String SENTINEL = "Stand settings.arrowDurability";

	private CoreConfigPresetsGameTests() {}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void presetCatalogAndPreviewDoNotApplyAnything(GameTestHelper helper) throws IOException {
		var common = JojoModConfig.getCommonConfigInstance(false);
		Map<String, JsonElement> beforeTest = snapshot(Set.of(RANGE, DAMAGE));
		try {
			helper.assertTrue(CommonConfigEditor.apply(Map.of(
					RANGE, new JsonPrimitive(17), DAMAGE, new JsonPrimitive(0.625D))).status() == Status.OK,
					"could not establish the custom starting config");
			var path = ModConfigs.getModConfigs(JojoMod.MOD_ID).stream().filter(c -> c.getSpec() == JojoModConfig.COMMON_SPEC)
					.findFirst().orElseThrow().getFullPath();
			byte[] before = Files.readAllBytes(path);
			var catalog = CoreConfigPresets.entries();
			helper.assertTrue(catalog.stream().map(CoreConfigPresets.Preset::id).toList().equals(List.of("adventure", "canon", "pvp")),
					"preset catalog changed");
			helper.assertTrue(catalog.stream().allMatch(preset -> preset.values().keySet().equals(catalog.getFirst().values().keySet())),
					"preset switching must address the same bounded key set");
			var preview = CoreConfigPresets.preview("pvp");
			helper.assertTrue(preview.status() == Status.OK && preview.changes().stream().anyMatch(change ->
					change.key().equals(RANGE) && change.before().getAsInt() == 17 && change.after().getAsInt() == 4),
					"preview did not compare the actual current value to the selected preset");
			helper.assertTrue(CoreConfigPresets.apply("nonexistent").status() == Status.UNKNOWN_PRESET
							&& CoreConfigPresets.reset("nonexistent").status() == Status.UNKNOWN_PRESET,
					"unknown preset must not select a fallback");
			helper.assertTrue(common.timeStopChunkRange.get() == 17 && common.timeStopDamageMultiplier.get() == 0.625D
							&& Arrays.equals(before, Files.readAllBytes(path)),
					"preview, catalog access, or unknown preset changed memory or disk");
			helper.succeed();
		}
		finally {
			CommonConfigEditor.apply(beforeTest);
		}
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void presetTransitionsAndResetOnlyTouchTheirDeclaredKeys(GameTestHelper helper) {
		var keys = new java.util.HashSet<>(CoreConfigPresets.entries().getFirst().values().keySet());
		keys.add(SENTINEL);
		var before = snapshot(keys);
		var common = JojoModConfig.getCommonConfigInstance(false);
		try {
			helper.assertTrue(CommonConfigEditor.set(SENTINEL, new JsonPrimitive(83)).status() == Status.OK,
					"could not establish an unrelated sentinel");
			helper.assertTrue(CoreConfigPresets.apply("adventure").status() == Status.OK
							&& common.standExpMultiplier.get() == 1.5D && !common.breathingTrainingDeterioration.get(),
					"Adventure values did not reach the live gameplay readers");
			helper.assertTrue(CoreConfigPresets.apply("pvp").status() == Status.OK && common.timeStopChunkRange.get() == 4
							&& common.timeStopDamageMultiplier.get() == 0.75D && common.standExpMultiplier.get() == 1.0D
							&& common.breathingTrainingDeterioration.get(),
					"PvP did not replace Adventure values and localize new Time Stops");
			helper.assertTrue(CoreConfigPresets.apply("canon").status() == Status.OK && common.timeStopChunkRange.get() == 12
							&& common.timeStopDamageMultiplier.get() == 1.0D && !common.skipStandProgression.get(),
					"Canon did not restore the seven stock values");
			helper.assertTrue(CoreConfigPresets.apply("pvp").status() == Status.OK && CoreConfigPresets.reset("pvp").status() == Status.OK
							&& common.timeStopChunkRange.get() == 12 && common.timeStopDamageMultiplier.get() == 1.0D
							&& common.arrowDurability.get() == 83,
					"reset must restore defaults only within the preset");
			helper.succeed();
		}
		finally {
			CommonConfigEditor.apply(before);
		}
	}

	private static Map<String, JsonElement> snapshot(Set<String> keys) {
		var out = new LinkedHashMap<String, JsonElement>();
		for (var entry : CommonConfigEditor.entries()) {
			if (keys.contains(entry.key())) out.put(entry.key(), entry.value());
		}
		if (!out.keySet().equals(keys)) throw new IllegalArgumentException("Preset addresses an unknown config key");
		return out;
	}
}
