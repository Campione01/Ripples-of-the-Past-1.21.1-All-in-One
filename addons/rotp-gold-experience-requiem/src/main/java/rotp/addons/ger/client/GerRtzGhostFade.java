package rotp.addons.ger.client;

/**
 * How the rewind's ghost copies of one entity look and leave (owner,
 * 2026-11-08: "保留，改成金色並淡得更快"): gold, the oldest copies going
 * first, and the whole trail gone two seconds after the rewind, however long
 * its presentation lasts. Plain numbers, with nothing of the client in them.
 */
public final class GerRtzGhostFade {
    /** The rewind's gold, the colour of its storm. */
    public static final int GOLD = 0xFFE650;
    public static final int MAX_COPIES = 12;
    /** When the newest copy is gone. Before, every copy stayed for the whole presentation. */
    public static final int TRAIL_CLEAR_TICKS = 40;
    public static final float COPY_FADE_TICKS = 6.0F;
    public static final float NEWEST_ALPHA = 0.42F;
    public static final float ALPHA_RANGE = 0.28F;

    private GerRtzGhostFade() {}

    /** The ticks a copy is drawn for: copy 0 is the newest, the last one the oldest and the first to go. */
    public static float lifeTicks(
            int copy, int copies, int presentationTicks) {
        if (copies <= 0 || copy < 0 || copy >= copies) {
            return 0.0F;
        }
        int clear = Math.min(TRAIL_CLEAR_TICKS, presentationTicks);
        return clear * (copies - copy) / (float) copies;
    }

    public static float alpha(
            int copy,
            int copies,
            float elapsedTicks,
            int presentationTicks) {
        float left =
                lifeTicks(copy, copies, presentationTicks) - elapsedTicks;
        if (left <= 0.0F) {
            return 0.0F;
        }
        float base = NEWEST_ALPHA - copy * ALPHA_RANGE / MAX_COPIES;
        return base * Math.min(left / COPY_FADE_TICKS, 1.0F);
    }

    public static float red() {
        return (GOLD >> 16 & 0xFF) / 255.0F;
    }

    public static float green() {
        return (GOLD >> 8 & 0xFF) / 255.0F;
    }

    public static float blue() {
        return (GOLD & 0xFF) / 255.0F;
    }
}
