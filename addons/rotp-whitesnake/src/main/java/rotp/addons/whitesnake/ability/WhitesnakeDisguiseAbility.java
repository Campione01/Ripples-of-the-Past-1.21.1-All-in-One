package rotp.addons.whitesnake.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disguise.WhitesnakeDisguiseState;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
import rotp.addons.whitesnake.network.OpenDisguiseSelectionPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WhitesnakeDisguiseAbility
        extends NoPoseStandEntityAbility {
    private static final String ABILITY_NAME = "disguise";

    public WhitesnakeDisguiseAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DisguiseAction::new);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public boolean canContinueAction(EntityActionInstance action) {
        return WhitesnakeAddon.isActiveWhitesnakeUser(
                action.getPowerUser(), ABILITY_NAME);
    }

    // R772.1 OQ-C04: the disguise goes on with the Stand recalled (the owner's 收回仍持續), so the HUD lights this slot
    @Override
    public boolean cl_runsWithStandRecalled(LivingEntity user) {
        return WhitesnakeDisguiseState.of(user).isDisguised();
    }

    @Override
    public Component getName(Power<?> context) {
        return isDisguised(context)
                ? abilityName(context, "_disable")
                : super.getName(context);
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return isDisguised(context)
                ? "disguise_disable"
                : super.getSpriteName(context);
    }

    private static boolean isDisguised(Power<?> context) {
        return context != null
                && WhitesnakeDisguiseState.of(context.getUser())
                        .isDisguised();
    }

    public static final class DisguiseAction
            extends EntityActionInstance {
        public DisguiseAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            LivingEntity user = getPowerUser();
            if (!(getPerformer()
                            instanceof WhitesnakeEntity whitesnake)
                    || user == null) {
                forceStop();
                return;
            }

            if (level().isClientSide()) {
                return;
            }

            if (!WhitesnakeAddon.isActiveWhitesnakeUser(
                    user, ABILITY_NAME)) {
                forceStop();
                return;
            }
            if (whitesnake.isDisguised()) {
                whitesnake.clearDisguiseState();
            }
            else {
                WhitesnakeEntity.SelectionSession session =
                        whitesnake.beginDisguiseSelection(user);
                if (session != null
                        && user instanceof ServerPlayer player) {
                    PacketDistributor.sendToPlayer(
                            player,
                            new OpenDisguiseSelectionPayload(
                                    session.id(),
                                    session.expiryGameTick()));
                }
            }
        }
    }
}
