package rotp.core.gametest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.config.client.ClientModSettings;
import rotp.core.mechanics.clothes.CharacterFromClothes;
import rotp.core.mechanics.clothes.ClothesArmorPriority;
import rotp.core.mechanics.clothes.EntityClothesInventory;
import rotp.core.mechanics.clothes.container.PlayerClothesMenu;
import rotp.core.mechanics.clothes.itemdata.ClothesDataComponent;
import rotp.core.mechanics.clothes.itemdata.ClothesPiece;
import rotp.core.mechanics.clothes.itemdata.ClothesSet;
import rotp.core.mechanics.clothes.itemdata.ClothesSlotType;
import rotp.core.mechanics.clothes.itemdata.StoryCharacter;
import rotp.core.mechanics.clothes.mannequin.MannequinEntity;
import rotp.core.subsystems.MergingDatapackRegistry;
import rotp.core.subsystems.StoryPart;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The clothes characters: the list-and-merge file format, the character a wearer counts as, and the clothes-or-armor setting. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClothesCharacterGameTests {
	private static final String CHARACTER_DIR = "ripples_clothes/character/";

	private ClothesCharacterGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aCharacterFileFromTwoPacksKeepsTheStandsOfBoth(GameTestHelper helper) {
		Map<String, String> modFiles = Map.of(
				"jojo_ripples/" + CHARACTER_DIR + "joseph.json", "{\"stand\": \"rotp_zhp:hermito_purple\"}",
				"jojo_ripples/" + CHARACTER_DIR + "jotaro.json", "{\"stand\": \"jojo_ripples:star_platinum\"}",
				"jojo_ripples/" + CHARACTER_DIR + "jonathan.json", "{}",
				"jojo_ripples/" + CHARACTER_DIR + "dio.json", "{\"stand\": \"jojo_ripples:the_world\"}");
		Map<String, String> addonFiles = Map.of(
				"jojo_ripples/" + CHARACTER_DIR + "dio.json", "{\"stand\": \"rotp_ova_addon:the_world_ova\"}");
		Map<String, String> userFiles = Map.of(
				"jojo_ripples/" + CHARACTER_DIR + "joseph.json", "{\"stand\": [\"pack_b:other_purple\", \"rotp_zhp:hermito_purple\"]}",
				"jojo_ripples/" + CHARACTER_DIR + "jonathan.json", "{\"stand\": \"pack_b:overdrive\"}",
				"pack_b/" + CHARACTER_DIR + "newcomer.json", "{}");
		var modPack = new MemoryPack("mod/jojo_ripples", Optional.of(new KnownPack("neoforge", "mod/jojo_ripples", "1")), modFiles);
		var addonPack = new MemoryPack("mod/rotp_ova_addon", Optional.of(new KnownPack("neoforge", "mod/rotp_ova_addon", "1")), addonFiles);
		var userPack = new MemoryPack("file/user_pack", Optional.empty(), userFiles);

		MappedRegistry<StoryCharacter> registry = new MappedRegistry<>(JojoRegistries.STORY_CHARACTERS_REG_KEY, Lifecycle.stable());
		Map<ResourceKey<?>, Exception> errors = new LinkedHashMap<>();
		try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(modPack, addonPack, userPack))) {
			MergingDatapackRegistry.loadContents(resources, NO_LOOKUP, registry, StoryCharacter.DIRECT_CODEC, StoryCharacter::merge, errors);
		}
		helper.assertTrue(errors.isEmpty(), "the two packs must load without errors: " + errors);
		helper.assertTrue(registry.size() == 5, "one entry per character id, not per file: " + registry.keySet());
		helper.assertTrue(stands(registry, "jojo_ripples:dio").equals(List.of("jojo_ripples:the_world", "rotp_ova_addon:the_world_ova")),
				"an add-on's file adds its Stand to the core's character: " + stands(registry, "jojo_ripples:dio"));
		helper.assertTrue(info(registry, "jojo_ripples:dio").knownPackInfo().isEmpty(),
				"a merged character must not claim a known pack, even when every copy came from one: a client would read only its own top file");

		helper.assertTrue(stands(registry, "jojo_ripples:joseph").equals(List.of("rotp_zhp:hermito_purple", "pack_b:other_purple")),
				"a list file merges into the single-id file, each Stand once, the lower pack's first: "
						+ stands(registry, "jojo_ripples:joseph"));
		helper.assertTrue(stands(registry, "jojo_ripples:jonathan").equals(List.of("pack_b:overdrive")),
				"a pack gives Stands to a character the mod left without one: " + stands(registry, "jojo_ripples:jonathan"));
		helper.assertTrue(stands(registry, "jojo_ripples:jotaro").equals(List.of("jojo_ripples:star_platinum")),
				"a character only one pack has stays as that pack wrote it");
		helper.assertTrue(stands(registry, "pack_b:newcomer").isEmpty(), "a character with no \"stand\" has none");

		helper.assertTrue(info(registry, "jojo_ripples:joseph").knownPackInfo().isEmpty(),
				"a merged character must not claim a known pack, or a client would read only its own top file");
		helper.assertTrue(info(registry, "jojo_ripples:jotaro").knownPackInfo().equals(modPack.location.knownPackInfo()),
				"a character from one known pack keeps it, as vanilla does");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theCharacterRegistryLoadsThroughTheMerge(GameTestHelper helper) {
		helper.assertTrue(MergingDatapackRegistry.hasLoaded(JojoRegistries.STORY_CHARACTERS_REG_KEY),
				"the game's character registry must be loaded by the merging loader");
		Registry<StoryCharacter> characters = helper.getLevel().registryAccess().registryOrThrow(JojoRegistries.STORY_CHARACTERS_REG_KEY);
		helper.assertTrue(stands(characters, "jojo_ripples:jotaro").equals(List.of("jojo_ripples:star_platinum")),
				"Jotaro's file must load: " + stands(characters, "jojo_ripples:jotaro"));
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theOfficialCharactersLoadWithTheirNamesAndStands(GameTestHelper helper) {
		Registry<StoryCharacter> characters = helper.getLevel().registryAccess().registryOrThrow(JojoRegistries.STORY_CHARACTERS_REG_KEY);
		helper.assertTrue(characters.size() >= 61, "the 61 official characters must load: " + characters.size());
		helper.assertTrue(stands(characters, "jojo_ripples:pucci").equals(List.of("rotp_whitesnake:whitesnake", "rotp_cm:cmoon",
				"cmoon_rm:cmoon", "rotp_mih:made_in_heaven")), "a list of Stands must load in order: " + stands(characters, "jojo_ripples:pucci"));
		helper.assertTrue(stands(characters, "jojo_ripples:joseph").equals(List.of("rotp_zhp:hermito_purple")),
				"the official 1.16 add-on id must map to ours: " + stands(characters, "jojo_ripples:joseph"));
		helper.assertTrue(stands(characters, "jojo_ripples:caesar").isEmpty(), "a character without a Stand has none");

		var en = readClothesLang("en_us");
		var cn = readClothesLang("zh_cn");
		var tw = readClothesLang("zh_tw");
		List<String> problems = new java.util.ArrayList<>();
		for (ResourceLocation id : characters.keySet()) {
			if (!id.getNamespace().equals(JojoMod.MOD_ID)) continue;
			for (ResourceLocation stand : characters.get(id).getStandIds()) {
				if (stand.getNamespace().equals(JojoMod.MOD_ID) && !JojoRegistries.DEFAULT_STANDS_REG.containsKey(stand)) {
					problems.add(id + " names " + stand + ", which the core does not register");
				}
			}
			String key = "character." + id.getNamespace() + "." + id.getPath();
			for (String name : List.of(key, key + ".short")) {
				if (!en.has(name)) problems.add("en_us lacks " + name);
				for (var lang : List.of(Map.entry("zh_cn", cn), Map.entry("zh_tw", tw))) {
					if (!lang.getValue().has(name)) problems.add(lang.getKey() + " lacks " + name);
					else if (en.has(name) && lang.getValue().get(name).getAsString().equals(en.get(name).getAsString())) {
						problems.add(lang.getKey() + " leaves " + name + " in English");
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), "character data and names: " + problems);
		helper.succeed();
	}

	private static com.google.gson.JsonObject readClothesLang(String locale) {
		String path = "assets/jojo_rotp_clothes/lang/" + locale + ".json";
		try (InputStream in = JojoMod.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) throw new GameTestAssertException(path + " is missing");
			return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (java.io.IOException e) {
			throw new GameTestAssertException("Could not read " + path + ": " + e);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theWearerCountsAsTheCharacterAllWornPiecesShare(GameTestHelper helper) {
		MannequinEntity mannequin = helper.spawn(ModEntityTypes.MANNEQUIN.get(), 1, 2, 1);
		helper.assertTrue(CharacterFromClothes.of(mannequin).equals(CharacterFromClothes.NONE), "nothing worn: no character");
		helper.assertTrue(CharacterFromClothes.of(null).equals(CharacterFromClothes.NONE), "no entity: no character");
		EntityClothesInventory clothes = mannequin.getData(ModDataAttachmentTypes.HUMANOID_CLOTHES.get());
		var registries = helper.getLevel().registryAccess();
		Holder<ClothesSet> anime = registries.registryOrThrow(JojoRegistries.CLOTHES_SETS_REG_KEY).getHolderOrThrow(
				ResourceKey.create(JojoRegistries.CLOTHES_SETS_REG_KEY, JojoMod.resLoc("jotaro_p3_anime")));
		Holder<ClothesSet> manga = registries.registryOrThrow(JojoRegistries.CLOTHES_SETS_REG_KEY).getHolderOrThrow(
				ResourceKey.create(JojoRegistries.CLOTHES_SETS_REG_KEY, JojoMod.resLoc("jotaro_p3_manga")));
		Holder<StoryPart> p3 = anime.value().getStoryPart().orElseThrow();
		ResourceLocation jotaro = JojoMod.resLoc("jotaro");

		clothes.setItemSlot(ClothesSlotType.HEAD, piece(anime, ClothesSlotType.HEAD));
		clothes.setItemSlot(ClothesSlotType.CHEST, piece(anime, ClothesSlotType.CHEST));
		assertWorn(helper, mannequin, jotaro, p3, "Jotaro's anime cap and coat");
		clothes.setItemSlot(ClothesSlotType.LEGS, piece(manga, ClothesSlotType.LEGS));
		assertWorn(helper, mannequin, jotaro, p3, "two sets of the same character and part");
		helper.assertTrue(clothes.getCharacter() != null && clothes.getCharacter().is(jotaro) && clothes.getStoryPart() == p3,
				"the clothes inventory must name the same character and part");

		Holder<StoryCharacter> someoneElse = Holder.direct(new StoryCharacter(Optional.empty()));
		var otherCharacterSet = Holder.direct(new ClothesSet(someoneElse, Optional.of(p3),
				Map.of(ClothesSlotType.FEET, anime.value().getPiece(ClothesSlotType.FEET)), Optional.empty()));
		clothes.setItemSlot(ClothesSlotType.FEET, piece(otherCharacterSet, ClothesSlotType.FEET));
		assertWorn(helper, mannequin, null, p3, "pieces of two characters from the same part");

		var noPartSet = Holder.direct(new ClothesSet(anime.value().getCharacter(), Optional.empty(),
				Map.of(ClothesSlotType.FEET, anime.value().getPiece(ClothesSlotType.FEET)), Optional.empty()));
		clothes.setItemSlot(ClothesSlotType.FEET, piece(noPartSet, ClothesSlotType.FEET));
		assertWorn(helper, mannequin, jotaro, null, "a Jotaro piece without a story part");

		clothes.clearContent();
		helper.assertTrue(CharacterFromClothes.of(mannequin).equals(CharacterFromClothes.NONE), "all taken off: no character");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theSettingPicksClothesOrArmorWhereBothAreWorn(GameTestHelper helper) {
		helper.assertTrue(new ClientModSettings.Settings().clothesArmorPriority == ClothesArmorPriority.CLOTHES,
				"clothes must be shown over armor by default, as in the official");
		MannequinEntity mannequin = helper.spawn(ModEntityTypes.MANNEQUIN.get(), 1, 2, 1);
		EntityClothesInventory clothes = mannequin.getData(ModDataAttachmentTypes.HUMANOID_CLOTHES.get());
		Holder<ClothesSet> anime = helper.getLevel().registryAccess().registryOrThrow(JojoRegistries.CLOTHES_SETS_REG_KEY).getHolderOrThrow(
				ResourceKey.create(JojoRegistries.CLOTHES_SETS_REG_KEY, JojoMod.resLoc("jotaro_p3_anime")));
		mannequin.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		clothes.setItemSlot(ClothesSlotType.HEAD, piece(anime, ClothesSlotType.HEAD));
		clothes.setItemSlot(ClothesSlotType.CHEST, piece(anime, ClothesSlotType.CHEST));
		mannequin.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));

		ClothesArmorPriority clothesFirst = ClothesArmorPriority.CLOTHES;
		helper.assertTrue(!clothesFirst.showsArmor(mannequin, EquipmentSlot.HEAD) && clothesFirst.showsClothes(mannequin, ClothesSlotType.HEAD),
				"clothes first: the cap hides the helmet");
		helper.assertTrue(clothesFirst.showsArmor(mannequin, EquipmentSlot.LEGS) && clothesFirst.showsClothes(mannequin, ClothesSlotType.CHEST),
				"clothes first: leggings without clothes and a coat without armor are both shown");

		ClothesArmorPriority armorFirst = ClothesArmorPriority.ARMOR;
		helper.assertTrue(armorFirst.showsArmor(mannequin, EquipmentSlot.HEAD) && !armorFirst.showsClothes(mannequin, ClothesSlotType.HEAD),
				"armor first: the helmet hides the cap");
		helper.assertTrue(armorFirst.showsArmor(mannequin, EquipmentSlot.LEGS) && armorFirst.showsClothes(mannequin, ClothesSlotType.CHEST),
				"armor first: leggings without clothes and a coat without armor are both shown");
		helper.assertTrue(clothesFirst.showsArmor(mannequin, EquipmentSlot.MAINHAND), "a hand item is never clothes' to hide");
		helper.succeed();
	}

	/** The official layout: every slot 12 pixels lower than the vanilla inventory's, under the line naming the character. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theClothesScreenSlotsSitUnderTheCharacterLine(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		PlayerClothesMenu menu = new PlayerClothesMenu(1, player.getInventory());
		helper.assertTrue(menu.slots.size() == 45, "4 armor, 4 clothes, offhand, 27 inventory and 9 hotbar slots: " + menu.slots.size());
		// slot index, x, y
		int[][] expected = { {0, 8, 20}, {3, 8, 74}, {4, 77, 20}, {7, 77, 74}, {8, 95, 74}, {9, 8, 96}, {35, 152, 132}, {36, 8, 154}, {44, 152, 154} };
		for (int[] slot : expected) {
			Slot actual = menu.slots.get(slot[0]);
			helper.assertTrue(actual.x == slot[1] && actual.y == slot[2],
					"slot " + slot[0] + " must be at " + slot[1] + "," + slot[2] + " in the official layout, not " + actual.x + "," + actual.y);
		}
		helper.succeed();
	}

	private static ItemStack piece(Holder<ClothesSet> set, ClothesSlotType slot) {
		return ModItems.CLOTHES_BASE_ITEM.get().makeClothesPieceStack(new ClothesDataComponent(set, slot, ClothesPiece.SubClothingPiece.FULL));
	}

	private static void assertWorn(GameTestHelper helper, LivingEntity wearer, @Nullable ResourceLocation character,
			@Nullable Holder<StoryPart> part, String worn) {
		CharacterFromClothes found = CharacterFromClothes.of(wearer);
		helper.assertTrue(character == null ? found.character().isEmpty() : found.character().filter(c -> c.is(character)).isPresent(),
				worn + " must count as " + (character == null ? "no character" : character) + ", not " + found.character());
		helper.assertTrue(part == null ? found.storyPart().isEmpty() : found.storyPart().filter(p -> p == part).isPresent(),
				worn + " must count as " + (part == null ? "no story part" : "part " + part.getKey()) + ", not " + found.storyPart());
	}

	static List<String> stands(Registry<StoryCharacter> registry, String id) {
		StoryCharacter character = registry.get(ResourceLocation.parse(id));
		if (character == null) throw new GameTestAssertException("no character " + id + " in " + registry.keySet());
		return character.getStandIds().stream().map(ResourceLocation::toString).collect(Collectors.toList());
	}

	private static RegistrationInfo info(Registry<StoryCharacter> registry, String id) {
		return registry.registrationInfo(ResourceKey.create(JojoRegistries.STORY_CHARACTERS_REG_KEY, ResourceLocation.parse(id)))
				.orElseThrow();
	}

	private static final RegistryOps.RegistryInfoLookup NO_LOOKUP = new RegistryOps.RegistryInfoLookup() {
		@Override
		public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
			return Optional.empty();
		}
	};

	/** A data pack held in memory: "namespace/path" to the file's text. */
	private static final class MemoryPack implements PackResources {
		private final PackLocationInfo location;
		private final Map<ResourceLocation, String> files = new HashMap<>();

		MemoryPack(String id, Optional<KnownPack> knownPack, Map<String, String> files) {
			this.location = new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, knownPack);
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
			String text = packType == PackType.SERVER_DATA ? files.get(location) : null;
			return text == null ? null : () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput output) {
			if (packType != PackType.SERVER_DATA) return;
			files.keySet().stream()
					.filter(file -> file.getNamespace().equals(namespace) && file.getPath().startsWith(path + "/"))
					.forEach(file -> output.accept(file, getResource(packType, file)));
		}

		@Override
		public Set<String> getNamespaces(PackType type) {
			return type == PackType.SERVER_DATA
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
