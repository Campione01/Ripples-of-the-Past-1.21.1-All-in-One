package rotp.addons.sexpistols.content.sexpistols;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

// 0.1.13 SexPistolsForcedTargetAction: marks the entity or block looked at
// (up to 128 blocks) as a forced target, or unmarks it; sneaking clears all.
// resolveLevelToUnlock(0), autoSummonStand, cooldown(4).
public final class SexPistolsForcedTargetAbility extends Ability {
    public static final int COOLDOWN_TICKS = 4;

    public SexPistolsForcedTargetAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(0);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        if (!(context instanceof StandPower standPower)
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || context.getUser() == null) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public Component getName(Power<?> context) {
        LivingEntity owner =
                context != null ? context.getUser() : null;
        if (owner == null) {
            return super.getName(context);
        }
        if (owner.isShiftKeyDown()) {
            return Component.translatable(
                    getTranslationKey(".clear"));
        }
        int current = SexPistolsAttachments.coordinator(owner)
                .forcedTargets().size();
        return Component.translatable(
                getTranslationKey(""),
                current,
                SexPistolsCoordinator.maxForcedTargets(
                        context instanceof StandPower standPower
                                ? standPower
                                : null));
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return "sex_pistols_forced_target";
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity owner,
            FriendlyByteBuf extraClientInput) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        StandPower standPower = StandPower.get(owner);
        if (standPower == null
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()) {
            return;
        }
        if (!standPower.isSummoned()) {
            SexPistolsStands.SEX_PISTOLS.get()
                    .summon(owner, standPower);
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        if (owner.isShiftKeyDown()) {
            coordinator.clearForcedTargets(owner);
            setCooldownOnUse(standPower);
            return;
        }

        HitResult look = SexPistolsAim.forcedLookTarget(owner);
        SexPistolsCoordinator.ForcedToggle result =
                switch (look.getType()) {
                    case ENTITY -> coordinator.toggleForcedTarget(
                            owner,
                            ((EntityHitResult) look).getEntity(),
                            null,
                            SexPistolsCoordinator.maxForcedTargets(
                                    standPower));
                    case BLOCK -> coordinator.toggleForcedTarget(
                            owner,
                            null,
                            ((BlockHitResult) look).getBlockPos(),
                            SexPistolsCoordinator.maxForcedTargets(
                                    standPower));
                    case MISS -> SexPistolsCoordinator
                            .ForcedToggle.INVALID;
                };
        switch (result) {
            case FULL -> ConditionCheck.sendActionFailedMessage(
                    this,
                    ConditionCheck.createNegative(
                            "sex_pistols_forced_targets_full"),
                    owner);
            case INVALID -> ConditionCheck.sendActionFailedMessage(
                    this,
                    ConditionCheck.createNegative(
                            "sex_pistols_no_forced_target"),
                    owner);
            default -> setCooldownOnUse(standPower);
        }
    }
}
