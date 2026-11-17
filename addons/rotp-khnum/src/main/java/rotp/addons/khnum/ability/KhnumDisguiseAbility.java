package rotp.addons.khnum.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.khnum.disguise.DisguiseClientBridge;
import rotp.addons.khnum.disguise.DisguiseMode;
import rotp.addons.khnum.disguise.KhnumDisguiseState;
import rotp.addons.khnum.entity.KhnumControllerEntity;
import net.minecraft.world.entity.LivingEntity;

public final class KhnumDisguiseAbility
        extends NoPoseStandEntityAbility {
    private final DisguiseMode mode;
    private final String disableName;

    public KhnumDisguiseAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            DisguiseMode mode,
            String disableName,
            int resolveLevel) {
        super(
                abilityType,
                abilityId,
                type -> new DisguiseAction(type, mode));
        this.mode = mode;
        this.disableName = disableName;
        resolveLevelToUnlock(resolveLevel);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        if (KhnumDisguiseState.of(user).mode() == mode) {
            Ability disable =
                    abilities.getContextVariation(disableName);
            return disable != null ? disable : this;
        }
        return this;
    }

    // the disguise goes on with the Stand recalled: the HUD lights the slot while the user wears this one
    @Override
    public boolean cl_runsWithStandRecalled(LivingEntity user) {
        return KhnumDisguiseState.of(user).mode() == mode;
    }

    private static final class DisguiseAction
            extends EntityActionInstance {
        private final DisguiseMode mode;

        private DisguiseAction(
                EntityActionType ability, DisguiseMode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void actionPerformStart() {
            if (!(getPerformer()
                    instanceof KhnumControllerEntity controller)) {
                forceStop();
                return;
            }
            if (level().isClientSide()) {
                DisguiseClientBridge.open(mode);
            }
            else {
                controller.beginSelection(mode);
            }
        }
    }
}
