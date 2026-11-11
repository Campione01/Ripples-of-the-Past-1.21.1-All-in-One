package rotp.core.config.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

	/**
	 * Before load the catalog exposes defaults; writes and previews require a loaded file. An add-on's rows follow the core's,
	 * then every Stand's rows of the Stands page.
	 */
	public static List<ConfigEntry> entries() {
		boolean loaded = JojoModConfig.COMMON_SPEC.isLoaded();
		var entries = new ArrayList<ConfigEntry>(VALUES.size());
		VALUES.forEach((key, value) -> entries.add(describe(key, value, loaded)));
		entries.addAll(AddonConfigCatalog.describe(AddonConfigCatalog.server(), Side.SERVER, false));
		entries.addAll(StandConfigCatalog.entries());
		return List.copyOf(entries);
	}

	public static ConfigEditResult set(String key, JsonElement value) {
		return apply(Collections.singletonMap(key, value));
	}

	public static ConfigEditResult reset(String key) {
		if (StandConfigKeys.isStandKey(key)) {
			JsonElement standDefault = StandConfigCatalog.defaultValue(key);
			return standDefault == null ? ConfigEditResult.error(Status.UNKNOWN_KEY, key) : set(key, standDefault);
		}
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
		defaults.putAll(StandConfigCatalog.groupDefaults(group));
		return defaults.isEmpty() ? ConfigEditResult.error(Status.UNKNOWN_GROUP, group) : edit(defaults, save);
	}

	public static ConfigEditResult preview(Map<String, JsonElement> replacements) {
		return edit(replacements, false);
	}

	public static ConfigEditResult apply(Map<String, JsonElement> replacements) {
		return edit(replacements, true);
	}

	/**
	 * A Stand row's obtaining joins the batch as the bannedStands value it asks for, its other rows are written to the Stand's
	 * files first; a spec that cannot be saved then puts those files back. Every Stand row a write moves is in the answer,
	 * so a stat another one clamped, and obtaining after a bannedStands edit, never wait for the next read.
	 */
	private static ConfigEditResult edit(Map<String, JsonElement> replacements, boolean save) {
		if (!JojoModConfig.COMMON_SPEC.isLoaded()) return ConfigEditResult.error(Status.UNAVAILABLE, null);
		var spec = new LinkedHashMap<String, JsonElement>();
		var standRows = new LinkedHashMap<String, JsonElement>();
		replacements.forEach((key, value) -> (StandConfigKeys.isStandKey(key) ? standRows : spec).put(key, value));
		StandConfigCatalog.Staged staged = StandConfigCatalog.Staged.NONE;
		if (!standRows.isEmpty()) {
			StandConfigCatalog.Stage stage = StandConfigCatalog.stage(standRows, spec.get(StandConfigCatalog.BANNED_STANDS));
			if (stage.refusal() != null) return stage.refusal();
			staged = stage.staged();
			spec.putAll(staged.specReplacements());
		}
		ConfigEditResult checked = SpecValues.edit(spec, CommonConfigEditor::target, false);
		if (checked.status() != Status.OK) return checked;
		if (!save) {
			var changes = new ArrayList<>(checked.changes());
			changes.addAll(staged.changes());
			return ConfigEditResult.ok(changes);
		}
		if (!staged.fileEdits().isEmpty() && StandConfigCatalog.unavailable() != null) {
			return ConfigEditResult.error(Status.UNAVAILABLE, null);
		}
		var touched = new LinkedHashSet<>(staged.stands());
		JsonElement banned = spec.get(StandConfigCatalog.BANNED_STANDS);
		if (banned != null) {
			touched.addAll(StandConfigCatalog.standsNamed(SpecValues.json(VALUES.get(StandConfigCatalog.BANNED_STANDS).get()), banned));
		}
		Map<String, JsonElement> before = StandConfigCatalog.values(touched);
		var previous = new LinkedHashMap<java.nio.file.Path, byte[]>();
		if (!staged.fileEdits().isEmpty() && !StandConfigCatalog.writeFiles(staged, previous)) {
			return ConfigEditResult.error(Status.SAVE_FAILED, null);
		}
		ConfigEditResult saved = SpecValues.edit(spec, CommonConfigEditor::target, true);
		if (saved.status() != Status.OK) {
			StandConfigCatalog.restoreFiles(previous);
			return saved;
		}
		StandConfigCatalog.applyLive(staged);
		var changes = new ArrayList<>(saved.changes());
		changes.addAll(StandConfigCatalog.movedRows(before, touched));
		return ConfigEditResult.ok(changes);
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
