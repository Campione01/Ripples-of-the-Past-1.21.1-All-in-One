package rotp.core.subsystems.entity_externalcontainer._stand.input;

import rotp.core.client.ClientGlobals;
import rotp.core.client.input.InputHandler;
import rotp.core.client.input.VanillaKeybinds;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
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
		handleOwnKeys(input.vanillaKeybinds, Minecraft.getInstance());
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

	private static void handleOwnKeys(VanillaKeybinds keys, Minecraft mc) {
		while (keys.tossStandItem.consumeClick()) {
			if (keyActs(StandItemInput.Action.DROP, keys.tossStandItem, mc.options.keyDrop, mc)) {
				boolean fullStack = Screen.hasControlDown();
				PacketDistributor.sendToServer(ClStandItemInputPacket.packet(fullStack ? StandItemInput.Action.DROP_FULL_STACK : StandItemInput.Action.DROP));
			}
		}
		while (keys.swapStandItemHands.consumeClick()) {
			if (keyActs(StandItemInput.Action.SWAP_HANDS, keys.swapStandItemHands, mc.options.keySwapOffhand, mc)) {
				PacketDistributor.sendToServer(ClStandItemInputPacket.packet(StandItemInput.Action.SWAP_HANDS));
			}
		}
	}

	private static boolean keyActs(StandItemInput.Action action, KeyMapping key, KeyMapping vanillaKey, Minecraft mc) {
		StandEntity stand = ClientGlobals.playerStandEntity;
		Player player = mc.player;
		return stand != null && player != null && StandItemInput.ownKeyActs(action, stand.isManuallyControlled(), key.same(vanillaKey),
				!stand.getMainHandItem().isEmpty() || !stand.getOffhandItem().isEmpty(),
				player.getMainHandItem().isEmpty(), player.getOffhandItem().isEmpty());
	}
}
