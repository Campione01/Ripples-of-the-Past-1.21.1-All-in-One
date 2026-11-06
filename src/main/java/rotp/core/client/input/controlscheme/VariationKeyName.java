package rotp.core.client.input.controlscheme;

import java.util.function.Supplier;

import net.minecraft.network.chat.Component;

/** The key's name for texts, in a class that also loads where the key itself does not exist. */
public final class VariationKeyName {
	private static Supplier<Component> name = () -> Component.translatable("jojo_ripples.key.variation");

	private VariationKeyName() {}

	public static void use(Supplier<Component> keyName) {
		name = keyName;
	}

	public static Component get() {
		return name.get();
	}
}
