package rotp.core.client.standskin;

import java.util.Locale;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The name the skins tab gives a skin: the Stand's name as the skin's own language files give it (Gold Experience
 * Requiem's five skins each name themselves so), else the Stand's own name for its default skin, else a name made of the
 * skin's id. No client classes here, so the smoke test can load it.
 */
public final class StandSkinNames {
	private StandSkinNames() {}

	public static Component displayName(@Nullable String ownTranslation, ResourceLocation skinId, boolean isDefault,
			Supplier<Component> standName) {
		if (ownTranslation != null && !ownTranslation.isBlank()) {
			return Component.literal(ownTranslation);
		}
		return isDefault ? standName.get() : Component.literal(fromId(skinId.getPath()));
	}

	// "the_grateful_dead_purple_repaint" -> "The Grateful Dead Purple Repaint"
	static String fromId(String path) {
		StringBuilder name = new StringBuilder();
		for (String word : path.substring(path.lastIndexOf('/') + 1).split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			if (name.length() > 0) {
				name.append(' ');
			}
			name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
		}
		return name.length() > 0 ? name.toString() : path;
	}
}
