package rotp.addons.kingcrimson.state;

import javax.annotation.Nullable;

import rotp.core.api.time.LevelTimeRates;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;

/**
 * What the end of a Time Erase does to the world it erased, by any route and in one tick, in the owner's order
 * (2026-11-07): every other player is put on the image the eraser saw of him; the day jumps ahead by the erased time
 * ("表現上應該是遊戲時間往後一下子跳了一截與時刪對應的時間"); the creatures around lose their thread for a moment; then what the
 * eraser threw or struck from inside the erase lands, on the places he aimed at. The eraser is a player, or a creature
 * that carries King Crimson (KingCrimsonCreatureErase), which throws no blood and is not dazed by its own erase.
 */
final class KingCrimsonTimeEraseEnd {
    private KingCrimsonTimeEraseEnd() {}

    /**
     * @param elapsedWorldTicks how much of the erase passed, in the world's time
     * @param thrownOut whether another ability threw the eraser out of the erase (Gold Experience Requiem within four
     *        chunks): nothing of his own lands then
     */
    static void run(LivingEntity eraser, @Nullable KingCrimsonTimeEraseShiftService.Session shift,
            int elapsedWorldTicks, boolean thrownOut) {
        // 1. The eraser's last sight is the truth.
        if (shift != null) {
            shift.finish(eraser);
        }
        // 2.
        if (eraser.level() instanceof ServerLevel erased) {
            jumpDayTime(erased, elapsedWorldTicks);
        }
        // 3.
        KingCrimsonAmnesia.strike(eraser, elapsedWorldTicks);
        // 4. The blood thrown from inside the erase lands, unless he was thrown out. A punch begun in the erase
        // strikes after this method returns: TimeEraseAttackPhaseMixin ends the erase at the head of the tick that
        // judges the hit.
        if (eraser instanceof ServerPlayer player) {
            KingCrimsonBloodThrowService.onEraseEnd(player, thrownOut);
        }
    }

    /**
     * Only the day's time, never the game time that cooldowns and schedules rest on; not with the daylight rule off,
     * and not while Made in Heaven accelerates the world the eraser is in (rule G3). The day's clock is the server's,
     * as for the time command: a dimension that keeps none of its own shows the Overworld's.
     */
    static void jumpDayTime(ServerLevel erased, int elapsedWorldTicks) {
        if (elapsedWorldTicks <= 0
                || !erased.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                || LevelTimeRates.rate(LevelTimeRates.Channel.TIME, erased) > 1.0F) {
            return;
        }
        for (ServerLevel level : erased.getServer().getAllLevels()) {
            level.setDayTime(level.getDayTime() + elapsedWorldTicks);
        }
    }
}
