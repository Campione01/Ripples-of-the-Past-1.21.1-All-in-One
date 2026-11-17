package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.network.TemporalStateSyncPayload;
import rotp.addons.kingcrimson.network.TimeEraseShiftPayload;
import rotp.addons.kingcrimson.network.TimeEraseTransitionPayload;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseShiftService;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.api.time.TimeEraseWitnesses;
import rotp.core.network.s2c.PlayVoiceLinePacket;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * Owner 2026-11-07, the shifted image: during a Time Erase every other player of the dimension is seen by the eraser
 * ahead of where he is, by a share of each move he makes, and is put there when the erase ends; he is told nothing
 * before that; then the day jumps ahead by the erased time. Each test holds the real key down and moves the other
 * players as a server sees a walking player move; each has a batch of its own, as an erase shifts every player of the level.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEraseShiftGameTests {
    private static final int BEGUN = 10;
    private static final int WALK_FROM = 12;
    private static final double STEP = 0.2D;
    private static final Set<UUID> KEEPERS = ConcurrentHashMap.newKeySet();
    private static volatile float worldRate = LevelTimeRates.NO_RATE;
    private static boolean registered;

    private KingCrimsonEraseShiftGameTests() {}

    // Neither can be unregistered: they answer only while a test of this class asks them to.
    private static synchronized void registerRules() {
        if (!registered) {
            TimeEraseWitnesses.register(KingCrimsonAddon.id("gametest_keeps_place"), (witness, eraser, question) ->
                    question == TimeEraseWitnesses.Question.KEEPS_PLACE && KEEPERS.contains(witness.getUUID()));
            LevelTimeRates.register(KingCrimsonAddon.id("gametest_shift_world_time"), (level, channel) ->
                    channel == LevelTimeRates.Channel.TIME ? worldRate : LevelTimeRates.NO_RATE);
            registered = true;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "kc_shift_walk")
    public static void anotherPlayerWhoWalksIsSeenAheadAndIsPutThereWhenTheEraseEnds(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-shift-walk", 24, 12);
        ServerPlayer eraser = scene.eraser;
        ServerPlayer walker = scene.other("kc-shift-walker", 5.5D, 2.0D, 6.5D);
        ServerPlayer stander = scene.other("kc-shift-stander", 5.5D, 2.0D, 9.5D);
        int[] sentBefore = new int[3];
        Vec3[] start = new Vec3[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            sentBefore[0] = KingCrimsonEraseFixture.listener(eraser).sentCount();
            sentBefore[1] = KingCrimsonEraseFixture.listener(walker).sentCount();
            sentBefore[2] = KingCrimsonEraseFixture.listener(stander).sentCount();
            scene.press();
        }));
        helper.runAfterDelay(BEGUN, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            start[0] = walker.position();
        }));
        scene.walk(walker, WALK_FROM, 40, STEP, 0.0D);
        helper.runAfterDelay(WALK_FROM + 44, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            Vec3 real = walker.position();
            Vec3 walked = real.subtract(start[0]);
            helper.assertTrue(Math.abs(walked.x - 8.0D) < 1.0E-6D, "Setup: the walker went " + walked + ", not 8 blocks east");
            Vec3 image = KingCrimsonTimeEraseShiftService.imageOf(eraser, walker);
            Vec3 expected = real.add(walked.scale(0.3D));
            scene.rule(near(image, expected), "a player who walked " + walked + " is seen at " + image.subtract(real)
                    + " from where he is, not 0.3 of his way ahead of it (" + expected.subtract(real) + ")");
            scene.rule(near(KingCrimsonTimeEraseShiftService.imageOf(eraser, stander), stander.position()),
                    "a player who stood still is seen away from where he is");

            List<TimeEraseShiftPayload> told = KingCrimsonEraseFixture.listener(eraser)
                    .payloadsSince(sentBefore[0], TimeEraseShiftPayload.class);
            TimeEraseShiftPayload last = told.isEmpty() ? null : told.get(told.size() - 1);
            scene.rule(last != null && near(imageIn(last, walker), image) && near(imageIn(last, stander), stander.position()),
                    "the eraser's client is not told where he sees the two players: " + last);
            scene.rule(voiceLines(eraser, sentBefore[0]) == 1, "the eraser heard his line " + voiceLines(eraser, sentBefore[0])
                    + " times");
            for (ServerPlayer other : List.of(walker, stander)) {
                int since = other == walker ? sentBefore[1] : sentBefore[2];
                scene.rule(oursSince(other, since).isEmpty(), other.getGameProfile().getName()
                        + " was sent, before the erase ended: " + oursSince(other, since));
            }
            // All he does and all that is done to him is real.
            float before = walker.getHealth();
            scene.rule(walker.hurt(walker.damageSources().magic(), 2.0F) && walker.getHealth() < before,
                    "a player in someone's erase is not hurt as usual");

            float yaw = walker.getYRot();
            float pitch = walker.getXRot();
            Vec3 standerAt = stander.position();
            int eraserFlashes = transitions(eraser, sentBefore[0]);
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            helper.assertTrue(!KingCrimsonEraseFixture.erasing(eraser), "Setup: letting the key go did not end the erase");
            scene.rule(near(walker.position(), image), "at the end he stands at " + walker.position()
                    + ", not on the image the eraser last saw of him at " + image);
            scene.rule(walker.getYRot() == yaw && walker.getXRot() == pitch, "the end turned his head");
            scene.rule(near(stander.position(), standerAt) && moves(stander, sentBefore[2]).isEmpty(),
                    "the end moved a player who stood still");
            List<ClientboundPlayerPositionPacket> moved = moves(walker, sentBefore[1]);
            scene.rule(moved.size() == 1
                            && moved.get(0).getRelativeArguments().equals(EnumSet.allOf(RelativeMovement.class))
                            && near(new Vec3(moved.get(0).getX(), moved.get(0).getY(), moved.get(0).getZ()), image.subtract(real))
                            && moved.get(0).getYRot() == 0.0F && moved.get(0).getXRot() == 0.0F,
                    "his client is not moved once, relative to where it has him, by " + image.subtract(real) + ": "
                            + moved.stream().map(move -> move.getRelativeArguments() + " " + move.getX() + " "
                                    + move.getY() + " " + move.getZ()).toList());
            scene.rule(transitions(eraser, sentBefore[0]) == eraserFlashes + 1 && transitions(walker, sentBefore[1]) == 0,
                    "the red flash of the end went to the eraser " + (transitions(eraser, sentBefore[0]) - eraserFlashes)
                            + " times and to the other player " + transitions(walker, sentBefore[1]));
            scene.rule(endSounds(walker, sentBefore[1]) == 1 && endSounds(eraser, sentBefore[0]) == 1,
                    "the end was heard by the eraser " + endSounds(eraser, sentBefore[0]) + " times and by the other player "
                            + endSounds(walker, sentBefore[1]));
            told = KingCrimsonEraseFixture.listener(eraser).payloadsSince(sentBefore[0], TimeEraseShiftPayload.class);
            scene.rule(!told.isEmpty() && told.get(told.size() - 1).images().isEmpty(),
                    "the eraser's client is not told to let go of the images");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = "kc_shift_back")
    public static void walkingBackTakesTheShiftBackAndALeapOrARideAddsNone(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-shift-back", 30, 12);
        ServerPlayer eraser = scene.eraser;
        ServerPlayer walker = scene.other("kc-shift-there-back", 6.5D, 2.0D, 4.5D);
        ServerPlayer rider = scene.other("kc-shift-rider", 6.5D, 2.0D, 9.5D);
        Pig pig = scene.fixture.track(EntityType.PIG.create(helper.getLevel()));
        pig.setNoAi(true);
        pig.setPos(rider.position());
        helper.getLevel().addFreshEntity(pig);
        int[] riderSent = new int[1];
        Vec3[] riderStart = new Vec3[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(rider.startRiding(pig, true), "Setup: the rider did not mount");
            scene.press();
        }));
        helper.runAfterDelay(BEGUN, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            riderSent[0] = KingCrimsonEraseFixture.listener(rider).sentCount();
            riderStart[0] = rider.position();
        }));
        for (int tick = 0; tick < 20; tick++) {
            helper.runAfterDelay(WALK_FROM + tick, () -> pig.setPos(pig.getX() + STEP, pig.getY(), pig.getZ()));
        }
        scene.walk(walker, WALK_FROM, 20, STEP, 0.0D);
        helper.runAfterDelay(WALK_FROM + 22, () -> scene.fixture.guarded(() -> {
            scene.rule(near(offset(eraser, walker), new Vec3(1.2D, 0.0D, 0.0D)),
                    "after 4 blocks east he is seen " + offset(eraser, walker) + " from where he is, not 1.2 east");
            helper.assertTrue(rider.getVehicle() == pig && rider.getX() - riderStart[0].x > 3.9D,
                    "Setup: the rider was not carried 4 blocks: " + rider.position().subtract(riderStart[0])
                            + ", riding " + rider.getVehicle() + "; the pig is alive " + pig.isAlive() + " with "
                            + pig.getHealth() + " health at " + pig.position().subtract(riderStart[0]) + " in "
                            + helper.getLevel().getBlockState(pig.blockPosition()) + ", last hurt by "
                            + pig.getLastDamageSource() + "; the rider's eyes are in "
                            + helper.getLevel().getBlockState(BlockPos.containing(rider.getEyePosition())));
            scene.rule(near(offset(eraser, rider), Vec3.ZERO), "a rider carried 4 blocks is seen "
                    + offset(eraser, rider) + " from where he is: a rider adds nothing");
        }));
        scene.walk(walker, WALK_FROM + 24, 20, -STEP, 0.0D);
        helper.runAfterDelay(WALK_FROM + 46, () -> scene.fixture.guarded(() -> {
            scene.rule(offset(eraser, walker).length() < 0.01D,
                    "there and back, he is seen " + offset(eraser, walker) + " from where he is, not where he is");
            // A pearl, a teleport: no move of his own.
            walker.setPos(walker.getX() + 10.0D, walker.getY(), walker.getZ());
        }));
        helper.runAfterDelay(WALK_FROM + 48, () -> scene.fixture.guarded(() ->
                scene.rule(offset(eraser, walker).length() < 0.01D,
                        "a leap of 10 blocks put his image " + offset(eraser, walker) + " from him: a leap adds nothing")));
        scene.walk(walker, WALK_FROM + 50, 10, STEP, 0.0D);
        helper.runAfterDelay(WALK_FROM + 62, () -> scene.fixture.guarded(() -> {
            scene.rule(near(offset(eraser, walker), new Vec3(0.6D, 0.0D, 0.0D)),
                    "after 2 more blocks east he is seen " + offset(eraser, walker) + " from where he is, not 0.6 east");
            walker.setPos(walker.getX() - 6.0D, walker.getY(), walker.getZ());
        }));
        helper.runAfterDelay(WALK_FROM + 64, () -> scene.fixture.guarded(() -> {
            scene.rule(near(offset(eraser, walker), new Vec3(0.6D, 0.0D, 0.0D)),
                    "a leap back of 6 blocks changed how far ahead he is seen to " + offset(eraser, walker)
                            + ": the image goes with him as it is");
            Vec3 image = KingCrimsonTimeEraseShiftService.imageOf(eraser, walker);
            Vec3 riderAt = rider.position();
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            scene.rule(near(walker.position(), image), "at the end he stands at " + walker.position()
                    + ", not on his image at " + image);
            scene.rule(rider.getVehicle() == pig && near(rider.position(), riderAt)
                            && moves(rider, riderSent[0]).isEmpty(),
                    "the end moved a rider, or took him off his mount");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "kc_shift_terrain")
    public static void theImageStaysWhereHeCouldHaveWalked(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-shift-terrain", 26, 22);
        ServerPlayer eraser = scene.eraser;
        BlockState stone = Blocks.STONE.defaultBlockState();
        // Five lanes to the east, each walked from x = 6.5 to x = 13.3, so each image is wanted 2.04 blocks further.
        for (int y = 2; y <= 4; y++) {
            for (int z = 2; z <= 4; z++) {
                scene.place(new BlockPos(14, y, z), stone);
            }
        }
        for (int x = 2; x <= 13; x++) {
            for (int z = 6; z <= 8; z++) {
                for (int y = 2; y <= 6; y++) {
                    scene.place(new BlockPos(x, y, z), stone);
                }
            }
            for (int z = 10; z <= 12; z++) {
                for (int y = 2; y <= 4; y++) {
                    scene.place(new BlockPos(x, y, z), stone);
                }
            }
        }
        for (int x = 14; x <= 16; x++) {
            for (int z = 14; z <= 16; z++) {
                scene.place(new BlockPos(x, 0, z), stone);
                scene.place(new BlockPos(x, 1, z), Blocks.LAVA.defaultBlockState());
            }
        }
        for (int x = 14; x <= 20; x++) {
            for (int z = 18; z <= 20; z++) {
                scene.place(new BlockPos(x, 2, z), stone);
            }
        }
        ServerPlayer atWall = scene.other("kc-shift-wall", 6.5D, 2.0D, 3.5D);
        ServerPlayer atCliff = scene.other("kc-shift-cliff", 6.5D, 7.0D, 7.5D);
        ServerPlayer atDrop = scene.other("kc-shift-drop", 6.5D, 5.0D, 11.5D);
        ServerPlayer atLava = scene.other("kc-shift-lava", 6.5D, 2.0D, 15.5D);
        ServerPlayer atStep = scene.other("kc-shift-step", 6.5D, 2.0D, 19.5D);
        List<ServerPlayer> walkers = List.of(atWall, atCliff, atDrop, atLava, atStep);
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(BEGUN, () -> scene.fixture.guarded(scene::assertErasing));
        for (ServerPlayer walker : walkers) {
            scene.walk(walker, WALK_FROM, 34, STEP, 0.0D);
        }
        helper.runAfterDelay(WALK_FROM + 38, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer walker : walkers) {
                helper.assertTrue(Math.abs(walker.getX() - origin.x - 13.3D) < 1.0E-6D,
                        "Setup: " + walker.getGameProfile().getName() + " is at x " + (walker.getX() - origin.x));
            }
            Vec3 wall = KingCrimsonTimeEraseShiftService.imageOf(eraser, atWall).subtract(origin);
            scene.rule(wall.x >= 13.3D - 1.0E-6D && wall.x <= 13.71D && level(wall.y, 2.0D),
                    "with a wall 0.7 ahead of him his image is at " + wall + ": it went into or through the wall at x 14");
            Vec3 cliff = KingCrimsonTimeEraseShiftService.imageOf(eraser, atCliff).subtract(origin);
            scene.rule(level(cliff.y, 7.0D) && cliff.x > 13.9D && cliff.x <= 14.3001D,
                    "with a drop of 5 blocks ahead his image is at " + cliff + ", not at the edge (x 14, y 7)");
            Vec3 drop = KingCrimsonTimeEraseShiftService.imageOf(eraser, atDrop).subtract(origin);
            scene.rule(level(drop.y, 2.0D) && Math.abs(drop.x - 15.34D) < 1.0E-3D,
                    "with a drop of 3 blocks ahead his image is at " + drop + ", not down it at x 15.34, y 2");
            Vec3 lava = KingCrimsonTimeEraseShiftService.imageOf(eraser, atLava).subtract(origin);
            scene.rule(level(lava.y, 2.0D) && lava.x >= 13.3D - 1.0E-6D && lava.x <= 13.71D,
                    "with lava 0.7 ahead of him his image is at " + lava + ", not short of the lava at x 14");
            Vec3 step = KingCrimsonTimeEraseShiftService.imageOf(eraser, atStep).subtract(origin);
            scene.rule(level(step.y, 3.0D) && Math.abs(step.x - 15.34D) < 1.0E-3D,
                    "with a step of one block ahead his image is at " + step + ", not up it at x 15.34, y 3");
            List<Vec3> images = walkers.stream().map(walker -> KingCrimsonTimeEraseShiftService.imageOf(eraser, walker)).toList();
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            for (int i = 0; i < walkers.size(); i++) {
                ServerPlayer walker = walkers.get(i);
                scene.rule(near(walker.position(), images.get(i)) && helper.getLevel().noCollision(walker),
                        walker.getGameProfile().getName() + " ended at " + walker.position().subtract(origin)
                                + ", not free on his image at " + images.get(i).subtract(origin));
            }
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_shift_all")
    public static void everyPlayerOfTheDimensionIsMovedInTheOneTickButASpectatorAndAWitnessWhoKeepsHisPlace(
            GameTestHelper helper) {
        registerRules();
        Scene scene = new Scene(helper, "kc-shift-all", 30, 20);
        ServerPlayer eraser = scene.eraser;
        // "一點點" is a setting: a whole block for each block here.
        KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.set(1.0D);
        ServerPlayer north = scene.other("kc-shift-north", 8.5D, 2.0D, 10.5D);
        ServerPlayer south = scene.other("kc-shift-south", 12.5D, 2.0D, 10.5D);
        ServerPlayer east = scene.other("kc-shift-east", 16.5D, 2.0D, 10.5D);
        ServerPlayer keeper = scene.other("kc-shift-keeper", 16.5D, 2.0D, 16.5D);
        ServerPlayer watcher = scene.other("kc-shift-spectator", 16.5D, 2.0D, 4.5D);
        ServerPlayer leaver = scene.other("kc-shift-leaver", 8.5D, 2.0D, 16.5D);
        watcher.setGameMode(GameType.SPECTATOR);
        KEEPERS.add(keeper.getUUID());
        scene.fixture.onClose(KEEPERS::clear);
        int[] keeperSent = new int[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(BEGUN, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            keeperSent[0] = KingCrimsonEraseFixture.listener(keeper).sentCount();
        }));
        scene.walk(north, WALK_FROM, 10, 0.0D, -STEP);
        scene.walk(south, WALK_FROM, 10, 0.0D, STEP);
        for (ServerPlayer walker : List.of(east, keeper, watcher, leaver)) {
            scene.walk(walker, WALK_FROM, 10, STEP, 0.0D);
        }
        helper.runAfterDelay(WALK_FROM + 12, () -> scene.fixture.guarded(() -> {
            scene.rule(near(offset(eraser, north), new Vec3(0.0D, 0.0D, -2.0D))
                            && near(offset(eraser, south), new Vec3(0.0D, 0.0D, 2.0D))
                            && near(offset(eraser, east), new Vec3(2.0D, 0.0D, 0.0D)),
                    "at one block for each block walked, three players who walked 2 blocks north, south and east are seen "
                            + offset(eraser, north) + ", " + offset(eraser, south) + " and " + offset(eraser, east)
                            + " from where they are");
            scene.rule(near(offset(eraser, keeper), new Vec3(2.0D, 0.0D, 0.0D)),
                    "a witness who keeps his place is seen " + offset(eraser, keeper) + " from where he is, not shifted "
                            + "like any other player");
            scene.rule(near(offset(eraser, watcher), Vec3.ZERO), "a Spectator is seen shifted by " + offset(eraser, watcher));
            scene.rule(near(offset(eraser, leaver), new Vec3(2.0D, 0.0D, 0.0D)), "Setup: the player who will leave is "
                    + "seen " + offset(eraser, leaver) + " from where he is");
            helper.getLevel().removePlayerImmediately(leaver, Entity.RemovalReason.DISCARDED);
        }));
        helper.runAfterDelay(WALK_FROM + 14, () -> scene.fixture.guarded(() -> {
            scene.rule(near(offset(eraser, leaver), Vec3.ZERO), "a player who left is still kept an image of");
            TimeEraseShiftPayload last = lastTold(eraser);
            scene.rule(last != null && imageIn(last, watcher) == null && imageIn(last, leaver) == null
                            && imageIn(last, keeper) != null && last.images().size() == 4,
                    "the eraser's client is told the images of " + last + ", not of the four players he sees shifted");
            List<ServerPlayer> moved = List.of(north, south, east);
            List<Vec3> images = moved.stream().map(player -> KingCrimsonTimeEraseShiftService.imageOf(eraser, player)).toList();
            Vec3 keeperAt = keeper.position();
            Vec3 watcherAt = watcher.position();
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            for (int i = 0; i < moved.size(); i++) {
                scene.rule(near(moved.get(i).position(), images.get(i)), moved.get(i).getGameProfile().getName()
                        + " is not on his image in the tick the erase ended");
            }
            scene.rule(near(keeper.position(), keeperAt) && moves(keeper, keeperSent[0]).isEmpty(),
                    "a witness who keeps his place was moved at the end, to " + keeper.position().subtract(keeperAt)
                            + " from where he was");
            scene.rule(near(watcher.position(), watcherAt), "a Spectator was moved at the end");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "kc_shift_off")
    public static void withTheSettingOffNobodyIsShownOrMoved(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-shift-off", 20, 10);
        ServerPlayer eraser = scene.eraser;
        KingCrimsonConfig.TIME_ERASE_SHIFT_PLAYERS.set(false);
        ServerPlayer walker = scene.other("kc-shift-unshifted", 6.5D, 2.0D, 5.5D);
        int[] sent = new int[2];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            sent[0] = KingCrimsonEraseFixture.listener(eraser).sentCount();
            sent[1] = KingCrimsonEraseFixture.listener(walker).sentCount();
            scene.press();
        }));
        helper.runAfterDelay(BEGUN, () -> scene.fixture.guarded(scene::assertErasing));
        scene.walk(walker, WALK_FROM, 20, STEP, 0.0D);
        helper.runAfterDelay(WALK_FROM + 24, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            Vec3 at = walker.position();
            scene.rule(near(offset(eraser, walker), Vec3.ZERO), "with timeEraseShiftPlayers off a player who walked is "
                    + "seen " + offset(eraser, walker) + " from where he is");
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            scene.rule(near(walker.position(), at) && moves(walker, sent[1]).isEmpty(),
                    "with timeEraseShiftPlayers off the end moved a player");
            scene.rule(KingCrimsonEraseFixture.listener(eraser).payloadsSince(sent[0], TimeEraseShiftPayload.class).isEmpty(),
                    "with timeEraseShiftPlayers off the eraser's client is told of images");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 180, batch = "kc_shift_daytime")
    public static void theDayJumpsAheadByTheErasedTimeUnlessTheDaylightRuleIsOffOrTheWorldAccelerates(
            GameTestHelper helper) {
        registerRules();
        Scene scene = new Scene(helper, "kc-shift-day", 12, 8);
        ServerPlayer eraser = scene.eraser;
        ServerLevel level = helper.getLevel();
        GameRules.BooleanValue daylight = level.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
        boolean daylightBefore = daylight.get();
        scene.fixture.onClose(() -> {
            worldRate = LevelTimeRates.NO_RATE;
            daylight.set(daylightBefore, level.getServer());
        });
        int sentBefore = KingCrimsonEraseFixture.listener(eraser).sentCount();
        daylight.set(true, level.getServer());
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(42, () -> scene.fixture.guarded(() -> {
            long jumped = scene.releaseAndReadTheDaysJump();
            scene.rule(scene.erased > 20 && jumped == scene.erased, "an erase of " + scene.erased
                    + " ticks moved the day's time by " + jumped + " ticks at its end");
            scene.rule(level.getGameTime() == scene.gameTimeAtRelease, "the end of an erase moved the game time");
            // Alone on the server nothing of the shifted image runs.
            scene.rule(KingCrimsonEraseFixture.listener(eraser).payloadsSince(sentBefore, TimeEraseShiftPayload.class).isEmpty(),
                    "alone in the dimension the eraser's client is told of images");
            daylight.set(false, level.getServer());
        }));
        helper.runAfterDelay(46, () -> scene.fixture.guarded(scene::pressAgain));
        helper.runAfterDelay(86, () -> scene.fixture.guarded(() -> {
            long jumped = scene.releaseAndReadTheDaysJump();
            scene.rule(scene.erased > 20 && jumped == 0L, "with the daylight rule off an erase of " + scene.erased
                    + " ticks moved the day's time by " + jumped + " ticks");
            daylight.set(true, level.getServer());
            // Rule G3: no jump while the world's time is accelerated (Made in Heaven).
            worldRate = 2.0F;
        }));
        helper.runAfterDelay(90, () -> scene.fixture.guarded(scene::pressAgain));
        helper.runAfterDelay(130, () -> scene.fixture.guarded(() -> {
            long jumped = scene.releaseAndReadTheDaysJump();
            scene.rule(scene.erased > 20 && jumped == 0L, "while the world accelerates an erase of " + scene.erased
                    + " ticks moved the day's time by " + jumped + " ticks");
            worldRate = LevelTimeRates.NO_RATE;
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = "kc_shift_punch")
    public static void aPunchFromTheEraseAimedAtAWalkingPlayersImageHitsHim(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-shift-punch", 24, 10);
        ServerPlayer eraser = scene.eraser;
        KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.set(1.0D);
        boolean pvp = helper.getLevel().getServer().isPvpAllowed();
        helper.getLevel().getServer().setPvpAllowed(true);
        scene.fixture.onClose(() -> helper.getLevel().getServer().setPvpAllowed(pvp));
        // He comes 4 blocks toward the eraser and is seen 4 blocks nearer still: 2 blocks in front of the eraser, while
        // he is 6 blocks away, out of any punch's reach.
        ServerPlayer walker = scene.other("kc-shift-punched", 12.5D, 2.0D, 1.5D);
        Vec3[] image = new Vec3[1];
        Vec3[] real = new Vec3[1];
        int[] sent = new int[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(BEGUN, () -> scene.fixture.guarded(scene::assertErasing));
        scene.walk(walker, WALK_FROM, 20, -STEP, 0.0D);
        helper.runAfterDelay(WALK_FROM + 24, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            image[0] = KingCrimsonTimeEraseShiftService.imageOf(eraser, walker);
            real[0] = walker.position();
            sent[0] = KingCrimsonEraseFixture.listener(walker).sentCount();
            helper.assertTrue(near(image[0].subtract(walker.position()), new Vec3(-4.0D, 0.0D, 0.0D))
                            && Math.abs(image[0].distanceTo(eraser.position()) - 2.0D) < 1.0E-3D,
                    "Setup: he is seen " + image[0].subtract(walker.position()) + " from where he is, "
                            + image[0].distanceTo(eraser.position()) + " blocks from the eraser");
            // The eraser's client picks the entity it shows on the image.
            StandPower power = StandPower.get(eraser);
            LivingComponentAction.getComponent(eraser).entityAim.setTarget(new ActionTarget(walker));
            LivingComponentAction.getComponent(power.getSummonedStandEntity()).entityAim.setTarget(new ActionTarget(walker));
            eraser.lookAt(EntityAnchorArgument.Anchor.EYES, image[0].add(0.0D, 0.9D, 0.0D));
            helper.assertTrue(scene.fixture.press(eraser, "punch", KingCrimsonEraseFixture.OTHER_KEY, InputMethod.CLICK, false)
                    .isActionSuccess, "Setup: the punch was not taken in the erase");
        }));
        helper.runAfterDelay(WALK_FROM + 100, () -> scene.fixture.guarded(() -> {
            // The key is still held and the erase would last 15 s: the punch ended it.
            helper.assertTrue(!KingCrimsonEraseFixture.erasing(eraser), "Setup: the punch did not end the erase");
            // The punch knocks him on from there in the tick it lands: where he landed is the move he was sent.
            List<ClientboundPlayerPositionPacket> moved = moves(walker, sent[0]);
            scene.rule(moved.size() == 1 && near(new Vec3(moved.get(0).getX(), moved.get(0).getY(), moved.get(0).getZ()),
                            image[0].subtract(real[0])),
                    "when the punch ended the erase he was not put on the image it was aimed at, "
                            + image[0].subtract(real[0]) + " from where he was: " + moved.stream().map(move ->
                                    move.getX() + " " + move.getY() + " " + move.getZ()).toList());
            scene.rule(walker.getHealth() < walker.getMaxHealth(), "the punch aimed at his image did not hit him: health "
                    + walker.getHealth());
            scene.finish();
        }));
    }

    static boolean near(Vec3 a, Vec3 b) {
        return a != null && b != null && a.distanceToSqr(b) < 1.0E-6D;
    }

    private static boolean level(double y, double expected) {
        return Math.abs(y - expected) < 1.0E-6D;
    }

    private static Vec3 offset(ServerPlayer eraser, ServerPlayer other) {
        return KingCrimsonTimeEraseShiftService.imageOf(eraser, other).subtract(other.position());
    }

    private static Vec3 imageIn(TimeEraseShiftPayload payload, Entity entity) {
        for (TimeEraseShiftPayload.Image image : payload.images()) {
            if (image.entityId() == entity.getId()) {
                return image.position();
            }
        }
        return null;
    }

    private static TimeEraseShiftPayload lastTold(ServerPlayer eraser) {
        List<TimeEraseShiftPayload> told = KingCrimsonEraseFixture.listener(eraser).payloads(TimeEraseShiftPayload.class);
        return told.isEmpty() ? null : told.get(told.size() - 1);
    }

    /** What this module, or the core for it, sent a player; the silent sync of the eraser's state apart. */
    static List<String> oursSince(ServerPlayer player, int index) {
        Map<String, Integer> ours = new LinkedHashMap<>();
        for (Packet<?> packet : KingCrimsonEraseFixture.listener(player).sentSince(index)) {
            if (packet instanceof ClientboundCustomPayloadPacket custom) {
                boolean module = custom.payload().type().id().getNamespace().equals(KingCrimsonAddon.MOD_ID)
                        && !(custom.payload() instanceof TemporalStateSyncPayload);
                if (module || custom.payload() instanceof PlayVoiceLinePacket) {
                    ours.merge(custom.payload().type().id().toString(), 1, Integer::sum);
                }
            } else if (packet instanceof ClientboundPlayerPositionPacket) {
                ours.merge("a move of his own place", 1, Integer::sum);
            } else if (packet instanceof ClientboundSoundPacket sound
                    && sound.getSound().unwrapKey().map(key -> key.location().getNamespace().equals(KingCrimsonAddon.MOD_ID))
                            .orElse(false)) {
                ours.merge("sound " + sound.getSound().unwrapKey().get().location(), 1, Integer::sum);
            }
        }
        return ours.entrySet().stream().map(sent -> sent.getKey() + " x" + sent.getValue()).toList();
    }

    static List<ClientboundPlayerPositionPacket> moves(ServerPlayer player, int index) {
        List<ClientboundPlayerPositionPacket> moves = new ArrayList<>();
        for (Packet<?> packet : KingCrimsonEraseFixture.listener(player).sentSince(index)) {
            if (packet instanceof ClientboundPlayerPositionPacket move) {
                moves.add(move);
            }
        }
        return moves;
    }

    static int transitions(ServerPlayer player, int index) {
        return KingCrimsonEraseFixture.listener(player).payloadsSince(index, TimeEraseTransitionPayload.class).size();
    }

    private static int voiceLines(ServerPlayer player, int index) {
        return (int) KingCrimsonEraseFixture.listener(player).payloadsSince(index, PlayVoiceLinePacket.class).stream()
                .filter(line -> line.sound() != null).count();
    }

    static int endSounds(ServerPlayer player, int index) {
        int heard = 0;
        for (Packet<?> packet : KingCrimsonEraseFixture.listener(player).sentSince(index)) {
            if (packet instanceof ClientboundSoundPacket sound
                    && sound.getSound().value() == KingCrimsonAddon.TIME_ERASE_END.value()) {
                heard++;
            }
        }
        return heard;
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final ServerPlayer eraser;
        private final StandPower power;
        private final List<String> broken = new ArrayList<>();
        private int erased;
        private long gameTimeAtRelease;

        private Scene(GameTestHelper helper, String name, int length, int width) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(length, width);
            eraser = fixture.player(name, 2.5D, 2.0D, 1.5D);
            power = fixture.kingCrimson(eraser, 4);
            helper.onEachTick(() -> {
                if (!eraser.isRemoved()) {
                    power.setStamina(power.getMaxStamina());
                }
            });
            double perMove = KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.get();
            boolean shifts = KingCrimsonConfig.TIME_ERASE_SHIFT_PLAYERS.get();
            fixture.onClose(() -> {
                KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.set(perMove);
                KingCrimsonConfig.TIME_ERASE_SHIFT_PLAYERS.set(shifts);
            });
        }

        private ServerPlayer other(String name, double x, double y, double z) {
            ServerPlayer player = fixture.player(name, x, y, z);
            player.setOnGround(true);
            return player;
        }

        /** A block of the scene's ground, put back when the test closes: the next test here finds the plain floor. */
        private void place(BlockPos relative, BlockState state) {
            BlockPos at = helper.absolutePos(relative);
            BlockState before = helper.getLevel().getBlockState(at);
            helper.getLevel().setBlock(at, state, 3);
            fixture.onClose(() -> helper.getLevel().setBlock(at, before, 3));
        }

        /** He is moved by this much in each of so many ticks, as the server sees a walking player move. */
        private void walk(ServerPlayer who, int fromTick, int ticks, double dx, double dz) {
            for (int tick = 0; tick < ticks; tick++) {
                helper.runAfterDelay(fromTick + tick, () -> {
                    if (!who.isRemoved()) {
                        who.setPos(who.getX() + dx, who.getY(), who.getZ() + dz);
                    }
                });
            }
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

        /** Lets the key go and returns by how much the day's time moved in that call. */
        private long releaseAndReadTheDaysJump() {
            assertErasing();
            ServerLevel level = helper.getLevel();
            gameTimeAtRelease = level.getGameTime();
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(eraser);
            erased = state.elapsedWorldTicks(gameTimeAtRelease);
            long day = level.getDayTime();
            fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            helper.assertTrue(!KingCrimsonEraseFixture.erasing(eraser), "Setup: letting the key go did not end the erase");
            return level.getDayTime() - day;
        }

        private void rule(boolean holds, String broke) {
            if (!holds) {
                broken.add(broke);
            }
        }

        private void finish() {
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(), "The shifted image: " + String.join("; ", broken)));
            fixture.succeed();
        }
    }
}
