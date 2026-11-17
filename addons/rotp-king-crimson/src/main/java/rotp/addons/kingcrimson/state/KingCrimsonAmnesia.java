package rotp.addons.kingcrimson.state;

import rotp.core.api.entity.MobAiPause;

import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Owner 2026-11-07: "在時刪結束後，範圍內所有受影響的非玩家生物ai會短暫停頓1.5s，以還時刪過程失憶的表現", and on its length: "停止時間
 * 隨時刪時間變化，最大為1.5s，當時刪最大時長未升滿或提前結束時，對應的ai呆滯時間等比例降低". When an erase ends, by any route, every
 * creature around stops thinking for a share of the setting: the erase's elapsed world time over the longest erase
 * of the top Resolve level. The pause is the core's (MobAiPause): the AI only, counted in the creature's own ticks,
 * never saved; a later end starts it again, it does not add up.
 * <p>
 * A creature on a track of Epitaph's prediction is paused like any other here. Its track, which holds its future,
 * takes the pause off it (MobAiPause.remainingTicks, then end) and gives what was left back when it lets the creature
 * go (MobAiPause.pause).
 */
public final class KingCrimsonAmnesia {
    private KingCrimsonAmnesia() {}

    /** The pause, in ticks, after an erase of which this much passed in the world's time. */
    public static int ticksFor(int elapsedWorldTicks) {
        int cap = KingCrimsonConfig.timeEraseCapTicks();
        int full = KingCrimsonConfig.timeEraseAmnesiaTicks();
        if (cap <= 0 || full <= 0 || elapsedWorldTicks <= 0) {
            return 0;
        }
        return (int) Math.round((double) full * Math.min(elapsedWorldTicks, cap) / cap);
    }

    static void strike(LivingEntity eraser, int elapsedWorldTicks) {
        int ticks = ticksFor(elapsedWorldTicks);
        if (ticks <= 0) {
            return;
        }
        for (Mob mob : eraser.level().getEntitiesOfClass(
                Mob.class,
                eraser.getBoundingBox().inflate(KingCrimsonPhase5Spec.TIME_ERASE_AFTERIMAGE_RADIUS),
                mob -> mob.isAlive() && !KingCrimsonConfig.isAmnesiaExempt(mob.getType())
                        // Owner 2026-11-11: "釋放時刪的生物自身不吃時刪結束時的暈眩". The fake body of an erase is no creature.
                        && mob != eraser && !KingCrimsonCreatureErase.isPart(mob)
                        // A decoy stands for a player, whom no erase dazes.
                        && !(mob instanceof KingCrimsonTimeEraseDecoyEntity))) {
            // The core refuses the types of jojo_ripples:never_ai_paused.
            MobAiPause.restart(mob, ticks);
        }
    }
}
