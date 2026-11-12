package rotp.core.mechanics.jojopose.client;

import org.lwjgl.glfw.GLFW;

import rotp.core.client.input.VanillaKeybinds;
import rotp.core.core.JojoMod;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** The pose list key. The official has none (the list is beside the chat only); a new key, so unbound by default. */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class JojoPoseKey {
	public static final String NAME = JojoMod.MOD_ID + ".key.jojo_pose";
	public static final KeyMapping KEY = new VanillaKeybinds.Jokerge(NAME, KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, VanillaKeybinds.MAIN_CATEGORY).withDescTooltip();

	private JojoPoseKey() {}

	@SubscribeEvent
	public static void register(RegisterKeyMappingsEvent event) {
		event.register(KEY);
	}
}
