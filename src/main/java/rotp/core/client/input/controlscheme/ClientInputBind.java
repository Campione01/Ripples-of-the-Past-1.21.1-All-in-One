package rotp.core.client.input.controlscheme;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyModifier;

public class ClientInputBind {
	protected KeyMapping vanillaMapping;
	protected ClientKey immutableKey;
	@Nonnull protected KeyModifier immutableKeyModifier;
	/** The modifier a moveset declared: it is read through the variation key at every look, so a change of that key needs no rebuild. */
	@Nullable protected InputKey.Modifier declaredModifier;
	
	public ClientInputBind(KeyMapping vanillaMapping) {
		this(vanillaMapping, null, null);
	}
	
	public ClientInputBind(ClientKey immutableKey) {
		this(null, immutableKey, KeyModifier.NONE);
	}
	
	public ClientInputBind(ClientKey immutableKey, KeyModifier modifier) {
		this(null, immutableKey, modifier);
	}
	
	@ApiStatus.Internal
	public ClientInputBind(KeyMapping vanillaMapping, ClientKey immutableKey, @Nonnull KeyModifier immutableKeyModifier) {
		this.vanillaMapping = vanillaMapping;
		this.immutableKey = immutableKey;
		this.immutableKeyModifier = immutableKeyModifier;
	}
	
	
	@Nullable
	public static ClientInputBind toClientInput(InputBindTemplate commonInput) {
		if (commonInput == null) return null;
		return switch (commonInput) {
			case InputKey hardcodedKey -> {
				ClientInputBind bind = new ClientInputBind(toClientKey(hardcodedKey), toClientModifier(hardcodedKey.modifier));
				bind.declaredModifier = hardcodedKey.modifier;
				yield bind;
			}
			case InputUseVanillaMapping keyMapping -> {
				KeyMapping vanillaKeyMapping = keyMapping.toClientKeybind();
				yield vanillaKeyMapping != null ? new ClientInputBind(vanillaKeyMapping) : null;
			}
			default -> null;
		};
	}
	
    public static ClientKey toClientKey(InputKey key) {
    	return switch (key.device) {
    		case KEYBOARD -> ClientKey.make(InputConstants.Type.KEYSYM, key.keyCode);
    		case MOUSE -> ClientKey.make(InputConstants.Type.MOUSE, key.keyCode);
    	};
    }
    
    /**
     * A declared SHIFT is the variation key. A declared CONTROL is Ctrl, except while the variation key is Ctrl:
     * it falls to Shift, so that the two never share a key.
     */
    public static KeyModifier toClientModifier(InputKey.Modifier commonModifier) {
    	if (commonModifier == null) return KeyModifier.NONE;
    	KeyModifier variation = VariationKey.modifier();
    	return switch (commonModifier) {
    		case SHIFT -> variation;
    		case CONTROL -> variation == KeyModifier.CONTROL ? KeyModifier.SHIFT : KeyModifier.CONTROL;
    	};
    }
    
	
	@Nullable
	public ClientKey getKey() {
		if (vanillaMapping != null && !vanillaMapping.isUnbound()) {
			return ClientKey.fromVanillaKeybind(vanillaMapping);
		}
		if (immutableKey != null) {
			return immutableKey;
		}

		return null;
	}

	public InputConstants.Key getVanillaKey() {
		if (vanillaMapping != null && !vanillaMapping.isUnbound()) {
			return vanillaMapping.getKey();
		}
		if (immutableKey != null) {
			InputConstants.Key key = immutableKey.getVanillaKey();
			if (key != null) {
				return key;
			}
		}
		
		return InputConstants.UNKNOWN;
	}
	
	@Nonnull
	public KeyModifier getKeyModifier() {
		if (declaredModifier != null) {
			return toClientModifier(declaredModifier);
		}
		if (vanillaMapping != null && !vanillaMapping.isUnbound()) {
			return vanillaMapping.getKeyModifier();
		}
		if (immutableKeyModifier != null) {
			return immutableKeyModifier;
		}
		
		return KeyModifier.NONE;
	}
	
	public boolean keyMatches(ClientKey key, KeyModifier curModifier) {
		ClientKey inputKey = this.getKey();
		if (inputKey != null && inputKey.equals(key)) {
			KeyModifier keyModifier = this.getKeyModifier();
			return keyModifier == KeyModifier.NONE || keyModifier == curModifier;
		}
		return false;
	}

	public boolean usesVanillaMapping(KeyMapping keyMapping) {
		if (vanillaMapping == null || keyMapping == null) {
			return false;
		}
		return vanillaMapping == keyMapping || vanillaMapping.getName().equals(keyMapping.getName());
	}
	
}
