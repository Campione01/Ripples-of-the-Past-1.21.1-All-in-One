package rotp.core.config.editor;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import rotp.core.config.editor.ConfigEditResult.Status;

/** Opt-in bundled presets. Loading the catalog does not edit either settings store. */
public final class CoreConfigPresets {
	private static final String RESOURCE = "/assets/jojo_ripples/config/presets.json";
	private static final Map<String, Preset> PRESETS = load();

	private CoreConfigPresets() {}

	public record Preset(String id, String name, Map<String, JsonElement> values) {
		public Preset {
			values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		}
	}

	public static List<Preset> entries() {
		return List.copyOf(PRESETS.values());
	}

	public static ConfigEditResult preview(String id) {
		var preset = PRESETS.get(id);
		return preset == null ? ConfigEditResult.error(Status.UNKNOWN_PRESET, id)
				: CommonConfigEditor.preview(preset.values());
	}

	public static ConfigEditResult apply(String id) {
		var preset = PRESETS.get(id);
		return preset == null ? ConfigEditResult.error(Status.UNKNOWN_PRESET, id)
				: CommonConfigEditor.apply(preset.values());
	}

	/** Resets only the keys in the chosen preset, to their spec defaults. */
	public static ConfigEditResult reset(String id) {
		var preset = PRESETS.get(id);
		if (preset == null) return ConfigEditResult.error(Status.UNKNOWN_PRESET, id);
		var defaults = new LinkedHashMap<String, JsonElement>();
		for (var field : CommonConfigEditor.entries()) {
			if (preset.values().containsKey(field.key())) defaults.put(field.key(), field.defaultValue());
		}
		for (String key : preset.values().keySet()) {
			if (!defaults.containsKey(key)) return ConfigEditResult.error(Status.UNKNOWN_KEY, key);
		}
		return CommonConfigEditor.apply(defaults);
	}

	private static Map<String, Preset> load() {
		var stream = CoreConfigPresets.class.getResourceAsStream(RESOURCE);
		if (stream == null) throw new IllegalStateException("Missing bundled core presets");
		try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			var catalog = new LinkedHashMap<String, Preset>();
			for (var member : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
				var profile = member.getValue().getAsJsonObject();
				var values = profile.getAsJsonObject("values").asMap();
				if (values.isEmpty() || values.values().stream().anyMatch(value -> !value.isJsonPrimitive())) {
					throw new IllegalStateException("Invalid bundled preset: " + member.getKey());
				}
				catalog.put(member.getKey(), new Preset(member.getKey(), profile.get("name").getAsString(), values));
			}
			return Collections.unmodifiableMap(catalog);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not read bundled core presets", exception);
		}
	}
}
