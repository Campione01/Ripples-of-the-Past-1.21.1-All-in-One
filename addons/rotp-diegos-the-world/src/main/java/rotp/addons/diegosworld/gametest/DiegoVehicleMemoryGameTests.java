package rotp.addons.diegosworld.gametest;

import java.lang.ref.WeakReference;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diegosworld.DiegoGameEvents;
import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.core.powersystem.PowerClass;

/**
 * The vehicle a Diego user last rode is remembered by its UUID: the memory keeps no entity, and so no level or server,
 * alive, and it is forgotten when the server has stopped.
 */
@GameTestHolder("rotp_theworld_bleeding")
@PrefixGameTestTemplate(false)
public final class DiegoVehicleMemoryGameTests {
    private DiegoVehicleMemoryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20, batch = "rotpDiegoVehicleMemory")
    public static void theRememberedVehicleIsNotKeptAliveAndTheStopForgetsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        // A fake player can ride nothing; a bare player never added to the level can.
        Player user = new Player(level, at, 0.0F, new GameProfile(UUID.randomUUID(), "diego-vehicle-memory")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        user.setNoGravity(true);
        try {
            WeakReference<Boat> boat = rideAndLeave(helper, level, user, at);
            awaitCollected(boat, "The vehicle a Diego user rode is kept alive by the vehicle memory");
            helper.assertTrue(DiegoGameEvents.remembersVehicleForTests(user.getUUID()),
                    "setup: the memory of the vehicle went with the vehicle");
            DiegoGameEvents.onServerStopped(new ServerStoppedEvent(level.getServer()));
            helper.assertTrue(!DiegoGameEvents.remembersVehicleForTests(user.getUUID()),
                    "ServerStoppedEvent did not forget the vehicles");
        }
        finally {
            user.discard();
        }
        helper.succeed();
    }

    // The boat lives only in this frame and the level: discarded, only the memory could keep it.
    private static WeakReference<Boat> rideAndLeave(
            GameTestHelper helper, ServerLevel level, Player user, BlockPos at) {
        Boat boat = EntityType.BOAT.create(level);
        helper.assertTrue(boat != null, "setup: could not create the boat");
        boat.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(level.addFreshEntity(boat), "setup: the boat was not added");
        helper.assertTrue(user.startRiding(boat, true), "setup: the user does not ride the boat");
        PowerClass.STAND.attachGet(user).setStand(DiegosTheWorldAddon.THE_WORLD.get());
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(user));
        helper.assertTrue(DiegoGameEvents.remembersVehicleForTests(user.getUUID()),
                "setup: the ridden vehicle was not remembered");
        boat.discard();
        return new WeakReference<>(boat);
    }

    private static void awaitCollected(WeakReference<?> ref, String message) {
        for (int i = 0; i < 40 && ref.get() != null; i++) {
            System.gc();
            try {
                Thread.sleep(25L);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (ref.get() != null) {
            throw new GameTestAssertException(message);
        }
    }
}
