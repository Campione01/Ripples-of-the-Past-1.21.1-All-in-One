package rotp.addons.kingcrimson.state;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.world.entity.LivingEntity;

/**
 * The stamina of a Time Erase's start, by the owner's rule of 2026-11-11: "時刪的耐力消耗與返還機制在兩種模式下都參照時間暫停做提前結束
 * 的能量返還獎勵". The core's time stop gives back, when it is resumed early, a share of what its start really took
 * (TimeStopState.getUnusedStartCostRefund): (charged - shortest) x unused ticks / total ticks, where shortest is the
 * price of its shortest stop. An erase does the same with its own start, in the mode with cooldowns and in the mode
 * without, for a player and for a creature.
 * <p>
 * A time stop is priced by its length and an erase's start is one price for its whole length, so the price of the
 * shortest erase is taken as that length's share of it: charged x shortest ticks / total ticks, with the core's own
 * shortest length (TimeStopLearning.MIN_RELEASE_TIME_STOP_TICKS, one tick). At Resolve level 4 and the default
 * settings (a start of 250 for 300 ticks) an erase ended after 20 ticks gives back 250 x 299/300 x 280/300 = 232.56.
 */
public final class KingCrimsonEraseStamina {
    private KingCrimsonEraseStamina() {}

    /**
     * Takes the start of an erase from the bar. As 1.16 did, a bar that cannot pay is emptied and the erase begins all
     * the same.
     *
     * @return what the start really took: 0 with infinite stamina, and 0 for a start the bar could not pay (the core's
     *         time stop refunds nothing of such a start either)
     */
    public static float payStart(@Nullable Ability ability, @Nullable StandPower power, float cost) {
        if (power == null) {
            return 0.0F;
        }
        float before = power.getStamina();
        boolean paid = StandAbilityStamina.consume(ability, power, cost, false);
        return paid ? Math.max(before - power.getStamina(), 0.0F) : 0.0F;
    }

    /**
     * With each tick's upkeep the core's time stop also charges its user's own regeneration, so that he regenerates
     * nothing during it; an erase does the same (owner 2026-11-11: "時停和時刪在正常cd模式下邏輯趨同 ... 以及對應的耐力獎懲").
     * In the mode with no cooldowns he keeps the core's share of it (StandAbilityFatigue.regenKeptDuringUse, a
     * fifth). While his Stand acts the core has cut his regeneration to just that already, in both modes, and
     * nothing more is taken.
     */
    static void takeRegenerationBack(StandPower power) {
        StandEntity stand = power.isSummoned() ? power.getSummonedStandEntity() : null;
        EntityActionInstance action = stand != null ? LivingComponentAction.getCurEntityAction(stand) : null;
        if (action != null && !action.canStaminaRegen(power, stand)) {
            return;
        }
        power.consumeStamina(power.getStaminaTickGain() * (1.0F - StandAbilityFatigue.regenKeptDuringUse(power)), true);
    }

    /** What an erase that ends now gives back of its start; 0 for one that ran its whole length. */
    public static float unusedStart(float charged, int totalTicks, int elapsedTicks) {
        if (!(charged > 0.0F) || !Float.isFinite(charged) || totalTicks <= 0) {
            return 0.0F;
        }
        float shortest = charged * Math.min(TimeStopLearning.MIN_RELEASE_TIME_STOP_TICKS, totalTicks) / totalTicks;
        float refundable = charged - shortest;
        int elapsed = Math.max(0, Math.min(elapsedTicks, totalTicks));
        if (!(refundable > 0.0F) || elapsed >= totalTicks) {
            return 0.0F;
        }
        return refundable * (totalTicks - elapsed) / totalTicks;
    }

    /** Puts the unused share of an erase's start back on the bar of its user. */
    static void refundUnused(LivingEntity user, float charged, int totalTicks, int elapsedTicks) {
        StandPower power = StandPower.get(user);
        float refund = unusedStart(charged, totalTicks, elapsedTicks);
        if (power != null && power.hasPower() && !power.isStaminaInfinite() && refund > 0.0F) {
            power.setStamina(power.getStamina() + refund);
        }
    }
}
