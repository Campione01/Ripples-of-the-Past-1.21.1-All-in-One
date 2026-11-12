package rotp.core.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.util.FakePlayer;
import rotp.core.init.ModMapDecorationTypes;
import rotp.core.mechanics.explorer_map_creation.ClExplorerMapDataPacket;
import rotp.core.mechanics.explorer_map_creation.ClExplorerMapModePacket;
import rotp.core.mechanics.explorer_map_creation.ExplorerMapMenu;
import rotp.core.mechanics.explorer_map_creation.ExplorerMapRules;
import rotp.core.mechanics.explorer_map_creation.ExplorerMapTables;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.client.ui.hud_power.ResolvePips;
import rotp.core.client.ui.tutorial_toast.TutorialTips;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.ResolveHudLayout;
import rotp.core.config.client.StandToggle;
import rotp.core.config.client.TutorialProgress;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.client.ClientConfigEditor;
import rotp.core.config.editor.client.ConfigWidgetRules;
import rotp.core.core.JojoMod;

/**
 * The official version's finished HUD and menu pieces, rewritten into this core's settings (lane OFFMENUS). The HUD is
 * client-only, so its wiring is read from its bytecode instead of being loaded.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OfficialMenusGameTests {
	private static final String HUD = "rotp/core/client/ui/hud_power/PowerHud";
	private static final String PIPS = "rotp/core/client/ui/hud_power/ResolvePips";
	private static final String SETTINGS = "rotp/core/config/client/ClientModSettings";
	private static final String LAYOUT = "rotp/core/config/client/ResolveHudLayout";
	private static final String TOGGLES_HUD = "rotp/core/client/ui/hud_power/StandTogglesHud";
	private static final String TIPS = "rotp/core/client/ui/tutorial_toast/TutorialTips";
	private static final String TOASTS = "rotp/core/client/ui/tutorial_toast/ModTutorialToasts";
	private static final String TOAST = "rotp/core/client/ui/tutorial_toast/TutorialToast";
	private static final String EXPLORER = "rotp/core/mechanics/explorer_map_creation/";

	private OfficialMenusGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void resolvePipsShowTheLevelTheHighestLevelAndTheMode(GameTestHelper helper) {
		expectPips(helper, ResolvePips.of(0, 0, 4, -1), 0, 0, 0, "level 0 shows four empty pips");
		expectPips(helper, ResolvePips.of(2, 3, 4, -1), 2, 3, 0, "level 2 of a highest 3 fills two and outlines three");
		expectPips(helper, ResolvePips.of(3, 1, 4, -1), 3, 3, 0, "the highest level is never shown below the level");
		expectPips(helper, ResolvePips.of(4, 4, 4, -1), 4, 4, 0, "the max level fills all four");
		expectPips(helper, ResolvePips.of(9, 9, 4, -1), 4, 4, 0, "a level past four is clamped to the pips");
		expectPips(helper, ResolvePips.of(2, 3, 4, 2), 0, 0, 3, "Resolve mode started at level 2 shows its level 3");
		expectPips(helper, ResolvePips.of(4, 4, 4, 4), 0, 0, 4, "Resolve mode at the max level shows four");
		expectPips(helper, ResolvePips.of(0, 0, 4, 0), 0, 0, 1, "Resolve mode from level 0 shows one");
		helper.assertTrue(ResolvePips.of(2, 2, 5, -1) == null && ResolvePips.of(1, 1, 3, -1) == null,
				"A Stand whose level count is not four must keep the strip");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void resolveLayoutSettingRoundTripsAndDefaultsToTheStrip(GameTestHelper helper) {
		helper.assertTrue(new ClientModSettings.Settings().resolveHudLayout == ResolveHudLayout.STRIP,
				"Today's strip must stay the default layout");
		helper.assertTrue(readSettings(helper, "{\"resolveHudLayout\": \"PIPS\", \"standExpBar\": true}").resolveHudLayout == ResolveHudLayout.PIPS,
				"A saved PIPS layout must load back");
		ClientModSettings.Settings unknown = readSettings(helper, "{\"resolveHudLayout\": \"SIDEWAYS\", \"standExpBar\": true}");
		helper.assertTrue(unknown.resolveHudLayout == ResolveHudLayout.STRIP && unknown.standExpBar,
				"An unknown layout must fall back to the strip and keep the other settings");
		helper.assertTrue(readSettings(helper, "{}").resolveHudLayout == ResolveHudLayout.STRIP, "A file without the key must load the strip");

		ConfigEntry entry = clientEntry(helper, "resolveHudLayout");
		helper.assertTrue(entry.kind() == ConfigEntry.Kind.ENUM && entry.choices().equals(List.of("STRIP", "PIPS"))
				&& "STRIP".equals(entry.defaultValue().getAsString()), "The settings row must offer STRIP and PIPS, STRIP by default: " + entry);
		helper.assertTrue(inSection(ConfigEntry.Side.CLIENT, "hud", "resolveHudLayout"), "The layout row must sit in the HUD section");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void resolveHudDrawsTheOfficialPipsWhenChosen(GameTestHelper helper) {
		Set<String> render = refs(helper, HUD + "$Resolve", "renderElement");
		helper.assertTrue(render.contains(SETTINGS + ".getSettingsReadOnly") && render.contains(LAYOUT + ".PIPS")
				&& render.contains(PIPS + ".of") && render.contains(HUD + "$Resolve.renderPips"),
				"The Resolve icon no longer draws the pips when the PIPS layout is chosen");
		helper.assertTrue(render.contains("rotp/core/powersystem/standpower/StandUtil.resolveLevelFill"),
				"The Resolve icon lost the strip, which stays the default");
		Set<String> pips = refs(helper, HUD + "$Resolve", "renderPips");
		for (String sprite : List.of("PIPS_MODE", "PIPS_EMPTY", "PIPS_OUTLINED", "PIPS_FILLED")) {
			helper.assertTrue(pips.contains(HUD + "$Resolve." + sprite), "The pips no longer draw " + sprite);
		}
		Set<String> sprites = refs(helper, HUD + "$Resolve", "<clinit>");
		for (String name : List.of("\"stages_h\"", "\"stage_%d_h\"", "\"stage_unlocked%d_h\"", "\"lvl_%d_h\"")) {
			helper.assertTrue(sprites.contains(name), "The pip sprites no longer include " + name);
		}
		List<String> textures = new ArrayList<>(List.of("stages_h"));
		for (int i = 1; i <= ResolvePips.PIPS; i++) {
			textures.addAll(List.of("stage_" + i + "_h", "stage_unlocked" + i + "_h", "lvl_" + i + "_h"));
		}
		for (String name : textures) {
			expectPng(helper, "/assets/" + JojoMod.MOD_ID + "/textures/hud/stand_resolve/stand_resolve_" + name + ".png", 32, 16);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void standToggleIconsCanStayOnTheHudEachByItsOwnSetting(GameTestHelper helper) {
		ClientModSettings.Settings defaults = new ClientModSettings.Settings();
		for (StandToggle toggle : StandToggle.values()) {
			helper.assertFalse(toggle.isAlwaysShown(defaults), toggle + " must only show after its key by default, as before");
		}
		ClientModSettings.Settings pickUp = readSettings(helper, "{\"showPickUpItemsToggle\": true}");
		helper.assertTrue(!StandToggle.BREAK_BLOCKS.isAlwaysShown(pickUp) && StandToggle.PICK_UP_ITEMS.isAlwaysShown(pickUp)
				&& !StandToggle.BEYOND_EFFECTIVE_RANGE.isAlwaysShown(pickUp), "Only the pick-up toggle's setting was saved on");
		ClientModSettings.Settings others = readSettings(helper, "{\"showBreakBlocksToggle\": true, \"showBeyondRangeToggle\": true}");
		helper.assertTrue(StandToggle.BREAK_BLOCKS.isAlwaysShown(others) && !StandToggle.PICK_UP_ITEMS.isAlwaysShown(others)
				&& StandToggle.BEYOND_EFFECTIVE_RANGE.isAlwaysShown(others), "The block and range toggles' settings were saved on");
		for (String key : List.of("showBreakBlocksToggle", "showPickUpItemsToggle", "showBeyondRangeToggle")) {
			ConfigEntry entry = clientEntry(helper, key);
			helper.assertTrue(entry.kind() == ConfigEntry.Kind.BOOLEAN && !entry.defaultValue().getAsBoolean(),
					key + " must be a switch, off by default");
			helper.assertTrue(inSection(ConfigEntry.Side.CLIENT, "hud", key), key + " must sit in the HUD section");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void standToggleHudKeepsTheChosenIconsWhileTheStandControlsShow(GameTestHelper helper) {
		Set<String> gate = refs(helper, TOGGLES_HUD, "isAlwaysShown");
		helper.assertTrue(gate.contains("rotp/core/config/client/StandToggle.isAlwaysShown") && gate.contains(TOGGLES_HUD + ".standControlsShown"),
				"An icon must stay only by its setting and while the Stand's controls show");
		helper.assertTrue(refs(helper, TOGGLES_HUD, "standControlsShown").contains("rotp/core/client/input/controlscheme/ClientControlScheme.hasAbility"),
				"The Stand's controls are those with a Stand ability");
		helper.assertTrue(refs(helper, TOGGLES_HUD, "shouldRender").contains(TOGGLES_HUD + ".isAlwaysShown"),
				"The toggles element no longer shows for an icon kept on the HUD");
		helper.assertTrue(refs(helper, TOGGLES_HUD, "renderElement").contains(TOGGLES_HUD + ".isAlwaysShown"),
				"The toggles element no longer draws an icon kept on the HUD");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void standToggleHudTooltipNamesTheHoveredToggleAndItsState(GameTestHelper helper) {
		Set<String> tooltip = refs(helper, TOGGLES_HUD, "checkTooltip");
		helper.assertTrue(tooltip.contains("rotp/core/config/client/StandToggle.langKey") && tooltip.contains("\"options.on\"")
				&& tooltip.contains("\"options.off\"") && tooltip.contains(TOGGLES_HUD + ".drawn"),
				"A hovered icon must name its toggle, on or off, from the icons drawn");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tutorialTipsShowOneAtATimeOnceASessionAfterTheFirstSeconds(GameTestHelper helper) {
		Predicate<String> always = tip -> true;
		Set<String> seen = new HashSet<>();
		TutorialTips.Schedule schedule = new TutorialTips.Schedule();
		helper.assertTrue(schedule.tick(true, TutorialTips.FIRST_TICKS - 1, seen, always) == null, "No tip in a player's first ten seconds");
		helper.assertTrue(TutorialTips.SUMMON.equals(schedule.tick(true, TutorialTips.FIRST_TICKS, seen, always)), "The summon tip comes first");
		int shownTicks = 0;
		while (schedule.showing() != null && shownTicks < 1000) {
			helper.assertTrue(schedule.tick(true, 1000, seen, always) == null, "A second tip must wait while one shows");
			shownTicks++;
		}
		helper.assertTrue(shownTicks == TutorialTips.SHOW_TICKS + 1, "A tip shows for 160 ticks (shown " + shownTicks + ")");
		for (int i = 0; i < TutorialTips.PAUSE_TICKS; i++) {
			helper.assertTrue(schedule.tick(true, 1000, seen, always) == null, "The next tip must wait out the pause");
		}
		helper.assertTrue(TutorialTips.SKILLS.equals(schedule.tick(true, 1000, seen, always)), "After the pause the skills tip shows");
		seen.add(TutorialTips.SKILLS);
		schedule.tick(true, 1000, seen, always);
		helper.assertTrue(schedule.showing() == null, "A tip the player learned leaves at once");
		for (int i = 0; i < TutorialTips.PAUSE_TICKS + 400; i++) {
			helper.assertTrue(schedule.tick(true, 1000, new HashSet<>(), always) == null, "A tip shows at most once a session");
		}

		TutorialTips.Schedule nextSession = new TutorialTips.Schedule();
		helper.assertTrue(TutorialTips.SKILLS.equals(nextSession.tick(true, 1000, Set.of(TutorialTips.SUMMON), always)),
				"A learned tip never shows again");
		TutorialTips.Schedule off = new TutorialTips.Schedule();
		helper.assertTrue(off.tick(false, 1000, Set.of(), always) == null, "The tutorial turned off shows nothing");
		helper.assertTrue(off.tick(true, 1000, Set.of(), always) != null && off.tick(false, 1000, Set.of(), always) == null
				&& off.showing() == null, "Turning the tutorial off ends the shown tip");
		helper.assertTrue(new TutorialTips.Schedule().tick(true, 1000, Set.of(), tip -> false) == null, "A tip that would not help stays away");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void tutorialTipsFollowWhatThePlayerCanDoAndHasDone(GameTestHelper helper) {
		helper.assertTrue(TutorialTips.learned(TutorialTips.SUMMON, true, false) && !TutorialTips.learned(TutorialTips.SUMMON, false, true),
				"Summoning the Stand learns the summon tip");
		helper.assertTrue(TutorialTips.learned(TutorialTips.SKILLS, false, true) && !TutorialTips.learned(TutorialTips.SKILLS, true, false),
				"Opening the Stand skills screen learns the skills tip");
		helper.assertTrue(TutorialTips.wanted(TutorialTips.SUMMON, true, false, false) && !TutorialTips.wanted(TutorialTips.SUMMON, true, true, false)
				&& !TutorialTips.wanted(TutorialTips.SUMMON, false, false, false), "The summon tip helps only an unsummoned Stand that can be summoned");
		helper.assertTrue(TutorialTips.wanted(TutorialTips.SKILLS, false, false, true) && !TutorialTips.wanted(TutorialTips.SKILLS, true, true, false),
				"The skills tip helps only when a skill can be learned");
		helper.assertTrue(!TutorialTips.canSummon(null) && !TutorialTips.canLearnStandSkill(null), "No Stand, no tip");

		Player boyUser = standUser(helper, ModStands.BOY_II_MAN.get());
		Player player = standUser(helper, JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum")));
		try {
			helper.assertFalse(TutorialTips.canSummon(PowerClass.STAND.attachGet(boyUser)), "A Stand that is never summoned gets no summon tip");
			StandPower power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(TutorialTips.canSummon(power), "Star Platinum is summoned, so the summon tip helps");

			StandTypePersistentData data = power.getCurTypeData();
			power.setResolveLevel(power.getMaxResolveLevel());
			data.setExp(1_000_000, power);
			// 1.16 rules: the max Resolve level learns every skill by itself, so one is taken back to leave something to learn
			helper.assertFalse(TutorialTips.canLearnStandSkill(power), "At the max Resolve level every skill is already learned");
			String relocked = data.getAllSkills().keySet().iterator().next();
			helper.assertTrue(data._setSkillUnlocked(relocked, false, false), "Could not take back the skill " + relocked);
			helper.assertTrue(TutorialTips.canLearnStandSkill(power), "With the exp and Resolve for them, Star Platinum's skills can be learned");
			for (int round = 0; round < 50 && TutorialTips.canLearnStandSkill(power); round++) {
				List.copyOf(data.getAllSkills().keySet()).forEach(name -> data.unlockSkill(power, name));
			}
			helper.assertFalse(TutorialTips.canLearnStandSkill(power), "With every skill learned, the skills tip has nothing to teach");
		}
		finally {
			boyUser.discard();
			player.discard();
		}
		helper.succeed();
	}

	private static Player standUser(GameTestHelper helper, StandType type) {
		helper.assertTrue(type != null, "Missing registered Stand");
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Stand user");
		helper.assertTrue(StandPowerTransitions.insert(PowerClass.STAND.attachGet(player), new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not give the user " + type);
		return player;
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tutorialTurnedBackOnShowsItsTipsAgain(GameTestHelper helper) {
		helper.assertTrue(new ClientModSettings.Settings().tutorialEnabled, "The tutorial is on by default, as in the official version");
		helper.assertTrue(readSettings(helper, "{\"tutorialEnabled\": false}").tutorialEnabled == false, "A saved off must load back");
		Path dir = FMLPaths.GAMEDIR.get().resolve("offmenus-tests");
		Path file = dir.resolve(TutorialProgress.FILE_NAME + "-" + System.nanoTime());
		try {
			TutorialProgress progress = new TutorialProgress(file.toFile());
			progress.add(TutorialTips.SUMMON);
			progress.add(TutorialTips.SKILLS);
			helper.assertTrue(new TutorialProgress(file.toFile()).seen().equals(Set.of(TutorialTips.SUMMON, TutorialTips.SKILLS)),
					"The learned tips must load back from their file");
			TutorialProgress.setCurrent(progress);
			ClientModSettings.Settings off = readSettings(helper, "{\"tutorialEnabled\": false}");
			off.setTutorialEnabled(false);
			helper.assertTrue(progress.seen().size() == 2, "Turning it off keeps what was learned");
			off.setTutorialEnabled(true);
			helper.assertTrue(off.tutorialEnabled && progress.seen().isEmpty() && new TutorialProgress(file.toFile()).seen().isEmpty(),
					"Turned back on, the tutorial shows all its tips again, saved so");
			progress.add(TutorialTips.SUMMON);
			ClientModSettings.Settings stillOn = new ClientModSettings.Settings();
			stillOn.setTutorialEnabled(true);
			helper.assertTrue(progress.seen().equals(Set.of(TutorialTips.SUMMON)), "Setting it on while on must not forget anything");
			Files.writeString(file, "{broken", StandardCharsets.UTF_8);
			helper.assertTrue(new TutorialProgress(file.toFile()).seen().isEmpty(), "A broken file loads as nothing learned");
		}
		catch (IOException e) {
			helper.fail("Cannot write " + file + ": " + e);
		}
		finally {
			TutorialProgress.setCurrent(null);
			try {
				Files.deleteIfExists(file);
			}
			catch (IOException ignored) {
				// a leftover file in the test run folder harms nothing
			}
		}

		ConfigEntry entry = clientEntry(helper, "tutorialEnabled");
		helper.assertTrue(entry.kind() == ConfigEntry.Kind.BOOLEAN && entry.defaultValue().getAsBoolean(), "The tutorial row is a switch, on by default");
		helper.assertTrue(inSection(ConfigEntry.Side.CLIENT, "controls", "tutorialEnabled"), "The tutorial row must sit in the controls section");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tutorialToastsAreDrivenByTheClientTick(GameTestHelper helper) {
		helper.assertTrue(annotations(helper, TOASTS, null).contains("Lnet/neoforged/fml/common/EventBusSubscriber;")
				&& annotations(helper, TOASTS, "onClientTick").contains("Lnet/neoforged/bus/api/SubscribeEvent;"),
				"The tutorial toasts are no longer subscribed to the client tick");
		Set<String> tick = refs(helper, TOASTS, "onClientTick");
		for (String ref : List.of(TIPS + "$Schedule.tick", TIPS + ".learned", TIPS + ".wanted", TIPS + ".canLearnStandSkill",
				"type:rotp/core/powersystem/standpower/client_screens/StandSkillsScreen", "rotp/core/config/client/TutorialProgress.add",
				"net/minecraft/client/gui/components/toasts/ToastComponent.addToast")) {
			helper.assertTrue(tick.contains(ref), "The tutorial tick no longer uses " + ref);
		}
		Set<String> render = refs(helper, TOAST, "render");
		helper.assertTrue(render.contains(TIPS + "$Schedule.progress") && render.contains("java/util/function/BooleanSupplier.getAsBoolean"),
				"The toast no longer shows its time and leaves when its tip ends");
		for (String code : List.of("en_us", "zh_cn", "zh_tw")) {
			JsonObject lang = readLang(helper, code);
			for (String key : List.of("jojo_ripples.tutorial.summon_stand", "jojo_ripples.tutorial.stand_skills.1", "jojo_ripples.tutorial.stand_skills.2")) {
				helper.assertTrue(lang.has(key), code + " lacks " + key);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void explorerMapRulesKeepWhatTheClientSendsInBounds(GameTestHelper helper) {
		helper.assertTrue(ExplorerMapRules.clampToBorder(Integer.MAX_VALUE, -100.5, 100.5) == 99
				&& ExplorerMapRules.clampToBorder(Integer.MIN_VALUE, -100.5, 100.5) == -100
				&& ExplorerMapRules.clampToBorder(42, -100.5, 100.5) == 42, "A destination must stay inside the world border");
		helper.assertTrue(ExplorerMapRules.cleanName("x".repeat(80)).length() == ExplorerMapRules.NAME_MAX
				&& ExplorerMapRules.cleanName("a§b\nc").equals("abc") && ExplorerMapRules.cleanName(null).isEmpty(),
				"A map's name is cut to 50 characters and cleaned as an anvil cleans it");
		helper.assertTrue(ExplorerMapRules.canMark(MapDecorationTypes.TARGET_X) && ExplorerMapRules.canMark(ModMapDecorationTypes.HAMON_TEMPLE)
				&& ExplorerMapRules.canMark(ModMapDecorationTypes.PILLARMAN_TEMPLE), "Fixed marks can be chosen");
		helper.assertTrue(!ExplorerMapRules.canMark(MapDecorationTypes.PLAYER) && !ExplorerMapRules.canMark(MapDecorationTypes.FRAME)
				&& !ExplorerMapRules.canMark(null), "A player or frame marker cannot be put on a map");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void explorerMapModeOpensOnlyAtTheTableTheServerSaw(GameTestHelper helper) {
		BlockPos table = helper.absolutePos(new BlockPos(1, 2, 1));
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.CARTOGRAPHY_TABLE);
		MenuPlayer player = new MenuPlayer(helper, table.south());
		player.containerMenu = new CartographyTableMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
		helper.assertFalse(ClExplorerMapModePacket.switchMode(player, true), "No table seen yet: the mode must not switch");
		ExplorerMapTables.remember(player, table.east(3));
		helper.assertFalse(ClExplorerMapModePacket.switchMode(player, true), "No cartography table there: the mode must not switch");
		ExplorerMapTables.remember(player, table);
		player.moveTo(table.getX() + 40, table.getY(), table.getZ());
		helper.assertFalse(ClExplorerMapModePacket.switchMode(player, true), "Out of reach of the table: the mode must not switch");
		player.moveTo(table.getX() + 0.5, table.getY(), table.getZ() + 1.5);
		player.containerMenu = player.inventoryMenu;
		helper.assertFalse(ClExplorerMapModePacket.switchMode(player, true), "Without the table's screen open the mode must not switch");
		player.containerMenu = new CartographyTableMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
		helper.assertTrue(ClExplorerMapModePacket.switchMode(player, true) && player.containerMenu instanceof ExplorerMapMenu,
				"At the table the server saw, the explorer mode must open");
		helper.assertTrue(ClExplorerMapModePacket.switchMode(player, false) && player.containerMenu instanceof CartographyTableMenu,
				"And back to vanilla's mode");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void explorerMapMenuMakesAMapOfTheChosenPlace(GameTestHelper helper) {
		BlockPos table = helper.absolutePos(new BlockPos(1, 2, 1));
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.CARTOGRAPHY_TABLE);
		MenuPlayer player = new MenuPlayer(helper, table.south());
		ExplorerMapMenu menu = new ExplorerMapMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
		player.containerMenu = menu;
		menu.inputMapSlot.set(new ItemStack(Items.MAP));
		ClExplorerMapDataPacket.posX(Integer.MAX_VALUE).applyTo(menu);
		ClExplorerMapDataPacket.posZ(-1234).applyTo(menu);
		ClExplorerMapDataPacket.icon(MapDecorationTypes.PLAYER).applyTo(menu);
		helper.assertTrue(menu.icon.is(MapDecorationTypes.TARGET_X), "A player marker must be refused");
		ClExplorerMapDataPacket.icon(ModMapDecorationTypes.HAMON_TEMPLE).applyTo(menu);
		ClExplorerMapDataPacket.name("y".repeat(70)).applyTo(menu);
		int borderEdge = (int) Math.floor(helper.getLevel().getWorldBorder().getMaxX()) - 1;
		helper.assertTrue(menu.posX == borderEdge && menu.posZ == -1234, "The destination must stay inside the border: " + menu.posX);
		helper.assertTrue(menu.icon.is(ModMapDecorationTypes.HAMON_TEMPLE) && menu.itemName.length() == ExplorerMapRules.NAME_MAX,
				"The icon and the cut name must be taken");

		ItemStack result = menu.resultMapSlot.getItem();
		MapDecorations decorations = result.get(DataComponents.MAP_DECORATIONS);
		helper.assertTrue(result.is(Items.FILLED_MAP) && decorations != null && decorations.decorations().values().stream()
				.anyMatch(entry -> entry.type().is(ModMapDecorationTypes.HAMON_TEMPLE) && entry.x() == borderEdge && entry.z() == -1234),
				"The result must be a filled map marked at the destination: " + result);
		menu.resultMapSlot.onTake(player, result);
		MapId id = result.get(DataComponents.MAP_ID);
		MapItemSavedData data = id != null ? helper.getLevel().getMapData(id) : null;
		helper.assertTrue(data != null && data.scale == ExplorerMapMenu.MAP_SCALE && data.dimension == helper.getLevel().dimension(),
				"Taking the result must make a new map at the explorer map's scale");
		helper.assertTrue(menu.inputMapSlot.getItem().isEmpty(), "Taking the result must use the empty map up");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void explorerMapScreenIsRegisteredAndReachedFromTheTable(GameTestHelper helper) {
		helper.assertTrue(refs(helper, "rotp/core/init/ModContainerScreensClient", "registerScreens")
				.contains(EXPLORER + "client/ExplorerMapScreen.<init>"), "The explorer map menu has no screen");
		helper.assertTrue(annotations(helper, EXPLORER + "client/ExplorerMapModeButtons", "onCartographyScreenInit")
				.contains("Lnet/neoforged/bus/api/SubscribeEvent;"), "The cartography table no longer gets its mode buttons");
		Set<String> buttons = refs(helper, EXPLORER + "client/ExplorerMapModeButtons", "create");
		helper.assertTrue(buttons.contains("net/neoforged/neoforge/network/PacketDistributor.sendToServer")
				&& buttons.contains("type:" + EXPLORER + "ClExplorerMapModePacket"), "The mode buttons no longer ask the server");
		Set<String> packets = refs(helper, "rotp/core/PacketsRegister", "register");
		helper.assertTrue(packets.contains("type:" + EXPLORER + "ClExplorerMapModePacket$Handler")
				&& packets.contains("type:" + EXPLORER + "ClExplorerMapDataPacket$Handler"), "The explorer map packets are not registered");
		String textures = "/assets/" + JojoMod.MOD_ID + "/textures/";
		expectPng(helper, textures + "gui/container/cartography_table2.png", 256, 256);
		expectPng(helper, textures + "gui/container/cartography_table2_name_field.png", 110, 16);
		expectPng(helper, textures + "gui/cartography_vanilla.png", 16, 16);
		expectPng(helper, textures + "gui/cartography_explorer.png", 16, 16);
		expectPng(helper, textures + "map/decorations/hamon_temple.png", 8, 8);
		for (String code : List.of("en_us", "zh_cn", "zh_tw")) {
			helper.assertTrue(readLang(helper, code).has("jojo_ripples.container.explorer_cartography_table"), code + " lacks the explorer title");
		}
		helper.succeed();
	}

	// a server player that takes any menu it is given, where a fake player would refuse it
	private static final class MenuPlayer extends FakePlayer {
		MenuPlayer(GameTestHelper helper, BlockPos at) {
			super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "offmenus-map-maker"));
			moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		}

		@Override
		public OptionalInt openMenu(@Nullable MenuProvider provider) {
			return openMenu(provider, (Consumer<RegistryFriendlyByteBuf>) null);
		}

		@Override
		public OptionalInt openMenu(@Nullable MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extraData) {
			if (provider == null) {
				return OptionalInt.empty();
			}
			containerMenu = provider.createMenu(1, getInventory(), this);
			return OptionalInt.of(1);
		}
	}

	private static JsonObject readLang(GameTestHelper helper, String code) {
		String path = "/assets/" + JojoMod.MOD_ID + "/lang/" + code + ".json";
		try (InputStream in = OfficialMenusGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
			return null;
		}
	}

	private static void expectPips(GameTestHelper helper, ResolvePips.Shown shown, int filled, int outlined, int mode, String what) {
		helper.assertTrue(shown != null && shown.filled() == filled && shown.outlined() == outlined && shown.modeLevel() == mode,
				what + " (got " + shown + ")");
	}

	static ClientModSettings.Settings readSettings(GameTestHelper helper, String json) {
		Path dir = FMLPaths.GAMEDIR.get().resolve("offmenus-tests");
		Path file = dir.resolve("client_settings-" + System.nanoTime() + ".json");
		try {
			Files.createDirectories(dir);
			Files.writeString(file, json, StandardCharsets.UTF_8);
			return ClientModSettings.readDetached(file.toFile());
		}
		catch (IOException e) {
			helper.fail("Cannot write " + file + ": " + e);
			return null;
		}
		finally {
			try {
				Files.deleteIfExists(file);
			}
			catch (IOException ignored) {
				// a leftover file in the test run folder harms nothing
			}
		}
	}

	static ConfigEntry clientEntry(GameTestHelper helper, String key) {
		ConfigEntry found = ClientConfigEditor.catalog().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
		helper.assertTrue(found != null, "No client settings row " + key);
		return found;
	}

	static boolean inSection(ConfigEntry.Side side, String section, String key) {
		List<ConfigEntry> shown = ClientConfigEditor.catalog().stream().filter(ConfigWidgetRules::isShown).toList();
		return ConfigWidgetRules.sections(side, shown).stream().anyMatch(s -> s.id().equals(section)
				&& s.entries().stream().anyMatch(entry -> entry.key().equals(key)));
	}

	static void expectPng(GameTestHelper helper, String path, int width, int height) {
		try (InputStream in = OfficialMenusGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing " + path);
			BufferedImage image = ImageIO.read(in);
			helper.assertTrue(image != null && image.getWidth() == width && image.getHeight() == height,
					path + " must be a " + width + "x" + height + " PNG");
		}
		catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
		}
	}

	// owner.name of every call, field and method reference, and "text" of every string, in each method of that name
	static Set<String> refs(GameTestHelper helper, String className, String methodName) {
		ClassNode node = new ClassNode();
		try (InputStream in = OfficialMenusGameTests.class.getResourceAsStream("/" + className + ".class")) {
			helper.assertTrue(in != null, "Missing class file " + className);
			new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}
		catch (IOException e) {
			helper.fail("Cannot read " + className + ": " + e);
		}
		Set<String> found = new HashSet<>();
		boolean seen = false;
		for (MethodNode method : node.methods) {
			// a lambda in the method is compiled into a method of its own: lambda$<method>$<n>
			if (!method.name.equals(methodName) && !method.name.startsWith("lambda$" + methodName + "$")) {
				continue;
			}
			seen = true;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call) {
					found.add(call.owner + "." + call.name);
				}
				else if (insn instanceof FieldInsnNode field) {
					found.add(field.owner + "." + field.name);
				}
				else if (insn instanceof TypeInsnNode type) {
					found.add("type:" + type.desc);
				}
				else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String text) {
					found.add("\"" + text + "\"");
				}
				else if (insn instanceof InvokeDynamicInsnNode indy) {
					for (Object arg : indy.bsmArgs) {
						if (arg instanceof Handle handle) {
							found.add(handle.getOwner() + "." + handle.getName());
						}
					}
				}
			}
		}
		helper.assertTrue(seen, "Missing method " + className + "." + methodName);
		return found;
	}

	// the descriptors of the annotations on the class (methodName null) or on its methods of that name
	static Set<String> annotations(GameTestHelper helper, String className, @Nullable String methodName) {
		ClassNode node = new ClassNode();
		try (InputStream in = OfficialMenusGameTests.class.getResourceAsStream("/" + className + ".class")) {
			helper.assertTrue(in != null, "Missing class file " + className);
			new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
		}
		catch (IOException e) {
			helper.fail("Cannot read " + className + ": " + e);
		}
		Set<String> found = new HashSet<>();
		List<List<AnnotationNode>> lists = new ArrayList<>();
		if (methodName == null) {
			lists.add(node.visibleAnnotations);
			lists.add(node.invisibleAnnotations);
		}
		else {
			node.methods.stream().filter(method -> method.name.equals(methodName)).forEach(method -> {
				lists.add(method.visibleAnnotations);
				lists.add(method.invisibleAnnotations);
			});
		}
		lists.stream().filter(list -> list != null).flatMap(List::stream).forEach(annotation -> found.add(annotation.desc));
		return found;
	}
}
