package rotp.addons.kingcrimson.shatter;

/**
 * The ground giving way under the erasing user's own camera: an envelope on the shatter's timeline and smooth noise on
 * it, both read at the same timeline tick as the plates, so the shake follows the breaking however long it is made.
 */
public final class KingCrimsonShatterShake {
    /** With the first crests: the first cut leaves at its launch and its crest follows by the wind's lag. */
    public static final float START_TICK =
            KingCrimsonShatterPlan.FIRST_LAUNCH_TICK + KingCrimsonShatterPlan.WIND_LAG_TICKS;
    /** Strongest while the cracks near the user open into trenches and race to the edge of the range. */
    public static final float PEAK_FROM_TICK = KingCrimsonShatterPlan.WAVE_FROM_TICK + 3.0F;
    public static final float PEAK_TO_TICK = KingCrimsonShatterPlan.HEAD_TICKS_TO_EDGE + 2.0F;
    /** Let go as the plates lift; gone long before the last crest and the last of the ground. */
    public static final float GONE_TICK = 30.0F;
    /** Degrees at the peak with vanilla's Screen Effects at full; a hurt tilt is 14 degrees of roll. */
    public static final float PITCH_DEGREES = 0.5F;
    public static final float YAW_DEGREES = 0.35F;
    public static final float ROLL_DEGREES = 0.7F;
    // A rumble of about 8 Hz over a slower sway of about 3 Hz, in timeline ticks.
    private static final float RUMBLE_TICKS = 2.5F;
    private static final float SWAY_TICKS = 7.0F;
    private static final float RUMBLE_SHARE = 0.55F;

    private KingCrimsonShatterShake() {}

    /** 0 outside the shake's window, 1 where the cracks open, eased at both ends. */
    public static float envelope(float tick) {
        if (!(tick > START_TICK) || tick >= GONE_TICK) {
            return 0.0F;
        }
        return smoothstep(START_TICK, PEAK_FROM_TICK, tick) * (1.0F - smoothstep(PEAK_TO_TICK, GONE_TICK, tick));
    }

    /** Pitch, yaw and roll in degrees at a timeline tick; strength is the Screen Effects scale. */
    public static void angles(float tick, float strength, float[] out) {
        float amount = envelope(tick) * Math.max(0.0F, Math.min(1.0F, strength));
        out[0] = amount == 0.0F ? 0.0F : PITCH_DEGREES * amount * noise(0, tick);
        out[1] = amount == 0.0F ? 0.0F : YAW_DEGREES * amount * noise(1, tick);
        out[2] = amount == 0.0F ? 0.0F : ROLL_DEGREES * amount * noise(2, tick);
    }

    /** Smooth noise in -1..1, its own for each channel. */
    public static float noise(int channel, float tick) {
        return RUMBLE_SHARE * valueNoise(channel * 2, tick / RUMBLE_TICKS)
                + (1.0F - RUMBLE_SHARE) * valueNoise(channel * 2 + 1, tick / SWAY_TICKS);
    }

    private static float valueNoise(int stream, float at) {
        float floor = (float) Math.floor(at);
        int knot = (int) floor;
        float t = at - floor;
        float ease = t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
        float a = knotValue(stream, knot);
        return a + (knotValue(stream, knot + 1) - a) * ease;
    }

    private static float knotValue(int stream, int knot) {
        long h = (knot * 0x9E3779B97F4A7C15L) ^ (stream * 0xC2B2AE3D27D4EB4FL) ^ 0x2545F4914F6CDD1DL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return ((h >>> 40) / (float) (1L << 24)) * 2.0F - 1.0F;
    }

    private static float smoothstep(float from, float to, float value) {
        float t = Math.max(0.0F, Math.min(1.0F, (value - from) / (to - from)));
        return t * t * (3.0F - 2.0F * t);
    }
}
