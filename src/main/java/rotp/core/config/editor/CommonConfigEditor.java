package rotp.core.config.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.WritingException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.neoforged.neoforge.common.ModConfigSpec;
import rotp.core.JojoModConfig;
import rotp.core.config.editor.ConfigEditResult.Change;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.ConfigEntry.Restart;
import rotp.core.config.editor.ConfigEntry.Side;
import rotp.core.core.JojoMod;

/** Edits the core's existing COMMON config on the logical server thread. */
public final class CommonConfigEditor {
	// These are the fields sent by JojoModConfig.Common.SyncedValues, not a new sync policy.
	private static final Set<String> SYNCED = Set.of(
			"keepStandOnDeath", "keepVampirismOnDeath", "keepHamonOnDeath", "dropStandDisc",
			"skipStandProgression", "resolveLvlPoints", "timeStopRangeChunks", "standPowerMultiplier",
			"standResistanceMultiplier", "standBodyThroughWalls", "hamonPointsMultiplier",
			"breathingTrainingMultiplier", "breathingTrainingDeterioration", "breathingHamonStatGap",
			"hamonEnergyTicksDown", "mixHamonTechniques", "techniqueSkillRequirements", "maxBloodMultiplier",
			"bloodDrainMultiplier", "bloodTickDown", "vampirismCuringDuration", "standStamina", "soulAscension",
			"saveDestroyedBlocks", "endermenBeyondTimeSpace", "bannedStands", "standArrowMode", "arrowDurability",
			"arrowDurabilityBeetle");
	private static final Map<String, ModConfigSpec.ConfigValue<?>> VALUES = catalog();

	private CommonConfigEditor() {}

	/** Before load the catalog exposes defaults; writes and previews require a loaded file. */
	public static List<ConfigEntry> entries() {
		boolean loaded = JojoModConfig.COMMON_SPEC.isLoaded();
		return VALUES.entrySet().stream().map(entry -> describe(entry.getKey(), entry.getValue(), loaded)).toList();
	}

	public static ConfigEditResult set(String key, JsonElement value) {
		return apply(Collections.singletonMap(key, value));
	}

	public static ConfigEditResult reset(String key) {
		var value = key == null ? null : VALUES.get(key);
		return value == null ? ConfigEditResult.error(Status.UNKNOWN_KEY, key) : set(key, json(value.getDefault()));
	}

	public static ConfigEditResult resetGroup(String group) {
		var defaults = new LinkedHashMap<String, JsonElement>();
		VALUES.forEach((key, value) -> {
			if (group(key).equals(group)) defaults.put(key, json(value.getDefault()));
		});
		return defaults.isEmpty() ? ConfigEditResult.error(Status.UNKNOWN_GROUP, group) : apply(defaults);
	}

	public static ConfigEditResult preview(Map<String, JsonElement> replacements) {
		return edit(replacements, false);
	}

	public static ConfigEditResult apply(Map<String, JsonElement> replacements) {
		return edit(replacements, true);
	}

	private static ConfigEditResult edit(Map<String, JsonElement> replacements, boolean save) {
		if (!JojoModConfig.COMMON_SPEC.isLoaded()) return ConfigEditResult.error(Status.UNAVAILABLE, null);
		var staged = new ArrayList<Edit>();
		for (var entry : replacements.entrySet()) {
			String key = entry.getKey();
			var value = key == null ? null : VALUES.get(key);
			if (value == null) return ConfigEditResult.error(Status.UNKNOWN_KEY, key);
			Object parsed;
			try {
				parsed = parse(entry.getValue(), value);
			}
			catch (IllegalArgumentException | ArithmeticException invalid) {
				return ConfigEditResult.error(Status.INVALID_VALUE, key);
			}
			Object previous = value.get();
			if (!parsed.equals(previous)) {
				staged.add(new Edit(value, previous, parsed,
						new Change(key, json(previous), json(parsed), restart(value))));
			}
		}
		if (save && !staged.isEmpty()) {
			staged.forEach(change -> assign(change.value(), change.next()));
			try {
				// FML writes the existing file atomically and fires the usual reload/sync event.
				JojoModConfig.COMMON_SPEC.save();
			}
			catch (WritingException error) {
				staged.forEach(change -> assign(change.value(), change.previous()));
				JojoMod.getLogger().error("Could not save the core common config", error);
				return ConfigEditResult.error(Status.SAVE_FAILED, null);
			}
		}
		return ConfigEditResult.ok(staged.stream().map(Edit::change).toList());
	}

	@SuppressWarnings("unchecked")
	private static void assign(ModConfigSpec.ConfigValue<?> value, Object parsed) {
		((ModConfigSpec.ConfigValue<Object>) value).set(parsed);
	}

	private record Edit(ModConfigSpec.ConfigValue<?> value, Object previous, Object next, Change change) {}

	private static Map<String, ModConfigSpec.ConfigValue<?>> catalog() {
		var values = new TreeMap<String, ModConfigSpec.ConfigValue<?>>();
		collect(JojoModConfig.COMMON_SPEC.getValues(), values);
		return Collections.unmodifiableMap(values);
	}

	private static void collect(UnmodifiableConfig config, Map<String, ModConfigSpec.ConfigValue<?>> values) {
		for (Object child : config.valueMap().values()) {
			if (child instanceof UnmodifiableConfig section) collect(section, values);
			else if (child instanceof ModConfigSpec.ConfigValue<?> value) {
				String path = String.join(".", value.getPath());
				if (values.put(path, value) != null) throw new IllegalStateException("Duplicate common key: " + path);
			}
			else throw new IllegalStateException("Unexpected entry in the core common config spec");
		}
	}

	private static ConfigEntry describe(String key, ModConfigSpec.ConfigValue<?> value, boolean loaded) {
		Object defaultValue = value.getDefault();
		var spec = value.getSpec();
		Kind kind = kind(defaultValue);
		Kind element = kind == Kind.LIST ? listKind((List<?>) defaultValue) : null;
		String leaf = value.getPath().getLast();
		Bounds size = spec instanceof ModConfigSpec.ListValueSpec list ? bounds(list.getSizeRange()) : null;
		Bounds range = kind == Kind.LIST ? listBounds(leaf, element) : bounds(spec.getRange());
		return new ConfigEntry(key, group(key), Side.SERVER, kind, element, json(defaultValue),
				json(loaded ? value.get() : defaultValue), range, size, choices(leaf, defaultValue), restart(value),
				SYNCED.contains(leaf), spec.getTranslationKey());
	}

	private static Restart restart(ModConfigSpec.ConfigValue<?> value) {
		return Restart.valueOf(value.getSpec().restartType().name());
	}

	private static String group(String key) {
		int dot = key.lastIndexOf('.');
		return dot < 0 ? "" : key.substring(0, dot);
	}

	private static @Nullable Bounds bounds(@Nullable ModConfigSpec.Range<?> range) {
		return range != null && range.getMin() instanceof Number min && range.getMax() instanceof Number max
				? new Bounds(min.doubleValue(), max.doubleValue()) : null;
	}

	private static @Nullable Bounds listBounds(String key, Kind kind) {
		if (kind == Kind.INTEGER) return new Bounds(0, Integer.MAX_VALUE);
		if (kind == Kind.DECIMAL) {
			double minimum = switch (key) {
				case "resolveLvlPoints", "maxBloodMultiplier" -> Double.MIN_VALUE;
				default -> 0;
			};
			return new Bounds(minimum, Double.MAX_VALUE);
		}
		return null;
	}

	private static List<String> choices(String key, Object defaultValue) {
		if (defaultValue instanceof Enum<?> choice) {
			return java.util.Arrays.stream(choice.getDeclaringClass().getEnumConstants()).map(Enum::name).toList();
		}
		if ("standBodyThroughWalls".equals(key)) {
			return List.of(JojoModConfig.BODY_MODE_STANDARD, JojoModConfig.BODY_MODE_PHASING);
		}
		return List.of();
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

	private static Kind kind(Object value) {
		if (value instanceof Boolean) return Kind.BOOLEAN;
		if (value instanceof Integer) return Kind.INTEGER;
		if (value instanceof Double) return Kind.DECIMAL;
		if (value instanceof String) return Kind.STRING;
		if (value instanceof Enum<?>) return Kind.ENUM;
		if (value instanceof List<?>) return Kind.LIST;
		throw new IllegalStateException("Unknown config value type: " + value);
	}

	private static Kind listKind(List<?> values) {
		// The only empty list in the current schema is the list of banned Stand IDs.
		return values.isEmpty() ? Kind.STRING : kind(values.getFirst());
	}

	private static JsonElement json(Object value) {
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
