package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Owner 2026-11-07: "能否酌情給予時長上限隨等級的一個提升，同時在設定頁面允許自定義時刪上限", "上限改成15吧". The longest erase is the
 * setting timeEraseMaxSeconds at Resolve 4 and 60% of it at Resolve 3, read once at the press. Each test holds the real
 * key down and counts the erase in server ticks; each has a batch of its own, as an erase moves the day of the server.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEraseLengthGameTests {
    private KingCrimsonEraseLengthGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_erase_length_resolve3")
    public static void atResolveThreeAHeldEraseLastsNineSeconds(GameTestHelper helper) {
        assertHeldEraseLasts(helper, 3, 15, 180);
    }

    @GameTest(template = "empty", timeoutTicks = 380, batch = "kc_erase_length_resolve4")
    public static void atResolveFourAHeldEraseLastsFifteenSeconds(GameTestHelper helper) {
        assertHeldEraseLasts(helper, 4, 15, 300);
    }

    @GameTest(template = "empty", timeoutTicks = 320, batch = "kc_erase_length_limit20_resolve3")
    public static void withALimitOfTwentySecondsResolveThreeGetsTwelve(GameTestHelper helper) {
        assertHeldEraseLasts(helper, 3, 20, 240);
    }

    @GameTest(template = "empty", timeoutTicks = 480, batch = "kc_erase_length_limit20_resolve4")
    public static void withALimitOfTwentySecondsResolveFourGetsTwenty(GameTestHelper helper) {
        assertHeldEraseLasts(helper, 4, 20, 400);
    }

    @GameTest(template = "empty", timeoutTicks = 420, batch = "kc_erase_length_edit")
    public static void aLimitEditedDuringAnEraseCountsFromTheNextOne(GameTestHelper helper) {
        Scene scene = new Scene(helper, 4, 15);
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        // Shorter, then longer: neither moves the erase that runs.
        helper.runAfterDelay(60, () -> KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(2));
        helper.runAfterDelay(120, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(scene.player),
                    "The limit was lowered to 2 s during the erase and the running erase of 15 s ended at once");
            KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(20);
        }));
        helper.runAfterDelay(340, () -> scene.fixture.guarded(() -> {
            scene.assertLasted(300, "An erase begun at a limit of 15 s lasts 15 s whatever the limit becomes");
            scene.fixture.release(scene.player, KingCrimsonEraseFixture.ERASE_KEY);
            scene.power.setAbilityCooldown("kingcrimson_timeerase", 0, 0);
            scene.power.setStamina(scene.power.getMaxStamina());
            scene.startedAt = -1L;
            scene.endedAt = -1L;
            scene.press();
        }));
        helper.runAfterDelay(350, () -> scene.fixture.guarded(() -> {
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(scene.player);
            helper.assertTrue(state.eraseLengthTicks() == 400,
                    "The next erase takes the edited limit of 20 s: its length is " + state.eraseLengthTicks()
                            + " ticks, not 400");
            scene.fixture.succeed();
        }));
    }

    private static void assertHeldEraseLasts(GameTestHelper helper, int resolveLevel, int limitSeconds, int ticks) {
        Scene scene = new Scene(helper, resolveLevel, limitSeconds);
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(8, () -> scene.fixture.guarded(() -> {
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(scene.player);
            helper.assertTrue(state.eraseLengthTicks() == ticks,
                    "At Resolve " + resolveLevel + " with a limit of " + limitSeconds + " s an erase is " + ticks
                            + " ticks long; the press gave " + state.eraseLengthTicks());
            helper.assertTrue(scene.fixture.decoys(scene.player).size() == 1, "Setup: no decoy");
        }));
        helper.runAfterDelay(ticks - 10, () -> scene.fixture.guarded(() ->
                helper.assertTrue(KingCrimsonEraseFixture.erasing(scene.player)
                                && scene.fixture.decoys(scene.player).size() == 1,
                        "The held erase or its decoy ended before " + (ticks - 12) + " of its " + ticks + " ticks")));
        helper.runAfterDelay(ticks + 20, () -> scene.fixture.guarded(() -> {
            scene.assertLasted(ticks, "At Resolve " + resolveLevel + " with a limit of " + limitSeconds + " s");
            helper.assertTrue(scene.fixture.decoys(scene.player).isEmpty(), "The decoy outlived its erase");
            scene.fixture.succeed();
        }));
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final ServerPlayer player;
        private final StandPower power;
        private long startedAt = -1L;
        private long endedAt = -1L;

        private Scene(GameTestHelper helper, int resolveLevel, int limitSeconds) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            int oldLimit = KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.get();
            fixture.onClose(() -> KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(oldLimit));
            KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(limitSeconds);
            player = fixture.player("kc-length-" + resolveLevel, 1.5D, 2.0D, 1.5D);
            player.setNoGravity(true);
            power = fixture.kingCrimson(player, resolveLevel);
            helper.onEachTick(() -> {
                if (player.isRemoved()) {
                    return;
                }
                // The upkeep of a long erase is not what these tests measure.
                power.setStamina(power.getMaxStamina());
                KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(player);
                long now = helper.getLevel().getGameTime();
                if (startedAt < 0L && state.isErasing(now)) {
                    startedAt = state.startedAt();
                }
                else if (startedAt >= 0L && endedAt < 0L && state.isIdle()) {
                    endedAt = now;
                }
            });
        }

        private void press() {
            helper.assertTrue(fixture.holdErase(player).isActionSuccess, "Setup: the held Time Erase did not start");
        }

        private void assertLasted(int ticks, String rule) {
            long lasted = endedAt - startedAt;
            helper.assertTrue(startedAt >= 0L && endedAt >= 0L && Math.abs(lasted - ticks) <= 2L,
                    rule + ", a held erase lasts " + ticks + " ticks; this one lasted "
                            + (endedAt < 0L ? "longer than the test waited" : String.valueOf(lasted)));
        }
    }
}
