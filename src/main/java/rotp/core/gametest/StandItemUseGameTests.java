package rotp.core.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.client.input.AbilityInputState;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.ability.input.InputKeyId;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.subsystems.entity_useitem.ClStandClickPacket;
import rotp.core.subsystems.entity_useitem.HitResultSync;
import rotp.core.subsystems.entity_useitem.StandItemUse;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * G9: a Stand uses what it holds on its user's right click, through the packet's server handler as the client sends it. The client's
 * own part (who gets the press, the HUD) cannot be loaded here: its source is read for the wiring, and the in-game pass plays it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandItemUseGameTests {
	private static final List<String> CORE_HUMANOIDS = List.of("star_platinum", "the_world", "crazy_diamond", "gold_experience",
			"magicians_red", "silver_chariot", "hierophant_green");
	private static final List<String> LANGUAGES = List.of("en_us", "en_pt", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br", "ru_ru",
			"uk_ua", "zh_cn", "zh_tw");

	private StandItemUseGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aStandDrawsAndShootsABowOnItsUsersRightClick(GameTestHelper helper) {
		Scene scene = new Scene(helper, "StandBow");
		try {
			scene.stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			scene.stand.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 5));
			scene.click(1L);
			EntityActionInstance drawing = scene.stand.getCurStandAction();
			helper.assertTrue(scene.stand.isUsingItem() && drawing != null && drawing.ability == ModSpecialActions.RMB_USING_ITEM.get(),
					"A right click did not make the Stand draw its bow: using " + scene.stand.isUsingItem() + ", action " + drawing);
			helper.assertTrue(scene.holdsTheRightButton(), "The draw is not held on the right button");
		}
		catch (RuntimeException | AssertionError e) {
			scene.close();
			throw e;
		}
		helper.runAfterDelay(25, () -> {
			try {
				helper.assertTrue(scene.stand.isUsingItem(), "The Stand let go of its bow before the release");
				helper.assertTrue(AbilityInput.keyReleaseFromNetwork(InputKeyId.STAND_ITEM_RMB, scene.user, 1L)
						== AbilityInput.ReleaseResult.RELEASED, "The release of the button did not reach the draw");
				List<Arrow> arrows = scene.level().getEntitiesOfClass(Arrow.class, new AABB(scene.user.blockPosition()).inflate(16),
						arrow -> arrow.getOwner() == scene.stand);
				helper.assertTrue(arrows.size() == 1, "The released bow shot " + arrows.size() + " arrows owned by the Stand");
				helper.assertTrue(arrows.get(0).getDeltaMovement().length() > 1, "The arrow left at speed "
						+ arrows.get(0).getDeltaMovement().length() + ": the draw did not charge the bow");
				helper.assertTrue(scene.stand.getOffhandItem().getCount() == 4, "The arrow did not come from the Stand's other hand: "
						+ scene.stand.getOffhandItem());
				helper.assertTrue(!scene.stand.isUsingItem(), "The Stand still draws after the shot");
				arrows.forEach(Arrow::discard);
			}
			finally {
				scene.close();
			}
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aStandWithNoArrowInItsHandsDoesNotDraw(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "StandBowEmpty")) {
			scene.stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			scene.user.getInventory().add(new ItemStack(Items.ARROW, 5));
			scene.click(1L);
			helper.assertTrue(!scene.stand.isUsingItem() && scene.stand.getCurStandAction() == null,
					"The Stand drew its bow with no arrow in its hands, or from its user's inventory");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aStandThrowsWhatItHolds(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "StandThrow")) {
			scene.stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SNOWBALL, 3));
			scene.click(1L);
			List<Snowball> thrown = scene.level().getEntitiesOfClass(Snowball.class, new AABB(scene.user.blockPosition()).inflate(16),
					snowball -> snowball.getOwner() == scene.stand);
			helper.assertTrue(thrown.size() == 1 && scene.stand.getMainHandItem().getCount() == 2,
					"The Stand threw " + thrown.size() + " snowballs, " + scene.stand.getMainHandItem() + " left");
			EntityActionInstance action = scene.stand.getCurStandAction();
			helper.assertTrue(action != null && action.ability == ModSpecialActions.RMB_CLICK_ITEM.get(), "The throw set no item action: " + action);
			thrown.forEach(Snowball::discard);
		}
		helper.succeed();
	}

	// the official's rule, which its text states: another move of the Stand keeps the button
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void anotherMoveOfTheStandKeepsTheClick(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "StandBusy")) {
			scene.stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
			Ability barrage = scene.power.getAbility("barrage");
			short key = 1;
			var held = AbilityInput.keyPress(key, barrage, scene.user, null, InputMethod.HOLD, 0, BufferingState.clickOnly(), barrage.abilityId);
			EntityActionInstance running = scene.stand.getCurStandAction();
			helper.assertTrue(held != null && running != null && running.ability == barrage, "Fixture: the barrage did not start");
			helper.assertTrue(!StandItemUse.takesRightClick(scene.power, scene.stand), "The rule let the click through a running barrage");
			scene.click(1L);
			helper.assertTrue(!scene.stand.isUsingItem() && scene.stand.getCurStandAction() == running,
					"A right click raised the shield through the Stand's barrage: action " + scene.stand.getCurStandAction());
			AbilityInput.keyReleaseFromNetwork(key, scene.user, held.generation);
		}
		helper.succeed();
	}

	// the throw is on the bare button by default (MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aGrabbingStandKeepsTheButtonForItsThrow(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "StandGrabbing")) {
			scene.stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Fixture: no cow");
			cow.moveTo(scene.user.position());
			helper.getLevel().addFreshEntity(cow);
			var grab = scene.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			try {
				grab.setGrabTarget(cow);
				helper.assertTrue(cow.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).isGrabbed(), "Fixture: the Stand did not grab the cow");
				scene.click(1L);
				helper.assertTrue(!scene.stand.isUsingItem(), "The right click went to the Stand's item while it holds an entity to throw");
				grab.setGrabTarget(null);
				scene.click(2L);
				helper.assertTrue(scene.stand.isUsingItem(), "The Stand does not take the click again once it has let go");
			}
			finally {
				grab.setGrabTarget(null);
				cow.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aLockedSkillLeavesTheClickToTheUser(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "StandLocked")) {
			scene.stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
			StandTypePersistentData data = scene.power.getCurTypeData();
			helper.assertTrue(data.isSkillUnlocked(StandItemUse.SKILL), "A new Star Platinum does not start with the skill");
			data._setSkillUnlocked(StandItemUse.SKILL, false, false);
			scene.click(1L);
			helper.assertTrue(!scene.stand.isUsingItem(), "The Stand used its item with the skill locked");
			data._setSkillUnlocked(StandItemUse.SKILL, true, false);
			scene.click(2L);
			helper.assertTrue(scene.stand.isUsingItem(), "The Stand does not use its item with the skill unlocked");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aMovesetThatDoesNotListTheSkillKeepsTheClick(GameTestHelper helper) {
		helper.assertTrue(StandItemUse.skillAllows(false, false), "A Stand whose moveset does not list the skill lost the click");
		helper.assertTrue(StandItemUse.skillAllows(true, true), "An unlocked skill did not allow the click");
		helper.assertTrue(!StandItemUse.skillAllows(true, false), "A locked skill allowed the click");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void everyCoreHumanoidStartsWithTheSkillAndOldDataGetsIt(GameTestHelper helper) {
		for (String id : CORE_HUMANOIDS) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
			helper.assertTrue(type != null, "Missing Stand type " + id);
			var skill = type.getUnlockableSkills().get(StandItemUse.SKILL);
			helper.assertTrue(skill != null && skill.isStarting, id + " does not start with the Use Item skill");
		}
		try (Scene scene = new Scene(helper, "StandOldData")) {
			StandTypePersistentData data = scene.power.getCurTypeData();
			CompoundTag saved = data.serializeNBT(helper.getLevel().registryAccess());
			ListTag old = new ListTag();
			for (Tag skill : saved.getList("skills", Tag.TAG_STRING)) {
				if (!StandItemUse.SKILL.equals(skill.getAsString())) {
					old.add(StringTag.valueOf(skill.getAsString()));
				}
			}
			saved.put("skills", old);
			data._setSkillUnlocked(StandItemUse.SKILL, false, false);
			data.deserializeNBT(helper.getLevel().registryAccess(), saved);
			helper.assertTrue(data.isSkillUnlocked(StandItemUse.SKILL), "A save from before the skill does not get it");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void movesOnTheButtonStepAsideUnlessTheyWantTheItem(GameTestHelper helper) {
		int plain = 1 << AbilityInputState.IS_ACTIVE | 1 << AbilityInputState.VISIBLE_WHEN_INACTIVE | 1 << AbilityInputState.HIGH_PRIORITY;
		helper.assertTrue(StandItemUse.inputStateBesideTheItem(plain) == 1 << AbilityInputState.HIGH_PRIORITY,
				"A plain move stays on the button or the HUD while the Stand holds an item, or lost another flag: " + plain + " -> "
						+ StandItemUse.inputStateBesideTheItem(plain));
		int wantsTheItem = 1 << AbilityInputState.IS_ACTIVE | 1 << AbilityInputState.WITH_ITEM_HELD;
		helper.assertTrue(StandItemUse.inputStateBesideTheItem(wantsTheItem) == wantsTheItem,
				"A move that wants the item stepped aside: " + StandItemUse.inputStateBesideTheItem(wantsTheItem));
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theSkillHasItsWordsItsIconAndItsClip(GameTestHelper helper) {
		for (String language : LANGUAGES) {
			JsonObject words = StandClipPoses116GameTests.readJson(helper, "lang/" + language + ".json");
			for (String key : List.of("jojo_ripples.skill.item_use", "jojo_ripples.skill.item_use.desc",
					"jojo_ripples.skill.item_use.controls", "jojo_ripples.ability.item_use")) {
				helper.assertTrue(words.has(key) && !words.get(key).getAsString().isBlank(), language + " lacks " + key);
			}
		}
		try (InputStream in = StandItemUseGameTests.class.getResourceAsStream("/assets/jojo_ripples/textures/ability/item_use.png")) {
			helper.assertTrue(in != null, "The skill has no icon");
			BufferedImage icon = ImageIO.read(in);
			helper.assertTrue(icon.getWidth() == 16 && icon.getHeight() == 16, "The icon is " + icon.getWidth() + "x" + icon.getHeight());
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		JsonObject bow = StandClipPoses116GameTests.readJson(helper, "animations/stand_default.animation.json")
				.getAsJsonObject("animations").getAsJsonObject("bow_shoot");
		helper.assertTrue(bow != null && bow.has("timeline") && bow.getAsJsonObject("bones").has("right_arm"),
				"A humanoid Stand without its own bow_shoot has nothing to play");
		helper.succeed();
	}

	/*
	 * Client wiring, by its source: a raw press whose moves stepped aside leaves a timer that lets vanilla through, and the first
	 * port's handler returned on any timer, so a normal press never reached the Stand. Shift keeps the button.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theClientSendsAPressWhoseMovesSteppedAside(GameTestHelper helper) {
		String click = source("client/input/StandVanillaClickInput.java");
		int timer = click.indexOf("HeldKeyTimer existingHeldKey = inputHandler.getHeldKeyTimer(key);");
		int released = click.indexOf("boolean rawPressAlreadyReleased = existingHeldKey == null;", timer);
		int cancelling = click.indexOf("if (existingHeldKey.cancelVanilla) {", released);
		int generation = click.indexOf("long inputGeneration = AbilityInput.nextInputGeneration(", cancelling);
		helper.assertTrue(timer >= 0 && released > timer && cancelling > released && generation > cancelling
				&& click.substring(cancelling, generation).indexOf("return false;") < 0,
				"A held key timer that lets vanilla through stops the Stand's click again");
		int rule = click.indexOf("private static boolean clickGoesToStand(StandEntity stand) {");
		helper.assertTrue(rule >= 0 && click.indexOf("!Screen.hasShiftDown()", rule) > rule
				&& click.indexOf("StandItemUse.takesRightClick(", rule) > rule, "The client's rule lost Shift or the shared rule");
		helper.assertTrue(click.contains("|| ClStandClickPacket.isUnsummoning(stand)\n\t\t\t\t|| !clickGoesToStand(stand)) {"),
				"The press does not ask the client's rule");
		helper.assertTrue(click.contains("StandItemUse.inputStateBesideTheItem(move.clientInputState)"), "The moves on the button do not step aside");
		helper.assertTrue(source("client/ClientPowerCache.java").contains("StandVanillaClickInput.movesGiveWayToTheItem(power, availableAbilitiesCache[i]);"),
				"The moves' update does not reach the rule");
		helper.assertTrue(source("client/ui/hud_power/PowerHudControlsElement.java").contains("StandVanillaClickInput.itemOnRightClick(controlScheme, key)"),
				"The HUD does not show the item on the button");
		helper.succeed();
	}

	private static String source(String file) {
		for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
			Path path = at.resolve("src/main/java/rotp/core").resolve(file);
			if (Files.isRegularFile(path)) {
				try {
					return Files.readString(path).replace("\r\n", "\n");
				}
				catch (IOException e) {
					throw new IllegalStateException("Could not read " + path, e);
				}
			}
		}
		throw new IllegalStateException("No source of " + file + " above " + Path.of("").toAbsolutePath());
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final FakePlayer user;
		final StandPower power;
		final StandEntity stand;
		private boolean closed;

		Scene(GameTestHelper helper, String name) {
			this.helper = helper;
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(at.x, at.y, at.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand's user");
			try {
				StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
				helper.assertTrue(type != null, "Missing Star Platinum");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not give the Stand");
				helper.assertTrue(type.summon(user, power), "Could not summon the Stand");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "The summoned Stand is missing");
				stand.summonLockTicks = 0;
				stand.gradualSummonWeaknessTicks = 0;
			}
			catch (RuntimeException | AssertionError e) {
				GameTestPlayers.discardAfterFailedSetup(user);
				throw e;
			}
		}

		ServerLevel level() {
			return helper.getLevel();
		}

		boolean holdsTheRightButton() {
			return user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.containsKey(InputKeyId.STAND_ITEM_RMB);
		}

		// through the packet's server handler, as the user's client sends it on a right click with nothing aimed at
		void click(long generation) {
			IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
					new Class<?>[] { IPayloadContext.class }, (proxy, method, args) -> {
						if (method.getName().equals("player")) {
							return user;
						}
						throw new UnsupportedOperationException("Fixture: the handler asked the context for " + method.getName());
					});
			// the Handler constructor sets the packet's static type; put back the registered one
			Field typeField;
			Object registeredType;
			try {
				typeField = ClStandClickPacket.class.getDeclaredField("type");
				typeField.setAccessible(true);
				registeredType = typeField.get(null);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Fixture: cannot read the packet type", e);
			}
			try {
				new ClStandClickPacket.Handler(JojoMod.resLoc("clstandclick")).handle(new ClStandClickPacket(
						new HitResultSync(null), generation, InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND), context);
			}
			finally {
				try {
					typeField.set(null, registeredType);
				}
				catch (ReflectiveOperationException e) {
					throw new IllegalStateException("Fixture: cannot restore the packet type", e);
				}
			}
		}

		@Override
		public void close() {
			if (closed) return;
			closed = true;
			if (stand != null && stand.isUsingItem()) stand.stopUsingItem();
			if (power.isSummoned()) power.getPowerType().forceUnsummon(user, power);
			user.discard();
		}
	}
}
