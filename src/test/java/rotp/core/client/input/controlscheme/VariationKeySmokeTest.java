package rotp.core.client.input.controlscheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import rotp.core.client.input.VanillaKeybinds;
import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.Hotbar;
import rotp.core.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import rotp.core.client.input.controlscheme.ClientControlScheme.InputsByKeyModifier;
import rotp.core.client.input.controlscheme.ClientControlScheme.MoveGroup;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * The owner: "這裏咱們改成可以玩家自己設定的，默認Ctrl". A declared Modifier.SHIFT is the player's variation key, whichever key
 * that is and without a restart; grab, charged heavy, throw, release and item swap are key mappings of their own.
 */
public final class VariationKeySmokeTest {
	private static final int L_CTRL = 341;
	private static final int R_CTRL = 345;
	private static final int L_SHIFT = 340;
	private static final int L_ALT = 342;
	private static final int KEY_V = 86;
	private static final int KEY_R = 82;
	private static final InputConstants.Key RMB = InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT);
	private static final InputConstants.Key MOUSE_5 = InputConstants.Type.MOUSE.getOrCreate(4);
	private static final AbilityControlsEntry BASE = new AbilityControlsEntry(null, "base");
	private static final AbilityControlsEntry VARIATION = new AbilityControlsEntry(null, "variation");
	private static final AbilityControlsEntry DECLARED_CTRL = new AbilityControlsEntry(null, "declared_ctrl");
	private static final Predicate<AbilityControlsEntry> ALL = entry -> true;
	private static final Predicate<AbilityControlsEntry> LOCKED = entry -> false;

	private VariationKeySmokeTest() {}

	public static void run() {
		KeyMapping setting = new VanillaKeybinds.SingleKeyMapping("test.variation_key", KeyConflictContext.IN_GAME,
				InputConstants.Type.KEYSYM, L_CTRL, "test.controls");
		VariationKey.useMapping(setting);
		try {
			verifyWhatAKeyCountsAs();
			verifySingleKey(setting);
			verifyDeclaredModifiers(setting);
			verifyDeclaredPairsOnOneButton(setting);
			verifySlotFollowsTheKey(setting);
			verifySchemesFollowTheKey(setting);
			verifyLockedVariationUnderEveryKey(setting);
			verifyKeysThatDoNotMove(setting);
			verifyThrowSharesItsButton();
		}
		finally {
			VariationKey.useMapping(null);
		}
		verifyDefaultWithoutTheMapping();
		verifySources();
	}

	private static InputConstants.Key key(int code) {
		return InputConstants.Type.KEYSYM.getOrCreate(code);
	}

	private static void verifyWhatAKeyCountsAs() {
		check(VariationKey.modifierOf(key(L_CTRL)) == KeyModifier.CONTROL && VariationKey.modifierOf(key(R_CTRL)) == KeyModifier.CONTROL,
				"Ctrl is the Ctrl modifier");
		check(VariationKey.modifierOf(key(L_SHIFT)) == KeyModifier.SHIFT, "Shift is the Shift modifier");
		check(VariationKey.modifierOf(key(L_ALT)) == KeyModifier.ALT, "Alt is the Alt modifier");
		check(VariationKey.modifierOf(key(KEY_V)) == VariationKey.BORROWED_MODIFIER && VariationKey.modifierOf(MOUSE_5) == VariationKey.BORROWED_MODIFIER,
				"a key that is no modifier borrows its value");

		check(VariationKey.heldAs(key(L_CTRL), key(L_CTRL)) == KeyModifier.CONTROL, "the set key counts");
		check(VariationKey.heldAs(key(L_SHIFT), key(L_CTRL)) == KeyModifier.SHIFT, "under Ctrl, Shift stays Shift for grab's mapping");
		check(VariationKey.heldAs(key(L_ALT), key(L_CTRL)) == KeyModifier.ALT, "under Ctrl, Alt stays Alt");
		check(VariationKey.heldAs(key(KEY_V), key(L_CTRL)) == KeyModifier.NONE, "under Ctrl, another key is no modifier");
		check(VariationKey.heldAs(key(KEY_V), key(KEY_V)) == VariationKey.BORROWED_MODIFIER, "the set key counts though it is no modifier");
		check(VariationKey.heldAs(MOUSE_5, MOUSE_5) == VariationKey.BORROWED_MODIFIER, "a mouse button can be the key");
		check(VariationKey.heldAs(key(L_ALT), key(KEY_V)) == KeyModifier.NONE, "a real Alt must not fire what the set key borrows Alt for");
		check(VariationKey.heldAs(key(L_CTRL), key(KEY_V)) == KeyModifier.CONTROL, "under another key, Ctrl stays Ctrl");
	}

	private static void verifySingleKey(KeyMapping setting) {
		setting.setKeyModifierAndCode(KeyModifier.CONTROL, key(KEY_V));
		check(setting.getKeyModifier() == KeyModifier.NONE && setting.getKey().equals(key(KEY_V)),
				"the setting holds one key: Ctrl+V in the binds screen is V");
		setting.setKeyModifierAndCode(KeyModifier.NONE, key(L_CTRL));
	}

	private static void verifyDeclaredModifiers(KeyMapping setting) {
		ClientInputBind declaredShift = ClientInputBind.toClientInput(InputKey.LMB.withModifier(InputKey.Modifier.SHIFT));
		ClientInputBind declaredCtrl = ClientInputBind.toClientInput(InputKey.LMB.withModifier(InputKey.Modifier.CONTROL));
		ClientKey lmb = ClientKey.make(InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_LEFT);

		check(ClientInputBind.toClientModifier(InputKey.Modifier.SHIFT) == KeyModifier.CONTROL, "by default a declared SHIFT is Ctrl");
		check(declaredShift.keyMatches(lmb, KeyModifier.CONTROL) && !declaredShift.keyMatches(lmb, KeyModifier.SHIFT),
				"a declared SHIFT goes with the variation key, not with the Shift key");

		check(ClientInputBind.toClientModifier(InputKey.Modifier.CONTROL) == KeyModifier.SHIFT && declaredCtrl.getKeyModifier() == KeyModifier.SHIFT,
				"by default a declared CONTROL falls to Shift");
		check(declaredCtrl.keyMatches(lmb, KeyModifier.SHIFT) && !declaredCtrl.keyMatches(lmb, KeyModifier.CONTROL),
				"by default a declared CONTROL goes with the Shift key, not with the variation key");

		setting.setKey(key(L_SHIFT));
		check(ClientInputBind.toClientModifier(InputKey.Modifier.SHIFT) == KeyModifier.SHIFT, "the setting takes effect at once");
		check(declaredShift.getKeyModifier() == KeyModifier.SHIFT, "a bind made earlier follows at once");
		check(ClientInputBind.toClientModifier(InputKey.Modifier.CONTROL) == KeyModifier.CONTROL && declaredCtrl.getKeyModifier() == KeyModifier.CONTROL,
				"on Shift a declared CONTROL is Ctrl again, as in 1.16, for a bind made earlier too");

		setting.setKey(key(KEY_V));
		check(declaredShift.getKeyModifier() == VariationKey.BORROWED_MODIFIER, "another key: the bind follows");
		check(declaredCtrl.getKeyModifier() == KeyModifier.CONTROL, "a declared CONTROL stays Ctrl under another key");
		check(ClientInputBind.toClientModifier(null) == KeyModifier.NONE, "no modifier stays none");

		setting.setKey(key(R_CTRL));
		check(declaredCtrl.getKeyModifier() == KeyModifier.SHIFT && declaredShift.getKeyModifier() == KeyModifier.CONTROL,
				"the right Ctrl is the same variation key: back to the default pair");

		setting.setKey(key(L_CTRL));
	}

	/** Ultimate Lifeform: Overdrive (SHIFT) and Beat (CONTROL) share LMB, Barrage (SHIFT, hold) and Kick (CONTROL, click) share RMB. */
	private static void verifyDeclaredPairsOnOneButton(KeyMapping setting) {
		MoveGroup group = new MoveGroup("test", Component.empty(), null);
		AbilityControlsEntry overdrive = new AbilityControlsEntry(null, "hamon_overdrive");
		AbilityControlsEntry beat = new AbilityControlsEntry(null, "hamon_overdrive_beat");
		AbilityControlsEntry barrage = new AbilityControlsEntry(null, "ultimate_hamon_barrage");
		AbilityControlsEntry kick = new AbilityControlsEntry(null, "ultimate_hamon_kick");
		group.binds.add(declaredBind(InputKey.LMB, InputKey.Modifier.SHIFT, InputMethod.CLICK, overdrive));
		group.binds.add(declaredBind(InputKey.LMB, InputKey.Modifier.CONTROL, InputMethod.CLICK, beat));
		group.binds.add(declaredBind(InputKey.RMB, InputKey.Modifier.SHIFT, InputMethod.HOLD, barrage));
		group.binds.add(declaredBind(InputKey.RMB, InputKey.Modifier.CONTROL, InputMethod.CLICK, kick));
		ClientKey lmb = ClientKey.make(InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_LEFT);
		ClientKey rmb = ClientKey.make(InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT);

		// the key map is rebuilt at every look, so one set of binds is read under every choice
		check(answers(group, lmb, KeyModifier.CONTROL, InputMethod.CLICK, overdrive) && answers(group, lmb, KeyModifier.SHIFT, InputMethod.CLICK, beat),
				"default: Ctrl + LMB is the overdrive, Shift + LMB is the beat");
		check(answers(group, rmb, KeyModifier.CONTROL, InputMethod.HOLD, barrage) && answers(group, rmb, KeyModifier.SHIFT, InputMethod.CLICK, kick),
				"default: Ctrl + RMB holds the barrage, Shift + RMB is the kick");

		setting.setKey(key(L_SHIFT));
		check(answers(group, lmb, KeyModifier.SHIFT, InputMethod.CLICK, overdrive) && answers(group, lmb, KeyModifier.CONTROL, InputMethod.CLICK, beat),
				"Shift without a relog: Shift + LMB is the overdrive, Ctrl + LMB is the beat");
		check(answers(group, rmb, KeyModifier.SHIFT, InputMethod.HOLD, barrage) && answers(group, rmb, KeyModifier.CONTROL, InputMethod.CLICK, kick),
				"Shift without a relog: Shift + RMB holds the barrage, Ctrl + RMB is the kick");

		setting.setKey(key(KEY_V));
		check(answers(group, lmb, VariationKey.BORROWED_MODIFIER, InputMethod.CLICK, overdrive) && answers(group, lmb, KeyModifier.CONTROL, InputMethod.CLICK, beat),
				"another key: it gives the overdrive, Ctrl + LMB the beat");

		setting.setKey(key(L_CTRL));
		check(answers(group, lmb, KeyModifier.CONTROL, InputMethod.CLICK, overdrive) && answers(group, lmb, KeyModifier.SHIFT, InputMethod.CLICK, beat),
				"and back to the default pair");
	}

	private static ClientControlScheme.Bind declaredBind(InputKey button, InputKey.Modifier modifier, InputMethod inputMethod, AbilityControlsEntry ability) {
		return new ClientControlScheme.Bind(ClientInputBind.toClientInput(button.withModifier(modifier)), inputMethod, ability);
	}

	/** The key map's answer for the press is exactly this ability, and nothing else is on that key and modifier. */
	private static boolean answers(MoveGroup group, ClientKey key, KeyModifier modifier, InputMethod inputMethod, AbilityControlsEntry expected) {
		InputsByKeyModifier onKey = group.getBinds(true).get(key);
		return onKey != null && onKey.getAll(modifier, inputMethod, ALL).equals(List.of(expected));
	}

	private static InputsByKeyModifier declaredSlot() {
		InputsByKeyModifier slot = new HotbarSlot(0).binds;
		slot.declare(null, InputMethod.CLICK, BASE);
		slot.declare(InputKey.Modifier.CONTROL, InputMethod.CLICK, DECLARED_CTRL);
		slot.declare(InputKey.Modifier.SHIFT, InputMethod.CLICK, VARIATION);
		slot.applyDeclaredMoves();
		return slot;
	}

	private static void verifySlotFollowsTheKey(KeyMapping setting) {
		InputsByKeyModifier slot = declaredSlot();
		check(slot.getAll(KeyModifier.CONTROL, InputMethod.CLICK, ALL).equals(List.of(VARIATION)),
				"under Ctrl, Ctrl + the key is the variation");
		check(slot.getAll(KeyModifier.SHIFT, InputMethod.CLICK, ALL).equals(List.of(DECLARED_CTRL)),
				"under Ctrl, a declared Ctrl moves to Shift + the key");
		check(slot.getAll(KeyModifier.NONE, InputMethod.CLICK, ALL).equals(List.of(BASE)), "under Ctrl, the plain key is the base");

		setting.setKey(key(L_SHIFT));
		slot.applyDeclaredMoves();
		check(slot.getAll(KeyModifier.SHIFT, InputMethod.CLICK, ALL).equals(List.of(VARIATION)), "moved to Shift: Shift + the key is the variation");
		check(slot.getAll(KeyModifier.CONTROL, InputMethod.CLICK, ALL).equals(List.of(DECLARED_CTRL)), "moved off Ctrl: a declared Ctrl comes back");

		setting.setKey(key(KEY_V));
		slot.applyDeclaredMoves();
		check(slot.getAll(VariationKey.BORROWED_MODIFIER, InputMethod.CLICK, ALL).equals(List.of(VARIATION)), "another key: it gives the variation");
		check(slot.getAll(KeyModifier.SHIFT, InputMethod.CLICK, ALL).equals(List.of(BASE)), "another key: Shift is the base");
		check(slot.getAll(KeyModifier.CONTROL, InputMethod.CLICK, ALL).equals(List.of(DECLARED_CTRL)), "another key: Ctrl is the declared Ctrl");
		check(slot.getAll(KeyModifier.NONE, InputMethod.CLICK, ALL).equals(List.of(BASE)), "the plain key is the base");

		setting.setKey(key(L_CTRL));
	}

	private static void verifySchemesFollowTheKey(KeyMapping setting) {
		ClientControlScheme scheme = new ClientControlScheme();
		MoveGroup group = new MoveGroup("test", Component.empty(), null);
		Hotbar hotbar = new Hotbar(new ClientInputBind(ClientKey.make(InputConstants.Type.MOUSE, 2)), null);
		HotbarSlot slot = new HotbarSlot(0);
		slot.binds.declare(null, InputMethod.CLICK, BASE);
		slot.binds.declare(InputKey.Modifier.SHIFT, InputMethod.CLICK, VARIATION);
		slot.binds.applyDeclaredMoves();
		hotbar.layout.add(slot);
		hotbar.applyLayout(true);
		hotbar.pickSlot(0);
		group.hotbars.add(hotbar);
		scheme.moveGroups.put("test", group);

		setting.setKey(key(L_SHIFT));
		AllControlSchemes.applyVariationKey(scheme);
		check(slot.binds.getAll(KeyModifier.SHIFT, InputMethod.CLICK, ALL).equals(List.of(VARIATION)), "a scheme in use must follow the key");
		check(hotbar.getSelected() == slot, "following the key must keep the player's selection");
		setting.setKey(key(L_CTRL));
		AllControlSchemes.applyVariationKey(scheme);
		check(slot.binds.getAll(KeyModifier.CONTROL, InputMethod.CLICK, ALL).equals(List.of(VARIATION)), "and back");
	}

	private static void verifyLockedVariationUnderEveryKey(KeyMapping setting) {
		for (int code : new int[] { L_CTRL, L_SHIFT, L_ALT, KEY_V }) {
			setting.setKey(key(code));
			InputsByKeyModifier slot = declaredSlot();
			KeyModifier variation = VariationKey.modifier();
			check(slot.getAll(variation, InputMethod.CLICK, LOCKED).equals(List.of(BASE)),
					"a locked variation leaves the key on the base under key " + code);
			check(slot.getAll(variation, InputMethod.CLICK, entry -> entry == VARIATION).equals(List.of(VARIATION)),
					"an unlocked variation takes the key under key " + code);
			InputsByKeyModifier gaveWay = new HotbarSlot(1).binds;
			gaveWay.declare(null, InputMethod.CLICK, BASE);
			gaveWay.declare(InputKey.Modifier.SHIFT, InputMethod.CLICK, VARIATION);
			gaveWay.applyDeclaredMoves();
			check(gaveWay.getAll(KeyModifier.NONE, InputMethod.CLICK, entry -> entry == VARIATION).equals(List.of(VARIATION)),
					"a base with nothing to do gives way to its variation on the plain key under key " + code);
		}
		setting.setKey(key(L_CTRL));
	}

	/** Grab and charged heavy stay on their own mapping, default Shift + RMB, under every choice; they can be rebound. */
	private static void verifyKeysThatDoNotMove(KeyMapping setting) {
		KeyMapping grab = new StandaloneMapping("test.grab", KeyModifier.SHIFT, RMB);
		ClientInputBind input = new ClientInputBind(grab);
		ClientKey rmb = ClientKey.make(InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT);
		ClientKey mouse5 = ClientKey.make(InputConstants.Type.MOUSE, 4);
		for (int code : new int[] { L_CTRL, L_SHIFT, L_ALT, KEY_V }) {
			setting.setKey(key(code));
			check(input.getKeyModifier() == KeyModifier.SHIFT, "grab's mapping must stay Shift + RMB under key " + code);
			check(input.keyMatches(rmb, KeyModifier.SHIFT), "grab must answer Shift + RMB under key " + code);
		}
		setting.setKey(key(L_CTRL));
		check(!input.keyMatches(rmb, KeyModifier.CONTROL), "the variation key on RMB must not grab");

		ClientInputBind standMode = new ClientInputBind(new StandaloneMapping("test.stand_mode", KeyModifier.CONTROL, key(KEY_R)));
		for (int code : new int[] { L_CTRL, L_SHIFT, L_ALT, KEY_V }) {
			setting.setKey(key(code));
			check(standMode.getKeyModifier() == KeyModifier.CONTROL, "a key mapping with Ctrl (stand mode, item swap) must stay Ctrl under key " + code);
		}
		setting.setKey(key(L_CTRL));

		grab.setKeyModifierAndCode(KeyModifier.ALT, MOUSE_5);
		check(input.keyMatches(mouse5, KeyModifier.ALT) && !input.keyMatches(rmb, KeyModifier.SHIFT), "rebound: another key and modifier");
		grab.setKeyModifierAndCode(KeyModifier.NONE, MOUSE_5);
		check(input.keyMatches(mouse5, KeyModifier.NONE) && input.keyMatches(mouse5, KeyModifier.CONTROL), "rebound: no modifier");

		check(MovesetBuilder.DEFAULT_GRAB_INPUT instanceof InputUseVanillaMapping, "grab is a key mapping");
		check(MovesetBuilder.DEFAULT_GRAB_THROW_INPUT instanceof InputUseVanillaMapping throwMapping
				&& MovesetBuilder.GRAB_THROW_KEY_MAPPING_NAME.equals(throwMapping.keyMappingName), "throw is a key mapping");
		check(MovesetBuilder.DEFAULT_GRAB_RELEASE_INPUT instanceof InputUseVanillaMapping releaseMapping
				&& MovesetBuilder.GRAB_RELEASE_KEY_MAPPING_NAME.equals(releaseMapping.keyMappingName), "release is a key mapping");
	}

	/** The throw on its mapping stays on one button with what shares RMB, and a Hotbars-off scheme still drops it there. */
	private static void verifyThrowSharesItsButton() {
		KeyMapping throwKey = new StandaloneMapping("test.throw", KeyModifier.NONE, RMB);
		MoveGroup group = new MoveGroup("test", Component.empty(), null);
		ClientControlScheme.Bind heavy = new ClientControlScheme.Bind(
				ClientInputBind.toClientInput(InputKey.RMB), InputMethod.CLICK, new AbilityControlsEntry(null, "heavy_punch"));
		ClientControlScheme.Bind thrown = new ClientControlScheme.Bind(
				new ClientInputBind(throwKey), InputMethod.HOLD, new AbilityControlsEntry(null, "grab_throw"));
		group.binds.add(heavy);
		group.binds.add(thrown);
		ClientKey rmb = ClientKey.make(InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT);
		ClientKey mouse5 = ClientKey.make(InputConstants.Type.MOUSE, 4);

		Map<ClientKey, InputsByKeyModifier> binds = group.getBinds(true);
		check(binds.size() == 1 && binds.get(rmb).getAll(KeyModifier.NONE, InputMethod.HOLD, ALL).get(0).abilityName().equals("grab_throw")
				&& binds.get(rmb).getAll(KeyModifier.NONE, InputMethod.CLICK, ALL).get(0).abilityName().equals("heavy_punch"),
				"by default the throw must share RMB with the heavy punch");

		throwKey.setKeyModifierAndCode(KeyModifier.NONE, MOUSE_5);
		binds = group.getBinds(true);
		check(binds.get(mouse5).getAll(KeyModifier.NONE, InputMethod.HOLD, ALL).get(0).abilityName().equals("grab_throw")
				&& binds.get(rmb).getAll(KeyModifier.NONE, InputMethod.HOLD, ALL).isEmpty(),
				"a throw moved off RMB must leave it");

		check(ClientControlScheme.Bind.isAttackOrUseButton(MovesetBuilder.DEFAULT_GRAB_THROW_INPUT),
				"the throw counts as the use button, as it did as InputKey.RMB");
		check(!ClientControlScheme.Bind.isAttackOrUseButton(MovesetBuilder.DEFAULT_GRAB_RELEASE_INPUT), "the release does not");
	}

	private static void verifyDefaultWithoutTheMapping() {
		check(VariationKey.modifier() == KeyModifier.CONTROL, "the default is Ctrl");
	}

	private static void verifySources() {
		Path root = Path.of(System.getProperty("user.dir")).resolve("src/main");
		String java = "java/rotp/core/";
		String keybinds = squash(read(root.resolve(java + "client/input/VanillaKeybinds.java")));
		check(keybinds.contains(squash("VariationKey.mappingName(), KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, MAIN_CATEGORY")),
				"the variation key is registered with Left Ctrl as its default");
		check(keybinds.contains(squash("VariationKey.useMapping(binds.variationKey);")), "the rule must read the registered mapping");
		check(keybinds.contains(squash("MovesetBuilder.GRAB_THROW_KEY_MAPPING_NAME, whileBound(() -> binds.grabThrow), InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT")),
				"throw defaults to RMB");
		check(keybinds.contains(squash("MovesetBuilder.GRAB_RELEASE_KEY_MAPPING_NAME, whileBound(() -> binds.grabRelease), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q")),
				"release defaults to Q");
		check(keybinds.contains(squash("MovesetBuilder.SWAP_ITEMS_KEY_MAPPING_NAME, KeyConflictContext.IN_GAME, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F")),
				"item swap defaults to Ctrl+F");
		check(keybinds.contains(squash(".key.stand_mode\", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R")),
				"stand mode defaults to Ctrl+R");

		String moveset = squash(read(root.resolve(java + "powersystem/MovesetBuilder.java")));
		check(moveset.contains(squash("\"items_swap_w_user\", InputMethod.CLICK, new InputUseVanillaMapping(SWAP_ITEMS_KEY_MAPPING_NAME)")),
				"items_swap_w_user goes through its key mapping");

		String items = read(root.resolve(java + "subsystems/entity_externalcontainer/_stand/input/ClientStandItemInputs.java"));
		check(items.contains("vanillaKeybinds.swapItems.consumeClick()") && !items.contains("new KeyMapping("),
				"item swap must read the mapping of the key binds screen");
		check(!between(items, "public static void handle(", "public static void handleInManualControl(").contains("hasControlDown"),
				"a rebound item swap must not ask for Ctrl");

		String input = squash(read(root.resolve(java + "client/input/InputHandler.java")));
		check(input.contains(squash("private List<Key> modifiersQueue")) && input.contains(squash("VariationKey.heldAs(modifiersQueue.get(i))")),
				"the current modifier is read through the setting at every look");
		check(input.contains(squash("!vanillaKeybinds.disableHUDControls.getKey().equals(vanillaKeybinds.variationKey.getKey())")),
				"a variation key on Alt must not turn off the controls it is held for");
		String syncCall = squash("syncVariationKey(false);");
		check(between(input, squash("private void addKeyModifier(Key key)"), squash("private void removeKeyModifier(Key key)")).contains(syncCall),
				"the server must hear the key at the key event, before the skill's press");
		check(between(input, squash("private void removeKeyModifier(Key key)"), squash("public KeyModifier getCurModifier()")).contains(syncCall),
				"the server must hear the key let go");
		check(input.contains(squash("public void onLogin(ClientPlayerNetworkEvent.LoggingIn event) { syncVariationKey(true);")),
				"a new connection must hear where the key stands");
		check(between(input, squash("public void handleKeyBindingsPost(ClientTickEvent.Post event)"), squash("vanillaKeybinds.handleTick();")).contains(squash("AllControlSchemes.followVariationKey();")),
				"a change in the binds screen must reach the slots with no restart");
		String schemes = squash(read(root.resolve(java + "client/input/controlscheme/AllControlSchemes.java")));
		check(schemes.contains(squash("controls.values().forEach(AllControlSchemes::applyVariationKey);")),
				"every scheme must follow a changed key");

		String hud = squash(read(root.resolve(java + "client/ui/hud_power/PowerHudControlsElement.java")));
		check(hud.contains(squash("standHotbar && modifier != VariationKey.modifier() ? KeyModifier.NONE : modifier")),
				"the HUD shows the variation under the key that is set");
		check(hud.contains(squash("modifier == KeyModifier.ALT && VariationKey.modifier() != KeyModifier.ALT")),
				"the HUD must not drop a variation key on Alt");

		String text = squash(read(root.resolve(java + "client/ui/text/StandSkillText.java")));
		check(text.contains(squash("\".controls\", VariationKeyName.get()")) && text.contains(squash("\".desc\", VariationKeyName.get()")),
				"skill texts must name the key that is set");

		for (String lang : List.of("en_pt", "en_us", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br", "ru_ru", "uk_ua", "zh_cn", "zh_tw")) {
			String file = read(root.resolve("resources/assets/jojo_ripples/lang/" + lang + ".json"));
			for (String key : List.of("variation", "variation.desc", "grab_throw", "grab_throw.desc", "grab_release", "grab_release.desc", "swap_items.desc")) {
				check(file.contains("\"jojo_ripples.key." + key + "\":"), lang + " lacks jojo_ripples.key." + key);
			}
			for (String line : file.split("\n")) {
				if (line.contains(".controls\"") && line.contains("Shift")) {
					throw new AssertionError(lang + " names Shift in a controls text: " + line.trim());
				}
			}
			check(lineOf(file, "jojo_ripples.skill.time_stop.controls").contains("%s"), lang + ": the time stop's text must take the key's name");
			check(lineOf(file, "jojo_ripples.skill.barrier.desc").contains("%s"), lang + ": the net's text must take the key's name");
			check(!lineOf(file, "jojo_ripples.skill.item_use.desc").contains("Shift"), lang + ": Use Item's text must name no key that can be rebound");
			for (String stand : List.of("star_platinum", "the_world")) {
				String description = valueOf(lineOf(file, "jojo_ripples.skill." + stand + ".time_stop.desc"));
				check(description.contains("\\n"), lang + ": " + stand + "'s time stop text must have its charge and double press line");
				// a default skin's overlay answers before the base file
				String overlay = read(root.resolve("resources/assets/jojo_ripples/stand_skins/" + stand + "/assets/jojo_ripples/lang/" + lang + ".json"));
				check(valueOf(lineOf(overlay, "jojo_ripples.skill.time_stop.desc")).equals(description),
						lang + ": the skin overlay of " + stand + " must hold the base file's time stop text");
			}
		}
		String english = read(root.resolve("resources/assets/jojo_ripples/lang/en_us.json"));
		check(lineOf(english, "jojo_ripples.key.variation.desc").contains("holding sprint and pressing the skill key"),
				"the description says what Ctrl does with sprint");
		String chinese = read(root.resolve("resources/assets/jojo_ripples/lang/zh_cn.json"));
		check(lineOf(chinese, "jojo_ripples.key.variation.desc").contains("按住疾跑再按技能键"), "the Chinese description says it too");

		String[] followers = {
				"impl/powers/hamon/abilities/HamonHealingAbility.java",
				"impl/stands/crazydiamond/CrazyDBlockBulletAbility.java",
				"impl/stands/crazydiamond/CrazyDRestoreTerrainAbility.java",
				"impl/stands/magiciansred/MagiciansRedCrossfireHurricaneAbility.java",
				"impl/stands/theworld/TheWorldTSPunchAbility.java",
				"impl/stands/_entitybase/StandEntityManualControlToggle.java",
				"impl/stands/hierophant/HierophantBarrierAbility.java",
				"impl/stands/crazydiamond/client/CrazyDBloodHomingMarker.java",
				"impl/stands/crazydiamond/client/TranslucentBlockRenderHelper.java" };
		for (String file : followers) {
			String source = read(root.resolve(java + file));
			check(source.contains("StandManualInput.isShiftHeld("), file + " must follow the variation key");
			check(!source.contains("isShiftKeyDown()"), file + " must not read the sneak for this");
		}
		check(!read(root.resolve(java + "impl/stands/_entitybase/StandEntityManualControlToggle.java")).contains("KEY_LSHIFT"),
				"the keep-position press must follow the variation key");
	}

	private static String lineOf(String file, String key) {
		for (String line : file.split("\n")) {
			if (line.contains("\"" + key + "\":")) {
				return line;
			}
		}
		throw new AssertionError("no line for " + key);
	}

	private static String valueOf(String line) {
		String value = line.substring(line.indexOf("\":") + 2).trim();
		return value.endsWith(",") ? value.substring(0, value.length() - 1) : value;
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static String between(String source, String start, String end) {
		int from = source.indexOf(start);
		int to = source.indexOf(end, from + start.length());
		if (from < 0 || to < 0) {
			throw new AssertionError("failed to locate " + start + " .. " + end);
		}
		return source.substring(from, to);
	}

	private static String squash(String source) {
		return source.replaceAll("\\s+", "");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class StandaloneMapping extends KeyMapping {
		StandaloneMapping(String name, KeyModifier modifier, InputConstants.Key key) {
			super(name, KeyConflictContext.IN_GAME, modifier, key, "test.controls");
		}

		@Override
		public boolean isUnbound() {
			// Same unknown-key predicate without initializing GLFW in the standalone JVM.
			return getKey().getType() == InputConstants.Type.KEYSYM && getKey().getValue() == -1;
		}
	}
}
