package rotp.core.client.input;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import rotp.core.client.ClientGlobals;
import rotp.core.client.ClientPowerCache;
import rotp.core.client.input.controlscheme.AllControlSchemes;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.InputsByKeyModifier;
import rotp.core.client.input.controlscheme.ClientKey;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_useitem.ClStandClickPacket;
import rotp.core.subsystems.entity_useitem.ServerSideLivingClick;
import rotp.core.subsystems.entity_useitem.StandItemUse;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class StandVanillaClickInput {

	@SubscribeEvent
	public static void onVanillaClickInput(InteractionKeyMappingTriggered event) {
		int keyCode = event.isAttack() ? InputConstants.MOUSE_BUTTON_LEFT : event.isUseItem() ? InputConstants.MOUSE_BUTTON_RIGHT : -1;
		if (keyCode < 0) {
			return;
		}
		InputHandler inputHandler = InputHandler.getInstance();
		if (event.isUseItem() && inputHandler != null
				&& inputHandler.shouldPreserveSemanticVanillaUsePress()) {
			return;
		}
		if (handleStandItemUseInput(event, keyCode, inputHandler)) {
			return;
		}
		
		if (handleVanillaMappedAbilityInput(event, keyCode)) {
			return;
		}
	}

	private static boolean handleStandItemUseInput(InteractionKeyMappingTriggered event, int keyCode,
			InputHandler inputHandler) {
		StandEntity stand = ClientGlobals.playerStandEntity;
		if (inputHandler == null || stand == null
				|| keyCode != InputConstants.MOUSE_BUTTON_RIGHT
				|| ClStandClickPacket.isUnsummoning(stand)
				|| !clickGoesToStand(stand)) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		ClientKey key = ClientKey.make(
				InputConstants.Type.MOUSE, keyCode);
		HeldKeyTimer existingHeldKey = inputHandler.getHeldKeyTimer(key);
		boolean rawPressAlreadyReleased = existingHeldKey == null;
		if (!rawPressAlreadyReleased) {
			// a timer that lets vanilla through is the raw press of a button whose abilities stepped aside or refused: that press is ours
			if (existingHeldKey.cancelVanilla) {
				event.setCanceled(true);
				event.setSwingHand(false);
				return true;
			}
		}
		long inputGeneration = AbilityInput.nextInputGeneration(
				mc.player, key.keyId());
		event.setCanceled(true);
		event.setSwingHand(false);
		inputHandler.putHeldKeyTimer(
				key, new HeldKeyTimer(
						key, true, KeyModifier.NONE));
		if (rawPressAlreadyReleased) {
			KeyMapping mapping = event.getKeyMapping();
			inputHandler.rawMouseClicks.replayed(key, mapping::isDown);
		}

		HitResult target = mc.hitResult;
		PacketDistributor.sendToServer(new ClStandClickPacket(
				target, inputGeneration,
				InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND));
		return true;
	}
	
	private static boolean handleVanillaMappedAbilityInput(InteractionKeyMappingTriggered event, int keyCode) {
		ClientKey key = ClientKey.make(InputConstants.Type.MOUSE, keyCode);
		InputHandler inputHandler = InputHandler.getInstance();
		if (inputHandler == null) {
			return false;
		}
		HeldKeyTimer heldKey = inputHandler.getHeldKeyTimer(key);
		if (heldKey != null) {
			if (heldKey.cancelVanilla) {
				event.setCanceled(true);
				event.setSwingHand(false);
				return true;
			}
			return false;
		}

		if (!inputHandler.rawMouseClicks.replaysVanillaClick(key)
				|| !shouldHandleVanillaMappedAbilityInput(inputHandler)) {
			return false;
		}
		boolean cancel = inputHandler.input(key, InputConstants.PRESS, 0);
		if (inputHandler.getHeldKeyTimer(key) != null) {
			KeyMapping mapping = event.getKeyMapping();
			inputHandler.rawMouseClicks.replayed(key, mapping::isDown);
		}
		if (cancel) {
			event.setCanceled(true);
			event.setSwingHand(false);
			return true;
		}
		return false;
	}

	private static boolean shouldHandleVanillaMappedAbilityInput(InputHandler inputHandler) {
		if (ClientGlobals.playerStandEntity != null) {
			return true;
		}
		if (inputHandler.curPowerClassToggle == PowerClass.STAND) {
			return true;
		}
		return !inputHandler.hotbarsSelection.isEmpty();
	}

	// The Shift key itself, whatever the variation key is: it leaves the button to grab and charged heavy and to the user's own hand
	private static boolean clickGoesToStand(StandEntity stand) {
		return !Screen.hasShiftDown()
				&& (stand.isManuallyControlled() || !InputHandler.inputsDisabled && ServerSideLivingClick.isEntityHoldingAnItem(stand))
				&& StandItemUse.takesRightClick(ClientPowerCache.getPower(PowerClass.STAND), stand);
	}

	/**
	 * A fresh list of moves: with an item in the Stand's hands and the click going to it, the moves on the bare button have to
	 * wait (StandItemUse.inputStateBesideTheItem), so neither the press nor the HUD finds them.
	 */
	public static void movesGiveWayToTheItem(Power<?> power, AvailableAbilities moves) {
		StandEntity stand = ClientGlobals.playerStandEntity;
		if (!(power instanceof StandPower) || !power.hasPower() || stand == null
				|| !ServerSideLivingClick.isEntityHoldingAnItem(stand)
				|| ClStandClickPacket.isUnsummoning(stand) || !clickGoesToStand(stand)) {
			return;
		}
		ClientControlScheme scheme = AllControlSchemes.getForPowerType(power.getPowerType());
		InputsByKeyModifier onTheButton = scheme != null
				? scheme.getCurGroup().getBinds(scheme.isHudActive()).get(InputHandler.RMB) : null;
		Map<InputMethod, List<AbilityControlsEntry>> bare = onTheButton != null ? onTheButton.movesByModifier.get(KeyModifier.NONE) : null;
		if (bare != null) {
			bare.values().stream().flatMap(List::stream)
					.map(move -> moves.getContextVariationContainer(move.abilityName()))
					.filter(Objects::nonNull)
					.forEach(move -> move.clientInputState = StandItemUse.inputStateBesideTheItem(move.clientInputState));
		}
	}

	/** The item the HUD shows on the right mouse button's row when the Stand's click goes to it. */
	public static ItemStack itemOnRightClick(ClientControlScheme controlScheme, ClientKey key) {
		StandEntity stand = ClientGlobals.playerStandEntity;
		if (controlScheme.powerClassCosmetic != PowerClass.STAND || !InputHandler.RMB.equals(key)
				|| stand == null || ClStandClickPacket.isUnsummoning(stand) || !clickGoesToStand(stand)) {
			return ItemStack.EMPTY;
		}
		return !stand.getMainHandItem().isEmpty() ? stand.getMainHandItem() : stand.getOffhandItem();
	}
}
