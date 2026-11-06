package rotp.core.client.input;

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
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
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
				|| !standCanRightClick(inputHandler, stand)
				|| ClStandClickPacket.isUnsummoning(stand)
				|| (!stand.isManuallyControlled()
						&& (InputHandler.inputsDisabled
								|| !ServerSideLivingClick.isEntityHoldingAnItem(stand)))) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		ClientKey key = ClientKey.make(
				InputConstants.Type.MOUSE, keyCode);
		HeldKeyTimer existingHeldKey = inputHandler.getHeldKeyTimer(key);
		if (existingHeldKey != null && existingHeldKey.cancelVanilla) {
			event.setCanceled(true);
			event.setSwingHand(false);
			return true;
		}
		// a timer that lets vanilla through is the raw press of a button whose abilities stepped aside or refused
		boolean rawPressAlreadyReleased = existingHeldKey == null;
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

	// Shift keeps the button for the Shift + RMB moves (grab, charged heavy) and the user's own click
	private static boolean standCanRightClick(InputHandler inputHandler, StandEntity stand) {
		return !inputHandler.isModifierKeyPressed(KeyModifier.SHIFT)
				&& StandItemUse.takesRightClick(ClientPowerCache.getPower(PowerClass.STAND), stand);
	}

	/**
	 * While the Stand holds an item and takes the click for it, the abilities bound to the bare right mouse button (heavy
	 * punch, guard) step aside, unless they want the item (AbilityInputState.WITH_ITEM_HELD).
	 */
	public static void onMovesUpdate(Power<?> power, AvailableAbilities abilities) {
		StandPower standPower = PowerClass.STAND.cast(power);
		InputHandler inputHandler = InputHandler.getInstance();
		StandEntity stand = ClientGlobals.playerStandEntity;
		if (standPower == null || inputHandler == null || stand == null || !standPower.hasPower()
				|| !ServerSideLivingClick.isEntityHoldingAnItem(stand)
				|| ClStandClickPacket.isUnsummoning(stand)
				|| !standCanRightClick(inputHandler, stand)) {
			return;
		}
		ClientControlScheme controlScheme = AllControlSchemes.getForPowerType(standPower.getPowerType());
		InputsByKeyModifier rmbBinds = controlScheme != null
				? controlScheme.getCurGroup().getBinds(controlScheme.isHudActive()).get(InputHandler.RMB) : null;
		if (rmbBinds == null) {
			return;
		}
		for (InputMethod inputMethod : InputMethod.values()) {
			for (AbilityControlsEntry entry : rmbBinds.getAll(KeyModifier.NONE, inputMethod)) {
				AbilityConditionCheck ability = abilities.getContextVariationContainer(entry.abilityName());
				if (ability != null) {
					AbilityInputState inputState = AbilityInputState.withValue(ability.clientInputState);
					if (!inputState.getFlag(AbilityInputState.WITH_ITEM_HELD)) {
						inputState.setFlag(AbilityInputState.IS_ACTIVE, false);
						inputState.setFlag(AbilityInputState.VISIBLE_WHEN_INACTIVE, false);
						ability.clientInputState = inputState._value;
					}
				}
			}
		}
	}

	/** The item the HUD shows on the right mouse button's row when the Stand's right click uses it. */
	public static ItemStack itemOnRightClick(ClientControlScheme controlScheme, ClientKey key, KeyModifier modifier) {
		InputHandler inputHandler = InputHandler.getInstance();
		StandEntity stand = ClientGlobals.playerStandEntity;
		if (controlScheme.powerClassCosmetic != PowerClass.STAND || modifier != KeyModifier.NONE || !InputHandler.RMB.equals(key)
				|| inputHandler == null || stand == null || ClStandClickPacket.isUnsummoning(stand)
				|| !standCanRightClick(inputHandler, stand)) {
			return ItemStack.EMPTY;
		}
		return !stand.getMainHandItem().isEmpty() ? stand.getMainHandItem() : stand.getOffhandItem();
	}
}
