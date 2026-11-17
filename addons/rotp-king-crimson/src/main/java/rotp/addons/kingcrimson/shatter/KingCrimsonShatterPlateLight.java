package rotp.addons.kingcrimson.shatter;

/**
 * One dim even light on the plates of a pack that declares the void. Such a pack lights a plate by the entity id alone, and its
 * emissive law is level * BLOCKLIGHT_BRIGHTNESS * Radiance(albedo) * albedo: the albedo counts twice, so at one level for every
 * block a birch log burns white and catches bloom beside dim grass. Dividing the level by the block's radiance leaves the albedo
 * counted once, which is what terrain under one light looks like.
 */
public final class KingCrimsonShatterPlateLight {
    /** 10097 to 10099 are markers a pack treats as a void, not as a light: a plate's id stays under them. */
    public static final int FIRST_LEVEL = 1;
    public static final int LAST_LEVEL = 96;
    /**
     * A dark texture needs more than LAST_LEVEL to stand in the even light: a dark wood log of R 0.017 needs about 235 (OQ-K03). A
     * pack that declares {@link #WIDE_MARKER_PATH} reads the levels above LAST_LEVEL from ids {@link #WIDE_ID_BASE} + level; a plate
     * keeps them as codes LAST_LEVEL + 1 to LAST_CODE, each WIDE_STEP times the last, so that one byte holds them.
     */
    public static final int LAST_CODE = 127;
    public static final float WIDE_STEP = 1.05F;
    public static final int WIDE_ID_BASE = 10200;
    public static final String WIDE_MARKER_PATH = "time_erase_plate_light";
    public static final int WIDE_MARKER_ID = WIDE_ID_BASE;
    private static final float DAYLIGHT_GAMMA = 2.2F;
    // Grass tops, the bulk of what plates show (0.099 by the game's textures), keep the level 40 of the look the owner accepted.
    private static final float EVEN_LIGHT = 0.04F;
    private static final int BRIGHTEST_EMISSION = 15;
    private static final float[] LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            LINEAR[i] = (float) Math.pow(i / 255.0D, DAYLIGHT_GAMMA);
        }
    }

    private KingCrimsonShatterPlateLight() {}

    public static float linear(int channel) {
        return LINEAR[channel & 255];
    }

    /** The pack's Radiance of a texture or vertex colour. */
    public static float radiance(int argb) {
        return (linear(argb >> 16) + linear(argb >> 8) + linear(argb)) / 3.0F;
    }

    /**
     * @param radiance what the block's pixels average to, weighted by themselves as the law weighs them, tint included
     * @param lightEmission the block's own light: only a block that really emits may glow
     */
    public static int level(float radiance, int lightEmission) {
        float level = EVEN_LIGHT / Math.max(radiance, 1.0E-3F);
        if (lightEmission > 0) {
            level = Math.max(level, LAST_LEVEL * 0.01F * Math.min(lightEmission, BRIGHTEST_EMISSION) / BRIGHTEST_EMISSION);
        }
        return Math.max(FIRST_LEVEL, Math.min(LAST_LEVEL, Math.round(level * 100.0F)));
    }

    /**
     * The level of {@link #level} without its cap, as a code of one byte: up to LAST_LEVEL the level itself, above it the wide steps.
     * A pack that cannot read the wide levels gets {@link #levelOfCode} capped at LAST_LEVEL, which is {@link #level}.
     */
    public static int code(float radiance, int lightEmission) {
        float wanted = EVEN_LIGHT / Math.max(radiance, 1.0E-3F) * 100.0F;
        int capped = level(radiance, lightEmission);
        if (capped < LAST_LEVEL || wanted <= LAST_LEVEL + 0.5F || lightEmission > 0) {
            return capped;
        }
        int steps = Math.round((float) (Math.log(wanted / LAST_LEVEL) / Math.log(WIDE_STEP)));
        return Math.max(LAST_LEVEL, Math.min(LAST_CODE, LAST_LEVEL + steps));
    }

    /** The level a code stands for: the code itself up to LAST_LEVEL, then LAST_LEVEL * WIDE_STEP^(code - LAST_LEVEL). */
    public static int levelOfCode(int code) {
        if (code <= LAST_LEVEL) return code;
        return Math.round(LAST_LEVEL * (float) Math.pow(WIDE_STEP, Math.min(code, LAST_CODE) - LAST_LEVEL));
    }

    /** The entity id that carries a code to the pack; level 0 is a quad nothing was asked of, as the far mesh's. */
    public static int plateId(int code, int noLevelId, boolean wide) {
        if (code <= 0) return noLevelId;
        if (code <= LAST_LEVEL) return 10000 + code;
        return wide ? WIDE_ID_BASE + levelOfCode(code) : 10000 + LAST_LEVEL;
    }

    /** What the pack gives a surface per unit of albedo at this level: equal across blocks means one even light. */
    public static float lightPerAlbedo(int level, float radiance) {
        return level * 0.01F * radiance;
    }
}
