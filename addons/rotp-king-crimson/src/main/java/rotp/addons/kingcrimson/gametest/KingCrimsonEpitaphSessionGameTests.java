package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphMode;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.EpitaphTargeting;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.impl.stands.crazydiamond.brokenblocks.BrokenBlocksChunkData;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * Epitaph, the prediction as a whole (EPITAPH-ARCH sections 0, 1.4, 5 and 6): who is foreseen, how far and how fast,
 * what holds a prediction and what ends it, and that it costs nothing while none runs.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphSessionGameTests {
    private KingCrimsonEpitaphSessionGameTests() {}

    // A16
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_targets")
    public static void onlyHuntersAndTheCrosshairEntityArePredicted(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-targets", 4, 32, 10);
        ServerLevel level = fixture.level;
        ServerPlayer other = fixture.other("kc-ep-other", 29.5D, 2.5D);
        Zombie hunter = fixture.hunter(EntityType.ZOMBIE, 9.5D, 2.5D);
        Skeleton archer = fixture.hunter(EntityType.SKELETON, 8.5D, 7.5D);
        archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        // Nearer to the other player than to the user: it stays after him.
        Zombie afterTheOther = fixture.mob(EntityType.ZOMBIE, 23.5D, 2.5D);
        afterTheOther.setTarget(other);
        Cow bystander = fixture.mob(EntityType.COW, 5.5D, 7.5D);
        Cow aimedAt = fixture.mob(EntityType.COW, 5.5D, 2.5D);
        helper.runAfterDelay(3, () -> fixture.guarded(() -> {
            helper.assertTrue(hunter.getTarget() == fixture.user && archer.getTarget() == fixture.user
                    && afterTheOther.getTarget() == other, "Setup: the creatures are not after whom the test set them on");
            Vec3 high = fixture.spot(7.5D, 5.0D, 6.5D);
            Arrow inFlight = new Arrow(level, archer, new ItemStack(Items.ARROW), null);
            inFlight.setPos(high);
            inFlight.shoot(-1.0D, 0.0D, -0.5D, 0.6F, 0.0F);
            fixture.thing(inFlight);
            Arrow ofNobody = new Arrow(level, high.x + 4.0D, high.y, high.z, new ItemStack(Items.ARROW), null);
            ofNobody.shoot(-1.0D, 0.0D, 0.0D, 0.6F, 0.0F);
            fixture.thing(ofNobody);
            Entity seen = EpitaphTargeting.crosshair(fixture.user, KingCrimsonConfig.epitaphRadius());
            fixture.rule(seen == aimedAt, "the user looks straight at a cow three blocks away, and under his crosshair is "
                    + (seen == null ? "nothing" : seen.getType().toShortString()));
            EpitaphSession session = fixture.press(aimedAt);
            Object[][] expected = {
                    { hunter, true, "the zombie that hunts the user" }, { archer, true, "the skeleton that hunts the user" },
                    { aimedAt, true, "the cow under the crosshair" }, { inFlight, true, "the arrow of the skeleton, in flight" },
                    { bystander, false, "a cow nearby" }, { afterTheOther, false, "a zombie that hunts another player" },
                    { ofNobody, false, "an arrow that nobody of the prediction shot" }, { other, false, "the other player" } };
            for (Object[] row : expected) {
                Entity entity = (Entity) row[0];
                boolean predicted = session.record().track(entity.getId()) != null;
                fixture.rule(predicted == (boolean) row[1] && EpitaphPredictions.isOnTrack(entity) == (boolean) row[1],
                        row[2] + " is " + (predicted ? "" : "not ") + "predicted, and on a track: " + EpitaphPredictions.isOnTrack(entity));
            }
            fixture.rule(session.mode() == EpitaphMode.MULTI_BOUND, "with two players online the prediction runs as " + session.mode());
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("Only the hunters and the crosshair entity are predicted");
        }));
    }

    // A17
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_table")
    public static void resolveTableGivesForesightAndPhase(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-table", 0, 8, 6);
        int seconds = KingCrimsonConfig.EPITAPH_MAX_FORESIGHT_SECONDS.get();
        fixture.base.onClose(() -> KingCrimsonConfig.EPITAPH_MAX_FORESIGHT_SECONDS.set(seconds));
        // The owner's table: 10 %, 10 %, 30 %, 60 %, 100 % of the longest foresight; phantoms for 0.5, 0.5, 1, 2, 3 s.
        int[] ofFive = { 10, 10, 30, 60, 100 };
        int[] ofTen = { 20, 20, 60, 120, 200 };
        int[] phase = { 10, 10, 20, 40, 60 };
        int[] quarterPhase = { 3, 3, 5, 10, 15 };
        helper.runAfterDelay(3, () -> fixture.guarded(() -> {
            fixture.rule(seconds == 5 && KingCrimsonEpitaphForesightSpec.MAX_FORESIGHT_SECONDS == 5,
                    "the longest foresight is " + seconds + " s by default, not 5");
            for (int resolve = 0; resolve <= 4; resolve++) {
                fixture.power.setResolveLevel(resolve);
                KingCrimsonConfig.EPITAPH_MAX_FORESIGHT_SECONDS.set(5);
                EpitaphSession session = fixture.press(null);
                fixture.rule(session.clock().foresight() == ofFive[resolve] && session.clock().phase() == phase[resolve]
                        && session.mode() == EpitaphMode.SOLO_SLOW, "at Resolve " + resolve + " a lone user foresees "
                                + session.clock().foresight() + " ticks with phantoms for " + session.clock().phase() + ", not "
                                + ofFive[resolve] + " and " + phase[resolve]);
                session.end(EpitaphEnd.CANCELLED);
                KingCrimsonConfig.EPITAPH_MAX_FORESIGHT_SECONDS.set(10);
                session = fixture.press(null);
                fixture.rule(session.clock().foresight() == ofTen[resolve] && session.clock().phase() == phase[resolve],
                        "with the longest foresight set to 10 s, Resolve " + resolve + " foresees " + session.clock().foresight()
                                + " ticks with phantoms for " + session.clock().phase() + ", not " + ofTen[resolve] + " and " + phase[resolve]);
                session.end(EpitaphEnd.CANCELLED);
                fixture.rule(KingCrimsonConfig.epitaphMultiplayerPhaseTicks(resolve) == quarterPhase[resolve],
                        "with several players online the phantoms of Resolve " + resolve + " run for "
                                + KingCrimsonConfig.epitaphMultiplayerPhaseTicks(resolve) + " ticks, not " + quarterPhase[resolve]);
            }
            fixture.finish("The table of Resolve gives the foresight and the phantom phase");
        }));
    }

    // A18
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_clocks")
    public static void clocksPerMode(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-clocks", 2, 20, 6);
        fixture.budget(0);
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 14.5D, 2.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] scene = new int[1];
        int[] checked = new int[2];
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            running[0] = fixture.press(null);
            helper.assertTrue(running[0].mode() == EpitaphMode.SOLO_SLOW && EpitaphSessions.isOnTrack(zombie),
                    "Setup: a lone user predicts as " + running[0].mode() + ", or the zombie is on no track");
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            int elapsed = session.clock().elapsed();
            double bodies = session.clock().body();
            float rate = EntityTimeRates.rate(EntityTimeRates.Channel.BEING, zombie);
            if (scene[0] == 0) {
                // Alone: 20 ticks of phantoms at a quarter, then the other 25 ticks of the 30 at full speed.
                double expected = Math.min(30.0D, Math.min(elapsed, 20) * 0.25D + Math.max(0, elapsed - 20));
                fixture.rule(Math.abs(bodies - expected) < 1.0E-9D, "alone, " + elapsed + " server ticks after the press the bodies are at tick "
                        + bodies + " of the future, not " + expected);
                if (!session.ended()) {
                    fixture.rule(elapsed < 20 ? rate == 0.25F : !(rate < 1.0F), "alone, " + elapsed
                            + " server ticks after the press a body on its track is declared to live at the rate " + rate);
                    checked[0]++;
                    return;
                }
                fixture.rule(elapsed == 45 && checked[0] >= 40, "alone, a foresight of 30 ticks lasted " + elapsed + " server ticks, not 45");
                scene[0] = 1;
                fixture.other("kc-ep-second", 18.5D, 4.5D);
                running[0] = fixture.press(null);
                helper.assertTrue(running[0].mode() == EpitaphMode.MULTI_BOUND && EpitaphSessions.isOnTrack(zombie),
                        "Setup: with a second player online the user predicts as " + running[0].mode());
                return;
            }
            fixture.rule(bodies == Math.min(30, elapsed), "with two players online, " + elapsed
                    + " server ticks after the press the bodies are at tick " + bodies);
            fixture.rule(session.clock().shown() == Math.min(30.0D, elapsed * 6.0D) || session.clock().shown() == session.room().tick(),
                    "with two players online the phantoms are shown at tick " + session.clock().shown() + " after " + elapsed
                            + " server ticks: they run the 30 ticks in 5");
            if (!session.ended()) {
                fixture.rule(!(rate < 1.0F), "with two players online a body on its track is declared to live at the rate " + rate);
                checked[1]++;
                return;
            }
            running[0] = null;
            fixture.rule(elapsed == 30 && checked[1] >= 25, "with two players online a foresight of 30 ticks lasted " + elapsed + " server ticks");
            fixture.finish("The clocks of each mode");
        }));
    }

    // A19
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_pause")
    public static void timeStopPausesTheSession(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-pause", 2, 16, 6);
        fixture.budget(0);
        ServerLevel level = fixture.level;
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        Runnable[] resume = new Runnable[1];
        EpitaphSession[] running = new EpitaphSession[1];
        int[] ticks = new int[1];
        double[] held = new double[4];
        Vec3[] stoodAt = new Vec3[1];
        helper.runAfterDelay(45, () -> fixture.guarded(() -> running[0] = fixture.press(null)));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            ticks[0]++;
            if (ticks[0] == 8) {
                resume[0] = fixture.stopTime(new ChunkPos(fixture.user.blockPosition()));
                helper.assertTrue(TimeStopState.shouldFreezeOnServer(fixture.user) || TimeStopState.shouldFreezeOnServer(zombie),
                        "Setup: the stopped time holds neither the user nor the zombie");
                held[0] = session.room().tick();
                held[1] = session.clock().shown();
                held[2] = session.clock().body();
                held[3] = session.clock().elapsed();
                stoodAt[0] = zombie.position();
            } else if (ticks[0] > 8 && ticks[0] <= 20) {
                fixture.rule(session.room().tick() == held[0] && session.clock().shown() == held[1] && session.clock().body() == held[2]
                        && session.clock().elapsed() == held[3], "in stopped time the room went from tick " + held[0] + " to "
                                + session.room().tick() + ", the shown tick from " + held[1] + " to " + session.clock().shown()
                                + ", the bodies from " + held[2] + " to " + session.clock().body());
                fixture.rule(ticks[0] == 9 || session.paused(), "in stopped time the prediction does not say it is held");
                fixture.rule(zombie.position().distanceTo(stoodAt[0]) < 1.0E-6D && EpitaphSessions.isOnTrack(zombie),
                        "in stopped time the zombie on its track moved, or left its track");
                if (ticks[0] == 20) {
                    resume[0].run();
                }
            } else if (ticks[0] == 24) {
                fixture.rule(session.clock().body() > held[2] && session.room().tick() > held[0] && !session.paused(),
                        "after time resumed the prediction did not go on: the bodies are at tick " + session.clock().body()
                                + ", where they were held at " + held[2]);
            }
            if (!session.ended()) {
                return;
            }
            running[0] = null;
            // 45 server ticks of its own, and the 12 it was held for.
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && ticks[0] >= 56 && ticks[0] <= 59,
                    "the prediction ended as " + session.endedAs() + " " + ticks[0] + " server ticks after the press");
            fixture.finish("Stopped time holds a prediction");
        }));
    }

    // A20
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_idle")
    public static void nothingRunsWithoutASession(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-idle", 0, 24, 12);
        ServerLevel level = fixture.level;
        // A fight with everything the hooks sit on: creatures that hunt and hit, a rider, shots, a blast, a death with
        // its loot, a block that falls and lands. The user is hit and takes nothing, so that he lives through it.
        fixture.user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 4));
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 8.5D, 2.5D);
        Skeleton skeleton = fixture.hunter(EntityType.SKELETON, 12.5D, 8.5D);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Chicken mount = fixture.mob(EntityType.CHICKEN, 10.5D, 5.5D);
        Zombie rider = fixture.hunter(EntityType.ZOMBIE, 10.5D, 5.5D);
        rider.setBaby(true);
        Zombie weak = fixture.mob(EntityType.ZOMBIE, 19.5D, 5.5D);
        weak.setNoAi(true);
        weak.setHealth(1.0F);
        BlockPos from = fixture.absolute(-1, -3, -1);
        BlockPos to = fixture.absolute(24, 7, 12);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        long[] passes = new long[1];
        int[] rode = new int[1];
        helper.runAfterDelay(2, () -> fixture.guarded(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                before.put(pos.immutable(), level.getBlockState(pos));
            }
            helper.assertTrue(rider.startRiding(mount, true), "Setup: the zombie did not mount the hen");
            Vec3 fuse = fixture.spot(18.5D, 2.0D, 4.5D);
            PrimedTnt tnt = fixture.thing(new PrimedTnt(level, fuse.x, fuse.y, fuse.z, null));
            tnt.setFuse(40);
            fixture.base.track(FallingBlockEntity.fall(level, fixture.absolute(21, 6, 10), Blocks.ANVIL.defaultBlockState()));
            helper.assertTrue(!EpitaphSessions.any && EpitaphSandbox.depth == 0, "Setup: a prediction runs");
            EpitaphSandbox.resetPasses();
            passes[0] = EpitaphSandbox.passes();
        }));
        helper.onEachTick(() -> {
            if (rider.isPassenger()) {
                rode[0]++;
            }
        });
        helper.runAfterDelay(102, () -> fixture.guarded(() -> {
            long passed = EpitaphSandbox.passes() - passes[0];
            fixture.rule(!weak.isAlive() && level.getBlockState(fixture.absolute(21, 2, 10)).is(Blocks.ANVIL) && rode[0] >= 20
                    && zombie.isAlive() && skeleton.isAlive(), "Setup: the fight did not happen: the zombie beside the TNT lives ("
                            + weak.isAlive() + "), no anvil landed, or the rider rode for " + rode[0] + " ticks only");
            fixture.rule(passed == 0L && !EpitaphSessions.any && EpitaphSandbox.depth == 0,
                    "over a hundred ticks of a fight with no prediction, " + passed + " hooks went past their flag");
            // The same count sees a prediction: one that runs is counted at once.
            EpitaphSession session = fixture.press(null);
            fixture.rule(EpitaphSandbox.passes() - passes[0] > 0L, "Setup: the count of hook passes does not move while a prediction runs");
            session.end(EpitaphEnd.CANCELLED);
            for (BlockPos pos : before.keySet()) {
                if (level.getBlockState(pos) != before.get(pos)) {
                    level.setBlock(pos, before.get(pos), 3);
                }
            }
            fixture.finish("Nothing of Epitaph runs while no prediction does");
        }));
    }

    // A21
    @GameTest(template = "empty", timeoutTicks = 500, batch = "kc_epitaph_ends")
    public static void endLeavesNothingHeld(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-ends", 0, 16, 8);
        fixture.budget(0);
        ServerLevel level = fixture.level;
        fixture.user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 4));
        Chicken mount = fixture.mob(EntityType.CHICKEN, 10.5D, 4.5D);
        Zombie rider = fixture.hunter(EntityType.ZOMBIE, 10.5D, 4.5D);
        rider.setBaby(true);
        Zombie walker = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        // The ticks they did by themselves: the age of a rider goes on while its tick is cancelled.
        int[] ticked = new int[2];
        fixture.listen(EntityTickEvent.Post.class, event -> {
            if (event.getEntity() == walker) {
                ticked[0]++;
            } else if (event.getEntity() == rider) {
                ticked[1]++;
            }
        });
        EpitaphEnd[] reasons = { EpitaphEnd.CANCELLED, EpitaphEnd.COMPLETE, EpitaphEnd.FAULT, EpitaphEnd.SERVER_STOPPING,
                EpitaphEnd.USER_LOGGED_OUT, EpitaphEnd.USER_LEFT_LEVEL, EpitaphEnd.USER_DIED };
        ServerPlayer[] presser = { fixture.user };
        EpitaphSession[] running = new EpitaphSession[1];
        int[] step = new int[1];
        int[] at = new int[1];
        int[] endedAt = new int[1];
        int[] marks = new int[2];
        helper.runAfterDelay(3, () -> fixture.guarded(
                () -> helper.assertTrue(rider.startRiding(mount, true), "Setup: the zombie did not mount the hen")));
        helper.onEachTick(() -> fixture.guarded(() -> {
            if (step[0] >= reasons.length || helper.getTick() < 6) {
                return;
            }
            EpitaphEnd reason = reasons[step[0]];
            at[0]++;
            if (at[0] == 1) {
                if (reason == EpitaphEnd.USER_DIED) {
                    presser[0] = fixture.other("kc-ep-ends-2", 4.5D, 5.5D);
                    fixture.base.kingCrimson(presser[0], 0);
                }
                running[0] = EpitaphSessions.predict(presser[0], List.of(mount, rider, walker));
                helper.assertTrue(running[0] != null && EpitaphSessions.isOnTrack(mount) && EpitaphSessions.isOnTrack(rider)
                        && EpitaphSessions.isOnTrack(walker), "Setup: before the end as " + reason + " the three are not on their tracks");
                marks[0] = ticked[0];
                marks[1] = ticked[1];
                endedAt[0] = 0;
                return;
            }
            EpitaphSession session = running[0];
            if (at[0] == 4) {
                fixture.rule(ticked[0] == marks[0] && ticked[1] == marks[1] && session.mode() == EpitaphMode.SOLO_SLOW
                        && EntityTimeRates.rate(EntityTimeRates.Channel.BEING, walker) == 0.25F,
                        "Setup: before the end as " + reason + " the bodies on their tracks ticked by themselves, or no rate is answered");
                if (step[0] == 0) {
                    // The world is saved while the prediction runs: no phantom is among what the level holds.
                    level.save(null, false, false);
                    for (SandboxRoom.Phantom phantom : session.room().all()) {
                        fixture.rule(level.getEntity(phantom.entity.getUUID()) == null && level.getEntity(phantom.entity.getId()) != phantom.entity,
                                "the level holds the phantom of a " + phantom.entity.getType().toShortString() + " when it is saved");
                    }
                }
                switch (reason) {
                    case CANCELLED -> session.end(EpitaphEnd.CANCELLED);
                    // A track with no frame: carrying a body along it throws.
                    case FAULT -> session.bind(walker, new EntityTrack(ActorKey.of(walker), ActorKey.of(walker), 0, false));
                    case SERVER_STOPPING -> EpitaphSessions.onServerStopping(new ServerStoppingEvent(level.getServer()));
                    case USER_LOGGED_OUT -> EpitaphSessions.onLogout(new PlayerEvent.PlayerLoggedOutEvent(presser[0]));
                    case USER_LEFT_LEVEL -> level.removePlayerImmediately(presser[0], Entity.RemovalReason.CHANGED_DIMENSION);
                    case USER_DIED -> {
                        presser[0].setHealth(0.0F);
                        presser[0].die(level.damageSources().generic());
                    }
                    default -> { }
                }
            }
            if (at[0] >= 4 && session.ended() && endedAt[0] == 0) {
                endedAt[0] = at[0];
                List<String> held = new ArrayList<>();
                if (session.endedAs() != reason) {
                    held.add("it ended as " + session.endedAs());
                }
                if (EpitaphSessions.any || !EpitaphSessions.all().isEmpty() || EpitaphPredictions.any()) {
                    held.add("a prediction is still said to run");
                }
                if (!session.binder().isEmpty()) {
                    held.add("the prediction still has bodies bound");
                }
                for (Entity body : List.of(mount, rider, walker)) {
                    if (EpitaphSessions.isOnTrack(body) || EpitaphPredictions.isOnTrack(body)) {
                        held.add("the " + body.getType().toShortString() + " is still on its track");
                    }
                }
                if (EpitaphSandbox.depth != 0 || EpitaphSandbox.replaying()) {
                    held.add("the room is still entered");
                }
                if (EntityTimeRates.rate(EntityTimeRates.Channel.BEING, walker) < 1.0F) {
                    held.add("a body is still declared to live slower");
                }
                fixture.rule(held.isEmpty(), "after the end as " + reason + ": " + String.join(", ", held));
                marks[0] = ticked[0];
                marks[1] = ticked[1];
            }
            if (endedAt[0] > 0 && at[0] == endedAt[0] + 3) {
                fixture.rule(ticked[0] > marks[0] && ticked[1] > marks[1], "three ticks after the end as " + reason
                        + " the walker ticked " + (ticked[0] - marks[0]) + " times and the rider " + (ticked[1] - marks[1]));
                step[0]++;
                at[0] = 0;
            } else if (at[0] > 80) {
                fixture.rule(false, "the prediction did not end as " + reason + " within 80 ticks");
                session.end(EpitaphEnd.CANCELLED);
                step[0]++;
                at[0] = 0;
            }
            if (step[0] == reasons.length) {
                fixture.finish("Every end leaves nothing held");
            }
        }));
    }

    // A22
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_order")
    public static void coreAndAddonMixinsBothRun(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-order", 0, 16, 8);
        fixture.budget(0);
        ServerLevel level = fixture.level;
        Chicken mount = fixture.mob(EntityType.CHICKEN, 10.5D, 4.5D);
        Zombie rider = fixture.mob(EntityType.ZOMBIE, 10.5D, 4.5D);
        rider.setBaby(true);
        Zombie walker = fixture.mob(EntityType.ZOMBIE, 12.5D, 2.5D);
        Runnable[] resume = new Runnable[1];
        BlockPos glass = fixture.absolute(4, 2, 5);
        fixture.base.onClose(() -> CrazyDRestoreTerrainAbility.forgetBrokenBlocks(level, List.of(glass)));
        BlockPos button = fixture.absolute(6, 2, 6);
        fixture.base.onClose(() -> level.setBlock(button, Blocks.AIR.defaultBlockState(), 3));
        long[] buttonTicks = new long[2];
        EpitaphSession[] running = new EpitaphSession[1];
        int[] mountTicks = new int[1];
        int[] ticked = new int[2];
        helper.onEachTick(() -> {
            if (buttonTicks[0] > 0L && buttonTicks[1] == 0L && !level.getBlockState(button).getOptionalValue(BlockStateProperties.POWERED).orElse(false)) {
                buttonTicks[1] = level.getGameTime();
            }
        });
        fixture.listen(EntityTickEvent.Post.class, event -> {
            if (running[0] != null && event.getEntity() == walker) {
                ticked[0]++;
            } else if (running[0] != null && event.getEntity() == rider) {
                ticked[1]++;
            }
        });
        helper.runAfterDelay(3, () -> fixture.guarded(() -> {
            helper.assertTrue(rider.startRiding(mount, true), "Setup: the zombie did not mount the hen");
            level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
            level.setBlock(button, Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                    .setValue(BlockStateProperties.POWERED, true), 3);
            // The hen moves in stopped time, so its rider is reached by the tick of a passenger, where the core has
            // its hold of stopped time and Epitaph has none: a rider on its track is held by its tick event.
            mount.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 200));
            EpitaphSession session = fixture.predict(rider, walker);
            helper.assertTrue(EpitaphSessions.isOnTrack(rider) && EpitaphSessions.isOnTrack(walker) && !EpitaphSessions.isOnTrack(mount),
                    "Setup: the rider and the walker are not on their tracks, or the hen is");
            // A block broken in the room, with no drops: the kind the core remembers for Crazy Diamond to restore.
            boolean broke;
            EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
            try {
                broke = level.destroyBlock(glass, false);
            }
            finally {
                EpitaphSandbox.exit();
            }
            BrokenBlocksChunkData memory = BrokenBlocksChunkData.getExistingData(level, glass);
            fixture.rule(broke && level.getBlockState(glass).is(Blocks.GLASS)
                    && KingCrimsonEpitaphFixture.ops(session, RecordedOp.DestroyBlock.class).stream().anyMatch(op -> op.pos().equals(glass)),
                    "a block broken in the room is gone from the world, or was not recorded");
            fixture.rule(memory == null || !memory.wasBlockBroken(glass),
                    "the core remembers, for Crazy Diamond to restore, a block that was only broken in a room: its hook was reached");
            resume[0] = fixture.stopTime(new ChunkPos(mount.blockPosition()));
            helper.assertTrue(TimeStopState.shouldFreezeOnServer(rider) && TimeStopState.shouldFreezeOnServer(walker)
                    && !TimeStopState.shouldFreezeOnServer(mount), "Setup: stopped time does not hold the rider and the walker, or holds the hen");
            // What the core does to an entity it holds, each time its tick comes up: the frames after a hit run down.
            rider.invulnerableTime = 15;
            walker.invulnerableTime = 15;
            mountTicks[0] = mount.tickCount;
            // A tick scheduled in the room, in stopped time. The core notes each tick scheduled into a stopped chunk, to
            // move it by the stop when time resumes: one that the room kept back was never scheduled.
            EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
            try {
                level.scheduleTick(button, Blocks.STONE_BUTTON, 1);
            }
            finally {
                EpitaphSandbox.exit();
            }
            fixture.rule(!level.getBlockTicks().hasScheduledTick(button, Blocks.STONE_BUTTON)
                    && KingCrimsonEpitaphFixture.ops(session, RecordedOp.ScheduleTick.class).stream().anyMatch(op -> op.pos().equals(button)),
                    "a tick scheduled in the room is scheduled in the world, or was not recorded");
            running[0] = session;
        }));
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            // For real, still in stopped time: the release of a pressed button, 20 ticks on.
            level.scheduleTick(button, Blocks.STONE_BUTTON, 20);
            buttonTicks[0] = level.getGameTime();
        }));
        helper.runAfterDelay(7, () -> fixture.guarded(() -> {
            fixture.rule(mount.tickCount > mountTicks[0] && !mount.isNoAi(), "Setup: the hen does not tick in stopped time");
            fixture.rule(walker.invulnerableTime < 15, "over four ticks of stopped time the core did nothing to a creature that walks its"
                    + " track: on the tick of an entity its hook does not act before the hold of the track");
            fixture.rule(rider.invulnerableTime < 15, "over four ticks of stopped time the core did nothing to a rider on its track:"
                    + " it is not frozen as a creature that walks its track is");
            resume[0].run();
        }));
        helper.runAfterDelay(10, () -> fixture.guarded(() -> {
            fixture.rule(!walker.isNoAi() && EpitaphSessions.isOnTrack(walker) && EpitaphSessions.isOnTrack(rider),
                    "after time resumed the walker is still without its AI, or a body left its track");
            fixture.rule(ticked[0] == 0 && ticked[1] == 0, "on their tracks, through stopped time and three ticks after it, the walker"
                    + " ticked by itself " + ticked[0] + " times and the rider " + ticked[1]);
            running[0].end(EpitaphEnd.CANCELLED);
            // For real, with no room: our hook lets the call through and the core's acts.
            boolean broke = level.destroyBlock(glass, false);
            BrokenBlocksChunkData memory = BrokenBlocksChunkData.getExistingData(level, glass);
            fixture.rule(broke && level.getBlockState(glass).isAir() && memory != null && memory.wasBlockBroken(glass),
                    "a block broken for real with no drops is not remembered by the core: its hook did not act");
        }));
        helper.runAfterDelay(34, () -> fixture.guarded(() -> {
            long waited = buttonTicks[1] - buttonTicks[0];
            // Scheduled two ticks before time resumed, the release is counted anew from the resume by the core.
            fixture.rule(buttonTicks[1] > 0L && waited >= 22L && waited <= 24L, "a button pressed for 20 ticks, two ticks before stopped time ended, "
                    + (buttonTicks[1] == 0L ? "was not released 29 ticks later" : "was released after " + waited + " ticks")
                    + ": the core did not move its tick by the stop, as if it had noted the tick of the same place that only the room saw");
            fixture.finish("The hooks of the core and of Epitaph on the same methods both act, in their order");
        }));
    }
}
