package rotp.addons.kingcrimson.state;

import rotp.core.api.time.TimeEraseWitnesses;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Players a Time Erase does not fully take, the owner's rule for Gold Experience Requiem's holders ("黃鎮持有者能看到時刪
 * 狀態下的玩家...但黃鎮玩家真身在時刪結束後不會被強制移動到假身"). King Crimson asks; who answers is another add-on's business, through
 * the core's witness rules (rotp.core.api.time.TimeEraseWitnesses, on both sides). With no rule both answers are no.
 */
public final class KingCrimsonEraseWitnesses {
    private KingCrimsonEraseWitnesses() {}

    /** Whether this player sees an eraser during his erase, drawn as he is at his real place. */
    public static boolean seesEraser(Player viewer, LivingEntity eraser) {
        return TimeEraseWitnesses.hasWitnesses() && TimeEraseWitnesses.seesEraser(viewer, eraser);
    }

    /** Whether this player is never put on his image when the eraser's erase ends. */
    public static boolean keepsPlace(Player player, LivingEntity eraser) {
        return TimeEraseWitnesses.hasWitnesses() && TimeEraseWitnesses.keepsPlace(player, eraser);
    }
}
