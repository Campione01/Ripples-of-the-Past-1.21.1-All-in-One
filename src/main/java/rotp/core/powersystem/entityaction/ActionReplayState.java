package rotp.core.powersystem.entityaction;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.syncher.SynchedEntityData;

/**
 * One action slot's action as it stands, taken by {@link LivingComponentAction#captureReplayState()} to be put on
 * another performer by {@link LivingComponentAction#applyReplayState}: its network form (the ability, phase and phase
 * tick, phase lengths, targets, Stand offset and what the action itself sends) and its synched values that are not at
 * their defaults. What lives only in the instance's own fields beyond that is not in it.
 * @param action the encoded action, or null for none
 */
public record ActionReplayState(@Nullable byte[] action, List<SynchedEntityData.DataValue<?>> synchedValues) {
	public static final ActionReplayState NONE = new ActionReplayState(null, List.of());

	public ActionReplayState {
		synchedValues = List.copyOf(synchedValues);
	}

	public boolean hasAction() {
		return action != null;
	}
}
