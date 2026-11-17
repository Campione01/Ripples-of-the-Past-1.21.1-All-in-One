package rotp.addons.kingcrimsonvoxy;

/**
 * The far tail (the owner, 2026-11-11: near to far in the plan's order, and the last of it, out in the distance, a little quicker).
 * From FROM on a far plate's clock runs up to 1 / (1 - share) times as fast, so the far stage, from about tick 57 to the last
 * plate at about tick 81 in the plan, takes some 30% less time. Nothing changes at the join: the share grows from none at the
 * near plan's range to all of it RAMP_BLOCKS farther, and the gain comes in over RAMP_TICKS. A share of 0 is the plan's own
 * timing (debug runs, to film both).
 */
final class KcVoxyTail {
    static final float SHARE = 0.3F;
    static final float FROM = 56.0F;
    private static final float RAMP_TICKS = 6.0F;
    private static final float RAMP_BLOCKS = 128.0F;
    static volatile float share = SHARE;

    private KcVoxyTail() {}

    /** A far plate's clock at an erase tick. */
    static float tick(float tick, float distance, int nearRange) {
        float ramp = Math.max(0.0F, Math.min(1.0F, (distance - nearRange) / RAMP_BLOCKS));
        float own = share * ramp * ramp * (3.0F - 2.0F * ramp);
        if (own <= 0.0F || tick <= FROM) {
            return tick;
        }
        float age = tick - FROM;
        float extra = age < RAMP_TICKS ? age * age / (2.0F * RAMP_TICKS) : age - RAMP_TICKS * 0.5F;
        return tick + own / (1.0F - own) * extra;
    }
}
