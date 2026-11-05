package rotp.addons.highwaystar.ability;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarEntity;
import rotp.addons.highwaystar.network.HighwayStarPayloads;
import rotp.addons.highwaystar.smell.HighwayStarSmellHelper;
import rotp.addons.highwaystar.smell.SmellTargets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** 1.16 HighwayStarSmellAction: while held, the creatures around the user and the Stand show through walls. */
public final class HighwayStarSmellAbility extends StandEntityAbility {
    public static final float RADIUS = 20.0F;
    public static final float STAMINA_COST = 5.0F;

    public HighwayStarSmellAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, SmellAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(40, 0);
    }

    // 1.16 TargetRequirement NONE
    @Override
    public boolean takesPressedTarget(StandEntity standEntity, ActionTarget target) {
        return false;
    }

    public static final class SmellAction extends EntityActionInstance {
        private boolean started;

        public SmellAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            LivingEntity user = getPowerUser();
            if (level().isClientSide() || user == null || started) {
                return;
            }
            started = true;
            if (performer instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(stand, HighwayStarAddon.SMELL_VOICE, 1.0F, 1.0F);
            }
            // 1.16 startedHolding: the user's sniff, then every creature around the user and the Stand is remembered
            user.level().playSound(null, user.getX(), user.getY(), user.getZ(),
                    HighwayStarAddon.SMELL_VOICE.get(), user.getSoundSource(), 1.0F, 1.0F);
            if (user instanceof Player) {
                SmellTargets smelled = user.getData(HighwayStarAddon.SMELL_TARGETS);
                user.level().getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(RADIUS),
                        e -> e != user && HighwayStarSmellHelper.isSmellable(e))
                        .forEach(e -> smelled.addTarget(e.getUUID()));
                StandPower power = StandPower.get(user);
                if (power != null && power.getSummonedStandEntity() instanceof HighwayStarEntity stand && stand.isAlive()) {
                    user.level().getEntitiesOfClass(LivingEntity.class, stand.getBoundingBox().inflate(RADIUS),
                            e -> e != user && e != stand && HighwayStarSmellHelper.isSmellable(e))
                            .forEach(e -> smelled.addTarget(e.getUUID()));
                }
            }
            sendSmell(user, true);
            StandAbilityStamina.consume(ability, StandPower.get(user), STAMINA_COST, false);
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        @Override
        public void onActionCleared(@Nullable EntityActionInstance newAction) {
            super.onActionCleared(newAction);
            LivingEntity user = getPowerUser();
            if (!level().isClientSide() && user != null) {
                sendSmell(user, false);
            }
        }

        private static void sendSmell(LivingEntity caster, boolean enabled) {
            if (caster instanceof ServerPlayer player) {
                HighwayStarPayloads.sendSmell(player, enabled, enabled ? RADIUS : 0.0F);
            }
        }
    }
}
