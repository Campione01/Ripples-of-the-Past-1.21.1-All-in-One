package rotp.core.gametest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.PlayerVoiceLineData;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.clothes.EntityClothesInventory;
import rotp.core.mechanics.clothes.itemdata.ClothesDataComponent;
import rotp.core.mechanics.clothes.itemdata.ClothesPiece;
import rotp.core.mechanics.clothes.itemdata.ClothesSet;
import rotp.core.mechanics.clothes.itemdata.ClothesSlotType;
import rotp.core.mechanics.clothes.itemdata.StoryCharacter;
import rotp.core.mechanics.voiceline.VoiceLinePacks;
import rotp.core.mechanics.voiceline.VoiceLinePacks.PackLine;
import rotp.core.mechanics.voiceline.VoiceLinePacks.PickedLine;
import rotp.core.mechanics.voiceline.VoiceLineSpeaker;
import rotp.core.network.s2c.PlayVoiceLinePacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.StoryPart;
import rotp.core.util.sound.MultiSoundEventResolver;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The official voice packs over 1.16's shouts: 1.16's line plays for everyone, and a pack's line replaces it, or is
 * added where 1.16 had none, for a speaker whose clothes make them that pack's character.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VoiceLinePackGameTests {
	// its own batch: in the default one these six moved the other tests' cells, and DirectionalBarrageGameTests'
	// occlusion test failed there in 2 runs of 2 (it passes without them)
	private static final String BATCH = "jojo_ripples_voice_packs";
	private static final String[] SHIPPED_PACKS = { "jotaro_p3", "josuke4", "joseph_p2" };
	private static final String[] NEW_CLIPS = { "stand_user/jotaro_yare_yare_1", "stand_user/jotaro_yare_yare_2",
			"stand_user/jotaro_yare_yare_3", "stand_user/jotaro_yare_yare_4", "stand_user/jotaro_yare_yare_daze_1",
			"stand_user/jotaro_yare_yare_daze_2", "stand_user/jotaro_yare_yare_daze_3", "hamon_user/joseph_scream_shooting" };

	private VoiceLinePackGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void withoutClothesTheShoutOf116Plays(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = give(helper, player, "star_platinum");
			PlayVoiceLinePacket line = summonAndHear(helper, player, power);
			helper.assertTrue(line.sound() != null && line.sound().is(MultiSoundEventResolver.resolve(ModSoundEvents.JOTARO_STAR_PLATINUM)),
					"Star Platinum's summon must say 1.16's Jotaro shout: " + line.sound());
			helper.assertTrue(!line.speaker().hasCharacter(), "a player in no clothes counts as no character: " + line.speaker());
			VoiceLinePacks packs = shippedPacks(helper);
			helper.assertTrue(packs.pick(line.triggerId(), line.speaker(), RandomSource.create(1)) == null,
					"with no character worn the client must keep 1.16's shout");
			VoiceLineSpeaker spUserInNoClothes = new VoiceLineSpeaker(Optional.empty(), Optional.of(JojoMod.resLoc("p3")),
					Optional.of(JojoMod.resLoc("star_platinum")));
			helper.assertTrue(packs.linesFor(ModSoundEvents.VOICELINE_STAND_SUMMON.getId(), spUserInNoClothes).isEmpty(),
					"no pack line is for a speaker who counts as no character");
		}
		finally {
			dismiss(player);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void theFullJotaroSetReplacesTheShoutWithThePackLine(GameTestHelper helper) {
		Player jotaro = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player dioStandJotaro = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = give(helper, jotaro, "star_platinum");
			wearFullSet(helper, jotaro, "jotaro_p3_anime");
			PlayVoiceLinePacket line = summonAndHear(helper, jotaro, power);
			helper.assertTrue(line.sound() != null && line.sound().is(MultiSoundEventResolver.resolve(ModSoundEvents.JOTARO_STAR_PLATINUM)),
					"the packet must still carry 1.16's shout for clients without the pack: " + line.sound());
			helper.assertTrue(ModSoundEvents.VOICELINE_STAND_SUMMON.getId().equals(line.triggerId()),
					"the summon shout must carry the pack trigger vl_stand_summon: " + line.triggerId());
			helper.assertTrue(line.speaker().equals(new VoiceLineSpeaker(Optional.of(JojoMod.resLoc("jotaro")),
					Optional.of(JojoMod.resLoc("p3")), Optional.of(JojoMod.resLoc("star_platinum")))),
					"the full anime set makes the speaker Jotaro of part 3 with Star Platinum: " + line.speaker());

			VoiceLinePacks packs = shippedPacks(helper);
			PickedLine picked = packs.pick(line.triggerId(), line.speaker(), RandomSource.create(7));
			helper.assertTrue(picked != null && picked.pack().equals(JojoMod.resLoc("jotaro_p3")),
					"the jotaro_p3 pack's summon line must replace 1.16's shout: " + picked);
			helper.assertTrue(picked.sound().getPath().startsWith("stand_user/jotaro_star_platinum")
					&& picked.subtitle().equals(Optional.of("jojo_ripples.subtitle.star_platinum_name")),
					"the pack's line is one of its Star Platinum clips, with its subtitle: " + picked);

			StandPower theWorld = give(helper, dioStandJotaro, "the_world");
			wearFullSet(helper, dioStandJotaro, "jotaro_p3_anime");
			PlayVoiceLinePacket dioLine = summonAndHear(helper, dioStandJotaro, theWorld);
			helper.assertTrue(dioLine.sound() != null && dioLine.sound().is(MultiSoundEventResolver.resolve(ModSoundEvents.DIO_THE_WORLD))
					&& dioLine.speaker().character().equals(Optional.of(JojoMod.resLoc("jotaro"))),
					"Jotaro's clothes on The World's user: 1.16's line with the speaker Jotaro: " + dioLine);
			helper.assertTrue(packs.pick(dioLine.triggerId(), dioLine.speaker(), RandomSource.create(7)) == null,
					"the Jotaro pack's summon line is for Star Platinum only, so 1.16's line stays for The World");
		}
		finally {
			dismiss(jotaro);
			dismiss(dioStandJotaro);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void aPackAddsItsLineWhere116HasNone(GameTestHelper helper) {
		Player joseph = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player nobody = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			helper.assertTrue(helper.getLevel().addFreshEntity(joseph) && helper.getLevel().addFreshEntity(nobody),
					"Could not add the Tommy Gun users");
			ItemStack gun = new ItemStack(ModItems.TOMMY_GUN.get());
			// no Hamon, so 1.16 says nothing when the magazine runs out
			gun.getItem().releaseUsing(gun, helper.getLevel(), nobody, 1);
			helper.assertTrue(lastSaid(nobody) == null, "1.16 has no line for a Tommy Gun user who is not Joseph: " + lastSaid(nobody));

			wearSetOf(helper, joseph, "joseph", "p2");
			gun.getItem().releaseUsing(gun, helper.getLevel(), joseph, 1);
			PlayVoiceLinePacket line = lastSaid(joseph);
			helper.assertTrue(line != null && line.sound() == null
					&& ModSoundEvents.VOICELINE_TOMMY_GUN_SHOT_MAGAZINE.getId().equals(line.triggerId()),
					"Joseph's part 2 clothes must send the pack trigger with no 1.16 line: " + line);
			PickedLine picked = shippedPacks(helper).pick(line.triggerId(), line.speaker(), RandomSource.create(3));
			helper.assertTrue(picked != null && picked.sound().equals(JojoMod.resLoc("hamon_user/joseph_war_declaration"))
					&& picked.subtitle().equals(Optional.of("jojo_ripples.subtitle.war_declaration")),
					"the joseph_p2 pack must add its war declaration: " + picked);
		}
		finally {
			joseph.discard();
			nobody.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void aMixedSetFallsBackTo116(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = give(helper, player, "star_platinum");
			wearFullSet(helper, player, "jotaro_p3_anime");
			ClothesSet josephSet = josephSet(helper, "joseph", "p3");
			player.getData(ModDataAttachmentTypes.HUMANOID_CLOTHES.get()).setItemSlot(ClothesSlotType.CHEST,
					piece(Holder.direct(josephSet), ClothesSlotType.CHEST));
			PlayVoiceLinePacket line = summonAndHear(helper, player, power);
			helper.assertTrue(!line.speaker().hasCharacter(),
					"Jotaro's cap with Joseph's coat makes no character: " + line.speaker());
			helper.assertTrue(line.sound() != null && line.sound().is(MultiSoundEventResolver.resolve(ModSoundEvents.JOTARO_STAR_PLATINUM)),
					"a mixed set keeps 1.16's shout: " + line.sound());
			helper.assertTrue(shippedPacks(helper).pick(line.triggerId(), line.speaker(), RandomSource.create(1)) == null,
					"a mixed set must not take a pack line");
		}
		finally {
			dismiss(player);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void theLoaderMergesEveryPacksCopy(GameTestHelper helper) {
		MemoryPack lower = new MemoryPack("mod/jojo_ripples", Map.of(
				"jojo_ripples/voicelines/jotaro_p3/data.json", "{\"character\": \"jojo_ripples:jotaro\", \"story_part\": \"jojo_ripples:p3\"}",
				"jojo_ripples/voicelines/jotaro_p3/sounds.json",
						"{\"vl_stand_summon\": {\"subtitle\": \"a\", \"sounds\": \"jojo_ripples:stand_user/a\"}}",
				"broken_ns/voicelines/broken/sounds.json", "{\"vl_stand_summon\": "));
		MemoryPack upper = new MemoryPack("file/user_pack", Map.of(
				"jojo_ripples/voicelines/jotaro_p3/data.json", "{\"story_part\": [\"jojo_ripples:p3\", \"jojo_ripples:p4\"]}",
				"jojo_ripples/voicelines/jotaro_p3/sounds.json", "{\"vl_stand_summon\": [{\"subtitle\": \"b\", \"sounds\": [\"jojo_ripples:stand_user/b\"]},"
						+ " {\"subtitle\": \"silent\", \"sounds\": []}], \"vl_time_resume\": {\"sounds\": \"jojo_ripples:stand_user/c\"}}",
				"addon_ns/voicelines/dio/sounds.json", "{\"jojo_ripples:vl_stand_summon\": {\"stand_type\": \"jojo_ripples:the_world\","
						+ " \"sounds\": \"addon_ns:dio_line\"}}"));
		VoiceLinePacks packs;
		try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(lower, upper))) {
			packs = VoiceLinePacks.load(resources);
		}
		ResourceLocation summon = ModSoundEvents.VOICELINE_STAND_SUMMON.getId();
		VoiceLineSpeaker jotaroP4 = speaker("jotaro", "p4", null);
		helper.assertTrue(subtitles(packs.linesFor(summon, jotaroP4)).equals(List.of("a", "b")),
				"both packs' lines for one trigger are kept, the lower pack's first: " + packs.linesFor(summon, jotaroP4));
		helper.assertTrue(subtitles(packs.linesFor(summon, speaker("jotaro", "p3", null))).equals(List.of("a", "b")),
				"the upper pack's story_part list replaces the lower one's");
		helper.assertTrue(packs.linesFor(summon, speaker("jotaro", "p5", null)).isEmpty(), "a part no copy names has no line");
		helper.assertTrue(packs.linesFor(JojoMod.resLoc("vl_time_resume"), jotaroP4).size() == 1
				&& packs.linesFor(JojoMod.resLoc("vl_time_resume"), jotaroP4).get(0).character().equals(JojoMod.resLoc("jotaro")),
				"the upper copy adds a trigger, for the character the lower copy's data names");
		List<PackLine> dio = packs.linesFor(summon, new VoiceLineSpeaker(Optional.of(ResourceLocation.fromNamespaceAndPath("addon_ns", "dio")),
				Optional.empty(), Optional.of(JojoMod.resLoc("the_world"))));
		helper.assertTrue(dio.size() == 1 && dio.get(0).sounds().equals(List.of(ResourceLocation.fromNamespaceAndPath("addon_ns", "dio_line"))),
				"a pack with no data.json is for the character of its own id, and a key may name another namespace's trigger: " + dio);
		helper.assertTrue(packs.linesFor(summon, new VoiceLineSpeaker(Optional.of(ResourceLocation.fromNamespaceAndPath("addon_ns", "dio")),
				Optional.empty(), Optional.of(JojoMod.resLoc("star_platinum")))).isEmpty(), "stand_type filters the line");
		helper.assertTrue(packs.packCount() == 3, "three packs, the broken one counted but empty: " + packs.packCount());
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void theShippedPacksAndTheNewClipsLoad(GameTestHelper helper) {
		VoiceLinePacks packs = shippedPacks(helper);
		helper.assertTrue(packs.packCount() == SHIPPED_PACKS.length, "the three official packs must load: " + packs.packCount());
		JsonObject en = readJson("assets/jojo_ripples/lang/en_us.json");
		JsonObject cn = readJson("assets/jojo_ripples/lang/zh_cn.json");
		JsonObject tw = readJson("assets/jojo_ripples/lang/zh_tw.json");
		List<String> problems = new ArrayList<>();
		Set<ResourceLocation> triggers = packs.allLines().keySet();
		for (ResourceLocation trigger : triggers) {
			if (!BuiltInRegistries.SOUND_EVENT.containsKey(trigger)) problems.add("trigger " + trigger + " is not a registered sound event");
			for (PackLine line : packs.allLines().get(trigger)) {
				line.sounds().forEach(sound -> checkClip(sound, problems));
				if (line.subtitle().isEmpty()) problems.add(line.pack() + " " + trigger + " has no subtitle");
				line.subtitle().ifPresent(key -> checkSubtitle(key, en, cn, tw, problems));
			}
		}
		helper.assertTrue(triggers.size() == 7, "the seven official triggers: " + triggers);
		helper.assertTrue(packs.allLines().get(JojoMod.resLoc("vl_tommy_gun_scream")).get(0).sounds()
				.equals(List.of(JojoMod.resLoc("hamon_user/joseph_scream_shooting"))), "Joseph's pack screams with the new clip");

		JsonObject sounds = readJson("assets/jojo_ripples/sounds.json");
		Set<String> clipsInEvents = new java.util.HashSet<>();
		for (String event : List.of("jotaro_yare_yare", "jotaro_yare_yare_daze")) {
			JsonObject definition = sounds.getAsJsonObject(event);
			if (definition == null) {
				problems.add("sounds.json lacks " + event);
				continue;
			}
			if (!BuiltInRegistries.SOUND_EVENT.containsKey(JojoMod.resLoc(event))) problems.add(event + " is not registered");
			checkSubtitle(definition.get("subtitle").getAsString(), en, cn, tw, problems);
			definition.getAsJsonArray("sounds").forEach(clip -> {
				checkClip(ResourceLocation.parse(clip.getAsString()), problems);
				clipsInEvents.add(ResourceLocation.parse(clip.getAsString()).getPath());
			});
		}
		for (String clip : NEW_CLIPS) {
			checkClip(JojoMod.resLoc(clip), problems);
			if (!clip.startsWith("hamon_user/") && !clipsInEvents.contains(clip)) problems.add(clip + " is in no sound event");
		}
		helper.assertTrue(problems.isEmpty(), "voice pack assets: " + problems);
		helper.succeed();
	}


	private static PlayVoiceLinePacket summonAndHear(GameTestHelper helper, Player player, StandPower power) {
		player.getData(ModDataAttachmentTypes.PLAYER_VOICE_LINES.get()).setLastSaid(null);
		helper.assertTrue(power.getPowerType().summon(player, power), "Could not summon " + power.getPowerType().getId());
		PlayVoiceLinePacket line = lastSaid(player);
		helper.assertTrue(line != null, "the summon said no line");
		return line;
	}

	@Nullable
	private static PlayVoiceLinePacket lastSaid(Player player) {
		PlayerVoiceLineData data = player.getData(ModDataAttachmentTypes.PLAYER_VOICE_LINES.get());
		return data.getLastSaid();
	}

	private static void wearFullSet(GameTestHelper helper, Player player, String setId) {
		Holder<ClothesSet> set = helper.getLevel().registryAccess().registryOrThrow(JojoRegistries.CLOTHES_SETS_REG_KEY)
				.getHolderOrThrow(ResourceKey.create(JojoRegistries.CLOTHES_SETS_REG_KEY, JojoMod.resLoc(setId)));
		EntityClothesInventory clothes = player.getData(ModDataAttachmentTypes.HUMANOID_CLOTHES.get());
		for (ClothesSlotType slot : ClothesSlotType.values()) {
			clothes.setItemSlot(slot, piece(set, slot));
		}
	}

	private static void wearSetOf(GameTestHelper helper, Player player, String character, String part) {
		Holder<ClothesSet> set = Holder.direct(josephSet(helper, character, part));
		EntityClothesInventory clothes = player.getData(ModDataAttachmentTypes.HUMANOID_CLOTHES.get());
		for (ClothesSlotType slot : ClothesSlotType.values()) {
			clothes.setItemSlot(slot, piece(set, slot));
		}
	}

	// the Jotaro anime pieces, made out to another character and part
	private static ClothesSet josephSet(GameTestHelper helper, String character, String part) {
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
		return new ClothesSet(who, Optional.of(storyPart), pieces, Optional.empty());
	}

	private static ItemStack piece(Holder<ClothesSet> set, ClothesSlotType slot) {
		return ModItems.CLOTHES_BASE_ITEM.get().makeClothesPieceStack(new ClothesDataComponent(set, slot, ClothesPiece.SubClothingPiece.FULL));
	}

	private static StandPower give(GameTestHelper helper, Player player, String standName) {
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the " + standName + " test player");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
		helper.assertTrue(type != null, "Missing Stand type " + standName);
		StandPower power = PowerClass.STAND.attachGet(player);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
		return power;
	}

	private static void dismiss(Player player) {
		StandPower power = PowerClass.STAND.get(player);
		if (power != null && power.hasPower() && power.isSummoned()) {
			power.getPowerType().forceUnsummon(player, power);
		}
		player.discard();
	}

	private static VoiceLineSpeaker speaker(String character, @Nullable String part, @Nullable String stand) {
		return new VoiceLineSpeaker(Optional.of(JojoMod.resLoc(character)), Optional.ofNullable(part).map(JojoMod::resLoc),
				Optional.ofNullable(stand).map(JojoMod::resLoc));
	}

	private static List<String> subtitles(List<PackLine> lines) {
		return lines.stream().map(line -> line.subtitle().orElse("-")).collect(Collectors.toList());
	}

	/** The packs the core ships, read from the mod's own files as a client would. */
	private static VoiceLinePacks shippedPacks(GameTestHelper helper) {
		Map<String, String> files = new HashMap<>();
		for (String pack : SHIPPED_PACKS) {
			for (String file : List.of("data.json", "sounds.json")) {
				String path = "jojo_ripples/voicelines/" + pack + "/" + file;
				files.put(path, readText("assets/" + path));
			}
		}
		try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(new MemoryPack("mod/jojo_ripples", files)))) {
			return VoiceLinePacks.load(resources);
		}
	}

	private static void checkClip(ResourceLocation sound, List<String> problems) {
		String path = "assets/" + sound.getNamespace() + "/sounds/" + sound.getPath() + ".ogg";
		if (JojoMod.class.getClassLoader().getResource(path) == null) problems.add("missing clip " + path);
	}

	private static void checkSubtitle(String key, JsonObject en, JsonObject cn, JsonObject tw, List<String> problems) {
		if (!en.has(key)) {
			problems.add("en_us lacks " + key);
			return;
		}
		for (var lang : List.of(Map.entry("zh_cn", cn), Map.entry("zh_tw", tw))) {
			if (!lang.getValue().has(key)) problems.add(lang.getKey() + " lacks " + key);
			else if (lang.getValue().get(key).getAsString().equals(en.get(key).getAsString())) problems.add(lang.getKey() + " leaves " + key + " in English");
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
