package rotp.core.mechanics.voiceline;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

/**
 * The official voice packs: {@code assets/<namespace>/voicelines/<pack>/sounds.json} maps a trigger (a {@code vl_*}
 * sound event, in the file's namespace unless the key names one) to one line or a list of them, each with its
 * {@code sounds} (sound file paths), an optional {@code subtitle} key and an optional {@code stand_type} filter.
 * {@code data.json} names the pack's {@code character} (by default the pack's own id) and an optional
 * {@code story_part} filter.
 * <p>
 * Every resource pack's copy counts: the lines of all copies of a pack's {@code sounds.json} are kept, lowest pack
 * first, and in {@code data.json} each field of the highest pack that sets it wins.
 */
public final class VoiceLinePacks {
	public static final VoiceLinePacks EMPTY = new VoiceLinePacks(Map.of(), 0);
	private static final String TOP_DIR = "voicelines";
	private static final String SOUNDS_FILE = "sounds.json";
	private static final String DATA_FILE = "data.json";

	private final Map<ResourceLocation, List<PackLine>> linesByTrigger;
	private final int packCount;

	private VoiceLinePacks(Map<ResourceLocation, List<PackLine>> linesByTrigger, int packCount) {
		this.linesByTrigger = linesByTrigger;
		this.packCount = packCount;
	}

	/** One line a pack has for a trigger, with the speaker it is for. */
	public record PackLine(List<ResourceLocation> sounds, Optional<String> subtitle, ResourceLocation character,
			Optional<List<ResourceLocation>> storyParts, Optional<List<ResourceLocation>> standTypes, ResourceLocation pack) {

		public boolean isFor(VoiceLineSpeaker speaker) {
			return speaker.character().filter(character::equals).isPresent()
					&& storyParts.map(parts -> speaker.storyPart().filter(parts::contains).isPresent()).orElse(true)
					&& standTypes.map(stands -> speaker.standType().filter(stands::contains).isPresent()).orElse(true);
		}
	}

	/** The sound file a pack line plays, and its subtitle key. */
	public record PickedLine(ResourceLocation sound, Optional<String> subtitle, ResourceLocation pack) {}

	public List<PackLine> linesFor(@Nullable ResourceLocation trigger, VoiceLineSpeaker speaker) {
		if (trigger == null || !speaker.hasCharacter()) {
			return List.of();
		}
		return linesByTrigger.getOrDefault(trigger, List.of()).stream().filter(line -> line.isFor(speaker)).toList();
	}

	/** A pack line to say instead of the 1.16 one, or null to keep the 1.16 one. */
	@Nullable
	public PickedLine pick(@Nullable ResourceLocation trigger, VoiceLineSpeaker speaker, RandomSource random) {
		List<PackLine> lines = linesFor(trigger, speaker);
		if (lines.isEmpty()) {
			return null;
		}
		PackLine line = lines.get(random.nextInt(lines.size()));
		return new PickedLine(line.sounds().get(random.nextInt(line.sounds().size())), line.subtitle(), line.pack());
	}

	public Map<ResourceLocation, List<PackLine>> allLines() {
		return linesByTrigger;
	}

	public int packCount() {
		return packCount;
	}


	public static VoiceLinePacks load(ResourceManager resources) {
		Map<ResourceLocation, PackPrep> packs = new TreeMap<>();
		Map<ResourceLocation, List<Resource>> files = resources.listResourceStacks(TOP_DIR, path -> path.getPath().endsWith(".json"));
		for (Map.Entry<ResourceLocation, List<Resource>> file : files.entrySet()) {
			ResourceLocation path = file.getKey();
			String fileName = path.getPath().substring(path.getPath().lastIndexOf('/') + 1);
			if (!fileName.equals(SOUNDS_FILE) && !fileName.equals(DATA_FILE)) {
				continue;
			}
			int packEnd = path.getPath().length() - fileName.length() - 1;
			if (packEnd <= TOP_DIR.length()) {
				continue;
			}
			ResourceLocation packId = path.withPath(path.getPath().substring(TOP_DIR.length() + 1, packEnd));
			PackPrep pack = packs.computeIfAbsent(packId, PackPrep::new);
			for (Resource copy : file.getValue()) {
				try (Reader reader = copy.openAsReader()) {
					JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
					if (fileName.equals(SOUNDS_FILE)) {
						pack.readSounds(json, path.getNamespace());
					}
					else {
						pack.readData(json);
					}
				}
				catch (Exception e) {
					JojoMod.getLogger().error("Could not read voice line pack file {} from {}", path, copy.sourcePackId(), e);
				}
			}
		}

		Map<ResourceLocation, List<PackLine>> linesByTrigger = new TreeMap<>();
		for (PackPrep pack : packs.values()) {
			pack.lines.forEach((trigger, lines) -> {
				for (LinePrep line : lines) {
					linesByTrigger.computeIfAbsent(trigger, __ -> new ArrayList<>()).add(new PackLine(
							List.copyOf(line.sounds), line.subtitle, pack.character,
							pack.storyParts, line.standTypes, pack.id));
				}
			});
		}
		linesByTrigger.replaceAll((trigger, lines) -> Collections.unmodifiableList(lines));
		return new VoiceLinePacks(Collections.unmodifiableMap(linesByTrigger), packs.size());
	}

	private static final class PackPrep {
		final ResourceLocation id;
		ResourceLocation character;
		Optional<List<ResourceLocation>> storyParts = Optional.empty();
		final Map<ResourceLocation, List<LinePrep>> lines = new TreeMap<>();

		PackPrep(ResourceLocation id) {
			this.id = id;
			this.character = id;
		}

		void readData(JsonObject json) {
			if (json.has("character")) {
				character = ResourceLocation.parse(json.get("character").getAsString());
			}
			if (json.has("story_part")) {
				storyParts = Optional.of(oneOrList(json.get("story_part"), e -> ResourceLocation.parse(e.getAsString())));
			}
		}

		void readSounds(JsonObject json, String namespace) {
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				String key = entry.getKey();
				ResourceLocation trigger = key.indexOf(':') >= 0
						? ResourceLocation.parse(key)
						: ResourceLocation.fromNamespaceAndPath(namespace, key);
				for (JsonElement lineJson : oneOrList(entry.getValue(), Function.identity())) {
					JsonObject object = lineJson.getAsJsonObject();
					LinePrep line = new LinePrep();
					if (object.has("subtitle")) {
						line.subtitle = Optional.of(object.get("subtitle").getAsString());
					}
					if (object.has("stand_type")) {
						line.standTypes = Optional.of(oneOrList(object.get("stand_type"), e -> ResourceLocation.parse(e.getAsString())));
					}
					if (object.has("sounds")) {
						line.sounds.addAll(oneOrList(object.get("sounds"), e -> ResourceLocation.parse(e.getAsString())));
					}
					if (line.sounds.isEmpty()) {
						JojoMod.getLogger().warn("Voice line pack {} has a line with no sounds for {}", id, trigger);
						continue;
					}
					lines.computeIfAbsent(trigger, __ -> new ArrayList<>()).add(line);
				}
			}
		}
	}

	private static final class LinePrep {
		Optional<String> subtitle = Optional.empty();
		Optional<List<ResourceLocation>> standTypes = Optional.empty();
		final List<ResourceLocation> sounds = new ArrayList<>();
	}

	private static <T> List<T> oneOrList(JsonElement json, Function<JsonElement, T> read) {
		if (json.isJsonArray()) {
			List<T> list = new ArrayList<>();
			json.getAsJsonArray().forEach(element -> list.add(read.apply(element)));
			return List.copyOf(list);
		}
		return List.of(read.apply(json));
	}
}
