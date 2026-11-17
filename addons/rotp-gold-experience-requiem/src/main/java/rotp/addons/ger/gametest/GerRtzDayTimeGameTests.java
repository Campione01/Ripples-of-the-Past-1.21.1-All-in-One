package rotp.addons.ger.gametest;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Client;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.state.GerRtzRewindService;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;

/**
 * Rule G3 and the owner's exception of 2026-11-08 ("只有和天堂製造使用者在八個區塊內使用才會倒回"):
 * an acceleration keeps the day time through a rewind, unless the one who
 * accelerates stands inside the rewound area. Made in Heaven in the core's
 * terms: the level's time runs faster and its user is ahead of everyone in
 * his own time.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerRtzDayTimeGameTests {
    private static final ResourceLocation RATES = GoldExperienceRequiemAddon.id("gametest_day_time");
    private static final float ACCELERATION = 4.0F;
    private static final long DAY_AT_MARK = 1_000L;
    private static final long DAY_AT_PRESS = 13_000L;
    // One chunk around the mark, not the ability's 8: drops another scene left in a chunk that unloads between the mark
    // and the press would make the rewind refuse (UNLOADED_TARGET), which is its rule and not this test's subject.
    private static final int MARK_CHUNK_RADIUS = 1;
    private static final Set<UUID> AHEAD = ConcurrentHashMap.newKeySet();
    private static volatile boolean accelerating;
    private static boolean ratesRegistered;

    private GerRtzDayTimeGameTests() {}

    private static synchronized void registerRates() {
        if (ratesRegistered) {
            return;
        }
        ratesRegistered = true;
        EntityTimeRates.register(RATES, (entity, channel) ->
                channel == EntityTimeRates.Channel.BEING && AHEAD.contains(entity.getUUID())
                        ? ACCELERATION
                        : EntityTimeRates.NO_RATE);
        LevelTimeRates.register(RATES, (level, channel) ->
                channel == LevelTimeRates.Channel.TIME && accelerating
                        ? ACCELERATION
                        : LevelTimeRates.NO_RATE);
    }

    private static final class DayTime extends Sequence {
        private final boolean inside;
        private Actor owner;
        private Client ahead;

        DayTime(GameTestHelper helper, boolean inside) {
            super(helper, inside ? "rtz-day/inside" : "rtz-day/outside", 100);
            this.inside = inside;
        }

        @Override
        boolean step() {
            if (owner == null) {
                registerRates();
                owner = scene.actor();
                ahead = scene.client(scene.origin.add(0, 0, 3));
                long dayBefore = scene.level.getDayTime();
                scene.level.setDayTime(DAY_AT_MARK);
                scene.premise(GerRtzRewindService.setMark(scene.level, owner.user(), MARK_CHUNK_RADIUS)
                                == GerRtzRewindService.Result.SUCCESS,
                        "the mark is set");
                UUID aheadId = ahead.player().getUUID();
                AHEAD.add(aheadId);
                accelerating = true;
                scene.onClose(() -> {
                    accelerating = false;
                    AHEAD.remove(aheadId);
                    scene.level.setDayTime(dayBefore);
                });
                if (!inside) {
                    // 12 chunks off: he left the marked area
                    Vec3 far = scene.origin.add(16.0D * 12, 0, 0);
                    ahead.player().setPos(far.x, far.y, far.z);
                }
                return false;
            }
            scene.level.setDayTime(DAY_AT_PRESS);
            scene.premise(LevelTimeRates.rate(LevelTimeRates.Channel.TIME, scene.level) > 1.0F
                            && EntityTimeRates.rate(EntityTimeRates.Channel.BEING, ahead.player())
                                    > EntityTimeRates.rate(EntityTimeRates.Channel.BEING, owner.user()),
                    "the level's time is accelerated by a player ahead of the Requiem user");
            GerRtzRewindService.Result result =
                    GerRtzRewindService.rewind(scene.level, owner.user(), owner.stand(), owner.power(), 0);
            helper.assertTrue(result == GerRtzRewindService.Result.SUCCESS,
                    scene.id + ": Return to Zero answered " + result);
            long day = scene.level.getDayTime();
            scene.log("day time after the rewind=" + day);
            if (inside) {
                helper.assertTrue(day == DAY_AT_MARK,
                        scene.id + ": with the one who accelerates inside the area the day time is " + day
                                + ", not the mark's " + DAY_AT_MARK);
            }
            else {
                helper.assertTrue(day >= DAY_AT_PRESS,
                        scene.id + ": with the one who accelerates outside the area the day time went back to "
                                + day);
            }
            return true;
        }
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_rtz_day_inside")
    public static void theDayTimeGoesBackWithTheAcceleratingUserInsideTheArea(GameTestHelper helper) {
        new DayTime(helper, true).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_rtz_day_outside")
    public static void theDayTimeStaysWithTheAcceleratingUserOutsideTheArea(GameTestHelper helper) {
        new DayTime(helper, false).start();
    }
}
