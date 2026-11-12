package rotp.core.impl.stands.crazydiamond.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import rotp.core.core.JojoMod;
import rotp.core.init.ModStatusEffects;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * 1.16 InputHandler: what a misshapen part does to its player's controls. The face inverts the mouse look, the legs invert
 * walking (and jump with sneak), the arms swap attack and use on whatever key or button each is bound to. Everything is
 * read from the effect each time, so it ends with the effect; the key bindings and the options file are never touched
 * (1.16 rebound the attack and use keys).
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class MisshapenControls {
	// 1.16 ZERO_SENSITIVITY: the sensitivity at which the turn factor 0.6 * s + 0.2 is 0
	static final double ZERO_SENSITIVITY = -1.0 / 3.0;
	static final KeyRouter<InputConstants.Key> KEYS = new KeyRouter<>();
	static final KeyRouter<Integer> RAW_BUTTONS = new KeyRouter<>();

	private MisshapenControls() {}

	// 1.16 setMouseSensitivity: mirrored around zero sensitivity, so the turn factor keeps its size and changes its sign
	public static double invertedSensitivity(double sensitivity) {
		return -sensitivity + ZERO_SENSITIVITY * 2;
	}

	public static void invertMovement(Input input) {
		input.forwardImpulse *= -1;
		input.leftImpulse *= -1;
		boolean down = input.down;
		input.down = input.up;
		input.up = down;
		boolean left = input.left;
		input.left = input.right;
		input.right = left;
		boolean jumping = input.jumping;
		input.jumping = input.shiftKeyDown;
		input.shiftKeyDown = jumping;
	}

	// KeyMappingMisshapenArmsMixin: every key or button the game passes to KeyMapping.set and KeyMapping.click
	public static InputConstants.Key routeSet(InputConstants.Key key, boolean down) {
		return KEYS.set(key, down, otherOfAttackOrUse(key), armsSwapped());
	}

	public static InputConstants.Key routeClick(InputConstants.Key key) {
		return KEYS.click(key, otherOfAttackOrUse(key), armsSwapped());
	}

	/*
	 * InputHandler.handleMouseInput: 1.16's Stand attack and ability rows rode the rebound attack and use keys. The raw
	 * button the Stand's input path takes is swapped; the game's own event keeps its button, so a click no Stand move
	 * takes reaches the key mappings unswapped and is swapped there once, and a click a move takes is cancelled before them.
	 */
	public static int routeRawMouseButton(int button, int action) {
		return rawRoute(RAW_BUTTONS, button, action, armsSwapped());
	}

	public static int rawRoute(KeyRouter<Integer> router, int button, int action, boolean swap) {
		Integer other = button == InputConstants.MOUSE_BUTTON_LEFT ? Integer.valueOf(InputConstants.MOUSE_BUTTON_RIGHT)
				: button == InputConstants.MOUSE_BUTTON_RIGHT ? Integer.valueOf(InputConstants.MOUSE_BUTTON_LEFT) : null;
		return switch (action) {
			case InputConstants.PRESS -> router.set(button, true, other, swap);
			case InputConstants.RELEASE -> router.set(button, false, other, swap);
			default -> button;
		};
	}

	// KeyMapping.releaseAll (a screen opened, the window lost focus) let every key mapping go
	public static void forgetHeldKeys() {
		KEYS.clear();
	}

	private static boolean armsSwapped() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.screen == null && mc.player.hasEffect(ModStatusEffects.MISSHAPEN_ARMS);
	}

	@Nullable
	private static InputConstants.Key otherOfAttackOrUse(InputConstants.Key key) {
		Options options = Minecraft.getInstance().options;
		InputConstants.Key attack = options.keyAttack.getKey();
		InputConstants.Key use = options.keyUse.getKey();
		if (attack.equals(use) || options.keyAttack.isUnbound() || options.keyUse.isUnbound()) {
			return null;
		}
		return key.equals(attack) ? use : key.equals(use) ? attack : null;
	}

	@SubscribeEvent
	public static void invertLook(CalculatePlayerTurnEvent event) {
		Player player = Minecraft.getInstance().player;
		if (player != null && player.hasEffect(ModStatusEffects.MISSHAPEN_FACE)) {
			event.setMouseSensitivity(invertedSensitivity(event.getMouseSensitivity()));
		}
	}

	@SubscribeEvent
	public static void invertWalking(MovementInputUpdateEvent event) {
		if (event.getEntity().hasEffect(ModStatusEffects.MISSHAPEN_LEGS)) {
			invertMovement(event.getInput());
		}
	}

	@SubscribeEvent
	public static void releaseSwappedKeysOnLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		releaseSwappedKeys();
		RAW_BUTTONS.clear();
	}

	// respawn (death) and dimension change; a raw button still held keeps its decision for its coming release
	@SubscribeEvent
	public static void releaseSwappedKeysOnNewPlayer(ClientPlayerNetworkEvent.Clone event) {
		releaseSwappedKeys();
		long window = Minecraft.getInstance().getWindow().getWindow();
		RAW_BUTTONS.keepOnly(button -> GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS);
	}

	private static void releaseSwappedKeys() {
		for (InputConstants.Key driven : KEYS.takeSwapped()) {
			KeyMapping.set(driven, false);
		}
	}


	/**
	 * What each held attack or use key drives, decided once at its press: the other one's mappings while the arms are
	 * misshapen, its own otherwise. Its repeats and its release follow that decision, so a key held as the effect begins or
	 * ends lets go of what it pressed and nothing stays held.
	 */
	public static final class KeyRouter<K> {
		private final Map<K, K> held = new HashMap<>();

		public K set(K key, boolean down, @Nullable K other, boolean swap) {
			K drives = held.get(key);
			if (!down) {
				held.remove(key);
				return drives != null ? drives : key;
			}
			if (drives != null) {
				return drives;
			}
			if (other == null) {
				return key;
			}
			drives = swap ? other : key;
			held.put(key, drives);
			return drives;
		}

		public K click(K key, @Nullable K other, boolean swap) {
			K drives = held.get(key);
			if (drives != null) {
				return drives;
			}
			return other != null && swap ? other : key;
		}

		public boolean isHeld(K key) {
			return held.containsKey(key);
		}

		public void clear() {
			held.clear();
		}

		public void keepOnly(Predicate<K> stillHeld) {
			held.keySet().removeIf(key -> !stillHeld.test(key));
		}

		/** Forgets every held key; returns what the swapped ones drive, to be let go. */
		public List<K> takeSwapped() {
			List<K> swapped = new ArrayList<>();
			held.forEach((key, drives) -> {
				if (!drives.equals(key)) {
					swapped.add(drives);
				}
			});
			held.clear();
			return swapped;
		}
	}
}
