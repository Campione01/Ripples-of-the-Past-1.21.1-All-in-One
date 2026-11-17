package rotp.addons.justice;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/** Server-only Justice disguise and merchant session state; call on the logical server thread. */
public final class JusticeDisguise {
	private static final int TARGET_INTERVAL = 10;
	private static final double RADIUS_SQR = JusticeFogPuppet.RADIUS * JusticeFogPuppet.RADIUS;
	private static final Map<MinecraftServer, ServerState> SERVERS = new IdentityHashMap<>();

	private JusticeDisguise() {}

	public enum Mode { NONE, VILLAGER, ZOMBIE }

	public static Mode getMode(Player player) {
		if (!(player instanceof ServerPlayer caster)) return Mode.NONE;
		ServerState state = SERVERS.get(caster.server);
		return state == null ? Mode.NONE : state.modes.getOrDefault(caster.getUUID(), Mode.NONE);
	}

	/** Caller checks Fog and sends the resulting mode to interested clients. */
	public static Mode cycle(ServerPlayer caster) {
		Mode next = switch (getMode(caster)) {
			case NONE -> Mode.VILLAGER;
			case VILLAGER -> Mode.ZOMBIE;
			case ZOMBIE -> Mode.NONE;
		};
		if (next == Mode.NONE) {
			clear(caster);
		}
		else {
			ServerState state = SERVERS.computeIfAbsent(caster.server, ignored -> new ServerState());
			state.modes.put(caster.getUUID(), next);
			// The donor forgot the villagers it had priced down whenever the mode moved on from Villager.
			if (next != Mode.VILLAGER) state.discounted.remove(caster.getUUID());
			if (next == Mode.ZOMBIE) endTradesOf(state, caster);
		}
		return next;
	}

	/** Called on Fog off, caster death, or logout. Normal recall does not clear a disguise. */
	public static void clear(ServerPlayer caster) {
		ServerState state = SERVERS.get(caster.server);
		if (state == null) return;
		state.modes.remove(caster.getUUID());
		state.discounted.remove(caster.getUUID());
		endTradesOf(state, caster);
		if (state.modes.isEmpty() && state.trades.isEmpty() && state.discounted.isEmpty()) SERVERS.remove(caster.server);
	}

	/** Clears the departing server's state after its normal logout callbacks. */
	public static void clearServer(MinecraftServer server) {
		ServerState state = SERVERS.remove(server);
		if (state != null) state.trades.forEach(JusticeDisguise::restore);
	}

	/** One player post-tick call; the donor ran the same ten-tick scan from two events. */
	public static void tick(ServerPlayer caster) {
		Mode mode = getMode(caster);
		ServerLevel level = caster.serverLevel();
		if (mode == Mode.NONE || level.getGameTime() % TARGET_INTERVAL != 0) return;
		var area = caster.getBoundingBox().inflate(JusticeFogPuppet.RADIUS);
		for (Monster mob : level.getEntitiesOfClass(Monster.class, area,
				entity -> entity.isAlive() && entity.getTarget() == caster && caster.distanceToSqr(entity) <= RADIUS_SQR)) {
			mob.setTarget(null);
		}
		for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, area,
				entity -> entity.isAlive() && caster.distanceToSqr(entity) <= RADIUS_SQR)) {
			if (mode == Mode.ZOMBIE) golem.setTarget(caster);
			else if (golem.getTarget() == caster) golem.setTarget(null);
		}
	}

	public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
		if (event.getEntity() instanceof Monster monster && monster.level() instanceof ServerLevel
				&& event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && getMode(player) != Mode.NONE) {
			event.setNewAboutToBeSetTarget(null);
		}
	}

	public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
		if (!(event.getEntity() instanceof Villager villager)) return;
		endTrade(villager);
		ServerState state = tradeState(villager);
		if (state != null) state.discounted.values().forEach(byVillager -> byVillager.remove(villager));
	}

	/** Called only after vanilla assigns the actual customer, before it sends the quote. */
	public static void beginTrade(Villager villager, Player customer) {
		if (!(customer instanceof ServerPlayer caster) || villager.level() != caster.level()
				|| villager.getTradingPlayer() != caster || getMode(caster) != Mode.VILLAGER) return;
		ServerState state = SERVERS.get(caster.server);
		if (state.trades.containsKey(villager)) return;
		// The donor priced each offer down once per caster, until the mode moved on from Villager.
		Set<MerchantOffer> priced = state.discounted.computeIfAbsent(caster.getUUID(), ignored -> new IdentityHashMap<>())
				.computeIfAbsent(villager, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
		Map<MerchantOffer, Integer> applied = new IdentityHashMap<>();
		for (MerchantOffer offer : villager.getOffers()) {
			if (!priced.add(offer)) continue;
			int discount = Math.max(1, offer.getBaseCostA().getCount() / 4);
			applied.put(offer, discount);
			offer.addToSpecialPriceDiff(-discount);
		}
		if (!applied.isEmpty()) state.trades.put(villager, new Trade(caster, applied));
	}

	/** Called before the old customer is replaced or cleared by vanilla. */
	public static void endTrade(Villager villager) {
		ServerState state = tradeState(villager);
		if (state == null) return;
		Trade trade = state.trades.remove(villager);
		if (trade != null) restore(villager, trade);
	}

	/** Native offer serialization includes specialPrice; never persist our session delta. */
	public static void saveWithoutTradeDiscount(Villager villager, Runnable save) {
		ServerState state = tradeState(villager);
		Trade trade = state == null ? null : state.trades.get(villager);
		if (trade == null || trade.suspended || villager.getTradingPlayer() != trade.caster) {
			save.run();
			return;
		}
		trade.suspended = true;
		adjust(villager, trade, 1);
		try {
			save.run();
		}
		finally {
			trade.suspended = false;
			if (state.trades.get(villager) == trade && villager.getTradingPlayer() == trade.caster) {
				adjust(villager, trade, -1);
			}
		}
	}

	private static ServerState tradeState(Villager villager) {
		return villager.level() instanceof ServerLevel level ? SERVERS.get(level.getServer()) : null;
	}

	private static void endTradesOf(ServerState state, ServerPlayer caster) {
		var trades = state.trades.entrySet().iterator();
		while (trades.hasNext()) {
			var entry = trades.next();
			if (!entry.getValue().caster.getUUID().equals(caster.getUUID())) continue;
			restore(entry.getKey(), entry.getValue());
			trades.remove();
		}
	}

	private static void restore(Villager villager, Trade trade) {
		if (!trade.suspended && villager.getTradingPlayer() == trade.caster) adjust(villager, trade, 1);
	}

	private static void adjust(Villager villager, Trade trade, int sign) {
		var offers = villager.getOffers();
		trade.applied.forEach((offer, discount) -> {
			if (offers.stream().anyMatch(current -> current == offer)) offer.addToSpecialPriceDiff(sign * discount);
		});
	}

	private static final class ServerState {
		private final Map<UUID, Mode> modes = new HashMap<>();
		private final Map<Villager, Trade> trades = new IdentityHashMap<>();
		private final Map<UUID, Map<Villager, Set<MerchantOffer>>> discounted = new HashMap<>();
	}

	private static final class Trade {
		private final ServerPlayer caster;
		private final Map<MerchantOffer, Integer> applied;
		private boolean suspended;

		private Trade(ServerPlayer caster, Map<MerchantOffer, Integer> applied) {
			this.caster = caster;
			this.applied = applied;
		}
	}
}
