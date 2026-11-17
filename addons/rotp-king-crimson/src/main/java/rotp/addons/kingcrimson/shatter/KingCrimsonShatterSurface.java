package rotp.addons.kingcrimson.shatter;

import java.util.Arrays;

/**
 * The surface the user can see, walked out from the feet: what the wind's crests and the cracks stand on. It is not the natural
 * ground of a column, which under a canopy or a roof lies hidden below it. Each column takes the standing place nearest to the one
 * next to it on the way in, so standing on leaves keeps to the leaves and walking under trees keeps to the ground.
 */
public final class KingCrimsonShatterSurface {
    /** Over this the next column is no step of the same surface. */
    private static final float STEP_BLOCKS = 2.5F;
    /** A trunk or a post is walked past; anything thicker than this along the way out is climbed. */
    private static final int OBSTACLE_COLUMNS = 3;

    public interface Columns {
        /** The heights a user could stand at in this column, any order; returns how many, 0 where nothing is known. */
        int standingPlaces(int dx, int dz, float[] into);
    }

    private KingCrimsonShatterSurface() {}

    /** Heights relative to the same zero as {@code feet}, indexed by {@link #index}; NaN where nothing is known. */
    public static float[] walk(int half, float feet, int mostPlaces, Columns columns) {
        int side = half * 2 + 1;
        float[] surface = new float[side * side];
        Arrays.fill(surface, Float.NaN);
        byte[] carried = new byte[side * side];
        float[] places = new float[mostPlaces];
        surface[index(half, 0, 0)] = nearest(places, columns.standingPlaces(0, 0, places), feet);
        if (Float.isNaN(surface[index(half, 0, 0)])) {
            surface[index(half, 0, 0)] = feet;
        }
        for (int ring = 1; ring <= half; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                int stride = Math.abs(dz) == ring ? 1 : ring * 2;
                for (int dx = -ring; dx <= ring; dx += stride) {
                    int at = index(half, dx, dz);
                    int from = index(half, inward(dx, ring), inward(dz, ring));
                    float previous = surface[from];
                    if (Float.isNaN(previous)) {
                        previous = feet;
                    }
                    int count = columns.standingPlaces(dx, dz, places);
                    if (count == 0) {
                        continue;
                    }
                    float next = nearest(places, count, previous);
                    if (Math.abs(next - previous) <= STEP_BLOCKS) {
                        surface[at] = next;
                        continue;
                    }
                    float below = highestBelow(places, count, previous);
                    if (!Float.isNaN(below)) {
                        // Past an edge: the canopy or the cliff ends here and the surface drops to what lies under it.
                        surface[at] = below;
                    } else if (carried[from] + 1 < OBSTACLE_COLUMNS) {
                        // Against a trunk: the columns behind it must not go on from its crown.
                        surface[at] = previous;
                        carried[at] = (byte) (carried[from] + 1);
                    } else {
                        surface[at] = next;
                    }
                }
            }
        }
        return surface;
    }

    public static int index(int half, int dx, int dz) {
        return dx + half + (dz + half) * (half * 2 + 1);
    }

    // One step in along the line to the feet.
    private static int inward(int offset, int ring) {
        return Math.round(offset * (ring - 1) / (float) ring);
    }

    private static float nearest(float[] places, int count, float to) {
        float best = Float.NaN;
        for (int i = 0; i < count; i++) {
            if (Float.isNaN(best) || Math.abs(places[i] - to) < Math.abs(best - to)) {
                best = places[i];
            }
        }
        return best;
    }

    private static float highestBelow(float[] places, int count, float to) {
        float best = Float.NaN;
        for (int i = 0; i < count; i++) {
            if (places[i] < to && (Float.isNaN(best) || places[i] > best)) {
                best = places[i];
            }
        }
        return best;
    }

    /**
     * Ground of a dimension whose blocks carry none of the vanilla tags: the Twilight Forest's stones, the Aether's, Deeper Darker's.
     * Anything solid and whole that is not grown or built; vanilla's own blocks stay with their tags, so that a planks roof is no ground.
     */
    public static boolean isModdedGround(boolean vanillaBlock, boolean solidFullBlock, boolean grownOrBuilt) {
        return !vanillaBlock && solidFullBlock && !grownOrBuilt;
    }
}
