package rotp.addons.kingcrimson.shatter;

import java.nio.FloatBuffer;
import java.util.Arrays;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;

/**
 * The outlines an eraser needs around him, kept by chunk section. A section is read once and stays cached as he walks, so a step costs
 * nothing but the sections that come into reach, nearest first and within a budget per frame; everything reads from one array in the
 * order of distance, so that the drawing can hold it all in one buffer.
 *
 * <p>The reach follows the speed of an erase: 200 ticks at twice a player's speed is 86 blocks walking and 112 sprinting, 11.2 blocks a
 * second. Fading from 19 to 32 blocks, the outlines show what is coming two to three seconds before the user meets it. Upward less: a
 * block far overhead stops nobody, and a forest's crowns would bury the trunks that do.
 */
public final class KingCrimsonTerrainOutlineField {
    public static final int RADIUS = 32;
    public static final int REACH_UP = 8;
    public static final int REACH_DOWN = 8;
    // Far sections go first if a terrain ever holds more: 1.7 MB of buffer.
    public static final int MOST_SEGMENTS = 60_000;
    private static final int SIDE = SectionPos.SECTION_SIZE;

    // The faint lines of a floor (owner, 2026-11-11: kept, wider, and graded so that a player can read the depth). Every floor the scan
    // holds under the feet has them; the one stood on is clearly the brightest, one a block or two down plainly dimmer, the bottom of a
    // pit 6 to 8 down faint, and the colour goes a deeper red with depth. Across, full near the feet and gone well inside the reach.
    // time_erase_outline.fsh holds the same numbers (a test compares them).
    /** The lowest floor with lines, counted from 1 for the floor stood on: its top lies REACH_DOWN under the feet. */
    public static final int FLOOR_LINE_LOWEST = REACH_DOWN + 1;
    /** One floor down from the one stood on, this much of its light. */
    public static final float FLOOR_LINE_STEP = 0.62F;
    /** And each floor further down this much of the one above it. */
    public static final float FLOOR_LINE_DIMS = 0.84F;
    public static final float FLOOR_LINE_FULL_ACROSS = 6.0F;
    public static final float FLOOR_LINE_GONE_ACROSS = 24.0F;
    /** A floor line is never brighter than this share of an edge's line at the same place: an edge always wins over it. */
    public static final float FLOOR_LINE_UNDER_EDGE = 0.8F;

    /** Segments of six floats, x0 y0 z0 x1 y1 z1, relative to the feet. What the shader shows, in the same words. */
    public record Result(float[] upper, float[] under) {
        public int upperCount() {
            return upper.length / 6;
        }

        public int underCount() {
            return under.length / 6;
        }
    }

    private final Long2ObjectOpenHashMap<KingCrimsonTerrainOutlineScan.Section> scanned = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet inBuffer = new LongOpenHashSet();
    private long[] wanted = new long[0];
    private BlockPos origin;
    private BlockPos seenFeet;
    private int revalidateAt;
    private boolean changed;
    private boolean complete;
    private int reads;
    private int rereads;
    private long readNanos;
    private long worstReadNanos;
    private long worstFrameNanos;
    private int mostSegmentsHeld;
    private int mostFacesHeld;
    private int facesWritten;

    public BlockPos origin() {
        return origin;
    }

    /** True when the array differs from what was last written: the buffer is out of date. */
    public boolean changed() {
        return changed;
    }

    /** True when every section in reach has been read. */
    public boolean complete() {
        return complete;
    }

    /** Reads what came into reach. At least one section a call, so that a slow frame still moves on, then up to the budget. */
    public void update(BlockGetter level, BlockPos feet, boolean erasing, long budgetNanos) {
        if (!erasing) return;
        if (origin == null) origin = feet.immutable();
        if (!feet.equals(seenFeet)) {
            boolean crossed = seenFeet == null || SectionPos.asLong(feet) != SectionPos.asLong(seenFeet);
            seenFeet = feet.immutable();
            rememberWanted(feet);
            if (crossed) forgetFarSections(feet);
        }
        long started = System.nanoTime();
        boolean done = true;
        boolean readOne = false;
        for (long section : wanted) {
            if (scanned.containsKey(section)) continue;
            if (readOne && System.nanoTime() - started >= budgetNanos) {
                done = false;
                break;
            }
            KingCrimsonTerrainOutlineScan.Section read = read(level, section);
            if (read == null) {
                done = false;
                continue;
            }
            readOne = true;
            scanned.put(section, read);
            if (!read.isEmpty()) changed = true;
        }
        complete = done;
        worstFrameNanos = Math.max(worstFrameNanos, System.nanoTime() - started);
    }

    private KingCrimsonTerrainOutlineScan.Section read(BlockGetter level, long section) {
        long started = System.nanoTime();
        KingCrimsonTerrainOutlineScan.Section segments = KingCrimsonTerrainOutlineScan.scanSection(
                level, SectionPos.x(section), SectionPos.y(section), SectionPos.z(section), origin);
        if (segments != null) {
            long took = System.nanoTime() - started;
            reads++;
            readNanos += took;
            worstReadNanos = Math.max(worstReadNanos, took);
        }
        return segments;
    }

    /** One section read again, in turn: a block placed in the erase by someone else shows within seconds. Once a tick is enough. */
    public void revalidateOne(BlockGetter level) {
        if (wanted.length == 0 || !complete) return;
        revalidateAt = (revalidateAt + 1) % wanted.length;
        long section = wanted[revalidateAt];
        KingCrimsonTerrainOutlineScan.Section old = scanned.get(section);
        if (old == null) return;
        KingCrimsonTerrainOutlineScan.Section now = read(level, section);
        rereads++;
        if (now != null && (!Arrays.equals(old.edges(), now.edges()) || !Arrays.equals(old.faces(), now.faces()))) {
            scanned.put(section, now);
            changed = true;
        }
    }

    private void rememberWanted(BlockPos feet) {
        int minX = SectionPos.blockToSectionCoord(feet.getX() - RADIUS);
        int maxX = SectionPos.blockToSectionCoord(feet.getX() + RADIUS);
        int minZ = SectionPos.blockToSectionCoord(feet.getZ() - RADIUS);
        int maxZ = SectionPos.blockToSectionCoord(feet.getZ() + RADIUS);
        int minY = SectionPos.blockToSectionCoord(feet.getY() - REACH_DOWN);
        int maxY = SectionPos.blockToSectionCoord(feet.getY() + REACH_UP);
        long[] found = new long[(maxX - minX + 1) * (maxZ - minZ + 1) * (maxY - minY + 1)];
        long[] order = new long[found.length];
        int count = 0;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                long nearest = nearestSquared(feet.getX(), SectionPos.sectionToBlockCoord(x))
                        + nearestSquared(feet.getZ(), SectionPos.sectionToBlockCoord(z));
                if (nearest > (long) RADIUS * RADIUS) continue;
                for (int y = minY; y <= maxY; y++) {
                    long ownDistance = nearest + nearestSquared(feet.getY(), SectionPos.sectionToBlockCoord(y));
                    // The nearest first: the top bits sort, the low bits say where it was.
                    order[count] = ownDistance << 20 | count;
                    found[count++] = SectionPos.asLong(x, y, z);
                }
            }
        }
        Arrays.sort(order, 0, count);
        long[] next = new long[count];
        for (int i = 0; i < count; i++) {
            next[i] = found[(int) (order[i] & 0xFFFFF)];
            // Back in reach, in the cache, but not in the buffer.
            KingCrimsonTerrainOutlineScan.Section cached = scanned.get(next[i]);
            if (cached != null && !cached.isEmpty() && !inBuffer.contains(next[i])) changed = true;
        }
        wanted = next;
    }

    private static long nearestSquared(int block, int sectionStart) {
        long away = Math.max(Math.max(sectionStart - block, block - (sectionStart + SIDE)), 0);
        return away * away;
    }

    // A section just out of reach stays a while: the user may turn back at its edge.
    private void forgetFarSections(BlockPos feet) {
        long horizontal = (long) (RADIUS + SIDE) * (RADIUS + SIDE);
        var entries = scanned.long2ObjectEntrySet().fastIterator();
        while (entries.hasNext()) {
            long section = entries.next().getLongKey();
            int y = SectionPos.sectionToBlockCoord(SectionPos.y(section));
            long nearest = nearestSquared(feet.getX(), SectionPos.sectionToBlockCoord(SectionPos.x(section)))
                    + nearestSquared(feet.getZ(), SectionPos.sectionToBlockCoord(SectionPos.z(section)));
            if (nearest > horizontal || y + SIDE < feet.getY() - REACH_DOWN - SIDE || y > feet.getY() + REACH_UP + SIDE) {
                entries.remove();
                inBuffer.remove(section);
            }
        }
    }

    /** How many floats {@link #write} needs at most for the edges. */
    public int edgeFloatsToWrite() {
        long total = 0;
        for (long section : wanted) {
            KingCrimsonTerrainOutlineScan.Section read = scanned.get(section);
            if (read != null) total += read.edges().length;
        }
        return (int) Math.min(total, (long) MOST_SEGMENTS * KingCrimsonTerrainOutlineScan.STRIDE);
    }

    /** How many floats {@link #write} needs at most for the faces. */
    public int faceFloatsToWrite() {
        long total = 0;
        for (long section : wanted) {
            KingCrimsonTerrainOutlineScan.Section read = scanned.get(section);
            if (read != null) total += read.faces().length;
        }
        return (int) Math.min(total, (long) MOST_SEGMENTS * KingCrimsonTerrainOutlineScan.STRIDE);
    }

    /** Puts all it holds into the two buffers, nearest first. @return the edges written; {@link #facesWritten} says the faces */
    public int write(FloatBuffer edges, FloatBuffer faces) {
        changed = false;
        inBuffer.clear();
        int edgeRoom = MOST_SEGMENTS * KingCrimsonTerrainOutlineScan.STRIDE;
        int faceRoom = edgeRoom;
        for (long section : wanted) {
            KingCrimsonTerrainOutlineScan.Section read = scanned.get(section);
            if (read == null || read.isEmpty()) continue;
            if (read.edges().length > edgeRoom || read.faces().length > faceRoom) break;
            edges.put(read.edges());
            faces.put(read.faces());
            edgeRoom -= read.edges().length;
            faceRoom -= read.faces().length;
            inBuffer.add(section);
        }
        int written = MOST_SEGMENTS - edgeRoom / KingCrimsonTerrainOutlineScan.STRIDE;
        facesWritten = MOST_SEGMENTS - faceRoom / KingCrimsonTerrainOutlineScan.STRIDE;
        mostSegmentsHeld = Math.max(mostSegmentsHeld, written);
        mostFacesHeld = Math.max(mostFacesHeld, facesWritten);
        return written;
    }

    public int facesWritten() {
        return facesWritten;
    }

    /** What the shader will do with the array, for a user standing at {@code feet}. */
    public Result classified(BlockPos feet) {
        if (origin == null) return new Result(new float[0], new float[0]);
        FloatBuffer all = FloatBuffer.allocate(edgeFloatsToWrite());
        int segments = write(all, FloatBuffer.allocate(faceFloatsToWrite()));
        float[] upper = new float[segments * 12];
        float[] under = new float[segments * 12];
        int upperUsed = 0;
        int underUsed = 0;
        float dx = origin.getX() - feet.getX();
        float dy = origin.getY() - feet.getY();
        float dz = origin.getZ() - feet.getZ();
        for (int i = 0; i < segments * KingCrimsonTerrainOutlineScan.STRIDE; i += KingCrimsonTerrainOutlineScan.STRIDE) {
            float x0 = all.get(i) + dx;
            float y0 = all.get(i + 1) + dy;
            float z0 = all.get(i + 2) + dz;
            float x1 = all.get(i + 3) + dx;
            float y1 = all.get(i + 4) + dy;
            float z1 = all.get(i + 5) + dz;
            float height = all.get(i + 6);
            float own = (float) Math.floor(height) + dy;
            boolean floorLine = height - Math.floor(height) > 0.25F;
            if (x0 == x1 && z0 == z1) {
                float low = Math.min(y0, y1);
                float high = Math.max(y0, y1);
                if (high > 0.0F) {
                    System.arraycopy(new float[] {x0, Math.max(low, 0.0F), z0, x1, high, z1}, 0, upper, upperUsed, 6);
                    upperUsed += 6;
                }
                if (low < 0.0F) {
                    System.arraycopy(new float[] {x0, low, z0, x1, Math.min(high, 0.0F), z1}, 0, under, underUsed, 6);
                    underUsed += 6;
                }
            } else if (floorLine) {
                if (own < 0.0F && -own <= floorLineDepth()) {
                    System.arraycopy(new float[] {x0, y0, z0, x1, y1, z1}, 0, under, underUsed, 6);
                    underUsed += 6;
                }
            } else if (own >= 0.0F) {
                System.arraycopy(new float[] {x0, y0, z0, x1, y1, z1}, 0, upper, upperUsed, 6);
                upperUsed += 6;
            } else {
                System.arraycopy(new float[] {x0, y0, z0, x1, y1, z1}, 0, under, underUsed, 6);
                underUsed += 6;
            }
        }
        return new Result(Arrays.copyOf(upper, upperUsed), Arrays.copyOf(under, underUsed));
    }

    /** How far under the feet the floor lines still show: time_erase_outline.fsh's FLOOR_LINE_LOWEST. */
    public static int floorLineDepth() {
        return FLOOR_LINE_LOWEST;
    }

    /**
     * How much of a floor line shows, before the edges' own fade at the end of the reach.
     *
     * @param depth how far under the feet its floor lies: 1 for the floor stood on
     * @param across how far it is from the feet, along the ground
     */
    public static float floorLineLevel(int depth, float across) {
        if (depth < 1 || depth > FLOOR_LINE_LOWEST) return 0.0F;
        float grade = depth == 1 ? 1.0F : FLOOR_LINE_STEP * (float) Math.pow(FLOOR_LINE_DIMS, depth - 2);
        float t = Math.max(0.0F, Math.min(1.0F, (across - FLOOR_LINE_FULL_ACROSS) / (FLOOR_LINE_GONE_ACROSS - FLOOR_LINE_FULL_ACROSS)));
        return grade * (1.0F - t * t * (3.0F - 2.0F * t));
    }

    /** One line for the log at the end of an erase. */
    public String summary() {
        return "sections read " + reads + " (again " + rereads + "), " + scanned.size() + " held, most edges " + mostSegmentsHeld + ", most faces " + mostFacesHeld
                + ", reading " + readNanos / 1_000_000L + " ms in all, worst section " + worstReadNanos / 1000L + " us, worst frame " + worstFrameNanos / 1000L + " us";
    }

    public int readCount() {
        return reads;
    }

    public long worstFrameNanos() {
        return worstFrameNanos;
    }

    public void reset() {
        scanned.clear();
        inBuffer.clear();
        wanted = new long[0];
        origin = null;
        seenFeet = null;
        revalidateAt = 0;
        changed = false;
        complete = false;
        reads = rereads = mostSegmentsHeld = mostFacesHeld = facesWritten = 0;
        readNanos = worstReadNanos = worstFrameNanos = 0L;
    }
}
