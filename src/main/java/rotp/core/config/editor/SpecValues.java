package rotp.core.config.editor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.neoforged.neoforge.common.ModConfigSpec;
import rotp.core.config.editor.ConfigEditResult.Change;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.ConfigEntry.Restart;
import rotp.core.config.editor.ConfigEntry.Side;
import rotp.core.core.JojoMod;

/** What both editors do with a value of a {@link ModConfigSpec}: describe it, parse what was sent for it, assign it, save it. */
public final class SpecValues {
	public record Target(ModConfigSpec spec, ModConfigSpec.ConfigValue<?> value) {}

	private record Edit(Target target, Object previous, Object next, Change change) {}

	private SpecValues() {}

	public static ConfigEntry describe(String key, ModConfigSpec.ConfigValue<?> value, boolean loaded, Side side, boolean synced,
			Function<Kind, Bounds> itemBounds, List<String> ownChoices, @Nullable String translationKey) {
		Object defaultValue = value.getDefault();
		var spec = value.getSpec();
		Kind kind = kind(defaultValue);
		Kind element = kind == Kind.LIST ? listKind((List<?>) defaultValue) : null;
		Bounds size = spec instanceof ModConfigSpec.ListValueSpec list ? bounds(list.getSizeRange()) : null;
		Bounds range = kind == Kind.LIST ? itemBounds.apply(element) : bounds(spec.getRange());
		List<String> choices = defaultValue instanceof Enum<?> choice
				? java.util.Arrays.stream(choice.getDeclaringClass().getEnumConstants()).map(Enum::name).toList() : ownChoices;
		// the items a server value's source offers travel with it; a client value is the client's own, offered by nobody
		List<ConfigChoices.Choice> offered = kind == Kind.LIST && side == Side.SERVER ? ConfigChoices.offered(value) : null;
		return new ConfigEntry(key, AddonConfigKeys.group(key), side, kind, element, json(defaultValue),
				json(loaded ? value.getRaw() : defaultValue), range, size, choices, restart(value), synced, translationKey, offered);
	}

	/** Validates the whole batch before anything moves, and saves every spec it touched once. A failed save puts all of them back. */
	public static ConfigEditResult edit(Map<String, JsonElement> replacements, Function<String, Target> targets, boolean save) {
		var staged = new ArrayList<Edit>();
		for (var entry : replacements.entrySet()) {
			String key = entry.getKey();
			Target target = key == null ? null : targets.apply(key);
			if (target == null) return ConfigEditResult.error(Status.UNKNOWN_KEY, key);
			if (!target.spec().isLoaded()) return ConfigEditResult.error(Status.UNAVAILABLE, key);
			var value = target.value();
			Object parsed;
			try {
				parsed = parse(entry.getValue(), value);
			}
			catch (IllegalArgumentException | ArithmeticException invalid) {
				return ConfigEditResult.error(Status.INVALID_VALUE, key);
			}
			// the stored value: a value read at a restart keeps its old one in get() until then (ModConfigSpec.ConfigValue.set)
			Object previous = value.getRaw();
			if (!parsed.equals(previous)) {
				staged.add(new Edit(target, previous, parsed, new Change(key, json(previous), json(parsed), restart(value))));
			}
		}
		if (save && !staged.isEmpty() && !saveAll(staged)) {
			return ConfigEditResult.error(Status.SAVE_FAILED, null);
		}
		return ConfigEditResult.ok(staged.stream().map(Edit::change).toList());
	}

	private static boolean saveAll(List<Edit> staged) {
		staged.forEach(change -> assign(change.target().value(), change.next()));
		Set<ModConfigSpec> touched = new LinkedHashSet<>();
		staged.forEach(change -> touched.add(change.target().spec()));
		List<ModConfigSpec> saved = new ArrayList<>();
		try {
			for (ModConfigSpec spec : touched) {
				// FML writes the existing file atomically and fires the usual reload/sync event.
				spec.save();
				saved.add(spec);
			}
			return true;
		}
		// the reload event runs other mods' listeners, so more than a write can fail here
		catch (RuntimeException error) {
			JojoMod.getLogger().error("Could not save a config the editor changed", error);
			staged.forEach(change -> assign(change.target().value(), change.previous()));
			for (ModConfigSpec spec : saved) {
				try {
					spec.save();
				}
				catch (RuntimeException again) {
					JojoMod.getLogger().error("Could not put back a config file after a failed save", again);
				}
			}
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private static void assign(ModConfigSpec.ConfigValue<?> value, Object parsed) {
		((ModConfigSpec.ConfigValue<Object>) value).set(parsed);
	}

	public static Restart restart(ModConfigSpec.ConfigValue<?> value) {
		return Restart.valueOf(value.getSpec().restartType().name());
	}

	private static @Nullable Bounds bounds(@Nullable ModConfigSpec.Range<?> range) {
		return range != null && range.getMin() instanceof Number min && range.getMax() instanceof Number max
				? new Bounds(min.doubleValue(), max.doubleValue()) : null;
	}

	private static Object parse(@Nullable JsonElement input, ModConfigSpec.ConfigValue<?> value) {
		Object defaultValue = value.getDefault();
		Kind kind = kind(defaultValue);
		Object parsed;
		if (kind == Kind.LIST) {
			if (input == null || !input.isJsonArray()) throw new IllegalArgumentException("Expected a list");
			JsonArray array = input.getAsJsonArray();
			if (value.getSpec() instanceof ModConfigSpec.ListValueSpec list && list.getSizeRange() != null
					&& !list.getSizeRange().test(array.size())) throw new IllegalArgumentException("Invalid list size");
			Kind element = listKind((List<?>) defaultValue);
			var items = new ArrayList<>(array.size());
			for (JsonElement item : array) items.add(parsePrimitive(item, element, null));
			parsed = List.copyOf(items);
			if (!ConfigChoices.allows(value, items)) throw new IllegalArgumentException("An item the server does not offer");
		}
		else {
			parsed = parsePrimitive(input, kind, defaultValue);
		}
		if (!value.getSpec().test(parsed)) throw new IllegalArgumentException("Outside the config spec");
		return parsed;
	}

	private static Object parsePrimitive(@Nullable JsonElement input, Kind kind, @Nullable Object defaultValue) {
		if (input == null || !input.isJsonPrimitive()) throw new IllegalArgumentException("Expected a primitive");
		JsonPrimitive raw = input.getAsJsonPrimitive();
		switch (kind) {
			case BOOLEAN: if (raw.isBoolean()) return raw.getAsBoolean(); break;
			case STRING: if (raw.isString()) return raw.getAsString(); break;
			case INTEGER: if (raw.isNumber()) return raw.getAsBigDecimal().intValueExact(); break;
			case DECIMAL:
				if (raw.isNumber() && Double.isFinite(raw.getAsDouble())) return raw.getAsDouble();
				break;
			case ENUM:
				if (raw.isString() && defaultValue instanceof Enum<?> value) {
					for (Enum<?> option : value.getDeclaringClass().getEnumConstants()) {
						if (option.name().equals(raw.getAsString())) return option;
					}
				}
				break;
			default: break;
		}
		throw new IllegalArgumentException("Wrong value type");
	}

	/** Throws IllegalStateException for a value the catalog does not know. */
	public static Kind kind(Object value) {
		if (value instanceof Boolean) return Kind.BOOLEAN;
		if (value instanceof Integer) return Kind.INTEGER;
		if (value instanceof Double) return Kind.DECIMAL;
		if (value instanceof String) return Kind.STRING;
		if (value instanceof Enum<?>) return Kind.ENUM;
		if (value instanceof List<?>) return Kind.LIST;
		throw new IllegalStateException("Unknown config value type: " + value);
	}

	public static Kind listKind(List<?> values) {
		// An empty default says nothing of its items. The core's only one is the list of banned Stand IDs.
		return values.isEmpty() ? Kind.STRING : kind(values.getFirst());
	}

	public static JsonElement json(Object value) {
		if (value instanceof Boolean bool) return new JsonPrimitive(bool);
		if (value instanceof Number number) return new JsonPrimitive(number);
		if (value instanceof Enum<?> option) return new JsonPrimitive(option.name());
		if (value instanceof String string) return new JsonPrimitive(string);
		if (value instanceof List<?> list) {
			JsonArray array = new JsonArray(list.size());
			list.forEach(item -> array.add(json(item)));
			return array;
		}
		throw new IllegalStateException("Unknown config value: " + value);
	}
}
