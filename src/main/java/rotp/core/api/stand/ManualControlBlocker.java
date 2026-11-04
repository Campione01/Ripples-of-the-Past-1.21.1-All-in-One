package rotp.core.api.stand;

import java.util.Objects;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * Add-on veto for entering Stand manual control. Leaving manual control is
 * never vetoed.
 */
@FunctionalInterface
public interface ManualControlBlocker {
	boolean isBlocked(
			LivingEntity user,
			StandEntity stand);

	/**
	 * The message its user sees on the action bar when this blocker refuses a
	 * press of the manual control key, as the core shows its other refusals
	 * (1.16's StandEntity.canBeManuallyControlled could carry one); null
	 * refuses silently. Asked on both logical sides, only while
	 * {@link #isBlocked}; the server sends it.
	 */
	@Nullable
	default Component refusalMessage(
			LivingEntity user,
			StandEntity stand) {
		return null;
	}

	/** A blocker that tells its user why with the given message. */
	static ManualControlBlocker withMessage(
			ManualControlBlocker blocker,
			Component message) {
		Objects.requireNonNull(blocker, "blocker");
		Objects.requireNonNull(message, "message");
		return new ManualControlBlocker() {
			@Override
			public boolean isBlocked(LivingEntity user, StandEntity stand) {
				return blocker.isBlocked(user, stand);
			}

			@Override
			public Component refusalMessage(LivingEntity user, StandEntity stand) {
				return message;
			}
		};
	}
}
