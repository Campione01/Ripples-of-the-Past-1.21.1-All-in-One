package rotp.addons.kingcrimson.shatter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.core.Direction;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Cell;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Outcome;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.RingRead;

/** Detached height surfaces for the completed, world-aligned Voxy rings. */
public final class KingCrimsonShatterFarSurface {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private final List<Long2FloatOpenHashMap> levels = new ArrayList<>(5);
    private final NearFootprint near;

    @FunctionalInterface
    public interface Columns {
        float top(int x, int z);
    }

    /** Bounds are half open. NaN is unowned; +infinity is owned with an unknown height. */
    public record NearFootprint(int minX, int minZ, int maxX, int maxZ, Columns columns) {
        public static final NearFootprint EMPTY = new NearFootprint(0, 0, 0, 0, (x, z) -> Float.NaN);

        private float top(int x, int z) {
            return x >= minX && x < maxX && z >= minZ && z < maxZ ? columns.top(x, z) : Float.NaN;
        }

        private boolean intersects(int x, int z, int size) {
            return minX < maxX && minZ < maxZ && x < maxX && x + size > minX && z < maxZ && z + size > minZ;
        }
    }

    @FunctionalInterface
    public interface Faces {
        /** False stops the ring. On a wall, one axis has zero extent and bottom is its known neighbor height. */
        boolean add(Cell own, Direction direction, int x0, int z0, int x1, int z1, float bottom);
    }

    public KingCrimsonShatterFarSurface(List<RingRead> rings, NearFootprint near, BooleanSupplier cancelled) {
        this.near = near;
        for (int level = 0; level < 5; level++) {
            var heights = new Long2FloatOpenHashMap();
            heights.defaultReturnValue(Float.NaN);
            levels.add(heights);
        }
        for (RingRead ring : rings) {
            if (ring.outcome() != Outcome.COMPLETE) continue;
            var heights = levels.get(ring.spec().level());
            for (Cell cell : ring.cells()) {
                if (cancelled.getAsBoolean()) return;
                heights.put(key(cell.x(), cell.z()), cell.topY());
            }
        }
    }

    public boolean emit(Cell cell, int size, Faces faces) {
        int x = cell.x(), z = cell.z();
        if (!near.intersects(x, z, size)) return rectangle(cell, x, z, x + size, z + size, faces);

        int[] taken = new int[size];
        int occupied = 0;
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                if (!Float.isNaN(near.top(x + dx, z + dz))) {
                    taken[dz] |= 1 << dx;
                    occupied++;
                }
            }
        }
        if (occupied == 0) return rectangle(cell, x, z, x + size, z + size, faces);
        if (occupied == size * size) return true;
        for (int row = 0; row < size; row++) {
            for (int dx = 0; dx < size;) {
                if ((taken[row] & 1 << dx) != 0) {
                    dx++;
                    continue;
                }
                int from = dx++;
                while (dx < size && (taken[row] & 1 << dx) == 0) dx++;
                if (!rectangle(cell, x + from, z + row, x + dx, z + row + 1, faces)) return false;
            }
        }
        return true;
    }

    private boolean rectangle(Cell cell, int x0, int z0, int x1, int z1, Faces faces) {
        if (!faces.add(cell, Direction.UP, x0, z0, x1, z1, cell.topY())) return false;
        for (Direction direction : SIDES) {
            boolean alongX = direction.getAxis() == Direction.Axis.Z;
            boolean negative = direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
            int edge = alongX ? (negative ? z0 : z1) : (negative ? x0 : x1);
            int outside = negative ? edge - 1 : edge;
            int from = alongX ? x0 : z0, to = alongX ? x1 : z1;
            int runStart = from;
            float runBottom = Float.NaN;
            for (int at = from; at <= to; at++) {
                float next = at == to ? Float.NaN : alongX ? height(at, outside) : height(outside, at);
                if (!Float.isFinite(next) || next >= cell.topY()) next = Float.NaN;
                if (Float.compare(next, runBottom) == 0) continue;
                if (Float.isFinite(runBottom) && !(alongX
                        ? faces.add(cell, direction, runStart, edge, at, edge, runBottom)
                        : faces.add(cell, direction, edge, runStart, edge, at, runBottom))) return false;
                runStart = at;
                runBottom = next;
            }
        }
        return true;
    }

    private float height(int x, int z) {
        float owned = near.top(x, z);
        if (!Float.isNaN(owned)) return owned;
        for (int level = 0; level < levels.size(); level++) {
            int mask = -(1 << level);
            float height = levels.get(level).get(key(x & mask, z & mask));
            if (!Float.isNaN(height)) return height;
        }
        return Float.NaN;
    }

    public static long key(int high, int low) {
        return (long) high << 32 | (low & 0xFFFFFFFFL);
    }

    /** The emitted ceiling is an inclusive block Y, not the surface height of that block. */
    public static float knownGroundTop(float groundTop, int highestEmittedBlockY) {
        return Float.isFinite(groundTop) && highestEmittedBlockY < Math.ceil(groundTop)
                ? groundTop : Float.POSITIVE_INFINITY;
    }

    /** NativeImage pixels are ABGR in a Java int; transparent pixels contribute no weight. */
    public static final class SpriteAverage {
        private long red, green, blue, weight;

        public void add(int rgba) {
            int alpha = rgba >>> 24;
            addWeighted(rgba, 0xFFFFFF, (long) alpha * 255);
        }

        /** Source-over for two sprite layers at the same texel, before the face average. */
        public void addLayered(int base, int baseTint, int overlay, int overlayTint) {
            int alpha = overlay >>> 24;
            addWeighted(base, baseTint, (long) (base >>> 24) * (255 - alpha));
            addWeighted(overlay, overlayTint, (long) alpha * 255);
        }

        private void addWeighted(int rgba, int tint, long contribution) {
            red += (rgba & 255) * (tint >>> 16 & 255) * contribution;
            green += (rgba >>> 8 & 255) * (tint >>> 8 & 255) * contribution;
            blue += (rgba >>> 16 & 255) * (tint & 255) * contribution;
            weight += contribution;
        }

        public int rgb() {
            long denominator = weight * 255;
            return denominator == 0 ? -1 : (int) (red / denominator) << 16 | (int) (green / denominator) << 8
                    | (int) (blue / denominator);
        }
    }

    public static int faceColor(int spriteRgb, int tint, int mapColor, float shade) {
        int color = spriteRgb >= 0 ? spriteRgb : mapColor;
        int multiplier = spriteRgb >= 0 ? tint : 0xFFFFFF;
        int r = channel(color, multiplier, shade, 16);
        int g = channel(color, multiplier, shade, 8);
        int b = channel(color, multiplier, shade, 0);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int channel(int color, int tint, float shade, int shift) {
        return (int) ((color >>> shift & 255) * (tint >>> shift & 255) * (shade / 255.0F));
    }
}
