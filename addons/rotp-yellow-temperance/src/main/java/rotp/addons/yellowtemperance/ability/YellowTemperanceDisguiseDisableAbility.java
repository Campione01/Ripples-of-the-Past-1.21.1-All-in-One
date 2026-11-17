package rotp.addons.yellowtemperance.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.YellowTemperanceDisguiseState;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.world.entity.LivingEntity;

public final class YellowTemperanceDisguiseDisableAbility
        extends NoPoseStandEntityAbility {
    private final boolean standDisguise;

    public YellowTemperanceDisguiseDisableAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            int resolveLevel,
            boolean standDisguise) {
        super(
                abilityType,
                abilityId,
                type -> new DisableAction(type, standDisguise));
        this.standDisguise = standDisguise;
        resolveLevelToUnlock(resolveLevel);
    }

    // R772.1 OQ-C04: the slot this ends stays lit with the Stand recalled while its disguise goes on
    @Override
    public boolean cl_runsWithStandRecalled(LivingEntity user) {
        YellowTemperanceDisguiseState state = YellowTemperanceDisguiseState.of(user);
        if (standDisguise) {
            return state.hasStandDisguise();
        }
        DisguiseMode ends = YellowTemperanceAddon.DISGUISE_PLAYER_DISABLE_NAME.equals(abilityId.nameInMoveset())
                ? DisguiseMode.PLAYER
                : DisguiseMode.ENTITY;
        return state.mode() == ends;
    }

    private static final class DisableAction
            extends EntityActionInstance {
        private final boolean standDisguise;

        private DisableAction(
                EntityActionType ability, boolean standDisguise) {
            super(ability);
            this.standDisguise = standDisguise;
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof YellowTemperanceControllerEntity
                            controller) {
                if (standDisguise) {
                    controller.clearStandDisguise();
                }
                else {
                    controller.clearDisguise();
                }
            }
        }
    }
}
