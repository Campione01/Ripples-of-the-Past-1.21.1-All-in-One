package rotp.core.gametest;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import rotp.core.core.JojoMod;
import rotp.core.item.polaroid.PhotosHandler;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandDamageContext;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The core's static holders of a session must let it go: a value never keeps its key, its server, level or entities
 * alive, the holders are emptied when the server has stopped, and a stopped server gets no new entry.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SessionHolderLifetimeGameTests {

	private SessionHolderLifetimeGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "session_holder_stand_damage_values")
	public static void anEntityAStandMadeDoesNotKeepTheStandsUserAlive(GameTestHelper helper) {
		Cow made = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(made != null, "setup: could not create the cow");
		WeakReference<Player> user = claimForAGoneUser(helper, made);
		awaitCollected(user, "An entity a Stand made keeps the Stand's user alive (StandDamageContext.MADE)");
		helper.assertTrue(StandDamageContext.madeBy(made) == null,
				"An entity still names the Stand of a user who is gone");
		Reference.reachabilityFence(made);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "session_holder_stand_damage_stopped")
	public static void theStoppedEventForgetsWhatTheStandsMade(GameTestHelper helper) {
		Cow made = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(made != null, "setup: could not create the cow");
		Player user = standUser(helper);
		StandDamageContext.claim(made, PowerClass.STAND.attachGet(user));
		helper.assertTrue(StandDamageContext.madeBy(made) != null, "setup: the claim was not kept");
		StandDamageContext.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));
		helper.assertTrue(StandDamageContext.madeBy(made) == null,
				"ServerStoppedEvent did not forget the entities the Stands made");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "session_holder_photos_values")
	public static void aPhotosHandlerDoesNotKeepItsServerAlive(GameTestHelper helper) {
		WeakReference<MinecraftServer> server = handlerForABareServer(helper);
		awaitCollected(server, "A server with a photos handler is kept alive by PhotosHandler.HANDLERS");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "session_holder_photos_stopped")
	public static void theStoppedEventForgetsAPhotosHandlerAndAStoppedServerGetsNone(GameTestHelper helper) {
		MinecraftServer server = bareServer(helper);
		PhotosHandler.get(server);
		helper.assertTrue(PhotosHandler.hasHandlerForTests(server), "setup: the running server got no handler");
		setStopped(server);
		PhotosHandler.onServerStopped(new ServerStoppedEvent(server));
		helper.assertTrue(!PhotosHandler.hasHandlerForTests(server),
				"ServerStoppedEvent did not forget the stopped server's photos handler");
		PhotosHandler.get(server);
		helper.assertTrue(!PhotosHandler.hasHandlerForTests(server), "A stopped server got a new photos handler");
		helper.succeed();
	}

	// The user lives only in this frame: once it returns, only the core's holders could keep it.
	private static WeakReference<Player> claimForAGoneUser(GameTestHelper helper, Cow made) {
		Player user = standUser(helper);
		StandPower power = PowerClass.STAND.attachGet(user);
		StandDamageContext.claim(made, power);
		helper.assertTrue(StandDamageContext.madeBy(made) != null, "setup: the claim was not kept");
		return new WeakReference<>(user);
	}

	// A Stand user never added to the level, so that nothing but the test and the holders know him.
	private static Player standUser(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(type != null, "setup: missing registered Star Platinum");
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "setup: could not grant Star Platinum");
		return user;
	}

	private static WeakReference<MinecraftServer> handlerForABareServer(GameTestHelper helper) {
		MinecraftServer server = bareServer(helper);
		PhotosHandler.get(server);
		helper.assertTrue(PhotosHandler.hasHandlerForTests(server), "setup: the running server got no handler");
		return new WeakReference<>(server);
	}

	/** A server of the game test server's class, allocated without its constructor. */
	private static MinecraftServer bareServer(GameTestHelper helper) {
		try {
			Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			Object unsafe = theUnsafe.get(null);
			return (MinecraftServer) unsafe.getClass().getMethod("allocateInstance", Class.class)
					.invoke(unsafe, helper.getLevel().getServer().getClass());
		}
		catch (ReflectiveOperationException exception) {
			throw new AssertionError("setup: could not allocate a bare server", exception);
		}
	}

	private static void setStopped(MinecraftServer server) {
		try {
			Field stopped = MinecraftServer.class.getDeclaredField("stopped");
			stopped.setAccessible(true);
			stopped.setBoolean(server, true);
		}
		catch (ReflectiveOperationException exception) {
			throw new AssertionError("setup: could not mark the bare server stopped", exception);
		}
		if (!server.isStopped()) {
			throw new GameTestAssertException("setup: the bare server is not stopped");
		}
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
