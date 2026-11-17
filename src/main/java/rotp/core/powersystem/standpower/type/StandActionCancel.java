package rotp.core.powersystem.standpower.type;

import javax.annotation.Nullable;

import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.init.ModDataAttachmentTypes;

/** What the Stand cancel key may end: an ability its user pressed, before it has done anything. */
public final class StandActionCancel {
	private StandActionCancel() {}

	public static boolean isCancellableWindup(@Nullable EntityActionInstance action) {
		return action != null && !action.isOver()
				&& (action.getPhase() == ActionPhase.BUTTON_CHARGE || action.getPhase() == ActionPhase.WINDUP)
				&& action.ability instanceof EntityActionAbility ability && ability.abilityId.powerClass() == PowerClass.STAND;
	}

	/**
	 * Ends the slot's action if {@link #isCancellableWindup}, as any cleared action ends (its onActionCleared runs), with
	 * the keys that held it and a press waiting behind it, so neither starts it again.
	 */
	public static boolean cancelWindup(@Nullable LivingComponentAction slot, StandPower standPower) {
		if (slot == null || !isCancellableWindup(slot.getAction())) {
			return false;
		}
		slot.setAction(null, SyncType.TRACKING_AND_SELF);
		standPower.interruptHeldInputs();
		standPower.getUser().getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
				.ifPresent(input -> input.inputBuffer(PowerClass.STAND).clear());
		return true;
	}
}
