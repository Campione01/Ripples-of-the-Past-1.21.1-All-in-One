package rotp.core.config.editor.client;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;

import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry;

/** The client's own settings: saved here and now, so there is never anything pending. */
public final class ClientConfigStore implements ConfigStore {
	private final Map<String, ConfigEntry> entries = new LinkedHashMap<>();
	private @Nullable Notice notice;

	public ClientConfigStore() {
		reload();
	}

	private void reload() {
		entries.clear();
		ClientConfigEditor.entries().forEach(entry -> entries.put(entry.key(), entry));
	}

	@Override
	public Access access() {
		return Access.EDITABLE;
	}

	@Override
	public List<ConfigEntry> entries() {
		return List.copyOf(entries.values());
	}

	@Override
	public @Nullable ConfigEntry entry(String key) {
		return entries.get(key);
	}

	@Override
	public @Nullable JsonElement pending(String key) {
		return null;
	}

	@Override
	public boolean busy() {
		return false;
	}

	@Override
	public boolean set(String key, JsonElement value) {
		return finish(ClientConfigEditor.set(key, value), key, true).status() == Status.OK;
	}

	@Override
	public boolean reset(String key) {
		return finish(ClientConfigEditor.reset(key), key, true).status() == Status.OK;
	}

	@Override
	public boolean resetKeys(List<String> keys, @Nullable Consumer<ConfigEditResult> done) {
		ConfigEditResult last = ConfigEditResult.ok(List.of());
		for (String key : keys) {
			last = finish(ClientConfigEditor.reset(key), key, false);
			if (last.status() != Status.OK) {
				break;
			}
		}
		if (done != null) {
			done.accept(last);
		}
		return last.status() == Status.OK;
	}

	@Override
	public boolean resetAll(Predicate<ConfigEntry> onPage, @Nullable Consumer<ConfigEditResult> done) {
		Set<String> groups = new LinkedHashSet<>();
		entries.values().stream().filter(entry -> onPage.test(entry) && ConfigWidgetRules.isShown(entry) && ConfigWidgetRules.isModified(entry))
				.forEach(entry -> groups.add(entry.group()));
		ConfigEditResult last = ConfigEditResult.ok(List.of());
		for (String group : groups) {
			last = finish(ClientConfigEditor.resetGroup(group), null, false);
			if (last.status() != Status.OK) {
				break;
			}
		}
		if (done != null) {
			done.accept(last);
		}
		return last.status() == Status.OK;
	}

	@Override
	public void tick(long nowMillis) {
	}

	@Override
	public @Nullable Notice takeNotice() {
		Notice taken = notice;
		notice = null;
		return taken;
	}

	private ConfigEditResult finish(ConfigEditResult result, @Nullable String key, boolean tell) {
		reload();
		if (result.status() != Status.OK) {
			notice = new Notice(Severity.ERROR, ConfigEditSession.TEXT + "status." + result.status().name().toLowerCase(Locale.ROOT),
					List.of(), key);
		}
		else if (tell) {
			notice = new Notice(Severity.OK, ConfigEditSession.TEXT + "status.saved", List.of(), key);
		}
		return result;
	}
}
