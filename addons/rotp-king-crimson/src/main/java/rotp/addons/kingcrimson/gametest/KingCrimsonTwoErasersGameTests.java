package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.mixin.epitaph.PlayerListAccessor;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageSpawnPayload;
import rotp.addons.kingcrimson.network.TimeEraseShiftPayload;
import rotp.addons.kingcrimson.state.KingCrimsonBloodThrowService;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseShiftService;
import rotp.core.api.entity.MobAiPause;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * Owner 2026-11-11: "兩個紅王持有者的時間刪除互相獨立，雙方都無法察覺自身在誰的時刪裡". Two holders of King Crimson on one server:
 * each erases on his own, also inside the erase of the other; nothing tells either that the other erases; one who
 * erases is out of the other's erase, and his decoy stands in the world for him. The holders are on the server's own
 * list of players, as real players are: the refusal these tests guard against read that list.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonTwoErasersGameTests {
    private static final int BEGUN = 10;
    private static final double STEP = 0.2D;

    private KingCrimsonTwoErasersGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120, batch = "kc_two_own")
    public static void twoHoldersEraseEachOnHisOwn(GameTestHelper helper) {
        Scene scene = new Scene(helper, 20, 12);
        ServerPlayer first = scene.holder("kc-two-first", 2.5D, 1.5D);
        ServerPlayer second = scene.holder("kc-two-second", 2.5D, 8.5D);
        scene.listed(first, second);
        int[] sent = new int[2];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            sent[1] = KingCrimsonEraseFixture.listener(second).sentCount();
            helper.assertTrue(scene.fixture.holdErase(first).isActionSuccess, "Setup: the first erase did not start");
        }));
        helper.runAfterDelay(2 + BEGUN, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(first), "Setup: the first holder does not erase");
            List<String> told = KingCrimsonEraseShiftGameTests.oursSince(second, sent[1]);
            scene.rule(told.isEmpty(), "a holder inside the erase of another holder was sent " + told);
            scene.rule(KingCrimsonTemporalStateService.canStartTimeSkip(second),
                    "the Time Skip of a holder is refused while another holder erases");
            sent[0] = KingCrimsonEraseFixture.listener(first).sentCount();
            scene.rule(scene.fixture.holdErase(second).isActionSuccess,
                    "the erase key of a holder is refused while another holder erases");
        }));
        helper.runAfterDelay(2 + 2 * BEGUN, () -> scene.fixture.guarded(() -> {
            scene.rule(KingCrimsonEraseFixture.erasing(first) && KingCrimsonEraseFixture.erasing(second),
                    "the two holders do not erase at the same time: the first " + KingCrimsonEraseFixture.erasing(first)
                            + ", the second " + KingCrimsonEraseFixture.erasing(second));
            scene.rule(scene.fixture.decoys(first).size() == 1 && scene.fixture.decoys(second).size() == 1,
                    "each erase does not have its own decoy: " + scene.fixture.decoys(first).size() + " and "
                            + scene.fixture.decoys(second).size());
            // What the second's erase sent the first, who erases: only where he sees the bodies of his own erase.
            List<String> told = new ArrayList<>(KingCrimsonEraseShiftGameTests.oursSince(first, sent[0]));
            told.removeIf(what -> what.startsWith(TimeEraseShiftPayload.TYPE.id().toString()));
            scene.rule(told.isEmpty(), "a holder who erases was sent, when another holder began to erase: " + told);
            scene.fixture.release(first, KingCrimsonEraseFixture.ERASE_KEY);
            scene.rule(!KingCrimsonEraseFixture.erasing(first) && KingCrimsonEraseFixture.erasing(second),
                    "the end of the erase of one holder did not leave the other's running: the first "
                            + KingCrimsonEraseFixture.erasing(first) + ", the second " + KingCrimsonEraseFixture.erasing(second));
            scene.rule(scene.fixture.decoys(first).isEmpty() && scene.fixture.decoys(second).size() == 1,
                    "the end of one erase did not take its own decoy and leave the other's: "
                            + scene.fixture.decoys(first).size() + " and " + scene.fixture.decoys(second).size());
        }));
        helper.runAfterDelay(6 + 2 * BEGUN, () -> scene.fixture.guarded(() -> {
            StandPower.get(first).setAbilityCooldown("kingcrimson_timeerase", 0, 0);
            scene.rule(scene.fixture.holdErase(first).isActionSuccess,
                    "inside the erase of the second holder the first cannot erase again");
        }));
        helper.runAfterDelay(6 + 3 * BEGUN, () -> scene.fixture.guarded(() -> {
            scene.rule(KingCrimsonEraseFixture.erasing(first) && KingCrimsonEraseFixture.erasing(second),
                    "an erase begun inside the erase of another holder does not run beside it");
            scene.fixture.release(second, KingCrimsonEraseFixture.ERASE_KEY);
            scene.rule(KingCrimsonEraseFixture.erasing(first) && !KingCrimsonEraseFixture.erasing(second),
                    "the end of the second erase did not leave the first's running");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = "kc_two_decoy")
    public static void oneWhoErasesIsOutOfTheOthersEraseAndHisDecoyStandsForHim(GameTestHelper helper) {
        Scene scene = new Scene(helper, 24, 14);
        ServerPlayer eraser = scene.holder("kc-two-eraser", 2.5D, 1.5D);
        ServerPlayer other = scene.holder("kc-two-other", 6.5D, 6.5D);
        scene.listed(eraser, other);
        Pig pig = scene.fixture.track(EntityType.PIG.create(helper.getLevel()));
        pig.setNoAi(true);
        pig.setPos(helper.absoluteVec(new Vec3(6.5D, 2.0D, 11.5D)));
        helper.getLevel().addFreshEntity(pig);
        KingCrimsonTimeEraseDecoyEntity[] decoy = new KingCrimsonTimeEraseDecoyEntity[1];
        Vec3[] decoyStart = new Vec3[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() ->
                helper.assertTrue(scene.fixture.holdErase(eraser).isActionSuccess, "Setup: the first erase did not start")));
        // He walks 2 blocks east in the erase of the other and is seen 0.6 ahead; then he stands, and erases himself.
        scene.walk(() -> other, 2 + BEGUN, 10, STEP, 0.0D);
        helper.runAfterDelay(18 + BEGUN, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(eraser), "Setup: the first holder does not erase");
            helper.assertTrue(KingCrimsonEraseShiftGameTests.near(offset(eraser, other), new Vec3(0.6D, 0.0D, 0.0D)),
                    "Setup: before he erases he is seen " + offset(eraser, other) + " from where he is, not 0.6 east");
            helper.assertTrue(scene.fixture.holdErase(other).isActionSuccess, "Setup: the second erase did not start");
        }));
        helper.runAfterDelay(18 + 2 * BEGUN, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(other) && scene.fixture.decoys(other).size() == 1,
                    "Setup: the second holder does not erase with one decoy");
            decoy[0] = scene.fixture.decoys(other).get(0);
            decoyStart[0] = decoy[0].position();
            helper.assertTrue(KingCrimsonEraseShiftGameTests.near(decoyStart[0], other.position()),
                    "Setup: the decoy stands " + decoyStart[0].subtract(other.position()) + " from its player, who stood still");
            scene.rule(KingCrimsonEraseShiftGameTests.near(offset(eraser, other), Vec3.ZERO),
                    "one who erases is seen by the other eraser " + offset(eraser, other) + " from where he is: shifted");
            scene.rule(KingCrimsonEraseShiftGameTests.near(offset(eraser, decoy[0]), new Vec3(0.6D, 0.0D, 0.0D)),
                    "the decoy of one who was seen 0.6 east of where he was is seen " + offset(eraser, decoy[0])
                            + " from where it is: it does not go on from his image");
            TimeEraseShiftPayload last = lastTold(eraser);
            scene.rule(last != null && imageIn(last, decoy[0]) != null && imageIn(last, other) == null,
                    "the client of the eraser is not told the image of the decoy and nothing of its player: " + last);
        }));
        // Both move 2 blocks, the decoy to the south: the decoy alone is seen further ahead.
        scene.walk(() -> other, 20 + 2 * BEGUN, 10, STEP, 0.0D);
        scene.walk(() -> decoy[0], 20 + 2 * BEGUN, 10, 0.0D, STEP);
        helper.runAfterDelay(34 + 2 * BEGUN, () -> scene.fixture.guarded(() -> {
            Vec3 went = decoy[0].position().subtract(decoyStart[0]);
            helper.assertTrue(Math.abs(went.z - 2.0D) < 1.0E-6D, "Setup: the decoy went " + went + ", not 2 blocks south");
            Vec3 ahead = new Vec3(0.6D, 0.0D, 0.0D).add(went.scale(0.3D));
            scene.rule(KingCrimsonEraseShiftGameTests.near(offset(eraser, decoy[0]), ahead),
                    "the decoy that went " + went + " is seen " + offset(eraser, decoy[0]) + " from where it is, not "
                            + ahead + ": it is not shifted as the player it stands for");
            scene.rule(KingCrimsonEraseShiftGameTests.near(offset(eraser, other), Vec3.ZERO),
                    "one who erases and walked 2 blocks is seen " + offset(eraser, other) + " from where he is");
            Vec3 image = KingCrimsonTimeEraseShiftService.imageOf(eraser, decoy[0]);
            Vec3 otherAt = other.position();
            // Thrown blood is thrown at what the eraser sees: the decoy on its image, and not the one who erases.
            eraser.lookAt(EntityAnchorArgument.Anchor.EYES, otherAt.add(0.0D, 0.9D, 0.0D));
            boolean atHim = KingCrimsonBloodThrowService.hasTarget(eraser);
            eraser.lookAt(EntityAnchorArgument.Anchor.EYES, image.add(0.0D, 0.9D, 0.0D));
            boolean atHisDecoy = KingCrimsonBloodThrowService.hasTarget(eraser);
            scene.rule(atHisDecoy && !atHim, "the thrown blood of an eraser finds the decoy of another eraser "
                    + (atHisDecoy ? "on its image" : "not at all") + ", and the one who erases "
                    + (atHim ? "where he is" : "not at all"));
            int otherSent = KingCrimsonEraseFixture.listener(other).sentCount();
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            helper.assertTrue(!KingCrimsonEraseFixture.erasing(eraser) && MobAiPause.remainingTicks(pig) > 0,
                    "Setup: the first erase did not end and daze the pig: " + MobAiPause.remainingTicks(pig));
            scene.rule(KingCrimsonEraseFixture.erasing(other), "the end of an erase ended the erase of another holder");
            scene.rule(KingCrimsonEraseShiftGameTests.near(other.position(), otherAt)
                            && KingCrimsonEraseShiftGameTests.moves(other, otherSent).isEmpty(),
                    "the end of an erase moved one who erases himself by " + other.position().subtract(otherAt));
            scene.rule(KingCrimsonEraseShiftGameTests.near(decoy[0].position(), image),
                    "at the end the decoy of the other eraser stands " + decoy[0].position().subtract(image)
                            + " from the image the eraser last saw of it");
            scene.rule(MobAiPause.remainingTicks(decoy[0]) == 0, "the end of an erase dazed the decoy of another eraser for "
                    + MobAiPause.remainingTicks(decoy[0]) + " ticks");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "kc_two_afterimage")
    public static void anEraserIsShownNothingOfOneWhoErasesButHisDecoy(GameTestHelper helper) {
        Scene scene = new Scene(helper, 20, 12);
        ServerPlayer hidden = scene.holder("kc-two-hidden", 6.5D, 6.5D);
        ServerPlayer eraser = scene.holder("kc-two-seer", 2.5D, 1.5D);
        scene.listed(hidden, eraser);
        int[] sent = new int[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() ->
                helper.assertTrue(scene.fixture.holdErase(hidden).isActionSuccess, "Setup: the first erase did not start")));
        helper.runAfterDelay(2 + BEGUN, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(hidden) && scene.fixture.decoys(hidden).size() == 1,
                    "Setup: the first holder does not erase with one decoy");
            sent[0] = KingCrimsonEraseFixture.listener(eraser).sentCount();
            helper.assertTrue(scene.fixture.holdErase(eraser).isActionSuccess, "Setup: the second erase did not start");
        }));
        helper.runAfterDelay(2 + 2 * BEGUN, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(eraser), "Setup: the second holder does not erase");
            KingCrimsonTimeEraseDecoyEntity decoy = scene.fixture.decoys(hidden).get(0);
            StandEntity stand = StandPower.get(hidden).getSummonedStandEntity();
            List<Integer> origins = afterimageOrigins(eraser, sent[0]);
            helper.assertTrue(stand != null && !origins.isEmpty(), "Setup: no Stand of the hidden holder, or no afterimage "
                    + "at all for the eraser: " + origins);
            scene.rule(!origins.contains(hidden.getId()) && !origins.contains(stand.getId()),
                    "an eraser is given an afterimage of one who erases, or of his hidden Stand");
            TimeEraseShiftPayload last = lastTold(eraser);
            scene.rule(!origins.contains(decoy.getId()) && last != null && imageIn(last, decoy) != null,
                    "the decoy of another eraser is not shown as a shifted player is: by its image, with no trailing "
                            + "afterimage");
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            // With the shifted image switched off a player has a trailing afterimage, and so has the decoy of one.
            KingCrimsonConfig.TIME_ERASE_SHIFT_PLAYERS.set(false);
        }));
        helper.runAfterDelay(6 + 2 * BEGUN, () -> scene.fixture.guarded(() -> {
            StandPower.get(eraser).setAbilityCooldown("kingcrimson_timeerase", 0, 0);
            sent[0] = KingCrimsonEraseFixture.listener(eraser).sentCount();
            helper.assertTrue(scene.fixture.holdErase(eraser).isActionSuccess, "Setup: the erase did not start again");
        }));
        helper.runAfterDelay(6 + 3 * BEGUN, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(eraser) && KingCrimsonEraseFixture.erasing(hidden),
                    "Setup: the two holders do not erase");
            KingCrimsonTimeEraseDecoyEntity decoy = scene.fixture.decoys(hidden).get(0);
            List<Integer> origins = afterimageOrigins(eraser, sent[0]);
            scene.rule(origins.contains(decoy.getId()), "with the shifted image off the decoy of another eraser has no "
                    + "trailing afterimage, as a player has");
            scene.rule(!origins.contains(hidden.getId()), "with the shifted image off an eraser is given an afterimage of "
                    + "one who erases");
            scene.finish();
        }));
    }

    private static Vec3 offset(ServerPlayer eraser, LivingEntity body) {
        return KingCrimsonTimeEraseShiftService.imageOf(eraser, body).subtract(body.position());
    }

    private static List<Integer> afterimageOrigins(ServerPlayer eraser, int since) {
        return KingCrimsonEraseFixture.listener(eraser).payloadsSince(since, TimeEraseAfterimageSpawnPayload.class).stream()
                .map(TimeEraseAfterimageSpawnPayload::originEntityId).toList();
    }

    private static TimeEraseShiftPayload lastTold(ServerPlayer eraser) {
        List<TimeEraseShiftPayload> told = KingCrimsonEraseFixture.listener(eraser).payloads(TimeEraseShiftPayload.class);
        return told.isEmpty() ? null : told.get(told.size() - 1);
    }

    private static Vec3 imageIn(TimeEraseShiftPayload payload, Entity entity) {
        for (TimeEraseShiftPayload.Image image : payload.images()) {
            if (image.entityId() == entity.getId()) {
                return image.position();
            }
        }
        return null;
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final List<String> broken = new ArrayList<>();

        private Scene(GameTestHelper helper, int length, int width) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(length, width);
            boolean shifts = KingCrimsonConfig.TIME_ERASE_SHIFT_PLAYERS.get();
            fixture.onClose(() -> KingCrimsonConfig.TIME_ERASE_SHIFT_PLAYERS.set(shifts));
        }

        /** A holder of King Crimson at the top Resolve level, his Stand out, whose bar never runs low. */
        private ServerPlayer holder(String name, double x, double z) {
            ServerPlayer player = fixture.player(name, x, 2.0D, z);
            player.setOnGround(true);
            StandPower power = fixture.kingCrimson(player, 4);
            helper.onEachTick(() -> {
                if (!player.isRemoved()) {
                    power.setStamina(power.getMaxStamina());
                }
            });
            return player;
        }

        /** On the server's own list of players, as every real player is, until the test closes. */
        private void listed(ServerPlayer... players) {
            List<ServerPlayer> list = ((PlayerListAccessor) helper.getLevel().getServer().getPlayerList()).kingCrimson$players();
            List<ServerPlayer> added = List.of(players);
            list.addAll(added);
            fixture.onClose(() -> list.removeAll(added));
        }

        /** It is moved by this much in each of so many ticks, as the server sees a walking body move. */
        private void walk(Supplier<? extends Entity> body, int fromTick, int ticks, double dx, double dz) {
            for (int tick = 0; tick < ticks; tick++) {
                helper.runAfterDelay(fromTick + tick, () -> {
                    Entity who = body.get();
                    if (who != null && !who.isRemoved()) {
                        who.setPos(who.getX() + dx, who.getY(), who.getZ() + dz);
                    }
                });
            }
        }

        private void rule(boolean holds, String broke) {
            if (!holds) {
                broken.add(broke);
            }
        }

        private void finish() {
            // The report of a failed test is kept in a book: a long list of broken rules is cut.
            String rules = String.join("; ", broken.subList(0, Math.min(5, broken.size())));
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(),
                    "Two erasers: " + (rules.length() > 1200 ? rules.substring(0, 1200) + "..." : rules)));
            fixture.succeed();
        }
    }
}
