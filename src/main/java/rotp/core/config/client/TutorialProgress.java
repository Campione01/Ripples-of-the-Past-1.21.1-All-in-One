package rotp.core.config.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

/**
 * The tutorial tips the player has learned, kept beside the client settings in tutorial_completed.json as the official
 * version kept them: the settings file holds only what the settings page edits.
 */
public final class TutorialProgress {
	public static final String FILE_NAME = "tutorial_completed.json";
	private static final Logger LOGGER = LogUtils.getLogger();
	private static @Nullable TutorialProgress current;

	private final File file;
	private final Set<String> seen = new LinkedHashSet<>();

	public TutorialProgress(File file) {
		this.file = file;
		load();
	}

	/** The game's own, beside its settings file; null before the settings are loaded. */
	public static @Nullable TutorialProgress current() {
		ClientModSettings settings = ClientModSettings.getInstance();
		if (current == null && settings != null) {
			current = new TutorialProgress(new File(settings.directory(), FILE_NAME));
		}
		return current;
	}

	@ApiStatus.Internal
	public static void setCurrent(@Nullable TutorialProgress progress) {
		current = progress;
	}

	/** The tutorial was turned back on: every tip shows again (the official version's "Reset Tutorial"). */
	public static void resetCurrent() {
		TutorialProgress progress = current();
		if (progress != null) {
			progress.clear();
		}
	}

	public Set<String> seen() {
		return Collections.unmodifiableSet(seen);
	}

	public void add(String tip) {
		if (seen.add(tip)) {
			save();
		}
	}

	public void clear() {
		if (!seen.isEmpty()) {
			seen.clear();
			save();
		}
	}

	private void load() {
		if (!file.isFile()) {
			return;
		}
		try {
			JsonElement json = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
			if (json.isJsonArray()) {
				for (JsonElement element : json.getAsJsonArray()) {
					if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
						seen.add(element.getAsString());
					}
				}
			}
		}
		catch (IOException | RuntimeException exception) {
			LOGGER.warn("Could not read the tutorial progress {}", file, exception);
		}
	}

	private void save() {
		JsonArray json = new JsonArray();
		seen.forEach(json::add);
		try {
			File parent = file.getAbsoluteFile().getParentFile();
			if (parent != null) {
				Files.createDirectories(parent.toPath());
			}
			Files.writeString(file.toPath(), json.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			LOGGER.warn("Could not save the tutorial progress {}", file, exception);
		}
	}
}
