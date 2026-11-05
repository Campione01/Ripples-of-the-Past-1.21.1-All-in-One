package rotp.addons.d4c.travel;

/**
 * Numbers and gates of the 1.16 parallel-world terrain copy
 * (1.16 am.java:385-438). Pure Java so the contract check can call it.
 */
public final class D4CParallelMirrorRules {
    /** Chunks copied on each side of the departure chunk (9x9). */
    public static final int CHUNK_RADIUS = 4;
    /** Blocks copied below the departure feet. */
    public static final int BELOW = 16;
    /** Blocks copied above the departure feet. */
    public static final int ABOVE = 48;

    private D4CParallelMirrorRules() {}

    /** Only overworld to parallel world crossings copy terrain. */
    public static boolean shouldMirror(
            boolean fromOverworld,
            boolean toParallel) {
        return fromOverworld && toParallel;
    }

    /**
     * Lowest copied Y: 16 below the feet as in 1.16, and down to the
     * parallel ground (groundTopY, the chunk's highest non-air block) so
     * a copy taken high up does not float. minY is inclusive.
     */
    public static int lowestY(int feetY, int groundTopY, int minY) {
        return Math.max(minY, Math.min(feetY - BELOW, groundTopY));
    }

    /**
     * Highest copied Y: 48 above the feet as in 1.16, and up to the
     * highest parallel block so a copy taken low down is not roofed over
     * by the flat ground. maxY is inclusive.
     */
    public static int highestY(int feetY, int groundTopY, int maxY) {
        return Math.min(maxY, Math.max(feetY + ABOVE, groundTopY));
    }

    /**
     * A changed block needs its own light check unless it is inside a
     * run of identical changes; these are the exceptions (emitters, shaped
     * occluders, and blocks that dim the light but stay see-through).
     */
    public static boolean alwaysRelight(
            int oldLightBlock,
            int oldEmission,
            boolean oldShaped,
            int newLightBlock,
            int newEmission,
            boolean newShaped) {
        return oldEmission > 0
                || newEmission > 0
                || oldShaped
                || newShaped
                || (newLightBlock < 15 && newLightBlock > oldLightBlock);
    }

    /** Portal blocks are not copied; they become air. */
    public static <S> S mirroredState(S overworld, boolean portal, S air) {
        return portal ? air : overworld;
    }

    /** Blocks already equal in the parallel world are skipped. */
    public static boolean needsWrite(Object target, Object current) {
        return target != null && !target.equals(current);
    }
}
