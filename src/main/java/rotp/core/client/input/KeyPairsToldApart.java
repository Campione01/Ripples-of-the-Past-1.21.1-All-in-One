package rotp.core.client.input;

import java.util.Set;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * OQ-C05: pairs of one of the mod's mappings and a mapping on the same key, one with a modifier and one without, that the
 * mod's input reads apart on every press, so the key binds screen does not mark them (NeoForge's in-game rule would).
 * Both sides of each pair are read only by consumeClick, and NeoForge's KeyMappingLookup clicks only the modifier's mapping
 * while the modifier is held and only the plain one while it is not; the swap's scheme bind (items_swap_w_user) matches only
 * with its modifier held (ClientInputBind#keyMatches), and in a container screen the mod's in-game swap is not active.
 * Bound on one key with the same modifier, or both without one, they are marked again.
 */
final class KeyPairsToldApart {
	private static final Set<Set<String>> PAIRS = Set.of(
			// summon the Stand, and the Stand HUD mode
			Set.of("jojo_ripples.key.toggle_stand", "jojo_ripples.key.stand_mode"),
			// swap items with the Stand, and vanilla's swap with the offhand
			Set.of("jojo_ripples.key.swap_items", "key.swapOffhand"));

	private KeyPairsToldApart() {}

	static boolean toldApart(KeyMapping a, KeyMapping b) {
		if (a.getName().equals(b.getName()) || !PAIRS.contains(Set.of(a.getName(), b.getName()))
				|| !a.getKey().equals(b.getKey())) {
			return false;
		}
		KeyModifier first = a.getKeyModifier();
		KeyModifier second = b.getKeyModifier();
		return first != second && (first == KeyModifier.NONE || second == KeyModifier.NONE);
	}
}
