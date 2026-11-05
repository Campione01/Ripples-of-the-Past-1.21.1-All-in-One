package rotp.addons.highwaystar.ability;

import java.util.Set;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** 1.16 HighwayStarActionBlocker and the checkSpecificConditions overrides of InitStands. */
public final class HighwayStarActionBlocker {
    /** The actions 1.16 refused in the split form (checkSplitOnly); the charged heavy punch is the heavy punch's. */
    private static final Set<String> SPLIT_ONLY = Set.of(
            HighwayStarAddon.PUNCH, HighwayStarAddon.BARRAGE, HighwayStarAddon.HEAVY,
            HighwayStarAddon.CHARGED_HEAVY, HighwayStarAddon.BLOCK, HighwayStarAddon.ARM_RETURN);

    private HighwayStarActionBlocker() {}

    @Nullable
    public static HighwayStarEntity summonedHighwayStar(@Nullable LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        return power != null && power.getSummonedStandEntity() instanceof HighwayStarEntity hs ? hs : null;
    }

    public static boolean isSplit(@Nullable LivingEntity user) {
        HighwayStarEntity hs = summonedHighwayStar(user);
        return hs != null && hs.isSplit();
    }

    public static boolean isArmMissing(@Nullable LivingEntity user) {
        HighwayStarEntity hs = summonedHighwayStar(user);
        return hs != null && hs.isArmMissing();
    }

    public static ConditionCheck splitCheck(LivingEntity user) {
        return isSplit(user)
                ? ConditionCheck.createNegative(Component.translatable("action.highwaystar.condition.need_human_form"))
                : ConditionCheck.POSITIVE;
    }

    public static ConditionCheck armMissingCheck(LivingEntity user) {
        return isArmMissing(user)
                ? ConditionCheck.createNegative(Component.translatable("action.highwaystar.condition.arm_missing"))
                : ConditionCheck.POSITIVE;
    }

    @Nullable
    public static ConditionCheck refuse(Ability ability, Power<?> context, LivingEntity user) {
        if (!(context instanceof StandPower power) || power.getPowerType() != HighwayStarAddon.HIGHWAY_STAR.get()) {
            return null;
        }
        String name = ability.name();
        ConditionCheck check = ConditionCheck.POSITIVE;
        if (SPLIT_ONLY.contains(name)) {
            check = splitCheck(user);
        }
        else if (HighwayStarAddon.FINISHER.equals(name)) {
            check = armMissingCheck(user);
            if (check.isPositive()) {
                check = splitCheck(user);
            }
        }
        else if (HighwayStarAddon.SEIZE.equals(name)) {
            check = splitCheck(user);
            if (check.isPositive()) {
                check = armMissingCheck(user);
            }
        }
        return check.isPositive() ? null : check;
    }

    /** 1.16 HighwayStarEntity.canBeManuallyControlled: no remote control in the split form. */
    public static boolean blocksManualControl(LivingEntity user, StandEntity stand) {
        return stand instanceof HighwayStarEntity hs && hs.isSplit();
    }
}
