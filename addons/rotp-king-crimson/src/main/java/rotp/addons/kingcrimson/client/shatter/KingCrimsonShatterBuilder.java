package rotp.addons.kingcrimson.client.shatter;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlateLight;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.Tags;

/**
 * Turns the real terrain around the user into the plates of the plan, off the render thread and reading only. Over the
 * whole range a plate is the real blocks with their real faces, because nothing may look worse the moment the plates take
 * over from the terrain. Only past a budget of vertices do the cells still to come, the farthest, go coarse in a flat colour.
 */
@OnlyIn(Dist.CLIENT)
final class KingCrimsonShatterBuilder {
    private static final int FULL_DETAIL_RADIUS = KingCrimsonShatterPlan.DEFAULT_RANGE;
    private static final int CELL = 4;
    private static final int DOUBLE_CELL_RADIUS = 176;
    private static final int PLANT_RADIUS = 128;
    private static final int BOTTOM_DETAIL_RADIUS = 40;
    // The plates have no fog: the crown of a tree that stands past the range would hang in the air where the terrain's fog hid
    // it. Only the last blocks of the range, where the fog has all but hidden what stands there too.
    private static final int BARE_EDGE_BLOCKS = 4;
    // A tint blends the biomes around the block, which is slow wherever the client has none cached: past this a cell of
    // columns takes one answer per kind of block, where a step in the colour cannot be seen.
    private static final int TINT_EXACT_RADIUS = 64;
    private static final int TINT_CELL_SHIFT = 2;
    private static final int TINT_ANY_HEIGHT = 2047;
    // Past this the cells still to come are drawn coarse, whatever stands there. They come nearest first.
    private static final int NEAR_VERTEX_BUDGET = 1_500_000;
    private static final int SCAN_BLOCKS = 48;
    private static final int SOLID_RUN = 10;
    private static final int UNDERGROUND_DEPTH = 12;
    private static final int SPAN_RADIUS = 48;
    private static final int SPAN_UP = 10;
    private static final int SPAN_DOWN = 6;
    private static final int NO_COLUMN = -1;
    private static final int NO_CELL = Integer.MIN_VALUE;
    private static final int MOST_STANDING_PLACES = 16;
    private static final int MAX_DEBRIS = 300;
    // Most leave the undersides: left behind below a plate that rises they show against the void, while one from a crack
    // is lost against the dirt of the plates around it.
    private static final int MAX_CRACK_DEBRIS = 90;
    private static final float CRACK_DEBRIS_CHANCE = 0.08F;
    private static final float UNDERSIDE_DEBRIS_CHANCE = 0.035F;
    private static final float UNDERSIDE_DEBRIS_BELOW = 2.5F;
    private static final float UNDERSIDE_DEBRIS_AFTER = 6.0F;
    // Near the user the plates have left by the time these rise, so they are the ones seen against the void.
    private static final float UNDERSIDE_DEBRIS_NEAR_CHANCE = 0.09F;
    private static final int UNDERSIDE_DEBRIS_NEAR_RADIUS = 28;
    // Above 1 more of them have left by the middle of the flight.
    private static final double UNDERSIDE_DEBRIS_EARLY = 1.4D;
    // The budget goes mostly where the user faces at the press, or few would be seen.
    private static final float DEBRIS_BEHIND_SHARE = 0.35F;
    private static final double DEBRIS_FRONT_FROM = 0.3D;
    private static final int DEBRIS_RADIUS = 64;
    // Not through the user's own body.
    private static final int DEBRIS_INNER_RADIUS = 3;
    private static final float DEBRIS_DRIFT_MIN = 0.06F;
    private static final float DEBRIS_DRIFT_MAX = 0.16F;
    private static final double DEBRIS_TURN_MOST = Math.toRadians(20.0D);
    // The weightless blocks of the anime (owner, 2026-11-11): whole blocks of the ground torn off the plates' tops after the
    // crack, floating up slowly and turning a little. A budget of their own, so that they take nothing from the small ones.
    private static final int MAX_LOOSE_BLOCKS = 100;
    private static final int ALL_DEBRIS = MAX_DEBRIS + MAX_LOOSE_BLOCKS;
    private static final float LOOSE_BLOCK_CHANCE = 0.006F;
    private static final int LOOSE_BLOCK_INNER_RADIUS = 6;
    // Past this a block is a speck of a few pixels; the far-terrain add-on carries the idea on beyond the near range.
    private static final int LOOSE_BLOCK_RADIUS = 96;
    private static final float LOOSE_BLOCK_AFTER_MIN = 4.0F;
    private static final float LOOSE_BLOCK_AFTER_MAX = 14.0F;
    private static final float LOOSE_BLOCK_SIZE_MIN = 0.6F;
    private static final float LOOSE_BLOCK_SIZE_MAX = 1.0F;
    private static final float LOOSE_BLOCK_RISE_MIN = 0.02F;
    private static final float LOOSE_BLOCK_RISE_MAX = 0.05F;
    private static final float LOOSE_BLOCK_DRIFT_MAX = 0.05F;
    private static final double LOOSE_BLOCK_SPIN_MIN = Math.toRadians(0.5D);
    private static final double LOOSE_BLOCK_SPIN_MAX = Math.toRadians(1.5D);
    private static final float LOOSE_BLOCK_OFF_CENTRE = 0.15F;
    private static final long LOOSE_BLOCK_SEED = 0x6A09E667F3BCC909L;
    private static final int WHITE = 0xFFFFFFFF;
    private static final float FLUID_TOP = 8.0F / 9.0F;
    private static final float WATER_SHADE = 0.62F;
    private static final float CRACK_SIDE_REACH = 1.2F;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int NORMAL_DOWN = Direction.DOWN.ordinal();
    private static final int NORMAL_UP = Direction.UP.ordinal();
    private static final int NORMAL_WALL = KingCrimsonShatterMesh.NORMAL_CUT_WALL;

    private final ClientLevel level;
    private final KingCrimsonShatterPlan plan;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int range;
    private final boolean span;
    private final int nearRadius;
    private final BooleanSupplier cancelled;
    private final BlockColors blockColors;
    private final BlockModelShaper shaper;
    private final Function<ResourceLocation, TextureAtlasSprite> atlas;
    private final RandomSource modelRandom = RandomSource.createNewThreadLocalInstance();
    private final SplittableRandom random;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
    private final int minY;
    private final int maxY;

    private final int columnHalf;
    private final int columnSide;
    private final int[] columnPlate;
    private final int[] columnTop;
    private final int[] columnBottom;
    private final float[] columnGroundTop;
    private final float[] representedTops;
    private final int[] columnLight;
    private final BlockState[][] columnStates;

    private final int cellHalf;
    private final int cellSide;
    private final int[] cellTop;
    private final int[] cellPlate;
    private final int[] cellColor;
    private final int[] cellSideColor;
    private final int[] cellLight;

    private final PlateBuilder[] plates;
    private final double[] groundSum;
    private final int[] groundCount;
    private final Map<BlockState, FlatColor> flatColors = new IdentityHashMap<>();
    private final Map<BlockState, CubeFaces> cubeFaces = new IdentityHashMap<>();
    private final Map<BlockState, TintMemo> tintMemos = new IdentityHashMap<>();

    private final int chunkMinX;
    private final int chunkMinZ;
    private final int chunkSide;
    private final LevelChunk[] chunks;
    private final boolean[] chunkRead;

    private final KingCrimsonShatterFarSource farSource = new ChunkFarSource();
    private final KingCrimsonShatterFarSource.Cell farCell = new KingCrimsonShatterFarSource.Cell();
    private final float[] debris = new float[KingCrimsonShatterMesh.DEBRIS_STRIDE * ALL_DEBRIS];
    private final int[] debrisColors = new int[6 * ALL_DEBRIS];
    private final int[] debrisLights = new int[ALL_DEBRIS];
    private final byte[] debrisLevels = new byte[ALL_DEBRIS];
    // Their own stream: the small blocks fall where they always fell.
    private final SplittableRandom looseRandom;
    private final boolean looseBlocks;
    private int smallDebrisCount;
    private int looseBlockCount;
    private final Map<TextureAtlasSprite, Float> spriteRadiance = new IdentityHashMap<>();
    private TextureAtlasSprite lastSprite;
    private float lastSpriteRadiance;
    private float[] surface;
    private int debrisCount;
    private int crackDebrisCount;
    private final KingCrimsonShatterPlan.Pose debrisPose = new KingCrimsonShatterPlan.Pose();
    private int vertices;
    private int coarseCells;

    KingCrimsonShatterBuilder(
            ClientLevel level,
            KingCrimsonShatterPlan plan,
            int originY,
            int range,
            long seed,
            BooleanSupplier cancelled) {
        this.level = level;
        this.plan = plan;
        this.originX = plan.originX();
        this.originY = originY;
        this.originZ = plan.originZ();
        this.cancelled = cancelled;
        Minecraft minecraft = Minecraft.getInstance();
        this.blockColors = minecraft.getBlockColors();
        this.shaper = minecraft.getBlockRenderer().getBlockModelShaper();
        this.atlas = minecraft.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        this.random = new SplittableRandom(seed);
        this.looseRandom = new SplittableRandom(seed ^ LOOSE_BLOCK_SEED);
        this.looseBlocks = KingCrimsonShatterDiagnostics.looseBlocksOn();
        this.minY = level.getMinBuildHeight();
        this.maxY = level.getMaxBuildHeight() - 1;

        int reach = Math.min(range, plan.range());
        int firstChunkReach = reach + 16 * 2;
        chunkMinX = (originX - firstChunkReach) >> 4;
        chunkMinZ = (originZ - firstChunkReach) >> 4;
        chunkSide = ((firstChunkReach * 2) >> 4) + 2;
        chunks = new LevelChunk[chunkSide * chunkSide];
        chunkRead = new boolean[chunkSide * chunkSide];

        this.span = aroundOwnHeight();
        this.range = span ? Math.min(reach, SPAN_RADIUS) : reach;
        this.nearRadius = Math.min(this.range, span ? SPAN_RADIUS : FULL_DETAIL_RADIUS);

        columnHalf = nearRadius + CELL * 2;
        columnSide = columnHalf * 2 + 1;
        int columns = columnSide * columnSide;
        columnPlate = new int[columns];
        Arrays.fill(columnPlate, NO_COLUMN);
        columnTop = new int[columns];
        columnBottom = new int[columns];
        columnGroundTop = new float[columns];
        representedTops = new float[columns];
        Arrays.fill(representedTops, Float.NaN);
        columnLight = new int[columns];
        columnStates = new BlockState[columns][];

        cellHalf = this.range / CELL + 2;
        cellSide = cellHalf * 2 + 1;
        int cells = cellSide * cellSide;
        cellTop = new int[cells];
        Arrays.fill(cellTop, NO_CELL);
        cellPlate = new int[cells];
        cellColor = new int[cells];
        cellSideColor = new int[cells];
        cellLight = new int[cells];

        plates = new PlateBuilder[plan.plateCount()];
        groundSum = new double[plan.plateCount()];
        groundCount = new int[plan.plateCount()];
    }

    /** Null when the erase ended while building. */
    KingCrimsonShatterMesh build() {
        long start = System.nanoTime();
        long[] order = sampleTerrain();
        if (order == null) {
            return null;
        }
        if (!span) {
            surface = KingCrimsonShatterSurface.walk(columnHalf, originY, MOST_STANDING_PLACES, this::standingPlaces);
        }
        KingCrimsonShatterMesh.CutLine[] cutLines = buildCutLines();
        KingCrimsonShatterWind wind = KingCrimsonShatterWind.build(plan, (dx, dz) -> surfaceAt(dx, dz) - originY, random.nextLong());
        Arrays.sort(order);
        for (long entry : order) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            int index = (int) entry;
            int ci = index % cellSide - cellHalf;
            int cj = index / cellSide - cellHalf;
            int distance = (int) Math.sqrt((double) (entry >>> 32));
            if (distance <= nearRadius && vertices < NEAR_VERTEX_BUDGET) {
                emitNearCell(ci, cj, distance);
            } else {
                coarseCells++;
                if (!span) {
                    emitFarCell(ci, cj);
                }
            }
        }
        return finish(start, cutLines, wind);
    }

    // Each cut sampled along its length: the ground under it, and when the plates beside it part.
    private KingCrimsonShatterMesh.CutLine[] buildCutLines() {
        List<KingCrimsonShatterPlan.Cut> cuts = plan.cuts();
        KingCrimsonShatterMesh.CutLine[] lines = new KingCrimsonShatterMesh.CutLine[cuts.size()];
        for (int c = 0; c < lines.length; c++) {
            KingCrimsonShatterPlan.Cut cut = cuts.get(c);
            int capacity = (int) cut.length * 2 + 3;
            float[] along = new float[capacity];
            float[] height = new float[capacity];
            float[] parting = new float[capacity];
            int count = 0;
            float lastParting = 0.0F;
            double place = 0.0D;
            while (true) {
                double dx = cut.startX + cut.dirX * place;
                double dz = cut.startZ + cut.dirZ * place;
                float ground = surfaceAt(dx, dz);
                along[count] = (float) place;
                height[count] = ground - originY;
                int left = plan.plateAt(dx - cut.dirZ * CRACK_SIDE_REACH, dz + cut.dirX * CRACK_SIDE_REACH);
                int right = plan.plateAt(dx + cut.dirZ * CRACK_SIDE_REACH, dz - cut.dirX * CRACK_SIDE_REACH);
                float part = Math.min(
                        left >= 0 ? plan.startTick(left) : Float.MAX_VALUE,
                        right >= 0 ? plan.startTick(right) : Float.MAX_VALUE);
                parting[count] = part == Float.MAX_VALUE ? 0.0F : part;
                lastParting = Math.max(lastParting, parting[count]);
                count++;
                if (place >= cut.length) {
                    break;
                }
                double fromOrigin = Math.hypot(dx, dz);
                place = Math.min(
                        cut.length,
                        place + (fromOrigin < 40.0D ? 0.5D : fromOrigin < 96.0D ? 1.0D : fromOrigin < 160.0D ? 2.0D : 4.0D));
            }
            lines[c] = new KingCrimsonShatterMesh.CutLine(
                    (float) cut.startX + 0.5F,
                    (float) cut.startZ + 0.5F,
                    (float) cut.dirX,
                    (float) cut.dirZ,
                    cut.startTick,
                    cut,
                    Arrays.copyOf(along, count),
                    Arrays.copyOf(height, count),
                    Arrays.copyOf(parting, count),
                    lastParting);
        }
        return lines;
    }

    // What the wind and the cracks stand on: the surface the user is on and can see, walked out from the feet. Past the columns that
    // were read, and in a user's own span where it is all one floor, the ground as before.
    private float surfaceAt(double dx, double dz) {
        if (surface != null) {
            int x = (int) Math.floor(dx + 0.5D);
            int z = (int) Math.floor(dz + 0.5D);
            if (Math.abs(x) <= columnHalf && Math.abs(z) <= columnHalf) {
                float height = surface[KingCrimsonShatterSurface.index(columnHalf, x, z)];
                if (!Float.isNaN(height)) return height;
            }
        }
        return groundAt(dx, dz);
    }

    private int standingPlaces(int dx, int dz, float[] into) {
        int x = originX + dx;
        int z = originZ + dz;
        int index = columnIndex(x, z);
        if (index < 0 || columnPlate[index] == NO_COLUMN) {
            return 0;
        }
        BlockState[] states = columnStates[index];
        int count = 0;
        for (int at = states.length - 1; at >= 0 && count < into.length; at--) {
            BlockState state = states[at];
            if (state.isAir() || !state.blocksMotion() && state.getFluidState().isEmpty()) {
                continue;
            }
            if (at + 1 < states.length && (states[at + 1].blocksMotion() || !states[at + 1].getFluidState().isEmpty())) {
                continue;
            }
            into[count++] = surfaceOf(state, x, columnBottom[index] + at, z);
        }
        return count;
    }

    // NaN where nothing is known of the ground.
    private float groundAt(double dx, double dz) {
        int x = originX + (int) Math.floor(dx + 0.5D);
        int z = originZ + (int) Math.floor(dz + 0.5D);
        int index = columnIndex(x, z);
        if (index >= 0 && columnPlate[index] != NO_COLUMN) {
            return columnGroundTop[index];
        }
        int cell = cellIndex(Math.floorDiv(x - originX, CELL), Math.floorDiv(z - originZ, CELL));
        return cell >= 0 && cellTop[cell] != NO_CELL ? cellTop[cell] + 1.0F : Float.NaN;
    }

    private KingCrimsonShatterMesh finish(
            long start, KingCrimsonShatterMesh.CutLine[] cutLines, KingCrimsonShatterWind wind) {
        int used = 0;
        for (PlateBuilder plate : plates) {
            if (plate != null) {
                used++;
            }
        }
        KingCrimsonShatterMesh.Plate[] built = new KingCrimsonShatterMesh.Plate[used];
        int solid = 0;
        int cutout = 0;
        int flat = 0;
        int at = 0;
        for (PlateBuilder plate : plates) {
            if (plate != null) {
                KingCrimsonShatterMesh.Plate done = plate.build();
                built[at++] = done;
                solid += done.solid.vertexCount();
                cutout += done.cutout.vertexCount();
                flat += done.flat.vertexCount();
            }
        }
        return new KingCrimsonShatterMesh(
                built,
                cutLines,
                wind,
                Arrays.copyOf(debris, debrisCount * KingCrimsonShatterMesh.DEBRIS_STRIDE),
                Arrays.copyOf(debrisColors, debrisCount * 6),
                Arrays.copyOf(debrisLights, debrisCount),
                Arrays.copyOf(debrisLevels, debrisCount),
                originX,
                originY,
                originZ,
                solid,
                cutout,
                flat,
                coarseCells,
                (System.nanoTime() - start) / 1_000_000L,
                footprint());
    }

    private rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface.NearFootprint footprint() {
        int minX = originX - columnHalf;
        int minZ = originZ - columnHalf;
        int side = columnSide;
        float[] tops = representedTops;
        return new rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface.NearFootprint(
                minX, minZ, minX + side, minZ + side, (x, z) -> tops[x - minX + (z - minZ) * side]);
    }

    // A user underground, or in a dimension with a ceiling, cannot use the surface.
    private boolean aroundOwnHeight() {
        if (level.dimensionType().hasCeiling()) {
            return true;
        }
        int deep = 0;
        int asked = 0;
        for (int dz = -8; dz <= 8; dz += 8) {
            for (int dx = -8; dx <= 8; dx += 8) {
                int x = originX + dx;
                int z = originZ + dz;
                LevelChunk chunk = chunkAt(x >> 4, z >> 4);
                if (chunk == null) {
                    continue;
                }
                asked++;
                if (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > originY + UNDERGROUND_DEPTH) {
                    deep++;
                }
            }
        }
        return asked > 0 && deep * 3 >= asked * 2;
    }

    private LevelChunk chunkAt(int chunkX, int chunkZ) {
        int ox = chunkX - chunkMinX;
        int oz = chunkZ - chunkMinZ;
        if (ox < 0 || oz < 0 || ox >= chunkSide || oz >= chunkSide) {
            return null;
        }
        int index = ox + oz * chunkSide;
        if (!chunkRead[index]) {
            chunkRead[index] = true;
            chunks[index] = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        }
        return chunks[index];
    }

    private long[] sampleTerrain() {
        long[] order = new long[cellSide * cellSide];
        int count = 0;
        for (int cj = -cellHalf; cj <= cellHalf; cj++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            for (int ci = -cellHalf; ci <= cellHalf; ci++) {
                int middleX = ci * CELL + CELL / 2;
                int middleZ = cj * CELL + CELL / 2;
                long squared = (long) middleX * middleX + (long) middleZ * middleZ;
                if (squared > (long) range * range) {
                    continue;
                }
                boolean near = squared <= (long) nearRadius * nearRadius;
                if (near) {
                    int x0 = originX + ci * CELL;
                    int z0 = originZ + cj * CELL;
                    for (int z = z0; z < z0 + CELL; z++) {
                        for (int x = x0; x < x0 + CELL; x++) {
                            sampleColumn(x, z);
                        }
                    }
                }
                if (!span) {
                    sampleCell(ci, cj);
                }
                order[count++] = squared << 32 | ((ci + cellHalf) + (cj + cellHalf) * cellSide);
            }
        }
        return Arrays.copyOf(order, count);
    }

    private int columnIndex(int x, int z) {
        int ox = x - originX + columnHalf;
        int oz = z - originZ + columnHalf;
        if (ox < 0 || oz < 0 || ox >= columnSide || oz >= columnSide) {
            return -1;
        }
        return ox + oz * columnSide;
    }

    private int cellIndex(int ci, int cj) {
        int oi = ci + cellHalf;
        int oj = cj + cellHalf;
        if (oi < 0 || oj < 0 || oi >= cellSide || oj >= cellSide) {
            return -1;
        }
        return oi + oj * cellSide;
    }

    private void sampleColumn(int x, int z) {
        int plate = plan.plateAt(x, z);
        LevelChunk chunk = plate < 0 ? null : chunkAt(x >> 4, z >> 4);
        if (chunk == null) {
            return;
        }
        int dx = x - originX;
        int dz = z - originZ;
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        int top;
        int bottom;
        int ground;
        if (span) {
            top = Math.min(maxY, originY + SPAN_UP);
            bottom = Math.max(minY, originY - SPAN_DOWN);
            ground = Integer.MIN_VALUE;
        } else {
            top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (top < minY) {
                return;
            }
            ground = findGround(chunk, x, z, top);
            bottom = Math.max(minY, ground - depth(distance, plate));
        }
        BlockState[] states = new BlockState[top - bottom + 1];
        for (int y = top; y >= bottom; y--) {
            states[y - bottom] = chunk.getBlockState(pos.set(x, y, z));
        }
        if (span) {
            for (int y = Math.min(originY, top); y >= bottom; y--) {
                if (states[y - bottom].isSolidRender(level, pos.set(x, y, z))) {
                    ground = y;
                    break;
                }
            }
        }
        int index = columnIndex(x, z);
        columnPlate[index] = plate;
        columnTop[index] = top;
        columnBottom[index] = bottom;
        columnStates[index] = states;
        columnLight[index] = surfaceLight(x, ground == Integer.MIN_VALUE ? originY - 1 : ground, top, z);
        if (ground == Integer.MIN_VALUE) {
            columnGroundTop[index] = Float.NaN;
            return;
        }
        float groundTop = surfaceOf(states[ground - bottom], x, ground, z);
        columnGroundTop[index] = groundTop;
        groundSum[plate] += groundTop;
        groundCount[plate]++;
    }

    // What a face takes when the block next to it holds no light, as a wall a cut opened in the ground: the light on the
    // ground above it, or above whatever was built on that ground.
    private int surfaceLight(int x, int ground, int top, int z) {
        int last = span ? ground + 1 : top + 1;
        for (int y = ground + 1; y <= last; y++) {
            int light = LevelRenderer.getLightColor(level, pos.set(x, y, z));
            if (light != 0) {
                return light;
            }
        }
        return 0;
    }

    private int faceLight(BlockState state, int x, int y, int z, Direction direction, int fallback) {
        neighborPos.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
        int light = LevelRenderer.getLightColor(level, state, neighborPos);
        return light != 0 ? light : fallback;
    }

    private boolean isCutWall(int plate, int x, int z, Direction direction) {
        int index = columnIndex(x + direction.getStepX(), z + direction.getStepZ());
        return index < 0 || columnPlate[index] != plate;
    }

    private float surfaceOf(BlockState state, int x, int y, int z) {
        if (!state.getFluidState().isEmpty()) {
            return y + FLUID_TOP;
        }
        if (state.isSolidRender(level, pos.set(x, y, z))) {
            return y + 1.0F;
        }
        var shape = state.getShape(level, pos);
        return y + (shape.isEmpty() ? 1.0F : (float) shape.max(Direction.Axis.Y));
    }

    private int findGround(LevelChunk chunk, int x, int z, int top) {
        int run = 0;
        int lowest = Math.max(minY, top - SCAN_BLOCKS);
        for (int y = top; y >= lowest; y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (isNaturalGround(state)) {
                return y;
            }
            run = state.isSolidRender(level, pos) ? run + 1 : 0;
            if (run >= SOLID_RUN) {
                return y + SOLID_RUN - 1;
            }
        }
        return lowest;
    }

    private boolean isNaturalGround(BlockState state) {
        return !state.getFluidState().isEmpty()
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(BlockTags.ICE)
                || state.is(Tags.Blocks.ORES)
                || state.is(Tags.Blocks.END_STONES)
                || state.is(Blocks.BEDROCK)
                || state.isSolidRender(level, pos) && KingCrimsonShatterSurface.isModdedGround(
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE),
                        true,
                        isGrownOrBuilt(state));
    }

    private static boolean isGrownOrBuilt(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOOL)
                || state.is(BlockTags.SLABS)
                || state.is(BlockTags.STAIRS)
                || state.is(BlockTags.WALLS)
                || state.is(BlockTags.FENCES)
                || state.is(Tags.Blocks.GLASS_BLOCKS);
    }

    private static int depth(int distance, int plate) {
        int base = distance < 24 ? 5 : distance < 56 ? 4 : 3;
        return base + (plate * 31 >> 2 & 1);
    }

    // Coarse: 4 blocks, 8 from far enough out; four cells of 4 then share what the first of them read.
    private static boolean isDouble(int ci, int cj) {
        int anchorX = (ci & ~1) * CELL + CELL;
        int anchorZ = (cj & ~1) * CELL + CELL;
        return (long) anchorX * anchorX + (long) anchorZ * anchorZ
                > (long) DOUBLE_CELL_RADIUS * DOUBLE_CELL_RADIUS;
    }

    private void sampleCell(int ci, int cj) {
        boolean twice = isDouble(ci, cj);
        int size = twice ? CELL * 2 : CELL;
        int anchorI = twice ? ci & ~1 : ci;
        int anchorJ = twice ? cj & ~1 : cj;
        int index = cellIndex(ci, cj);
        if (twice && (ci != anchorI || cj != anchorJ)) {
            int anchor = cellIndex(anchorI, anchorJ);
            if (anchor >= 0 && cellTop[anchor] != NO_CELL) {
                cellTop[index] = cellTop[anchor];
                cellPlate[index] = cellPlate[anchor];
                cellColor[index] = cellColor[anchor];
                cellSideColor[index] = cellSideColor[anchor];
                cellLight[index] = cellLight[anchor];
                return;
            }
        }
        int x = originX + anchorI * CELL + size / 2;
        int z = originZ + anchorJ * CELL + size / 2;
        int plate = plan.plateAt(x, z);
        if (plate < 0 || !farSource.sample(x, z, farCell)) {
            return;
        }
        cellTop[index] = farCell.top;
        cellPlate[index] = plate;
        cellColor[index] = shadeRgb(farCell.color, 0.97F + 0.06F * (float) random.nextDouble());
        cellSideColor[index] = farCell.sideColor;
        cellLight[index] = farCell.light;
        if (farCell.natural) {
            groundSum[plate] += farCell.top + 1.0D;
            groundCount[plate]++;
        }
    }

    // The far ring's only source for now: the client's own chunks.
    private final class ChunkFarSource implements KingCrimsonShatterFarSource {
        @Override
        public boolean sample(int x, int z, Cell cell) {
            LevelChunk chunk = chunkAt(x >> 4, z >> 4);
            if (chunk == null) {
                return false;
            }
            int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (top < minY) {
                return false;
            }
            BlockState state = chunk.getBlockState(pos.set(x, top, z));
            cell.top = top;
            cell.color = flatColor(state, x, top, z);
            BlockState below = chunk.getBlockState(pos.set(x, top - 1, z));
            cell.sideColor = below.isAir() ? shadeRgb(cell.color, 0.8F) : flatColor(below, x, top - 1, z);
            cell.natural = !state.is(BlockTags.LEAVES) && state.getFluidState().isEmpty();
            cell.light = LevelRenderer.getLightColor(level, pos.set(x, top + 1, z));
            return true;
        }
    }

    private PlateBuilder plateBuilder(int plate) {
        PlateBuilder builder = plates[plate];
        if (builder == null) {
            double y = groundCount[plate] > 0 ? groundSum[plate] / groundCount[plate] : originY;
            builder = new PlateBuilder(plate, plan.pivotX(plate), y, plan.pivotZ(plate));
            plates[plate] = builder;
        }
        return builder;
    }

    private void emitNearCell(int ci, int cj, int distance) {
        int x0 = originX + ci * CELL;
        int z0 = originZ + cj * CELL;
        boolean mergedBottom = distance > BOTTOM_DETAIL_RADIUS && !span;
        for (int z = z0; z < z0 + CELL; z++) {
            for (int x = x0; x < x0 + CELL; x++) {
                emitColumn(x, z, mergedBottom);
            }
        }
        if (mergedBottom) {
            emitMergedBottom(x0, z0);
        }
    }

    private void emitMergedBottom(int x0, int z0) {
        int plate = columnPlate[Math.max(0, columnIndex(x0, z0))];
        boolean uniform = plate != NO_COLUMN;
        int lowest = Integer.MAX_VALUE;
        for (int z = z0; z < z0 + CELL && uniform; z++) {
            for (int x = x0; x < x0 + CELL; x++) {
                int index = columnIndex(x, z);
                if (index < 0 || columnPlate[index] != plate) {
                    uniform = false;
                    break;
                }
                lowest = Math.min(lowest, columnBottom[index]);
            }
        }
        if (uniform) {
            PlateBuilder builder = plateBuilder(plate);
            builder.atColumn(x0 + CELL / 2, z0 + CELL / 2);
            int index = columnIndex(x0, z0);
            BlockState state = columnStates[index][0];
            if (!texturedDown(builder, state, x0, x0 + CELL, lowest, z0, z0 + CELL, columnGroundTop[index], columnLight[index])) {
                int color = state.isAir() ? 0xFF3B2A1E : flatColor(state, x0, columnBottom[index], z0);
                flatDown(builder, x0, x0 + CELL, lowest, z0, z0 + CELL, color, columnLight[index]);
            }
            return;
        }
        for (int z = z0; z < z0 + CELL; z++) {
            for (int x = x0; x < x0 + CELL; x++) {
                int index = columnIndex(x, z);
                if (index >= 0 && columnPlate[index] != NO_COLUMN) {
                    BlockState state = columnStates[index][0];
                    PlateBuilder builder = plateBuilder(columnPlate[index]);
                    builder.atColumn(x, z);
                    if (!texturedDown(builder, state, x, x + 1, columnBottom[index], z, z + 1, columnGroundTop[index], columnLight[index])) {
                        int color = state.isAir() ? 0xFF3B2A1E : flatColor(state, x, columnBottom[index], z);
                        flatDown(builder, x, x + 1, columnBottom[index], z, z + 1, color, columnLight[index]);
                    }
                }
            }
        }
    }

    // A merged underside in one quad of the block's own texture, stretched over the cell and darkened as the wall above it is at that
    // depth: a flat colour there read as a pale tile when a plate turned its underside to the camera. False when the block has no face.
    private boolean texturedDown(
            PlateBuilder plate, BlockState state, int x0, int x1, int y, int z0, int z1, float groundTop, int light) {
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return false;
        }
        BakedModel model = shaper.getBlockModel(state);
        modelRandom.setSeed(42L);
        List<BakedQuad> quads = model.getQuads(state, Direction.DOWN, modelRandom, ModelData.EMPTY, null);
        TextureAtlasSprite sprite = quads.isEmpty() ? model.getParticleIcon(ModelData.EMPTY) : quads.get(0).getSprite();
        if (sprite == null) {
            return false;
        }
        int color = WHITE;
        if (!quads.isEmpty() && quads.get(0).isTinted()) {
            pos.set(x0, y, z0);
            color = tintOf(state, quads.get(0).getTintIndex());
        }
        if (!Float.isNaN(groundTop)) {
            color = depthShade(color, groundTop - y);
        }
        float ax = (float) (x0 - plate.pivotX);
        float bx = (float) (x1 - plate.pivotX);
        float fy = (float) (y - plate.pivotY);
        float az = (float) (z0 - plate.pivotZ);
        float bz = (float) (z1 - plate.pivotZ);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        KingCrimsonShatterMesh.LayerBuilder layer = plate.solid;
        layer.level = (byte) KingCrimsonShatterPlateLight.code(
                radianceOf(sprite) * KingCrimsonShatterPlateLight.radiance(color), state.getLightEmission());
        layer.quad(
                NORMAL_DOWN,
                light,
                ax, fy, bz, u0, v1, color,
                ax, fy, az, u0, v0, color,
                bx, fy, az, u1, v0, color,
                bx, fy, bz, u1, v1, color);
        vertices += 4;
        plate.include(ax, fy, az);
        plate.include(bx, fy, bz);
        return true;
    }

    private void flatDown(PlateBuilder plate, int x0, int x1, int y, int z0, int z1, int color, int light) {
        float ax = (float) (x0 - plate.pivotX);
        float bx = (float) (x1 - plate.pivotX);
        float fy = (float) (y - plate.pivotY);
        float az = (float) (z0 - plate.pivotZ);
        float bz = (float) (z1 - plate.pivotZ);
        plate.flat.level = flatLevel(color);
        plate.flat.quad(
                NORMAL_DOWN,
                light,
                ax, fy, bz, 0.5F, 0.5F, color,
                ax, fy, az, 0.5F, 0.5F, color,
                bx, fy, az, 0.5F, 0.5F, color,
                bx, fy, bz, 0.5F, 0.5F, color);
        vertices += 4;
        plate.include(ax, fy, az);
        plate.include(bx, fy, bz);
    }

    private void emitColumn(int x, int z, boolean mergedBottom) {
        int index = columnIndex(x, z);
        if (index < 0 || columnPlate[index] == NO_COLUMN) {
            return;
        }
        int plate = columnPlate[index];
        int dx = x - originX;
        int dz = z - originZ;
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        PlateBuilder builder = plateBuilder(plate);
        builder.atColumn(x, z);
        BlockState[] states = columnStates[index];
        int bottom = columnBottom[index];
        int top = columnTop[index];
        float groundTop = columnGroundTop[index];
        builder.include((float) (x - builder.pivotX), (float) (top + 1 - builder.pivotY), (float) (z - builder.pivotZ));
        builder.include((float) (x + 1 - builder.pivotX), (float) (bottom - builder.pivotY), (float) (z + 1 - builder.pivotZ));
        builder.span(Float.isNaN(groundTop) ? top + 1.0F : groundTop, bottom);
        boolean bareEdge = !span && !Float.isNaN(groundTop) && distance > range - BARE_EDGE_BLOCKS;
        int highestEmittedY = Integer.MIN_VALUE;
        for (int y = bareEdge ? Math.min(top, (int) Math.ceil(groundTop) - 1) : top; y >= bottom; y--) {
            BlockState state = states[y - bottom];
            if (state.isAir()) {
                continue;
            }
            FluidState fluid = state.getFluidState();
            boolean wet = !fluid.isEmpty();
            if (!wet && !state.blocksMotion() && distance > PLANT_RADIUS) {
                continue;
            }
            highestEmittedY = Math.max(highestEmittedY, y);
            pos.set(x, y, z);
            boolean aboveWet = y < top && !states[y + 1 - bottom].getFluidState().isEmpty();
            if (wet) {
                emitFluid(builder, fluid, x, y, z, plate, aboveWet, y == bottom && mergedBottom, columnLight[index]);
                if (aboveWet) {
                    continue;
                }
            }
            if (state.getRenderShape() == RenderShape.MODEL) {
                emitModel(builder, state, x, y, z, plate, groundTop, y == bottom && mergedBottom, columnLight[index]);
            }
        }
        representedTops[index] = rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface
                .knownGroundTop(groundTop, highestEmittedY);
        if (!Float.isNaN(groundTop)) {
            maybeBorderDebris(plate, x, z, groundTop, distance);
            maybeRisingBlock(plate, x, z, bottom, states, distance);
            maybeLooseBlock(plate, x, z, groundTop, bottom, top, states, distance, columnLight[index]);
        }
    }

    private BlockState neighbor(int plate, int x, int y, int z, Direction direction) {
        int index = columnIndex(x + direction.getStepX(), z + direction.getStepZ());
        if (index < 0 || columnPlate[index] != plate) {
            return null;
        }
        int ny = y + direction.getStepY();
        int bottom = columnBottom[index];
        if (ny < bottom || ny > columnTop[index]) {
            return null;
        }
        return columnStates[index][ny - bottom];
    }

    // The face shows when the neighbour is air, does not hide it, or is not there: in another plate or out of the span.
    private boolean faceShows(BlockState state, int x, int y, int z, Direction direction, BlockState neighbor) {
        if (neighbor == null || neighbor.isAir()) {
            return true;
        }
        if (!neighbor.getFluidState().isEmpty() && !neighbor.canOcclude()) {
            return false;
        }
        if (state.getBlock() == neighbor.getBlock() && state.is(BlockTags.LEAVES)) {
            return false;
        }
        neighborPos.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
        if (neighbor.isSolidRender(level, neighborPos)) {
            return false;
        }
        return Block.shouldRenderFace(state, level, pos, direction, neighborPos);
    }

    private void emitModel(
            PlateBuilder builder,
            BlockState state,
            int x,
            int y,
            int z,
            int plate,
            float groundTop,
            boolean skipDown,
            int surfaceLight) {
        BakedModel model = shaper.getBlockModel(state);
        long seed = state.getSeed(pos);
        float lx = (float) (x - builder.pivotX);
        float ly = (float) (y - builder.pivotY);
        float lz = (float) (z - builder.pivotZ);
        if (state.hasOffsetFunction()) {
            Vec3 offset = state.getOffset(level, pos);
            lx += (float) offset.x;
            ly += (float) offset.y;
            lz += (float) offset.z;
        }
        modelRandom.setSeed(seed);
        ChunkRenderTypeSet types = model.getRenderTypes(state, modelRandom, ModelData.EMPTY);
        float depthBase = Float.isNaN(groundTop) ? y + 1.0F : groundTop;
        for (RenderType type : types) {
            KingCrimsonShatterMesh.LayerBuilder layer = type == RenderType.solid() ? builder.solid : builder.cutout;
            for (Direction direction : DIRECTIONS) {
                if (skipDown && direction == Direction.DOWN) {
                    continue;
                }
                BlockState next = neighbor(plate, x, y, z, direction);
                if (!faceShows(state, x, y, z, direction, next)) {
                    continue;
                }
                modelRandom.setSeed(seed);
                List<BakedQuad> quads = model.getQuads(state, direction, modelRandom, ModelData.EMPTY, type);
                if (!quads.isEmpty()) {
                    boolean horizontal = direction.getAxis().isHorizontal();
                    emitQuads(
                            quads, layer, state, lx, ly, lz, horizontal, depthBase, builder.pivotY,
                            faceLight(state, x, y, z, direction, surfaceLight),
                            horizontal && isCutWall(plate, x, z, direction));
                }
            }
            modelRandom.setSeed(seed);
            List<BakedQuad> loose = model.getQuads(state, null, modelRandom, ModelData.EMPTY, type);
            if (!loose.isEmpty()) {
                int own = LevelRenderer.getLightColor(level, state, pos);
                emitQuads(loose, layer, state, lx, ly, lz, false, depthBase, builder.pivotY, own != 0 ? own : surfaceLight, false);
            }
        }
    }

    private void emitQuads(
            List<BakedQuad> quads,
            KingCrimsonShatterMesh.LayerBuilder layer,
            BlockState state,
            float lx,
            float ly,
            float lz,
            boolean darkenWithDepth,
            float depthBase,
            double pivotY,
            int light,
            boolean cutWall) {
        int tintIndex = -1;
        int tint = WHITE;
        int emission = state.getLightEmission();
        for (int q = 0, count = quads.size(); q < count; q++) {
            BakedQuad quad = quads.get(q);
            int color = WHITE;
            if (quad.isTinted()) {
                if (quad.getTintIndex() != tintIndex) {
                    tintIndex = quad.getTintIndex();
                    tint = tintOf(state, tintIndex);
                }
                color = tint;
            }
            layer.level = (byte) KingCrimsonShatterPlateLight.code(
                    radianceOf(quad.getSprite()) * KingCrimsonShatterPlateLight.radiance(color), emission);
            int[] v = quad.getVertices();
            float y0 = Float.intBitsToFloat(v[1]) + ly;
            float y1 = Float.intBitsToFloat(v[9]) + ly;
            float y2 = Float.intBitsToFloat(v[17]) + ly;
            float y3 = Float.intBitsToFloat(v[25]) + ly;
            int c0 = color;
            int c1 = color;
            int c2 = color;
            int c3 = color;
            if (darkenWithDepth) {
                float ground = (float) (depthBase - pivotY);
                c0 = depthShade(color, ground - y0);
                c1 = depthShade(color, ground - y1);
                c2 = depthShade(color, ground - y2);
                c3 = depthShade(color, ground - y3);
            }
            layer.quad(
                    !quad.isShade() ? NORMAL_UP : cutWall ? NORMAL_WALL : quad.getDirection().ordinal(),
                    light,
                    Float.intBitsToFloat(v[0]) + lx, y0, Float.intBitsToFloat(v[2]) + lz,
                    Float.intBitsToFloat(v[4]), Float.intBitsToFloat(v[5]), c0,
                    Float.intBitsToFloat(v[8]) + lx, y1, Float.intBitsToFloat(v[10]) + lz,
                    Float.intBitsToFloat(v[12]), Float.intBitsToFloat(v[13]), c1,
                    Float.intBitsToFloat(v[16]) + lx, y2, Float.intBitsToFloat(v[18]) + lz,
                    Float.intBitsToFloat(v[20]), Float.intBitsToFloat(v[21]), c2,
                    Float.intBitsToFloat(v[24]) + lx, y3, Float.intBitsToFloat(v[26]) + lz,
                    Float.intBitsToFloat(v[28]), Float.intBitsToFloat(v[29]), c3);
            vertices += 4;
        }
    }

    // A texture's radiance as the pack's emissive law weighs it: each pixel's light goes with its own radiance, so that a texture of
    // pale and dark pixels counts by its pale ones.
    private float radianceOf(TextureAtlasSprite sprite) {
        if (sprite == lastSprite) {
            return lastSpriteRadiance;
        }
        Float known = spriteRadiance.get(sprite);
        if (known == null) {
            int width = sprite.contents().width();
            int height = sprite.contents().height();
            double sum = 0.0D;
            double squares = 0.0D;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = sprite.getPixelRGBA(0, x, y);
                    double alpha = (pixel >>> 24) / 255.0D;
                    double radiance = KingCrimsonShatterPlateLight.radiance(pixel);
                    sum += alpha * radiance;
                    squares += alpha * radiance * radiance;
                }
            }
            known = sum > 0.0D ? (float) (squares / sum) : 0.0F;
            spriteRadiance.put(sprite, known);
        }
        lastSprite = sprite;
        lastSpriteRadiance = known;
        return known;
    }

    private static byte flatLevel(int color) {
        return (byte) KingCrimsonShatterPlateLight.code(KingCrimsonShatterPlateLight.radiance(color), 0);
    }

    // The tint at the current block. The faces of a block take one answer; the cells are the emission's, so a cell asks once.
    private int tintOf(BlockState state, int tintIndex) {
        int dx = pos.getX() - originX;
        int dz = pos.getZ() - originZ;
        long place = dx * dx + dz * dz <= TINT_EXACT_RADIUS * TINT_EXACT_RADIUS
                ? BlockPos.asLong(dx, pos.getY(), dz)
                : BlockPos.asLong(dx >> TINT_CELL_SHIFT, TINT_ANY_HEIGHT, dz >> TINT_CELL_SHIFT);
        TintMemo memo = tintMemos.computeIfAbsent(state, key -> new TintMemo());
        if (memo.place != place || memo.tintIndex != tintIndex) {
            memo.place = place;
            memo.tintIndex = tintIndex;
            memo.tint = 0xFF000000 | blockColors.getColor(state, level, pos, tintIndex);
        }
        return memo.tint;
    }

    private static final class TintMemo {
        private long place = Long.MIN_VALUE;
        private int tintIndex;
        private int tint;
    }

    // A plate's dirt side darkens downward, so that it reads as depth without any light on it.
    private static int depthShade(int color, float below) {
        if (below <= 0.0F) {
            return color;
        }
        return shadeRgb(color, 1.0F - 0.075F * Math.min(below, 6.0F));
    }

    private static int shadeRgb(int color, float factor) {
        int r = Math.min(255, (int) ((color >> 16 & 255) * factor));
        int g = Math.min(255, (int) ((color >> 8 & 255) * factor));
        int b = Math.min(255, (int) ((color & 255) * factor));
        return color & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void emitFluid(
            PlateBuilder builder,
            FluidState fluid,
            int x,
            int y,
            int z,
            int plate,
            boolean aboveWet,
            boolean skipDown,
            int surfaceLight) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = atlas.apply(extensions.getStillTexture(fluid, level, pos));
        int tint = fluidShade(fluid, 0xFF000000 | extensions.getTintColor(fluid, level, pos));
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float ax = (float) (x - builder.pivotX);
        float bx = ax + 1.0F;
        float ay = (float) (y - builder.pivotY);
        float by = ay + (aboveWet ? 1.0F : FLUID_TOP);
        float az = (float) (z - builder.pivotZ);
        float bz = az + 1.0F;
        KingCrimsonShatterMesh.LayerBuilder layer = builder.solid;
        int light = faceLight(fluid.createLegacyBlock(), x, y, z, Direction.UP, surfaceLight);
        layer.level = (byte) KingCrimsonShatterPlateLight.code(
                radianceOf(sprite) * KingCrimsonShatterPlateLight.radiance(tint), fluid.createLegacyBlock().getLightEmission());
        if (!aboveWet) {
            BlockState above = neighbor(plate, x, y, z, Direction.UP);
            if (above == null || !above.isSolidRender(level, pos)) {
                layer.quad(
                        Direction.UP.ordinal(),
                        light,
                        ax, by, az, u0, v0, tint,
                        ax, by, bz, u0, v1, tint,
                        bx, by, bz, u1, v1, tint,
                        bx, by, az, u1, v0, tint);
                vertices += 4;
            }
        }
        if (!skipDown && neighbor(plate, x, y, z, Direction.DOWN) == null) {
            layer.quad(
                    Direction.DOWN.ordinal(),
                    light,
                    ax, ay, bz, u0, v0, tint,
                    ax, ay, az, u0, v1, tint,
                    bx, ay, az, u1, v1, tint,
                    bx, ay, bz, u1, v0, tint);
            vertices += 4;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState next = neighbor(plate, x, y, z, direction);
            if (next != null && (!next.getFluidState().isEmpty() || next.canOcclude())) {
                continue;
            }
            int normal = isCutWall(plate, x, z, direction) ? NORMAL_WALL : direction.ordinal();
            switch (direction) {
                case NORTH -> layer.quad(
                        normal,
                        light,
                        bx, by, az, u0, v0, tint,
                        bx, ay, az, u0, v1, tint,
                        ax, ay, az, u1, v1, tint,
                        ax, by, az, u1, v0, tint);
                case SOUTH -> layer.quad(
                        normal,
                        light,
                        ax, by, bz, u0, v0, tint,
                        ax, ay, bz, u0, v1, tint,
                        bx, ay, bz, u1, v1, tint,
                        bx, by, bz, u1, v0, tint);
                case WEST -> layer.quad(
                        normal,
                        light,
                        ax, by, az, u0, v0, tint,
                        ax, ay, az, u0, v1, tint,
                        ax, ay, bz, u1, v1, tint,
                        ax, by, bz, u1, v0, tint);
                default -> layer.quad(
                        normal,
                        light,
                        bx, by, bz, u0, v0, tint,
                        bx, ay, bz, u0, v1, tint,
                        bx, ay, az, u1, v1, tint,
                        bx, by, az, u1, v0, tint);
            }
            vertices += 4;
        }
    }

    // Opaque water would look far brighter than real water, which lies over the dark of the deep.
    private static int fluidShade(FluidState fluid, int tint) {
        return fluid.is(FluidTags.WATER) ? shadeRgb(tint, WATER_SHADE) : tint;
    }

    // Roundabout's rising blocks: along the cracks once a plate parts there.
    private void maybeBorderDebris(int plate, int x, int z, float groundTop, int distance) {
        if (distance > DEBRIS_RADIUS
                || distance < DEBRIS_INNER_RADIUS
                || crackDebrisCount >= MAX_CRACK_DEBRIS
                || random.nextDouble() > CRACK_DEBRIS_CHANCE * debrisShare(x, z, distance)) {
            return;
        }
        boolean border = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int next = columnIndex(x + direction.getStepX(), z + direction.getStepZ());
            if (next >= 0 && columnPlate[next] != NO_COLUMN && columnPlate[next] != plate) {
                border = true;
                break;
            }
        }
        if (!border) {
            return;
        }
        int index = columnIndex(x, z);
        int ground = (int) Math.floor(groundTop - 0.01F);
        BlockState state = columnStates[index][Math.max(0, Math.min(columnTop[index], ground) - columnBottom[index])];
        int before = debrisCount;
        addDebris(state, x + (float) random.nextDouble(), groundTop, z + (float) random.nextDouble(),
                KingCrimsonShatterPlan.debrisSpawnTick(plan.startTick(plate), (float) random.nextDouble()), columnLight[index], plate);
        crackDebrisCount += debrisCount - before;
    }

    // From a plate's underside, once the plate has left: a block that rose through whole ground would show through it.
    private void maybeRisingBlock(int plate, int x, int z, int bottom, BlockState[] states, int distance) {
        if (distance > DEBRIS_RADIUS
                || distance < DEBRIS_INNER_RADIUS
                || random.nextDouble() > (distance < UNDERSIDE_DEBRIS_NEAR_RADIUS ? UNDERSIDE_DEBRIS_NEAR_CHANCE : UNDERSIDE_DEBRIS_CHANCE)
                        * debrisShare(x, z, distance)) {
            return;
        }
        BlockState state = states[0];
        if (state.isAir()) {
            return;
        }
        addDebris(state, x + (float) random.nextDouble(), bottom - (float) random.nextDouble() * UNDERSIDE_DEBRIS_BELOW, z + (float) random.nextDouble(),
                KingCrimsonShatterPlan.debrisSpawnTick(
                        plan.startTick(plate) + UNDERSIDE_DEBRIS_AFTER, (float) Math.pow(random.nextDouble(), UNDERSIDE_DEBRIS_EARLY)),
                columnLight[columnIndex(x, z)],
                plate);
    }

    // Roundabout's rising blocks, smaller and mixed in size.
    private float debrisShare(int x, int z, int distance) {
        double facing = ((x - originX) * plan.facingX() + (z - originZ) * plan.facingZ()) / Math.max(1, distance);
        return facing >= DEBRIS_FRONT_FROM ? 1.0F : DEBRIS_BEHIND_SHARE;
    }

    // A whole block of the ground off the top of its plate, sitting on the surface as it leaves, then floating up as if it weighed
    // nothing. Only from open ground: a block that rose through a trunk or a roof would show through it.
    private void maybeLooseBlock(
            int plate, int x, int z, float groundTop, int bottom, int top, BlockState[] states, int distance, int light) {
        if (!looseBlocks
                || distance > LOOSE_BLOCK_RADIUS
                || distance < LOOSE_BLOCK_INNER_RADIUS
                || looseBlockCount >= MAX_LOOSE_BLOCKS
                || looseRandom.nextDouble() > LOOSE_BLOCK_CHANCE * debrisShare(x, z, distance)) {
            return;
        }
        int ground = (int) Math.floor(groundTop - 0.01F);
        if (ground < bottom || ground > top) {
            return;
        }
        for (int y = ground + 1; y <= top; y++) {
            BlockState above = states[y - bottom];
            if (above.blocksMotion() || !above.getFluidState().isEmpty()) {
                return;
            }
        }
        BlockState state = states[ground - bottom];
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        SplittableRandom r = looseRandom;
        float size = LOOSE_BLOCK_SIZE_MIN + (float) r.nextDouble() * (LOOSE_BLOCK_SIZE_MAX - LOOSE_BLOCK_SIZE_MIN);
        float cx = x + 0.5F + ((float) r.nextDouble() * 2.0F - 1.0F) * LOOSE_BLOCK_OFF_CENTRE;
        float cz = z + 0.5F + ((float) r.nextDouble() * 2.0F - 1.0F) * LOOSE_BLOCK_OFF_CENTRE;
        float wanted = plan.startTick(plate) + LOOSE_BLOCK_AFTER_MIN
                + (float) r.nextDouble() * (LOOSE_BLOCK_AFTER_MAX - LOOSE_BLOCK_AFTER_MIN);
        float spawn = Math.min(wanted, plan.tearStartTick(plate));
        float rise = LOOSE_BLOCK_RISE_MIN + (float) r.nextDouble() * (LOOSE_BLOCK_RISE_MAX - LOOSE_BLOCK_RISE_MIN);
        float spin = (float) (LOOSE_BLOCK_SPIN_MIN + r.nextDouble() * (LOOSE_BLOCK_SPIN_MAX - LOOSE_BLOCK_SPIN_MIN))
                * (r.nextDouble() < 0.5D ? -1.0F : 1.0F);
        float drift = (float) r.nextDouble() * LOOSE_BLOCK_DRIFT_MAX;
        double turn = (r.nextDouble() * 2.0D - 1.0D) * DEBRIS_TURN_MOST;
        float axisX = (float) r.nextDouble() * 2.0F - 1.0F;
        float axisY = (float) r.nextDouble() * 2.0F - 1.0F;
        float axisZ = (float) r.nextDouble() * 2.0F - 1.0F;
        float endFraction = (float) r.nextDouble();
        int at = debrisCount * KingCrimsonShatterMesh.DEBRIS_STRIDE;
        placeDebris(at, state, cx, groundTop + size * 0.5F, cz, spawn, plate, size, rise, KingCrimsonShatterPlan.LOOSE_RISE_GROWTH,
                axisX, axisY, axisZ, spin, turn, drift, endFraction, light);
        looseBlockCount++;
    }

    private void addDebris(BlockState state, float x, float y, float z, float wantedSpawn, int light, int plate) {
        if (smallDebrisCount >= MAX_DEBRIS || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        int at = debrisCount * KingCrimsonShatterMesh.DEBRIS_STRIDE;
        float small = (float) random.nextDouble();
        float axisX = (float) random.nextDouble() * 2.0F - 1.0F;
        float axisY = (float) random.nextDouble() * 2.0F - 1.0F;
        float axisZ = (float) random.nextDouble() * 2.0F - 1.0F;
        // Not from a plate that has already started to tear away.
        float spawn = Math.min(wantedSpawn, plan.tearStartTick(plate));
        float rise = 0.04F + 0.02F * (float) random.nextDouble();
        float spin = (float) Math.toRadians(1.0D + random.nextDouble() * 1.5D) * (random.nextDouble() < 0.5D ? -1.0F : 1.0F);
        double turn = (random.nextDouble() * 2.0D - 1.0D) * DEBRIS_TURN_MOST;
        float speed = DEBRIS_DRIFT_MIN + (float) random.nextDouble() * (DEBRIS_DRIFT_MAX - DEBRIS_DRIFT_MIN);
        float endFraction = (float) random.nextDouble();
        placeDebris(at, state, x, y, z, spawn, plate, 0.15F + 0.45F * small, rise, KingCrimsonShatterPlan.DEBRIS_RISE_GROWTH,
                axisX, axisY, axisZ, spin, turn, speed, endFraction, light);
        smallDebrisCount++;
    }

    // What every rising block keeps: where it leaves its plate, how it moves and turns, when it goes, and its faces' look.
    private void placeDebris(
            int at, BlockState state, float x, float y, float z, float spawn, int plate, float size, float rise, float growth,
            float axisX, float axisY, float axisZ, float spin, double turn, float speed, float endFraction, int light) {
        CubeFaces faces = cubeFacesOf(state);
        float length = Math.max(0.01F, (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ));
        // It leaves its plate wherever that plate has got to by then, not where the ground was.
        plan.pose(plate, spawn, debrisPose);
        PlateBuilder owner = plateBuilder(plate);
        double fromX = x - owner.pivotX;
        double fromY = y - owner.pivotY;
        double fromZ = z - owner.pivotZ;
        KingCrimsonShatterPlan.Pose p = debrisPose;
        debris[at] = (float) (owner.pivotX + p.dx + p.m00 * fromX + p.m01 * fromY + p.m02 * fromZ - originX);
        debris[at + 1] = (float) (owner.pivotY + p.dy + p.m10 * fromX + p.m11 * fromY + p.m12 * fromZ - originY);
        debris[at + 2] = (float) (owner.pivotZ + p.dz + p.m20 * fromX + p.m21 * fromY + p.m22 * fromZ - originZ);
        debris[at + 3] = size;
        debris[at + 4] = rise;
        debris[at + 5] = spawn;
        debris[at + 6] = axisX / length;
        debris[at + 7] = axisY / length;
        debris[at + 8] = axisZ / length;
        debris[at + 9] = spin;
        // Out from the origin at its own speed and a little off the radial, as the plates go.
        float fromOriginX = x - originX;
        float fromOriginZ = z - originZ;
        float fromOrigin = Math.max(0.01F, (float) Math.sqrt(fromOriginX * fromOriginX + fromOriginZ * fromOriginZ));
        float cos = (float) Math.cos(turn);
        float sin = (float) Math.sin(turn);
        debris[at + 10] = (fromOriginX * cos - fromOriginZ * sin) / fromOrigin * speed;
        debris[at + 11] = (fromOriginX * sin + fromOriginZ * cos) / fromOrigin * speed;
        debris[at + 12] = KingCrimsonShatterPlan.debrisEndTick(spawn, fromOrigin, endFraction);
        System.arraycopy(faces.uvs, 0, debris, at + 13, 24);
        debris[at + KingCrimsonShatterMesh.DEBRIS_GROWTH] = growth;
        debrisLights[debrisCount] = light;
        pos.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        for (int face = 0; face < 6; face++) {
            int tintIndex = faces.tintIndex[face];
            debrisColors[debrisCount * 6 + face] = tintIndex < 0
                    ? WHITE
                    : 0xFF000000 | blockColors.getColor(state, level, pos, tintIndex);
        }
        debrisLevels[debrisCount] = (byte) KingCrimsonShatterPlateLight.code(
                faces.radiance * KingCrimsonShatterPlateLight.radiance(debrisColors[debrisCount * 6 + Direction.UP.ordinal()]),
                state.getLightEmission());
        debrisCount++;
    }

    private CubeFaces cubeFacesOf(BlockState state) {
        CubeFaces known = cubeFaces.get(state);
        if (known != null) {
            return known;
        }
        BakedModel model = shaper.getBlockModel(state);
        float[] uvs = new float[24];
        int[] tints = new int[6];
        float radiance = 0.0F;
        TextureAtlasSprite fallback = model.getParticleIcon(ModelData.EMPTY);
        for (Direction direction : DIRECTIONS) {
            modelRandom.setSeed(42L);
            List<BakedQuad> quads = model.getQuads(state, direction, modelRandom, ModelData.EMPTY, null);
            BakedQuad quad = quads.isEmpty() ? null : quads.get(0);
            TextureAtlasSprite sprite = quad != null ? quad.getSprite() : fallback;
            int at = direction.ordinal();
            uvs[at * 4] = sprite.getU0();
            uvs[at * 4 + 1] = sprite.getV0();
            uvs[at * 4 + 2] = sprite.getU1();
            uvs[at * 4 + 3] = sprite.getV1();
            tints[at] = quad != null && quad.isTinted() ? quad.getTintIndex() : -1;
            radiance += radianceOf(sprite) / DIRECTIONS.length;
        }
        CubeFaces faces = new CubeFaces(uvs, tints, radiance);
        cubeFaces.put(state, faces);
        return faces;
    }

    private record CubeFaces(float[] uvs, int[] tintIndex, float radiance) {}

    private void emitFarCell(int ci, int cj) {
        boolean twice = !isNearCellFallback(ci, cj) && isDouble(ci, cj);
        if (twice && (ci & 1) != 0 | (cj & 1) != 0) {
            return;
        }
        int index = cellIndex(ci, cj);
        if (index < 0 || cellTop[index] == NO_CELL) {
            return;
        }
        int size = twice ? CELL * 2 : CELL;
        int top = cellTop[index];
        int plate = cellPlate[index];
        int color = cellColor[index];
        int sideColor = cellSideColor[index];
        int light = cellLight[index];
        PlateBuilder builder = plateBuilder(plate);
        int x0 = originX + ci * CELL;
        int z0 = originZ + cj * CELL;
        int x1 = x0 + size;
        int z1 = z0 + size;
        for (int z = z0; z < z1; ++z) {
            for (int x = x0; x < x1; ++x) {
                int owned = columnIndex(x, z);
                if (owned >= 0) representedTops[owned] = top + 1.0F;
            }
        }
        builder.atColumn(x0 + size / 2, z0 + size / 2);
        float ax = (float) (x0 - builder.pivotX);
        float bx = (float) (x1 - builder.pivotX);
        float az = (float) (z0 - builder.pivotZ);
        float bz = (float) (z1 - builder.pivotZ);
        float topY = (float) (top + 1 - builder.pivotY);
        int depth = 3 + (plate * 31 >> 2 & 1);
        float bottomY = topY - depth;
        builder.spanLocal(topY, bottomY);
        KingCrimsonShatterMesh.LayerBuilder flat = builder.flat;
        flat.level = flatLevel(color);
        flat.quad(
                NORMAL_UP,
                light,
                ax, topY, az, 0.5F, 0.5F, color,
                ax, topY, bz, 0.5F, 0.5F, color,
                bx, topY, bz, 0.5F, 0.5F, color,
                bx, topY, az, 0.5F, 0.5F, color);
        int bottomColor = shadeRgb(sideColor, 0.6F);
        flat.level = flatLevel(bottomColor);
        flat.quad(
                NORMAL_DOWN,
                light,
                ax, bottomY, bz, 0.5F, 0.5F, bottomColor,
                ax, bottomY, az, 0.5F, 0.5F, bottomColor,
                bx, bottomY, az, 0.5F, 0.5F, bottomColor,
                bx, bottomY, bz, 0.5F, 0.5F, bottomColor);
        vertices += 8;
        builder.include(ax, topY, az);
        builder.include(bx, bottomY, bz);
        int steps = size / CELL;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int neighborI = ci + direction.getStepX() * (direction.getStepX() > 0 ? steps : 1);
            int neighborJ = cj + direction.getStepZ() * (direction.getStepZ() > 0 ? steps : 1);
            int neighbor = cellIndex(neighborI, neighborJ);
            if (neighbor < 0 || cellTop[neighbor] == NO_CELL) {
                farSide(builder, NORMAL_WALL, direction, ax, bx, az, bz, topY, bottomY, sideColor, light);
                continue;
            }
            boolean samePlate = cellPlate[neighbor] == plate;
            float low = samePlate
                    ? Math.max((float) (cellTop[neighbor] + 1 - builder.pivotY), topY - 16.0F)
                    : bottomY;
            if (low < topY) {
                farSide(builder, samePlate ? direction.ordinal() : NORMAL_WALL, direction, ax, bx, az, bz, topY, low, sideColor, light);
            }
        }
    }

    private boolean isNearCellFallback(int ci, int cj) {
        int middleX = ci * CELL + CELL / 2;
        int middleZ = cj * CELL + CELL / 2;
        return (long) middleX * middleX + (long) middleZ * middleZ <= (long) nearRadius * nearRadius;
    }

    private void farSide(
            PlateBuilder builder, int normal, Direction direction,
            float ax, float bx, float az, float bz,
            float topY, float lowY, int color, int light) {
        int lowColor = shadeRgb(color, 0.7F);
        KingCrimsonShatterMesh.LayerBuilder flat = builder.flat;
        flat.level = flatLevel(color);
        switch (direction) {
            case NORTH -> flat.quad(
                    normal,
                    light,
                    bx, topY, az, 0.5F, 0.5F, color,
                    bx, lowY, az, 0.5F, 0.5F, lowColor,
                    ax, lowY, az, 0.5F, 0.5F, lowColor,
                    ax, topY, az, 0.5F, 0.5F, color);
            case SOUTH -> flat.quad(
                    normal,
                    light,
                    ax, topY, bz, 0.5F, 0.5F, color,
                    ax, lowY, bz, 0.5F, 0.5F, lowColor,
                    bx, lowY, bz, 0.5F, 0.5F, lowColor,
                    bx, topY, bz, 0.5F, 0.5F, color);
            case WEST -> flat.quad(
                    normal,
                    light,
                    ax, topY, az, 0.5F, 0.5F, color,
                    ax, lowY, az, 0.5F, 0.5F, lowColor,
                    ax, lowY, bz, 0.5F, 0.5F, lowColor,
                    ax, topY, bz, 0.5F, 0.5F, color);
            default -> flat.quad(
                    normal,
                    light,
                    bx, topY, bz, 0.5F, 0.5F, color,
                    bx, lowY, bz, 0.5F, 0.5F, lowColor,
                    bx, lowY, az, 0.5F, 0.5F, lowColor,
                    bx, topY, az, 0.5F, 0.5F, color);
        }
        vertices += 4;
    }

    private int flatColor(BlockState state, int x, int y, int z) {
        FlatColor flat = flatColors.get(state);
        if (flat == null) {
            flat = readFlatColor(state);
            flatColors.put(state, flat);
        }
        if (flat.tintIndex < 0) {
            return flat.rgb;
        }
        int tint;
        FluidState fluid = state.getFluidState();
        pos.set(x, y, z);
        if (flat.fluidTint) {
            tint = fluidShade(fluid, IClientFluidTypeExtensions.of(fluid).getTintColor(fluid, level, pos));
        } else {
            tint = blockColors.getColor(state, level, pos, flat.tintIndex);
        }
        int r = (flat.rgb >> 16 & 255) * (tint >> 16 & 255) / 255;
        int g = (flat.rgb >> 8 & 255) * (tint >> 8 & 255) / 255;
        int b = (flat.rgb & 255) * (tint & 255) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private FlatColor readFlatColor(BlockState state) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && state.getRenderShape() != RenderShape.MODEL) {
            IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
            TextureAtlasSprite sprite = atlas.apply(extensions.getStillTexture());
            return new FlatColor(averageOf(sprite), 0, true);
        }
        BakedModel model = shaper.getBlockModel(state);
        modelRandom.setSeed(42L);
        List<BakedQuad> quads = model.getQuads(state, Direction.UP, modelRandom, ModelData.EMPTY, null);
        if (quads.isEmpty()) {
            modelRandom.setSeed(42L);
            quads = model.getQuads(state, null, modelRandom, ModelData.EMPTY, null);
        }
        if (quads.isEmpty()) {
            return new FlatColor(averageOf(model.getParticleIcon(ModelData.EMPTY)), -1, false);
        }
        BakedQuad quad = quads.get(0);
        return new FlatColor(averageOf(quad.getSprite()), quad.isTinted() ? quad.getTintIndex() : -1, false);
    }

    private static int averageOf(TextureAtlasSprite sprite) {
        int width = sprite.contents().width();
        int height = sprite.contents().height();
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        long weight = 0L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = sprite.getPixelRGBA(0, x, y);
                int alpha = pixel >>> 24;
                red += (long) (pixel & 255) * alpha;
                green += (long) (pixel >> 8 & 255) * alpha;
                blue += (long) (pixel >> 16 & 255) * alpha;
                weight += alpha;
            }
        }
        if (weight == 0L) {
            return 0xFF808080;
        }
        return 0xFF000000 | (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
    }

    private record FlatColor(int rgb, int tintIndex, boolean fluidTint) {}

    private final class PlateBuilder {
        private final int id;
        private final double pivotX;
        private final double pivotY;
        private final double pivotZ;
        private final KingCrimsonShatterMesh.LayerBuilder solid = new KingCrimsonShatterMesh.LayerBuilder();
        private final KingCrimsonShatterMesh.LayerBuilder cutout = new KingCrimsonShatterMesh.LayerBuilder();
        private final KingCrimsonShatterMesh.LayerBuilder flat = new KingCrimsonShatterMesh.LayerBuilder();
        private float farthest;
        private float groundTop = Float.NEGATIVE_INFINITY;
        private float bottom = Float.POSITIVE_INFINITY;

        private PlateBuilder(int id, double pivotX, double pivotY, double pivotZ) {
            this.id = id;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
        }

        private void include(float x, float y, float z) {
            float squared = x * x + y * y + z * z;
            if (squared > farthest) {
                farthest = squared;
            }
        }

        private void span(float worldGroundTop, float worldBottom) {
            spanLocal((float) (worldGroundTop - pivotY), (float) (worldBottom - pivotY));
        }

        private void spanLocal(float localGroundTop, float localBottom) {
            groundTop = Math.max(groundTop, localGroundTop);
            bottom = Math.min(bottom, localBottom);
        }

        // Whatever is emitted next goes when this column goes, in all the layers.
        private void atColumn(int x, int z) {
            float order = plan.tearOrder(id, x - originX, z - originZ);
            solid.order = order;
            cutout.order = order;
            flat.order = order;
        }

        // Each bucket takes as many quads as the next, so that a plate wears away evenly over its tear.
        private float[] tearLimits() {
            int total = solid.quadCount() + cutout.quadCount() + flat.quadCount();
            float[] orders = new float[total];
            int at = solid.copyOrders(orders, 0);
            at = cutout.copyOrders(orders, at);
            flat.copyOrders(orders, at);
            Arrays.sort(orders);
            float[] limits = new float[KingCrimsonShatterMesh.TEAR_BUCKETS];
            for (int bucket = 0; bucket < limits.length; bucket++) {
                limits[bucket] = total == 0 ? 0.0F : orders[Math.max(0, (bucket + 1) * total / limits.length - 1)];
            }
            return limits;
        }

        private KingCrimsonShatterMesh.Plate build() {
            float[] limits = tearLimits();
            return new KingCrimsonShatterMesh.Plate(
                    id,
                    pivotX,
                    pivotY,
                    pivotZ,
                    (float) Math.sqrt(farthest) + 3.0F,
                    groundTop,
                    bottom,
                    solid.build(limits),
                    cutout.build(limits),
                    flat.build(limits));
        }
    }
}
