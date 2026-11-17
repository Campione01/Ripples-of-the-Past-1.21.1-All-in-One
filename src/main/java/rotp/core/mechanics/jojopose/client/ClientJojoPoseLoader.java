package rotp.core.mechanics.jojopose.client;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.OfficialRigPose;
import rotp.core.core.JojoMod;
import rotp.core.mechanics.clothes.CharacterFromClothes;
import rotp.core.mechanics.jojopose.JojoPoseAction;
import rotp.core.mechanics.jojopose.JojoPoseAction.PosingInstance;
import rotp.core.mechanics.jojopose.JojoPoseSets;
import rotp.core.mechanics.jojopose.JojoPoseSets.PoseRef;
import rotp.core.mechanics.jojopose.JojoPoseSets.PoseSet;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.StringUtil;

import com.mojang.datafixers.util.Pair;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** The JoJo poses of the resource packs on this client, with their clips (official ClientJojoPoseLoader). */
public class ClientJojoPoseLoader extends SimplePreparableReloadListener<ClientJojoPoseLoader.Loaded> {
	private static final ClientJojoPoseLoader INSTANCE = new ClientJojoPoseLoader();
	private static final String SUMMON_CLIP = "summon";

	private Loaded loaded = Loaded.EMPTY;
	private final Map<PoseRef, AnimFramePose> heldPoses = new HashMap<>();
	// decided as the Stand's summon clip starts, so a pose that ends meanwhile does not switch clips
	private final Map<StandEntity, OptionalInt> summonVariants = new WeakHashMap<>();

	public record Loaded(JojoPoseSets sets, Map<ResourceLocation, Map<String, RotpAnimDefinition>> clips) {
		static final Loaded EMPTY = new Loaded(JojoPoseSets.EMPTY, Map.of());
	}

	public static void init(RegisterClientReloadListenersEvent event) {
		event.registerReloadListener(INSTANCE);
	}

	@Override
	protected Loaded prepare(ResourceManager resources, ProfilerFiller profiler) {
		Map<ResourceLocation, Map<String, RotpAnimDefinition>> clips = new HashMap<>();
		JojoPoseSets sets = JojoPoseSets.load(resources, (set, pose, clipJson, pack) -> {
			try {
				clips.computeIfAbsent(set, __ -> new LinkedHashMap<>()).put(pose, ParseGeckoAnims.parseAnim(clipJson));
			}
			catch (Exception e) {
				JojoMod.getLogger().error("Failed to load the JoJo pose {}#{} from {}", set, pose, pack, e);
			}
		});
		return new Loaded(sets, clips);
	}

	@Override
	protected void apply(Loaded prepared, ResourceManager resources, ProfilerFiller profiler) {
		loaded = prepared;
		heldPoses.clear();
		summonVariants.clear();
		JojoMod.getLogger().info("Loaded {} JoJo pose sets", prepared.sets().allSets().size());
	}

	public static JojoPoseSets sets() {
		return INSTANCE.loaded.sets();
	}

	@Nullable
	public static RotpAnimDefinition clip(ResourceLocation set, String pose) {
		Map<String, RotpAnimDefinition> setClips = INSTANCE.loaded.clips().get(set);
		return setClips != null ? setClips.get(pose) : null;
	}

	/** The poses the entity's worn character may strike that this client can draw. */
	public static List<PoseRef> posesFor(LivingEntity entity) {
		return sets().posesFor(CharacterFromClothes.of(entity)).stream()
				.filter(pose -> clip(pose.set(), pose.pose()) != null)
				.toList();
	}

	/** The pose as it is held, its clip's last frame on our rig (the official JojoPose.getPose), for the pose list. */
	@Nullable
	public static AnimFramePose heldPose(PoseRef pose) {
		RotpAnimDefinition clip = clip(pose.set(), pose.pose());
		if (clip == null) {
			return null;
		}
		return INSTANCE.heldPoses.computeIfAbsent(pose, __ -> {
			AnimFramePose frame = clip.calcAnimPose(null, null, clip.lengthInSeconds, 1, new AnimFramePose(), new Vector3f());
			OfficialRigPose.remap(frame);
			return frame.deepCopy();
		});
	}

	/**
	 * The summon clip variant a Stand plays as it appears while its user holds a pose with a {@code stand_summon_pose} for
	 * that Stand (official: Jotaro's volume13 with Star Platinum's summon2). Only {@code summon<N>} clips are read.
	 */
	public static OptionalInt standSummonVariant(StandEntity stand) {
		return INSTANCE.summonVariants.computeIfAbsent(stand, ClientJojoPoseLoader::userPoseSummonVariant);
	}

	private static OptionalInt userPoseSummonVariant(StandEntity stand) {
		PosingInstance posing = JojoPoseAction.getPose(stand.getUser());
		ResourceLocation standType = stand.getStandType();
		PoseSet set = posing != null ? sets().set(posing.poseSet) : null;
		if (set == null || standType == null) {
			return OptionalInt.empty();
		}
		Optional<JojoPoseSets.StandSummonPose> summon = set.data(posing.poseName).standSummonPose()
				.filter(pose -> pose.standType().equals(standType));
		if (summon.isEmpty()) {
			return OptionalInt.empty();
		}
		Pair<String, OptionalInt> clip = StringUtil.splitIntAtTheEnd(summon.get().animName());
		if (!SUMMON_CLIP.equals(clip.getFirst())) {
			return OptionalInt.empty();
		}
		// clip numbers count from 1 (ActionAnimIdentifier.getOrCreate)
		return OptionalInt.of(clip.getSecond().orElse(1) - 1);
	}
}
