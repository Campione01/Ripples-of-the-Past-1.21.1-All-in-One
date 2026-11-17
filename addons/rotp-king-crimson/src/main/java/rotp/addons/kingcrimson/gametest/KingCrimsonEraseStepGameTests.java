package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.network.TemporalStateSyncPayload;
import rotp.addons.kingcrimson.network.TimeEraseShiftPayload;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseStepService;
import rotp.core.api.entity.MobAiPause;
import rotp.core.api.stand.BlinkLanding;
import rotp.core.api.time.event.TimeEraseStartEvent;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The Time Erase step (EV1105AC section 13, the owner: "利用時刪從a移動到b，過程被刪除實現的瞬移 ... 解鎖等級和冷卻與消耗與距離你參照世界的
 * 時停趕路就行"), through its key: how far it goes at each Resolve level, where it lands, what it costs and how long it
 * waits, what an answering ability leaves of it, and that it is an erase for nobody else. A player walks 0.1 * 2.1585
 * blocks in a tick, the measure the core's blink uses.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEraseStepGameTests {
    private static final String STEP = KingCrimsonTimeEraseStepService.ABILITY;
    private static final String ERASE = "kingcrimson_timeerase";
    private static final double WALKED_IN_A_TICK = 0.1D * 2.1585D;

    private KingCrimsonEraseStepGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_step_reach")
    public static void aStepReachesAsFarAsItsUserWalksInHisLongestEraseAndIsAnEraseForNobodyElse(GameTestHelper helper) {
        String title = "The Time Erase step's reach";
        Scene scene = new Scene(helper, "kc-step-reach", 72, 6);
        ServerPlayer user = scene.user;
        StandPower power = scene.power;
        ServerLevel level = helper.getLevel();
        ServerPlayer watcher = scene.fixture.player("kc-step-watcher", 6.5D, 2.0D, 4.5D);
        Pig pig = scene.place(EntityType.PIG.create(level), 4.5D, 4.5D);
        int limit = KingCrimsonConfig.TIME_ERASE_STEP_MAX_DISTANCE.get();
        scene.fixture.onClose(() -> KingCrimsonConfig.TIME_ERASE_STEP_MAX_DISTANCE.set(limit));
        helper.runAfterDelay(4, () -> scene.fixture.guarded(() -> {
            // Resolve 4: the 300 ticks of his longest erase are 64.8 blocks of walking, and the setting stops at 64.
            long day = level.getDayTime();
            int sentUser = KingCrimsonEraseFixture.listener(user).sentCount();
            int sentWatcher = KingCrimsonEraseFixture.listener(watcher).sentCount();
            float stamina = power.getStamina();
            Vec3 moved = scene.step();
            scene.rule(moved != null && near(moved, 64.0D, 0.0D, 0.0D), "at Resolve 4, looking along the floor at "
                    + "nothing, a step moved him " + moved + ", not 64 blocks ahead");
            // The trip implies 297 ticks of walking: 0.8 of an erase's 250 and of its 0.5 a tick.
            float cost = 0.8F * (250.0F + 0.5F * Mth.ceil(64.0D / WALKED_IN_A_TICK));
            scene.rule(Math.abs(stamina - power.getStamina() - cost) < 1.0F, "a step of 64 blocks took "
                    + (stamina - power.getStamina()) + " stamina, not " + cost);
            // Owner 2026-11-11 and 12: Time Erase waits as long as it ran, and the step a sixth of that for the 297
            // ticks its trip implies.
            scene.rule(power.getAbilityCooldown(STEP) == 50 && power.getAbilityCooldown(ERASE) == 50,
                    "after a step its cooldown is " + power.getAbilityCooldown(STEP) + " ticks and Time Erase's "
                            + power.getAbilityCooldown(ERASE) + ", not a sixth of the 297 that Time Erase waits after 297 ticks");
            // It is no erase for anybody: he alone sees the red flash, once, and those around hear an erase end.
            scene.rule(KingCrimsonEraseFixture.state(user).isIdle() && scene.fixture.decoys(user).isEmpty(),
                    "a step left its user in an erase (idle " + KingCrimsonEraseFixture.state(user).isIdle() + ") or left "
                            + scene.fixture.decoys(user).size() + " decoys");
            scene.rule(level.getDayTime() == day, "a step moved the day's time by " + (level.getDayTime() - day));
            scene.rule(!MobAiPause.isPaused(pig), "a step made a pig beside him lose its thread");
            scene.rule(KingCrimsonEraseShiftGameTests.transitions(user, sentUser) == 1, "its user saw the red flash "
                    + KingCrimsonEraseShiftGameTests.transitions(user, sentUser) + " times, not once");
            KingCrimsonEraseFixture.Listener watching = KingCrimsonEraseFixture.listener(watcher);
            scene.rule(KingCrimsonEraseShiftGameTests.transitions(watcher, sentWatcher) == 0
                            && watching.payloadsSince(sentWatcher, TemporalStateSyncPayload.class).isEmpty()
                            && watching.payloadsSince(sentWatcher, TimeEraseShiftPayload.class).isEmpty(),
                    "another player was shown the flash " + KingCrimsonEraseShiftGameTests.transitions(watcher, sentWatcher)
                            + " times, sent " + watching.payloadsSince(sentWatcher, TemporalStateSyncPayload.class).size()
                            + " erase states and " + watching.payloadsSince(sentWatcher, TimeEraseShiftPayload.class).size()
                            + " shifted images");
            scene.rule(KingCrimsonEraseShiftGameTests.endSounds(watcher, sentWatcher) == 1, "another player near where "
                    + "he began heard an erase end " + KingCrimsonEraseShiftGameTests.endSounds(watcher, sentWatcher)
                    + " times, not once");
            // A level below takes its share of the longest erase: 0.3 at Resolve 2, 0.6 at Resolve 3.
            for (int[] levelAndTicks : new int[][] {{2, 90}, {3, 180}}) {
                scene.again(levelAndTicks[0]);
                moved = scene.step();
                double walked = WALKED_IN_A_TICK * levelAndTicks[1];
                scene.rule(moved != null && near(moved, walked, 0.0D, 0.0D), "at Resolve " + levelAndTicks[0]
                        + " a step moved him " + moved + ", not the " + walked + " blocks of " + levelAndTicks[1] + " ticks");
            }
            KingCrimsonConfig.TIME_ERASE_STEP_MAX_DISTANCE.set(10);
            scene.again(4);
            moved = scene.step();
            scene.rule(moved != null && near(moved, 10.0D, 0.0D, 0.0D), "with timeEraseStepMaxDistance at 10 a step at "
                    + "Resolve 4 moved him " + moved);
            scene.finish(title);
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_step_landing")
    public static void aStepLandsOnTheBlockAimedAtOrBehindTheEntityAimedAtAndNeverInAWall(GameTestHelper helper) {
        String title = "Where a Time Erase step lands";
        Scene scene = new Scene(helper, "kc-step-landing", 30, 12);
        ServerPlayer user = scene.user;
        StandPower power = scene.power;
        ServerLevel level = helper.getLevel();
        // Two blocks high, twelve blocks ahead of him.
        helper.setBlock(new BlockPos(14, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(14, 3, 2), Blocks.STONE);
        // It looks along +z: behind it is toward -z.
        Pig pig = scene.place(EntityType.PIG.create(level), 10.5D, 8.5D);
        pig.setYRot(0.0F);
        Vec3 origin = user.position();
        helper.runAfterDelay(4, () -> scene.fixture.guarded(() -> {
            Vec3 moved = scene.step();
            scene.rule(moved != null && near(moved, 12.0D, 2.0D, 0.0D), "aimed at a block two high twelve blocks "
                    + "ahead, a step moved him " + moved + ", not onto it");
            scene.again(4);
            scene.aim(pig);
            moved = scene.step();
            Vec3 behind = pig.position().add(0.0D, 0.0D, -(pig.getBbWidth() + user.getBbWidth())).subtract(origin);
            scene.rule(moved != null && near(moved, behind.x, behind.y, behind.z), "aimed at a pig that looks away from "
                    + "him, a step moved him " + moved + ", not behind it at " + behind);
            scene.rule(Math.abs(Mth.wrapDegrees(user.getYRot())) < 1.0F, "landed behind the pig he looks "
                    + Mth.wrapDegrees(user.getYRot()) + " degrees off it");
            // No room behind it (a block at head height): before it, on his own line.
            helper.setBlock(new BlockPos(10, 3, 6), Blocks.STONE);
            scene.again(4);
            scene.aim(pig);
            Vec3 before = BlinkLanding.entityPos(user, pig, false).subtract(origin);
            moved = scene.step();
            scene.rule(moved != null && near(moved, before.x, before.y, before.z), "with a block where he would stand "
                    + "behind the pig, a step moved him " + moved + ", not before it at " + before);
            // No room before it either: refused, nothing spent, and he is told.
            helper.setBlock(new BlockPos(9, 3, 7), Blocks.STONE);
            scene.again(4);
            scene.aim(pig);
            int sent = KingCrimsonEraseFixture.listener(user).sentCount();
            float stamina = power.getStamina();
            moved = scene.step();
            scene.rule(moved == null && user.position().distanceTo(origin) < 0.01D
                            && Math.abs(power.getStamina() - stamina) < 0.01F && power.getAbilityCooldown(STEP) == 0,
                    "with no room behind the pig nor before it: stepped " + (moved != null) + ", he is "
                            + user.position().distanceTo(origin) + " blocks from where he stood, stamina taken "
                            + (stamina - power.getStamina()) + ", cooldown " + power.getAbilityCooldown(STEP));
            scene.rule(messages(user, sent, "rotp_kingcrimson.message.timeerase_step_blocked") == 1, "he was told "
                    + messages(user, sent, "rotp_kingcrimson.message.timeerase_step_blocked")
                    + " times that there is no room to land");
            // Not inside an erase.
            scene.again(4);
            helper.assertTrue(scene.fixture.holdErase(user).isActionSuccess, "Setup: the held Time Erase did not start");
        }));
        helper.runAfterDelay(12, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(user), "no erase is running", title);
            power.setAbilityCooldown(STEP, 0, 0);
            Vec3 moved = scene.step();
            scene.rule(moved == null && KingCrimsonEraseFixture.erasing(user), "inside his own erase a step moved him "
                    + moved + " and left the erase running: " + KingCrimsonEraseFixture.erasing(user));
            scene.fixture.release(user, KingCrimsonEraseFixture.ERASE_KEY);
            scene.finish(title);
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_step_answer")
    public static void aStepReturnedToZeroLeavesItsUserWhereHeIsHavingPaidAndBelowResolveTwoThereIsNone(
            GameTestHelper helper) {
        String title = "A Time Erase step answered";
        Scene scene = new Scene(helper, "kc-step-answer", 30, 8);
        ServerPlayer user = scene.user;
        StandPower power = scene.power;
        ServerPlayer novice = scene.fixture.player("kc-step-novice", 2.5D, 2.0D, 5.5D);
        StandPower novicePower = scene.fixture.kingCrimsonUnskilled(novice, 1);
        List<TimeEraseStartEvent> asked = new ArrayList<>();
        // What the other ability answers: nothing, steps, or full erases.
        TimeEraseStartEvent.Kind[] answered = new TimeEraseStartEvent.Kind[1];
        Consumer<TimeEraseStartEvent> other = event -> {
            if (event.getEraser() == user) {
                asked.add(event);
                if (event.getKind() == answered[0]) {
                    event.setCanceled(true);
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TimeEraseStartEvent.class, other);
        scene.fixture.onClose(() -> NeoForge.EVENT_BUS.unregister(other));
        helper.runAfterDelay(4, () -> scene.fixture.guarded(() -> {
            // Resolve 2: 90 ticks, 19.4 blocks; 0.8 of 250 and of 0.5 a tick for them.
            float cost = 0.8F * (250.0F + 0.5F * 90.0F);
            scene.again(2);
            Vec3 from = user.position();
            Vec3 moved = scene.step();
            TimeEraseStartEvent question = asked.size() == 1 ? asked.get(0) : null;
            scene.rule(moved != null && question != null && question.getKind() == TimeEraseStartEvent.Kind.STEP
                            && question.getDurationTicks() == 90 && question.getPosition().distanceTo(from) < 0.01D,
                    "a step of 90 ticks: stepped " + (moved != null) + ", the other time abilities were asked "
                            + asked.size() + " times" + (question == null ? "" : ", as " + question.getKind() + " of "
                            + question.getDurationTicks() + " ticks, " + question.getPosition().distanceTo(from)
                            + " blocks from where he stood"));
            // Returned to zero: the press went off, he stays, has paid, and the cooldown runs.
            scene.again(2);
            answered[0] = TimeEraseStartEvent.Kind.STEP;
            int sent = KingCrimsonEraseFixture.listener(user).sentCount();
            float stamina = power.getStamina();
            boolean went = scene.stepByKey(user);
            scene.rule(went && user.position().distanceTo(from) < 0.01D, "a step returned to zero: the press went off "
                    + went + ", and he is " + user.position().distanceTo(from) + " blocks from where he stood");
            scene.rule(Math.abs(stamina - power.getStamina() - cost) < 1.0F && power.getAbilityCooldown(STEP) == 20,
                    "a step returned to zero took " + (stamina - power.getStamina()) + " stamina, not " + cost
                            + ", and its cooldown is " + power.getAbilityCooldown(STEP) + " ticks, not a sixth of the 120"
                            + " that Time Erase waits at the least, after 90 ticks too");
            scene.rule(KingCrimsonEraseShiftGameTests.transitions(user, sent) == 1, "returned to zero, he saw the red "
                    + "flash " + KingCrimsonEraseShiftGameTests.transitions(user, sent) + " times, not once");
            // An ability that answers full erases alone does not answer a step.
            scene.again(2);
            answered[0] = TimeEraseStartEvent.Kind.ERASE;
            moved = scene.step();
            scene.rule(moved != null, "an ability that answers only full erases returned a step to zero");
            answered[0] = null;
            // One Resolve level under Time Erase, and none below: the skill learnt, Resolve 1 has no step.
            novicePower.getCurTypeData()._setSkillUnlocked(STEP, true, false);
            Vec3 noviceFrom = novice.position();
            boolean early = scene.stepByKey(novice);
            scene.rule(!early && novice.position().distanceTo(noviceFrom) < 0.01D, "at Resolve 1 a step went off: "
                    + early + ", moved " + novice.position().distanceTo(noviceFrom) + " blocks");
            novicePower.setResolveLevel(2);
            boolean learnt = scene.stepByKey(novice);
            scene.rule(learnt && Math.abs(novice.getX() - noviceFrom.x - WALKED_IN_A_TICK * 90.0D) < 0.01D,
                    "at Resolve 2 a step went off: " + learnt + ", moved " + (novice.getX() - noviceFrom.x) + " blocks");
            scene.finish(title);
        }));
    }

    private static boolean near(Vec3 moved, double x, double y, double z) {
        return Math.abs(moved.x - x) < 0.01D && Math.abs(moved.y - y) < 0.01D && Math.abs(moved.z - z) < 0.01D;
    }

    private static int messages(ServerPlayer player, int index, String key) {
        int messages = 0;
        for (Packet<?> packet : KingCrimsonEraseFixture.listener(player).sentSince(index)) {
            if (packet instanceof ClientboundSystemChatPacket bar && bar.overlay()
                    && bar.content().getContents() instanceof TranslatableContents text && text.getKey().equals(key)) {
                messages++;
            }
        }
        return messages;
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final ServerPlayer user;
        private final StandPower power;
        private final Vec3 origin;
        private final List<String> broken = new ArrayList<>();
        private boolean finished;

        private Scene(GameTestHelper helper, String name, int length, int width) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(length, width);
            user = fixture.player(name, 2.5D, 2.0D, 2.5D);
            power = fixture.kingCrimson(user, 4);
            origin = user.position();
        }

        /** Something that stays where it is put. */
        private <T extends Entity> T place(T entity, double x, double z) {
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }
            entity.setPos(helper.absoluteVec(new Vec3(x, 2.0D, z)));
            helper.getLevel().addFreshEntity(entity);
            return fixture.track(entity);
        }

        /** Back where he began, looking along the floor, rested, at this Resolve level. */
        private void again(int resolveLevel) {
            user.moveTo(origin.x, origin.y, origin.z, -90.0F, 0.0F);
            user.setYHeadRot(-90.0F);
            power.setResolveLevel(resolveLevel);
            power.setAbilityCooldown(STEP, 0, 0);
            power.setAbilityCooldown(ERASE, 0, 0);
            power.setStamina(power.getMaxStamina());
        }

        private void aim(Entity target) {
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
        }

        private boolean stepByKey(ServerPlayer player) {
            return fixture.press(player, STEP, KingCrimsonEraseFixture.OTHER_KEY, InputMethod.CLICK, false).isActionSuccess;
        }

        /** The step's key; how far it moved him, or null when the press did not go off. */
        private Vec3 step() {
            Vec3 from = user.position();
            return stepByKey(user) ? user.position().subtract(from) : null;
        }

        private void rule(boolean holds, String broke) {
            if (!holds && !finished) {
                broken.add(broke);
            }
        }

        private void premise(boolean holds, String what, String title) {
            if (!holds && !finished) {
                broken.add("Setup: " + what);
                finish(title);
            }
        }

        private void finish(String title) {
            if (finished) {
                return;
            }
            finished = true;
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(), title + ": " + String.join("; ", broken)));
            fixture.succeed();
        }
    }
}
