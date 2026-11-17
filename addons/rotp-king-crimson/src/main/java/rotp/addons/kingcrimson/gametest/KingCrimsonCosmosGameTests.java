package rotp.addons.kingcrimson.gametest;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPackRoute;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterShake;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlateLight;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterSurface;
import rotp.addons.kingcrimson.shatter.KingCrimsonTerrainOutlineField;
import rotp.addons.kingcrimson.shatter.KingCrimsonTerrainOutlineScan;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonCosmosGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos FEET = new BlockPos(100, 64, 100);

    private KingCrimsonCosmosGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_cosmos")
    public static void onlyAPackThatDeclaresTheVoidTakesItsRoute(GameTestHelper helper) {
        helper.assertTrue(KingCrimsonShatterPackRoute.choose(false, 10099) == KingCrimsonShatterPackRoute.NO_SHADERS,
                "With no pack in use nothing a stale id map says counts");
        helper.assertTrue(KingCrimsonShatterPackRoute.choose(true, 10099) == KingCrimsonShatterPackRoute.DECLARED,
                "A pack that declares the void at its id draws it");
        helper.assertTrue(KingCrimsonShatterPackRoute.choose(true, -1) == KingCrimsonShatterPackRoute.UNDECLARED,
                "An id map without the name is an ordinary pack");
        helper.assertTrue(KingCrimsonShatterPackRoute.choose(true, 10098) == KingCrimsonShatterPackRoute.UNDECLARED,
                "The name on any other id is not the declaration");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_cosmos")
    public static void cameraShakeRidesTheBreakingOfTheGround(GameTestHelper helper) {
        for (float tick : new float[] {-5.0F, 0.0F, 1.0F, KingCrimsonShatterShake.START_TICK, KingCrimsonShatterShake.GONE_TICK,
                KingCrimsonShatterPlan.WIND_LAST_TICK, KingCrimsonShatterPlan.END_TICK, 200.0F, Float.NaN}) {
            helper.assertTrue(KingCrimsonShatterShake.envelope(tick) == 0.0F, "No shake outside its window, was at tick " + tick);
        }
        helper.assertTrue(KingCrimsonShatterShake.START_TICK > 0.0F
                        && KingCrimsonShatterShake.GONE_TICK < KingCrimsonShatterPlan.WIND_LAST_TICK,
                "It starts with the first crests and is gone before the last crest and the ground's end");
        float peakAt = 0.0F;
        float peak = 0.0F;
        for (float tick = 0.0F; tick < KingCrimsonShatterPlan.END_TICK; tick += 0.05F) {
            float envelope = KingCrimsonShatterShake.envelope(tick);
            if (envelope > peak) {
                peak = envelope;
                peakAt = tick;
            }
        }
        helper.assertTrue(peak > 0.99F && peakAt >= KingCrimsonShatterShake.PEAK_FROM_TICK - 0.1F
                        && peakAt <= KingCrimsonShatterShake.PEAK_TO_TICK,
                "Strongest as the cracks open into trenches, was " + peak + " at tick " + peakAt);
        helper.assertTrue(KingCrimsonShatterShake.envelope(KingCrimsonShatterShake.START_TICK + 0.5F) < 0.2F
                        && KingCrimsonShatterShake.envelope(22.0F) < 0.5F,
                "It comes in gently and lets go as the plates lift");
        float[] angles = new float[3];
        float[] previous = null;
        float most = 0.0F;
        for (float tick = 0.0F; tick < KingCrimsonShatterShake.GONE_TICK + 1.0F; tick += 0.05F) {
            KingCrimsonShatterShake.angles(tick, 1.0F, angles);
            most = Math.max(most, Math.max(Math.abs(angles[0]), Math.max(Math.abs(angles[1]), Math.abs(angles[2]))));
            if (previous != null) {
                for (int i = 0; i < 3; i++) {
                    helper.assertTrue(Math.abs(angles[i] - previous[i]) < 0.1F,
                            "Smooth, not a jitter: angle " + i + " jumped at tick " + tick);
                }
            }
            previous = angles.clone();
        }
        helper.assertTrue(most > 0.15F && most <= 1.0F, "Slight, but there: most " + most + " degrees, a hurt tilt is 14");
        KingCrimsonShatterShake.angles(9.0F, 0.0F, angles);
        helper.assertTrue(angles[0] == 0.0F && angles[1] == 0.0F && angles[2] == 0.0F,
                "Screen Effects at 0 takes it away");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_cosmos")
    public static void paleAndDarkBlocksTakeOneLightAndOnlyEmittersGlow(GameTestHelper helper) {
        // As the game's own textures measure, weighted as the pack's law weighs them, with the plains tint on the grass.
        float grass = 0.0989F;
        float birch = 0.84F;
        float snow = 0.979F;
        int grassLevel = KingCrimsonShatterPlateLight.level(grass, 0);
        int birchLevel = KingCrimsonShatterPlateLight.level(birch, 0);
        int snowLevel = KingCrimsonShatterPlateLight.level(snow, 0);
        helper.assertTrue(grassLevel == 40, "Grass keeps the level of the accepted look, was " + grassLevel);
        helper.assertTrue(birchLevel < grassLevel / 3 && snowLevel <= birchLevel,
                "A pale block takes a lower level, was birch " + birchLevel + " snow " + snowLevel);
        float grassLight = KingCrimsonShatterPlateLight.lightPerAlbedo(grassLevel, grass);
        float birchLight = KingCrimsonShatterPlateLight.lightPerAlbedo(birchLevel, birch);
        float snowLight = KingCrimsonShatterPlateLight.lightPerAlbedo(snowLevel, snow);
        helper.assertTrue(Math.abs(birchLight / grassLight - 1.0F) < 0.15F && Math.abs(snowLight / grassLight - 1.0F) < 0.15F,
                "One even light per unit of albedo, was grass " + grassLight + " birch " + birchLight + " snow " + snowLight);
        helper.assertTrue(KingCrimsonShatterPlateLight.level(0.5F, 15) == KingCrimsonShatterPlateLight.LAST_LEVEL,
                "A block of full light glows");
        helper.assertTrue(KingCrimsonShatterPlateLight.level(0.5F, 15) > KingCrimsonShatterPlateLight.level(0.5F, 0) * 5,
                "A block that emits stands out from one of the same colour that does not");
        helper.assertTrue(KingCrimsonShatterPlateLight.level(0.0F, 0) == KingCrimsonShatterPlateLight.LAST_LEVEL
                        && KingCrimsonShatterPlateLight.level(1.0F, 0) >= KingCrimsonShatterPlateLight.FIRST_LEVEL,
                "The level stays in the plate range, under the pack's two void markers");
        helper.assertTrue(KingCrimsonShatterPlateLight.LAST_LEVEL + 10000 < 10097, "A plate never takes a marker's id");
        helper.assertTrue(Math.abs(KingCrimsonShatterPlateLight.radiance(0xFFFFFFFF) - 1.0F) < 1.0E-5F
                        && Math.abs(KingCrimsonShatterPlateLight.radiance(0xFF808080) - 0.2195F) < 0.002F,
                "Radiance is the pack's: the mean of linear channels");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_cosmos")
    public static void crestsFollowTheSurfaceFromTheFeet(GameTestHelper helper) {
        int half = 20;
        // On a canopy at 20 over ground at 5: the surface stays on the leaves, then drops where the canopy ends.
        float[] canopy = KingCrimsonShatterSurface.walk(half, 20.0F, 8, (dx, dz, into) -> {
            into[0] = 5.0F;
            if (Math.abs(dx) > 10 || Math.abs(dz) > 10) return 1;
            into[1] = 20.0F;
            return 2;
        });
        helper.assertTrue(at(canopy, half, 5, 0) == 20.0F && at(canopy, half, 10, -10) == 20.0F,
                "Standing on leaves keeps to the leaves, was " + at(canopy, half, 5, 0));
        helper.assertTrue(at(canopy, half, 12, 0) == 5.0F, "Past the canopy's edge it drops to the ground");

        // Under trees on ground at 6: a crown at 15 overhead, a trunk at x 3 that leaves only the crown to stand on.
        float[] under = KingCrimsonShatterSurface.walk(half, 6.0F, 8, (dx, dz, into) -> {
            if (dx == 3 && dz == 0) {
                into[0] = 15.0F;
                return 1;
            }
            into[0] = 6.0F;
            if (dx < 2 || dx > 6) return 1;
            into[1] = 15.0F;
            return 2;
        });
        helper.assertTrue(at(under, half, 2, 0) == 6.0F && at(under, half, 5, 0) == 6.0F && at(under, half, 8, 0) == 6.0F,
                "Walking under trees does not go up into the canopy, was " + at(under, half, 5, 0));
        helper.assertTrue(at(under, half, 3, 0) == 6.0F, "A trunk is passed, not climbed, was " + at(under, half, 3, 0));

        // A wall too thick to be a trunk is climbed; a slope is followed.
        float[] wall = KingCrimsonShatterSurface.walk(half, 6.0F, 8, (dx, dz, into) -> {
            into[0] = dx < 5 ? 6.0F : 14.0F;
            return 1;
        });
        helper.assertTrue(at(wall, half, 4, 0) == 6.0F && at(wall, half, 12, 0) == 14.0F,
                "A hillside is climbed, was " + at(wall, half, 12, 0));
        float[] slope = KingCrimsonShatterSurface.walk(half, 0.0F, 8, (dx, dz, into) -> {
            into[0] = Math.max(Math.abs(dx), Math.abs(dz));
            into[1] = 40.0F;
            return 2;
        });
        helper.assertTrue(at(slope, half, 9, 0) == 9.0F, "A slope is followed step by step");

        float[] unknown = KingCrimsonShatterSurface.walk(half, 6.0F, 8, (dx, dz, into) -> dx > 3 ? 0 : setting(into, 6.0F));
        helper.assertTrue(Float.isNaN(at(unknown, half, 5, 0)), "A column nothing is known of stays unknown");

        helper.assertTrue(KingCrimsonShatterSurface.isModdedGround(false, true, false),
                "A modded dimension's solid whole block is ground");
        helper.assertTrue(!KingCrimsonShatterSurface.isModdedGround(true, true, false),
                "Vanilla's blocks stay with their tags: a stone-brick roof is no ground");
        helper.assertTrue(!KingCrimsonShatterSurface.isModdedGround(false, true, true)
                        && !KingCrimsonShatterSurface.isModdedGround(false, false, false),
                "A modded leaf, log or plant is no ground");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_cosmos")
    public static void terrainOutlineSelectsWhatBlocksAndKeepsTheFloorApart(GameTestHelper helper) {
        Blocks3 flat = new Blocks3();
        flat.floor(Blocks.STONE);
        KingCrimsonTerrainOutlineField.Result floorOnly = outlines(flat, FEET);
        helper.assertTrue(floorOnly.upperCount() == 0, "A flat floor blocks nothing, was " + floorOnly.upperCount());
        helper.assertTrue(floorOnly.underCount() > 0, "A flat floor under the feet has its thin lines");
        for (int i = 0; i < floorOnly.under().length; i += 3) {
            helper.assertTrue(floorOnly.under()[i + 1] == 0.0F, "The floor's lines lie on its top");
        }

        Blocks3 grass = new Blocks3();
        grass.floor(Blocks.STONE);
        for (int dz = -6; dz <= 6; dz++) {
            for (int dx = -6; dx <= 6; dx++) {
                grass.put(dx, 0, dz, Blocks.SHORT_GRASS);
                grass.put(dx, 1, dz, Blocks.DANDELION);
            }
        }
        helper.assertTrue(outlines(grass, FEET).upperCount() == 0,
                "Grass and flowers have no collision shape and do not block");

        Blocks3 wall = new Blocks3();
        wall.floor(Blocks.STONE);
        for (int dx = -2; dx <= 2; dx++) {
            for (int y = 0; y <= 2; y++) {
                wall.put(dx, y, 3, Blocks.STONE);
            }
        }
        KingCrimsonTerrainOutlineField.Result walled = outlines(wall, FEET);
        helper.assertTrue(walled.upperCount() > 0, "A wall blocks and is outlined");
        helper.assertTrue(walled.upperCount() <= 40,
                "Its edges are merged into a silhouette, not a grid of every cube, were " + walled.upperCount());
        boolean topEdgeWhole = false;
        for (int i = 0; i < walled.upper().length; i += 6) {
            float[] s = walled.upper();
            helper.assertTrue(s[i + 1] >= 0.0F && s[i + 4] >= 0.0F, "Nothing of the upper set lies under the feet");
            topEdgeWhole |= s[i] == -2.0F && s[i + 3] == 3.0F && s[i + 1] == 3.0F && s[i + 4] == 3.0F && s[i + 2] == 3.0F;
        }
        helper.assertTrue(topEdgeWhole, "The wall's long top edge is one line");

        Blocks3 hole = new Blocks3();
        hole.floor(Blocks.STONE);
        for (int dz = 2; dz <= 3; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                hole.put(dx, -1, dz, Blocks.AIR);
                hole.put(dx, -2, dz, Blocks.AIR);
            }
        }
        KingCrimsonTerrainOutlineField.Result holed = outlines(hole, FEET);
        helper.assertTrue(holed.upperCount() == 0 && holed.underCount() > floorOnly.underCount(),
                "The edge of a pit is in the set under the feet");

        Blocks3 fence = new Blocks3();
        fence.floor(Blocks.STONE);
        fence.put(2, 0, 0, Blocks.OAK_FENCE);
        fence.put(-2, 0, 0, Blocks.WHITE_CARPET);
        KingCrimsonTerrainOutlineField.Result fenced = outlines(fence, FEET);
        helper.assertTrue(fenced.upperCount() == 8, "A post is its box without its foot on the ground and a carpet is nothing, was " + fenced.upperCount());

        Blocks3 asked = new Blocks3();
        asked.floor(Blocks.STONE);
        KingCrimsonTerrainOutlineField idle = new KingCrimsonTerrainOutlineField();
        idle.update(asked, FEET, false, Long.MAX_VALUE);
        helper.assertTrue(idle.classified(FEET).upperCount() == 0 && idle.classified(FEET).underCount() == 0,
                "Nothing when not erasing");
        helper.assertTrue(asked.reads == 0, "Not one block is read when not erasing, were " + asked.reads);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_cosmos")
    public static void terrainOutlineReadsEachSectionOnceAndOnlyWhatComesIntoReach(GameTestHelper helper) {
        Blocks3 walls = new Blocks3();
        walls.floor(Blocks.STONE);
        for (int dz = -40; dz <= 40; dz += 7) {
            for (int dx = -40; dx <= 40; dx += 7) {
                walls.put(dx, 0, dz, Blocks.STONE);
            }
        }
        KingCrimsonTerrainOutlineField field = new KingCrimsonTerrainOutlineField();
        field.update(walls, FEET, true, Long.MAX_VALUE);
        int first = field.readCount();
        helper.assertTrue(first > 0 && field.complete(), "All in reach is read with the budget to do it");
        int blockReads = walls.reads;
        field.update(walls, FEET, true, Long.MAX_VALUE);
        helper.assertTrue(walls.reads == blockReads, "A frame on the same spot reads nothing, were " + (walls.reads - blockReads));
        for (int step = 1; step <= 16; step++) {
            field.update(walls, FEET.offset(step, 0, 0), true, Long.MAX_VALUE);
        }
        int walked = field.readCount() - first;
        helper.assertTrue(walked > 0 && walked <= first / 3,
                "Walking one section reads only the row that came into reach, read " + walked + " of " + first);
        int atTheEnd = field.readCount();
        for (int step = 15; step >= 0; step--) {
            field.update(walls, FEET.offset(step, 0, 0), true, Long.MAX_VALUE);
        }
        helper.assertTrue(field.readCount() == atTheEnd, "Turning back reads nothing: what lay in reach stays a while");

        KingCrimsonTerrainOutlineField slow = new KingCrimsonTerrainOutlineField();
        slow.update(walls, FEET, true, 0L);
        helper.assertTrue(slow.readCount() == 1 && !slow.complete(),
                "With no budget left a frame still reads one section and no more, read " + slow.readCount());

        Blocks3 lone = new Blocks3();
        lone.put(28, 0, 0, Blocks.STONE);
        lone.put(70, 0, 0, Blocks.STONE);
        KingCrimsonTerrainOutlineField.Result far = outlines(lone, FEET);
        boolean nearOne = false;
        boolean farOne = false;
        for (int i = 0; i < far.upper().length; i += 6) {
            nearOne |= far.upper()[i] >= 28.0F && far.upper()[i] <= 29.0F;
            farOne |= far.upper()[i] >= 70.0F;
        }
        helper.assertTrue(nearOne, "A block 28 blocks off is outlined: the reach covers what the user can get to");
        helper.assertTrue(!farOne, "A block 70 blocks off is not read");
        helper.assertTrue(KingCrimsonTerrainOutlineField.RADIUS >= 24, "The owner asked for much more than 8 blocks");

        Blocks3 bar = new Blocks3();
        BlockPos beforeLine = new BlockPos(111, 64, 100);
        bar.states.put(beforeLine, Blocks.STONE.defaultBlockState());
        bar.states.put(beforeLine.east(), Blocks.STONE.defaultBlockState());
        KingCrimsonTerrainOutlineField.Result barred = outlines(bar, FEET);
        helper.assertTrue(barred.upperCount() == 16 && barred.underCount() == 0,
                "A bar of two blocks over a section's limit: 4 long edges cut in two and 4 at each end, each edge once, were "
                        + barred.upperCount() + " and " + barred.underCount());
        Blocks3 single = new Blocks3();
        single.states.put(beforeLine, Blocks.STONE.defaultBlockState());
        KingCrimsonTerrainOutlineField.Result cube = outlines(single, FEET);
        helper.assertTrue(cube.upperCount() == 12,
                "One block whose side lies on a section's limit has its 12 edges, each once, were " + cube.upperCount());
        helper.assertTrue(faces(single) == 6, "One block has 6 faces, each once, were " + faces(single));
        helper.assertTrue(faces(bar) == 10, "Over a section's limit the bar's long sides are cut in two, its 2 flush sides are gone, were " + faces(bar));
        Blocks3 plate = new Blocks3();
        for (int dz = 0; dz < 8; dz++) {
            for (int dx = 0; dx < 8; dx++) {
                plate.states.put(new BlockPos(97 + dx, 64, 97 + dz), Blocks.STONE.defaultBlockState());
            }
        }
        helper.assertTrue(faces(plate) == 6, "A slab of whole blocks in one section is one box of faces, merged, were " + faces(plate));

        ServerLevel level = helper.getLevel();
        BlockPos real = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.OAK_LEAVES);
        int sx = SectionPos.blockToSectionCoord(real.getX());
        int sy = SectionPos.blockToSectionCoord(real.getY());
        int sz = SectionPos.blockToSectionCoord(real.getZ());
        KingCrimsonTerrainOutlineScan.Section byChunks = KingCrimsonTerrainOutlineScan.scanSection(level, sx, sy, sz, real);
        KingCrimsonTerrainOutlineScan.Section byBlocks = KingCrimsonTerrainOutlineScan.scanSection(new Delegate(level), sx, sy, sz, real);
        helper.assertTrue(byChunks != null && byChunks.edges().length > 0 && byChunks.faces().length > 0
                        && Arrays.equals(byChunks.edges(), byBlocks.edges()) && Arrays.equals(byChunks.faces(), byBlocks.faces()),
                "Reading a real chunk's sections directly gives what reading its blocks one by one gives");
        helper.assertTrue(KingCrimsonTerrainOutlineScan.scanSection(level, sx + 100_000, sy, sz, real) == null,
                "A chunk not loaded is asked for again later, not taken for air");

        LOGGER.info("KC-OUTLINE-COST {}", cost("forest", forest()));
        LOGGER.info("KC-OUTLINE-COST {}", cost("caves", caves()));
        helper.succeed();
    }

    // Terrain made up for the numbers, in one array as a chunk's palette is: real states, real collision shapes, no game world to wait for.
    private static String cost(String name, Dense world) {
        BlockPos feet = new BlockPos(0, world.feetY, 0);
        KingCrimsonTerrainOutlineField field = new KingCrimsonTerrainOutlineField();
        long started = System.nanoTime();
        field.update(world, feet, true, Long.MAX_VALUE);
        long allAtOnce = System.nanoTime() - started;
        int firstReads = field.readCount();
        FloatBuffer buffer = FloatBuffer.allocate(KingCrimsonTerrainOutlineField.MOST_SEGMENTS * KingCrimsonTerrainOutlineScan.STRIDE);
        FloatBuffer faces = FloatBuffer.allocate(KingCrimsonTerrainOutlineField.MOST_SEGMENTS * KingCrimsonTerrainOutlineScan.STRIDE);
        started = System.nanoTime();
        int segments = field.write(buffer, faces);
        long written = System.nanoTime() - started;
        float[] all = buffer.array();
        int within24 = 0;
        int within16 = 0;
        for (int i = 0; i < segments * KingCrimsonTerrainOutlineScan.STRIDE; i += KingCrimsonTerrainOutlineScan.STRIDE) {
            double d = Math.max(Math.hypot(all[i], all[i + 2]), Math.hypot(all[i + 3], all[i + 5]));
            if (d <= 24.0D) within24++;
            if (d <= 16.0D) within16++;
        }
        KingCrimsonTerrainOutlineField walker = new KingCrimsonTerrainOutlineField();
        int mostInOneCrossing = 0;
        int before = 0;
        // 112 blocks at 11.2 blocks a second, at 60 frames a second, with the client's budget.
        for (int frame = 0; frame < 600; frame++) {
            int x = frame * 112 / 600;
            walker.update(world, feet.offset(x, 0, 0), true, 2_000_000L);
            mostInOneCrossing = Math.max(mostInOneCrossing, walker.readCount() - before);
            before = walker.readCount();
        }
        return name + ": sections read " + firstReads + ", edges " + segments + " (within 24 blocks " + within24 + ", within 16 " + within16
                + "), faces " + field.facesWritten() + ", buffers " + (segments + field.facesWritten()) * KingCrimsonTerrainOutlineScan.STRIDE * Float.BYTES / 1024 + " KB, all at once "
                + allAtOnce / 1_000_000L + " ms, writing the buffer " + written / 1000L + " us; walking 112 blocks: " + walker.summary()
                + ", most sections in one frame " + mostInOneCrossing;
    }

    private static Dense forest() {
        Dense world = new Dense(64);
        RandomSource random = RandomSource.create(4021L);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        for (int z = Dense.MIN; z < Dense.MAX; z++) {
            for (int x = Dense.MIN; x < Dense.MAX; x++) {
                int ground = 62 + (int) Math.round(2.5D * Math.sin(x * 0.11D) + 2.0D * Math.cos(z * 0.07D + x * 0.03D));
                for (int y = Dense.BOTTOM; y <= ground; y++) {
                    world.set(x, y, z, stone);
                }
            }
        }
        // A tree about every five blocks, the crowns grown into one another: as thick as a forest gets.
        for (int z = Dense.MIN + 4; z < Dense.MAX - 4; z += 5) {
            for (int x = Dense.MIN + 4; x < Dense.MAX - 4; x += 5) {
                int tx = x + random.nextInt(3) - 1;
                int tz = z + random.nextInt(3) - 1;
                int ground = world.top(tx, tz);
                int height = 5 + random.nextInt(4);
                for (int y = 1; y <= height; y++) {
                    world.set(tx, ground + y, tz, log);
                }
                int crown = 2 + random.nextInt(2);
                for (int dz = -crown; dz <= crown; dz++) {
                    for (int dy = -crown; dy <= crown; dy++) {
                        for (int dx = -crown; dx <= crown; dx++) {
                            if (dx * dx + dy * dy + dz * dz > crown * crown + 1 || random.nextInt(7) == 0) continue;
                            world.setIfAir(tx + dx, ground + height + dy, tz + dz, leaves);
                        }
                    }
                }
            }
        }
        return world;
    }

    private static Dense caves() {
        Dense world = new Dense(60);
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int z = Dense.MIN; z < Dense.MAX; z++) {
            for (int y = Dense.BOTTOM; y < Dense.TOP; y++) {
                for (int x = Dense.MIN; x < Dense.MAX; x++) {
                    // Winding tunnels all through the rock: about four parts in ten of it hollow.
                    double wave = Math.sin(x * 0.19D + Math.sin(z * 0.13D) * 2.0D)
                            + Math.sin(y * 0.31D + Math.cos(x * 0.11D) * 2.0D)
                            + Math.sin(z * 0.23D + Math.sin(y * 0.17D) * 2.0D);
                    if (Math.abs(wave) > 0.55D) world.set(x, y, z, stone);
                }
            }
        }
        return world;
    }

    private static int faces(BlockGetter level) {
        KingCrimsonTerrainOutlineField field = new KingCrimsonTerrainOutlineField();
        field.update(level, FEET, true, Long.MAX_VALUE);
        field.write(FloatBuffer.allocate(field.edgeFloatsToWrite()), FloatBuffer.allocate(field.faceFloatsToWrite()));
        return field.facesWritten();
    }

    private static KingCrimsonTerrainOutlineField.Result outlines(BlockGetter level, BlockPos feet) {
        KingCrimsonTerrainOutlineField field = new KingCrimsonTerrainOutlineField();
        field.update(level, feet, true, Long.MAX_VALUE);
        return field.classified(feet);
    }

    private static int setting(float[] into, float value) {
        into[0] = value;
        return 1;
    }

    private static float at(float[] surface, int half, int dx, int dz) {
        return surface[KingCrimsonShatterSurface.index(half, dx, dz)];
    }

    /** Real block states and their real shapes, on a world of their own: a test structure's neighbours must not count. */
    private static final class Blocks3 implements BlockGetter {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private BlockState floor = Blocks.AIR.defaultBlockState();
        private int reads;

        // As far as any scan goes: its limit never reads as an edge.
        private void floor(Block block) {
            floor = block.defaultBlockState();
        }

        private void put(int dx, int y, int dz, Block block) {
            states.put(FEET.offset(dx, y, dz), block.defaultBlockState());
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            reads++;
            BlockState put = states.get(pos.immutable());
            return put != null ? put : pos.getY() < FEET.getY() ? floor : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    private static final class Dense implements BlockGetter {
        private static final int MIN = -64;
        private static final int MAX = 184;
        private static final int BOTTOM = 32;
        private static final int TOP = 96;
        private static final BlockState AIR = Blocks.AIR.defaultBlockState();
        private final BlockState[] states = new BlockState[(MAX - MIN) * (MAX - MIN) * (TOP - BOTTOM)];
        private final int feetY;

        private Dense(int feetY) {
            this.feetY = feetY;
        }

        private static boolean inside(int x, int y, int z) {
            return x >= MIN && x < MAX && z >= MIN && z < MAX && y >= BOTTOM && y < TOP;
        }

        private static int index(int x, int y, int z) {
            return (x - MIN) + (z - MIN) * (MAX - MIN) + (y - BOTTOM) * (MAX - MIN) * (MAX - MIN);
        }

        private void set(int x, int y, int z, BlockState state) {
            if (inside(x, y, z)) states[index(x, y, z)] = state;
        }

        private void setIfAir(int x, int y, int z, BlockState state) {
            if (inside(x, y, z) && states[index(x, y, z)] == null) states[index(x, y, z)] = state;
        }

        private int top(int x, int z) {
            for (int y = TOP - 1; y >= BOTTOM; y--) {
                if (states[index(x, y, z)] != null) return y;
            }
            return BOTTOM;
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (!inside(pos.getX(), pos.getY(), pos.getZ())) return AIR;
            BlockState state = states[index(pos.getX(), pos.getY(), pos.getZ())];
            return state != null ? state : AIR;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    /** A real level seen as nothing but a getter of blocks. */
    private record Delegate(ServerLevel level) implements BlockGetter {
        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return level.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return level.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return level.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }
}
