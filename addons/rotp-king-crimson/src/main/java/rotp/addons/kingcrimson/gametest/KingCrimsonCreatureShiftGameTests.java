package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.network.TimeEraseShiftPayload;
import rotp.addons.kingcrimson.state.KingCrimsonBloodThrowService;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseShiftService;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Owner 2026-11-11: "現在多人模式下，非玩家的其他生物在時刪中也按照運動漂移後的假身計算，單人模式普通生物邏輯不變", bounded by his answer on
 * its cost: "允許隨機抽固定數量的玩家周圍最近的生物做運動漂移，其他不變". With more than one player online an erase shifts, for each
 * player, the setting's number of creatures drawn at random among twice as many of the nearest to him, and puts them on
 * their images at its end; with one player online it shifts no creature. The creatures here stand still by themselves
 * and are moved as a server sees a walking creature move.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonCreatureShiftGameTests {
    private static final int BEGUN = 10;
    private static final double STEP = 0.2D;

    private KingCrimsonCreatureShiftGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 220, batch = "kc_creature_draw")
    public static void withSeveralPlayersOnlineSoManyOfTheCreaturesNearestToAPlayerAreShiftedAsHeIs(GameTestHelper helper) {
        Scene scene = new Scene(helper, 62, 24);
        KingCrimsonConfig.TIME_ERASE_SHIFT_CREATURES.set(3);
        // The eraser stands 36 blocks and more from the twelve creatures round the other player, and has two of his own.
        ServerPlayer eraser = scene.eraser(2.5D, 12.5D);
        List<Pig> own = List.of(scene.pig(6.5D, 10.5D), scene.pig(6.5D, 14.5D));
        ServerPlayer other = scene.player("kc-creature-other", 50.5D, 12.5D);
        List<Pig> nearest = List.of(scene.pig(47.5D, 9.5D), scene.pig(47.5D, 15.5D), scene.pig(53.5D, 9.5D),
                scene.pig(53.5D, 15.5D), scene.pig(50.5D, 8.0D), scene.pig(50.5D, 17.0D));
        List<Pig> further = List.of(scene.pig(38.5D, 6.5D), scene.pig(38.5D, 18.5D), scene.pig(40.5D, 12.5D),
                scene.pig(44.5D, 2.5D), scene.pig(44.5D, 22.5D), scene.pig(38.5D, 12.5D));
        List<Pig> all = new ArrayList<>(nearest);
        all.addAll(further);
        List<Set<Integer>> draws = new ArrayList<>();
        int[] sent = new int[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            sent[0] = KingCrimsonEraseFixture.listener(other).sentCount();
            scene.press(eraser);
        }));
        for (Pig pig : all) {
            scene.walk(pig, 2 + BEGUN, 10, STEP, 0.0D);
        }
        for (Pig pig : own) {
            scene.walk(pig, 2 + BEGUN, 10, STEP, 0.0D);
        }
        helper.runAfterDelay(16 + BEGUN, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(eraser), "Setup: no erase is running");
            scene.rule(KingCrimsonEraseShiftGameTests.near(offset(eraser, own.get(0)), new Vec3(0.6D, 0.0D, 0.0D))
                            && KingCrimsonEraseShiftGameTests.near(offset(eraser, own.get(1)), new Vec3(0.6D, 0.0D, 0.0D)),
                    "the two creatures nearest to the eraser himself are seen " + offset(eraser, own.get(0)) + " and "
                            + offset(eraser, own.get(1)) + " from where they are: he is not one of the players");
            List<Pig> shifted = new ArrayList<>();
            List<Pig> odd = new ArrayList<>();
            for (Pig pig : all) {
                Vec3 offset = offset(eraser, pig);
                if (KingCrimsonEraseShiftGameTests.near(offset, new Vec3(0.6D, 0.0D, 0.0D))) {
                    shifted.add(pig);
                } else if (!KingCrimsonEraseShiftGameTests.near(offset, Vec3.ZERO)) {
                    odd.add(pig);
                }
            }
            scene.rule(shifted.size() == 3 && odd.isEmpty(), "with twelve creatures round a player and the setting at 3, "
                    + shifted.size() + " of them are seen 0.6 of their 2 blocks ahead and " + odd.size()
                    + " somewhere else: not exactly three");
            scene.rule(nearest.containsAll(shifted), "a creature that is not among the six nearest to the player was drawn");
            Set<Integer> told = toldCreatures(eraser, all);
            scene.rule(told.equals(ids(shifted)), "the client of the eraser is told the images of the creatures " + told
                    + ", and those drawn are " + ids(shifted));
            List<String> sentOther = KingCrimsonEraseShiftGameTests.oursSince(other, sent[0]);
            scene.rule(sentOther.isEmpty(), "the player whose creatures drift was sent, before the erase ended: " + sentOther);
            List<Vec3> images = shifted.stream().map(pig -> KingCrimsonTimeEraseShiftService.imageOf(eraser, pig)).toList();
            List<Pig> left = new ArrayList<>(all);
            left.removeAll(shifted);
            List<Vec3> places = left.stream().map(Entity::position).toList();
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            for (int i = 0; i < shifted.size(); i++) {
                scene.rule(KingCrimsonEraseShiftGameTests.near(shifted.get(i).position(), images.get(i)),
                        "at the end a drawn creature stands " + shifted.get(i).position().subtract(images.get(i))
                                + " from the image the eraser last saw of it");
            }
            for (int i = 0; i < left.size(); i++) {
                scene.rule(KingCrimsonEraseShiftGameTests.near(left.get(i).position(), places.get(i)),
                        "the end moved a creature that was not drawn by " + left.get(i).position().subtract(places.get(i)));
            }
        }));
        // Six more erases, between which nothing moves: who is drawn is not always the same.
        for (int round = 0; round < 6; round++) {
            int from = 22 + BEGUN + round * 16;
            helper.runAfterDelay(from, () -> scene.fixture.guarded(() -> scene.pressAgain(eraser)));
            helper.runAfterDelay(from + BEGUN + 2, () -> scene.fixture.guarded(() -> {
                helper.assertTrue(KingCrimsonEraseFixture.erasing(eraser), "Setup: no erase is running");
                Set<Integer> told = toldCreatures(eraser, all);
                scene.rule(told.size() == 3 && ids(nearest).containsAll(told), "a later erase drew the creatures " + told
                        + ": not three of the six nearest " + ids(nearest));
                draws.add(told);
                scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            }));
        }
        int last = 22 + BEGUN + 6 * 16;
        helper.runAfterDelay(last, () -> scene.fixture.guarded(() -> {
            scene.rule(new HashSet<>(draws).size() > 1, "six erases drew the same three creatures each time: " + draws);
            KingCrimsonConfig.TIME_ERASE_SHIFT_CREATURES.set(0);
            scene.pressAgain(eraser);
        }));
        helper.runAfterDelay(last + BEGUN + 2, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(eraser), "Setup: no erase is running");
            scene.rule(toldCreatures(eraser, all).isEmpty(), "with the setting at 0 the creatures "
                    + toldCreatures(eraser, all) + " are shifted");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_creature_solo")
    public static void withOnePlayerOnlineNoCreatureIsShifted(GameTestHelper helper) {
        Scene scene = new Scene(helper, 20, 12);
        ServerPlayer eraser = scene.eraser(2.5D, 6.5D);
        List<Pig> pigs = List.of(scene.pig(6.5D, 3.5D), scene.pig(6.5D, 6.5D), scene.pig(6.5D, 9.5D), scene.pig(9.5D, 6.5D));
        int[] sent = new int[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(EpitaphSessions.isAlone(helper.getLevel().getServer()), "Setup: more than one player is online");
            sent[0] = KingCrimsonEraseFixture.listener(eraser).sentCount();
            scene.press(eraser);
        }));
        for (Pig pig : pigs) {
            scene.walk(pig, 2 + BEGUN, 10, STEP, 0.0D);
        }
        helper.runAfterDelay(16 + BEGUN, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(eraser), "Setup: no erase is running");
            for (Pig pig : pigs) {
                scene.rule(KingCrimsonEraseShiftGameTests.near(offset(eraser, pig), Vec3.ZERO), "with one player online a "
                        + "creature that walked 2 blocks is seen " + offset(eraser, pig) + " from where it is");
            }
            scene.rule(KingCrimsonEraseFixture.listener(eraser).payloadsSince(sent[0], TimeEraseShiftPayload.class).isEmpty(),
                    "with one player online the client of the eraser is told of images");
            List<Vec3> places = pigs.stream().map(Entity::position).toList();
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            for (int i = 0; i < pigs.size(); i++) {
                scene.rule(KingCrimsonEraseShiftGameTests.near(pigs.get(i).position(), places.get(i)),
                        "with one player online the end of an erase moved a creature");
            }
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_creature_rider")
    public static void aCreatureThatCarriesAPlayerStaysAndThrownBloodFindsAShiftedCreatureOnItsImage(GameTestHelper helper) {
        Scene scene = new Scene(helper, 24, 14);
        // A whole block for each block, so that an image stands clear of its creature.
        KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.set(1.0D);
        ServerPlayer eraser = scene.eraser(2.5D, 6.5D);
        scene.player("kc-creature-second", 18.5D, 12.5D);
        ServerPlayer rider = scene.player("kc-creature-rider", 10.5D, 2.5D);
        Pig mount = scene.pig(10.5D, 2.5D);
        Pig free = scene.pig(10.5D, 6.5D);
        Vec3[] riderAt = new Vec3[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(rider.startRiding(mount, true), "Setup: the rider did not mount");
            scene.press(eraser);
        }));
        helper.runAfterDelay(1 + BEGUN, () -> riderAt[0] = rider.position());
        scene.walk(mount, 2 + BEGUN, 10, STEP, 0.0D);
        scene.walk(free, 2 + BEGUN, 10, 0.0D, STEP);
        helper.runAfterDelay(16 + BEGUN, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(eraser) && rider.getVehicle() == mount
                            && rider.getX() - riderAt[0].x > 1.9D, "Setup: no erase runs, or the rider was not carried 2 blocks");
            scene.rule(KingCrimsonEraseShiftGameTests.near(offset(eraser, free), new Vec3(0.0D, 0.0D, 2.0D)),
                    "a creature that walked 2 blocks south is seen " + offset(eraser, free) + " from where it is, not 2 south");
            scene.rule(KingCrimsonEraseShiftGameTests.near(offset(eraser, mount), Vec3.ZERO),
                    "a creature that carried a player 2 blocks is seen " + offset(eraser, mount) + " from where it is");
            // Thrown blood is thrown at what the eraser sees.
            Vec3 image = KingCrimsonTimeEraseShiftService.imageOf(eraser, free);
            eraser.lookAt(EntityAnchorArgument.Anchor.EYES, free.position().add(0.0D, 0.45D, 0.0D));
            boolean atItsPlace = KingCrimsonBloodThrowService.hasTarget(eraser);
            eraser.lookAt(EntityAnchorArgument.Anchor.EYES, image.add(0.0D, 0.45D, 0.0D));
            boolean atItsImage = KingCrimsonBloodThrowService.hasTarget(eraser);
            scene.rule(atItsImage && !atItsPlace, "thrown blood finds a shifted creature "
                    + (atItsImage ? "on its image" : "not on its image") + " and "
                    + (atItsPlace ? "where it is" : "not where it is"));
            Vec3 mountAt = mount.position();
            Vec3 carried = rider.position();
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            scene.rule(KingCrimsonEraseShiftGameTests.near(free.position(), image), "at the end the creature stands "
                    + free.position().subtract(image) + " from its image");
            scene.rule(rider.getVehicle() == mount && KingCrimsonEraseShiftGameTests.near(mount.position(), mountAt)
                            && KingCrimsonEraseShiftGameTests.near(rider.position(), carried),
                    "the end moved a creature that carries a player by " + mount.position().subtract(mountAt)
                            + ", or took him off it");
            scene.finish();
        }));
    }

    // The analogue of T6 for a creature: what a prediction holds on its track is seen where it is and is put nowhere.
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_creature_track")
    public static void aCreatureOnTheTrackOfAPredictionIsNotShifted(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-creature-track", 4, 28, 12);
        fixture.budget(0);
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        ServerPlayer user = fixture.user;
        // A second player, further from the zombie than the user: with him online an erase shifts creatures.
        fixture.other("kc-creature-track-him", 24.5D, 9.5D);
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D));
        Pig pig = fixture.mob(EntityType.PIG, 8.5D, 8.5D);
        pig.setNoAi(true);
        EpitaphSession[] running = new EpitaphSession[1];
        Vec3[] from = new Vec3[1];
        // A second in: the zombie walks at the user by now, and its track goes on from there.
        helper.runAfterDelay(25, () -> fixture.guarded(() -> {
            running[0] = fixture.press(null);
            helper.assertTrue(EpitaphSessions.isOnTrack(zombie) && !EpitaphSessions.isOnTrack(pig),
                    "Setup: the zombie that hunts the user is on no track, or the pig is on one");
        }));
        helper.runAfterDelay(28, () -> fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(user), "Setup: the erase could not begin");
            from[0] = zombie.position();
        }));
        for (int tick = 0; tick < 10; tick++) {
            helper.runAfterDelay(30 + tick, () -> pig.setPos(pig.getX(), pig.getY(), pig.getZ() + STEP));
        }
        helper.runAfterDelay(48, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            double went = zombie.position().distanceTo(from[0]);
            helper.assertTrue(KingCrimsonEraseFixture.erasing(user) && !session.ended() && EpitaphSessions.isOnTrack(zombie)
                    && went > 0.5D, "Setup: twenty ticks into the erase the user does not erase, or the zombie is off its "
                    + "track, or it went only " + went + " blocks");
            helper.assertTrue(KingCrimsonEraseShiftGameTests.near(offset(user, pig), new Vec3(0.0D, 0.0D, 0.6D)),
                    "Setup: a creature no prediction holds is seen " + offset(user, pig) + " from where it is, not 0.6 south");
            double shifted = offset(user, zombie).length();
            fixture.rule(shifted < 1.0E-6D, "a creature that went " + went + " blocks on the track of a prediction is seen "
                    + shifted + " blocks from where it is by the one who erases");
            Vec3 before = zombie.position();
            KingCrimsonTemporalStateService.terminate(user);
            double moved = zombie.position().distanceTo(before);
            fixture.rule(moved < 1.0E-6D, "the end of the erase moved a creature on its track " + moved + " blocks");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A creature a prediction holds is not shifted by an erase");
        }));
    }

    private static Vec3 offset(ServerPlayer eraser, LivingEntity body) {
        return KingCrimsonTimeEraseShiftService.imageOf(eraser, body).subtract(body.position());
    }

    private static Set<Integer> ids(List<? extends Entity> entities) {
        Set<Integer> ids = new HashSet<>();
        for (Entity entity : entities) {
            ids.add(entity.getId());
        }
        return ids;
    }

    /** The creatures the eraser's client was told an image of in the last tick. */
    private static Set<Integer> toldCreatures(ServerPlayer eraser, List<? extends Entity> creatures) {
        List<TimeEraseShiftPayload> told = KingCrimsonEraseFixture.listener(eraser).payloads(TimeEraseShiftPayload.class);
        Set<Integer> named = new HashSet<>();
        if (!told.isEmpty()) {
            for (TimeEraseShiftPayload.Image image : told.get(told.size() - 1).images()) {
                named.add(image.entityId());
            }
        }
        named.retainAll(ids(creatures));
        return named;
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final List<String> broken = new ArrayList<>();

        private Scene(GameTestHelper helper, int length, int width) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(length, width);
            int creatures = KingCrimsonConfig.TIME_ERASE_SHIFT_CREATURES.get();
            double perMove = KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.get();
            fixture.onClose(() -> {
                KingCrimsonConfig.TIME_ERASE_SHIFT_CREATURES.set(creatures);
                KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.set(perMove);
            });
        }

        private ServerPlayer eraser(double x, double z) {
            ServerPlayer player = player("kc-creature-eraser", x, z);
            StandPower power = fixture.kingCrimson(player, 4);
            helper.onEachTick(() -> {
                if (!player.isRemoved()) {
                    power.setStamina(power.getMaxStamina());
                }
            });
            return player;
        }

        private ServerPlayer player(String name, double x, double z) {
            ServerPlayer player = fixture.player(name, x, 2.0D, z);
            player.setOnGround(true);
            return player;
        }

        /** A pig that stands where it is put: it goes only where the test moves it. */
        private Pig pig(double x, double z) {
            Pig pig = fixture.track(EntityType.PIG.create(helper.getLevel()));
            pig.setNoAi(true);
            pig.setPersistenceRequired();
            pig.setPos(helper.absoluteVec(new Vec3(x, 2.0D, z)));
            helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Setup: a pig was not added");
            return pig;
        }

        private void walk(Entity who, int fromTick, int ticks, double dx, double dz) {
            for (int tick = 0; tick < ticks; tick++) {
                helper.runAfterDelay(fromTick + tick, () -> {
                    if (!who.isRemoved()) {
                        who.setPos(who.getX() + dx, who.getY(), who.getZ() + dz);
                    }
                });
            }
        }

        private void press(ServerPlayer eraser) {
            helper.assertTrue(fixture.holdErase(eraser).isActionSuccess, "Setup: the held Time Erase did not start");
        }

        private void pressAgain(ServerPlayer eraser) {
            StandPower.get(eraser).setAbilityCooldown("kingcrimson_timeerase", 0, 0);
            press(eraser);
        }

        private void rule(boolean holds, String broke) {
            if (!holds && !broken.contains(broke)) {
                broken.add(broke);
            }
        }

        private void finish() {
            // The report of a failed test is kept in a book: a long list of broken rules is cut.
            String rules = String.join("; ", broken.subList(0, Math.min(5, broken.size())));
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(),
                    "Shifted creatures: " + (rules.length() > 1200 ? rules.substring(0, 1200) + "..." : rules)));
            fixture.succeed();
        }
    }
}
