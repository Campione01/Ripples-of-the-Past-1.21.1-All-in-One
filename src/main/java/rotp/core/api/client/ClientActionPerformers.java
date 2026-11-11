package rotp.core.api.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed resolvers of performers that the client's action sync hears of but that are not in the client level: an
 * add-on's client copies of entities kept apart from the world, which handle the server's packets about them (King
 * Crimson's Epitaph phantoms). The action sync asks them first, by the packet's entity id and performer UUID, when it
 * looks a performer up to apply an action or one of its dependent packets, and when it decides whether a performer's
 * generation ledger is still live; the client level answers when none does. An answer whose UUID is not the packet's
 * is not taken, and a resolver that fails counts as no answer. Without a resolver nothing is asked. Client side, on the
 * client thread.
 */
public final class ClientActionPerformers {

	@FunctionalInterface
	public interface Resolver {
		/** @return the performer with this id and UUID, or null to leave it to the next resolver and the level */
		@Nullable
		LivingEntity resolve(int entityId, UUID performerUuid);
	}

	private static final Map<ResourceLocation, Resolver> RESOLVERS = new LinkedHashMap<>();
	private static volatile ResourceLocation[] owners = new ResourceLocation[0];
	private static volatile Resolver[] resolvers = new Resolver[0];

	private ClientActionPerformers() {}

	public static synchronized void register(ResourceLocation owner, Resolver resolver) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(resolver, "resolver");
		if (RESOLVERS.putIfAbsent(owner, resolver) != null) {
			throw new IllegalStateException("Duplicate client action performer resolver: " + owner);
		}
		refresh();
	}

	/** @return whether a resolver was registered for the owner */
	public static synchronized boolean unregister(ResourceLocation owner) {
		boolean removed = RESOLVERS.remove(Objects.requireNonNull(owner, "owner")) != null;
		if (removed) {
			refresh();
		}
		return removed;
	}

	private static void refresh() {
		owners = RESOLVERS.keySet().toArray(new ResourceLocation[0]);
		resolvers = RESOLVERS.values().toArray(new Resolver[0]);
	}

	public static boolean hasResolvers() {
		return resolvers.length > 0;
	}

	@Nullable
	public static LivingEntity resolve(int entityId, UUID performerUuid) {
		Resolver[] all = resolvers;
		if (all.length == 0 || performerUuid == null) {
			return null;
		}
		ResourceLocation[] keys = owners;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			try {
				LivingEntity performer = all[i].resolve(entityId, performerUuid);
				if (performer != null && performer.getId() == entityId && performerUuid.equals(performer.getUUID())) {
					return performer;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Client action performer resolver {} failed.", keys[i], error);
			}
		}
		return null;
	}
}
