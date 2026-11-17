package rotp.addons.kingcrimson.gametest;

import java.util.Arrays;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterMotion;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterProjection;

/** The plan behind Time Erase's breaking ground: pure math, so nothing here needs a world or a client. */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonShatterPlanGameTests {
    private static final int ORIGIN_X = 137;
    private static final int ORIGIN_Z = -2051;
    private static final int RANGE = KingCrimsonShatterPlan.DEFAULT_RANGE;
    private static final long[] SEEDS = {1L, 0x1234_5678_9ABCL, -77L};
    private static final float YAW = 37.0F;

    private KingCrimsonShatterPlanGameTests() {}

    private static KingCrimsonShatterPlan plan(long seed) {
        return KingCrimsonShatterPlan.create(ORIGIN_X, ORIGIN_Z, seed, RANGE, YAW);
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void irisFarPlaneComesFromTheProjectionNotRenderDistance(GameTestHelper helper) {
        for (float expected : new float[]{512.0F, 1024.0F, 8192.0F}) {
            Matrix4f projection = new Matrix4f().perspective(1.1F, 16.0F / 9.0F, 0.05F, expected);
            projection.m20(0.001F).m21(-0.001F);
            float actual = KingCrimsonShatterProjection.farPlane(projection);
            helper.assertTrue(Math.abs(actual - expected) < expected * 0.007F,
                    "Projection far plane must distinguish the Iris/Voxy projection from vanilla render distance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void voidDepthReconstructsTheRequestedNearDistance(GameTestHelper helper) {
        for (float far : new float[]{512.0F, 8192.0F}) {
            for (float near : new float[]{0.05F, 0.2F}) {
                Matrix4f projection = new Matrix4f().perspective(1.1F, 16.0F / 9.0F, near, far);
                projection.m20(0.001F).m21(-0.002F);
                Matrix4f inverse = new Matrix4f(projection).invert();
                float previous = 0.0F;
                for (float distance : new float[]{8.0F, 16.0F, 32.0F}) {
                    float depth = KingCrimsonShatterProjection.depthAtDistance(projection, distance);
                    Vector3f restored = inverse.transformProject(new Vector3f(0.0F, 0.0F, depth * 2.0F - 1.0F));
                    helper.assertTrue(Math.abs(restored.z + distance) < 0.002F * distance,
                            "Void depth must reconstruct the measured distance, not the shell or a fixed clip value");
                    helper.assertTrue(depth > previous && depth < KingCrimsonShatterProjection.VOID_DEPTH,
                            "Void samples must remain ordered and distinct from the shell marker");
                    previous = depth;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void farPlatesKeepTheNativeProjectionWhenItFits(GameTestHelper helper) {
        Matrix4f projection = new Matrix4f().perspective(1.1F, 16.0F / 9.0F, 0.05F, 8192.0F)
                .m20(0.001F).m21(-0.002F);
        Matrix4f original = new Matrix4f(projection);
        Matrix4f selected = KingCrimsonShatterProjection.forFarPlates(projection, 128.0F, 7500.0F);
        helper.assertTrue(selected != null && selected.equals(original) && projection.equals(original),
                "A native projection that covers the batch must be reused unchanged");
        int previous = -1;
        for (float distance : new float[]{64.0F, 127.0F, 128.0F, 129.0F, 250.0F, 300.0F, 512.0F, 1024.0F,
                2048.0F, 4096.0F, 7500.0F}) {
            Vector4f view = new Vector4f(distance * 0.3F, distance * -0.2F, -distance, 1.0F);
            Vector4f before = original.transform(new Vector4f(view));
            Vector4f after = selected.transform(view);
            helper.assertTrue(before.x == after.x && before.y == after.y && before.w == after.w && before.z == after.z,
                    "The far batch must keep native clip XYZW at " + distance);
            int depth = depth24(after.z / after.w);
            helper.assertTrue(depth > previous, "Native plate depth must remain ordered at " + distance);
            previous = depth;
        }
        int marker = (int) Math.round(KingCrimsonShatterProjection.VOID_DEPTH * (double) 0xFFFFFF);
        helper.assertTrue(previous <= marker - 2, "The farthest native plate must stay ahead of the D24 shell marker");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void farPlatesDeclineShortProjectionWithoutReversingOverlap(GameTestHelper helper) {
        float seam = 128.0F;
        float farthest = 7500.0F;
        Matrix4f projection = new Matrix4f().perspective(1.1F, 16.0F / 9.0F, 0.05F, 512.0F)
                .m20(0.001F).m21(-0.002F);
        Matrix4f original = new Matrix4f(projection);
        helper.assertTrue(KingCrimsonShatterProjection.depthAtDistance(projection, 250.0F)
                        < KingCrimsonShatterProjection.depthAtDistance(projection, 300.0F),
                "Native depth keeps the near plate in front at the overlap");
        helper.assertTrue(KingCrimsonShatterProjection.forFarPlates(projection, seam, farthest) == null
                        && projection.equals(original),
                "A short projection must decline the far batch rather than reverse near/far depth at the overlap");
        helper.assertTrue(KingCrimsonShatterProjection.forFarPlates(new Matrix4f(), seam, farthest) == null
                        && KingCrimsonShatterProjection.forFarPlates(new Matrix4f(original).m02(0.1F), seam, farthest) == null
                        && KingCrimsonShatterProjection.forFarPlates(new Matrix4f(original).m21(Float.NaN), seam, farthest) == null
                        && KingCrimsonShatterProjection.forFarPlates(original, 127.0F, farthest) == null
                        && KingCrimsonShatterProjection.forFarPlates(original, 512.0F, farthest) == null
                        && KingCrimsonShatterProjection.forFarPlates(original, seam, Float.POSITIVE_INFINITY) == null,
                "Unsupported projections and impossible bounds must not get a fabricated depth mapping");
        helper.succeed();
    }

    private static int depth24(float ndcZ) {
        return (int) Math.round((ndcZ * 0.5D + 0.5D) * 0xFFFFFF);
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void irisMotionIsZeroBeforeTheFirstRenderedFrame(GameTestHelper helper) {
        Vector3f delta = KingCrimsonShatterMotion.pivotDelta(false,
                new Matrix4f().translation(-20.0F, 1.0F, 3.0F), new Matrix4f().translation(27.0F, 4.0F, 5.0F),
                new Matrix4f(), new Matrix4f(), new Vector3f(7.0F, 8.0F, 9.0F), new Vector3f());
        helper.assertTrue(delta.lengthSquared() == 0.0F, "Uninitialized Iris history must not emit motion");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void irisMotionIncludesTheCameraAndThePlate(GameTestHelper helper) {
        Vector3f delta = KingCrimsonShatterMotion.pivotDelta(true,
                new Matrix4f().rotationY((float) (Math.PI / 2.0)).translate(-2.0F, 0.0F, 0.0F),
                new Matrix4f().translation(4.0F, 0.0F, 0.0F),
                new Matrix4f().translation(-1.0F, 0.0F, 0.0F), new Matrix4f().translation(3.0F, 0.0F, 0.0F),
                new Vector3f(), new Vector3f());
        helper.assertTrue(delta.distanceSquared(-2.0F, 0.0F, -2.0F) < 1.0E-10F,
                "Iris must receive the current pivot minus the prior view-space pivot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void aPlateStaysWhereTheGroundIsUntilItComesLoose(GameTestHelper helper) {
        KingCrimsonShatterPlan.Pose pose = new KingCrimsonShatterPlan.Pose();
        for (long seed : SEEDS) {
            KingCrimsonShatterPlan plan = plan(seed);
            for (int plate = 0; plate < plan.plateCount(); plate++) {
                plan.pose(plate, 0.0F, pose);
                helper.assertTrue(pose.isIdentity(), "plate " + plate + " is not where the ground is at the start");
                plan.pose(plate, plan.startTick(plate), pose);
                helper.assertTrue(pose.isIdentity(), "plate " + plate + " moved before its start");
                helper.assertTrue(plan.startTick(plate) > plan.latestArrival(plate),
                        "plate " + plate + " starts at " + plan.startTick(plate) + ", before its lines reach it at "
                                + plan.latestArrival(plate));
                plan.pose(plate, plan.startTick(plate) + 5.0F, pose);
                helper.assertFalse(pose.isIdentity(), "plate " + plate + " never moves");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_shatter_plan")
    public static void everyColumnInRangeHasOnePlateAndPlatesHoldTogether(GameTestHelper helper) {
        for (long seed : SEEDS) {
            KingCrimsonShatterPlan plan = plan(seed);
            int side = RANGE * 2 + 1;
            int[] owner = new int[side * side];
            boolean[] seen = new boolean[plan.plateCount()];
            for (int dz = -RANGE; dz <= RANGE; dz++) {
                for (int dx = -RANGE; dx <= RANGE; dx++) {
                    int index = dx + RANGE + (dz + RANGE) * side;
                    if (dx * dx + dz * dz > RANGE * RANGE) {
                        owner[index] = -1;
                        continue;
                    }
                    int plate = plan.plateAt(ORIGIN_X + dx, ORIGIN_Z + dz);
                    helper.assertTrue(plate >= 0 && plate < plan.plateCount(),
                            "column " + dx + "," + dz + " has no plate: " + plate);
                    helper.assertTrue(plate == plan.plateAt(ORIGIN_X + dx, ORIGIN_Z + dz),
                            "column " + dx + "," + dz + " changed its plate");
                    owner[index] = plate;
                    seen[plate] = true;
                }
            }
            helper.assertTrue(plan.plateAt(ORIGIN_X + RANGE * 3, ORIGIN_Z) == -1,
                    "a column far beyond the range has a plate");
            for (int plate = 0; plate < plan.plateCount(); plate++) {
                helper.assertTrue(seen[plate] || plan.plateDistance(plate) > RANGE * 0.7F,
                        "plate " + plate + " at " + plan.plateDistance(plate) + " blocks owns no column");
            }
            assertPlatesHoldTogether(helper, plan, owner, side);
        }
        helper.succeed();
    }

    // A loose piece far from the rest of its plate would fly with it, across a gap. Pieces of a few columns at a voxel
    // stair of a thin corner are not worth a rule.
    private static void assertPlatesHoldTogether(
            GameTestHelper helper, KingCrimsonShatterPlan plan, int[] owner, int side) {
        boolean[] visited = new boolean[owner.length];
        int[] queue = new int[owner.length];
        int[] largest = new int[plan.plateCount()];
        int[] total = new int[plan.plateCount()];
        for (int start = 0; start < owner.length; start++) {
            if (visited[start] || owner[start] < 0) {
                continue;
            }
            int plate = owner[start];
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            while (head < tail) {
                int at = queue[head++];
                int ax = at % side;
                int az = at / side;
                for (int oz = -1; oz <= 1; oz++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        int nx = ax + ox;
                        int nz = az + oz;
                        if (nx < 0 || nz < 0 || nx >= side || nz >= side) {
                            continue;
                        }
                        int next = nx + nz * side;
                        if (!visited[next] && owner[next] == plate) {
                            visited[next] = true;
                            queue[tail++] = next;
                        }
                    }
                }
            }
            largest[plate] = Math.max(largest[plate], tail);
            total[plate] += tail;
        }
        for (int plate = 0; plate < plan.plateCount(); plate++) {
            int loose = total[plate] - largest[plate];
            helper.assertTrue(loose * 50 <= total[plate] + 50,
                    "plate " + plate + " has " + loose + " of its " + total[plate] + " columns apart from the rest");
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void theHoleOpensAtTheUserAndSpreads(GameTestHelper helper) {
        for (long seed : SEEDS) {
            KingCrimsonShatterPlan plan = plan(seed);
            float earliest = Float.MAX_VALUE;
            float latest = 0.0F;
            for (int near = 0; near < plan.plateCount(); near++) {
                earliest = Math.min(earliest, plan.startTick(near));
                latest = Math.max(latest, plan.startTick(near));
                for (int far = 0; far < plan.plateCount(); far++) {
                    if (plan.plateDistance(far) >= plan.plateDistance(near) + KingCrimsonShatterPlan.LEADS_BY_BLOCKS + 0.01F) {
                        helper.assertTrue(plan.startTick(far) > plan.startTick(near),
                                "plate " + far + " at " + plan.plateDistance(far) + " blocks starts no later than plate "
                                        + near + " at " + plan.plateDistance(near));
                    }
                }
            }
            helper.assertTrue(latest - earliest >= 20.0F, "the plates all start within " + (latest - earliest) + " ticks: no ripple");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void everyPlateIsGoneByTheEnd(GameTestHelper helper) {
        KingCrimsonShatterPlan.Pose pose = new KingCrimsonShatterPlan.Pose();
        for (long seed : SEEDS) {
            KingCrimsonShatterPlan plan = plan(seed);
            for (int plate = 0; plate < plan.plateCount(); plate++) {
                float retire = plan.retireTick(plate);
                float tear = plan.tearStartTick(plate);
                float shrink = (retire - tear) * KingCrimsonShatterPlan.TEAR_SHRINK_SHARE;
                helper.assertTrue(retire <= KingCrimsonShatterPlan.END_TICK,
                        "plate " + plate + " lives until tick " + retire);
                float life = retire - plan.startTick(plate);
                helper.assertTrue(life >= KingCrimsonShatterPlan.MIN_LIFE_TICKS - 0.01F
                                && life <= KingCrimsonShatterPlan.MAX_LIFE_TICKS,
                        "plate " + plate + " lives " + life + " ticks");
                helper.assertTrue(retire - tear >= 10.0F && retire - tear <= 32.0F, "plate " + plate + " tears for " + (retire - tear));
                plan.pose(plate, retire + 0.01F, pose);
                helper.assertTrue(pose.scale == 0.0F, "plate " + plate + " is still there after its end");
                plan.pose(plate, KingCrimsonShatterPlan.END_TICK, pose);
                helper.assertTrue(pose.scale == 0.0F, "plate " + plate + " is still there after the end of it all");
                plan.pose(plate, retire - shrink - 0.01F, pose);
                helper.assertTrue(pose.scale == 1.0F, "plate " + plate + " shrinks too early");
                plan.pose(plate, retire - shrink * 0.5F, pose);
                helper.assertTrue(pose.scale > 0.0F && pose.scale < 1.0F, "plate " + plate + " does not shrink: " + pose.scale);
                helper.assertTrue(plan.tearShare(plate, tear) == 0.0F, "plate " + plate + " has lost columns before its tear");
                helper.assertTrue(plan.tearShare(plate, retire) == 1.0F, "plate " + plate + " has columns left at its end");
                float share = 0.0F;
                for (float tick = tear + 1.0F; tick < retire; tick += 1.0F) {
                    float now = plan.tearShare(plate, tick);
                    helper.assertTrue(now >= share && now - share <= 0.15F, "plate " + plate + " loses " + (now - share) + " in one tick");
                    share = now;
                }
            }
        }
        helper.succeed();
    }

    // There is no common moment: the ends are spread, no short while takes many plates, and the horizon thins first.
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void thePlatesGoEachInItsOwnTime(GameTestHelper helper) {
        float end = KingCrimsonShatterPlan.END_TICK;
        helper.assertTrue(end >= 80.0F && end <= 90.0F, "the ground ends at tick " + end + ", not about 4.45 s after the flip");
        for (int range : new int[] {128, RANGE}) {
            // many seeds: one seed in four would let the dice bunch too many ends without the spread
            for (long seed = 1L; seed <= 40L; seed++) {
                KingCrimsonShatterPlan plan = KingCrimsonShatterPlan.create(ORIGIN_X, ORIGIN_Z, seed * 0x9E3779B97F4A7C15L, range, YAW);
                int plates = plan.plateCount();
                float[] ends = new float[plates];
                for (int plate = 0; plate < plates; plate++) {
                    ends[plate] = plan.retireTick(plate);
                }
                float[] sorted = ends.clone();
                Arrays.sort(sorted);
                helper.assertTrue(sorted[plates - 1] - sorted[0] >= 24.0F,
                        "the plates end within " + (sorted[plates - 1] - sorted[0]) + " ticks of each other");
                helper.assertTrue(sorted[plates - 1] <= end, "a plate ends at " + sorted[plates - 1]);
                int most = 0;
                for (int first = 0, last = 0; last < plates; last++) {
                    while (sorted[last] - sorted[first] > 2.0F) {
                        first++;
                    }
                    most = Math.max(most, last - first + 1);
                }
                helper.assertTrue(most * 100 <= plates * 12, most + " of " + plates + " plates go within 2 ticks at range " + range);
                // the plates are sorted by distance
                int quarter = plates / 4;
                float nearEnds = 0.0F;
                float farEnds = 0.0F;
                for (int i = 0; i < quarter; i++) {
                    nearEnds += ends[i];
                    farEnds += ends[plates - 1 - i];
                }
                helper.assertTrue(nearEnds / quarter >= farEnds / quarter + 6.0F,
                        "the near plates end at " + nearEnds / quarter + " on average, the far ones at " + farEnds / quarter);
            }
        }
        helper.succeed();
    }

    // A plate has flown visibly before it starts to go, and the near ones have a long way behind them.
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void aPlateHasFlownBeforeItTears(GameTestHelper helper) {
        KingCrimsonShatterPlan.Pose pose = new KingCrimsonShatterPlan.Pose();
        for (int range : new int[] {128, RANGE}) {
            for (long seed : SEEDS) {
                KingCrimsonShatterPlan plan = KingCrimsonShatterPlan.create(ORIGIN_X, ORIGIN_Z, seed, range, YAW);
                for (int plate = 0; plate < plan.plateCount(); plate++) {
                    float tear = plan.tearStartTick(plate);
                    plan.pose(plate, tear, pose);
                    float flown = (float) Math.hypot(pose.dx, pose.dz);
                    helper.assertTrue(pose.scale == 1.0F, "plate " + plate + " has shrunk at the start of its tear");
                    if (plan.plateDistance(plate) < 30.0F) {
                        helper.assertTrue(tear >= 45.0F, "plate " + plate + " near the user tears from tick " + tear);
                        helper.assertTrue(flown >= 18.0F, "plate " + plate + " near the user has flown only " + flown + " at its tear");
                        plan.pose(plate, plan.retireTick(plate), pose);
                        float last = (float) Math.hypot(pose.dx, pose.dz);
                        helper.assertTrue(last >= 45.0F, "plate " + plate + " near the user has flown only " + last + " by its end");
                    } else {
                        helper.assertTrue(flown >= 2.0F, "plate " + plate + " has flown only " + flown + " at its tear");
                    }
                }
            }
        }
        helper.succeed();
    }

    // The flight scatters: off the radial and at its own height, and still near the horizon line when the plate has gone.
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void platesScatterAndEndNearTheHorizon(GameTestHelper helper) {
        KingCrimsonShatterPlan.Pose pose = new KingCrimsonShatterPlan.Pose();
        for (long seed : SEEDS) {
            KingCrimsonShatterPlan plan = plan(seed);
            int plates = plan.plateCount();
            int turned = 0;
            float lowest = Float.MAX_VALUE;
            float highest = 0.0F;
            for (int plate = 0; plate < plates; plate++) {
                float dirX = plan.pivotX(plate) - ORIGIN_X;
                float dirZ = plan.pivotZ(plate) - ORIGIN_Z;
                float length = (float) Math.hypot(dirX, dirZ);
                plan.pose(plate, plan.retireTick(plate), pose);
                float out = (pose.dx * dirX + pose.dz * dirZ) / length;
                float aside = (-pose.dx * dirZ + pose.dz * dirX) / length;
                double angle = Math.toDegrees(Math.atan2(Math.abs(aside), out));
                helper.assertTrue(angle <= 15.0D, "plate " + plate + " flies " + angle + " degrees off the radial");
                if (angle >= 3.0D) {
                    turned++;
                }
                double elevation = Math.toDegrees(Math.atan2(pose.dy, length + out));
                helper.assertTrue(elevation <= 9.0D, "plate " + plate + " ends " + elevation + " degrees above the horizon");
                lowest = Math.min(lowest, pose.dy);
                highest = Math.max(highest, pose.dy);
            }
            helper.assertTrue(turned * 3 >= plates, "only " + turned + " of " + plates + " plates leave the radial");
            helper.assertTrue(highest - lowest >= 10.0F, "the plates end between " + lowest + " and " + highest + " blocks up");
        }
        helper.succeed();
    }

    // Vanilla's far plane is 4 x the render distance. A camera at the origin must still see each plate go.
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void nothingReachesTheFarPlaneBeforeItHasGone(GameTestHelper helper) {
        KingCrimsonShatterPlan.Pose pose = new KingCrimsonShatterPlan.Pose();
        for (int range : new int[] {32, 48, 64, 96, 128, 176, RANGE}) {
            for (long seed : SEEDS) {
                KingCrimsonShatterPlan plan = KingCrimsonShatterPlan.create(ORIGIN_X, ORIGIN_Z, seed, range, YAW);
                for (int plate = 0; plate < plan.plateCount(); plate++) {
                    plan.pose(plate, plan.retireTick(plate), pose);
                    double far = Math.sqrt(square(plan.pivotX(plate) - ORIGIN_X + pose.dx) + square(pose.dy) + square(plan.pivotZ(plate) - ORIGIN_Z + pose.dz))
                            + plan.plateRadius(plate);
                    helper.assertTrue(far <= range * 3.6D, "plate " + plate + " is " + far + " blocks out at render range " + range);
                }
            }
        }
        helper.succeed();
    }

    private static double square(double value) {
        return value * value;
    }

    // Fired out from the feet: anchored there at first, then the line lets go and keeps running, at no more than the cap.
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void crestsAreFiredOutFromTheFeet(GameTestHelper helper) {
        for (long seed : SEEDS) {
            KingCrimsonShatterPlan plan = plan(seed);
            int primaries = 0;
            for (KingCrimsonShatterPlan.Cut cut : plan.cuts()) {
                float reach = cut.crestReach;
                if (cut.kind == KingCrimsonShatterPlan.KIND_BRANCH) {
                    helper.assertTrue(reach <= 70.0F, "a cross crest reaches for " + reach + " blocks");
                }
                if (cut.kind != KingCrimsonShatterPlan.KIND_PRIMARY) {
                    continue;
                }
                primaries++;
                float launch = KingCrimsonShatterPlan.crestHead(reach, 1.0F);
                helper.assertTrue(launch <= 8.5F, "a crest leaves at " + launch + " blocks a tick");
                helper.assertTrue(launch >= 4.0F, "a crest leaves at only " + launch + " blocks a tick");
                float atDeath = KingCrimsonShatterPlan.crestHead(reach, KingCrimsonShatterPlan.CREST_LIFE_MIN);
                helper.assertTrue(atDeath >= 60.0F, "a crest's head gets only " + atDeath + " blocks out in its life");
                float stillRuns = atDeath - KingCrimsonShatterPlan.crestHead(reach, KingCrimsonShatterPlan.CREST_LIFE_MIN - 1.0F);
                helper.assertTrue(stillRuns >= 1.0F, "a crest's head has all but stopped: " + stillRuns + " blocks a tick");
                helper.assertTrue(KingCrimsonShatterPlan.crestHead(reach, KingCrimsonShatterPlan.CREST_LIFE_MAX) <= KingCrimsonShatterPlan.CREST_FARTHEST,
                        "a crest's head passes the ground that was read for it");
                helper.assertTrue(cut.startTick + KingCrimsonShatterPlan.WIND_LAG_TICKS + KingCrimsonShatterPlan.CREST_LIFE_MAX <= KingCrimsonShatterPlan.WIND_LAST_TICK,
                        "the renderer would cut a crest off");
                for (float age = 0.5F; age <= KingCrimsonShatterPlan.CREST_LIFE_MAX; age += 0.5F) {
                    helper.assertTrue(cut.headDistance(age + KingCrimsonShatterPlan.WIND_LAG_TICKS) > KingCrimsonShatterPlan.crestHead(reach, age),
                            "a crest overtakes its crack at age " + age);
                }
                helper.assertTrue(cut.headDistance(10.0F) >= RANGE, "a crack is only " + cut.headDistance(10.0F) + " blocks out at age 10");
                float feet = KingCrimsonShatterPlan.crestTail(KingCrimsonShatterPlan.crestHead(reach, 2.0F), 22.0F);
                helper.assertTrue(feet <= 1.0F, "a crest has left the feet " + feet + " blocks behind at age 2");
                float before = KingCrimsonShatterPlan.crestTail(KingCrimsonShatterPlan.crestHead(reach, 12.0F), 40.0F);
                float after = KingCrimsonShatterPlan.crestTail(KingCrimsonShatterPlan.crestHead(reach, 20.0F), 40.0F);
                helper.assertTrue(before > 1.0F && after >= before + 8.0F, "a crest's tail went from " + before + " to " + after);
            }
            helper.assertTrue(primaries >= 7, primaries + " primaries");
        }
        helper.succeed();
    }

    // Clusters and strips go together: a column goes about when its neighbour goes, and the plate as a whole takes its time.
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void aPlateTearsInClusters(GameTestHelper helper) {
        for (long seed : SEEDS) {
            KingCrimsonShatterPlan plan = plan(seed);
            for (int plate = 0; plate < plan.plateCount(); plate += 4) {
                int dx0 = (int) (plan.pivotX(plate) - ORIGIN_X);
                int dz0 = (int) (plan.pivotZ(plate) - ORIGIN_Z);
                double step = 0.0D;
                float lowest = Float.MAX_VALUE;
                float highest = -Float.MAX_VALUE;
                int counted = 0;
                for (int dz = -24; dz <= 24; dz++) {
                    for (int dx = -24; dx <= 24; dx++) {
                        float order = plan.tearOrder(plate, dx0 + dx, dz0 + dz);
                        helper.assertTrue(order >= 0.0F && order <= 1.0F, "a column's order is " + order);
                        step += Math.abs(order - plan.tearOrder(plate, dx0 + dx + 1, dz0 + dz));
                        lowest = Math.min(lowest, order);
                        highest = Math.max(highest, order);
                        counted++;
                    }
                }
                helper.assertTrue(step / counted <= 0.12D, "next to each other the columns of plate " + plate + " differ by " + step / counted);
                helper.assertTrue(highest - lowest >= 0.3F, "the columns of plate " + plate + " all go at about one time");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void smallBlocksDriftOutAndEndEachInItsOwnTime(GameTestHelper helper) {
        float end = KingCrimsonShatterPlan.END_TICK;
        float shrink = KingCrimsonShatterPlan.DEBRIS_SHRINK_TICKS;
        for (float earliest = 0.0F; earliest <= 90.0F; earliest += 2.5F) {
            for (float fraction = 0.0F; fraction <= 1.0F; fraction += 0.25F) {
                float spawn = KingCrimsonShatterPlan.debrisSpawnTick(earliest, fraction);
                helper.assertTrue(spawn >= KingCrimsonShatterPlan.DEBRIS_FIRST_TICK && spawn <= KingCrimsonShatterPlan.DEBRIS_LAST_TICK,
                        "a small block that may leave from " + earliest + " leaves at " + spawn);
                helper.assertTrue(spawn >= Math.min(earliest, KingCrimsonShatterPlan.DEBRIS_LAST_TICK) - 1.0E-3F,
                        "a small block leaves at " + spawn + ", before its " + earliest);
                for (float distance = 3.0F; distance <= 64.0F; distance += 15.25F) {
                    for (float own = 0.0F; own <= 1.0F; own += 0.2F) {
                        float gone = KingCrimsonShatterPlan.debrisEndTick(spawn, distance, own);
                        helper.assertTrue(gone >= KingCrimsonShatterPlan.DEBRIS_FIRST_END_TICK && gone <= end,
                                "a small block goes at " + gone);
                        helper.assertTrue(gone >= spawn + 10.9F, "a small block from " + spawn + " is gone at " + gone);
                        helper.assertTrue(KingCrimsonShatterPlan.debrisScale(gone - shrink - 1.0F - spawn, gone - shrink - 1.0F, gone) == 1.0F,
                                "a small block from " + spawn + " is not full size just before its shrink");
                        float during = KingCrimsonShatterPlan.debrisScale(gone - shrink * 0.5F - spawn, gone - shrink * 0.5F, gone);
                        helper.assertTrue(during > 0.0F && during < 1.0F, "a small block does not shrink: " + during);
                        helper.assertTrue(KingCrimsonShatterPlan.debrisScale(gone - spawn, gone, gone) == 0.0F, "a small block outlives its end");
                    }
                }
            }
        }
        float nearOne = KingCrimsonShatterPlan.debrisEndTick(8.0F, 5.0F, 0.5F);
        float farOne = KingCrimsonShatterPlan.debrisEndTick(8.0F, 60.0F, 0.5F);
        helper.assertTrue(nearOne >= farOne + 8.0F, "a near block ends at " + nearOne + ", a far one at " + farOne);
        float early = KingCrimsonShatterPlan.debrisEndTick(8.0F, 30.0F, 0.1F);
        float late = KingCrimsonShatterPlan.debrisEndTick(8.0F, 30.0F, 0.9F);
        helper.assertTrue(late >= early + 15.0F, "two blocks at one distance end at " + early + " and " + late);
        float before = 0.0F;
        for (float age = 1.0F; age <= 80.0F; age += 1.0F) {
            float step = KingCrimsonShatterPlan.debrisDrift(age) - KingCrimsonShatterPlan.debrisDrift(age - 1.0F);
            helper.assertTrue(step > before, "a small block's drift slows at age " + age);
            before = step;
        }
        float drifted = KingCrimsonShatterPlan.debrisDrift(60.0F) * 0.1F;
        helper.assertTrue(drifted >= 10.0F && drifted <= 40.0F, "a usual small block drifts " + drifted + " blocks in 3 s");
        helper.assertTrue(KingCrimsonShatterPlan.debrisRise(0.06F, 36.0F) <= 6.0F, "the small blocks rise too fast");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_shatter_plan")
    public static void theSameSeedGivesTheSamePlan(GameTestHelper helper) {
        KingCrimsonShatterPlan.Pose first = new KingCrimsonShatterPlan.Pose();
        KingCrimsonShatterPlan.Pose second = new KingCrimsonShatterPlan.Pose();
        KingCrimsonShatterPlan one = plan(99L);
        KingCrimsonShatterPlan again = plan(99L);
        KingCrimsonShatterPlan other = plan(100L);
        helper.assertTrue(one.plateCount() == again.plateCount(), "the plate counts differ");
        helper.assertTrue(one.cuts().size() == again.cuts().size(), "the cut counts differ");
        int moved = 0;
        for (int dz = -RANGE; dz <= RANGE; dz += 3) {
            for (int dx = -RANGE; dx <= RANGE; dx += 3) {
                if (dx * dx + dz * dz > RANGE * RANGE) {
                    continue;
                }
                int plate = one.plateAt(ORIGIN_X + dx, ORIGIN_Z + dz);
                helper.assertTrue(plate == again.plateAt(ORIGIN_X + dx, ORIGIN_Z + dz),
                        "column " + dx + "," + dz + " differs under the same seed");
                if (other.plateCount() != one.plateCount() || plate != other.plateAt(ORIGIN_X + dx, ORIGIN_Z + dz)) {
                    moved++;
                }
            }
        }
        helper.assertTrue(moved > 1000, "another seed moved only " + moved + " sampled columns to another plate");
        for (int plate = 0; plate < one.plateCount(); plate++) {
            helper.assertTrue(one.startTick(plate) == again.startTick(plate), "start of " + plate + " differs");
            helper.assertTrue(one.retireTick(plate) == again.retireTick(plate), "end of " + plate + " differs");
            for (float tick = 0.0F; tick < KingCrimsonShatterPlan.END_TICK; tick += 7.5F) {
                one.pose(plate, tick, first);
                again.pose(plate, tick, second);
                helper.assertTrue(first.dx == second.dx && first.dy == second.dy && first.dz == second.dz
                                && first.m01 == second.m01 && first.m20 == second.m20 && first.scale == second.scale,
                        "pose of " + plate + " at " + tick + " differs");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void platesLeaveUpAndOutward(GameTestHelper helper) {
        KingCrimsonShatterPlan.Pose pose = new KingCrimsonShatterPlan.Pose();
        KingCrimsonShatterPlan plan = plan(5L);
        for (int plate = 0; plate < plan.plateCount(); plate++) {
            float previousOut = 0.0F;
            float previousUp = 0.0F;
            float dirX = plan.pivotX(plate) - ORIGIN_X;
            float dirZ = plan.pivotZ(plate) - ORIGIN_Z;
            float length = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
            for (float age = 1.0F; age < plan.retireTick(plate) - plan.startTick(plate) - 1.0F; age += 1.0F) {
                plan.pose(plate, plan.startTick(plate) + age, pose);
                float out = (pose.dx * dirX + pose.dz * dirZ) / length;
                helper.assertTrue(out >= previousOut && pose.dy >= previousUp,
                        "plate " + plate + " turned back at age " + age);
                previousOut = out;
                previousUp = pose.dy;
            }
            plan.pose(plate, plan.startTick(plate) + 8.0F, pose);
            helper.assertTrue(pose.dy > 0.0F && pose.dy <= 1.6F, "plate " + plate + " lifted " + pose.dy + " in its first ticks");
        }
        helper.succeed();
    }

    // Crack, trench, parting and flight are one motion: from the moment a plate comes loose until it has gone it neither
    // stands still nor slows down, in its displacement or in its turn.
    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void everyPlateMovesInOneSmoothRun(GameTestHelper helper) {
        float step = 3.0F;
        KingCrimsonShatterPlan.Pose pose = new KingCrimsonShatterPlan.Pose();
        for (long seed : SEEDS) {
            KingCrimsonShatterPlan plan = plan(seed);
            for (int plate = 0; plate < plan.plateCount(); plate++) {
                float start = plan.startTick(plate);
                // the breaking ripples out, so far out a plate waits for the ripple; near the user only for the crack
                float wait = plan.plateDistance(plate) < 16.0F ? 4.0F : 34.0F;
                helper.assertTrue(start <= plan.latestArrival(plate) + wait,
                        "plate " + plate + " waits from " + plan.latestArrival(plate) + " to " + start + " after its lines have run by");
                helper.assertTrue(start <= 36.0F, "plate " + plate + " starts only at tick " + start);
                float lastTick = plan.retireTick(plate);
                double before = 0.0D;
                double lastSpeed = 0.0D;
                float m01 = 0.0F;
                float m02 = 0.0F;
                float m12 = 0.0F;
                for (float tick = start + step; tick <= lastTick; tick += step) {
                    plan.pose(plate, tick, pose);
                    double travelled = Math.sqrt(pose.dx * pose.dx + pose.dy * pose.dy + pose.dz * pose.dz);
                    double speed = travelled - before;
                    helper.assertTrue(speed > 1.0E-4D, "plate " + plate + " stands still at tick " + tick);
                    helper.assertTrue(speed >= lastSpeed - 1.0E-3D,
                            "plate " + plate + " slows from " + lastSpeed + " to " + speed + " a step at tick " + tick);
                    float turned = Math.abs(pose.m01 - m01) + Math.abs(pose.m02 - m02) + Math.abs(pose.m12 - m12);
                    helper.assertTrue(turned > 1.0E-5F, "plate " + plate + " stops turning at tick " + tick);
                    before = travelled;
                    lastSpeed = speed;
                    m01 = pose.m01;
                    m02 = pose.m02;
                    m12 = pose.m12;
                }
            }
        }
        helper.succeed();
    }

    // Crack lines and plate edges are the same straight lines: every side of every plate lies on one cut.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_shatter_plan")
    public static void platesAreCutByStraightLinesThatRunInOrder(GameTestHelper helper) {
        for (long seed : SEEDS) {
            KingCrimsonShatterPlan plan = plan(seed);
            List<KingCrimsonShatterPlan.Cut> cuts = plan.cuts();
            int primaries = 0;
            float latestLaunch = 0.0F;
            for (KingCrimsonShatterPlan.Cut cut : cuts) {
                helper.assertTrue(Math.abs(cut.dirX * cut.dirX + cut.dirZ * cut.dirZ - 1.0D) < 1.0E-6D,
                        "a cut's direction is not a unit");
                if (cut.kind == KingCrimsonShatterPlan.KIND_PRIMARY) {
                    primaries++;
                    helper.assertTrue(cut.startX == 0.0D && cut.startZ == 0.0D, "a primary does not leave the user's feet");
                    latestLaunch = Math.max(latestLaunch, cut.startTick);
                } else {
                    double reach = Math.hypot(cut.startX, cut.startZ);
                    // a cut starts when a line that runs out from the feet at the head's speed can have reached it
                    helper.assertTrue(cut.startTick >= plan.headAge(reach) - 1.0E-3F,
                            "a cut " + reach + " blocks out starts at " + cut.startTick);
                }
            }
            helper.assertTrue(primaries >= 7 && primaries <= 9, primaries + " primaries");
            helper.assertTrue(latestLaunch >= 1.0F && latestLaunch <= 2.0F, "the launches end at " + latestLaunch);
            helper.assertTrue(plan.plateCount() >= 40 && plan.plateCount() <= 400, plan.plateCount() + " plates");
            for (int plate = 0; plate < plan.plateCount(); plate++) {
                double[] x = plan.polygonX(plate);
                double[] z = plan.polygonZ(plate);
                for (int i = 0; i < x.length; i++) {
                    int next = (i + 1) % x.length;
                    double middleX = (x[i] + x[next]) * 0.5D;
                    double middleZ = (z[i] + z[next]) * 0.5D;
                    boolean onLine = Math.hypot(x[next] - x[i], z[next] - z[i]) < 1.0E-6D
                            || Math.abs(Math.hypot(middleX, middleZ)) > RANGE * 0.9D;
                    for (KingCrimsonShatterPlan.Cut cut : cuts) {
                        double along = (middleX - cut.startX) * cut.dirX + (middleZ - cut.startZ) * cut.dirZ;
                        double across = -(middleX - cut.startX) * cut.dirZ + (middleZ - cut.startZ) * cut.dirX;
                        if (Math.abs(across) < 1.0E-6D && along >= -1.0E-6D && along <= cut.length + 1.0E-6D) {
                            onLine = true;
                            break;
                        }
                    }
                    helper.assertTrue(onLine, "side " + i + " of plate " + plate + " lies on no cut");
                }
                // near the user, plates are small enough to read; far out they grow
                if (plan.plateDistance(plate) < 40.0F) {
                    helper.assertTrue(area(x, z) < 900.0D, "plate " + plate + " near the user has area " + area(x, z));
                }
            }
        }
        helper.succeed();
    }

    private static double area(double[] x, double[] z) {
        double twice = 0.0D;
        for (int i = 0; i < x.length; i++) {
            int next = (i + 1) % x.length;
            twice += x[i] * z[next] - x[next] * z[i];
        }
        return Math.abs(twice) * 0.5D;
    }

    // The range is a parameter: well past the usual one the wave must still read as one and end in time.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_shatter_plan")
    public static void aMuchLargerRangeStillEndsInTime(GameTestHelper helper) {
        KingCrimsonShatterPlan plan = KingCrimsonShatterPlan.create(ORIGIN_X, ORIGIN_Z, 7L, 1024, YAW);
        helper.assertTrue(plan.plateCount() <= 700, plan.plateCount() + " plates");
        helper.assertTrue(plan.plateAt(ORIGIN_X + 1000, ORIGIN_Z) >= 0, "no plate 1000 blocks out");
        for (int plate = 0; plate < plan.plateCount(); plate++) {
            helper.assertTrue(plan.startTick(plate) <= 44.0F, "plate " + plate + " starts at " + plan.startTick(plate));
            helper.assertTrue(plan.retireTick(plate) <= KingCrimsonShatterPlan.END_TICK,
                    "plate " + plate + " ends at " + plan.retireTick(plate));
            helper.assertTrue(plan.retireTick(plate) - plan.startTick(plate) >= KingCrimsonShatterPlan.MIN_LIFE_TICKS - 0.01F,
                    "plate " + plate + " lives too short: " + (plan.retireTick(plate) - plan.startTick(plate)));
            helper.assertTrue(plan.latestArrival(plate) <= 25.0F,
                    "the cracks of plate " + plate + " arrive only at " + plan.latestArrival(plate));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_shatter_plan")
    public static void a2048BlockPlanKeepsThePlateLifetimesAndWave(GameTestHelper helper) {
        long[] seeds = new long[SEEDS.length + 1];
        seeds[0] = 7L;
        System.arraycopy(SEEDS, 0, seeds, 1, SEEDS.length);
        KingCrimsonShatterPlan.Pose pose = new KingCrimsonShatterPlan.Pose();
        for (long seed : seeds) {
            KingCrimsonShatterPlan plan = KingCrimsonShatterPlan.create(ORIGIN_X, ORIGIN_Z, seed, 2048, YAW);
            int plates = plan.plateCount();
            helper.assertTrue(plates <= 700 && plan.plateAt(ORIGIN_X + 2024, ORIGIN_Z) >= 0,
                    "The bounded plan must actually reach the far ring, seed " + seed);
            float[] ends = new float[plates];
            for (int plate = 0; plate < plates; plate++) {
                float start = plan.startTick(plate);
                float retire = plan.retireTick(plate);
                float life = retire - start;
                String where = " at plate " + plate + ", seed " + seed;
                ends[plate] = retire;
                helper.assertTrue(retire <= 85.0F, "Late retirement" + where + ": " + retire);
                helper.assertTrue(life >= KingCrimsonShatterPlan.MIN_LIFE_TICKS
                                && life <= KingCrimsonShatterPlan.MAX_LIFE_TICKS,
                        "Invalid life" + where + ": " + life);
                helper.assertTrue(start > plan.latestArrival(plate) && plan.latestArrival(plate) <= 25.0F,
                        "The crack must reach its edges before the plate moves" + where);
                float tear = retire - plan.tearStartTick(plate);
                helper.assertTrue(tear >= 10.0F && tear <= 32.0F && plan.tearScale(plate, 85.0F) == 0.0F,
                        "The tear must remain gradual and finish by the end" + where);
                double previous = 0.0D;
                for (float tick = start + 3.0F; tick < retire; tick += 3.0F) {
                    plan.pose(plate, tick, pose);
                    double travel = Math.sqrt(square(pose.dx) + square(pose.dy) + square(pose.dz));
                    helper.assertTrue(travel > previous, "Flight stops at tick " + tick + where);
                    previous = travel;
                }
            }
            Arrays.sort(ends);
            int most = 0;
            for (int first = 0, last = 0; last < plates; last++) {
                while (ends[last] - ends[first] > 2.0F) first++;
                most = Math.max(most, last - first + 1);
            }
            helper.assertTrue(most * 100 <= plates * 12, most + " of " + plates + " plates go within 2 ticks, seed " + seed);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_plan")
    public static void farRetirementSlotsResolveAThreePlateCycle(GameTestHelper helper) {
        int[] order = {0, 3, 1, 2};
        float[] ends = {68.0F, 68.03F, 68.067F, 68.133F};
        float[] distances = {300.0F, 400.0F, 500.0F, 100.0F};
        float[] starts = {44.1F, 42.0F, 42.5F, 30.0F};
        float[] tears = {56.1F, 54.75F, 55.03F, 54.0F};
        int[] matched = KingCrimsonShatterPlan.matchFarRetirementSlots(order, ends, distances, starts, tears);
        helper.assertTrue(Arrays.equals(matched, new int[]{1, 3, 2, 0}) && Arrays.equals(order, new int[]{0, 3, 1, 2}),
                "The three far plates need a cycle, and the interleaved near slot must remain fixed");
        helper.assertTrue(KingCrimsonShatterPlan.matchFarRetirementSlots(order,
                        new float[]{68.0F, 68.03F, 68.067F, 68.099F}, distances, starts, tears) == null,
                "No compatible retirement should be reported when every far slot is too early for a plate");
        helper.succeed();
    }
}
