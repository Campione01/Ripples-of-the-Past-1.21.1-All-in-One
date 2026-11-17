package rotp.addons.kingcrimson.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase3Spec;
import rotp.addons.kingcrimson.ability.KingCrimsonEpitaphAbility;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.EpitaphTargeting;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Epitaph, what a press does with what it finds under the crosshair (the decision of the owner on a rider, and on the
 * refund of a press and on the fake player of a mod), and a walked player who leaves the level.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphPressGameTests {
    private static final int PRESS_AT = 5;
    private static final short EPITAPH_KEY = 31;
    private static final String EPITAPH = "kingcrimson_epitaph";

    private KingCrimsonEpitaphPressGameTests() {}

    // The passive erase would answer a foreseen hit, and a walked player heals at a pace of his own: neither is the matter here.
    private static void quiet(KingCrimsonEpitaphFixture fixture) {
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        fixture.steadyHealth();
    }

    // Players hurt players in this test, whatever the server of the tests is set to.
    private static void playersFight(KingCrimsonEpitaphFixture fixture) {
        MinecraftServer server = fixture.level.getServer();
        boolean allowed = server.isPvpAllowed();
        fixture.base.onClose(() -> server.setPvpAllowed(allowed));
        server.setPvpAllowed(true);
    }

    // A second player, ticked as a connection ticks one: he stays where the server last put him.
    private static ServerPlayer enemy(KingCrimsonEpitaphFixture fixture, String name, double x, double z) {
        ServerPlayer enemy = fixture.other(name, x, z);
        fixture.base.tickAsConnection(enemy);
        return enemy;
    }

    // The owner, 2026-11-11: a rider under the crosshair and what he rides are one target at one cost. The phantom of the
    // rider rides the phantom of his mount, which goes where he steers it; in the walk the real mount carries him.
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_press_rider")
    public static void aRiderAndHisMountAreForeseenAndWalkedAsOne(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-rider", 4, 24, 8);
        fixture.budget(0);
        quiet(fixture);
        playersFight(fixture);
        ServerPlayer user = fixture.user;
        StandPower power = fixture.power;
        // Straight ahead of the user, on a horse that answers to its rider.
        ServerPlayer enemy = enemy(fixture, "kc-ep-rider-him", 16.5D, 2.5D);
        Horse mount = fixture.mob(EntityType.HORSE, 16.5D, 2.5D);
        mount.setTamed(true);
        mount.equipSaddle(new ItemStack(Items.SADDLE), null);
        mount.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2D);
        String title = "A rider and his mount are foreseen and walked as one";
        EpitaphSession[] running = new EpitaphSession[1];
        int[] seen = new int[1];
        helper.runAfterDelay(PRESS_AT - 2, () -> fixture.guarded(
                () -> helper.assertTrue(enemy.startRiding(mount, true), "Setup: the player did not mount the horse")));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            Entity aimed = EpitaphTargeting.crosshair(user, KingCrimsonConfig.epitaphRadius());
            helper.assertTrue(enemy.getVehicle() == mount && mount.getControllingPassenger() == enemy, "Setup: the player does not"
                    + " ride the horse, or it does not answer to him");
            fixture.rule(aimed == enemy, "with a rider and his horse straight ahead, under the crosshair of the user is " + aimed);
            float dear = KingCrimsonConfig.epitaphPlayerStamina();
            float before = power.getStamina();
            boolean went = fixture.base.press(user, EPITAPH, EPITAPH_KEY, InputMethod.CLICK, false).isActionSuccess;
            EpitaphSession session = EpitaphSessions.of(user);
            helper.assertTrue(went && session != null, "Setup: the press with a rider under the crosshair began no prediction");
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            session.foreseeAll();
            EntityTrack rider = session.record().track(enemy.getId());
            EntityTrack ridden = session.record().track(mount.getId());
            fixture.rule(session.aimsAt(enemy) && rider != null && ridden != null, "a player who rides under the crosshair is "
                    + (session.aimsAt(enemy) ? "aimed at" : "no target") + " and has " + (rider != null ? "a" : "no")
                    + " track; what he rides has " + (ridden != null ? "a" : "no") + " track");
            float took = before - power.getStamina();
            fixture.rule(Math.abs(took - dear) < 0.01F, "the press at a rider and his mount took " + took + " stamina, not the " + dear
                    + " of one press at a player");
            fixture.rule(enemy.getVehicle() == mount && EpitaphPredictions.isForcedWalking(enemy) && EpitaphSessions.isOnTrack(mount),
                    "after the press the rider " + (enemy.getVehicle() == mount ? "rides on" : "is off his mount") + " and is "
                            + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked") + ", and his mount is "
                            + (EpitaphSessions.isOnTrack(mount) ? "carried" : "free"));
            if (rider == null || ridden == null) {
                session.end(EpitaphEnd.CANCELLED);
                fixture.finish(title);
                return;
            }
            // In the room: he sits on it for as long as both are there, and it goes where he wants to go: at the user.
            int both = Math.min(15, Math.min(rider.lastTick(), ridden.lastTick()));
            double apart = 0.0D;
            for (int tick = 0; tick <= both; tick++) {
                Vec3 seat = rider.frame(tick).position().subtract(ridden.frame(tick).position());
                apart = Math.max(apart, Math.sqrt(seat.x * seat.x + seat.z * seat.z));
            }
            fixture.rule(both >= 10 && apart < 0.3D, "in the first " + both + " ticks of the future the phantom of the rider was up to "
                    + apart + " blocks beside the phantom of his mount: he does not sit on it");
            Vec3 from = ridden.frame(0).position();
            Vec3 to = ridden.frame(both).position();
            fixture.rule(from.x - to.x > 3.0D, "in " + both + " ticks the phantom of the horse went from "
                    + KingCrimsonEpitaphFixture.at(from) + " to " + KingCrimsonEpitaphFixture.at(to) + ": its rider, who is after"
                    + " the user 14 blocks away, did not ride it there");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.ended()) {
                return;
            }
            // For real: the horse is carried along its track, and carries him.
            double tick = session.clock().body();
            double his = enemy.position().distanceTo(KingCrimsonEpitaphFixture.on(session.record().track(enemy.getId()), tick));
            double its = mount.position().distanceTo(KingCrimsonEpitaphFixture.on(session.record().track(mount.getId()), tick));
            fixture.rule(enemy.getVehicle() == mount && his < 0.05D && its < 1.0E-6D, "at tick " + tick + " of their tracks the rider "
                    + (enemy.getVehicle() == mount ? "rides" : "does not ride") + " his horse, " + his + " blocks from where his"
                    + " phantom was then, and the horse stands " + its + " blocks from where its phantom was");
            if (++seen[0] == 10) {
                running[0] = null;
                session.end(EpitaphEnd.CANCELLED);
                fixture.finish(title);
            }
        }));
    }

    // The owner, 2026-11-11: the mount of a rider goes where he steers it and nowhere by itself. A pig does not answer to a
    // rider without the rod: left alone it walks to whoever holds a carrot, with him on its back; its phantom stands.
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_press_rider_pig")
    public static void theMountOfARiderGoesNowhereByItself(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-pig", 4, 24, 8);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT));
        ServerPlayer enemy = enemy(fixture, "kc-ep-pig-him", 11.5D, 2.5D);
        Pig mount = fixture.mob(EntityType.PIG, 11.5D, 2.5D);
        Vec3[] began = new Vec3[1];
        helper.runAfterDelay(2, () -> fixture.guarded(() -> {
            helper.assertTrue(enemy.startRiding(mount, true), "Setup: the player did not mount the pig");
            began[0] = mount.position();
        }));
        helper.runAfterDelay(22, () -> fixture.guarded(() -> {
            // The control: what the pig itself does with him on its back.
            double alone = began[0].x - mount.getX();
            helper.assertTrue(enemy.getVehicle() == mount && mount.getControllingPassenger() == null && alone > 1.0D, "Setup: in 20"
                    + " ticks the pig with the player on its back went " + alone + " blocks towards the carrot of the user");
            EpitaphSession session = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            session.foreseeAll();
            EntityTrack ridden = session.record().track(mount.getId());
            EntityTrack rider = session.record().track(enemy.getId());
            helper.assertTrue(ridden != null && rider != null && ridden.lastTick() >= 20, "Setup: the pig or its rider has no track of"
                    + " twenty ticks");
            double went = ridden.frame(20).position().distanceTo(ridden.frame(0).position());
            fixture.rule(went < 0.5D, "the phantom of a pig its rider cannot steer went " + went + " blocks in twenty ticks by itself;"
                    + " the pig had gone " + alone + " in the twenty before the press");
            Vec3 seat = rider.frame(20).position().subtract(ridden.frame(20).position());
            fixture.rule(Math.sqrt(seat.x * seat.x + seat.z * seat.z) < 0.3D, "at tick 20 the phantom of the rider is "
                    + Math.sqrt(seat.x * seat.x + seat.z * seat.z) + " blocks beside the phantom of the pig: he does not sit on it");
            fixture.rule(enemy.getVehicle() == mount && EpitaphPredictions.isForcedWalking(enemy) && EpitaphSessions.isOnTrack(mount),
                    "after the press the rider " + (enemy.getVehicle() == mount ? "rides on" : "is off the pig") + " and is "
                            + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked") + ", and the pig is "
                            + (EpitaphSessions.isOnTrack(mount) ? "carried" : "free"));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The mount of a rider goes nowhere by itself");
        }));
    }

    // 3.6, asked for by the coordinator: a walked player who goes to another dimension goes free, and the prediction goes on
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_press_left")
    public static void aWalkedPlayerWhoLeavesTheLevelGoesFree(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-left", 3, 20, 8);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer mover = enemy(fixture, "kc-ep-left-him", 9.5D, 2.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        boolean[] looked = new boolean[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            running[0] = fixture.predict(mover);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(mover);
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(!session.ended() && EpitaphPredictions.isForcedWalking(mover) && EpitaphPredictions.isForcedWalking(user),
                    "Setup: eight ticks after the press the two players are not both walked");
            Vec3 stood = mover.position();
            // What the game posts when a player has gone through a portal.
            EpitaphSessions.onChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(mover, Level.OVERWORLD, Level.NETHER));
            boolean walked = EpitaphPredictions.isForcedWalking(mover);
            boolean predicted = EpitaphPredictions.isPredictedPlayer(mover);
            fixture.rule(!walked && !predicted && mover.position().distanceTo(stood) < 1.0E-9D, "gone to another dimension in the middle"
                    + " of his walk, the player is " + (walked ? "still walked" : "let go") + ", " + (predicted ? "still" : "no longer")
                    + " predicted, and was moved " + mover.position().distanceTo(stood) + " blocks");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user), "a walked player who left the level ended the"
                    + " prediction (" + session.endedAs() + ") or let its user go");
            looked[0] = true;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || !looked[0] || !session.ended()) {
                return;
            }
            running[0] = null;
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && session.record().failed() == 0, "without the player the prediction"
                    + " ended as " + session.endedAs() + " with " + session.record().failed() + " ops that failed");
            fixture.finish("A walked player who leaves the level goes free, and the prediction goes on");
        }));
    }

    // Asked for by the coordinator: a press that is paid and then begins no prediction is given back
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_press_refund")
    public static void aPressThatBeginsNoPredictionIsGivenBack(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-back", 4, 16, 8);
        ServerPlayer user = fixture.user;
        StandPower power = fixture.power;
        Zombie hunter = fixture.hunter(EntityType.ZOMBIE, 10.5D, 2.5D);
        fixture.base.onClose(() -> EpitaphSessions.failNextStart = false);
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            float before = power.getStamina();
            EpitaphSessions.failNextStart = true;
            BufferingState pressed = fixture.base.press(user, EPITAPH, EPITAPH_KEY, InputMethod.CLICK, false);
            helper.assertTrue(!EpitaphSessions.failNextStart, "Setup: the press never came to the start of a prediction");
            fixture.rule(!pressed.isActionSuccess && EpitaphSessions.of(user) == null && !EpitaphSessions.any, "a prediction that failed as"
                    + " it started: the press " + (pressed.isActionSuccess ? "counts as done" : "counts as not done") + ", and a prediction "
                    + (EpitaphSessions.any ? "runs" : "does not run"));
            fixture.rule(power.getStamina() == before && power.getAbilityCooldown(EPITAPH) <= 0, "a press that began no prediction left"
                    + " the user " + power.getStamina() + " of the " + before + " stamina he had, and he waits "
                    + power.getAbilityCooldown(EPITAPH) + " ticks");
            fixture.rule(!EpitaphSessions.isOnTrack(hunter) && !EpitaphPredictions.isForcedWalking(user)
                    && !user.hasData(KingCrimsonAddon.EPITAPH_FORESEEING), "after a prediction that failed as it started the zombie is "
                            + (EpitaphSessions.isOnTrack(hunter) ? "on a track" : "free") + ", the user "
                            + (EpitaphPredictions.isForcedWalking(user) ? "walked" : "free") + ", and he "
                            + (user.hasData(KingCrimsonAddon.EPITAPH_FORESEEING) ? "still bears" : "does not bear") + " the mark");
            // The next press is a press like any other.
            BufferingState again = fixture.base.press(user, EPITAPH, EPITAPH_KEY, InputMethod.CLICK, false);
            EpitaphSession session = EpitaphSessions.of(user);
            float took = before - power.getStamina();
            fixture.rule(again.isActionSuccess && session != null && KingCrimsonGroundGameTestFixture.near(took,
                    KingCrimsonPhase3Spec.EPITAPH_ACTIVATION_STAMINA), "the press after it " + (session != null ? "began" : "began no")
                            + " prediction and took " + took + " stamina");
            if (session != null) {
                session.end(EpitaphEnd.CANCELLED);
            }
            fixture.finish("A press that begins no prediction is given back");
        }));
    }

    // Decision 75, asked for by the coordinator: the fake player of a mod under the crosshair is no target
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_press_fake")
    public static void aFakePlayerUnderTheCrosshairIsNoTarget(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-fake", 4, 16, 8);
        ServerPlayer user = fixture.user;
        Zombie hunter = fixture.hunter(EntityType.ZOMBIE, 10.5D, 5.5D);
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // As a mod makes one to break blocks or use items in the name of a machine: a player object that is in no list.
            FakePlayer fake = FakePlayerFactory.get(fixture.level, new GameProfile(UUID.randomUUID(), "kc-ep-machine"));
            Vec3 ahead = fixture.spot(6.5D, 2.0D, 2.5D);
            fake.moveTo(ahead.x, ahead.y, ahead.z, 0.0F, 0.0F);
            helper.assertTrue(fake.isAlive() && !fake.isSpectator() && fake.level() == fixture.level,
                    "Setup: the fake player is dead, a spectator or in another level");
            float plain = KingCrimsonConfig.epitaphStamina();
            float cost = KingCrimsonEpitaphAbility.costAt(fake);
            fixture.rule(!EpitaphTargeting.isPlayerTarget(fake) && cost == plain, "a fake player under the crosshair "
                    + (EpitaphTargeting.isPlayerTarget(fake) ? "counts" : "does not count") + " as a player to foresee, and a press at"
                    + " him costs " + cost + ", a press at nothing " + plain);
            boolean chosen = EpitaphTargeting.select(user, fake, KingCrimsonConfig.epitaphRadius(), 24).contains(fake);
            fixture.rule(!chosen, "a fake player under the crosshair is among those a press foresees");
            EpitaphSession session = fixture.press(fake);
            fixture.rule(!session.aimsAt(fake) && session.room().phantom(fake.getId()) == null
                    && session.record().track(hunter.getId()) != null, "the press at a fake player "
                            + (session.aimsAt(fake) ? "is aimed at him" : "is aimed at nobody") + ", he has "
                            + (session.room().phantom(fake.getId()) != null ? "a" : "no") + " phantom, and the zombie that hunts the user is "
                            + (session.record().track(hunter.getId()) != null ? "foreseen" : "not foreseen"));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The fake player of a mod under the crosshair is no target");
        }));
    }
}
