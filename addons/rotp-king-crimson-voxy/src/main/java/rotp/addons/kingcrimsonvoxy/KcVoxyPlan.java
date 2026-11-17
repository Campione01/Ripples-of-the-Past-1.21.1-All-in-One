package rotp.addons.kingcrimsonvoxy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;

/**
 * The far ground of one erase: the plan, which plate owns each column, and the near mesh's columns. Built off the render
 * thread, straight into the GPU's staging memory (KcVoxyWords). The plan has the near plan's seed and yaw and the near plan's
 * range in its flight and tear law (KingCrimsonShatterPlan.createFar), so that plates either side of the join move alike.
 */
final class KcVoxyPlan {
    // Not 2048: the plan's retirement matching for that range is for the far mesh this add-on replaces, and its limits do not fit this law.
    static final int FAR_RANGE = 2049;
    static final int HALF_EXTENT = 2304;
    static final int BLOCKS_PER_TEXEL_SHIFT = 1;
    static final int TEXELS = (HALF_EXTENT * 2) >> BLOCKS_PER_TEXEL_SHIFT;
    static final int MAX_FOOTPRINT_SIDE = 2048;
    static final int MAX_FOOTPRINT_WORDS = (MAX_FOOTPRINT_SIDE * MAX_FOOTPRINT_SIDE >> 5) + 1;
    static final int OUTER_SECTORS = 32;
    static final int OUTER_RINGS = 3;
    private static final double[] OUTER_RADIUS = {3072.0D, 6144.0D, 12288.0D};
    private static final double RIM = 2000.0D;

    final KingCrimsonShatterPlan plan;
    final int plates;
    /** Words written into the staging memory: plate ids per texel, two to a word, 0xFFFF beyond the plan; then the near columns as bits. */
    final int words;
    final int firstFootprintWord;
    final int footprintMinX;
    final int footprintMinZ;
    final int footprintSide;
    final int nearRange;
    /** The loose pieces' table (KcVoxyPieces) starts at this word, after the near columns. */
    final int firstPieceWord;
    final KcVoxyPieces pieces;
    final int[] outerRimPlate;
    final float[] outerPivotX;
    final float[] outerPivotZ;
    final long planNanos;
    final long mapNanos;

    KcVoxyPlan(int originX, int originZ, long seed, float yaw, int nearRange, KingCrimsonShatterFarSurface.NearFootprint footprint,
            ByteBuffer out) {
        long start = System.nanoTime();
        plan = KingCrimsonShatterPlan.createFar(originX, originZ, seed, FAR_RANGE, yaw, nearRange);
        plates = plan.plateCount();
        long planned = System.nanoTime();
        planNanos = planned - start;

        footprintMinX = footprint.minX() - originX;
        footprintMinZ = footprint.minZ() - originZ;
        footprintSide = footprint.maxX() - footprint.minX();
        if (footprintSide > MAX_FOOTPRINT_SIDE) {
            throw new IllegalStateException("near footprint of " + footprintSide + " blocks is wider than the far map's room");
        }
        firstFootprintWord = (TEXELS * TEXELS + 1) >> 1;
        rasterize(out.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer());

        int side = footprintSide;
        int[] bits = new int[(side * side + 31 >> 5) + 1];
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                if (!Float.isNaN(footprint.columns().top(footprint.minX() + x, footprint.minZ() + z))) {
                    int bit = z * side + x;
                    bits[bit >> 5] |= 1 << (bit & 31);
                }
            }
        }
        out.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer().put(firstFootprintWord, bits);
        this.nearRange = nearRange;
        firstPieceWord = firstFootprintWord + bits.length;
        int ownMinX = footprint.minX();
        int ownMinZ = footprint.minZ();
        KcVoxyPieces.Near near = (x, z) -> {
            int dx = x - ownMinX;
            int dz = z - ownMinZ;
            if (dx < 0 || dz < 0 || dx >= side || dz >= side) {
                return false;
            }
            int bit = dz * side + dx;
            return (bits[bit >> 5] >>> (bit & 31) & 1) != 0;
        };
        pieces = KcVoxyPieces.place(plan, originX, originZ, seed, nearRange, near,
                out.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer(), firstPieceWord);
        words = firstPieceWord + KcVoxyPieces.WORDS;

        outerRimPlate = new int[OUTER_RINGS * OUTER_SECTORS];
        outerPivotX = new float[outerRimPlate.length];
        outerPivotZ = new float[outerRimPlate.length];
        for (int ring = 0; ring < OUTER_RINGS; ring++) {
            for (int sector = 0; sector < OUTER_SECTORS; sector++) {
                double angle = ((sector + 0.5D) / OUTER_SECTORS - 0.5D) * Math.PI * 2.0D;
                int index = ring * OUTER_SECTORS + sector;
                int rim = plan.plateAt(Math.cos(angle) * RIM, Math.sin(angle) * RIM);
                outerRimPlate[index] = Math.max(rim, 0);
                outerPivotX[index] = (float) (Math.cos(angle) * OUTER_RADIUS[ring]);
                outerPivotZ[index] = (float) (Math.sin(angle) * OUTER_RADIUS[ring]);
            }
        }
        mapNanos = System.nanoTime() - planned;
    }

    // Texel centres inside each plate's convex outline, one row at a time; a later plate wins a shared edge, as plates are listed.
    private void rasterize(ShortBuffer ids) {
        int step = 1 << BLOCKS_PER_TEXEL_SHIFT;
        double[][] xs = new double[plates][];
        double[][] zs = new double[plates][];
        int[] firstRow = new int[plates];
        int[] lastRow = new int[plates];
        for (int plate = 0; plate < plates; plate++) {
            xs[plate] = plan.polygonX(plate);
            zs[plate] = plan.polygonZ(plate);
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (double z : zs[plate]) {
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            firstRow[plate] = Math.max(0, (int) Math.floor((minZ + HALF_EXTENT) / step));
            lastRow[plate] = Math.min(TEXELS - 1, (int) Math.ceil((maxZ + HALF_EXTENT) / step));
        }
        short[] line = new short[TEXELS];
        for (int row = 0; row < TEXELS; row++) {
            Arrays.fill(line, (short) 0xFFFF);
            double z = row * step + step * 0.5D - HALF_EXTENT;
            for (int plate = 0; plate < plates; plate++) {
                if (row < firstRow[plate] || row > lastRow[plate]) {
                    continue;
                }
                double[] px = xs[plate];
                double[] pz = zs[plate];
                double low = Double.POSITIVE_INFINITY;
                double high = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < px.length; i++) {
                    int next = (i + 1) % px.length;
                    double z0 = pz[i];
                    double z1 = pz[next];
                    if ((z0 <= z) == (z1 <= z)) {
                        continue;
                    }
                    double x = px[i] + (px[next] - px[i]) * (z - z0) / (z1 - z0);
                    low = Math.min(low, x);
                    high = Math.max(high, x);
                }
                if (low > high) {
                    continue;
                }
                int firstColumn = Math.max(0, (int) Math.ceil((low + HALF_EXTENT - step * 0.5D) / step));
                int lastColumn = Math.min(TEXELS - 1, (int) Math.floor((high + HALF_EXTENT - step * 0.5D) / step));
                if (firstColumn <= lastColumn) {
                    Arrays.fill(line, firstColumn, lastColumn + 1, (short) plate);
                }
            }
            ids.put(row * TEXELS, line);
        }
    }
}
