package rotp.addons.kingcrimson.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;

/**
 * Epitaph, a rider on what is no creature (the ruling after stage G, by the rider rule of the owner of 2026-11-11: a
 * rider and what he rides are one unit). A minecart does not answer to its rider: its phantom keeps its run on the
 * rails with him in it. A boat answers to his paddles: its phantom goes where he wants to go. In the walk each carries
 * him along its track.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphVehicleGameTests {
    private static final int PRESS_AT = 22;

    private KingCrimsonEpitaphVehicleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_vehicle_cart")
    public static void aMinecartKeepsItsRunOnTheRailsWithItsRider(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-cart", 4, 30, 8);
        fixture.budget(0);
        quiet(fixture);
        for (int x = 3; x <= 28; x++) {
            helper.setBlock(new BlockPos(x, 2, 5), Blocks.RAIL);
        }
        ServerPlayer rider = rider(fixture, "kc-ep-cart-him", 5.5D, 5.5D);
        Minecart cart = EntityType.MINECART.create(fixture.level);
        cart.setPos(fixture.spot(5.5D, 2.0625D, 5.5D));
        fixture.thing(cart);
        Vec3[] began = new Vec3[1];
        helper.runAfterDelay(PRESS_AT - 10, () -> fixture.guarded(() -> {
            helper.assertTrue(rider.startRiding(cart, true), "Setup: the player did not get into the minecart");
            cart.setDeltaMovement(0.3D, 0.0D, 0.0D);
            began[0] = cart.position();
        }));
        Scene scene = new Scene(fixture, rider, cart, "A minecart keeps its run on the rails with its rider");
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // The control: what the minecart itself does with him in it.
            double alone = cart.getX() - began[0].x;
            helper.assertTrue(rider.getVehicle() == cart && alone > 1.5D, "Setup: in ten ticks the minecart with the player in it"
                    + " ran " + alone + " blocks along the rails");
            EntityTrack ridden = scene.press();
            if (ridden == null) {
                return;
            }
            Vec3 run = ridden.frame(20).position().subtract(ridden.frame(0).position());
            fixture.rule(run.x > alone && Math.abs(run.z) < 0.01D, "the phantom of the minecart went " + run + " in twenty ticks;"
                    + " the minecart had run " + alone + " blocks east in the ten before the press: it does not keep its run");
            scene.follow();
        }));
        scene.watch();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_vehicle_boat")
    public static void aBoatGoesWhereItsRiderSteersIt(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-boat", 4, 24, 8);
        fixture.budget(0);
        quiet(fixture);
        // Players fight here, whatever the server of the tests is set to: the rider is after the user.
        MinecraftServer server = fixture.level.getServer();
        boolean allowed = server.isPvpAllowed();
        fixture.base.onClose(() -> server.setPvpAllowed(allowed));
        server.setPvpAllowed(true);
        // Straight ahead of the user, in a boat on the ground.
        ServerPlayer rider = rider(fixture, "kc-ep-boat-him", 16.5D, 2.5D);
        Boat boat = EntityType.BOAT.create(fixture.level);
        boat.setPos(fixture.spot(16.5D, 2.0D, 2.5D));
        fixture.thing(boat);
        Vec3[] began = new Vec3[1];
        helper.runAfterDelay(2, () -> fixture.guarded(() -> {
            helper.assertTrue(rider.startRiding(boat, true), "Setup: the player did not get into the boat");
            began[0] = boat.position();
        }));
        Scene scene = new Scene(fixture, rider, boat, "A boat goes where its rider steers it");
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            double alone = boat.position().distanceTo(began[0]);
            helper.assertTrue(rider.getVehicle() == boat && boat.getControllingPassenger() == rider && alone < 0.1D,
                    "Setup: the player does not sit at the paddles of the boat, or it went " + alone + " blocks by itself");
            EntityTrack ridden = scene.press();
            if (ridden == null) {
                return;
            }
            Vec3 from = ridden.frame(0).position();
            Vec3 to = ridden.frame(Math.min(30, ridden.lastTick())).position();
            fixture.rule(from.x - to.x > 1.0D, "in thirty ticks the phantom of the boat went from " + KingCrimsonEpitaphFixture.at(from)
                    + " to " + KingCrimsonEpitaphFixture.at(to) + ": its rider, who is after the user 14 blocks to the west, did"
                    + " not paddle it there");
            scene.follow();
        }));
        scene.watch();
    }

    // The passive erase would answer a foreseen hit, and a walked player heals at a pace of his own: neither is the matter here.
    private static void quiet(KingCrimsonEpitaphFixture fixture) {
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        fixture.steadyHealth();
    }

    // A second player, ticked as a connection ticks one: he stays where the server last put him.
    private static ServerPlayer rider(KingCrimsonEpitaphFixture fixture, String name, double x, double z) {
        ServerPlayer rider = fixture.other(name, x, z);
        fixture.base.tickAsConnection(rider);
        return rider;
    }

    /** What both vehicles share: the press at the rider, his seat in the room, and the walk for real. */
    private static final class Scene {
        private final KingCrimsonEpitaphFixture fixture;
        private final ServerPlayer rider;
        private final Entity vehicle;
        private final String title;
        private EpitaphSession running;
        private int seen;

        private Scene(KingCrimsonEpitaphFixture fixture, ServerPlayer rider, Entity vehicle, String title) {
            this.fixture = fixture;
            this.rider = rider;
            this.vehicle = vehicle;
            this.title = title;
        }

        /** The press with the rider under the crosshair; the track of what he rides, or null when the test is over. */
        private EntityTrack press() {
            EpitaphSession session = fixture.press(rider);
            KingCrimsonEpitaphFixture.acceptTeleport(fixture.user);
            KingCrimsonEpitaphFixture.acceptTeleport(rider);
            session.foreseeAll();
            EntityTrack his = session.record().track(rider.getId());
            EntityTrack ridden = session.record().track(vehicle.getId());
            fixture.rule(his != null && ridden != null && Math.min(his.lastTick(), ridden.lastTick()) >= 20, "the rider has "
                    + (his != null ? "a" : "no") + " track and what he rides has " + (ridden != null ? "a" : "no")
                    + " track, or one of them is shorter than twenty ticks");
            fixture.rule(rider.getVehicle() == vehicle && EpitaphPredictions.isForcedWalking(rider) && EpitaphSessions.isOnTrack(vehicle),
                    "after the press the rider " + (rider.getVehicle() == vehicle ? "rides on" : "is off what he rode") + " and is "
                            + (EpitaphPredictions.isForcedWalking(rider) ? "walked" : "not walked") + ", and what he rides is "
                            + (EpitaphSessions.isOnTrack(vehicle) ? "carried" : "free"));
            if (his == null || ridden == null || Math.min(his.lastTick(), ridden.lastTick()) < 20) {
                session.end(EpitaphEnd.CANCELLED);
                fixture.finish(title);
                return null;
            }
            double apart = 0.0D;
            for (int tick = 0; tick <= 20; tick++) {
                Vec3 seat = his.frame(tick).position().subtract(ridden.frame(tick).position());
                apart = Math.max(apart, Math.sqrt(seat.x * seat.x + seat.z * seat.z));
            }
            fixture.rule(apart < 0.3D, "in the first twenty ticks of the future the phantom of the rider was up to " + apart
                    + " blocks beside the phantom of what he rides: he does not sit in it");
            running = session;
            return ridden;
        }

        private void follow() {
            seen = 0;
        }

        /** For real: what he rides is carried along its track, and carries him, for ten ticks; then the test is over. */
        private void watch() {
            fixture.helper.onEachTick(() -> fixture.guarded(() -> {
                EpitaphSession session = running;
                if (session == null || session.ended()) {
                    return;
                }
                double tick = session.clock().body();
                double his = rider.position().distanceTo(KingCrimsonEpitaphFixture.on(session.record().track(rider.getId()), tick));
                double its = vehicle.position().distanceTo(KingCrimsonEpitaphFixture.on(session.record().track(vehicle.getId()), tick));
                fixture.rule(rider.getVehicle() == vehicle && his < 0.05D && its < 1.0E-6D, "at tick " + tick + " of their tracks the"
                        + " rider " + (rider.getVehicle() == vehicle ? "rides" : "does not ride") + ", " + his + " blocks from where"
                        + " his phantom was then, and what he rides is " + its + " blocks from where its phantom was");
                if (++seen == 10) {
                    running = null;
                    session.end(EpitaphEnd.CANCELLED);
                    fixture.finish(title);
                }
            }));
        }
    }
}
