package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import rotp.core.core.JojoMod;
import rotp.core.entityattachment.DataEventListeners;
import rotp.core.entityattachment.TickingEntityData;
import rotp.core.init.ModDataAttachmentTypes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Data can be attached to an entity on a thread that does not tick it. Pack crash of 2026-11-13: a client sound listener
 * ran on the sound engine thread and attached data to the client player while the render thread, in the player's first
 * tick after a dimension change, was moving the data attached before into the ticking map; DataEventListeners.onTick
 * threw ConcurrentModificationException. Such data joins the ticks whole, moved by the thread that ticks the entity.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DataEventListenersThreadGameTests {
	private static final String TICK_A = "tick ProbeA";
	private static final String TICK_B = "tick ProbeB";

	private DataEventListenersThreadGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void dataAttachedOnAnotherThreadWhileTheEntityTicksJoinsItsTicks(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			List<String> visits = Collections.synchronizedList(new ArrayList<>());
			AtomicReference<Throwable> attachFailure = new AtomicReference<>();
			Probe attachedElsewhere = new ProbeB(visits);
			// at the tick's first move another thread attaches data, through the call an attachment's constructor makes
			MovesWatched moves = watchMovesIntoTheTickingMap(helper, player, () -> {
				Thread other = new Thread(() -> attachedElsewhere.addTicking(player), "rotp-gametest-attach");
				other.setUncaughtExceptionHandler((thread, error) -> attachFailure.set(error));
				other.start();
				try {
					other.join(10_000L);
				}
				catch (InterruptedException error) {
					Thread.currentThread().interrupt();
				}
				if (other.isAlive()) {
					attachFailure.compareAndSet(null, new AssertionError("the other thread did not finish in 10 s"));
				}
			});
			new ProbeA(visits).addTicking(player);
			Thread tickThread = Thread.currentThread();

			tick(helper, player, "the first tick");
			tick(helper, player, "the second tick");

			helper.assertTrue(attachFailure.get() == null, "Attaching data on another thread failed: " + attachFailure.get());
			List<String> seen = List.copyOf(visits);
			// the data attached elsewhere ticks from the tick it was attached in, or from the next one
			helper.assertTrue(seen.equals(List.of(TICK_A, TICK_B, TICK_A, TICK_B)) || seen.equals(List.of(TICK_A, TICK_A, TICK_B)),
					"Over two ticks the data ticked as " + seen + "; expected ProbeA in both ticks and ProbeB, attached on"
					+ " another thread during the first, from the first or the second tick");
			helper.assertTrue(moves.movers.size() == 2 && moves.movers.stream().allMatch(mover -> mover == tickThread),
					"Only the thread that ticks the entity moves its data into the ticking map, once each; moved by "
					+ moves.movers.stream().map(Thread::getName).toList() + ", ticked by " + tickThread.getName());
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void dataAttachedByTickingDataDuringTheTickTicksFromTheNextTick(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			List<String> visits = new ArrayList<>();
			// ProbeB sorts after Attacher, so the tick still has it ahead if it went straight into the ticking map
			new Attacher(visits, player, new ProbeB(visits)).addTicking(player);

			tick(helper, player, "the tick in which ticking data attached more");
			List<String> first = List.copyOf(visits);
			helper.assertTrue(first.equals(List.of("tick Attacher")),
					"The tick in which ticking data attached more ticked " + first + ", expected only [tick Attacher]");
			tick(helper, player, "the next tick");
			helper.assertTrue(visits.equals(List.of("tick Attacher", "tick Attacher", TICK_B)),
					"Data attached during a tick ticks from the next one: " + visits);
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static void tick(GameTestHelper helper, Entity entity, String which) {
		try {
			listeners(entity).onTick();
		}
		catch (ConcurrentModificationException error) {
			String where = Arrays.stream(error.getStackTrace()).limit(3).map(String::valueOf)
					.collect(Collectors.joining(" <- "));
			helper.fail("Data attached during " + which + " broke the tick: " + error + " at " + where);
		}
	}

	private static DataEventListeners listeners(Entity entity) {
		return entity.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get());
	}

	@SuppressWarnings("unchecked")
	private static MovesWatched watchMovesIntoTheTickingMap(GameTestHelper helper, Entity entity, Runnable onFirstMove) {
		DataEventListeners listeners = listeners(entity);
		try {
			Field field = DataEventListeners.class.getDeclaredField("ticking");
			field.setAccessible(true);
			Map<Class<?>, TickingEntityData> ticking = (Map<Class<?>, TickingEntityData>) field.get(listeners);
			helper.assertTrue(ticking.isEmpty(), "The new entity already ticks data: " + ticking.keySet());
			MovesWatched watched = new MovesWatched(((SortedMap<Class<?>, TickingEntityData>) ticking).comparator(), onFirstMove);
			field.set(listeners, watched);
			return watched;
		}
		catch (ReflectiveOperationException | ClassCastException error) {
			throw new AssertionError("Could not watch DataEventListeners.ticking", error);
		}
	}

	// the ticking map, in its own order, recording which thread moves data into it
	private static final class MovesWatched extends TreeMap<Class<?>, TickingEntityData> {
		private final List<Thread> movers = Collections.synchronizedList(new ArrayList<>());
		private final Runnable onFirstMove;
		private boolean moved;

		private MovesWatched(Comparator<? super Class<?>> order, Runnable onFirstMove) {
			super(order);
			this.onFirstMove = onFirstMove;
		}

		@Override
		public TickingEntityData put(Class<?> type, TickingEntityData data) {
			movers.add(Thread.currentThread());
			TickingEntityData previous = super.put(type, data);
			if (!moved) {
				moved = true;
				onFirstMove.run();
			}
			return previous;
		}
	}

	private abstract static class Probe implements TickingEntityData {
		private final List<String> visits;

		private Probe(List<String> visits) {
			this.visits = visits;
		}

		@Override
		public void tick() {
			visits.add("tick " + getClass().getSimpleName());
		}
	}

	private static final class ProbeA extends Probe {
		private ProbeA(List<String> visits) {
			super(visits);
		}
	}

	private static final class ProbeB extends Probe {
		private ProbeB(List<String> visits) {
			super(visits);
		}
	}

	private static final class Attacher extends Probe {
		private final Entity entity;
		private final Probe attaches;
		private boolean attached;

		private Attacher(List<String> visits, Entity entity, Probe attaches) {
			super(visits);
			this.entity = entity;
			this.attaches = attaches;
		}

		@Override
		public void tick() {
			super.tick();
			if (!attached) {
				attached = true;
				attaches.addTicking(entity);
			}
		}
	}
}
