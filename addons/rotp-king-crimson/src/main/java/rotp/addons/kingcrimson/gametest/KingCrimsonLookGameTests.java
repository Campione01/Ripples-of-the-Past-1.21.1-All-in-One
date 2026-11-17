package rotp.addons.kingcrimson.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
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
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlateLight;
import rotp.addons.kingcrimson.shatter.KingCrimsonTerrainOutlineField;

/** The look of the erase that can be told without a client (lane KCVIS, R772.1): laws, codes and the outline's floors. */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonLookGameTests {
    private static final BlockPos FEET = new BlockPos(100, 64, 100);
    private static final String OUTLINE_PROGRAM = "/assets/rotp_kingcrimson/shaders/core/time_erase_outline.fsh";

    private KingCrimsonLookGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_look")
    public static void looseBlocksFloatUpMoreSlowlyThanTheSmallOnes(GameTestHelper helper) {
        helper.assertTrue(KingCrimsonShatterPlan.LOOSE_RISE_GROWTH < KingCrimsonShatterPlan.DEBRIS_RISE_GROWTH,
                "a loose block must speed up less than a small one as it rises");
        for (float age = 1.0F; age <= 80.0F; age += 1.0F) {
            helper.assertTrue(KingCrimsonShatterPlan.debrisRise(0.06F, age)
                            == KingCrimsonShatterPlan.debrisRise(0.06F, KingCrimsonShatterPlan.DEBRIS_RISE_GROWTH, age),
                    "the small blocks' rise must stay what the far-terrain add-on reads at age " + age);
            float loose = KingCrimsonShatterPlan.debrisRise(0.05F, KingCrimsonShatterPlan.LOOSE_RISE_GROWTH, age);
            float before = KingCrimsonShatterPlan.debrisRise(0.05F, KingCrimsonShatterPlan.LOOSE_RISE_GROWTH, age - 1.0F);
            helper.assertTrue(loose > before, "a loose block stops rising at age " + age);
        }
        float slowest = KingCrimsonShatterPlan.debrisRise(0.02F, KingCrimsonShatterPlan.LOOSE_RISE_GROWTH, 40.0F);
        float fastest = KingCrimsonShatterPlan.debrisRise(0.05F, KingCrimsonShatterPlan.LOOSE_RISE_GROWTH, 60.0F);
        helper.assertTrue(slowest >= 2.0F && fastest <= 9.0F,
                "a loose block floats up slowly but visibly: " + slowest + " blocks in 2 s at the least, " + fastest + " in 3 s at the most");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_look")
    public static void floorLinesAreGradedByDepthSoThatThePlayerReadsIt(GameTestHelper helper) {
        float standing = KingCrimsonTerrainOutlineField.floorLineLevel(1, 0.0F);
        float oneDown = KingCrimsonTerrainOutlineField.floorLineLevel(2, 0.0F);
        float lowest = KingCrimsonTerrainOutlineField.floorLineLevel(KingCrimsonTerrainOutlineField.FLOOR_LINE_LOWEST, 0.0F);
        helper.assertTrue(standing == 1.0F, "the floor stood on is the brightest, was " + standing);
        helper.assertTrue(oneDown <= 0.7F, "a floor a block or two down is plainly dimmer, was " + oneDown);
        helper.assertTrue(lowest > 0.1F && lowest < 0.3F, "the bottom of a pit 8 down is faint but there, was " + lowest);
        for (int depth = 2; depth <= KingCrimsonTerrainOutlineField.FLOOR_LINE_LOWEST; depth++) {
            float upper = KingCrimsonTerrainOutlineField.floorLineLevel(depth - 1, 0.0F);
            float lower = KingCrimsonTerrainOutlineField.floorLineLevel(depth, 0.0F);
            helper.assertTrue(lower < upper * 0.9F, "two floors " + (depth - 1) + " and " + depth + " down cannot be told apart");
        }
        helper.assertTrue(KingCrimsonTerrainOutlineField.floorLineLevel(KingCrimsonTerrainOutlineField.FLOOR_LINE_LOWEST + 1, 0.0F) == 0.0F
                        && KingCrimsonTerrainOutlineField.floorLineLevel(0, 0.0F) == 0.0F,
                "no lines under the reach or above the feet");
        helper.assertTrue(KingCrimsonTerrainOutlineField.floorLineLevel(1, 6.0F) == 1.0F
                        && KingCrimsonTerrainOutlineField.floorLineLevel(1, 24.0F) == 0.0F
                        && KingCrimsonTerrainOutlineField.floorLineLevel(1, 15.0F) > 0.2F,
                "across, full to 6 blocks and gone by 24");
        helper.assertTrue(KingCrimsonTerrainOutlineField.FLOOR_LINE_GONE_ACROSS < KingCrimsonTerrainOutlineField.RADIUS
                        && KingCrimsonTerrainOutlineField.FLOOR_LINE_UNDER_EDGE < 1.0F,
                "the lines stay inside the reach and under the edges");
        String program = text(OUTLINE_PROGRAM);
        List<String> missing = new ArrayList<>();
        for (String line : new String[] {
            constant("FLOOR_LINE_LOWEST", KingCrimsonTerrainOutlineField.FLOOR_LINE_LOWEST),
            constant("FLOOR_LINE_STEP", KingCrimsonTerrainOutlineField.FLOOR_LINE_STEP),
            constant("FLOOR_LINE_DIMS", KingCrimsonTerrainOutlineField.FLOOR_LINE_DIMS),
            constant("FLOOR_LINE_FULL_ACROSS", KingCrimsonTerrainOutlineField.FLOOR_LINE_FULL_ACROSS),
            constant("FLOOR_LINE_GONE_ACROSS", KingCrimsonTerrainOutlineField.FLOOR_LINE_GONE_ACROSS),
            constant("FLOOR_LINE_UNDER_EDGE", KingCrimsonTerrainOutlineField.FLOOR_LINE_UNDER_EDGE),
            "min(UNDER_ALPHA * FLOOR_LINE_LEVEL * grade * near, FLOOR_LINE_UNDER_EDGE * UNDER_ALPHA * level)",
        }) {
            if (!program.contains(line)) missing.add(line);
        }
        helper.assertTrue(missing.isEmpty(), "time_erase_outline.fsh does not carry the floor lines' numbers: " + missing);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "kc_look")
    public static void floorLinesReachThePitsBottom(GameTestHelper helper) {
        Ground pit = new Ground();
        // A stepped pit in front of the feet: its floors lie 2, 4, 6 and 8 blocks under the one stood on.
        pit.dig(-6, 6, 2, 14, -2, -1);
        pit.dig(-4, 4, 4, 12, -4, -3);
        pit.dig(-2, 2, 6, 10, -6, -5);
        pit.dig(-1, 1, 7, 9, -8, -7);
        KingCrimsonTerrainOutlineField field = new KingCrimsonTerrainOutlineField();
        field.update(pit, FEET, true, Long.MAX_VALUE);
        KingCrimsonTerrainOutlineField.Result outlines = field.classified(FEET);
        // Down the middle of a floor no edge runs: a line along x = 0 there is a floor line. The pit's bottom is x -1..1, z 7..9.
        Map<Integer, Integer> floors = new HashMap<>();
        float[] under = outlines.under();
        for (int i = 0; i < under.length; i += 6) {
            if (under[i] == 0.0F && under[i + 3] == 0.0F && under[i + 1] == under[i + 4] && under[i + 2] != under[i + 5]) {
                float near = Math.min(under[i + 2], under[i + 5]);
                float far = Math.max(under[i + 2], under[i + 5]);
                int y = Math.round(under[i + 1]);
                boolean inside = y == 0 ? far <= 2.0F : y == -8 && near >= 7.0F && far <= 10.0F;
                if (inside) floors.merge(y, 1, Integer::sum);
            }
        }
        helper.assertTrue(floors.getOrDefault(0, 0) > 0, "no line on the floor stood on; found " + floors);
        helper.assertTrue(floors.getOrDefault(-8, 0) > 0, "no line on the pit's bottom 8 blocks down; found " + floors);
        helper.assertTrue(KingCrimsonTerrainOutlineField.floorLineDepth() == KingCrimsonTerrainOutlineField.FLOOR_LINE_LOWEST,
                "the set under the feet and the program keep floors to the same depth");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_look")
    public static void darkWoodGetsTheEvenLightWhereThePackCanTakeIt(GameTestHelper helper) {
        float grass = 0.099F;
        float darkWood = 0.017F;
        helper.assertTrue(KingCrimsonShatterPlateLight.code(grass, 0) == KingCrimsonShatterPlateLight.level(grass, 0),
                "a block the cap never reached keeps its level");
        int code = KingCrimsonShatterPlateLight.code(darkWood, 0);
        int level = KingCrimsonShatterPlateLight.levelOfCode(code);
        helper.assertTrue(code > KingCrimsonShatterPlateLight.LAST_LEVEL && code <= KingCrimsonShatterPlateLight.LAST_CODE,
                "dark wood's code stays in one byte above the cap, was " + code);
        float wanted = KingCrimsonShatterPlateLight.lightPerAlbedo(KingCrimsonShatterPlateLight.level(grass, 0), grass);
        for (float radiance = 0.0092F; radiance <= 0.12F; radiance *= 1.07F) {
            int each = KingCrimsonShatterPlateLight.levelOfCode(KingCrimsonShatterPlateLight.code(radiance, 0));
            float light = KingCrimsonShatterPlateLight.lightPerAlbedo(each, radiance);
            helper.assertTrue(Math.abs(light - wanted) <= wanted * 0.06F,
                    String.format(Locale.ROOT, "a face of radiance %.4f gets %.4f per unit of albedo against grass's %.4f", radiance, light, wanted));
        }
        int narrow = KingCrimsonShatterPlateLight.plateId(code, 10040, false);
        int wide = KingCrimsonShatterPlateLight.plateId(code, 10040, true);
        helper.assertTrue(narrow == 10000 + KingCrimsonShatterPlateLight.LAST_LEVEL,
                "a pack that does not declare the wide levels keeps the cap, was " + narrow);
        helper.assertTrue(wide == KingCrimsonShatterPlateLight.WIDE_ID_BASE + level,
                "a pack that declares them reads the level from the id, was " + wide);
        for (int each = 0; each <= KingCrimsonShatterPlateLight.LAST_CODE; each++) {
            for (boolean declared : new boolean[] {false, true}) {
                int id = KingCrimsonShatterPlateLight.plateId(each, 10040, declared);
                helper.assertTrue(id < 10097 || id > KingCrimsonShatterPlateLight.WIDE_ID_BASE + KingCrimsonShatterPlateLight.LAST_LEVEL,
                        "code " + each + " takes the id " + id + " of a marker or of the narrow range's neighbours");
                helper.assertTrue(declared || id <= 10000 + KingCrimsonShatterPlateLight.LAST_LEVEL,
                        "code " + each + " goes past the cap on a pack that cannot read it");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_look")
    public static void aFarPlanFlysAndTearsByTheNearPlansLaw(GameTestHelper helper) {
        int near = 128;
        KingCrimsonShatterPlan own = KingCrimsonShatterPlan.create(137, -2051, 7L, 2049, 37.0F);
        KingCrimsonShatterPlan far = KingCrimsonShatterPlan.createFar(137, -2051, 7L, 2049, 37.0F, near);
        helper.assertTrue(far.plateCount() == own.plateCount() && far.lawRange() == near && own.lawRange() == 2049,
                "the same star of cuts, only the law differs");
        // The same stream gives each plate the same dice in both: only the law's range makes them differ.
        KingCrimsonShatterPlan.Pose lawPose = new KingCrimsonShatterPlan.Pose();
        KingCrimsonShatterPlan.Pose ownPose = new KingCrimsonShatterPlan.Pose();
        int checked = 0;
        int sooner = 0;
        int shorter = 0;
        for (int plate = 0; plate < far.plateCount(); plate++) {
            float distance = far.plateDistance(plate);
            if (distance < near || distance > 1000.0F) continue;
            checked++;
            helper.assertTrue(far.tearStartTick(plate) <= own.tearStartTick(plate) + 1.0E-3F,
                    "a far plate at " + distance + " blocks tears later by the near law than by its own");
            if (far.tearStartTick(plate) < own.tearStartTick(plate) - 0.5F) sooner++;
            far.pose(plate, 84.0F, lawPose);
            own.pose(plate, 84.0F, ownPose);
            double lawFlown = Math.sqrt(lawPose.dx * lawPose.dx + lawPose.dz * lawPose.dz);
            double ownFlown = Math.sqrt(ownPose.dx * ownPose.dx + ownPose.dz * ownPose.dz);
            helper.assertTrue(lawFlown <= ownFlown + 1.0E-3D, "a far plate at " + distance + " blocks flies farther by the near law");
            if (lawFlown < ownFlown - 1.0D) shorter++;
        }
        helper.assertTrue(checked > 10, "only " + checked + " plates between the near range and 1000 blocks");
        helper.assertTrue(sooner * 2 > checked && shorter * 2 > checked,
                "of " + checked + " far plates only " + sooner + " tear sooner and " + shorter + " fly less by the near law");
        KingCrimsonShatterPlan same = KingCrimsonShatterPlan.createFar(137, -2051, 7L, 256, 37.0F, 256);
        KingCrimsonShatterPlan plain = KingCrimsonShatterPlan.create(137, -2051, 7L, 256, 37.0F);
        for (int plate = 0; plate < plain.plateCount(); plate++) {
            helper.assertTrue(same.tearStartTick(plate) == plain.tearStartTick(plate) && same.retireTick(plate) == plain.retireTick(plate),
                    "a plan whose law is its own range is the plain plan");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_look")
    public static void theNearPlatesGoInThePlansOrder(GameTestHelper helper) {
        // Section 6 of the owner's end (2026-11-11): the ground under the user goes last, the edge of the near range first.
        int range = 128;
        for (long seed : new long[] {1L, 0x1234_5678_9ABCL, -77L}) {
            KingCrimsonShatterPlan plan = KingCrimsonShatterPlan.create(137, -2051, seed, range, 37.0F);
            float inner = median(plan, 0.0F, 32.0F);
            float outer = median(plan, 96.0F, 128.0F);
            helper.assertTrue(inner > outer + 8.0F,
                    "seed " + seed + ": the plates near the feet go at " + inner + ", those at the edge at " + outer);
            float last = 0.0F;
            for (int plate = 0; plate < plan.plateCount(); plate++) {
                last = Math.max(last, plan.retireTick(plate));
            }
            helper.assertTrue(last <= KingCrimsonShatterPlan.END_TICK + 1.0E-3F, "a plate outlives the erase's end: " + last);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_look")
    public static void theUniverseFadesOffTheReturnWhereThePackDoesNotDeclareTheVoid(GameTestHelper helper) {
        // Owner, 2026-11-12: "修：結束前加約半秒淡出". A pack that declares the void keeps its end, as does no shader pack.
        helper.assertTrue(KingCrimsonShatterPackRoute.UNDECLARED.fadesTheReturn(), "a pack that does not declare the void must fade");
        helper.assertTrue(!KingCrimsonShatterPackRoute.DECLARED.fadesTheReturn(), "a pack that declares the void must keep its end");
        helper.assertTrue(!KingCrimsonShatterPackRoute.NO_SHADERS.fadesTheReturn(), "no shader pack has an exposure to wait for");
        float seconds = KingCrimsonShatterPlan.RETURN_FADE_SECONDS;
        helper.assertTrue(seconds >= 0.4F && seconds <= 0.6F, "about half a second, not " + seconds);
        helper.assertTrue(KingCrimsonShatterPlan.returnOpacity(0.0F) == 1.0F
                        && KingCrimsonShatterPlan.returnOpacity(-1.0F) == 1.0F
                        && KingCrimsonShatterPlan.returnOpacity(Float.NaN) == 1.0F,
                "the world comes back under the whole universe");
        helper.assertTrue(KingCrimsonShatterPlan.returnOpacity(0.1F * seconds) > 0.95F,
                "the universe must stay nearly whole while the pack's exposure is at its highest: "
                        + KingCrimsonShatterPlan.returnOpacity(0.1F * seconds));
        helper.assertTrue(KingCrimsonShatterPlan.returnOpacity(0.9F * seconds) > 0.0F,
                "the fade must last its half second");
        helper.assertTrue(KingCrimsonShatterPlan.returnOpacity(seconds) == 0.0F
                        && KingCrimsonShatterPlan.returnOpacity(10.0F) == 0.0F,
                "the universe must be gone when the fade is over");
        float last = 1.0F;
        for (int step = 1; step <= 50; step++) {
            float now = KingCrimsonShatterPlan.returnOpacity(seconds * step / 50.0F);
            helper.assertTrue(now <= last && last - now < 0.06F, "the fade must go smoothly down, step " + step + ": " + last + " -> " + now);
            last = now;
        }
        helper.succeed();
    }

    private static float median(KingCrimsonShatterPlan plan, float from, float to) {
        List<Float> ends = new ArrayList<>();
        for (int plate = 0; plate < plan.plateCount(); plate++) {
            float distance = plan.plateDistance(plate);
            if (distance >= from && distance < to) ends.add(plan.retireTick(plate));
        }
        ends.sort(Float::compare);
        return ends.isEmpty() ? Float.NaN : ends.get(ends.size() / 2);
    }

    private static String constant(String name, float value) {
        String number = value == Math.rint(value) ? String.format(Locale.ROOT, "%.1f", value) : Float.toString(value);
        return "const float " + name + " = " + number + ";";
    }

    private static String text(String path) {
        try (var in = KingCrimsonAddon.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(path, failure);
        }
    }

    /** Stone under the feet as far as any scan goes, with holes dug where a test wants them. */
    private static final class Ground implements BlockGetter {
        private final Map<BlockPos, BlockState> states = new HashMap<>();

        private void dig(int x0, int x1, int z0, int z1, int y0, int y1) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        states.put(FEET.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState dug = states.get(pos.immutable());
            return dug != null ? dug : pos.getY() < FEET.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
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
}
