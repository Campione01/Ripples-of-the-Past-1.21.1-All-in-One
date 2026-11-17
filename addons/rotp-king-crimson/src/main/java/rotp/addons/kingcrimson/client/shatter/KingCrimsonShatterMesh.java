package rotp.addons.kingcrimson.client.shatter;

import java.util.Arrays;

import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The broken ground of one erase: flat arrays, built off the render thread and only read afterwards. Coordinates of a
 * plate are relative to its pivot, so that a frame only has to place and turn them.
 */
@OnlyIn(Dist.CLIENT)
final class KingCrimsonShatterMesh {
    /** x y z, size, rise speed, spawn tick, axis x y z, spin, drift velocity x z, end tick, six faces of u0 v0 u1 v1, rise growth. */
    static final int DEBRIS_STRIDE = 38;
    /** Where a rising block keeps how it speeds up as it rises. */
    static final int DEBRIS_GROWTH = 37;
    /** The normal of a wall a cut opened: the index past the six of {@code Direction.ordinal()}. */
    static final int NORMAL_CUT_WALL = 6;
    /** A plate's tear goes in this many steps, each a whole number of columns off the end of its draw range. */
    static final int TEAR_BUCKETS = 64;

    final Plate[] plates;
    final CutLine[] cuts;
    final KingCrimsonShatterWind wind;
    final float[] debris;
    final int[] debrisColors;
    final int[] debrisLights;
    /** One emissive level per rising block; see {@link Layer#levels}. */
    final byte[] debrisLevels;
    final double originX;
    final double originY;
    final double originZ;
    final int solidVertices;
    final int cutoutVertices;
    final int flatVertices;
    /** Cells past the budget of vertices, which went coarse. */
    final int coarseCells;
    final long buildMillis;
    final KingCrimsonShatterFarSurface.NearFootprint footprint;

    KingCrimsonShatterMesh(
            Plate[] plates,
            CutLine[] cuts,
            KingCrimsonShatterWind wind,
            float[] debris,
            int[] debrisColors,
            int[] debrisLights,
            double originX,
            double originY,
            double originZ,
            int solidVertices,
            int cutoutVertices,
            int flatVertices,
            int coarseCells,
            long buildMillis) {
        this(plates, cuts, wind, debris, debrisColors, debrisLights, originX, originY, originZ,
                solidVertices, cutoutVertices, flatVertices, coarseCells, buildMillis,
                KingCrimsonShatterFarSurface.NearFootprint.EMPTY);
    }

    KingCrimsonShatterMesh(
            Plate[] plates,
            CutLine[] cuts,
            KingCrimsonShatterWind wind,
            float[] debris,
            int[] debrisColors,
            int[] debrisLights,
            double originX,
            double originY,
            double originZ,
            int solidVertices,
            int cutoutVertices,
            int flatVertices,
            int coarseCells,
            long buildMillis,
            KingCrimsonShatterFarSurface.NearFootprint footprint) {
        this(plates, cuts, wind, debris, debrisColors, debrisLights, new byte[debrisLights.length], originX, originY, originZ,
                solidVertices, cutoutVertices, flatVertices, coarseCells, buildMillis, footprint);
    }

    KingCrimsonShatterMesh(
            Plate[] plates,
            CutLine[] cuts,
            KingCrimsonShatterWind wind,
            float[] debris,
            int[] debrisColors,
            int[] debrisLights,
            byte[] debrisLevels,
            double originX,
            double originY,
            double originZ,
            int solidVertices,
            int cutoutVertices,
            int flatVertices,
            int coarseCells,
            long buildMillis,
            KingCrimsonShatterFarSurface.NearFootprint footprint) {
        this.plates = plates;
        this.cuts = cuts;
        this.wind = wind;
        this.debris = debris;
        this.debrisColors = debrisColors;
        this.debrisLights = debrisLights;
        this.debrisLevels = debrisLevels;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.solidVertices = solidVertices;
        this.cutoutVertices = cutoutVertices;
        this.flatVertices = flatVertices;
        this.coarseCells = coarseCells;
        this.buildMillis = buildMillis;
        this.footprint = footprint;
    }

    @OnlyIn(Dist.CLIENT)
    static final class Plate {
        final int id;
        final double pivotX;
        final double pivotY;
        final double pivotZ;
        final float radius;
        /** The plate's ground and its underside, in its own coordinates: what can come between the camera and the user. */
        final float groundTop;
        final float bottom;
        Layer solid;
        Layer cutout;
        Layer flat;

        Plate(
                int id,
                double pivotX,
                double pivotY,
                double pivotZ,
                float radius,
                float groundTop,
                float bottom,
                Layer solid,
                Layer cutout,
                Layer flat) {
            this.id = id;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.radius = radius;
            this.groundTop = groundTop;
            this.bottom = bottom;
            this.solid = solid;
            this.cutout = cutout;
            this.flat = flat;
        }

        void releaseGeometry() {
            solid = null;
            cutout = null;
            flat = null;
        }
    }

    /** One straight crack on the ground, sampled along its length; all relative to the erase's origin. */
    @OnlyIn(Dist.CLIENT)
    static final class CutLine {
        final float startX;
        final float startZ;
        final float dirX;
        final float dirZ;
        final float startTick;
        final KingCrimsonShatterPlan.Cut source;
        final float[] along;
        final float[] height;
        /** When the plates on either side part at each sample: the line gives way to their real edges. */
        final float[] parting;
        final float lastParting;

        CutLine(
                float startX,
                float startZ,
                float dirX,
                float dirZ,
                float startTick,
                KingCrimsonShatterPlan.Cut source,
                float[] along,
                float[] height,
                float[] parting,
                float lastParting) {
            this.startX = startX;
            this.startZ = startZ;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.startTick = startTick;
            this.source = source;
            this.along = along;
            this.height = height;
            this.parting = parting;
            this.lastParting = lastParting;
        }
    }

    /**
     * Quads: position and texture of each vertex, its colour, and one normal index and one real light per quad. They lie in
     * the order of the plate's tear, those that go last first; {@code kept[n]} is how many quads the first n buckets hold,
     * so that a draw takes that many from the start and needs no search.
     */
    @OnlyIn(Dist.CLIENT)
    static final class Layer {
        static final int STRIDE = 5;
        static final Layer EMPTY = new Layer(new float[0], new int[0], new byte[0], new int[0], new byte[0], new int[TEAR_BUCKETS + 1]);

        final float[] vertices;
        final int[] colors;
        final byte[] normals;
        final int[] lights;
        /** The emissive level of each quad for a pack that declares the void; 0 where nothing was asked of it. */
        final byte[] levels;
        final int[] kept;

        Layer(float[] vertices, int[] colors, byte[] normals, int[] lights, byte[] levels, int[] kept) {
            this.vertices = vertices;
            this.colors = colors;
            this.normals = normals;
            this.lights = lights;
            this.levels = levels;
            this.kept = kept;
        }

        int vertexCount() {
            return colors.length;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static final class LayerBuilder {
        private float[] vertices = new float[Layer.STRIDE * 4 * 8];
        private int[] colors = new int[4 * 8];
        private byte[] normals = new byte[8];
        private int[] lights = new int[8];
        private float[] orders = new float[8];
        private byte[] levels = new byte[8];
        private int quads;
        /** When the column being emitted goes: every quad takes this until it changes. */
        float order;
        /** The emissive level: every quad takes this until it changes. */
        byte level;

        void quad(
                int normal,
                int light,
                float x0, float y0, float z0, float u0, float v0, int c0,
                float x1, float y1, float z1, float u1, float v1, int c1,
                float x2, float y2, float z2, float u2, float v2, int c2,
                float x3, float y3, float z3, float u3, float v3, int c3) {
            if (quads == normals.length) {
                int capacity = quads * 2;
                normals = Arrays.copyOf(normals, capacity);
                lights = Arrays.copyOf(lights, capacity);
                orders = Arrays.copyOf(orders, capacity);
                levels = Arrays.copyOf(levels, capacity);
                colors = Arrays.copyOf(colors, capacity * 4);
                vertices = Arrays.copyOf(vertices, capacity * 4 * Layer.STRIDE);
            }
            normals[quads] = (byte) normal;
            lights[quads] = light;
            orders[quads] = order;
            levels[quads] = level;
            int color = quads * 4;
            colors[color] = c0;
            colors[color + 1] = c1;
            colors[color + 2] = c2;
            colors[color + 3] = c3;
            int at = color * Layer.STRIDE;
            float[] out = vertices;
            out[at] = x0;
            out[at + 1] = y0;
            out[at + 2] = z0;
            out[at + 3] = u0;
            out[at + 4] = v0;
            out[at + 5] = x1;
            out[at + 6] = y1;
            out[at + 7] = z1;
            out[at + 8] = u1;
            out[at + 9] = v1;
            out[at + 10] = x2;
            out[at + 11] = y2;
            out[at + 12] = z2;
            out[at + 13] = u2;
            out[at + 14] = v2;
            out[at + 15] = x3;
            out[at + 16] = y3;
            out[at + 17] = z3;
            out[at + 18] = u3;
            out[at + 19] = v3;
            quads++;
        }

        int vertexCount() {
            return quads * 4;
        }

        int quadCount() {
            return quads;
        }

        int copyOrders(float[] into, int at) {
            System.arraycopy(orders, 0, into, at, quads);
            return at + quads;
        }

        /** {@code limits} is each bucket's highest order, the same for all the layers of a plate, so that a column goes as one. */
        Layer build(float[] limits) {
            if (quads == 0) {
                return Layer.EMPTY;
            }
            byte[] bucketOf = new byte[quads];
            int[] kept = new int[TEAR_BUCKETS + 1];
            for (int quad = 0; quad < quads; quad++) {
                int found = Arrays.binarySearch(limits, orders[quad]);
                // The first of equal limits, or the first above the order.
                int bucket = found < 0 ? -found - 1 : found;
                while (bucket > 0 && limits[bucket - 1] >= orders[quad]) {
                    bucket--;
                }
                bucket = Math.min(bucket, TEAR_BUCKETS - 1);
                bucketOf[quad] = (byte) bucket;
                kept[bucket + 1]++;
            }
            for (int bucket = 0; bucket < TEAR_BUCKETS; bucket++) {
                kept[bucket + 1] += kept[bucket];
            }
            int[] next = Arrays.copyOf(kept, TEAR_BUCKETS);
            float[] sortedVertices = new float[quads * 4 * Layer.STRIDE];
            int[] sortedColors = new int[quads * 4];
            byte[] sortedNormals = new byte[quads];
            int[] sortedLights = new int[quads];
            byte[] sortedLevels = new byte[quads];
            for (int quad = 0; quad < quads; quad++) {
                int to = next[bucketOf[quad]]++;
                System.arraycopy(vertices, quad * 4 * Layer.STRIDE, sortedVertices, to * 4 * Layer.STRIDE, 4 * Layer.STRIDE);
                System.arraycopy(colors, quad * 4, sortedColors, to * 4, 4);
                sortedNormals[to] = normals[quad];
                sortedLights[to] = lights[quad];
                sortedLevels[to] = levels[quad];
            }
            return new Layer(sortedVertices, sortedColors, sortedNormals, sortedLights, sortedLevels, kept);
        }
    }
}
