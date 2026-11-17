package rotp.core.subsystems.entity_useitem;

import javax.annotation.Nullable;

import rotp.core.client.input.AbilityInputState;
import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;

/**
 * When a Stand takes its user's right click for the item in its hands or the block in front of it. A humanoid Stand lists this
 * as a skill it starts with (MovesetBuilder.addWhatItsMovesBring); a Stand whose moveset does not list the skill keeps the
 * click as well, so an add-on's Stand loses nothing. No client classes here, so server gametests can run it.
 */
public final class StandItemUse {
	public static final String SKILL = "item_use";

	private StandItemUse() {}

	public static boolean hasSkill(@Nullable StandPower power) {
		PowerData data = power != null ? power.getCurTypeData() : null;
		return data != null && skillAllows(data.getAllSkills().containsKey(SKILL), data.isSkillUnlocked(SKILL));
	}

	public static boolean skillAllows(boolean listedInMoveset, boolean unlocked) {
		return !listedInMoveset || unlocked;
	}

	/** Another move of the Stand keeps the button: only the item actions themselves let the next click through. */
	public static boolean actionLeavesTheClick(@Nullable EntityActionInstance action) {
		return action == null
				|| action.ability == ModSpecialActions.RMB_CLICK_ITEM.get()
				|| action.ability == ModSpecialActions.RMB_USING_ITEM.get();
	}

	/** A Stand that holds an entity keeps the button for its throw, bare RMB by default, whatever is in its hands. */
	public static boolean takesRightClick(@Nullable StandPower power, @Nullable StandEntity stand) {
		return stand != null && hasSkill(power) && actionLeavesTheClick(stand.getCurStandAction())
				&& LivingComponentGrab.getEntityGrabbedBy(stand) == null;
	}

	/**
	 * The client input state of a move bound to the same button while the Stand's click goes to its item: gone from the HUD and
	 * from the button, unless the move wants the item (WITH_ITEM_HELD).
	 */
	public static int inputStateBesideTheItem(int inputState) {
		return AbilityInputState._getBit(inputState, AbilityInputState.WITH_ITEM_HELD)
				? inputState
				: inputState & ~(1 << AbilityInputState.IS_ACTIVE | 1 << AbilityInputState.VISIBLE_WHEN_INACTIVE);
	}
}
