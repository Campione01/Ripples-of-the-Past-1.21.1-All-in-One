package rotp.core.client.input.controlscheme;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * The player's variation key (jojo_ripples.key.variation): what a moveset's declared Modifier.SHIFT means. A declared
 * Modifier.CONTROL stays Ctrl, but while this key is Ctrl it is Shift, so that the two never share a key.
 * <p>
 * Binds are looked up by KeyModifier, which has no value of its own for a key that is no modifier. Such a key borrows
 * ALT: no moveset declares Alt, and Alt held turns the controls off by default. Left and right count as one key,
 * as in NeoForge's own modifiers.
 */
public final class VariationKey {
	public static final KeyModifier DEFAULT_MODIFIER = KeyModifier.CONTROL;
	public static final KeyModifier BORROWED_MODIFIER = KeyModifier.ALT;

	@Nullable private static KeyMapping mapping;

	private VariationKey() {}

	public static void useMapping(@Nullable KeyMapping keyMapping) {
		mapping = keyMapping;
	}

	/** The value a declared Modifier.SHIFT is looked up by at the moment. */
	public static KeyModifier modifier() {
		return mapping != null ? modifierOf(mapping.getKey()) : DEFAULT_MODIFIER;
	}

	public static KeyModifier modifierOf(InputConstants.Key variationKey) {
		KeyModifier modifier = KeyModifier.getKeyModifier(variationKey);
		return modifier != KeyModifier.NONE ? modifier : BORROWED_MODIFIER;
	}

	/** What a key being held counts as now, NONE if it is no modifier at all. */
	public static KeyModifier heldAs(InputConstants.Key heldKey) {
		return mapping != null ? heldAs(heldKey, mapping.getKey()) : KeyModifier.getKeyModifier(heldKey);
	}

	public static KeyModifier heldAs(InputConstants.Key heldKey, InputConstants.Key variationKey) {
		if (heldKey.equals(variationKey)) {
			return modifierOf(variationKey);
		}
		KeyModifier modifier = KeyModifier.getKeyModifier(heldKey);
		return modifier == BORROWED_MODIFIER && isBorrowing(variationKey) ? KeyModifier.NONE : modifier;
	}

	public static boolean isBorrowing() {
		return mapping != null && isBorrowing(mapping.getKey());
	}

	public static boolean isBorrowing(InputConstants.Key variationKey) {
		return !KeyModifier.isKeyCodeModifier(variationKey);
	}

	/** The key's name for the HUD's own texts. */
	public static Component displayName() {
		return VariationKeyName.get();
	}

	public static String mappingName() {
		return "jojo_ripples.key.variation";
	}
}
