package rotp.core.config.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.client.ui.utils.GuiIcon;
import rotp.core.config.editor.client.ConfigWidgetRules;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.StoryPart;

/**
 * What the Stands page shows of a Stand beside its rows: its name, its icon and its part, from this client's own registry
 * and Stand skins, as the creative tab sorts the discs. A Stand the client does not know reads by its id.
 */
final class StandViews {
	record View(ResourceLocation id, Component name, @Nullable GuiIcon icon, @Nullable ResourceLocation part,
			@Nullable Component partName, @Nullable Component partIcon, int partPriority) {}

	/** By part (the parts in their order, then the Stands of none), the part's own first Stand, then by name. */
	static final Comparator<View> ORDER = Comparator.comparing(View::part, StoryPart.ID_COMPARATOR)
			.thenComparingInt(View::partPriority)
			.thenComparing(view -> view.name().getString(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(view -> view.id().toString());

	private final Map<ResourceLocation, View> views = new HashMap<>();

	View view(ResourceLocation id) {
		return views.computeIfAbsent(id, StandViews::make);
	}

	private static View make(ResourceLocation id) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(id);
		Component name = type != null ? type.name.get() : Component.literal(ConfigWidgetRules.fallbackName(id.getPath()));
		StandSkinsLoader skins = StandSkinsLoader.getInstance();
		StandSkin skin = skins != null ? skins.getDefaultSkin(id) : null;
		GuiIcon icon = skin != null ? skin.getStandIcon() : null;
		Holder<StoryPart> part = null;
		Minecraft minecraft = Minecraft.getInstance();
		// the parts are a registry of the world's: on the title screen the page sorts by name alone
		if (skin != null && minecraft.level != null) {
			part = skin.getStoryPart(minecraft.level.registryAccess());
		}
		return new View(id, name, icon, StoryPart.getId(part), part != null ? part.value().getPartName() : null,
				part != null ? part.value().getPartIconAsText() : null, type != null ? type.discStoryPartPriority : 100);
	}

	/** The parts the Stands are in, in their order, each with its name; a null key for the Stands of no part. */
	Map<ResourceLocation, Component> parts(List<ResourceLocation> stands) {
		List<View> sorted = new ArrayList<>(stands.stream().map(this::view).toList());
		sorted.sort(ORDER);
		Map<ResourceLocation, Component> parts = new LinkedHashMap<>();
		for (View view : sorted) {
			if (!parts.containsKey(view.part())) {
				parts.put(view.part(), view.partName());
			}
		}
		return parts;
	}

	static boolean inPart(View view, @Nullable String partFilter) {
		return partFilter == null || Objects.equals(partFilter, view.part() != null ? view.part().toString() : "");
	}
}
