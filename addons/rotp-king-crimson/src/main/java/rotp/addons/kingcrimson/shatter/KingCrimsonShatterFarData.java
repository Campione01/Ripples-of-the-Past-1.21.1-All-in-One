package rotp.addons.kingcrimson.shatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** The bounded column scan for Voxy's real sections. No client classes or borrowed storage in the result. */
public final class KingCrimsonShatterFarData {
    public static final int MAX_RADIUS = 2048;
    private static final int MAX_LEVEL = 4;
    private static final int TILE = 1 << MAX_LEVEL;
    private static final int SIDE = 32;
    private static final int MASK = SIDE - 1;
    private static final long FILL_FLAG = 1L << 24;
    private static final int BLOCK_MASK = (1 << 20) - 1;

    private KingCrimsonShatterFarData() {}

    /** A nonempty, world-aligned rectangle of whole 16-block tiles, with exclusive maxima. */
    public record Area(int minX, int minZ, int maxX, int maxZ) {
        public Area {
            if (minX >= maxX || minZ >= maxZ || ((minX | minZ | maxX | maxZ) & (TILE - 1)) != 0) {
                throw new IllegalArgumentException("Invalid Voxy read area");
            }
        }

        public boolean contains(int x, int z) {
            return x >= minX && x < maxX && z >= minZ && z < maxZ;
        }
    }

    public record RingSpec(int innerRadius, int outerRadius, int level) {
        public RingSpec {
            if (innerRadius < 0 || innerRadius >= outerRadius || outerRadius > MAX_RADIUS
                    || level < 0 || level > MAX_LEVEL) {
                throw new IllegalArgumentException("Invalid Voxy ring");
            }
        }

        public int cellSize() {
            return 1 << level;
        }

        /** Assign whole world-aligned tiles so a finer ring cannot overlap a coarser ring at their boundary. */
        public boolean owns(int originX, int originZ, int x, int z) {
            long dx = (long) (x & -TILE) + TILE / 2 - originX;
            long dz = (long) (z & -TILE) + TILE / 2 - originZ;
            if (Math.abs(dx) >= outerRadius || Math.abs(dz) >= outerRadius) return false;
            long distanceSquared = dx * dx + dz * dz;
            return distanceSquared >= innerRadius * innerRadius && distanceSquared < outerRadius * outerRadius;
        }

        public boolean supportsHeight(int minY, int maxY) {
            // The engine's section key stores a signed, eight-bit section Y.
            return minY < maxY && sectionOf(minY, level) >= -128 && sectionOf(maxY - 1, level) <= 127;
        }
    }

    public record Cell(int x, int z, float topY, int blockId, int biomeId) {}

    /** Empty counts present sections with no top found in the requested columns/height, not absent keys. */
    public record RingStats(int asked, int missing, int empty, int cacheHits, int columns, int nonAirCells, long nanos) {}

    public enum Outcome {
        COMPLETE, CANCELLED, ENGINE_STOPPED, DEADLINE, SECTION_LIMIT, CELL_LIMIT, FAILED;

        public boolean allowsCompletedRings() {
            return this == COMPLETE || this == DEADLINE || this == SECTION_LIMIT || this == CELL_LIMIT;
        }
    }

    public record RingRead(RingSpec spec, List<Cell> cells, RingStats stats, Outcome outcome, String failure) {
        public RingRead {
            cells = outcome == Outcome.COMPLETE ? List.copyOf(cells) : List.of();
        }
    }

    /** One job's caps, shared by its rings in inner-to-outer order. */
    public static final class Budget {
        private int sectionsLeft;
        private int columnsLeft;
        private final long deadline;

        public Budget(int sections, int columns, long deadline) {
            if (sections < 0 || columns < 0) throw new IllegalArgumentException("Negative Voxy read budget");
            sectionsLeft = sections;
            columnsLeft = columns;
            this.deadline = deadline;
        }
    }

    /** Implementations return one owned section reference, or null. Only the scan sees the borrowed cell array. */
    public interface Sections<S> {
        default void beforeAcquire() throws InterruptedException {}
        boolean isOpen() throws Throwable;
        S acquireCached(int level, int x, int y, int z) throws Throwable;
        S acquireIfExists(int level, int x, int y, int z) throws Throwable;
        long[] rawOrNull(S section) throws Throwable;
        long uniformValue(S section) throws Throwable;
        void release(S section) throws Throwable;
    }

    public static int sectionOf(int block, int level) {
        return block >> (5 + level);
    }

    public static int indexOf(int x, int y, int z, int level) {
        return ((y >> level) & MASK) << 10 | ((z >> level) & MASK) << 5 | ((x >> level) & MASK);
    }

    public static int blockId(long cell) {
        return (int) (cell >>> 27) & BLOCK_MASK;
    }

    public static int biomeId(long cell) {
        return (int) (cell >>> 47) & 511;
    }

    public static int carriedBlockId(long cell) {
        return (int) cell & BLOCK_MASK;
    }

    public static boolean isCarrier(long cell) {
        return (cell & FILL_FLAG) != 0L && blockId(cell) == 0 && carriedBlockId(cell) != 0;
    }

    public static int fill(long cell) {
        return (cell & FILL_FLAG) != 0L ? (int) (cell >>> 20) & 15 : blockId(cell) == 0 ? 0 : 16;
    }

    public static <S> RingRead scan(RingSpec spec, int originX, int originZ, int minY, int maxY,
            Budget budget, BooleanSupplier cancelled, Sections<S> sections) {
        return scanWithin(spec, originX, originZ, minY, maxY, budget, cancelled, sections, null);
    }

    public static <S> RingRead scan(RingSpec spec, int originX, int originZ, int minY, int maxY,
            Budget budget, BooleanSupplier cancelled, Sections<S> sections, Area area) {
        return scanWithin(spec, originX, originZ, minY, maxY, budget, cancelled, sections, Objects.requireNonNull(area));
    }

    private static <S> RingRead scanWithin(RingSpec spec, int originX, int originZ, int minY, int maxY,
            Budget budget, BooleanSupplier cancelled, Sections<S> sections, Area area) {
        if (!spec.supportsHeight(minY, maxY)) throw new IllegalArgumentException("Voxy cannot address this height");
        Scan<S> scan = new Scan<>(spec, originX, originZ, minY, maxY, budget, cancelled, sections, area);
        try {
            return scan.result(scan.read(), null);
        } catch (Throwable failure) {
            if (failure instanceof Error error && !(failure instanceof LinkageError)) throw error;
            return scan.result(Outcome.FAILED, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static final class Scan<S> {
        private final RingSpec spec;
        private final int originX, originZ, minY, maxY;
        private final Budget budget;
        private final BooleanSupplier cancelled;
        private final Sections<S> sections;
        private final Area area;
        private final long start = System.nanoTime();
        private final int[] unresolved = new int[SIDE * SIDE];
        private final List<Cell> cells = new ArrayList<>();
        private int asked, missing, empty, cacheHits, columns;

        private Scan(RingSpec spec, int originX, int originZ, int minY, int maxY,
                Budget budget, BooleanSupplier cancelled, Sections<S> sections, Area area) {
            this.spec = spec;
            this.originX = originX;
            this.originZ = originZ;
            this.minY = minY;
            this.maxY = maxY;
            this.budget = budget;
            this.cancelled = cancelled;
            this.sections = sections;
            this.area = area;
        }

        private Outcome stop() throws Throwable {
            if (cancelled.getAsBoolean()) return Outcome.CANCELLED;
            if (!sections.isOpen()) return Outcome.ENGINE_STOPPED;
            return System.nanoTime() - budget.deadline >= 0L ? Outcome.DEADLINE : null;
        }

        private Outcome read() throws Throwable {
            int level = spec.level();
            int sectionSize = SIDE << level;
            int reach = spec.outerRadius() + TILE / 2;
            int sxStart = sectionOf(originX - reach, level);
            int sxEnd = sectionOf(originX + reach, level);
            int szStart = sectionOf(originZ - reach, level);
            int szEnd = sectionOf(originZ + reach, level);
            if (area != null) {
                sxStart = Math.max(sxStart, sectionOf(area.minX(), level));
                sxEnd = Math.min(sxEnd, sectionOf(area.maxX() - 1, level));
                szStart = Math.max(szStart, sectionOf(area.minZ(), level));
                szEnd = Math.min(szEnd, sectionOf(area.maxZ() - 1, level));
            }
            int syTop = sectionOf(maxY - 1, level);
            int syBottom = sectionOf(minY, level);
            for (int sz = szStart; sz <= szEnd; sz++) {
                for (int sx = sxStart; sx <= sxEnd; sx++) {
                    Outcome stop = stop();
                    if (stop != null) return stop;
                    int baseX = sx * sectionSize;
                    int baseZ = sz * sectionSize;
                    int remaining = collectColumns(baseX, baseZ, sectionSize);
                    if (remaining > budget.columnsLeft) return Outcome.CELL_LIMIT;
                    columns += remaining;
                    budget.columnsLeft -= remaining;
                    for (int sy = syTop; sy >= syBottom && remaining > 0; sy--) {
                        stop = stop();
                        if (stop != null) return stop;
                        if (budget.sectionsLeft == 0) return Outcome.SECTION_LIMIT;
                        sections.beforeAcquire();
                        stop = stop();
                        if (stop != null) return stop;
                        budget.sectionsLeft--;
                        asked++;
                        S section = sections.acquireCached(level, sx, sy, sz);
                        if (section == null) {
                            stop = stop();
                            if (stop != null) return stop;
                            section = sections.acquireIfExists(level, sx, sy, sz);
                        } else {
                            cacheHits++;
                        }
                        if (section == null) {
                            missing++;
                            continue;
                        }
                        try {
                            stop = stop();
                            if (stop != null) return stop;
                            int next = scanSection(section, baseX, sy * sectionSize, baseZ, remaining);
                            if (next == remaining) empty++;
                            remaining = next;
                        } finally {
                            sections.release(section);
                        }
                    }
                }
            }
            Outcome stop = stop();
            return stop == null ? Outcome.COMPLETE : stop;
        }

        private int collectColumns(int baseX, int baseZ, int sectionSize) {
            int count = 0;
            int size = spec.cellSize();
            for (int z = 0; z < sectionSize; z += TILE) {
                for (int x = 0; x < sectionSize; x += TILE) {
                    int tileX = baseX + x;
                    int tileZ = baseZ + z;
                    if (area != null && !area.contains(tileX, tileZ)) continue;
                    if (!spec.owns(originX, originZ, tileX, tileZ)) continue;
                    for (int dz = 0; dz < TILE; dz += size) {
                        for (int dx = 0; dx < TILE; dx += size) {
                            unresolved[count++] = ((z + dz) >> spec.level()) << 5 | ((x + dx) >> spec.level());
                        }
                    }
                }
            }
            return count;
        }

        private int scanSection(S section, int baseX, int baseY, int baseZ, int count) throws Throwable {
            long[] raw = sections.rawOrNull(section);
            long uniform = raw == null ? sections.uniformValue(section) : 0L;
            if (raw == null && blockId(uniform) == 0 && !isCarrier(uniform)) return count;
            if (raw != null && raw.length != SIDE * SIDE * SIDE) throw new IllegalStateException("Invalid Voxy section size");
            int size = spec.cellSize();
            int top = (Math.min(maxY - 1 - baseY, SIDE * size - 1)) >> spec.level();
            int bottom = Math.max(0, minY - baseY) >> spec.level();
            int next = 0;
            for (int i = 0; i < count; i++) {
                int column = unresolved[i];
                boolean found = false;
                for (int y = top; y >= bottom; y--) {
                    long cell = raw == null ? uniform : raw[y << 10 | column];
                    int block = isCarrier(cell) ? carriedBlockId(cell) : blockId(cell);
                    if (block == 0) continue;
                    int fill = fill(cell);
                    if (fill == 0) continue;
                    float height = Math.min(maxY, baseY + y * size + fill * size / 16.0F);
                    if (height <= minY) continue;
                    cells.add(new Cell(baseX + (column & MASK) * size, baseZ + (column >> 5) * size,
                            height, block, biomeId(cell)));
                    found = true;
                    break;
                }
                if (!found) unresolved[next++] = column;
            }
            return next;
        }

        private RingRead result(Outcome outcome, String failure) {
            return new RingRead(spec, cells,
                    new RingStats(asked, missing, empty, cacheHits, columns, cells.size(), System.nanoTime() - start), outcome, failure);
        }
    }
}
