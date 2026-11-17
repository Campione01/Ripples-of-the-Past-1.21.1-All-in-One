package rotp.addons.kingcrimson.shatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How the ground breaks up when Time Erase starts: which plate owns each column, the straight cuts that divide them, and
 * where a plate is at a given tick of the erase. Pure math seeded from the erase's origin, with no client classes, so
 * that game tests can load it. Tick 0 comes one tick before the first crack leaves the user's feet.
 *
 * <p>The partition is made of the cuts themselves. Primary cuts leave the user's feet in a star; as each runs, secondary
 * cuts branch off it toward the outside, alternating sides, and run straight until they meet a line; more chords farther
 * out make plates grow with their distance. Each cut splits one convex piece in two, so every column in range belongs
 * to exactly one plate, and a crack and a plate's edge are the same line.
 */
public final class KingCrimsonShatterPlan {
    public static final int DEFAULT_RANGE = 256;
    /** A crest follows the thin dark line of its cut by this much. */
    public static final float WIND_LAG_TICKS = 0.5F;
    /** No crest is left after this tick. */
    public static final float WIND_LAST_TICK = 38.0F;
    /** How slowly a crest's head, and the crack just ahead of it, lose their speed: long, so that the head keeps running. */
    public static final float HEAD_TAU_TICKS = 19.0F;
    /** A crest's head leaves at its reach over {@link #HEAD_TAU_TICKS}, about 5 to 8.5 blocks a tick. */
    public static final float CREST_REACH_MIN = 100.0F;
    public static final float CREST_REACH_MAX = 160.0F;
    public static final float CREST_LIFE_MIN = 24.0F;
    public static final float CREST_LIFE_MAX = 32.0F;
    /** How far the crests' heads can come from their cuts' starts in all. */
    public static final float CREST_FARTHEST = 132.0F;
    /** The shrink that takes the last clusters of a plate, as a share of its tear. */
    public static final float TEAR_SHRINK_SHARE = 0.3F;
    public static final float MIN_LIFE_TICKS = 24.0F;
    public static final float MAX_LIFE_TICKS = 82.0F;
    /** The last of the ground has gone by this tick, about 4.45 s after the sky flips. Nothing of it is left afterwards. */
    public static final float END_TICK = 85.0F;
    /** The universe's fade off the returned world, see {@link #returnOpacity}; real time, as the pack's exposure follows it. */
    public static final float RETURN_FADE_SECONDS = 0.5F;
    /** Small blocks start to rise between these ticks, about 0.6 and 3.35 s after the sky flips. */
    public static final float DEBRIS_FIRST_TICK = 8.0F;
    public static final float DEBRIS_LAST_TICK = 63.0F;
    /** Each small block ends at its own time from this tick on, in about {@link #DEBRIS_SHRINK_TICKS}. */
    public static final float DEBRIS_FIRST_END_TICK = 50.0F;
    public static final float DEBRIS_SHRINK_TICKS = 6.0F;
    /** A plate nearer the user than another by more than this starts before it: inner plates lead, outer ones follow. */
    public static final float LEADS_BY_BLOCKS = 24.0F;

    public static final int KIND_PRIMARY = 0;
    public static final int KIND_BRANCH = 1;
    public static final int KIND_CHORD = 2;

    // A crack keeps a little ahead of its crest; then, as the crest slows down, it races on to the outer edge, which it
    // reaches in this many ticks however large the range is.
    private static final float CRACK_LEAD = 1.25F;
    private static final float CRACK_RACES_FROM_TICK = 3.5F;
    static final float HEAD_TICKS_TO_EDGE = 10.0F;
    private static final float LEAST_RACE = 0.4F;
    private static final float CROSS_CREST_REACH = 0.4F;
    // A chord has no crest; its crack leaves as if it had a small one.
    private static final float CHORD_CREST_REACH = 54.0F;
    private static final float FEET_CLEAR_BLOCKS = 1.0F;
    static final float FIRST_LAUNCH_TICK = 1.0F;
    private static final float PRIMARY_STAGGER_TICKS = 0.9F;
    private static final float LAST_CUT_START_TICK = HEAD_TICKS_TO_EDGE;
    // Uneven, seen from behind the user: 0 is where he faces, positive to his right. None runs straight at the camera.
    private static final double[] PRIMARY_DEGREES = {-158.0D, -117.0D, -71.0D, -23.0D, 29.0D, 62.0D, 108.0D, 143.0D};
    private static final double PRIMARY_JITTER_DEGREES = 6.0D;
    private static final double MIN_BRANCH_ANGLE = Math.toRadians(26.0D);
    private static final double MAX_BRANCH_ANGLE = Math.toRadians(46.0D);
    private static final double FIRST_BRANCH_ALONG = 6.0D;
    private static final double FIRST_BRANCH_SPREAD = 7.0D;
    // A plate starts this soon after the last line round it has run past: the line is its edge, and it comes loose.
    private static final float LOOSE_AFTER_TICKS = 0.5F;
    private static final float LEAD_TICKS = 0.25F;
    // The breaking spreads out behind the running crests; near the user a plate starts as soon as the crack has passed.
    static final float WAVE_FROM_TICK = 4.0F;
    private static final float WAVE_TICKS_PER_BLOCK = 1.0F / 6.0F;
    private static final float WAVE_MOST_TICKS = 26.0F;
    private static final float WAVE_JITTER_TICKS = 3.0F;
    private static final float WAVE_JITTER_FULL_BLOCKS = 24.0F;
    // One law for the opening of the trenches, the lift, the tilt and the tumble: the share of its way a plate has gone at an
    // age, 1 at this age. The exponent is above 1 so that it leaves slowly and only ever gets faster, and low enough that the
    // gaps can be seen opening while the crests still run: at 2 the ground seemed to hold still for a third of a second.
    private static final float TRAVEL_REFERENCE_TICKS = 45.0F;
    private static final double TRAVEL_POWER = 1.7D;
    private static final float OUT_REACH = 8.0F;
    // In proportion to the distance, so that the ground spreads like a stretched sheet: every gap opens, none closes.
    private static final float OUT_REACH_PER_BLOCK = 0.4F;
    private static final float UP_REACH = 8.5F;
    private static final float UP_REACH_PER_BLOCK = 0.045F;
    private static final float MAX_TILT = (float) Math.toRadians(9.0D);
    private static final float MIN_TUMBLE = (float) Math.toRadians(3.0D);
    private static final float MAX_TUMBLE = (float) Math.toRadians(16.0D);
    // The flight: one more long term of the outward motion that gets going later and carries the plate toward the horizon.
    private static final float FLIGHT_REFERENCE_TICKS = 58.0F;
    private static final float FLIGHT_POWER = 3.0F;
    private static final float FLIGHT_PER_RANGE = 0.6F;
    private static final float FLIGHT_PER_BLOCK = 0.8F;
    private static final float FLIGHT_SHARE_MIN = 0.7F;
    private static final float FLIGHT_SHARE_MAX = 1.35F;
    private static final float FLIGHT_DELAY_MOST = 12.0F;
    // Off the radial, so that it scatters and does not grow as a neat ring: the flight's share of the motion grows, and so
    // does the turn it brings.
    private static final double FLIGHT_TURN_MOST = Math.toRadians(14.0D);
    // A little of the flight goes up, differently for each plate, so that they pass above and under each other late on.
    private static final float FLIGHT_UP_MOST = 0.05F;
    // Vanilla's far plane is 4 x the render distance, measured from the camera, which the user may have walked away with.
    private static final float FARTHEST_RANGES = 3.4F;
    // Each plate tears away in its own time: the horizon thins first, the plates near the user last.
    private static final float TEAR_NEAR_FROM = 52.0F;
    private static final float TEAR_NEAR_SPREAD = 18.0F;
    private static final float TEAR_FAR_FROM = 25.0F;
    private static final float TEAR_FAR_SPREAD = 27.0F;
    private static final float TEAR_FAR_AT_RANGE = 0.9F;
    private static final float TEAR_AFTER_START_TICKS = 12.0F;
    private static final float TEAR_TICKS_MIN = 13.0F;
    private static final float TEAR_TICKS_MAX = 25.0F;
    private static final float ENDS_APART_TICKS = 2.0F;
    private static final float ENDS_APART_SHARE = 0.1F;
    // Clusters and strips: the strips lie across the way out, so that they read as strips when seen flat from the user.
    private static final float TEAR_GRAIN_SHARE = 0.16F;
    private static final float TEAR_GRAIN_MIN = 2.0F;
    private static final float TEAR_GRAIN_MAX = 12.0F;
    private static final float TEAR_STRIP_STRETCH = 3.0F;
    private static final float TEAR_FINE_SHARE = 0.3F;
    private static final float TEAR_COLUMN_SHARE = 0.05F;
    // A small block drifts out at its own speed times this law: slowly at first, and it only gets faster.
    private static final float DEBRIS_DRIFT_GROWTH = 0.05F;
    /** How a small block from a crack or an underside speeds up as it rises. */
    public static final float DEBRIS_RISE_GROWTH = 0.0025F;
    /** A loose block torn off a plate's top floats up as if it weighed nothing: slower, and it hardly speeds up. */
    public static final float LOOSE_RISE_GROWTH = 0.0015F;
    private static final float DEBRIS_LEAST_LIFE_TICKS = 11.0F;
    private static final float DEBRIS_NEAR_SHARE = 0.35F;
    private static final float DEBRIS_NEAR_BLOCKS = 64.0F;
    private static final int MAX_PLATES = 700;
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private final int originX;
    private final int originZ;
    private final long seed;
    private final int range;
    // The range a far plan's flight and tear follow: the near plan's, so that a plate just outside the join flies and tears as one
    // just inside it does. 0 for a plan of its own range.
    private final int lawRange;
    private final float edgeReach;
    private final double facingX;
    private final double facingZ;
    private final double firstAngle;
    private final double[] rayAngles;
    private final Node[] wedges;
    private final double[][] wedgeOuter;
    private final List<Cut> cuts = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();
    private final int plateCount;

    private final double[][] polygonX;
    private final double[][] polygonZ;
    private final float[] pivotX;
    private final float[] pivotZ;
    private final float[] plateDistance;
    private final float[] latestArrival;
    private final float[] outX;
    private final float[] outZ;
    private final float[] startTick;
    private final float[] outReach;
    private final float[] upReach;
    private final float[] tiltAngle;
    private final float[] tiltAxis;
    private final float[] tumbleAxis;
    private final float[] tumbleAngle;
    private final float[] plateRadius;
    private final float[] flightX;
    private final float[] flightZ;
    private final float[] flightReach;
    private final float[] flightDelay;
    private final float[] flightUp;
    private final float[] tearStart;
    private final float[] tearTicks;
    private final long[] tearSeed;
    private final float[] tearAlongX;
    private final float[] tearAlongZ;
    private final float[] tearGrain;

    private KingCrimsonShatterPlan(int originX, int originZ, long seed, int range, float facingYawDegrees, int lawRange) {
        this.originX = originX;
        this.originZ = originZ;
        this.seed = seed;
        this.range = range;
        this.lawRange = lawRange;
        Stream stream = new Stream(mix(seed));

        int primaries = PRIMARY_DEGREES.length;
        double[] degrees = new double[primaries];
        for (int i = 0; i < primaries; i++) {
            degrees[i] = PRIMARY_DEGREES[i] + (stream.nextDouble() * 2.0D - 1.0D) * PRIMARY_JITTER_DEGREES;
        }
        // Yaw 0 faces +z, which is 90 degrees in this plan's atan2(z, x); both angles grow the same way.
        firstAngle = Math.toRadians(90.0D + facingYawDegrees + degrees[0]);
        rayAngles = new double[primaries];
        double widest = 0.0D;
        for (int i = 0; i < primaries; i++) {
            rayAngles[i] = Math.toRadians(degrees[i] - degrees[0]);
            double next = i + 1 < primaries ? degrees[i + 1] : degrees[0] + 360.0D;
            widest = Math.max(widest, Math.toRadians(next - degrees[i]));
        }
        // The outer polygon holds the whole circle of the range, and its edges are as straight as all the others.
        double reach = range / Math.cos(widest * 0.5D);
        edgeReach = (float) reach;
        double facing = Math.toRadians(90.0D + facingYawDegrees);
        facingX = Math.cos(facing);
        facingZ = Math.sin(facing);
        double[] rayX = new double[primaries];
        double[] rayZ = new double[primaries];
        int[] launchOrder = shuffled(primaries, stream);
        for (int i = 0; i < primaries; i++) {
            rayX[i] = Math.cos(firstAngle + rayAngles[i]);
            rayZ[i] = Math.sin(firstAngle + rayAngles[i]);
            float launch = FIRST_LAUNCH_TICK + launchOrder[i] * PRIMARY_STAGGER_TICKS / (primaries - 1) + stream.nextFloat() * 0.1F;
            float crestReach = CREST_REACH_MIN + stream.nextFloat() * (CREST_REACH_MAX - CREST_REACH_MIN);
            cuts.add(new Cut(0.0D, 0.0D, rayX[i], rayZ[i], reach, launch, KIND_PRIMARY, stream.nextLong(), crestReach, edgeReach));
        }
        wedges = new Node[primaries];
        wedgeOuter = new double[primaries][];
        for (int i = 0; i < primaries; i++) {
            int next = (i + 1) % primaries;
            Piece piece = new Piece(
                    new double[] {0.0D, rayX[i] * reach, rayX[next] * reach},
                    new double[] {0.0D, rayZ[i] * reach, rayZ[next] * reach},
                    new int[] {i, -1, next});
            Node root = new Node();
            root.piece = piece;
            piece.node = root;
            wedges[i] = root;
            pieces.add(piece);
            double edgeX = rayX[next] - rayX[i];
            double edgeZ = rayZ[next] - rayZ[i];
            // the outward normal of the far edge, and how far along it the edge lies
            double normalX = edgeZ;
            double normalZ = -edgeX;
            double offset = (normalX * rayX[i] + normalZ * rayZ[i]) * reach;
            wedgeOuter[i] = new double[] {normalX, normalZ, offset};
        }

        branchOffPrimaries(primaries, rayX, rayZ, reach, stream);
        fillWithChords(stream);

        pieces.sort(Comparator.comparingDouble(Piece::distance));
        plateCount = pieces.size();
        polygonX = new double[plateCount][];
        polygonZ = new double[plateCount][];
        pivotX = new float[plateCount];
        pivotZ = new float[plateCount];
        plateDistance = new float[plateCount];
        latestArrival = new float[plateCount];
        outX = new float[plateCount];
        outZ = new float[plateCount];
        startTick = new float[plateCount];
        outReach = new float[plateCount];
        upReach = new float[plateCount];
        tiltAngle = new float[plateCount];
        tiltAxis = new float[plateCount * 3];
        tumbleAxis = new float[plateCount * 3];
        tumbleAngle = new float[plateCount];
        plateRadius = new float[plateCount];
        flightX = new float[plateCount];
        flightZ = new float[plateCount];
        flightReach = new float[plateCount];
        flightDelay = new float[plateCount];
        flightUp = new float[plateCount];
        tearStart = new float[plateCount];
        tearTicks = new float[plateCount];
        tearSeed = new long[plateCount];
        tearAlongX = new float[plateCount];
        tearAlongZ = new float[plateCount];
        tearGrain = new float[plateCount];
        for (int plate = 0; plate < plateCount; plate++) {
            Piece piece = pieces.get(plate);
            piece.node.plate = plate;
            describePlate(plate, piece);
        }
        innerPlatesLead();
        for (int plate = 0; plate < plateCount; plate++) {
            timeTheTear(plate);
        }
        spreadTheEnds();
        for (int plate = 0; plate < plateCount; plate++) {
            keepFromTheFarPlane(plate);
        }
    }

    /** {@code facingYawDegrees} is the way the user faces at the press: the star of cuts is laid out around it. */
    public static KingCrimsonShatterPlan create(int originX, int originZ, long seed, int range, float facingYawDegrees) {
        return new KingCrimsonShatterPlan(originX, originZ, seed, range, facingYawDegrees, 0);
    }

    /**
     * A plan of the far field past a near plan of {@code nearRange}: its cuts and plates reach {@code range}, while its plates fly and
     * tear by the near plan's law, as the far-terrain add-on wants them. The same seed and yaw as the near plan give the same star.
     */
    public static KingCrimsonShatterPlan createFar(
            int originX, int originZ, long seed, int range, float facingYawDegrees, int nearRange) {
        if (nearRange <= 0 || nearRange > range) throw new IllegalArgumentException("near range " + nearRange + " of " + range);
        return new KingCrimsonShatterPlan(originX, originZ, seed, range, facingYawDegrees, nearRange);
    }

    /** The range this plan's plates fly and tear by: {@link #range} but for a far plan. */
    public int lawRange() {
        return lawRange > 0 ? lawRange : range;
    }

    // As each primary runs, secondaries leave it toward the outside, alternating sides, in order of arrival.
    private void branchOffPrimaries(int primaries, double[] rayX, double[] rayZ, double reach, Stream stream) {
        List<Branch> branches = new ArrayList<>();
        for (int ray = 0; ray < primaries; ray++) {
            double along = FIRST_BRANCH_ALONG + stream.nextDouble() * FIRST_BRANCH_SPREAD;
            int side = stream.nextFloat() < 0.5F ? 1 : -1;
            while (along < reach * 0.92D) {
                double turn = MIN_BRANCH_ANGLE + stream.nextDouble() * (MAX_BRANCH_ANGLE - MIN_BRANCH_ANGLE);
                branches.add(new Branch(cuts.get(ray).arrivalAt(along), ray, along, side, turn));
                side = -side;
                along = along * (1.38D + stream.nextDouble() * 0.3D) + 1.5D;
            }
        }
        branches.sort(Comparator.comparingDouble(branch -> branch.tick));
        for (Branch branch : branches) {
            double px = rayX[branch.ray] * branch.along;
            double pz = rayZ[branch.ray] * branch.along;
            double cos = Math.cos(branch.turn * branch.side);
            double sin = Math.sin(branch.turn * branch.side);
            double dx = rayX[branch.ray] * cos - rayZ[branch.ray] * sin;
            double dz = rayX[branch.ray] * sin + rayZ[branch.ray] * cos;
            Piece piece = pieceAt(px + dx * 0.01D, pz + dz * 0.01D);
            if (piece != null) {
                split(piece, px, pz, dx, dz, KIND_BRANCH, cuts.get(branch.ray).crestReach * CROSS_CREST_REACH, stream);
            }
        }
    }

    // Plates grow with their distance: a piece too large for where it lies gets one more long diagonal.
    private void fillWithChords(Stream stream) {
        int index = 0;
        List<Piece> stuck = new ArrayList<>();
        while (index < pieces.size() && pieces.size() < MAX_PLATES) {
            Piece piece = pieces.get(index);
            double size = plateSize(piece.distance());
            if (stuck.contains(piece) || (piece.area() <= size * size * 1.5D && piece.longestEdge() <= size * 2.2D)) {
                index++;
                continue;
            }
            double cx = piece.centerX();
            double cz = piece.centerZ();
            double length = Math.max(1.0E-3D, Math.sqrt(cx * cx + cz * cz));
            double radialX = cx / length;
            double radialZ = cz / length;
            double turn = MIN_BRANCH_ANGLE + stream.nextDouble() * (MAX_BRANCH_ANGLE - MIN_BRANCH_ANGLE);
            int side = stream.nextFloat() < 0.5F ? 1 : -1;
            boolean done = false;
            for (int attempt = 0; attempt < 2 && !done; attempt++, side = -side) {
                double cos = Math.cos(turn * side);
                double sin = Math.sin(turn * side);
                double dx = radialX * cos - radialZ * sin;
                double dz = radialX * sin + radialZ * cos;
                done = split(piece, cx, cz, dx, dz, KIND_CHORD, CHORD_CREST_REACH, stream);
            }
            if (!done) {
                stuck.add(piece);
            }
        }
    }

    private static double plateSize(double distance) {
        return 7.0D + 0.3D * distance;
    }

    // Splits a convex piece along a line; false when that would leave a sliver.
    private boolean split(Piece piece, double px, double pz, double dx, double dz, int kind, float crestReach, Stream stream) {
        double normalX = -dz;
        double normalZ = dx;
        int count = piece.x.length;
        double[] side = new double[count];
        for (int i = 0; i < count; i++) {
            side[i] = normalX * (piece.x[i] - px) + normalZ * (piece.z[i] - pz);
        }
        int first = -1;
        int second = -1;
        for (int i = 0; i < count; i++) {
            if (side[i] >= 0.0D != side[(i + 1) % count] >= 0.0D) {
                if (first < 0) {
                    first = i;
                } else if (second < 0) {
                    second = i;
                } else {
                    return false;
                }
            }
        }
        if (second < 0) {
            return false;
        }
        double t1 = side[first] / (side[first] - side[(first + 1) % count]);
        double t2 = side[second] / (side[second] - side[(second + 1) % count]);
        double ax = piece.x[first] + (piece.x[(first + 1) % count] - piece.x[first]) * t1;
        double az = piece.z[first] + (piece.z[(first + 1) % count] - piece.z[first]) * t1;
        double bx = piece.x[second] + (piece.x[(second + 1) % count] - piece.x[second]) * t2;
        double bz = piece.z[second] + (piece.z[(second + 1) % count] - piece.z[second]) * t2;

        int countA = second - first + 2;
        double[] xA = new double[countA];
        double[] zA = new double[countA];
        int[] edgeA = new int[countA];
        xA[0] = ax;
        zA[0] = az;
        for (int i = 0; i < second - first; i++) {
            xA[i + 1] = piece.x[first + 1 + i];
            zA[i + 1] = piece.z[first + 1 + i];
            edgeA[i] = piece.edgeCut[first + i];
        }
        edgeA[countA - 2] = piece.edgeCut[second];
        xA[countA - 1] = bx;
        zA[countA - 1] = bz;
        int countB = count - countA + 4;
        double[] xB = new double[countB];
        double[] zB = new double[countB];
        int[] edgeB = new int[countB];
        xB[0] = bx;
        zB[0] = bz;
        int at = 1;
        for (int i = second + 1; i != first + 1; i = (i + 1) % count) {
            xB[at] = piece.x[i % count];
            zB[at] = piece.z[i % count];
            edgeB[at - 1] = piece.edgeCut[(i + count - 1) % count];
            at++;
        }
        edgeB[at - 1] = piece.edgeCut[first];
        xB[at] = ax;
        zB[at] = az;

        Piece pieceA = new Piece(xA, zA, edgeA);
        Piece pieceB = new Piece(xB, zB, edgeB);
        double size = plateSize(Math.min(pieceA.distance(), pieceB.distance()));
        if (Math.min(pieceA.area(), pieceB.area()) < size * size * 0.28D
                || pieceA.area() / pieceA.longestEdge() < size * 0.3D
                || pieceB.area() / pieceB.longestEdge() < size * 0.3D) {
            return false;
        }

        // A cut runs from its inner end, starting when the line it leaves reaches that point.
        boolean fromA = ax * ax + az * az <= bx * bx + bz * bz;
        double startX = fromA ? ax : bx;
        double startZ = fromA ? az : bz;
        double endX = fromA ? bx : ax;
        double endZ = fromA ? bz : az;
        int leaves = piece.edgeCut[fromA ? first : second];
        float tick = (leaves >= 0
                ? cuts.get(leaves).arrivalAtPoint(startX, startZ)
                : headAge(Math.sqrt(startX * startX + startZ * startZ))) + stream.nextFloat() * 0.3F;
        // However deep a chain of cuts goes, they are all running by then: the far ground cracks as fast as the near.
        tick = Math.min(tick, LAST_CUT_START_TICK);
        double length = Math.hypot(endX - startX, endZ - startZ);
        int cut = cuts.size();
        cuts.add(new Cut(
                startX, startZ, (endX - startX) / length, (endZ - startZ) / length, length, tick, kind, stream.nextLong(), crestReach, edgeReach));
        edgeA[countA - 1] = cut;
        edgeB[countB - 1] = cut;

        Node node = piece.node;
        node.piece = null;
        node.normalX = normalX;
        node.normalZ = normalZ;
        node.offset = normalX * px + normalZ * pz;
        boolean aOnPositive = side[(first + 1) % count] >= 0.0D;
        node.positive = new Node();
        node.negative = new Node();
        Node nodeA = aOnPositive ? node.positive : node.negative;
        Node nodeB = aOnPositive ? node.negative : node.positive;
        nodeA.piece = pieceA;
        nodeB.piece = pieceB;
        pieceA.node = nodeA;
        pieceB.node = nodeB;
        int index = pieces.indexOf(piece);
        pieces.set(index, pieceA);
        pieces.add(pieceB);
        return true;
    }

    private Piece pieceAt(double dx, double dz) {
        Node node = wedgeAt(dx, dz);
        while (node.piece == null) {
            node = node.normalX * dx + node.normalZ * dz >= node.offset ? node.positive : node.negative;
        }
        return node.piece;
    }

    private Node wedgeAt(double dx, double dz) {
        return wedges[wedgeIndex(dx, dz)];
    }

    private int wedgeIndex(double dx, double dz) {
        double relative = wrap(Math.atan2(dz, dx) - firstAngle);
        int low = 0;
        int high = rayAngles.length - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (rayAngles[middle] <= relative) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private void describePlate(int plate, Piece piece) {
        Stream stream = new Stream(mix(seed ^ (plate * GOLDEN + 0x5851F42D4C957F2DL)));
        polygonX[plate] = piece.x;
        polygonZ[plate] = piece.z;
        double centerX = piece.centerX();
        double centerZ = piece.centerZ();
        float distance = (float) Math.sqrt(centerX * centerX + centerZ * centerZ);
        float dirX = distance > 1.0E-3F ? (float) (centerX / distance) : 1.0F;
        float dirZ = distance > 1.0E-3F ? (float) (centerZ / distance) : 0.0F;
        pivotX[plate] = (float) (originX + centerX);
        pivotZ[plate] = (float) (originZ + centerZ);
        plateDistance[plate] = distance;
        outX[plate] = dirX;
        outZ[plate] = dirZ;

        float arrival = 0.0F;
        int corners = piece.x.length;
        for (int i = 0; i < corners; i++) {
            int line = piece.edgeCut[i];
            if (line >= 0) {
                Cut cut = cuts.get(line);
                arrival = Math.max(arrival, cut.arrivalAtPoint(piece.x[i], piece.z[i]));
                arrival = Math.max(arrival, cut.arrivalAtPoint(piece.x[(i + 1) % corners], piece.z[(i + 1) % corners]));
            }
        }
        latestArrival[plate] = arrival;
        float wave = WAVE_FROM_TICK
                + Math.min(distance * WAVE_TICKS_PER_BLOCK, WAVE_MOST_TICKS)
                + stream.nextFloat() * WAVE_JITTER_TICKS * smooth(distance / WAVE_JITTER_FULL_BLOCKS);
        startTick[plate] = Math.max(wave, arrival + LOOSE_AFTER_TICKS);

        // Near the user a plate slides away more than it rises, so that it does not come up through his view.
        float nearness = smooth(distance / 26.0F);
        float flightDistance = Math.min(distance, 1024.0F);
        outReach[plate] = (OUT_REACH + OUT_REACH_PER_BLOCK * flightDistance) * (0.88F + stream.nextFloat() * 0.24F);
        upReach[plate] = (UP_REACH + UP_REACH_PER_BLOCK * flightDistance) * (0.7F + stream.nextFloat() * 0.6F)
                * (0.25F + 0.75F * smooth(distance / 30.0F));
        tiltAngle[plate] = MAX_TILT * (0.25F + stream.nextFloat() * 0.75F)
                // most plates lean back, their inner edge up and their dirt side to the user, as in the reference
                * (stream.nextFloat() < 0.75F ? 1.0F : -1.0F)
                * (0.5F + 0.5F * nearness);

        float axisTurn = (stream.nextFloat() - 0.5F) * 1.2F;
        float cos = (float) Math.cos(axisTurn);
        float sin = (float) Math.sin(axisTurn);
        // up cross outward, so that a positive angle lowers the outer edge
        tiltAxis[plate * 3] = dirZ * cos + dirX * sin;
        tiltAxis[plate * 3 + 1] = 0.0F;
        tiltAxis[plate * 3 + 2] = dirZ * sin - dirX * cos;

        float tumbleX = stream.nextFloat() * 2.0F - 1.0F;
        float tumbleY = (stream.nextFloat() * 2.0F - 1.0F) * 0.35F;
        float tumbleZ = stream.nextFloat() * 2.0F - 1.0F;
        float length = (float) Math.sqrt(tumbleX * tumbleX + tumbleY * tumbleY + tumbleZ * tumbleZ);
        if (length < 0.01F) {
            tumbleX = 1.0F;
            length = 1.0F;
        }
        tumbleAxis[plate * 3] = tumbleX / length;
        tumbleAxis[plate * 3 + 1] = tumbleY / length;
        tumbleAxis[plate * 3 + 2] = tumbleZ / length;
        // Most turn little; a few go right over.
        float tumbleShare = stream.nextFloat();
        tumbleAngle[plate] = (MIN_TUMBLE + tumbleShare * tumbleShare * (MAX_TUMBLE - MIN_TUMBLE))
                * (stream.nextFloat() < 0.5F ? -1.0F : 1.0F);

        double turn = (stream.nextDouble() * 2.0D - 1.0D) * FLIGHT_TURN_MOST;
        flightX[plate] = (float) (dirX * Math.cos(turn) - dirZ * Math.sin(turn));
        flightZ[plate] = (float) (dirX * Math.sin(turn) + dirZ * Math.cos(turn));
        // One read of the field, which the far-terrain add-on's ShatterPlanLawMixin redirects until it builds with createFar.
        int ownRange = range;
        flightReach[plate] = (FLIGHT_PER_RANGE * (lawRange > 0 ? lawRange : ownRange) + FLIGHT_PER_BLOCK * flightDistance)
                * (FLIGHT_SHARE_MIN + stream.nextFloat() * (FLIGHT_SHARE_MAX - FLIGHT_SHARE_MIN));
        flightDelay[plate] = stream.nextFloat() * FLIGHT_DELAY_MOST;
        flightUp[plate] = stream.nextFloat() * FLIGHT_UP_MOST;

        float radius = 0.0F;
        for (int i = 0; i < corners; i++) {
            radius = Math.max(radius, (float) Math.hypot(piece.x[i] - centerX, piece.z[i] - centerZ));
        }
        plateRadius[plate] = radius;
        tearSeed[plate] = stream.nextLong();
        float stripTurn = (stream.nextFloat() - 0.5F) * 1.0F;
        // The strip's long axis: across the way out, more or less.
        tearAlongX[plate] = -dirZ * (float) Math.cos(stripTurn) - dirX * (float) Math.sin(stripTurn);
        tearAlongZ[plate] = dirX * (float) Math.cos(stripTurn) - dirZ * (float) Math.sin(stripTurn);
        tearGrain[plate] = Math.max(TEAR_GRAIN_MIN, Math.min(TEAR_GRAIN_MAX, (float) Math.sqrt(piece.area()) * TEAR_GRAIN_SHARE));
    }

    // The plates are sorted by distance: a plate waits for all that lie well inside it, however late their cracks came.
    private void innerPlatesLead() {
        int inner = 0;
        float latestInner = Float.NEGATIVE_INFINITY;
        for (int plate = 0; plate < plateCount; plate++) {
            while (plateDistance[inner] < plateDistance[plate] - LEADS_BY_BLOCKS) {
                latestInner = Math.max(latestInner, startTick[inner]);
                inner++;
            }
            startTick[plate] = Math.max(startTick[plate], latestInner + LEAD_TICKS);
        }
    }

    private void timeTheTear(int plate) {
        Stream stream = new Stream(mix(seed ^ (plate * GOLDEN + 0x2127599BF4325C37L)));
        int ownRange = range;
        float far = smooth(plateDistance[plate] / ((lawRange > 0 ? lawRange : ownRange) * TEAR_FAR_AT_RANGE));
        float from = TEAR_NEAR_FROM + (TEAR_FAR_FROM - TEAR_NEAR_FROM) * far;
        float spread = TEAR_NEAR_SPREAD + (TEAR_FAR_SPREAD - TEAR_NEAR_SPREAD) * far;
        float start = Math.max(from + stream.nextFloat() * spread, startTick[plate] + TEAR_AFTER_START_TICKS);
        float most = Math.min(TEAR_TICKS_MAX, END_TICK - start);
        tearStart[plate] = start;
        tearTicks[plate] = TEAR_TICKS_MIN + stream.nextFloat() * (most - TEAR_TICKS_MIN);
    }

    // However the dice fall, no short while takes many plates: ends that come too close move apart, by a tick or two at most.
    private void spreadTheEnds() {
        int[] order = new int[plateCount];
        float[] ends = new float[plateCount];
        for (int plate = 0; plate < plateCount; plate++) {
            order[plate] = plate;
        }
        // insertion sort: the plates are few, and this runs once, off the render thread
        for (int i = 1; i < plateCount; i++) {
            int plate = order[i];
            float end = tearStart[plate] + tearTicks[plate];
            int at = i;
            while (at > 0 && tearStart[order[at - 1]] + tearTicks[order[at - 1]] > end) {
                order[at] = order[at - 1];
                at--;
            }
            order[at] = plate;
        }
        float gap = ENDS_APART_TICKS / Math.max(1, (int) (plateCount * ENDS_APART_SHARE));
        for (int i = 0; i < plateCount; i++) {
            float end = tearStart[order[i]] + tearTicks[order[i]];
            ends[i] = i == 0 ? end : Math.max(end, ends[i - 1] + gap);
        }
        for (int i = plateCount - 1; i >= 0; i--) {
            ends[i] = i == plateCount - 1 ? Math.min(ends[i], END_TICK) : Math.min(ends[i], ends[i + 1] - gap);
            tearTicks[order[i]] = ends[i] - tearStart[order[i]];
        }
        if (range == 2048) {
            int[] matched = matchFarRetirementSlots(order, ends, plateDistance, startTick, tearStart);
            if (matched == null) throw new IllegalStateException("No compatible retirement slots for the far shatter plan");
            for (int i = 0; i < plateCount; i++) {
                if (matched[i] != order[i]) tearTicks[matched[i]] = ends[i] - tearStart[matched[i]];
            }
        }
    }

    /** Matches existing ascending retirement slots without changing near slots or any launch/tear onset. */
    public static int[] matchFarRetirementSlots(int[] order, float[] ends, float[] distances, float[] starts, float[] tears) {
        int[] far = new int[order.length];
        int count = 0;
        boolean needsMatching = false;
        for (int i = 0; i < order.length; i++) {
            int plate = order[i];
            float life = ends[i] - starts[plate];
            boolean compatible = life >= MIN_LIFE_TICKS && life <= MAX_LIFE_TICKS && ends[i] > tears[plate];
            if (distances[plate] < DEFAULT_RANGE) {
                if (!compatible) return null;
            } else {
                far[count++] = i;
                needsMatching |= !compatible;
            }
        }
        if (!needsMatching) return order;

        int[] minimum = new int[count];
        int[] maximum = new int[count];
        for (int p = 0; p < count; p++) {
            int plate = order[far[p]];
            float oldTear = ends[far[p]] - tears[plate];
            float minTear = Math.min(oldTear, TEAR_TICKS_MIN);
            float maxTear = Math.max(oldTear, TEAR_TICKS_MAX);
            minimum[p] = count;
            maximum[p] = -1;
            for (int slot = 0; slot < count; slot++) {
                float life = ends[far[slot]] - starts[plate];
                float tear = ends[far[slot]] - tears[plate];
                if (life >= MIN_LIFE_TICKS && life <= MAX_LIFE_TICKS && tear > 0.0F && tear >= minTear && tear <= maxTear) {
                    minimum[p] = Math.min(minimum[p], slot);
                    maximum[p] = slot;
                }
            }
        }

        int[] assigned = new int[count];
        for (int slot = 0; slot < count; slot++) assigned[slot] = slot;
        boolean matchedBySwaps = true;
        for (int slot = 0; slot < count; slot++) {
            int p = assigned[slot];
            if (slot >= minimum[p] && slot <= maximum[p]) continue;
            int later = slot + 1;
            for (; later < count; later++) {
                int other = assigned[later];
                if (later < minimum[p] || later > maximum[p] || slot < minimum[other] || slot > maximum[other]
                        || ends[far[later]] - tears[order[far[p]]] > TEAR_TICKS_MAX
                        || ends[far[slot]] - tears[order[far[other]]] < TEAR_TICKS_MIN) continue;
                assigned[slot] = other;
                assigned[later] = p;
                break;
            }
            if (later == count) {
                matchedBySwaps = false;
                break;
            }
        }

        if (!matchedBySwaps) {
            // Reassign from the untouched slots. At the latest slot, prefer the largest eligible minimum.
            // In any solution the displaced plate can take its earlier slot: its minimum is no greater.
            boolean[] used = new boolean[count];
            for (int slot = count - 1; slot >= 0; slot--) {
                int best = -1;
                for (int p = 0; p < count; p++) {
                    if (used[p] || slot < minimum[p] || slot > maximum[p]) continue;
                    if (best < 0 || minimum[p] > minimum[best] || minimum[p] == minimum[best] && p == slot) best = p;
                }
                if (best < 0) return null;
                assigned[slot] = best;
                used[best] = true;
            }
        }
        int[] matched = order.clone();
        for (int slot = 0; slot < count; slot++) matched[far[slot]] = order[far[assigned[slot]]];
        return matched;
    }

    private void keepFromTheFarPlane(int plate) {
        float lastAge = retireTick(plate) - startTick[plate];
        float room = FARTHEST_RANGES * range - plateDistance[plate] - plateRadius[plate] - outReach[plate] * travel(lastAge);
        float flownAtTheEnd = flight(lastAge - flightDelay[plate]);
        if (flownAtTheEnd > 0.0F) {
            flightReach[plate] = Math.min(flightReach[plate], Math.max(0.0F, room) / flownAtTheEnd);
        }
    }

    public int plateCount() {
        return plateCount;
    }

    public int range() {
        return range;
    }

    public float maximumHorizontalReach() {
        return FARTHEST_RANGES * range;
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public List<Cut> cuts() {
        return cuts;
    }

    /** The plate owning a column, or -1 beyond the outer edge. */
    public int plateAt(int x, int z) {
        return plateAt((double) (x - originX), (double) (z - originZ));
    }

    /** The plate at a place given relative to the origin, or -1 beyond the outer edge. */
    public int plateAt(double dx, double dz) {
        int wedge = wedgeIndex(dx, dz);
        double[] outer = wedgeOuter[wedge];
        if (outer[0] * dx + outer[1] * dz > outer[2]) {
            return -1;
        }
        Node node = wedges[wedge];
        while (node.piece == null) {
            node = node.normalX * dx + node.normalZ * dz >= node.offset ? node.positive : node.negative;
        }
        return node.plate;
    }

    /** The corners of a plate's outline relative to the origin, counter-clockwise when x is right and z up. */
    public double[] polygonX(int plate) {
        return polygonX[plate];
    }

    public double[] polygonZ(int plate) {
        return polygonZ[plate];
    }

    /** Where a plate's motion and rotation are measured from, in world blocks. */
    public float pivotX(int plate) {
        return pivotX[plate];
    }

    public float pivotZ(int plate) {
        return pivotZ[plate];
    }

    public float plateDistance(int plate) {
        return plateDistance[plate];
    }

    /** The tick of the erase at which the plate starts to move: very slowly, and it never stops afterwards. */
    public float startTick(int plate) {
        return startTick[plate];
    }

    public float plateRadius(int plate) {
        return plateRadius[plate];
    }

    /** The tick of the erase by which the plate has torn away and nothing of it is left: its own for each plate. */
    public float retireTick(int plate) {
        return tearStart[plate] + tearTicks[plate];
    }

    /** The tick of the erase at which a plate's first columns go. */
    public float tearStartTick(int plate) {
        return tearStart[plate];
    }

    /** The share of a plate's quads that have gone at a tick: 0 until its tear starts, 1 at its retire tick. */
    public float tearShare(int plate, float tick) {
        return smooth((tick - tearStart[plate]) / tearTicks[plate]);
    }

    /** What a plate is scaled by at a tick: 1 until the last of its tear, which it spends shrinking to nothing. */
    public float tearScale(int plate, float tick) {
        float rest = (tearStart[plate] + tearTicks[plate] - tick) / (tearTicks[plate] * TEAR_SHRINK_SHARE);
        return smooth(rest);
    }

    /**
     * When a column of a plate goes, relative to the plate's other columns: the higher, the sooner. A low-frequency noise of
     * the place, so that clusters and strips go together, and a little that is the column's own. All in 0 to about 1.
     */
    public float tearOrder(int plate, int dx, int dz) {
        float grain = tearGrain[plate];
        float alongX = tearAlongX[plate];
        float alongZ = tearAlongZ[plate];
        float across = (-alongZ * dx + alongX * dz) / grain;
        float along = (alongX * dx + alongZ * dz) / (grain * TEAR_STRIP_STRETCH);
        long key = tearSeed[plate];
        float coarse = valueNoise(key, along, across);
        float fine = valueNoise(~key, along * 2.3F + 11.0F, across * 2.3F + 5.0F);
        float own = hashFloat(key ^ 0x5A5A5A5A5A5A5A5AL, dx, dz);
        return (coarse * (1.0F - TEAR_FINE_SHARE) + fine * TEAR_FINE_SHARE) * (1.0F - TEAR_COLUMN_SHARE) + own * TEAR_COLUMN_SHARE;
    }

    /** The tick by which the lines of a plate's outline have run past all its corners. */
    public float latestArrival(int plate) {
        return latestArrival[plate];
    }

    /** The ticks the fastest head takes to run a distance. */
    public float headAge(double distance) {
        return headAge(distance, CREST_REACH_MAX * CRACK_LEAD, raceFor(CREST_REACH_MAX * CRACK_LEAD, edgeReach));
    }

    /** The unit vector of the way the user faced at the press, relative to the origin. */
    public double facingX() {
        return facingX;
    }

    public double facingZ() {
        return facingZ;
    }

    private static float raceFor(float near, float edgeReach) {
        float race = HEAD_TICKS_TO_EDGE - CRACK_RACES_FROM_TICK;
        float kept = near * (1.0F - (float) Math.exp(-HEAD_TICKS_TO_EDGE / HEAD_TAU_TICKS));
        return Math.max(LEAST_RACE, (edgeReach - kept) / (race * race));
    }

    private static float headDistance(float age, float near, float race) {
        if (age <= 0.0F) {
            return 0.0F;
        }
        float racing = Math.max(0.0F, age - CRACK_RACES_FROM_TICK);
        return near * (1.0F - (float) Math.exp(-age / HEAD_TAU_TICKS)) + race * racing * racing;
    }

    // The law is smooth and only grows, and the race gets it past any distance in time.
    private static float headAge(double distance, float near, float race) {
        float low = 0.0F;
        float high = 256.0F;
        for (int i = 0; i < 26; i++) {
            float middle = (low + high) * 0.5F;
            if (headDistance(middle, near, race) < distance) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return high;
    }

    /** How far a crest's head has run a number of ticks after it started: it leaves fast and keeps going. */
    public static float crestHead(float reach, float age) {
        return reach * (1.0F - (float) Math.exp(-age / HEAD_TAU_TICKS));
    }

    /** Where a crest's tail is: at the user's feet until the head is its length away, then it lets go and runs after it. */
    public static float crestTail(float head, float length) {
        return Math.max(FEET_CLEAR_BLOCKS, head - length);
    }

    /** A rising block's height a number of ticks after it left the ground: slow, and slowly faster. */
    public static float debrisRise(float speed, float age) {
        return debrisRise(speed, DEBRIS_RISE_GROWTH, age);
    }

    /** The same with a block's own growth: {@link #DEBRIS_RISE_GROWTH} or {@link #LOOSE_RISE_GROWTH}. */
    public static float debrisRise(float speed, float growth, float age) {
        return speed * age + growth * age * age;
    }

    /** How much of its drift speed a small block has run a number of ticks after it left: slowly faster. */
    public static float debrisDrift(float age) {
        return age + DEBRIS_DRIFT_GROWTH * age * age;
    }

    /** When a small block that may leave from {@code earliest} on does leave; {@code fraction} is its random 0 to 1. */
    public static float debrisSpawnTick(float earliest, float fraction) {
        float from = Math.max(DEBRIS_FIRST_TICK, Math.min(earliest, DEBRIS_LAST_TICK));
        return from + (DEBRIS_LAST_TICK - from) * fraction;
    }

    /** When a small block has gone; {@code fraction} is its random 0 to 1, and a nearer one goes later. */
    public static float debrisEndTick(float spawn, float distance, float fraction) {
        float near = 1.0F - Math.min(1.0F, distance / DEBRIS_NEAR_BLOCKS);
        float share = near * DEBRIS_NEAR_SHARE + fraction * (1.0F - DEBRIS_NEAR_SHARE);
        float end = DEBRIS_FIRST_END_TICK + (END_TICK - DEBRIS_FIRST_END_TICK) * share;
        return Math.min(END_TICK, Math.max(end, spawn + DEBRIS_LEAST_LIFE_TICKS));
    }

    /** A rising block's size as a share of its own: it grows in quickly and shrinks away by its own end. */
    public static float debrisScale(float age, float tick, float end) {
        return Math.max(0.0F, Math.min(1.0F, age * 0.25F)) * smooth((end - tick) / DEBRIS_SHRINK_TICKS);
    }

    /** The share of its way a plate has gone at an age: 0 at its start, and its speed only grows from there. */
    private static float travel(float age) {
        return (float) Math.pow(age / TRAVEL_REFERENCE_TICKS, TRAVEL_POWER);
    }

    /** The share of its flight a plate has flown: nothing for a while, and then it runs away. */
    private static float flight(float age) {
        return age <= 0.0F ? 0.0F : (float) Math.pow(age / FLIGHT_REFERENCE_TICKS, FLIGHT_POWER);
    }

    /** Fills {@code pose} for a plate at a tick of the erase (with partial ticks). */
    public void pose(int plate, float tick, Pose pose) {
        pose.setIdentity();
        float age = tick - startTick[plate];
        if (age <= 0.0F) {
            return;
        }
        float way = travel(age);
        float flown = flightReach[plate] * flight(age - flightDelay[plate]);
        float outward = outReach[plate] * way;
        pose.dx = outX[plate] * outward + flightX[plate] * flown;
        pose.dz = outZ[plate] * outward + flightZ[plate] * flown;
        pose.dy = upReach[plate] * way + flightUp[plate] * flown;
        pose.rotate(
                tiltAxis[plate * 3], tiltAxis[plate * 3 + 1], tiltAxis[plate * 3 + 2], tiltAngle[plate] * way,
                tumbleAxis[plate * 3], tumbleAxis[plate * 3 + 1], tumbleAxis[plate * 3 + 2], tumbleAngle[plate] * way);
        pose.scale = tearScale(plate, tick);
    }

    /** One dead straight crack, relative to the origin. Its head leaves the start at the start tick and runs to the end. */
    public static final class Cut {
        public final double startX;
        public final double startZ;
        public final double dirX;
        public final double dirZ;
        public final double length;
        public final float startTick;
        public final int kind;
        public final long key;
        /** How far the crest on this line runs. */
        public final float crestReach;
        private final float near;
        private final float race;

        private Cut(
                double startX,
                double startZ,
                double dirX,
                double dirZ,
                double length,
                float startTick,
                int kind,
                long key,
                float crestReach,
                float edgeReach) {
            this.startX = startX;
            this.startZ = startZ;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.length = length;
            this.startTick = startTick;
            this.kind = kind;
            this.key = key;
            this.crestReach = crestReach;
            this.near = crestReach * CRACK_LEAD;
            this.race = raceFor(this.near, edgeReach);
        }

        public float arrivalAt(double along) {
            return startTick + headAge(along, near, race);
        }

        /** How far the head of this cut has run at a number of ticks after its start. */
        public float headDistance(float age) {
            return KingCrimsonShatterPlan.headDistance(age, near, race);
        }

        private float arrivalAtPoint(double x, double z) {
            return arrivalAt(Math.max(0.0D, (x - startX) * dirX + (z - startZ) * dirZ));
        }
    }

    /** A plate's displacement, rotation and scale about its pivot. */
    public static final class Pose {
        public float dx;
        public float dy;
        public float dz;
        public float m00;
        public float m01;
        public float m02;
        public float m10;
        public float m11;
        public float m12;
        public float m20;
        public float m21;
        public float m22;
        public float scale;

        public Pose() {
            setIdentity();
        }

        void setIdentity() {
            dx = 0.0F;
            dy = 0.0F;
            dz = 0.0F;
            m00 = 1.0F;
            m01 = 0.0F;
            m02 = 0.0F;
            m10 = 0.0F;
            m11 = 1.0F;
            m12 = 0.0F;
            m20 = 0.0F;
            m21 = 0.0F;
            m22 = 1.0F;
            scale = 1.0F;
        }

        public boolean isIdentity() {
            return dx == 0.0F && dy == 0.0F && dz == 0.0F && scale == 1.0F
                    && m00 == 1.0F && m01 == 0.0F && m02 == 0.0F
                    && m10 == 0.0F && m11 == 1.0F && m12 == 0.0F
                    && m20 == 0.0F && m21 == 0.0F && m22 == 1.0F;
        }

        // The second rotation about the unit axis b, applied after the first about a.
        private void rotate(
                float ax, float ay, float az, float aAngle,
                float bx, float by, float bz, float bAngle) {
            float ac = (float) Math.cos(aAngle);
            float as = (float) Math.sin(aAngle);
            float at = 1.0F - ac;
            float a00 = at * ax * ax + ac;
            float a01 = at * ax * ay - as * az;
            float a02 = at * ax * az + as * ay;
            float a10 = at * ax * ay + as * az;
            float a11 = at * ay * ay + ac;
            float a12 = at * ay * az - as * ax;
            float a20 = at * ax * az - as * ay;
            float a21 = at * ay * az + as * ax;
            float a22 = at * az * az + ac;
            float bc = (float) Math.cos(bAngle);
            float bs = (float) Math.sin(bAngle);
            float bt = 1.0F - bc;
            float b00 = bt * bx * bx + bc;
            float b01 = bt * bx * by - bs * bz;
            float b02 = bt * bx * bz + bs * by;
            float b10 = bt * bx * by + bs * bz;
            float b11 = bt * by * by + bc;
            float b12 = bt * by * bz - bs * bx;
            float b20 = bt * bx * bz - bs * by;
            float b21 = bt * by * bz + bs * bx;
            float b22 = bt * bz * bz + bc;
            m00 = b00 * a00 + b01 * a10 + b02 * a20;
            m01 = b00 * a01 + b01 * a11 + b02 * a21;
            m02 = b00 * a02 + b01 * a12 + b02 * a22;
            m10 = b10 * a00 + b11 * a10 + b12 * a20;
            m11 = b10 * a01 + b11 * a11 + b12 * a21;
            m12 = b10 * a02 + b11 * a12 + b12 * a22;
            m20 = b20 * a00 + b21 * a10 + b22 * a20;
            m21 = b20 * a01 + b21 * a11 + b22 * a21;
            m22 = b20 * a02 + b21 * a12 + b22 * a22;
        }
    }

    /**
     * How much of the universe still covers the returned world this many seconds after an erase ended, on a pack whose exposure has
     * to come back from the dark: nearly whole at first, while the pack still overexposes, then eased out by
     * {@link #RETURN_FADE_SECONDS}.
     */
    public static float returnOpacity(float seconds) {
        if (!(seconds > 0.0F)) return 1.0F;
        return 1.0F - smooth(seconds / RETURN_FADE_SECONDS);
    }

    /** Hermite ease of 0 to 1, clamped. */
    public static float smooth(float value) {
        float clamped = value <= 0.0F ? 0.0F : Math.min(value, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static double wrap(double angle) {
        double wrapped = angle % TWO_PI;
        return wrapped < 0.0D ? wrapped + TWO_PI : wrapped;
    }

    private static int[] shuffled(int count, Stream stream) {
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        for (int i = count - 1; i > 0; i--) {
            int other = stream.nextInt(i + 1);
            int kept = order[i];
            order[i] = order[other];
            order[other] = kept;
        }
        return order;
    }

    private static long mix(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static float hashFloat(long key, int i, int j) {
        return (mix(key + i * GOLDEN + j * 0xC2B2AE3D27D4EB4FL) >>> 40) * 0x1.0p-24F;
    }

    private static float valueNoise(long key, float u, float v) {
        int i = (int) Math.floor(u);
        int j = (int) Math.floor(v);
        float fu = smooth(u - i);
        float fv = smooth(v - j);
        float a = hashFloat(key, i, j);
        float b = hashFloat(key, i + 1, j);
        float c = hashFloat(key, i, j + 1);
        float d = hashFloat(key, i + 1, j + 1);
        return (a + (b - a) * fu) * (1.0F - fv) + (c + (d - c) * fu) * fv;
    }

    private record Branch(float tick, int ray, double along, int side, double turn) {}

    private static final class Node {
        private Piece piece;
        private int plate = -1;
        private double normalX;
        private double normalZ;
        private double offset;
        private Node positive;
        private Node negative;
    }

    // A convex piece of the plane, relative to the origin. Edge i runs from corner i to the next and lies on a cut.
    private static final class Piece {
        private final double[] x;
        private final double[] z;
        private final int[] edgeCut;
        private final double area;
        private final double centerX;
        private final double centerZ;
        private Node node;

        private Piece(double[] x, double[] z, int[] edgeCut) {
            this.x = x;
            this.z = z;
            this.edgeCut = edgeCut;
            double twiceArea = 0.0D;
            double sumX = 0.0D;
            double sumZ = 0.0D;
            for (int i = 0; i < x.length; i++) {
                int next = (i + 1) % x.length;
                double cross = x[i] * z[next] - x[next] * z[i];
                twiceArea += cross;
                sumX += (x[i] + x[next]) * cross;
                sumZ += (z[i] + z[next]) * cross;
            }
            double safe = Math.abs(twiceArea) < 1.0E-9D ? 1.0E-9D : twiceArea;
            area = Math.abs(twiceArea) * 0.5D;
            centerX = sumX / (3.0D * safe);
            centerZ = sumZ / (3.0D * safe);
        }

        private double area() {
            return area;
        }

        private double centerX() {
            return centerX;
        }

        private double centerZ() {
            return centerZ;
        }

        private double distance() {
            return Math.sqrt(centerX * centerX + centerZ * centerZ);
        }

        private double longestEdge() {
            double longest = 1.0E-6D;
            for (int i = 0; i < x.length; i++) {
                int next = (i + 1) % x.length;
                longest = Math.max(longest, Math.hypot(x[next] - x[i], z[next] - z[i]));
            }
            return longest;
        }
    }

    private static final class Stream {
        private long state;

        private Stream(long state) {
            this.state = state;
        }

        private long nextLong() {
            state += GOLDEN;
            return mix(state);
        }

        private double nextDouble() {
            return (nextLong() >>> 11) * 0x1.0p-53D;
        }

        private float nextFloat() {
            return (float) nextDouble();
        }

        private int nextInt(int bound) {
            return (int) (nextDouble() * bound);
        }
    }
}
