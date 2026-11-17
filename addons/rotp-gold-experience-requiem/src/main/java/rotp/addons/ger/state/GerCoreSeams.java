package rotp.addons.ger.state;

import rotp.core.api.entity.MobAiPause;
import rotp.core.api.time.event.BitesTheDustDetonateEvent;
import rotp.core.api.time.event.TimeEraseStartEvent;

import rotp.addons.ger.ability.GerReturnToZeroMarkAbility;
import net.minecraft.world.entity.Mob;

/**
 * Where Requiem meets the core API that round R772.1 added for it (core
 * 15b2c710). Each method is the whole of one meeting.
 */
public final class GerCoreSeams {
    /** A full Time Erase is returned to zero within the rewind's own 8 chunks. */
    public static final int ERASE_ANSWER_CHUNKS =
            GerReturnToZeroMarkAbility.MARK_CHUNK_RADIUS;
    /**
     * King Crimson's Time Erase step is answered within 4 chunks, and an
     * eraser who comes that near in a running erase is thrown out of it
     * (owner, 2026-11-08: "這個技能的話改成四個區塊").
     */
    public static final int ERASE_NEAR_CHUNKS = 4;

    private GerCoreSeams() {}

    /**
     * The rewind stops the thinking of its user's enemies for a moment: their
     * AI only, never saved, and never shortening a pause that already runs.
     */
    public static void pauseAi(Mob mob, int ticks) {
        MobAiPause.pause(mob, ticks);
    }

    /**
     * Bites the Dust on a user whose Requiem is out vanishes as it would go
     * off: defused, so that Killer Queen takes the bomb away with no
     * explosion, no damage and no loop. Cancelling would only hold it.
     */
    public static void returnDetonationToZero(
            BitesTheDustDetonateEvent event) {
        event.defuse();
    }

    /**
     * How near a summoned Requiem returns the starting erase to zero: a full
     * erase within {@link #ERASE_ANSWER_CHUNKS}, King Crimson's step within
     * {@link #ERASE_NEAR_CHUNKS}.
     */
    public static int eraseAnswerChunks(TimeEraseStartEvent event) {
        return event.getKind() == TimeEraseStartEvent.Kind.STEP
                ? ERASE_NEAR_CHUNKS
                : ERASE_ANSWER_CHUNKS;
    }
}
