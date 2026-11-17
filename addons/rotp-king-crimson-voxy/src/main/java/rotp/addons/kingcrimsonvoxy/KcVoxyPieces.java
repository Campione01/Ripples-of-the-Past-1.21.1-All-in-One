package rotp.addons.kingcrimsonvoxy;

import java.nio.IntBuffer;

import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;

/**
 * Loose pieces of the far ground: small squares of Voxy's own terrain that break away from their plate a moment after it starts
 * to move, and float up, turning, until a moment after their plate has gone (the owner, 2026-11-11: a little more of the
 * weightless blocks the anime shows after the ground cracks). Only as far as a piece can be seen: at 240 rendered rows over 70
 * degrees (720p, upscaled three times) one block is a pixel at about 196 blocks, and a pixel-sized speck is lost among the
 * plates: a piece is a small cluster, four blocks wide to 320 blocks and eight to 512 (two to three pixels), none farther.
 *
 * <p>Each piece lies inside one 32-block square of the world, at most four inside one 128-block square: a Voxy section of LOD
 * level 2 or finer then holds four at most, and draws each as one more instance cut to the piece's cell (KcVoxyShaderPatch).
 * The GPU reads them per 128-block square: the first piece's index and the count, then four cells of 16 bits (x and z in the
 * square, 7 bits each, and the side as 2 << code), then one word per piece with its plate.
 */
final class KcVoxyPieces {
    static final int SQUARE_SHIFT = 7;
    static final int TILE_SHIFT = 5;
    static final int REACH = 512;
    static final int SMALL_UNTIL = 320;
    /** Squares per side of the table around the origin's square: the reach and one square of slack each way. */
    static final int SQUARES = 2 * ((REACH >> SQUARE_SHIFT) + 2) + 1;
    static final int SQUARE_WORDS = 4;
    static final int MAX_PER_SQUARE = 4;
    static final int MAX = SQUARES * SQUARES * MAX_PER_SQUARE;
    static final int WORDS = SQUARES * SQUARES * SQUARE_WORDS + MAX;
    /** A piece's chance in each 32-block square of the ring: modest, the plates stay the main thing. */
    private static final float SHARE = 0.5F;
    /** Not at the join itself: the near shatter's own debris and plates are there. */
    private static final int FROM_JOIN = 16;
    private static final float DETACH_AFTER_START = 1.0F;
    private static final float DETACH_SPREAD = 5.0F;
    // The law of King Crimson's near debris (KingCrimsonShatterPlan.debrisRise: speed * age + gain * age^2, in blocks), quicker:
    // at its speeds a piece 200 blocks out would rise one pixel before its plate goes, lost in the plates' own band. With these
    // and the reach (distance / REACH_BLOCKS) a piece stands about a sixth of its distance above its plate (some 10 degrees, 60
    // pixels at 720p) after 25 ticks, at any distance: out of the band the lifted plates make, against the void. Measured in
    // game: at an eighth the pieces stayed inside that band.
    private static final float RISE_SPEED_MIN = 0.35F;
    private static final float RISE_SPEED_MAX = 0.55F;
    private static final float RISE_GAIN = 0.019F;
    static final float REACH_BLOCKS = 128.0F;
    private static final float SPIN_MOST_DEGREES = 4.0F;
    // A piece outlives its plate's tear a little, then goes in a few ticks of its own (its plate's tear would take it while it
    // rose), always by the end of the erase.
    private static final float END_AFTER_RETIRE = 1.0F;
    private static final float END_SPREAD = 4.0F;
    private static final float FADE_TICKS = 4.0F;

    final int count;
    final int[] parent;
    /** Cell corner relative to the erase's origin, and its side, in blocks. */
    final int[] cellX;
    final int[] cellZ;
    final int[] side;
    final float[] detachTick;
    /** On its plate's clock: the piece has gone by then. */
    final float[] endTick;
    final float[] riseSpeed;
    /** Radians per tick, either way. */
    final float[] spin;
    final float[] distance;

    private KcVoxyPieces(int capacity) {
        parent = new int[capacity];
        cellX = new int[capacity];
        cellZ = new int[capacity];
        side = new int[capacity];
        detachTick = new float[capacity];
        endTick = new float[capacity];
        riseSpeed = new float[capacity];
        spin = new float[capacity];
        distance = new float[capacity];
        count = capacity;
    }

    /** The pieces of one erase, and their table written at {@code firstWord} of {@code words}. */
    static KcVoxyPieces place(KingCrimsonShatterPlan plan, int originX, int originZ, long seed, int nearRange,
            Near near, IntBuffer words, int firstWord) {
        int[] squareFirst = new int[SQUARES * SQUARES];
        int[] squareCount = new int[SQUARES * SQUARES];
        int[] squareCells = new int[SQUARES * SQUARES * MAX_PER_SQUARE];
        int[] found = new int[MAX * 4];
        float[] foundDistance = new float[MAX];
        int n = 0;
        int originSquareX = originX >> SQUARE_SHIFT;
        int originSquareZ = originZ >> SQUARE_SHIFT;
        int half = SQUARES >> 1;
        int from = nearRange + FROM_JOIN;
        for (int sz = 0; sz < SQUARES; sz++) {
            for (int sx = 0; sx < SQUARES; sx++) {
                int square = sz * SQUARES + sx;
                squareFirst[square] = n;
                int worldSquareX = (originSquareX + sx - half) << SQUARE_SHIFT;
                int worldSquareZ = (originSquareZ + sz - half) << SQUARE_SHIFT;
                for (int tile = 0; tile < 16 && squareCount[square] < MAX_PER_SQUARE; tile++) {
                    int tileX = worldSquareX + ((tile & 3) << TILE_SHIFT);
                    int tileZ = worldSquareZ + ((tile >> 2) << TILE_SHIFT);
                    long h = mix(seed ^ ((long) tileX * 0x9E3779B97F4A7C15L) ^ ((long) tileZ * 0xC2B2AE3D27D4EB4FL));
                    if (unit(h) >= SHARE) {
                        continue;
                    }
                    double centreX = tileX + 16 - originX;
                    double centreZ = tileZ + 16 - originZ;
                    double d = Math.sqrt(centreX * centreX + centreZ * centreZ);
                    if (d < from || d > REACH) {
                        continue;
                    }
                    int size = d < SMALL_UNTIL ? 4 : 8;
                    int room = (32 - size) >> 1;
                    int x = tileX + (int) (unit(mix(h + 1)) * (room + 1)) * 2;
                    int z = tileZ + (int) (unit(mix(h + 2)) * (room + 1)) * 2;
                    int plate = plan.plateAt(x, z);
                    if (plate < 0 || plan.plateAt(x + size - 1, z) != plate || plan.plateAt(x, z + size - 1) != plate
                            || plan.plateAt(x + size - 1, z + size - 1) != plate || owned(near, x, z, size)) {
                        continue;
                    }
                    int at = n * 4;
                    found[at] = plate;
                    found[at + 1] = x - originX;
                    found[at + 2] = z - originZ;
                    found[at + 3] = size;
                    foundDistance[n] = (float) d;
                    int local = (x - worldSquareX) | (z - worldSquareZ) << 7 | (size == 4 ? 1 : 2) << 14;
                    squareCells[square * MAX_PER_SQUARE + squareCount[square]] = local;
                    squareCount[square]++;
                    n++;
                }
            }
        }
        KcVoxyPieces pieces = new KcVoxyPieces(n);
        for (int i = 0; i < n; i++) {
            int plate = found[i * 4];
            long h = mix(seed ^ 0x51ED27A3B1E3F4D5L ^ (long) i * 0x632BE59BD9B4E019L);
            pieces.parent[i] = plate;
            pieces.cellX[i] = found[i * 4 + 1];
            pieces.cellZ[i] = found[i * 4 + 2];
            pieces.side[i] = found[i * 4 + 3];
            pieces.distance[i] = foundDistance[i];
            pieces.detachTick[i] = plan.startTick(plate) + DETACH_AFTER_START + unit(h) * DETACH_SPREAD;
            pieces.riseSpeed[i] = RISE_SPEED_MIN + unit(mix(h + 1)) * (RISE_SPEED_MAX - RISE_SPEED_MIN);
            pieces.endTick[i] = Math.min(KingCrimsonShatterPlan.END_TICK - 0.5F,
                    plan.retireTick(plate) + END_AFTER_RETIRE + unit(mix(h + 4)) * END_SPREAD);
            float turn = (0.25F + 0.75F * unit(mix(h + 2))) * (float) Math.toRadians(SPIN_MOST_DEGREES);
            pieces.spin[i] = unit(mix(h + 3)) < 0.5F ? -turn : turn;
        }
        for (int square = 0; square < SQUARES * SQUARES; square++) {
            int at = firstWord + square * SQUARE_WORDS;
            int[] cells = new int[MAX_PER_SQUARE];
            System.arraycopy(squareCells, square * MAX_PER_SQUARE, cells, 0, MAX_PER_SQUARE);
            words.put(at, squareFirst[square] | squareCount[square] << 16);
            words.put(at + 1, cells[0] | cells[1] << 16);
            words.put(at + 2, cells[2] | cells[3] << 16);
            words.put(at + 3, 0);
        }
        int parents = firstWord + SQUARES * SQUARES * SQUARE_WORDS;
        for (int i = 0; i < n; i++) {
            words.put(parents + i, pieces.parent[i]);
        }
        return pieces;
    }

    private static boolean owned(Near near, int x, int z, int size) {
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                if (near.owns(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the near mesh owns a column of the world (KcVoxyPlan reads its own bits of the near footprint). */
    @FunctionalInterface
    interface Near {
        boolean owns(int x, int z);
    }

    /** How far a piece has risen at a tick of its plate's clock, before the reach that far plates get. */
    float rise(int piece, float tick) {
        float age = Math.max(0.0F, tick - detachTick[piece]);
        return riseSpeed[piece] * age + RISE_GAIN * age * age;
    }

    /** The share of the piece torn away (KcVoxyShaderPatch's tear): none until FADE_TICKS before its end, all at its end. */
    float tear(int piece, float tick) {
        float t = Math.max(0.0F, Math.min(1.0F, (tick - (endTick[piece] - FADE_TICKS)) / FADE_TICKS));
        return t * t * (3.0F - 2.0F * t);
    }

    float turn(int piece, float tick) {
        return spin[piece] * Math.max(0.0F, tick - detachTick[piece]);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long h) {
        return (h >>> 40) / (float) (1L << 24);
    }
}
