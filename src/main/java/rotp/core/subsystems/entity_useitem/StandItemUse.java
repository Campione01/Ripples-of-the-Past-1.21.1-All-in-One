package rotp.core.subsystems.entity_useitem;

import javax.annotation.Nullable;

import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * When a Stand takes its user's right click for the item in its hands or the block in front of it. A humanoid Stand lists this
 * as a skill it starts with (MovesetBuilder.addHumanoidStandSkills); a Stand whose moveset does not list the skill keeps the
 * click as well, so an add-on's Stand loses nothing. No client classes here, so server gametests can run it.
 */
public final class StandItemUse {
	public static final String SKILL = "item_use";

	private StandItemUse() {}

	public static boolean hasSkill(@Nullable StandPower power) {
		PowerData data = power != null ? power.getCurTypeData() : null;
		return data != null && (!data.getAllSkills().containsKey(SKILL) || data.isSkillUnlocked(SKILL));
	}

	/** Another move of the Stand keeps the button: only the item actions themselves let the next click through. */
	public static boolean actionLeavesTheClick(@Nullable EntityActionInstance action) {
		return action == null
				|| action.ability == ModSpecialActions.RMB_CLICK_ITEM.get()
				|| action.ability == ModSpecialActions.RMB_USING_ITEM.get();
	}

	public static boolean takesRightClick(@Nullable StandPower power, @Nullable StandEntity stand) {
		return stand != null && hasSkill(power) && actionLeavesTheClick(stand.getCurStandAction());
	}
}
