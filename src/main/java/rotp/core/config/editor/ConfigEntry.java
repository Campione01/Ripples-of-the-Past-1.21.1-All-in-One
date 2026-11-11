package rotp.core.config.editor;

import java.util.List;
import javax.annotation.Nullable;

import com.google.gson.JsonElement;

/**
 * One editable setting, not a copy of the underlying configuration store. {@code listChoices} is set for a list whose
 * items the server offers ({@link ConfigChoices}), the screen's tick boxes; null for every other setting.
 */
public record ConfigEntry(String key, String group, Side side, Kind kind, @Nullable Kind elementKind,
		JsonElement defaultValue, JsonElement value, @Nullable Bounds bounds, @Nullable Bounds listSize,
		List<String> choices, Restart restart, boolean synced, @Nullable String translationKey,
		@Nullable List<ConfigChoices.Choice> listChoices) {

	public ConfigEntry {
		choices = List.copyOf(choices);
		listChoices = listChoices != null ? List.copyOf(listChoices) : null;
	}

	public ConfigEntry(String key, String group, Side side, Kind kind, @Nullable Kind elementKind,
			JsonElement defaultValue, JsonElement value, @Nullable Bounds bounds, @Nullable Bounds listSize,
			List<String> choices, Restart restart, boolean synced, @Nullable String translationKey) {
		this(key, group, side, kind, elementKind, defaultValue, value, bounds, listSize, choices, restart, synced, translationKey, null);
	}

	public enum Side { SERVER, CLIENT }
	public enum Kind { BOOLEAN, INTEGER, DECIMAL, STRING, ENUM, LIST }
	public enum Restart { NONE, WORLD, GAME }

	public record Bounds(double minimum, double maximum) {
		public boolean contains(double value) {
			return Double.isFinite(value) && value >= minimum && value <= maximum;
		}
	}
}
