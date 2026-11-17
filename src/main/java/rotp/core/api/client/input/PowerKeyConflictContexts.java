package rotp.core.api.client.input;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * Client-only key conflict contexts for keys a power's HUD takes through its control scheme (InputUseVanillaMapping binds).
 * Such a press goes to the open HUD's scheme first and to another power's only when the open one has no bind on that key
 * (AllControlSchemes#getCustomKeyScheme), so a Stand key and a non-Stand power key, or the keys of two Stands, never act on
 * one press: the key binds screen does not mark them as sharing a key. Against vanilla's keys and every key that acts
 * whatever HUD is open, they conflict as {@link KeyConflictContext#IN_GAME} does, and they are active exactly when it is,
 * so what a press does is unchanged.
 * <p>
 * NeoForge's KeyMapping#same falls back to comparing the bare key when the contexts do not conflict, so the mappings must
 * be {@link PowerKeyMapping}s (or override same and hasKeyModifierConflict through {@link #separates}): an add-on registers
 * its Stand's own skill key in one line with {@link #standKey}, a non-Stand power's with {@link #nonStandPowerKey}.
 */
public final class PowerKeyConflictContexts {
	public enum PowerGroup {
		STAND,
		NON_STAND
	}

	private static final PowerContext ANY_STAND = new PowerContext(PowerGroup.STAND, null, () -> true);
	private static final PowerContext ANY_NON_STAND = new PowerContext(PowerGroup.NON_STAND, null, () -> true);

	private PowerKeyConflictContexts() {}

	/** A key every Stand's HUD may take (the core's grab keys). */
	public static IKeyConflictContext stand() {
		return ANY_STAND;
	}

	/** A key only this Stand's HUD takes. */
	public static IKeyConflictContext stand(ResourceLocation standTypeId) {
		return new PowerContext(PowerGroup.STAND, Objects.requireNonNull(standTypeId, "standTypeId"), () -> true);
	}

	/** A key every non-Stand power's HUD may take. */
	public static IKeyConflictContext nonStandPower() {
		return ANY_NON_STAND;
	}

	/** A key only this non-Stand power's HUD takes (Ultimate Lifeform's skill keys). */
	public static IKeyConflictContext nonStandPower(ResourceLocation powerTypeId) {
		return new PowerContext(PowerGroup.NON_STAND, Objects.requireNonNull(powerTypeId, "powerTypeId"), () -> true);
	}

	/** The same scope, active only while {@code active} also says so (a key the open HUD must have a bind on). */
	public static IKeyConflictContext whileActive(IKeyConflictContext powerContext, BooleanSupplier active) {
		if (!(powerContext instanceof PowerContext scope)) {
			throw new IllegalArgumentException("Not a power key conflict context: " + powerContext);
		}
		return new PowerContext(scope.group, scope.typeId, Objects.requireNonNull(active, "active"));
	}

	/** Whether two mappings' contexts keep them apart, so that the key binds screen does not mark them. */
	public static boolean separates(KeyMapping a, KeyMapping b) {
		return a.getKeyConflictContext() instanceof PowerContext first && b.getKeyConflictContext() instanceof PowerContext second
				&& !first.conflicts(second);
	}

	/** A Stand's own skill key, named as its moveset's InputUseVanillaMapping names it. */
	public static KeyMapping standKey(String name, ResourceLocation standTypeId, InputConstants.Type type, int keyCode, String category) {
		return new PowerKeyMapping(name, stand(standTypeId), KeyModifier.NONE, type, keyCode, category);
	}

	/** A non-Stand power's own skill key. */
	public static KeyMapping nonStandPowerKey(String name, ResourceLocation powerTypeId, InputConstants.Type type, int keyCode,
			String category) {
		return new PowerKeyMapping(name, nonStandPower(powerTypeId), KeyModifier.NONE, type, keyCode, category);
	}

	public static class PowerKeyMapping extends KeyMapping {
		public PowerKeyMapping(String name, IKeyConflictContext context, KeyModifier modifier, InputConstants.Type type, int keyCode,
				String category) {
			super(name, context, modifier, type, keyCode, category);
		}

		@Override
		public boolean same(KeyMapping other) {
			return !separates(this, other) && super.same(other);
		}

		@Override
		public boolean hasKeyModifierConflict(KeyMapping other) {
			return !separates(this, other) && super.hasKeyModifierConflict(other);
		}
	}

	private static final class PowerContext implements IKeyConflictContext {
		private final PowerGroup group;
		@Nullable private final ResourceLocation typeId;
		private final BooleanSupplier active;

		private PowerContext(PowerGroup group, @Nullable ResourceLocation typeId, BooleanSupplier active) {
			this.group = group;
			this.typeId = typeId;
			this.active = active;
		}

		@Override
		public boolean isActive() {
			return KeyConflictContext.IN_GAME.isActive() && active.getAsBoolean();
		}

		// one power's keys meet another's only through the open HUD, which takes the press for one of them
		@Override
		public boolean conflicts(IKeyConflictContext other) {
			if (other instanceof PowerContext power) {
				return group == power.group && (typeId == null || power.typeId == null || typeId.equals(power.typeId));
			}
			return KeyConflictContext.IN_GAME.conflicts(other);
		}

		@Override
		public String toString() {
			return "PowerKeyConflictContext[" + group + (typeId != null ? ", " + typeId : "") + "]";
		}
	}
}
