package rotp.addons.kingcrimson.shatter;

import java.util.Arrays;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The edges of the real blocks in one chunk section that show what is in the way once the world is gone: only those a silhouette needs,
 * where two faces meet at an angle, not where two blocks lie flush, merged along their length. A section owns the edges that start on its
 * lower lattice lines, so that two sections never draw one edge twice, and it knows nothing of the user: whether an edge counts as above
 * or under the feet is decided from the height it carries, and each section is only read once. It also gives the faces those edges bound,
 * merged into rectangles: they are drawn into a depth of their own, so that a nearer solid hides what is behind it.
 */
public final class KingCrimsonTerrainOutlineScan {
    private static final int SIDE = SectionPos.SECTION_SIZE;
    private static final int LAST = SIDE - 1;
    // The section and one block around it, so that a limit of the section never reads as an edge of the terrain.
    private static final int WIDE = SIDE + 2;
    // Each edge: both ends relative to the origin, then the height of the highest block it touches; a floor line carries + FLOOR_MARK.
    public static final int STRIDE = 7;
    public static final float FLOOR_MARK = 0.5F;
    // A flat floor has no edge at all; every this many blocks of the world a faint line keeps it readable, and it stays put as the user walks.
    public static final int FLOOR_LINE_EVERY = 4;
    private static final double LOWEST_PARTIAL = 0.2D;
    private static final int NO_EDGE = Integer.MIN_VALUE;
    private static final float[] NOTHING = new float[0];
    public static final Section EMPTY = new Section(NOTHING, NOTHING);
    // What a face's last value says: a fill shades them apart so that a block reads as a body.
    public static final float FACE_SIDE_X = 0.0F;
    public static final float FACE_SIDE_Z = 1.0F;
    public static final float FACE_TOP = 2.0F;
    public static final float FACE_BOTTOM = 3.0F;

    /** Edges: both ends, then the height. Faces: the two opposite corners of a rectangle along the axes, then its kind. */
    public record Section(float[] edges, float[] faces) {
        public boolean isEmpty() {
            return edges.length == 0 && faces.length == 0;
        }
    }

    private KingCrimsonTerrainOutlineScan() {}

    /** @return the section relative to {@code origin}, or null if its chunk is not loaded yet */
    @Nullable
    public static Section scanSection(BlockGetter level, int sectionX, int sectionY, int sectionZ, BlockPos origin) {
        return new Scan(level, sectionX, sectionY, sectionZ, origin).run();
    }

    private static final class Scan {
        private final BlockGetter level;
        private final int sectionX;
        private final int sectionY;
        private final int sectionZ;
        private final int baseX;
        private final int baseY;
        private final int baseZ;
        private final float offsetX;
        private final float offsetY;
        private final float offsetZ;
        private final int heightOffset;
        private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        private final boolean[] full = new boolean[WIDE * WIDE * WIDE];
        private LevelChunkSection[] around;
        private float[] values = new float[STRIDE * 32];
        private int used;
        private float[] faceValues = new float[STRIDE * 32];
        private int facesUsed;

        private Scan(BlockGetter level, int sectionX, int sectionY, int sectionZ, BlockPos origin) {
            this.level = level;
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
            baseX = SectionPos.sectionToBlockCoord(sectionX);
            baseY = SectionPos.sectionToBlockCoord(sectionY);
            baseZ = SectionPos.sectionToBlockCoord(sectionZ);
            offsetX = baseX - origin.getX();
            offsetY = baseY - origin.getY();
            offsetZ = baseZ - origin.getZ();
            heightOffset = baseY - origin.getY();
        }

        private Section run() {
            if (level instanceof LevelReader reader) {
                if (reader.getChunk(sectionX, sectionZ, ChunkStatus.FULL, false) == null) return null;
                if (!findSectionsAround(reader)) return EMPTY;
            }
            read();
            for (Direction.Axis axis : Direction.Axis.values()) {
                edgesAlong(axis);
            }
            floorLines();
            faces();
            if (used == 0 && facesUsed == 0) return EMPTY;
            return new Section(Arrays.copyOf(values, used), Arrays.copyOf(faceValues, facesUsed));
        }

        // Every open side of a whole block, rectangles merged plane by plane.
        private void faces() {
            boolean[] open = new boolean[SIDE * SIDE];
            for (Direction direction : Direction.values()) {
                Direction.Axis axis = direction.getAxis();
                int step = direction.getAxisDirection().getStep();
                for (int plane = 0; plane <= LAST; plane++) {
                    boolean any = false;
                    for (int v = 0; v <= LAST; v++) {
                        for (int u = 0; u <= LAST; u++) {
                            boolean here = isFull(cellX(axis, plane, u, v), cellY(axis, plane, u, v), cellZ(axis, plane, u, v))
                                    && !isFull(cellX(axis, plane + step, u, v), cellY(axis, plane + step, u, v), cellZ(axis, plane + step, u, v));
                            open[u + v * SIDE] = here;
                            any |= here;
                        }
                    }
                    if (any) mergeFaces(open, axis, step > 0 ? plane + 1 : plane, faceKind(direction));
                }
            }
        }

        private static float faceKind(Direction direction) {
            return switch (direction) {
                case UP -> FACE_TOP;
                case DOWN -> FACE_BOTTOM;
                case EAST, WEST -> FACE_SIDE_X;
                case NORTH, SOUTH -> FACE_SIDE_Z;
            };
        }

        private static int cellX(Direction.Axis axis, int plane, int u, int v) {
            return axis == Direction.Axis.X ? plane : u;
        }

        private static int cellY(Direction.Axis axis, int plane, int u, int v) {
            return axis == Direction.Axis.Y ? plane : v;
        }

        private static int cellZ(Direction.Axis axis, int plane, int u, int v) {
            return axis == Direction.Axis.Z ? plane : axis == Direction.Axis.X ? u : v;
        }

        private void mergeFaces(boolean[] open, Direction.Axis axis, int at, float kind) {
            for (int v = 0; v <= LAST; v++) {
                for (int u = 0; u <= LAST; u++) {
                    if (!open[u + v * SIDE]) continue;
                    int width = 1;
                    while (u + width <= LAST && open[u + width + v * SIDE]) width++;
                    int height = 1;
                    grow:
                    while (v + height <= LAST) {
                        for (int i = 0; i < width; i++) {
                            if (!open[u + i + (v + height) * SIDE]) break grow;
                        }
                        height++;
                    }
                    for (int j = 0; j < height; j++) {
                        Arrays.fill(open, u + (v + j) * SIDE, u + width + (v + j) * SIDE, false);
                    }
                    switch (axis) {
                        case X -> addFace(at, v, u, at, v + height, u + width, kind);
                        case Y -> addFace(u, at, v, u + width, at, v + height, kind);
                        case Z -> addFace(u, v, at, u + width, v + height, at, kind);
                    }
                }
            }
        }

        private void addFace(float x0, float y0, float z0, float x1, float y1, float z1, float kind) {
            if (facesUsed == faceValues.length) faceValues = Arrays.copyOf(faceValues, facesUsed * 2);
            faceValues[facesUsed++] = x0 + offsetX;
            faceValues[facesUsed++] = y0 + offsetY;
            faceValues[facesUsed++] = z0 + offsetZ;
            faceValues[facesUsed++] = x1 + offsetX;
            faceValues[facesUsed++] = y1 + offsetY;
            faceValues[facesUsed++] = z1 + offsetZ;
            faceValues[facesUsed++] = kind;
        }

        // Most of a terrain's volume is air: a section with nothing around it costs no block read at all.
        private boolean findSectionsAround(LevelReader reader) {
            around = new LevelChunkSection[27];
            boolean any = false;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    ChunkAccess chunk = reader.getChunk(sectionX + dx, sectionZ + dz, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        int index = chunk.getSectionIndexFromSectionY(sectionY + dy);
                        if (index < 0 || index >= chunk.getSectionsCount()) continue;
                        LevelChunkSection section = chunk.getSection(index);
                        if (section.hasOnlyAir()) continue;
                        around[dx + 1 + (dy + 1) * 3 + (dz + 1) * 9] = section;
                        any = true;
                    }
                }
            }
            return any;
        }

        private static int aroundIndex(int x, int y, int z) {
            return (x < 0 ? 0 : x > LAST ? 2 : 1) + (y < 0 ? 0 : y > LAST ? 2 : 1) * 3 + (z < 0 ? 0 : z > LAST ? 2 : 1) * 9;
        }

        private static int cell(int x, int y, int z) {
            return x + 1 + (y + 1) * WIDE + (z + 1) * WIDE * WIDE;
        }

        private boolean isFull(int x, int y, int z) {
            return full[cell(x, y, z)];
        }

        private void read() {
            for (int z = -1; z <= SIDE; z++) {
                for (int y = -1; y <= SIDE; y++) {
                    for (int x = -1; x <= SIDE; x++) {
                        BlockState state;
                        if (around != null) {
                            LevelChunkSection section = around[aroundIndex(x, y, z)];
                            if (section == null) continue;
                            state = section.getBlockState(x & LAST, y & LAST, z & LAST);
                            if (state.isAir()) continue;
                            at.set(baseX + x, baseY + y, baseZ + z);
                        } else {
                            at.set(baseX + x, baseY + y, baseZ + z);
                            state = level.getBlockState(at);
                            if (state.isAir()) continue;
                        }
                        VoxelShape shape = state.getCollisionShape(level, at);
                        if (shape.isEmpty()) continue;
                        if (state.isCollisionShapeFullBlock(level, at)) {
                            full[cell(x, y, z)] = true;
                        } else if (x >= 0 && y >= 0 && z >= 0 && x <= LAST && y <= LAST && z <= LAST) {
                            partial(shape.bounds(), x, y, z);
                        }
                    }
                }
            }
        }

        // A fence or a slab: its box, without what sits on a whole block below it. A carpet is walked over and blocks nothing.
        private void partial(AABB box, int x, int y, int z) {
            if (box.maxY < LOWEST_PARTIAL) return;
            float height = heightOffset + y;
            float x0 = (float) (x + box.minX);
            float y0 = (float) (y + box.minY);
            float z0 = (float) (z + box.minZ);
            float x1 = (float) (x + box.maxX);
            float y1 = (float) (y + box.maxY);
            float z1 = (float) (z + box.maxZ);
            add(x0, y1, z0, x1, y1, z0, height);
            add(x1, y1, z0, x1, y1, z1, height);
            add(x1, y1, z1, x0, y1, z1, height);
            add(x0, y1, z1, x0, y1, z0, height);
            addFace(x0, y1, z0, x1, y1, z1, FACE_TOP);
            addFace(x0, y0, z0, x1, y0, z1, FACE_BOTTOM);
            addFace(x0, y0, z0, x0, y1, z1, FACE_SIDE_X);
            addFace(x1, y0, z0, x1, y1, z1, FACE_SIDE_X);
            addFace(x0, y0, z0, x1, y1, z0, FACE_SIDE_Z);
            addFace(x0, y0, z1, x1, y1, z1, FACE_SIDE_Z);
            add(x0, y0, z0, x0, y1, z0, height);
            add(x1, y0, z0, x1, y1, z0, height);
            add(x1, y0, z1, x1, y1, z1, height);
            add(x0, y0, z1, x0, y1, z1, height);
            if (box.minY > 0.0D || !isFull(x, y - 1, z)) {
                add(x0, y0, z0, x1, y0, z0, height);
                add(x1, y0, z0, x1, y0, z1, height);
                add(x1, y0, z1, x0, y0, z1, height);
                add(x0, y0, z1, x0, y0, z0, height);
            }
        }

        private void edgesAlong(Direction.Axis axis) {
            for (int b = 0; b <= LAST; b++) {
                for (int c = 0; c <= LAST; c++) {
                    lineAlong(axis, b, c);
                }
            }
        }

        // All the lattice edges on one straight line, runs that touch the same height merged.
        private void lineAlong(Direction.Axis axis, int b, int c) {
            int runStart = 0;
            int runHeight = NO_EDGE;
            for (int a = 0; a <= SIDE; a++) {
                int height = a <= LAST ? edgeHeight(axis, a, b, c) : NO_EDGE;
                if (height != runHeight) {
                    if (runHeight != NO_EDGE) emit(axis, runStart, a, b, c, runHeight);
                    runStart = a;
                    runHeight = height;
                }
            }
        }

        // The edge from (a, b, c) one block along the axis, with the four cells around it: where it is an edge, the height of its highest.
        private int edgeHeight(Direction.Axis axis, int a, int b, int c) {
            boolean p = cellAround(axis, a, b, c, 0, 0);
            boolean q = cellAround(axis, a, b, c, -1, 0);
            boolean r = cellAround(axis, a, b, c, 0, -1);
            boolean s = cellAround(axis, a, b, c, -1, -1);
            int count = (p ? 1 : 0) + (q ? 1 : 0) + (r ? 1 : 0) + (s ? 1 : 0);
            boolean diagonal = count == 2 && p == s;
            if (count != 1 && count != 3 && !diagonal) return NO_EDGE;
            // An upright edge takes its height from where it is drawn: one value here lets the whole run merge.
            if (axis == Direction.Axis.Y) return heightOffset;
            // For the other two axes c is the height of the lattice line: p and q lie above it, r and s below.
            return heightOffset + (p || q ? c : c - 1);
        }

        private boolean cellAround(Direction.Axis axis, int a, int b, int c, int offsetB, int offsetC) {
            return switch (axis) {
                case X -> isFull(a, c + offsetC, b + offsetB);
                case Y -> isFull(b + offsetB, a, c + offsetC);
                case Z -> isFull(b + offsetB, c + offsetC, a);
            };
        }

        private void emit(Direction.Axis axis, int from, int to, int b, int c, int height) {
            switch (axis) {
                case X -> add(from, c, b, to, c, b, height);
                case Y -> add(b, from, c, b, to, c, height);
                case Z -> add(b, c, from, b, c, to, height);
            }
        }

        // The tops that lie open, on a coarse grid of the world.
        private void floorLines() {
            for (int y = 0; y <= LAST; y++) {
                for (int z = 0; z <= LAST; z++) {
                    if (Math.floorMod(baseZ + z, FLOOR_LINE_EVERY) == 0) floorRun(y, z, true);
                }
                for (int x = 0; x <= LAST; x++) {
                    if (Math.floorMod(baseX + x, FLOOR_LINE_EVERY) == 0) floorRun(y, x, false);
                }
            }
        }

        private void floorRun(int y, int line, boolean alongX) {
            int runStart = -1;
            for (int a = 0; a <= SIDE; a++) {
                boolean open = a <= LAST
                        && (alongX ? isFull(a, y, line) && !isFull(a, y + 1, line) : isFull(line, y, a) && !isFull(line, y + 1, a));
                if (open && runStart < 0) {
                    runStart = a;
                } else if (!open && runStart >= 0) {
                    float height = heightOffset + y + FLOOR_MARK;
                    if (alongX) add(runStart, y + 1, line, a, y + 1, line, height);
                    else add(line, y + 1, runStart, line, y + 1, a, height);
                    runStart = -1;
                }
            }
        }

        private void add(float x0, float y0, float z0, float x1, float y1, float z1, float height) {
            if (used == values.length) values = Arrays.copyOf(values, used * 2);
            values[used++] = x0 + offsetX;
            values[used++] = y0 + offsetY;
            values[used++] = z0 + offsetZ;
            values[used++] = x1 + offsetX;
            values[used++] = y1 + offsetY;
            values[used++] = z1 + offsetZ;
            values[used++] = height;
        }
    }
}
