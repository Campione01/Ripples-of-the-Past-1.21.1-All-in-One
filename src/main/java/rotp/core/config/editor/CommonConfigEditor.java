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
import com.google.gson.JsonElement;
import net.neoforged.neoforge.common.ModConfigSpec;
import rotp.core.JojoModConfig;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.ConfigEntry.Side;

/** Edits the core's existing COMMON config, and what add-ons give the server to own, on the logical server thread. */
public final class CommonConfigEditor {
	// These are the fields sent by JojoModConfig.Common.SyncedValues, not a new sync policy.
	private static final Set<String> SYNCED = Set.of(
			"keepStandOnDeath", "keepVampirismOnDeath", "keepHamonOnDeath", "dropStandDisc",
			"skipStandProgression", "resolveLvlPoints", "timeStopRangeChunks", "timeStopChargeSeconds", "standPowerMultiplier",
			"standResistanceMultiplier", "standBodyThroughWalls", "hamonPointsMultiplier",
			"breathingTrainingMultiplier", "breathingTrainingDeterioration", "breathingHamonStatGap",
			"hamonEnergyTicksDown", "mixHamonTechniques", "techniqueSkillRequirements", "maxBloodMultiplier",
			"bloodDrainMultiplier", "bloodTickDown", "vampirismCuringDuration", "standStamina", "soulAscension",
			"saveDestroyedBlocks", "endermenBeyondTimeSpace", "bannedStands", "standArrowMode", "arrowDurability",
			"arrowDurabilityBeetle");
	private static final Map<String, ModConfigSpec.ConfigValue<?>> VALUES = catalog();

	private CommonConfigEditor() {}

	/** Before load the catalog exposes defaults; writes and previews require a loaded file. An add-on's rows follow the core's. */
	public static List<ConfigEntry> entries() {
		boolean loaded = JojoModConfig.COMMON_SPEC.isLoaded();
		var entries = new ArrayList<ConfigEntry>(VALUES.size());
		VALUES.forEach((key, value) -> entries.add(describe(key, value, loaded)));
		entries.addAll(AddonConfigCatalog.describe(AddonConfigCatalog.server(), Side.SERVER, false));
		return List.copyOf(entries);
	}

	public static ConfigEditResult set(String key, JsonElement value) {
		return apply(Collections.singletonMap(key, value));
	}

	public static ConfigEditResult reset(String key) {
		var target = key == null ? null : target(key);
		return target == null ? ConfigEditResult.error(Status.UNKNOWN_KEY, key) : set(key, SpecValues.json(target.value().getDefault()));
	}

	public static ConfigEditResult resetGroup(String group) {
		return resetGroup(group, true);
	}

	/** {@code save} false only says what the reset would change. */
	public static ConfigEditResult resetGroup(String group, boolean save) {
		var defaults = new LinkedHashMap<String, JsonElement>();
		VALUES.forEach((key, value) -> {
			if (AddonConfigKeys.group(key).equals(group)) defaults.put(key, SpecValues.json(value.getDefault()));
		});
		defaults.putAll(AddonConfigCatalog.groupDefaults(AddonConfigCatalog.server(), group));
		return defaults.isEmpty() ? ConfigEditResult.error(Status.UNKNOWN_GROUP, group) : edit(defaults, save);
	}

	public static ConfigEditResult preview(Map<String, JsonElement> replacements) {
		return edit(replacements, false);
	}

	public static ConfigEditResult apply(Map<String, JsonElement> replacements) {
		return edit(replacements, true);
	}

	private static ConfigEditResult edit(Map<String, JsonElement> replacements, boolean save) {
		if (!JojoModConfig.COMMON_SPEC.isLoaded()) return ConfigEditResult.error(Status.UNAVAILABLE, null);
		return SpecValues.edit(replacements, CommonConfigEditor::target, save);
	}

	private static @Nullable SpecValues.Target target(String key) {
		var value = VALUES.get(key);
		return value != null ? new SpecValues.Target(JojoModConfig.COMMON_SPEC, value) : AddonConfigCatalog.server().get(key);
	}

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
		String leaf = value.getPath().getLast();
		return SpecValues.describe(key, value, loaded, Side.SERVER, SYNCED.contains(leaf), element -> listBounds(leaf, element),
				"standBodyThroughWalls".equals(leaf) ? List.of(JojoModConfig.BODY_MODE_STANDARD, JojoModConfig.BODY_MODE_PHASING) : List.of(),
				value.getSpec().getTranslationKey());
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
}
