package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState.EraseKind;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseShiftService;
import rotp.core.api.entity.MobAiPause;
import rotp.core.api.time.event.TimeEraseRunningEvent;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * What the end of a Time Erase leaves behind, beside the shifted players: the creatures' amnesia (EV1105AC section 10,
 * "停止時間隨時刪時間變化，最大為1.5s") and the end forced on the eraser by another time ability (section 14, "直接被強制退出時刪").
 * A test's callbacks and the fixture's tick of its players run in no fixed order within a server tick: what is read
 * here is either done inside the callback itself or left a tick to settle.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEraseEndGameTests {
    private KingCrimsonEraseEndGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 560, batch = "kc_end_amnesia")
    public static void theCreaturesAroundStopThinkingForAShareOfTheSettingThatFollowsTheErasedTime(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-end-amnesia");
        ServerPlayer eraser = scene.eraser;
        ServerLevel level = helper.getLevel();
        int limit = KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.get();
        String exempt = KingCrimsonConfig.TIME_ERASE_AMNESIA_EXEMPT.get();
        // The longest erase is 200 ticks here, and the setting's 1.5 s are 30 ticks.
        KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(10);
        KingCrimsonConfig.TIME_ERASE_AMNESIA_EXEMPT.set("minecraft:cow");
        scene.fixture.onClose(() -> {
            KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(limit);
            KingCrimsonConfig.TIME_ERASE_AMNESIA_EXEMPT.set(exempt);
        });
        Pig pig = scene.mob(EntityType.PIG.create(level), 6.5D, 4.5D);
        Cow cow = scene.mob(EntityType.COW.create(level), 8.5D, 4.5D);
        // The creatures' own ticks that find them paused: the pause counts in those.
        int[] pigStood = {0};
        int[] cowStood = {0};
        Consumer<EntityTickEvent.Pre> count = event -> {
            if (event.getEntity() == pig && MobAiPause.remainingTicks(pig) > 0) {
                pigStood[0]++;
            } else if (event.getEntity() == cow && MobAiPause.remainingTicks(cow) > 0) {
                cowStood[0]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTickEvent.Pre.class, count);
        scene.fixture.onClose(() -> NeoForge.EVENT_BUS.unregister(count));
        int[] phase = {0};
        long[] wait = {0L};
        helper.onEachTick(() -> scene.fixture.guarded(() -> {
            if (phase[0] > 8) {
                return;
            }
            boolean idle = KingCrimsonEraseFixture.state(eraser).isIdle();
            int elapsed = idle ? 0 : KingCrimsonEraseFixture.state(eraser).elapsedWorldTicks(level.getGameTime());
            switch (phase[0]) {
                case 0 -> {
                    if (helper.getTick() >= 2) {
                        scene.press();
                        phase[0] = 1;
                    }
                }
                case 1 -> {
                    if (!idle) {
                        phase[0] = 2;
                    }
                }
                case 2 -> {
                    // The held erase runs out by itself.
                    if (idle) {
                        scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
                        wait[0] = helper.getTick() + 45;
                        phase[0] = 3;
                    }
                }
                case 3 -> {
                    if (helper.getTick() >= wait[0]) {
                        scene.rule(pigStood[0] == 30, "after a full erase of 200 ticks a pig stood for " + pigStood[0]
                                + " of its ticks, not 30");
                        scene.rule(cowStood[0] == 0, "a cow, on timeEraseAmnesiaExempt, stood for " + cowStood[0] + " ticks");
                        scene.pressAgain();
                        phase[0] = 4;
                    }
                }
                case 4 -> {
                    if (!idle && elapsed >= 100) {
                        scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
                        scene.rule(MobAiPause.remainingTicks(pig) == 15, "after an erase let go at " + elapsed
                                + " of 200 ticks a pig stops thinking for " + MobAiPause.remainingTicks(pig)
                                + " ticks, not 15");
                        // An erase that ends while they still stand starts the pause again: it does not add up.
                        scene.premise(KingCrimsonTemporalStateService.beginTimeErase(eraser, EraseKind.REFLEX),
                                "the next erase did not begin", "The amnesia");
                        phase[0] = 5;
                    }
                }
                case 5 -> {
                    if (!idle && elapsed >= 10) {
                        scene.premise(MobAiPause.remainingTicks(pig) > 2, "the pig's pause of 15 ticks is over",
                                "The amnesia");
                        KingCrimsonTemporalStateService.terminate(eraser);
                        scene.rule(MobAiPause.remainingTicks(pig) == 2, "an erase of " + elapsed + " ticks that ended while "
                                + "the pig still stood left it " + MobAiPause.remainingTicks(pig) + " ticks, not its own 2");
                        wait[0] = helper.getTick() + 10;
                        phase[0] = 6;
                    }
                }
                case 6 -> {
                    if (helper.getTick() >= wait[0]) {
                        scene.premise(!MobAiPause.isPaused(pig), "the pig still stands, with "
                                + MobAiPause.remainingTicks(pig) + " ticks left", "The amnesia");
                        pigStood[0] = 0;
                        // The passive's erase of 2 s, begun as the passive will begin it; it runs out by itself.
                        scene.premise(KingCrimsonTemporalStateService.beginTimeErase(eraser, EraseKind.REFLEX),
                                "the reflex erase did not begin", "The amnesia");
                        phase[0] = 7;
                    }
                }
                case 7 -> {
                    if (idle) {
                        wait[0] = helper.getTick() + 15;
                        phase[0] = 8;
                    }
                }
                default -> {
                    if (helper.getTick() >= wait[0]) {
                        scene.rule(pigStood[0] == 6, "after a reflex erase of 40 ticks a pig stood for " + pigStood[0]
                                + " of its ticks, not 6");
                        phase[0] = 9;
                        scene.finish("The amnesia");
                    }
                }
            }
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_end_thrown_out")
    public static void anotherTimeAbilityThrowsTheEraserOutWhereHeStandsAndNothingOfHisOwnComesOfIt(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-end-thrown");
        ServerPlayer eraser = scene.eraser;
        ServerLevel level = helper.getLevel();
        ServerPlayer walker = scene.fixture.player("kc-end-walker", 6.5D, 2.0D, 8.5D);
        Pig pig = scene.mob(EntityType.PIG.create(level), 6.5D, 4.5D);
        Pig punched = scene.mob(EntityType.PIG.create(level), 4.5D, 1.5D);
        // 0: lets the erase run; 1: cancels the event; 2: answers it, as Gold Experience Requiem does.
        int[] mode = {0};
        int[] posts = {0};
        int[] wrongElapsed = {0};
        // When it threw the eraser out: the erase's ticks by then, the day's time and the game time.
        long[] thrown = new long[3];
        Consumer<TimeEraseRunningEvent> other = event -> {
            if (event.getEraser() != eraser) {
                return;
            }
            posts[0]++;
            if (event.getTicksElapsed() != KingCrimsonEraseFixture.state(eraser).elapsedWorldTicks(level.getGameTime())) {
                wrongElapsed[0]++;
            }
            if (mode[0] != 0) {
                thrown[0] = event.getTicksElapsed();
                thrown[1] = level.getDayTime();
                thrown[2] = level.getGameTime();
                if (mode[0] == 1) {
                    event.setCanceled(true);
                } else {
                    event.answer(KingCrimsonAddon.id("gametest_answer"), null);
                }
                mode[0] = 0;
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TimeEraseRunningEvent.class, other);
        scene.fixture.onClose(() -> NeoForge.EVENT_BUS.unregister(other));
        Vec3[] image = new Vec3[1];
        int[] sent = new int[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        for (int tick = 0; tick < 20; tick++) {
            helper.runAfterDelay(12 + tick, () -> walker.setPos(walker.getX() + 0.2D, walker.getY(), walker.getZ()));
        }
        helper.runAfterDelay(40, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            scene.rule(posts[0] >= 25 && wrongElapsed[0] == 0, "the running erase asked the other time abilities "
                    + posts[0] + " times in about 35 ticks, " + wrongElapsed[0] + " times with other than its ticks so far");
            image[0] = KingCrimsonTimeEraseShiftService.imageOf(eraser, walker);
            helper.assertTrue(image[0].distanceTo(walker.position()) > 1.0D, "Setup: the walker is not seen shifted");
            sent[0] = KingCrimsonEraseFixture.listener(eraser).sentCount();
            mode[0] = 2;
        }));
        helper.runAfterDelay(43, () -> scene.fixture.guarded(() -> {
            scene.rule(KingCrimsonEraseFixture.state(eraser).isIdle(), "an answered erase goes on");
            // The rest of the end runs as at any end.
            scene.rule(KingCrimsonEraseShiftGameTests.near(walker.position(), image[0]),
                    "thrown out, the eraser left the other player off his image");
            long jumped = level.getDayTime() - thrown[1] - (level.getGameTime() - thrown[2]);
            scene.rule(thrown[0] > 30L && jumped == thrown[0], "thrown out after " + thrown[0]
                    + " ticks, the day's time jumped by " + jumped);
            scene.rule(MobAiPause.isPaused(pig), "thrown out, the erase left the creatures their thread");
            scene.rule(scene.power.getAbilityCooldown("kingcrimson_timeerase") > 0, "thrown out, the erase has no cooldown");
            // Whoever answered shows its own effect in place of the erase's.
            scene.rule(KingCrimsonEraseShiftGameTests.endSounds(eraser, sent[0]) == 0
                            && KingCrimsonEraseShiftGameTests.transitions(eraser, sent[0]) == 0
                            && startSoundStops(eraser, sent[0]) == 1,
                    "answered by another ability, the erase's own end was heard "
                            + KingCrimsonEraseShiftGameTests.endSounds(eraser, sent[0]) + " times and flashed "
                            + KingCrimsonEraseShiftGameTests.transitions(eraser, sent[0]) + " times, and its start sound "
                            + "was stopped " + startSoundStops(eraser, sent[0]) + " times");
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
        }));
        helper.runAfterDelay(48, () -> scene.fixture.guarded(scene::pressAgain));
        helper.runAfterDelay(70, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            sent[0] = KingCrimsonEraseFixture.listener(eraser).sentCount();
            mode[0] = 1;
        }));
        helper.runAfterDelay(73, () -> scene.fixture.guarded(() -> {
            scene.rule(KingCrimsonEraseFixture.state(eraser).isIdle(), "an erase whose running event was cancelled goes on");
            scene.rule(KingCrimsonEraseShiftGameTests.endSounds(eraser, sent[0]) == 1
                            && KingCrimsonEraseShiftGameTests.transitions(eraser, sent[0]) == 1,
                    "thrown out with nobody answering, the erase's own end was heard "
                            + KingCrimsonEraseShiftGameTests.endSounds(eraser, sent[0]) + " times and flashed "
                            + KingCrimsonEraseShiftGameTests.transitions(eraser, sent[0]) + " times");
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
        }));
        // A blow begun inside the erase is called off with it.
        helper.runAfterDelay(78, () -> scene.fixture.guarded(scene::pressAgain));
        helper.runAfterDelay(100, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            StandEntity stand = scene.power.getSummonedStandEntity();
            LivingComponentAction.getComponent(eraser).entityAim.setTarget(new ActionTarget(punched));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(punched));
            eraser.lookAt(EntityAnchorArgument.Anchor.EYES, punched.getBoundingBox().getCenter());
            helper.assertTrue(scene.fixture.press(eraser, "punch", KingCrimsonEraseFixture.OTHER_KEY, InputMethod.CLICK, false)
                    .isActionSuccess, "Setup: the punch was not taken in the erase");
            posts[0] = 0;
            mode[0] = 2;
        }));
        helper.runAfterDelay(160, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.state(eraser).isIdle() && posts[0] == 1,
                    "the erase was not thrown out at its first tick after the punch was pressed (asked " + posts[0]
                            + " times)", "Thrown out of an erase");
            scene.rule(punched.getHealth() == punched.getMaxHealth() && punched.isAlive(),
                    "the punch begun inside an erase he was thrown out of landed: the pig has " + punched.getHealth()
                            + " health");
            scene.finish("Thrown out of an erase");
        }));
    }

    private static int startSoundStops(ServerPlayer player, int index) {
        int stops = 0;
        for (Packet<?> packet : KingCrimsonEraseFixture.listener(player).sentSince(index)) {
            if (packet instanceof ClientboundStopSoundPacket stop
                    && KingCrimsonAddon.id("time_erase_start").equals(stop.getName())) {
                stops++;
            }
        }
        return stops;
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final ServerPlayer eraser;
        private final StandPower power;
        private final List<String> broken = new ArrayList<>();

        private Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(20, 12);
            eraser = fixture.player(name, 2.5D, 2.0D, 1.5D);
            power = fixture.kingCrimson(eraser, 4);
            helper.onEachTick(() -> {
                if (!eraser.isRemoved()) {
                    power.setStamina(power.getMaxStamina());
                }
            });
        }

        /** A creature that stays where it is put; a pause counts on it all the same. */
        private <T extends Mob> T mob(T mob, double x, double z) {
            Vec3 at = helper.absoluteVec(new Vec3(x, 2.0D, z));
            mob.setNoAi(true);
            mob.setPos(at);
            helper.getLevel().addFreshEntity(mob);
            return fixture.track(mob);
        }

        private void press() {
            helper.assertTrue(fixture.holdErase(eraser).isActionSuccess, "Setup: the held Time Erase did not start");
        }

        private void pressAgain() {
            power.setAbilityCooldown("kingcrimson_timeerase", 0, 0);
            power.setStamina(power.getMaxStamina());
            press();
        }

        private void assertErasing() {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(eraser), "Setup: no erase is running");
        }

        private void rule(boolean holds, String broke) {
            if (!holds) {
                broken.add(broke);
            }
        }

        /** A step the rest of the test stands on: where it fails, the test ends with the rules broken before it. */
        private void premise(boolean holds, String what, String title) {
            if (!holds) {
                broken.add("Setup: " + what);
                finish(title);
            }
        }

        private void finish(String what) {
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(), what + ": " + String.join("; ", broken)));
            fixture.succeed();
        }
    }
}
