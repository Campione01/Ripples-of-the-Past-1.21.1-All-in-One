package rotp.core.impl.stands.crazydiamond.client;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

import org.lwjgl.glfw.GLFW;

import rotp.core.core.JojoMod;
import rotp.core.init.ModStatusEffects;

import net.minecraft.client.Minecraft;
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
 * walking (and jump with sneak), the arms swap the left and right mouse buttons. Everything is read from the effect each
 * time, so it ends with the effect; the options file is never touched (1.16 rebound the attack and use keys).
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class MisshapenControls {
	// 1.16 ZERO_SENSITIVITY: the sensitivity at which the turn factor 0.6 * s + 0.2 is 0
	static final double ZERO_SENSITIVITY = -1.0 / 3.0;
	static final ClickRouter CLICKS = new ClickRouter();

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

	// called by MouseHandlerMisshapenArmsMixin for every mouse button event, before the game sees it
	public static int routeMouseButton(int button, int action) {
		Minecraft mc = Minecraft.getInstance();
		boolean swap = mc.player != null && mc.screen == null && mc.player.hasEffect(ModStatusEffects.MISSHAPEN_ARMS);
		return CLICKS.route(button, action, swap);
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
	public static void forgetSwappedClicksOnLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		CLICKS.clear();
	}

	// respawn and dimension change: a button let go meanwhile (a screen took the release) is forgotten
	@SubscribeEvent
	public static void forgetSwappedClicksOnNewPlayer(ClientPlayerNetworkEvent.Clone event) {
		long window = Minecraft.getInstance().getWindow().getWindow();
		CLICKS.keepOnly(button -> GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS);
	}


	/**
	 * Left and right buttons pressed while the arms are misshapen go to the other button, and so does their release, even
	 * once the effect has ended, so no key stays held down.
	 */
	public static final class ClickRouter {
		private final Set<Integer> swappedHeld = new HashSet<>();

		public int route(int button, int action, boolean swap) {
			if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				return button;
			}
			if (action == GLFW.GLFW_PRESS) {
				if (swap) {
					swappedHeld.add(button);
					return other(button);
				}
				swappedHeld.remove(button);
				return button;
			}
			if (action == GLFW.GLFW_RELEASE && swappedHeld.remove(button)) {
				return other(button);
			}
			return button;
		}

		public void clear() {
			swappedHeld.clear();
		}

		public void keepOnly(IntPredicate stillHeld) {
			swappedHeld.removeIf(button -> !stillHeld.test(button));
		}

		public boolean isSwappedHeld(int button) {
			return swappedHeld.contains(button);
		}

		private static int other(int button) {
			return button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : GLFW.GLFW_MOUSE_BUTTON_LEFT;
		}
	}
}
