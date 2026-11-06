package rotp.core.subsystems.entity_externalcontainer._stand.input;

import rotp.core.client.ClientGlobals;
import rotp.core.client.input.InputHandler;
import rotp.core.core.JojoMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class ClientStandItemInputs {

	@SubscribeEvent
	public static void handle(ClientTickEvent.Pre event) {
		InputHandler input = InputHandler.getInstance();
		if (input == null) {
			return;
		}
		while (input.vanillaKeybinds.swapItems.consumeClick()) {
			if (ClientGlobals.playerStandEntity != null) {
				PacketDistributor.sendToServer(ClStandItemInputPacket.packet(StandItemInput.Action.SWAP_USER_AND_STAND));
			}
		}
	}

	public static void handleInManualControl(Minecraft mc) {
		while (mc.options.keyDrop.consumeClick()) {
			boolean fullStack = Screen.hasControlDown();
			PacketDistributor.sendToServer(ClStandItemInputPacket.packet(fullStack ? StandItemInput.Action.DROP_FULL_STACK : StandItemInput.Action.DROP));
		}
		while (mc.options.keySwapOffhand.consumeClick()) {
			PacketDistributor.sendToServer(ClStandItemInputPacket.packet(StandItemInput.Action.SWAP_HANDS));
		}
	}
}
