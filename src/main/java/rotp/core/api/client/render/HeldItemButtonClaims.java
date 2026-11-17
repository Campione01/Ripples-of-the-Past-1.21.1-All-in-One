package rotp.core.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only, owner-keyed claims of a mouse button by a held item. An item that takes a button the Stand's moves are bound
 * to (Sex Pistols' revolver takes the left button, owner M0114) says so here, and the HUD shows the held item in that
 * button's slot instead of the Stand move the press no longer reaches. A claim that throws counts as none.
 */
public final class HeldItemButtonClaims {
	public enum Button {
		LEFT,
		RIGHT
	}

	@FunctionalInterface
	public interface Claim {
		/** Whether this held item, in either hand of the client's player, takes the button. */
		boolean claims(@Nullable Player player, ItemStack held, Button button);
	}

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<ResourceLocation, Claim> CLAIMS = new LinkedHashMap<>();
	private static volatile List<Claim> snapshot = List.of();

	private HeldItemButtonClaims() {}

	public static synchronized void register(ResourceLocation owner, Claim claim) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(claim, "claim");
		if (CLAIMS.putIfAbsent(owner, claim) != null) {
			throw new IllegalStateException("Duplicate held item button claim: " + owner);
		}
		snapshot = List.copyOf(new ArrayList<>(CLAIMS.values()));
	}

	/** The held item, main hand first, that takes the button from the Stand's moves; empty for none. */
	public static ItemStack claimingItem(@Nullable Player player, @Nullable Button button) {
		if (player == null || button == null) {
			return ItemStack.EMPTY;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (!held.isEmpty() && claims(player, held, button)) {
				return held;
			}
		}
		return ItemStack.EMPTY;
	}

	static boolean claims(@Nullable Player player, ItemStack held, Button button) {
		for (Claim claim : snapshot) {
			try {
				if (claim.claims(player, held, button)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				LOGGER.error("Held item button claim failed for {}.", button, error);
			}
		}
		return false;
	}
}
