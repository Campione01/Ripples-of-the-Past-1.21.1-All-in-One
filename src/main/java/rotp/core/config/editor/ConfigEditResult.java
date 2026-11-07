package rotp.core.config.editor;

import java.util.List;
import javax.annotation.Nullable;

import com.google.gson.JsonElement;

public record ConfigEditResult(Status status, @Nullable String key, List<Change> changes) {
	public ConfigEditResult {
		changes = List.copyOf(changes);
	}

	public static ConfigEditResult ok(List<Change> changes) {
		return new ConfigEditResult(Status.OK, null, changes);
	}

	public static ConfigEditResult error(Status status, @Nullable String key) {
		return new ConfigEditResult(status, key, List.of());
	}

	public enum Status { OK, UNAVAILABLE, FORBIDDEN, UNKNOWN_KEY, UNKNOWN_GROUP, UNKNOWN_PRESET, INVALID_VALUE, SAVE_FAILED }
	public record Change(String key, JsonElement before, JsonElement after, ConfigEntry.Restart restart) {}
}
