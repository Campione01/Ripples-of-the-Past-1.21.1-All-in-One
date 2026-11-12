package rotp.core.mechanics.jojopose;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.mechanics.clothes.CharacterFromClothes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The official JoJo poses: {@code assets/<namespace>/jojo_pose/<set>/animation.json} holds a set's poses (GeckoLib player
 * clips, one per pose, played to their last frame and held), and {@code data.json} its optional {@code character} and
 * {@code story_part} filters and, per pose under {@code anim_specific}, a {@code stand_summon_pose}.
 * <p>
 * Every resource pack's copy counts: a higher pack's pose replaces a lower pack's pose of the same name and adds the
 * others after them; in {@code data.json} each field of the highest pack that sets it wins, per pose for
 * {@code anim_specific}. Voice lines are not read here: a pose's line is the voice packs' trigger {@link #voiceTrigger}.
 * <p>
 * Common code (no client classes), so game tests can run it on the server.
 */
public final class JojoPoseSets {
	public static final JojoPoseSets EMPTY = new JojoPoseSets(Map.of());
	public static final String TOP_DIR = "jojo_pose";
	public static final String ANIM_FILE = "animation.json";
	public static final String DATA_FILE = "data.json";
	public static final String VOICE_TRIGGER_PREFIX = "vl_jojo_pose/";

	private final Map<ResourceLocation, PoseSet> sets;

	private JojoPoseSets(Map<ResourceLocation, PoseSet> sets) {
		this.sets = sets;
	}

	/** A pose set: who may strike its poses, and the poses in their order. */
	public record PoseSet(ResourceLocation id, Optional<ResourceLocation> character, Optional<List<ResourceLocation>> storyParts,
			List<String> poses, Map<String, PoseData> poseData) {

		/**
		 * The official ClientJojoPoseLoader.getPosesForCharacter: a set names no character or the wearer's, and no story
		 * part or one of them is the wearer's. A wearer who counts as no character has only the sets that name none.
		 */
		public boolean isFor(Optional<ResourceLocation> wornCharacter, Optional<ResourceLocation> wornStoryPart) {
			return character.map(own -> wornCharacter.filter(own::equals).isPresent()).orElse(true)
					&& storyParts.map(parts -> wornStoryPart.filter(parts::contains).isPresent()).orElse(true);
		}

		public PoseData data(String pose) {
			return poseData.getOrDefault(pose, PoseData.EMPTY);
		}
	}

	/** A pose's own data. {@code standSummonPose}: the clip its Stand plays as it is summoned during the pose. */
	public record PoseData(Optional<StandSummonPose> standSummonPose) {
		public static final PoseData EMPTY = new PoseData(Optional.empty());
	}

	public record StandSummonPose(ResourceLocation standType, String animName) {}

	/** One pose of one set. */
	public record PoseRef(ResourceLocation set, String pose) {
		public ResourceLocation voiceTrigger() {
			return JojoPoseSets.voiceTrigger(set, pose);
		}
	}

	/**
	 * The voice packs' trigger of a pose ({@code assets/<ns>/voicelines/<pack>/sounds.json} key): the set's namespace and
	 * {@code vl_jojo_pose/<set path>/<pose>}.
	 */
	public static ResourceLocation voiceTrigger(ResourceLocation set, String pose) {
		return set.withPath(VOICE_TRIGGER_PREFIX + set.getPath() + "/" + pose);
	}

	/** A pose name a pack may use: it becomes a resource path in its voice trigger. */
	public static boolean isValidPoseName(@Nullable String pose) {
		return pose != null && !pose.isEmpty() && pose.length() <= JojoPoseAction.MAX_POSE_NAME_LENGTH
				&& pose.indexOf('/') < 0 && ResourceLocation.isValidPath(pose);
	}

	@Nullable
	public PoseSet set(ResourceLocation id) {
		return sets.get(id);
	}

	public Map<ResourceLocation, PoseSet> allSets() {
		return sets;
	}

	/** The poses a wearer of this character and story part may strike, set by set in id order. */
	public List<PoseRef> posesFor(Optional<ResourceLocation> character, Optional<ResourceLocation> storyPart) {
		List<PoseRef> poses = new ArrayList<>();
		for (PoseSet set : sets.values()) {
			if (set.isFor(character, storyPart)) {
				set.poses().forEach(pose -> poses.add(new PoseRef(set.id(), pose)));
			}
		}
		return poses;
	}

	public List<PoseRef> posesFor(CharacterFromClothes worn) {
		return posesFor(worn.character().flatMap(JojoPoseSets::id), worn.storyPart().flatMap(JojoPoseSets::id));
	}

	private static Optional<ResourceLocation> id(Holder<?> holder) {
		return holder.unwrapKey().map(ResourceKey::location);
	}


	/** Sees each copy of each set's animation file, lowest pack first: the client parses the clips from it. */
	@FunctionalInterface
	public interface PoseClipVisitor {
		void visit(ResourceLocation set, String pose, JsonObject clipJson, String sourcePackId);
	}

	public static JojoPoseSets load(ResourceManager resources) {
		return load(resources, null);
	}

	public static JojoPoseSets load(ResourceManager resources, @Nullable PoseClipVisitor clips) {
		Map<ResourceLocation, SetPrep> sets = new TreeMap<>();
		Map<ResourceLocation, List<Resource>> files = resources.listResourceStacks(TOP_DIR, path -> path.getPath().endsWith(".json"));
		for (Map.Entry<ResourceLocation, List<Resource>> file : files.entrySet()) {
			ResourceLocation path = file.getKey();
			String fileName = path.getPath().substring(path.getPath().lastIndexOf('/') + 1);
			if (!fileName.equals(ANIM_FILE) && !fileName.equals(DATA_FILE)) {
				continue;
			}
			int setEnd = path.getPath().length() - fileName.length() - 1;
			if (setEnd <= TOP_DIR.length()) {
				continue;
			}
			ResourceLocation setId = path.withPath(path.getPath().substring(TOP_DIR.length() + 1, setEnd));
			SetPrep set = sets.computeIfAbsent(setId, SetPrep::new);
			for (Resource copy : file.getValue()) {
				try (Reader reader = copy.openAsReader()) {
					JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
					if (fileName.equals(ANIM_FILE)) {
						set.readPoses(json, copy.sourcePackId(), clips);
					}
					else {
						set.readData(json);
					}
				}
				catch (Exception e) {
					JojoMod.getLogger().error("Could not read JoJo pose file {} from {}", path, copy.sourcePackId(), e);
				}
			}
		}

		Map<ResourceLocation, PoseSet> built = new TreeMap<>();
		for (SetPrep set : sets.values()) {
			if (!set.poses.isEmpty()) {
				built.put(set.id, new PoseSet(set.id, set.character, set.storyParts, List.copyOf(set.poses),
						Collections.unmodifiableMap(new LinkedHashMap<>(set.poseData))));
			}
		}
		return new JojoPoseSets(Collections.unmodifiableMap(built));
	}

	private static final class SetPrep {
		final ResourceLocation id;
		Optional<ResourceLocation> character = Optional.empty();
		Optional<List<ResourceLocation>> storyParts = Optional.empty();
		final List<String> poses = new ArrayList<>();
		final Map<String, PoseData> poseData = new LinkedHashMap<>();

		SetPrep(ResourceLocation id) {
			this.id = id;
		}

		void readPoses(JsonObject json, String sourcePackId, @Nullable PoseClipVisitor clips) {
			JsonObject animations = json.getAsJsonObject("animations");
			if (animations == null) {
				return;
			}
			for (Map.Entry<String, JsonElement> clip : animations.entrySet()) {
				String pose = clip.getKey();
				if (pose.startsWith("unused#")) {
					continue;
				}
				if (!isValidPoseName(pose)) {
					JojoMod.getLogger().warn("JoJo pose set {} in {}: '{}' is not a valid pose name (lowercase a-z, 0-9, _ . -)",
							id, sourcePackId, pose);
					continue;
				}
				if (!clip.getValue().isJsonObject()) {
					continue;
				}
				if (!poses.contains(pose)) {
					poses.add(pose);
				}
				if (clips != null) {
					clips.visit(id, pose, clip.getValue().getAsJsonObject(), sourcePackId);
				}
			}
		}

		void readData(JsonObject json) {
			if (json.has("character")) {
				character = Optional.of(ResourceLocation.parse(json.get("character").getAsString()));
			}
			if (json.has("story_part")) {
				storyParts = Optional.of(oneOrList(json.get("story_part"), e -> ResourceLocation.parse(e.getAsString())));
			}
			JsonObject perPose = json.has("anim_specific") ? json.getAsJsonObject("anim_specific") : null;
			if (perPose != null) {
				for (Map.Entry<String, JsonElement> entry : perPose.entrySet()) {
					JsonObject data = entry.getValue().getAsJsonObject();
					Optional<StandSummonPose> summon = Optional.empty();
					if (data.has("stand_summon_pose")) {
						JsonObject summonJson = data.getAsJsonObject("stand_summon_pose");
						summon = Optional.of(new StandSummonPose(ResourceLocation.parse(summonJson.get("stand_id").getAsString()),
								summonJson.get("anim_name").getAsString()));
					}
					if (data.has("voice_line")) {
						JojoMod.getLogger().warn("JoJo pose {}#{}: the pose's voice_line is not read; give its line in a voice pack "
								+ "under the trigger {}", id, entry.getKey(), voiceTrigger(id, entry.getKey()));
					}
					poseData.put(entry.getKey(), new PoseData(summon));
				}
			}
		}
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
