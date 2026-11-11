package rotp.core.config.editor.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;

import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEditResult.Change;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEditorPackets.Operation;
import rotp.core.config.editor.ConfigEntry;

/**
 * The screen's side of the server's settings: one request at a time, and a value counts only once the server has said
 * so. A write waits in {@link #pending(String)} until its reply; a refusal or a silence leaves the setting as it was.
 */
public final class ConfigEditSession implements ConfigStore {
	public static final long ANSWER_TIMEOUT_MILLIS = 5000;
	public static final String TEXT = "jojo_ripples.config.screen.";

	public interface Transport {
		boolean hasWorld();

		boolean supported();

		/** The request's id; 0 when the connection cannot take it; -1 when the value cannot be sent at all. */
		long send(Operation operation, String key, @Nullable JsonElement value);

		@Nullable Answer answer(long id);
	}

	public record Answer(boolean canEdit, ConfigEditResult result, List<ConfigEntry> entries) {}

	private record Job(Operation operation, String key, @Nullable JsonElement value, boolean silent,
			@Nullable Consumer<ConfigEditResult> done, List<String> pendingKeys) {}

	private final Transport transport;
	private final Supplier<List<ConfigEntry>> localEntries;
	private final Map<String, ConfigEntry> entries = new LinkedHashMap<>();
	private final Map<String, JsonElement> pending = new HashMap<>();
	private final Deque<Job> queue = new ArrayDeque<>();
	private Access access = Access.LOADING;
	private @Nullable Job inFlight;
	private long inFlightId;
	private long sentAt;
	private long now;
	private @Nullable Notice notice;

	public ConfigEditSession(Transport transport, Supplier<List<ConfigEntry>> localEntries) {
		this.transport = transport;
		this.localEntries = localEntries;
	}

	public void open(long nowMillis) {
		now = nowMillis;
		queue.clear();
		pending.clear();
		inFlight = null;
		if (!transport.hasWorld()) {
			access = Access.NO_WORLD;
			replaceEntries(localEntries.get());
		}
		else if (!transport.supported()) {
			access = Access.UNSUPPORTED;
			entries.clear();
		}
		else {
			access = Access.LOADING;
			enqueue(new Job(Operation.READ, "", null, true, null, List.of()));
		}
	}

	@Override
	public Access access() {
		return access;
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
		return pending.get(key);
	}

	@Override
	public boolean busy() {
		return inFlight != null || !queue.isEmpty();
	}

	@Override
	public boolean set(String key, JsonElement value) {
		if (access != Access.EDITABLE || !entries.containsKey(key)) {
			return false;
		}
		pending.put(key, value);
		enqueue(new Job(Operation.SET, key, value, false, null, List.of(key)));
		return true;
	}

	@Override
	public boolean reset(String key) {
		ConfigEntry entry = entries.get(key);
		if (access != Access.EDITABLE || entry == null) {
			return false;
		}
		pending.put(key, entry.defaultValue());
		enqueue(new Job(Operation.RESET, key, null, false, null, List.of(key)));
		return true;
	}

	@Override
	public boolean resetKeys(List<String> keys, @Nullable Consumer<ConfigEditResult> done) {
		if (access != Access.EDITABLE) {
			return false;
		}
		List<String> known = keys.stream().filter(entries::containsKey).toList();
		for (int i = 0; i < known.size(); i++) {
			String key = known.get(i);
			pending.put(key, entries.get(key).defaultValue());
			enqueue(new Job(Operation.RESET, key, null, true, i == known.size() - 1 ? done : null, List.of(key)));
		}
		return !known.isEmpty();
	}

	@Override
	public boolean resetAll(Predicate<ConfigEntry> onPage, @Nullable Consumer<ConfigEditResult> done) {
		if (access != Access.EDITABLE) {
			return false;
		}
		Set<String> groups = new LinkedHashSet<>();
		entries.values().stream().filter(entry -> onPage.test(entry) && ConfigWidgetRules.isShown(entry) && ConfigWidgetRules.isModified(entry))
				.forEach(entry -> groups.add(entry.group()));
		List<String> ordered = new ArrayList<>(groups);
		for (int i = 0; i < ordered.size(); i++) {
			String group = ordered.get(i);
			List<String> keys = entries.values().stream().filter(entry -> entry.group().equals(group))
					.map(ConfigEntry::key).toList();
			keys.forEach(key -> pending.put(key, entries.get(key).defaultValue()));
			enqueue(new Job(Operation.RESET_GROUP, group, null, true, i == ordered.size() - 1 ? done : null, keys));
		}
		return !ordered.isEmpty();
	}

	/** Only asks: the reply goes to {@code done} and no setting changes. */
	public boolean previewPreset(String id, Consumer<ConfigEditResult> done) {
		if (access != Access.EDITABLE) {
			return false;
		}
		enqueue(new Job(Operation.PREVIEW_PRESET, id, null, true, done, List.of()));
		return true;
	}

	public boolean applyPreset(String id, Consumer<ConfigEditResult> done) {
		if (access != Access.EDITABLE) {
			return false;
		}
		enqueue(new Job(Operation.APPLY_PRESET, id, null, true, done, List.of()));
		return true;
	}

	public boolean resetPreset(String id, @Nullable Consumer<ConfigEditResult> done) {
		if (access != Access.EDITABLE) {
			return false;
		}
		enqueue(new Job(Operation.RESET_PRESET, id, null, true, done, List.of()));
		return true;
	}

	public void retry(long nowMillis) {
		open(nowMillis);
	}

	@Override
	public void tick(long nowMillis) {
		now = nowMillis;
		if (inFlight != null) {
			Answer answer = transport.answer(inFlightId);
			if (answer != null) {
				finish(answer);
			}
			else if (now - sentAt >= ANSWER_TIMEOUT_MILLIS) {
				timedOut();
			}
		}
		pump();
	}

	@Override
	public @Nullable Notice takeNotice() {
		Notice taken = notice;
		notice = null;
		return taken;
	}

	private void enqueue(Job job) {
		queue.add(job);
		pump();
	}

	private void pump() {
		while (inFlight == null && !queue.isEmpty()) {
			Job job = queue.poll();
			long id = transport.send(job.operation(), job.key(), job.value());
			if (id > 0) {
				inFlight = job;
				inFlightId = id;
				sentAt = now;
				return;
			}
			job.pendingKeys().forEach(pending::remove);
			Status status = id < 0 ? Status.INVALID_VALUE : Status.UNAVAILABLE;
			refused(job, status);
			if (job.done() != null) {
				job.done().accept(ConfigEditResult.error(status, job.key()));
			}
		}
	}

	private void finish(Answer answer) {
		Job job = inFlight;
		inFlight = null;
		job.pendingKeys().forEach(pending::remove);
		ConfigEditResult result = answer.result();
		if (job.operation() == Operation.READ) {
			if (result.status() == Status.OK) {
				replaceEntries(answer.entries());
				access = answer.canEdit() ? Access.EDITABLE : Access.READ_ONLY;
			}
			else {
				access = Access.NO_ANSWER;
			}
		}
		else if (result.status() == Status.OK) {
			if (job.operation() != Operation.PREVIEW_PRESET) {
				result.changes().forEach(this::accept);
			}
			if (!job.silent()) {
				notice = new Notice(Severity.OK, TEXT + "status.saved", List.of(), job.key());
			}
		}
		else {
			if (result.status() == Status.FORBIDDEN || !answer.canEdit()) {
				access = Access.READ_ONLY;
				dropQueuedWrites();
			}
			refused(job, result.status());
		}
		if (job.done() != null) {
			job.done().accept(result);
		}
	}

	private void timedOut() {
		Job job = inFlight;
		inFlight = null;
		job.pendingKeys().forEach(pending::remove);
		notice = new Notice(Severity.ERROR, TEXT + "status.no_answer", List.of(), flashKey(job));
		if (job.operation() == Operation.READ) {
			access = Access.NO_ANSWER;
		}
		else {
			// A write the server may have taken after all: ask what it holds now.
			queue.addFirst(new Job(Operation.READ, "", null, true, null, List.of()));
		}
		if (job.done() != null) {
			job.done().accept(ConfigEditResult.error(Status.UNAVAILABLE, job.key()));
		}
	}

	private void dropQueuedWrites() {
		for (Job job : queue) {
			job.pendingKeys().forEach(pending::remove);
			if (job.done() != null) {
				job.done().accept(ConfigEditResult.error(Status.FORBIDDEN, job.key()));
			}
		}
		queue.clear();
	}

	private void refused(Job job, Status status) {
		notice = new Notice(Severity.ERROR, TEXT + "status." + status.name().toLowerCase(Locale.ROOT), List.of(), flashKey(job));
	}

	private static @Nullable String flashKey(Job job) {
		return job.pendingKeys().size() == 1 ? job.pendingKeys().getFirst() : null;
	}

	private void accept(Change change) {
		ConfigEntry entry = entries.get(change.key());
		if (entry != null) {
			entries.put(change.key(), withValue(entry, change.after()));
		}
	}

	private void replaceEntries(List<ConfigEntry> fresh) {
		entries.clear();
		fresh.forEach(entry -> entries.put(entry.key(), entry));
	}

	private static ConfigEntry withValue(ConfigEntry entry, JsonElement value) {
		return new ConfigEntry(entry.key(), entry.group(), entry.side(), entry.kind(), entry.elementKind(),
				entry.defaultValue(), value, entry.bounds(), entry.listSize(), entry.choices(), entry.restart(),
				entry.synced(), entry.translationKey());
	}
}
