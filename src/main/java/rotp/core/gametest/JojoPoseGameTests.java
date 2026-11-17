package rotp.core.gametest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import rotp.core.client.entityanim.OfficialPlayerBones;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModSpecialActions;
import rotp.core.mechanics.clothes.CharacterFromClothes;
import rotp.core.mechanics.clothes.EntityClothesInventory;
import rotp.core.mechanics.clothes.itemdata.ClothesDataComponent;
import rotp.core.mechanics.clothes.itemdata.ClothesPiece;
import rotp.core.mechanics.clothes.itemdata.ClothesSet;
import rotp.core.mechanics.clothes.itemdata.ClothesSlotType;
import rotp.core.mechanics.clothes.itemdata.StoryCharacter;
import rotp.core.mechanics.jojopose.ClJojoPosePacket;
import rotp.core.mechanics.jojopose.JojoPoseAction;
import rotp.core.mechanics.jojopose.JojoPoseAction.PosingInstance;
import rotp.core.mechanics.jojopose.JojoPoseSets;
import rotp.core.mechanics.jojopose.JojoPoseSets.PoseRef;
import rotp.core.mechanics.jojopose.JojoPoseSets.PoseSet;
import rotp.core.mechanics.voiceline.VoiceLinePacks;
import rotp.core.mechanics.voiceline.VoiceLinePacks.PickedLine;
import rotp.core.network.s2c.PlayVoiceLinePacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionInstancePacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionPhaseTimePacket;
import rotp.core.subsystems.StoryPart;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The official JoJo poses on our player animation path (lane OFFPOSES): the pose sets load and merge over every pack,
 * the worn character chooses the set, a pose starts and ends by the official rules, says its voice pack line, and is
 * sent to the poser and every player tracking it. The clips themselves are client code; their bones are checked here.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JojoPoseGameTests {
	private static final String BATCH = "jojo_ripples_poses";
	private static final ResourceLocation JOTARO_SET = ResourceLocation.fromNamespaceAndPath("jojo_rotp_clothes", "jotaro_p3");
	private static final List<String> JOTARO_POSES = List.of("point", "cap", "volume13");
	// our player rig's bones (PlayerModelBends.getModelPartForPlayerAnim)
	private static final Set<String> OUR_BONES = Set.of("body", "torso", "torso_bend", "head", "left_arm", "right_arm",
			"left_leg", "right_leg", "left_arm_bend", "right_arm_bend", "left_leg_bend", "right_leg_bend", "leftItem",
			"rightItem", "cape", "cape_bend");

	private JojoPoseGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void poseSetsLoadAndMergeEveryPacksCopy(GameTestHelper helper) {
		String clip = "{\"loop\": \"hold_on_last_frame\", \"animation_length\": 0.2, \"bones\": {}}";
		MemoryPack lower = new MemoryPack("mod/jojo_ripples", Map.of(
				"jojo_rotp_clothes/jojo_pose/jotaro_p3/animation.json",
						"{\"animations\": {\"point\": " + clip + ", \"cap\": " + clip + "}}",
				"jojo_rotp_clothes/jojo_pose/jotaro_p3/data.json", "{\"character\": \"jojo_ripples:jotaro\", \"story_part\": [\"jojo_ripples:p3\"],"
						+ " \"anim_specific\": {\"point\": {\"stand_summon_pose\": {\"stand_id\": \"jojo_ripples:star_platinum\", \"anim_name\": \"summon2\"}}}}",
				"broken_ns/jojo_pose/broken/animation.json", "{\"animations\": ",
				"other_ns/jojo_pose/no_clips/data.json", "{\"character\": \"jojo_ripples:dio\"}"));
		MemoryPack upper = new MemoryPack("file/user_pack", Map.of(
				"jojo_rotp_clothes/jojo_pose/jotaro_p3/animation.json",
						"{\"animations\": {\"cap\": " + clip + ", \"wave\": " + clip + ", \"Bad Name\": " + clip + "}}",
				"jojo_rotp_clothes/jojo_pose/jotaro_p3/data.json", "{\"story_part\": [\"jojo_ripples:p3\", \"jojo_ripples:p4\"],"
						+ " \"anim_specific\": {\"cap\": {}}}"));
		List<String> visits = new ArrayList<>();
		JojoPoseSets sets;
		try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(lower, upper))) {
			sets = JojoPoseSets.load(resources, (set, pose, json, pack) -> visits.add(pack + ":" + pose));
		}
		helper.assertTrue(sets.allSets().keySet().equals(Set.of(JOTARO_SET)),
				"only the set with poses loads; the broken file and the set with no clips are dropped: " + sets.allSets().keySet());
		PoseSet set = sets.set(JOTARO_SET);
		helper.assertTrue(set.poses().equals(List.of("point", "cap", "wave")),
				"the lower pack's poses first, the upper pack's new pose after them, the bad name skipped: " + set.poses());
		helper.assertTrue(set.character().equals(Optional.of(JojoMod.resLoc("jotaro"))),
				"a field only the lower data.json sets is kept: " + set.character());
		helper.assertTrue(set.storyParts().equals(Optional.of(List.of(JojoMod.resLoc("p3"), JojoMod.resLoc("p4")))),
				"the upper data.json's story_part wins: " + set.storyParts());
		helper.assertTrue(set.data("point").standSummonPose().map(pose -> pose.standType().equals(JojoMod.resLoc("star_platinum"))
				&& pose.animName().equals("summon2")).orElse(false), "point keeps its Stand summon pose: " + set.data("point"));
		int lowerCap = visits.indexOf("mod/jojo_ripples:cap");
		int upperCap = visits.indexOf("file/user_pack:cap");
		helper.assertTrue(lowerCap >= 0 && upperCap > lowerCap,
				"each copy's clip is seen lowest pack first, so the upper pack's cap replaces the lower one: " + visits);

		JojoPoseSets shipped = shippedPoseSets();
		PoseSet jotaro = shipped.set(JOTARO_SET);
		helper.assertTrue(jotaro != null && jotaro.poses().equals(JOTARO_POSES), "the official Jotaro poses load: "
				+ (jotaro != null ? jotaro.poses() : shipped.allSets().keySet()));
		helper.assertTrue(jotaro.character().equals(Optional.of(JojoMod.resLoc("jotaro")))
				&& jotaro.storyParts().equals(Optional.of(List.of(JojoMod.resLoc("p3")))), "for Jotaro of part 3: " + jotaro);
		helper.assertTrue(jotaro.data("volume13").standSummonPose().map(pose -> pose.standType().equals(JojoMod.resLoc("star_platinum"))
				&& pose.animName().equals("summon2")).orElse(false), "volume13 summons Star Platinum with summon2: " + jotaro.data("volume13"));

		List<String> problems = new ArrayList<>();
		JsonObject clips = readJson("assets/jojo_rotp_clothes/jojo_pose/jotaro_p3/animation.json").getAsJsonObject("animations");
		for (Map.Entry<String, JsonElement> pose : clips.entrySet()) {
			for (String bone : pose.getValue().getAsJsonObject().getAsJsonObject("bones").keySet()) {
				if (!OUR_BONES.contains(bone) && !OfficialPlayerBones.isOfficialOnly(bone)) {
					problems.add(pose.getKey() + " keys " + bone + ", a bone neither our rig nor the bone table has");
				}
			}
		}
		for (String sprite : List.of("pose_slot", "pose_slot_selection")) {
			String path = "assets/jojo_ripples/textures/gui/sprites/widget/" + sprite + ".png";
			if (JojoMod.class.getClassLoader().getResource(path) == null) problems.add("missing " + path);
		}
		JsonObject en = readJson("assets/jojo_ripples/lang/en_us.json");
		for (String locale : List.of("zh_cn", "zh_tw")) {
			JsonObject lang = readJson("assets/jojo_ripples/lang/" + locale + ".json");
			for (String key : List.of("jojo_ripples.key.jojo_pose", "jojo_ripples.key.jojo_pose.desc",
					"jojo_ripples.screen.jojo_poses", "jojo_ripples.screen.jojo_poses.none")) {
				if (!en.has(key)) problems.add("en_us lacks " + key);
				else if (!lang.has(key)) problems.add(locale + " lacks " + key);
				else if (lang.get(key).getAsString().equals(en.get(key).getAsString())) problems.add(locale + " leaves " + key + " in English");
			}
		}
		helper.assertTrue(problems.isEmpty(), "pose assets: " + problems);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void theWornCharacterChoosesThePoseSet(GameTestHelper helper) {
		JojoPoseSets shipped = shippedPoseSets();
		Player jotaro = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player joseph = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player nobody = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player jotaroP4 = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player josephP3 = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		wearFullSet(helper, jotaro, "jotaro_p3_anime");
		wearSetOf(helper, joseph, "joseph", "p2");
		wearSetOf(helper, jotaroP4, "jotaro", "p4");
		wearSetOf(helper, josephP3, "joseph", "p3");
		helper.assertTrue(shipped.posesFor(CharacterFromClothes.of(josephP3)).isEmpty(),
				"the set's character filter keeps Joseph of part 3 out: " + shipped.posesFor(CharacterFromClothes.of(josephP3)));

		helper.assertTrue(names(shipped.posesFor(CharacterFromClothes.of(jotaro))).equals(JOTARO_POSES),
				"Jotaro's part 3 clothes give the Jotaro poses: " + shipped.posesFor(CharacterFromClothes.of(jotaro)));
		helper.assertTrue(shipped.posesFor(CharacterFromClothes.of(joseph)).isEmpty(),
				"Joseph's clothes give no Jotaro pose: " + shipped.posesFor(CharacterFromClothes.of(joseph)));
		helper.assertTrue(shipped.posesFor(CharacterFromClothes.of(jotaroP4)).isEmpty(),
				"the set's story part filter keeps Jotaro of part 4 out: " + shipped.posesFor(CharacterFromClothes.of(jotaroP4)));
		helper.assertTrue(shipped.posesFor(CharacterFromClothes.of(nobody)).isEmpty(),
				"as in the official, no character and no pose for everyone means no pose: " + shipped.posesFor(CharacterFromClothes.of(nobody)));

		String clip = "{\"animation_length\": 0.2, \"bones\": {}}";
		Map<String, String> files = new HashMap<>(shippedPoseFiles());
		files.put("my_pack/jojo_pose/everyone/animation.json", "{\"animations\": {\"wave\": " + clip + "}}");
		JojoPoseSets withGeneric;
		try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(new MemoryPack("file/generic", files)))) {
			withGeneric = JojoPoseSets.load(resources);
		}
		PoseRef wave = new PoseRef(ResourceLocation.fromNamespaceAndPath("my_pack", "everyone"), "wave");
		helper.assertTrue(withGeneric.posesFor(CharacterFromClothes.of(nobody)).equals(List.of(wave)),
				"a wearer of no character falls back to the poses that name no character: " + withGeneric.posesFor(CharacterFromClothes.of(nobody)));
		helper.assertTrue(withGeneric.posesFor(CharacterFromClothes.of(joseph)).equals(List.of(wave)),
				"Joseph gets only the pose for everyone: " + withGeneric.posesFor(CharacterFromClothes.of(joseph)));
		List<PoseRef> jotaroPoses = withGeneric.posesFor(CharacterFromClothes.of(jotaro));
		helper.assertTrue(names(jotaroPoses).equals(List.of("wave", "point", "cap", "volume13")),
				"Jotaro gets his set and the pose for everyone, set by set in id order (path first): " + jotaroPoses);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void aPoseHoldsUntilAMoveOrAnAttack(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		PlayerMovementInputData input = PlayerMovementInputData.get(player);
		LivingComponentAction own = LivingComponentAction.getComponent(player);

		helper.assertTrue(JojoPoseAction.start(player, JOTARO_SET, "cap"), "a free player must be able to pose");
		PosingInstance cap = JojoPoseAction.getPose(player);
		helper.assertTrue(cap != null && cap.getPhase() == ActionPhase.PERFORM && cap.poseSet.equals(JOTARO_SET)
				&& cap.poseName.equals("cap"), "the pose holds in its perform: " + describe(cap));
		for (int i = 0; i < 40; i++) own.tick();
		helper.assertTrue(JojoPoseAction.getPose(player) == cap, "a pose with no input holds on: " + describe(own.getAction()));

		helper.assertTrue(JojoPoseAction.start(player, JOTARO_SET, "volume13") && JojoPoseAction.getPose(player).poseName.equals("volume13"),
				"another pose replaces a held pose");
		helper.assertTrue(!JojoPoseAction.start(player, JOTARO_SET, "Bad/Name") && !JojoPoseAction.start(player, JOTARO_SET, ""),
				"a pose name that is no resource path is refused");

		record Ender(String what, Runnable apply, Runnable undo) {}
		List<Ender> enders = List.of(
				new Ender("walking forward", () -> input.setValues(0, 1, false, false, false), () -> input.setValues(0, 0, false, false, false)),
				new Ender("strafing", () -> input.setValues(-1, 0, false, false, false), () -> input.setValues(0, 0, false, false, false)),
				new Ender("jumping", () -> input.setValues(0, 0, true, false, false), () -> input.setValues(0, 0, false, false, false)),
				new Ender("sneaking", () -> input.setValues(0, 0, false, true, false), () -> input.setValues(0, 0, false, false, false)),
				new Ender("swinging an arm", () -> player.swinging = true, () -> player.swinging = false));
		for (Ender ender : enders) {
			helper.assertTrue(JojoPoseAction.start(player, JOTARO_SET, "point"), "setup: could not pose before " + ender.what());
			own.tick();
			helper.assertTrue(JojoPoseAction.getPose(player) != null, "setup: the pose ended with no input before " + ender.what());
			ender.apply().run();
			own.tick();
			helper.assertTrue(JojoPoseAction.getPose(player) == null && own.getAction() == null,
					ender.what() + " must end the pose: " + describe(own.getAction()));
			ender.undo().run();
		}

		LivingComponentAction standSlot = LivingComponentAction.getComponent(player, PowerClass.STAND);
		helper.assertTrue(JojoPoseAction.start(player, JOTARO_SET, "point"), "setup: could not pose before a Stand action");
		standSlot.setAction(heldAction(ModSpecialActions.STAND_ENTITY_BLOCK.get()), player, SyncType.NO_SYNC);
		own.tick();
		helper.assertTrue(JojoPoseAction.getPose(player) == null, "a Stand-power action of the poser's own must end the pose");
		helper.assertTrue(!JojoPoseAction.start(player, JOTARO_SET, "point"), "no pose while the poser performs a Stand-power action");
		standSlot.setAction(null, SyncType.NO_SYNC);

		own.setAction(heldAction(ModSpecialActions.RMB_CLICK_ITEM.get()), player, SyncType.NO_SYNC);
		helper.assertTrue(!JojoPoseAction.start(player, JOTARO_SET, "point") && !(own.getAction() instanceof PosingInstance),
				"a pose must not cut another action off: " + describe(own.getAction()));
		own.setAction(null, SyncType.NO_SYNC);

		helper.assertTrue(JojoPoseAction.start(player, JOTARO_SET, "point"), "setup: could not pose before an ability");
		helper.assertTrue(JojoPoseAction.getPose(player).canBeCancelledInto(ModSpecialActions.RMB_USING_ITEM.get()),
				"any action may replace a pose (official canBeCancelledInto)");
		helper.assertTrue(JojoPoseAction.stop(player) && JojoPoseAction.getPose(player) == null, "the stop request ends the pose");

		Player spectator = GameTestPlayers.makeServerMockPlayer(helper, GameType.SPECTATOR);
		helper.assertTrue(!JojoPoseAction.start(spectator, JOTARO_SET, "point"), "a spectator does not pose");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void posingSaysThePoseLineOfTheVoicePacks(GameTestHelper helper) {
		Player jotaro = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player nobody = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		wearFullSet(helper, jotaro, "jotaro_p3_anime");
		VoiceLinePacks packs = shippedVoicePacks();
		ResourceLocation capTrigger = ResourceLocation.fromNamespaceAndPath("jojo_rotp_clothes", "vl_jojo_pose/jotaro_p3/cap");

		PlayVoiceLinePacket line = strikeAndHear(jotaro, "cap");
		helper.assertTrue(line != null && line.sound() == null && capTrigger.equals(line.triggerId()),
				"Jotaro's cap pose must say the pack trigger of the pose, with no 1.16 sound: " + line);
		helper.assertTrue(line.speaker().character().equals(Optional.of(JojoMod.resLoc("jotaro")))
				&& line.speaker().storyPart().equals(Optional.of(JojoMod.resLoc("p3"))), "the speaker is Jotaro of part 3: " + line.speaker());
		PlayVoiceLinePacket received = overTheWire(helper, line);
		helper.assertTrue(capTrigger.equals(received.triggerId()), "a client reads the pose trigger from the packet: " + received.triggerId());
		Set<String> subtitles = new HashSet<>();
		RandomSource random = RandomSource.create(5);
		for (int i = 0; i < 32; i++) {
			PickedLine picked = packs.pick(received.triggerId(), received.speaker(), random);
			helper.assertTrue(picked != null && picked.sound().getPath().startsWith("stand_user/jotaro_yare_yare"),
					"the pose pack's yare yare must play for the cap pose: " + picked);
			subtitles.add(picked.subtitle().orElse("-"));
		}
		helper.assertTrue(subtitles.equals(Set.of("jojo_ripples.subtitle.yare_yare", "jojo_ripples.subtitle.yare_yare_daze")),
				"both official lines of the cap pose, each with its subtitle: " + subtitles);

		PlayVoiceLinePacket point = strikeAndHear(jotaro, "point");
		helper.assertTrue(point != null && packs.pick(point.triggerId(), point.speaker(), RandomSource.create(1)) == null,
				"the point pose has no line in the packs: " + point);
		helper.assertTrue(strikeAndHear(nobody, "cap") == null, "a poser who counts as no character says nothing");

		List<String> problems = new ArrayList<>();
		packs.linesFor(capTrigger, line.speaker()).forEach(packLine -> packLine.sounds().forEach(sound -> {
			String path = "assets/" + sound.getNamespace() + "/sounds/" + sound.getPath() + ".ogg";
			if (JojoMod.class.getClassLoader().getResource(path) == null) problems.add("missing clip " + path);
		}));
		helper.assertTrue(problems.isEmpty(), "pose voice clips: " + problems);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void thePoseReachesThePoserAndEveryPlayerTrackingIt(GameTestHelper helper) {
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		Recorded watcher = Recorded.add(helper, "pose-watcher", origin.add(1, 0, 0));
		Recorded poser = null;
		try {
			poser = Recorded.add(helper, "pose-poser", origin);
			int poserId = poser.player.getId();
			helper.assertTrue(watcher.connection.packets.stream().anyMatch(packet -> packet instanceof ClientboundAddEntityPacket add
					&& add.getId() == poserId), "Fixture: the watcher does not track the poser");

			int poserFrom = poser.connection.packets.size();
			int watcherFrom = watcher.connection.packets.size();
			ClJojoPosePacket request = overTheWire(helper, ClJojoPosePacket.start(JOTARO_SET, "volume13"));
			helper.assertTrue(ClJojoPosePacket.handleOnServer(request, poser.player), "the server must take the pose request");
			for (Recorded who : List.of(poser, watcher)) {
				int from = who == poser ? poserFrom : watcherFrom;
				TrEntityActionInstancePacket sent = who.connection.actionPacket(from, poserId);
				helper.assertTrue(sent != null, (who == poser ? "the poser" : "the tracking player") + " must be sent the pose");
				EntityActionInstance copy = writeAndRead(helper, sent);
				helper.assertTrue(copy instanceof PosingInstance posing && posing.poseSet.equals(JOTARO_SET)
						&& posing.poseName.equals("volume13") && posing.getPhase() == ActionPhase.PERFORM
						&& posing.getEntityAnim().name().equals("volume13"),
						"the copy sent holds the same pose in its perform: " + describe(copy));
			}

			poserFrom = poser.connection.packets.size();
			watcherFrom = watcher.connection.packets.size();
			helper.assertTrue(ClJojoPosePacket.handleOnServer(overTheWire(helper, ClJojoPosePacket.stop()), poser.player)
					&& JojoPoseAction.getPose(poser.player) == null, "the stop request must end the pose");
			for (Recorded who : List.of(poser, watcher)) {
				int from = who == poser ? poserFrom : watcherFrom;
				helper.assertTrue(who.connection.phaseSyncs(from, poserId).stream().anyMatch(sync -> sync.phase() == null),
						(who == poser ? "the poser" : "the tracking player") + " must be told the pose ended");
			}
		}
		finally {
			watcher.player.discard();
			if (poser != null) poser.player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void theOfficialRigBonesFoldOntoOurs(GameTestHelper helper) {
		helper.assertTrue(OfficialPlayerBones.isOfficialOnly("body_rot") && OfficialPlayerBones.isOfficialOnly("left_item")
				&& !OfficialPlayerBones.isOfficialOnly("body") && !OfficialPlayerBones.isOfficialOnly("torso"),
				"body_rot and the item bones are the official rig's; body and torso are ours too");
		// what the official rig draws: body_rot, then body inside it, then a point on the body
		Vector3f outerRot = new Vector3f(0.3F, (float) Math.toRadians(104), -0.2F);
		Vector3f outerPos = new Vector3f(1, -2, 0.5F);
		Vector3f innerRot = new Vector3f(-0.6F, 0.1F, 0.12F);
		Vector3f innerPos = new Vector3f(-0.05F, 0.16F, -2.24F);
		Vector3f pivot = new Vector3f(0, 12, 0);
		Vector3f point = new Vector3f(3, -7, 1);
		Vector3f official = new Vector3f(point);
		rotation(innerRot).transform(official);
		official.add(pivot).add(innerPos);
		rotation(outerRot).transform(official);
		official.add(outerPos);

		Vector3f rot = new Vector3f();
		Vector3f pos = new Vector3f();
		OfficialPlayerBones.fold(outerRot, outerPos, innerRot, innerPos, pivot, rot, pos);
		Vector3f ours = new Vector3f(point);
		rotation(rot).transform(ours);
		ours.add(pivot).add(pos);
		helper.assertTrue(ours.distance(official) < 1.0E-3F, "the folded body must put each point where body_rot and body did: "
				+ ours + " against " + official);
		helper.succeed();
	}


	private static Quaternionf rotation(Vector3f rot) {
		return new Quaternionf().rotationZYX(rot.z, rot.y, rot.x);
	}

	@Nullable
	private static PlayVoiceLinePacket strikeAndHear(Player player, String pose) {
		var data = player.getData(ModDataAttachmentTypes.PLAYER_VOICE_LINES.get());
		data.setLastSaid(null);
		if (!JojoPoseAction.start(player, JOTARO_SET, pose)) {
			throw new GameTestAssertException("setup: " + pose + " could not start");
		}
		LivingComponentAction.getComponent(player).tick();
		return data.getLastSaid();
	}

	private static EntityActionInstance heldAction(rotp.core.powersystem.entityaction.type.EntityActionType type) {
		EntityActionInstance action = new EntityActionInstance(type);
		action.phasesLength.put(ActionPhase.PERFORM, 100.0F);
		action.setStartingPhase();
		return action;
	}

	private static String describe(@Nullable EntityActionInstance action) {
		if (action == null) return "no action";
		String pose = action instanceof PosingInstance posing ? " " + posing.poseSet + "#" + posing.poseName : "";
		return action.getClass().getSimpleName() + pose + " in " + action.getPhase();
	}

	private static List<String> names(List<PoseRef> poses) {
		return poses.stream().map(PoseRef::pose).collect(Collectors.toList());
	}

	private static PlayVoiceLinePacket overTheWire(GameTestHelper helper, PlayVoiceLinePacket packet) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			PlayVoiceLinePacket.Handler handler = new PlayVoiceLinePacket.Handler(JojoMod.resLoc("voiceline"));
			handler.encode(packet, buf);
			return handler.decode(buf);
		}
		finally {
			buf.release();
		}
	}

	private static ClJojoPosePacket overTheWire(GameTestHelper helper, ClJojoPosePacket packet) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			ClJojoPosePacket.Handler handler = new ClJojoPosePacket.Handler(JojoMod.resLoc("cljjpose"));
			handler.encode(packet, buf);
			ClJojoPosePacket read = handler.decode(buf);
			helper.assertTrue(read.equals(packet) && !buf.isReadable(), "the pose request reads back as " + read + " from " + packet);
			return read;
		}
		finally {
			buf.release();
		}
	}

	// as the client reads the action packet (ClientEntityActionSyncQueue.applyAction)
	private static EntityActionInstance writeAndRead(GameTestHelper helper, TrEntityActionInstancePacket packet) {
		RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			new TrEntityActionInstancePacket.Handler(JojoMod.resLoc("action")).encode(packet, wire);
			wire.readInt();
			wire.readUUID();
			wire.readVarLong();
			int slot = wire.readUnsignedByte();
			helper.assertTrue(slot == LivingComponentAction.MAIN_SLOT, "the pose goes in the poser's own slot, not " + slot);
			EntityActionInstance action = EntityActionInstance.decode(helper.getLevel(), wire);
			helper.assertTrue(!wire.isReadable(), "the pose packet has " + wire.readableBytes() + " bytes after its action");
			return action;
		}
		finally {
			wire.release();
		}
	}

	private static void wearFullSet(GameTestHelper helper, Player player, String setId) {
		Holder<ClothesSet> set = helper.getLevel().registryAccess().registryOrThrow(JojoRegistries.CLOTHES_SETS_REG_KEY)
				.getHolderOrThrow(ResourceKey.create(JojoRegistries.CLOTHES_SETS_REG_KEY, JojoMod.resLoc(setId)));
		wear(player, set);
	}

	// the Jotaro anime pieces, made out to another character and part
	private static void wearSetOf(GameTestHelper helper, Player player, String character, String part) {
		var registries = helper.getLevel().registryAccess();
		ClothesSet anime = registries.registryOrThrow(JojoRegistries.CLOTHES_SETS_REG_KEY).get(JojoMod.resLoc("jotaro_p3_anime"));
		Holder<StoryCharacter> who = registries.registryOrThrow(JojoRegistries.STORY_CHARACTERS_REG_KEY)
				.getHolderOrThrow(ResourceKey.create(JojoRegistries.STORY_CHARACTERS_REG_KEY, JojoMod.resLoc(character)));
		Holder<StoryPart> storyPart = registries.registryOrThrow(JojoRegistries.STORY_PARTS_REG_KEY)
				.getHolderOrThrow(ResourceKey.create(JojoRegistries.STORY_PARTS_REG_KEY, JojoMod.resLoc(part)));
		Map<ClothesSlotType, ClothesPiece> pieces = new LinkedHashMap<>();
		for (ClothesSlotType slot : ClothesSlotType.values()) {
			pieces.put(slot, anime.getPiece(slot));
		}
		wear(player, Holder.direct(new ClothesSet(who, Optional.of(storyPart), pieces, Optional.empty())));
	}

	private static void wear(Player player, Holder<ClothesSet> set) {
		EntityClothesInventory clothes = player.getData(ModDataAttachmentTypes.HUMANOID_CLOTHES.get());
		for (ClothesSlotType slot : ClothesSlotType.values()) {
			clothes.setItemSlot(slot, ModItems.CLOTHES_BASE_ITEM.get().makeClothesPieceStack(
					new ClothesDataComponent(set, slot, ClothesPiece.SubClothingPiece.FULL)));
		}
	}

	private static Map<String, String> shippedPoseFiles() {
		Map<String, String> files = new HashMap<>();
		for (String file : List.of("animation.json", "data.json")) {
			String path = "jojo_rotp_clothes/jojo_pose/jotaro_p3/" + file;
			files.put(path, readText("assets/" + path));
		}
		return files;
	}

	private static JojoPoseSets shippedPoseSets() {
		try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(new MemoryPack("mod/jojo_ripples", shippedPoseFiles())))) {
			return JojoPoseSets.load(resources);
		}
	}

	/** The core's voice packs with the pose pack, read from the mod's own files as a client would. */
	private static VoiceLinePacks shippedVoicePacks() {
		Map<String, String> files = new HashMap<>();
		for (String pack : List.of("jojo_ripples/voicelines/jotaro_p3", "jojo_rotp_clothes/voicelines/jotaro_p3")) {
			for (String file : List.of("data.json", "sounds.json")) {
				files.put(pack + "/" + file, readText("assets/" + pack + "/" + file));
			}
		}
		try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(new MemoryPack("mod/jojo_ripples", files)))) {
			return VoiceLinePacks.load(resources);
		}
	}

	private static String readText(String path) {
		try (InputStream in = JojoMod.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) throw new GameTestAssertException(path + " is missing");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new GameTestAssertException("Could not read " + path + ": " + e);
		}
	}

	private static JsonObject readJson(String path) {
		return JsonParser.parseString(readText(path)).getAsJsonObject();
	}

	/** A fake player in the level whose connection keeps what the server sends it. */
	private record Recorded(FakePlayer player, RecordingConnection connection) {
		static Recorded add(GameTestHelper helper, String name, Vec3 at) {
			FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
			player.setNoGravity(true);
			RecordingConnection connection = new RecordingConnection(player);
			player.connection = connection;
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add " + name);
			// chunks a fake player never receives: it tracks entities only in chunks it holds
			ChunkPos chunk = player.chunkPosition();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					connection.chunkSender.dropChunk(player, new ChunkPos(chunk.x + dx, chunk.z + dz));
				}
			}
			return new Recorded(player, connection);
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(FakePlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Nullable
		TrEntityActionInstancePacket actionPacket(int from, int performerId) {
			for (Packet<?> packet : packets.subList(from, packets.size())) {
				if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof TrEntityActionInstancePacket sent) {
					RegistryFriendlyByteBuf peek = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
					try {
						new TrEntityActionInstancePacket.Handler(JojoMod.resLoc("action")).encode(sent, peek);
						if (peek.readInt() == performerId) return sent;
					}
					finally {
						peek.release();
					}
				}
			}
			return null;
		}

		List<TrEntityActionPhaseTimePacket> phaseSyncs(int from, int performerId) {
			List<TrEntityActionPhaseTimePacket> syncs = new ArrayList<>();
			for (Packet<?> packet : packets.subList(from, packets.size())) {
				if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof TrEntityActionPhaseTimePacket sync
						&& sync.performerId() == performerId) {
					syncs.add(sync);
				}
			}
			return syncs;
		}

		@Override
		public void send(Packet<?> packet) {
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(this::send);
			}
			else {
				packets.add(packet);
			}
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			send(packet);
		}
	}

	/** A resource pack held in memory: "namespace/path" to the file's text. */
	private static final class MemoryPack implements PackResources {
		private final PackLocationInfo location;
		private final Map<ResourceLocation, String> files = new HashMap<>();

		MemoryPack(String id, Map<String, String> files) {
			this.location = new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty());
			files.forEach((path, text) -> {
				int slash = path.indexOf('/');
				this.files.put(ResourceLocation.fromNamespaceAndPath(path.substring(0, slash), path.substring(slash + 1)), text);
			});
		}

		@Override @Nullable
		public IoSupplier<InputStream> getRootResource(String... elements) {
			return null;
		}

		@Override @Nullable
		public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
			String text = packType == PackType.CLIENT_RESOURCES ? files.get(location) : null;
			return text == null ? null : () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput output) {
			if (packType != PackType.CLIENT_RESOURCES) return;
			files.keySet().stream()
					.filter(file -> file.getNamespace().equals(namespace) && file.getPath().startsWith(path + "/"))
					.forEach(file -> output.accept(file, getResource(packType, file)));
		}

		@Override
		public Set<String> getNamespaces(PackType type) {
			return type == PackType.CLIENT_RESOURCES
					? files.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toSet())
					: Set.of();
		}

		@Override @Nullable
		public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
			return null;
		}

		@Override
		public PackLocationInfo location() {
			return location;
		}

		@Override
		public void close() {}
	}
}
