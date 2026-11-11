package rotp.core.config.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import rotp.core.JojoModConfig;
import rotp.core.command.commands.JojoConfigCommand;
import rotp.core.config.editor.ConfigEditResult.Change;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.ConfigEntry.Restart;
import rotp.core.config.editor.ConfigEntry.Side;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.standpower.StandSpecials;
import rotp.core.powersystem.standpower.StandSpecials.Special;
import rotp.core.powersystem.standpower.StandStats.StatWithValue;
import rotp.core.powersystem.standpower.StandTuning;
import rotp.core.powersystem.standpower.StandTuning.MiningTier;
import rotp.core.powersystem.standpower.StandTuning.Percent;
import rotp.core.powersystem.standpower.datapack.DataDrivenStandsLoader;
import rotp.core.powersystem.standpower.type.StandType;

/**
 * The Stands page's rows: for every registered Stand whether it can be obtained, its stats, its shares of damage, cooldowns,
 * stamina and block breaking, and its special values. Obtaining is the core's bannedStands list seen per Stand; the rest is
 * the Stand's files in the world's jojoconfig pack, the files /jojoconfig stand_stats writes and /reload reads, so the
 * command, a hand edit and this page keep one store. A value back at its default leaves the file, so an untouched Stand has none.
 */
public final class StandConfigCatalog {
	public static final String TEXT = "jojo_ripples.config.stand.";
	public static final String OBTAINABLE = "obtainable";
	public static final String STATS_MEMBER = "stats";
	public static final String BANNED_STANDS = "Stand settings.bannedStands";
	private static final double STAT_MAX = 1000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Where a row lives: the member file and the path in it, or nowhere for obtaining (bannedStands). */
	private record Row(String field, Kind kind, @Nullable Bounds bounds, List<String> choices, String translationKey,
			@Nullable String member, List<String> path, Function<StandType, JsonElement> value,
			Function<StandType, JsonElement> defaultValue) {}

	/** One file edit: a path set to a value, or removed when the value is the default. */
	record FileEdit(ResourceLocation standId, String member, List<String> path, @Nullable JsonElement value) {}

	/** A checked batch of Stand rows: the bannedStands value obtaining asks for, the file edits, the rows' own changes. */
	public record Staged(Map<String, JsonElement> specReplacements, List<FileEdit> fileEdits, List<Change> changes,
			Set<ResourceLocation> stands) {
		public static final Staged NONE = new Staged(Map.of(), List.of(), List.of(), Set.of());
	}

	/** A batch staged, or the refusal of its first row that cannot be. */
	public record Stage(@Nullable ConfigEditResult refusal, Staged staged) {}

	private StandConfigCatalog() {}

	public static List<StandType> stands() {
		return JojoRegistries.DEFAULT_STANDS_REG.stream().filter(StandType::isEnabled)
				.sorted(Comparator.comparing(stand -> stand.getId().toString())).toList();
	}

	public static List<ConfigEntry> entries() {
		List<ConfigEntry> entries = new ArrayList<>();
		for (StandType stand : stands()) {
			entries.addAll(entries(stand));
		}
		return entries;
	}

	public static List<ConfigEntry> entries(StandType stand) {
		List<ConfigEntry> entries = new ArrayList<>();
		for (Row row : rows(stand)) {
			entries.add(entry(stand, row));
		}
		return entries;
	}

	@Nullable
	public static ConfigEntry entry(String key) {
		ResourceLocation id = StandConfigKeys.standId(key);
		StandType stand = id != null ? JojoRegistries.DEFAULT_STANDS_REG.get(id) : null;
		Row row = stand != null ? row(stand, StandConfigKeys.field(key)) : null;
		return row != null ? entry(stand, row) : null;
	}

	private static ConfigEntry entry(StandType stand, Row row) {
		return new ConfigEntry(StandConfigKeys.key(stand.getId(), row.field()), StandConfigKeys.group(stand.getId()), Side.SERVER,
				row.kind(), null, row.defaultValue().apply(stand), row.value().apply(stand), row.bounds(), null, row.choices(),
				Restart.NONE, false, row.translationKey());
	}

	/** Every row of the Stand at its default. */
	public static Map<String, JsonElement> groupDefaults(String group) {
		Map<String, JsonElement> defaults = new LinkedHashMap<>();
		ResourceLocation id = StandConfigKeys.standId(group);
		StandType stand = id != null && group.indexOf('/') < 0 ? JojoRegistries.DEFAULT_STANDS_REG.get(id) : null;
		if (stand != null) {
			for (Row row : rows(stand)) {
				defaults.put(StandConfigKeys.key(stand.getId(), row.field()), row.defaultValue().apply(stand));
			}
		}
		return defaults;
	}

	@Nullable
	public static JsonElement defaultValue(String key) {
		ConfigEntry entry = entry(key);
		return entry != null ? entry.defaultValue() : null;
	}

	private static List<Row> rows(StandType stand) {
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(OBTAINABLE, Kind.BOOLEAN, null, List.of(), TEXT + OBTAINABLE, null, List.of(),
				type -> new JsonPrimitive(!JojoModConfig.getCommonConfigInstance(false).isStandBanned(type.getId())),
				type -> new JsonPrimitive(true)));
		rows.add(stat(StatWithValue.RANDOM_WEIGHT, 0));
		for (StatWithValue stat : List.of(StatWithValue.POWER, StatWithValue.POWER_MAX, StatWithValue.SPEED, StatWithValue.SPEED_MAX,
				StatWithValue.DURABILITY, StatWithValue.DURABILITY_MAX, StatWithValue.PRECISION, StatWithValue.PRECISION_MAX)) {
			rows.add(stat(stat, 0));
		}
		rows.add(stat(StatWithValue.RANGE_EFFECTIVE, 1));
		rows.add(stat(StatWithValue.RANGE_MAX, 1));
		for (Percent percent : Percent.values()) {
			if (percent != Percent.MINING_STRENGTH) {
				rows.add(percent(percent));
			}
		}
		List<String> tiers = java.util.Arrays.stream(MiningTier.values()).map(MiningTier::id).toList();
		rows.add(new Row(StandTuning.MINING_TIER, Kind.STRING, null, tiers, TEXT + StandTuning.MINING_TIER, StandTuning.MEMBER,
				List.of(StandTuning.MINING_TIER), type -> new JsonPrimitive(type.getTuning().miningTier().id()),
				type -> new JsonPrimitive(MiningTier.BY_STRENGTH.id())));
		rows.add(percent(Percent.MINING_STRENGTH));
		for (Special special : StandSpecials.of(stand.getId())) {
			Kind kind = special.integer() ? Kind.INTEGER : Kind.DECIMAL;
			rows.add(new Row(StandConfigKeys.SPECIAL + special.key(), kind, new Bounds(special.min(), special.max()), List.of(),
					TEXT + StandConfigKeys.SPECIAL + special.key(), StandTuning.MEMBER, List.of(StandTuning.SPECIAL, special.key()),
					type -> number(kind, special.get(type)), type -> number(kind, special.defaultValue())));
		}
		return rows;
	}

	private static Row stat(StatWithValue stat, double minimum) {
		return new Row(stat.nameInJson(), Kind.DECIMAL, new Bounds(minimum, STAT_MAX), List.of(), TEXT + stat.nameInJson(),
				STATS_MEMBER, List.of(stat.nameInJson()), type -> new JsonPrimitive(type.getStandStats().getValue(stat)),
				type -> new JsonPrimitive(type.getStandStats().getDefaultValue(stat)));
	}

	private static Row percent(Percent percent) {
		return new Row(percent.nameInJson, Kind.INTEGER, new Bounds(0, StandTuning.PERCENT_MAX), List.of(), TEXT + percent.nameInJson,
				StandTuning.MEMBER, List.of(percent.nameInJson), type -> new JsonPrimitive(type.getTuning().percent(percent)),
				type -> new JsonPrimitive(StandTuning.PERCENT_DEFAULT));
	}

	private static JsonPrimitive number(Kind kind, double value) {
		return kind == Kind.INTEGER ? new JsonPrimitive((int) Math.round(value)) : new JsonPrimitive(value);
	}

	@Nullable
	private static Row row(StandType stand, String field) {
		for (Row row : rows(stand)) {
			if (row.field().equals(field)) {
				return row;
			}
		}
		return null;
	}

	/**
	 * Checks every Stand row of the batch and says what it would change, writing nothing: obtaining turns into one
	 * bannedStands replacement for the spec batch (merged with one the batch names itself), the rest into file edits.
	 */
	public static Stage stage(Map<String, JsonElement> standRows, @Nullable JsonElement bannedInBatch) {
		List<String> banned = bannedInBatch != null && bannedInBatch.isJsonArray() ? strings(bannedInBatch.getAsJsonArray())
				: new ArrayList<String>(JojoModConfig.getCommonConfigInstance(false).bannedStands.get());
		boolean bannedChanged = false;
		List<FileEdit> edits = new ArrayList<>();
		List<Change> changes = new ArrayList<>();
		Set<ResourceLocation> stands = new LinkedHashSet<>();
		for (var replacement : standRows.entrySet()) {
			String key = replacement.getKey();
			ResourceLocation id = StandConfigKeys.standId(key);
			StandType stand = id != null ? JojoRegistries.DEFAULT_STANDS_REG.get(id) : null;
			Row row = stand != null ? row(stand, StandConfigKeys.field(key)) : null;
			if (row == null) {
				return new Stage(ConfigEditResult.error(Status.UNKNOWN_KEY, key), Staged.NONE);
			}
			JsonElement parsed = parse(row, replacement.getValue());
			if (parsed == null) {
				return new Stage(ConfigEditResult.error(Status.INVALID_VALUE, key), Staged.NONE);
			}
			JsonElement before = row.value().apply(stand);
			if (parsed.equals(before)) {
				continue;
			}
			changes.add(new Change(key, before, parsed, Restart.NONE));
			stands.add(id);
			if (row.member() == null) {
				bannedChanged |= setObtainable(banned, id, parsed.getAsBoolean());
			}
			else {
				boolean isDefault = parsed.equals(row.defaultValue().apply(stand));
				edits.add(new FileEdit(id, row.member(), row.path(), isDefault ? null : parsed));
			}
		}
		return new Stage(null, new Staged(bannedChanged ? Map.of(BANNED_STANDS, toJson(banned)) : Map.of(), edits, changes, stands));
	}

	// a Stand the list names twice, or by a bare path, is unbanned by every entry that names it
	private static boolean setObtainable(List<String> banned, ResourceLocation id, boolean obtainable) {
		boolean listed = banned.stream().anyMatch(entry -> id.toString().equals(normalized(entry)));
		if (obtainable && listed) {
			banned.removeIf(entry -> id.toString().equals(normalized(entry)));
			return true;
		}
		if (!obtainable && !listed) {
			banned.add(id.toString());
			return true;
		}
		return false;
	}

	@Nullable
	private static String normalized(String entry) {
		ResourceLocation id = ResourceLocation.tryParse(entry.trim());
		if (id != null && ("minecraft".equals(id.getNamespace()) || "jojo".equals(id.getNamespace()))) {
			id = JojoMod.resLoc(id.getPath());
		}
		return id != null ? id.toString() : null;
	}

	private static List<String> strings(JsonArray array) {
		List<String> list = new ArrayList<>(array.size());
		array.forEach(item -> list.add(item.getAsString()));
		return list;
	}

	private static JsonArray toJson(List<String> list) {
		JsonArray array = new JsonArray(list.size());
		list.forEach(array::add);
		return array;
	}

	@Nullable
	private static JsonElement parse(Row row, @Nullable JsonElement input) {
		if (input == null || !input.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive raw = input.getAsJsonPrimitive();
		try {
			return switch (row.kind()) {
				case BOOLEAN -> raw.isBoolean() ? new JsonPrimitive(raw.getAsBoolean()) : null;
				case STRING -> raw.isString() && row.choices().contains(raw.getAsString()) ? new JsonPrimitive(raw.getAsString()) : null;
				case INTEGER -> {
					if (!raw.isNumber()) yield null;
					int value = raw.getAsBigDecimal().intValueExact();
					yield row.bounds() == null || row.bounds().contains(value) ? new JsonPrimitive(value) : null;
				}
				case DECIMAL -> {
					if (!raw.isNumber()) yield null;
					double value = raw.getAsDouble();
					yield row.bounds() == null || row.bounds().contains(value) ? new JsonPrimitive(value) : null;
				}
				default -> null;
			};
		}
		catch (ArithmeticException | NumberFormatException invalid) {
			return null;
		}
	}

	/** Why a write cannot happen now, or null: no server, no Stand data, or a jojoconfig pack the server has switched off. */
	@Nullable
	public static Status unavailable() {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return Status.UNAVAILABLE;
		}
		var packs = server.getPackRepository();
		String pack = "file/" + JojoConfigCommand.packRoot(server).getFileName();
		return packs.getAvailableIds().contains(pack) && !packs.getSelectedIds().contains(pack) ? Status.UNAVAILABLE : null;
	}

	/**
	 * Writes the staged files, each atomically; the Stands are configured from them afterwards ({@link #applyLive}). On a
	 * failure every file written so far is put back and false is returned.
	 */
	public static boolean writeFiles(Staged staged, Map<Path, byte[]> previous) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return false;
		}
		Map<Path, JsonObject> files = new LinkedHashMap<>();
		try {
			Path root = JojoConfigCommand.ensurePackBase(server);
			for (FileEdit edit : staged.fileEdits()) {
				Path path = JojoConfigCommand.standFilePath(root, edit.standId(), edit.member() + ".json");
				JsonObject json = files.get(path);
				if (json == null) {
					previous.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
					json = Files.exists(path) ? JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject()
							: new JsonObject();
					files.put(path, json);
				}
				applyEdit(json, edit.path(), edit.value());
			}
			for (var file : files.entrySet()) {
				if (file.getValue().isEmpty()) {
					Files.deleteIfExists(file.getKey());
				}
				else {
					writeAtomically(file.getKey(), GSON.toJson(file.getValue()).getBytes(StandardCharsets.UTF_8));
				}
			}
			return true;
		}
		catch (IOException | RuntimeException failure) {
			JojoMod.getLogger().error("Could not write the Stand settings into the jojoconfig data pack", failure);
			restoreFiles(previous);
			return false;
		}
	}

	public static void restoreFiles(Map<Path, byte[]> previous) {
		for (var file : previous.entrySet()) {
			try {
				if (file.getValue() == null) {
					Files.deleteIfExists(file.getKey());
				}
				else {
					writeAtomically(file.getKey(), file.getValue());
				}
			}
			catch (IOException again) {
				JojoMod.getLogger().error("Could not put back {} after a failed Stand settings write", file.getKey(), again);
			}
		}
	}

	/** The Stands of the batch take their files now, as the next /reload reads them, and every player is told. */
	public static void applyLive(Staged staged) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return;
		}
		Map<ResourceLocation, List<FileEdit>> byStand = new LinkedHashMap<>();
		staged.fileEdits().forEach(edit -> byStand.computeIfAbsent(edit.standId(), id -> new ArrayList<>()).add(edit));
		byStand.forEach((id, edits) -> DataDrivenStandsLoader.applyEditedStand(server, id, config -> {
			for (FileEdit edit : edits) {
				JsonObject member = config.has(edit.member()) && config.get(edit.member()).isJsonObject()
						? config.getAsJsonObject(edit.member()) : new JsonObject();
				applyEdit(member, edit.path(), edit.value());
				if (member.isEmpty()) {
					config.remove(edit.member());
				}
				else {
					config.add(edit.member(), member);
				}
			}
		}));
	}

	private static void applyEdit(JsonObject json, List<String> path, @Nullable JsonElement value) {
		JsonObject parent = json;
		for (int i = 0; i < path.size() - 1; i++) {
			String name = path.get(i);
			if (!parent.has(name) || !parent.get(name).isJsonObject()) {
				if (value == null) {
					return;
				}
				parent.add(name, new JsonObject());
			}
			parent = parent.getAsJsonObject(name);
		}
		String leaf = path.getLast();
		if (value == null) {
			parent.remove(leaf);
		}
		else {
			parent.add(leaf, value.deepCopy());
		}
		// an object emptied by the edit goes with it, so a file of nothing but defaults ends up empty
		if (value == null && path.size() > 1 && parent.isEmpty()) {
			applyEdit(json, path.subList(0, path.size() - 1), null);
		}
	}

	private static void writeAtomically(Path path, byte[] bytes) throws IOException {
		Files.createDirectories(path.getParent());
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.write(temp, bytes);
		try {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException notOnThisDisk) {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Every row of these Stands that is not what it was: a stat a new value clamped, obtaining that bannedStands moved. */
	public static List<Change> movedRows(Map<String, JsonElement> before, Set<ResourceLocation> stands) {
		List<Change> moved = new ArrayList<>();
		for (ResourceLocation id : stands) {
			StandType stand = JojoRegistries.DEFAULT_STANDS_REG.get(id);
			if (stand == null) {
				continue;
			}
			for (ConfigEntry entry : entries(stand)) {
				JsonElement was = before.get(entry.key());
				if (was != null && !was.equals(entry.value())) {
					moved.add(new Change(entry.key(), was, entry.value(), Restart.NONE));
				}
			}
		}
		return moved;
	}

	public static Map<String, JsonElement> values(Set<ResourceLocation> stands) {
		Map<String, JsonElement> values = new LinkedHashMap<>();
		for (ResourceLocation id : stands) {
			StandType stand = JojoRegistries.DEFAULT_STANDS_REG.get(id);
			if (stand != null) {
				entries(stand).forEach(entry -> values.put(entry.key(), entry.value()));
			}
		}
		return values;
	}

	/** The Stands whose obtaining row a bannedStands list from before to after moves. */
	public static Set<ResourceLocation> standsNamed(JsonElement before, JsonElement after) {
		Set<String> ids = new LinkedHashSet<>();
		for (JsonElement list : List.of(before, after)) {
			if (list.isJsonArray()) {
				list.getAsJsonArray().forEach(item -> {
					String id = item.isJsonPrimitive() ? normalized(item.getAsString()) : null;
					if (id != null) {
						ids.add(id);
					}
				});
			}
		}
		Set<ResourceLocation> stands = new LinkedHashSet<>();
		ids.forEach(id -> {
			ResourceLocation location = ResourceLocation.tryParse(id);
			if (location != null && JojoRegistries.DEFAULT_STANDS_REG.containsKey(location)) {
				stands.add(location);
			}
		});
		return stands;
	}
}
