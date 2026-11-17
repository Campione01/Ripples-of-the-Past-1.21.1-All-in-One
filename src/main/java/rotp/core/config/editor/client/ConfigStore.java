package rotp.core.config.editor.client;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;

import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEntry;

/** What the settings screen needs from a settings store, whether it answers at once or over the network. */
public interface ConfigStore {
	enum Access { EDITABLE, READ_ONLY, NO_WORLD, UNSUPPORTED, LOADING, NO_ANSWER }
	enum Severity { OK, ERROR }

	/** A line for the screen's status bar. {@code flashKey} names the setting it is about, if one. */
	record Notice(Severity severity, String textKey, List<Object> args, @Nullable String flashKey) {}

	Access access();

	List<ConfigEntry> entries();

	@Nullable ConfigEntry entry(String key);

	/** The value asked for and not accepted yet. A widget shows this as pending, never as the setting's value. */
	@Nullable JsonElement pending(String key);

	/** A request is on its way or waiting its turn. */
	boolean busy();

	/** False when this store does not take the edit: nothing was sent or saved. */
	boolean set(String key, JsonElement value);

	boolean reset(String key);

	/** Resets the settings named, one request each, then calls {@code done} with the last result. */
	boolean resetKeys(List<String> keys, @Nullable Consumer<ConfigEditResult> done);

	default boolean resetAll(@Nullable Consumer<ConfigEditResult> done) {
		return resetAll(entry -> true, done);
	}

	/** Resets what a page of the store shows: one store holds both the Gameplay and the Stands page. */
	boolean resetAll(Predicate<ConfigEntry> onPage, @Nullable Consumer<ConfigEditResult> done);

	void tick(long nowMillis);

	@Nullable Notice takeNotice();
}
