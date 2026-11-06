package rotp.core.gametest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.ModEntityTypes;
import rotp.core.mrpresident.CocoJumboTurtleEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 CocoJumboTurtleEntity.onRegularTutelSpawn added the extra turtle through the spawn event's
 * accessor (the WorldGenRegion at chunk generation). The live ServerLevel must not be written to,
 * since chunk generation runs off the server thread. A proxy accessor stands in for the region.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CocoJumboChunkSpawnGameTests {
	// unique fake day times, so the handler's once-per-day-time guard never blocks a run
	private static final AtomicLong DAY_TIME = new AtomicLong(-1_000_000_000L);
	private static final long SHARED_RANDOM_SEED = 0x5EEDL;
	private static final long WORKER_TIMEOUT_NANOS = 5_000_000_000L;

	private CocoJumboChunkSpawnGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void chunkGenExtraTurtleGoesToTheEventAccessor(GameTestHelper helper) {
		runSpawn(helper, false, 1);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void cancelledTurtleSpawnAddsNoCocoJumbo(GameTestHelper helper) {
		runSpawn(helper, true, 0);
		helper.succeed();
	}

	// 1.16 onMobSpawn ran at LOWEST: a veto from a later (LOW) handler still stops the extra turtle
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void lateTurtleSpawnVetoAddsNoCocoJumbo(GameTestHelper helper) {
		Consumer<FinalizeSpawnEvent> lateVeto = event -> {
			if (event.getEntity().getType() == EntityType.TURTLE && Proxy.isProxyClass(event.getLevel().getClass())) {
				event.setSpawnCancelled(true);
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOW, FinalizeSpawnEvent.class, lateVeto);
		try {
			runSpawn(helper, false, 0, NeoForge.EVENT_BUS::post);
		}
		finally {
			NeoForge.EVENT_BUS.unregister(lateVeto);
		}
		helper.succeed();
	}

	// 1.16 ForgeHooks.canEntitySpawn on the extra turtle: another mod can veto the Coco Jumbo turtle itself
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void positionCheckVetoBlocksCocoJumbo(GameTestHelper helper) {
		Consumer<MobSpawnEvent.PositionCheck> veto = event -> {
			if (event.getEntity() instanceof CocoJumboTurtleEntity && Proxy.isProxyClass(event.getLevel().getClass())) {
				event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, MobSpawnEvent.PositionCheck.class, veto);
		try {
			runSpawn(helper, false, 0);
		}
		finally {
			NeoForge.EVENT_BUS.unregister(veto);
		}
		helper.succeed();
	}

	// Chunk generation fires this off the server thread: the extra turtle still comes, from the view the server thread
	// keeps, with no number taken from a player's random or the world's and the players' list not walked.
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void chunkGenRollOffTheServerThreadLeavesThePlayersAndTheWorldAlone(GameTestHelper helper) {
		runSpawnWithPlayer(helper, false, 1, (event, player) -> {
			ServerLevel level = helper.getLevel();
			CocoJumboTurtleEntity.refreshPlayerSpawnViews(level.getServer());
			level.players().remove(player);
			RandomSource worldRandom = level.getRandom();
			RandomSource playerRandom = player.getRandom();
			worldRandom.setSeed(SHARED_RANDOM_SEED);
			playerRandom.setSeed(SHARED_RANDOM_SEED);
			long expected = RandomSource.create(SHARED_RANDOM_SEED).nextLong();
			Throwable[] thrown = new Throwable[1];
			Thread worker = new Thread(() -> {
				try {
					CocoJumboTurtleEntity.onRegularTurtleSpawn(event);
				}
				catch (Throwable failure) {
					thrown[0] = failure;
				}
			}, "coco-jumbo-spawn-test");
			worker.start();
			// Block reads off the server thread wait for its chunk executor, so this thread keeps that running.
			long deadline = System.nanoTime() + WORKER_TIMEOUT_NANOS;
			while (worker.isAlive() && System.nanoTime() < deadline) {
				if (!level.getChunkSource().pollTask()) {
					LockSupport.parkNanos(100_000L);
				}
			}
			long nextOfWorld = worldRandom.nextLong();
			long nextOfPlayer = playerRandom.nextLong();
			worldRandom.setSeed(RandomSupport.generateUniqueSeed());
			helper.assertTrue(!worker.isAlive(), "the handler did not return on its own thread");
			helper.assertTrue(thrown[0] == null, "the handler threw off the server thread: " + thrown[0]);
			helper.assertTrue(nextOfWorld == expected, "the handler took a number from the world's random");
			helper.assertTrue(nextOfPlayer == expected, "the handler took a number from the player's random");
		});
		helper.succeed();
	}

	private static void runSpawn(GameTestHelper helper, boolean cancelled, int expectedInRegion) {
		runSpawn(helper, cancelled, expectedInRegion, CocoJumboTurtleEntity::onRegularTurtleSpawn);
	}

	private static void runSpawn(GameTestHelper helper, boolean cancelled, int expectedInRegion,
			Consumer<FinalizeSpawnEvent> dispatch) {
		runSpawnWithPlayer(helper, cancelled, expectedInRegion, (event, player) -> dispatch.accept(event));
	}

	private static void runSpawnWithPlayer(GameTestHelper helper, boolean cancelled, int expectedInRegion,
			BiConsumer<FinalizeSpawnEvent, ServerPlayer> dispatch) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.SAND);
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		double x = pos.getX() + 0.5D;
		double y = pos.getY();
		double z = pos.getZ() + 0.5D;
		AABB area = new AABB(pos).inflate(4.0D);
		level.getEntities(ModEntityTypes.COCO_JUMBO_TURTLE.get(), area, e -> true).forEach(Entity::discard);

		List<Entity> added = new ArrayList<>();
		long dayTime = DAY_TIME.decrementAndGet();
		RandomSource regionRandom = RandomSource.create(SHARED_RANDOM_SEED);
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "addFreshEntity":
				added.add((Entity) args[0]);
				return Boolean.TRUE;
			case "addFreshEntityWithPassengers":
				added.add((Entity) args[0]);
				return null;
			case "dayTime":
				return dayTime;
			case "getRandom":
				// a WorldGenRegion has its own
				return regionRandom;
			default:
				try {
					return method.invoke(level, args);
				}
				catch (InvocationTargetException e) {
					throw e.getCause();
				}
			}
		};
		ServerLevelAccessor region = (ServerLevelAccessor) Proxy.newProxyInstance(
				ServerLevelAccessor.class.getClassLoader(), new Class<?>[] { ServerLevelAccessor.class }, handler);

		Turtle turtle = EntityType.TURTLE.create(level);
		helper.assertTrue(turtle != null, "could not create a turtle");
		turtle.moveTo(x, y, z, 0.0F, 0.0F);
		FinalizeSpawnEvent event = new FinalizeSpawnEvent(turtle, region, x, y, z,
				level.getCurrentDifficultyAt(pos), MobSpawnType.CHUNK_GENERATION, null, null);
		if (cancelled) {
			event.setSpawnCancelled(true);
		}

		// nearest player: a fresh server player (no turtle advancement: 7.5% chunk roll), the turtle seeded to roll under 1%
		ServerPlayer player = new ServerPlayer(level.getServer(), level,
				new GameProfile(UUID.randomUUID(), "CocoJumboSpawn"), ClientInformation.createDefault());
		// setPos, not moveTo: ServerPlayer.moveTo(x, y, z) calls connection.resetPosition(), and this player has no connection
		player.setPos(x + 3.0D, y, z);
		helper.assertTrue(player.distanceToSqr(x, y, z) < 16.0D, "the test player did not move next to the turtle");
		RandomSource random = turtle.getRandom();
		long seed = 0L;
		while (true) {
			random.setSeed(seed);
			if (random.nextFloat() < 0.01F) {
				break;
			}
			seed++;
		}
		random.setSeed(seed);

		ModConfigSpec.ConfigValue<Boolean> spawnConfig = JojoModConfig.COMMON_SPEC.getValues().get(List.of("spawnCocoJumboTurtle"));
		boolean previous = spawnConfig.get();
		level.players().add(player);
		try {
			spawnConfig.set(true);
			helper.assertTrue(level.getNearestPlayer(x, y, z, -1.0D, EntitySelector.NO_SPECTATORS) == player,
					"the test player is not the nearest player");
			dispatch.accept(event, player);
			int inLevel = level.getEntities(ModEntityTypes.COCO_JUMBO_TURTLE.get(), area, e -> true).size();
			helper.assertTrue(inLevel == 0,
					"the extra Coco Jumbo turtle was added straight to the live ServerLevel (" + inLevel + ")");
			helper.assertTrue(added.size() == expectedInRegion
					&& added.stream().allMatch(e -> e instanceof CocoJumboTurtleEntity),
					"expected " + expectedInRegion + " Coco Jumbo turtle(s) added through the spawn accessor"
					+ (cancelled ? " for a cancelled turtle spawn" : "") + ", got " + added);
		}
		finally {
			level.players().remove(player);
			spawnConfig.set(previous);
			player.getAdvancements().stopListening();
			level.getEntities(ModEntityTypes.COCO_JUMBO_TURTLE.get(), area, e -> true).forEach(Entity::discard);
		}
	}
}
