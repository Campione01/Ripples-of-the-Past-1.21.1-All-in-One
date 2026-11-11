package rotp.core.config.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.CoreConfigPresets;
import rotp.core.config.editor.client.ConfigStore;
import rotp.core.config.editor.client.ConfigWidgetRules;

/** The lines of a setting's tooltip: what it does, its limits, its default, and what its flags mean. */
final class ConfigTooltips {
	private static final String TEXT = "jojo_ripples.config.screen.";
	private static final double ABOVE_ZERO_BELOW = 1e-100;

	private ConfigTooltips() {}

	static List<Component> forEntry(ConfigEntry entry, ConfigStore store) {
		List<Component> lines = new ArrayList<>();
		lines.add(PaperUi.label(entry).copy().withStyle(ChatFormatting.BOLD));
		String description = Language.getInstance().getOrDefault(ConfigWidgetRules.tooltipKey(entry), "");
		if (!description.isBlank()) {
			lines.add(Component.literal(description));
		}
		String needs = ConfigWidgetRules.enabledBy(entry.key());
		ConfigEntry needed = needs != null ? store.entry(needs) : null;
		if (needed != null && !needed.value().getAsBoolean()) {
			lines.add(Component.translatable(TEXT + "needs", PaperUi.label(needed)));
		}
		Component range = rangeLine(entry);
		if (range != null) {
			lines.add(range);
		}
		if (entry.kind() != ConfigEntry.Kind.LIST) {
			lines.add(Component.translatable(TEXT + "default", PaperUi.valueText(entry, entry.defaultValue())));
		}
		if (entry.synced()) {
			lines.add(Component.translatable(TEXT + (entry.side() == ConfigEntry.Side.CLIENT ? "flag.sent_to_server" : "flag.sent_to_players")));
		}
		switch (entry.restart()) {
			case WORLD -> lines.add(Component.translatable(TEXT + "flag.restart_world"));
			case GAME -> lines.add(Component.translatable(TEXT + "flag.restart_game"));
			case NONE -> {}
		}
		return lines;
	}

	static Component rangeOf(ConfigEntry.Kind kind, Bounds bounds) {
		return rangeLineOf(kind, bounds);
	}

	private static Component rangeLine(ConfigEntry entry) {
		if (entry.kind() == ConfigEntry.Kind.LIST || entry.bounds() == null) {
			return null;
		}
		if (ConfigWidgetRules.isPercent(entry)) {
			return Component.translatable(TEXT + "range", number(entry.kind(), entry.bounds().minimum()) + "%",
					number(entry.kind(), entry.bounds().maximum()) + "%");
		}
		return rangeLineOf(entry.kind(), entry.bounds());
	}

	private static Component rangeLineOf(ConfigEntry.Kind kind, Bounds bounds) {
		if (bounds.minimum() > 0 && bounds.minimum() < ABOVE_ZERO_BELOW) {
			return Component.translatable(TEXT + "range_above_zero");
		}
		String minimum = number(kind, bounds.minimum());
		if (bounds.maximum() >= Integer.MAX_VALUE) {
			return Component.translatable(TEXT + "range_from", minimum);
		}
		return Component.translatable(TEXT + "range", minimum, number(kind, bounds.maximum()));
	}

	private static String number(ConfigEntry.Kind kind, double value) {
		return ConfigWidgetRules.formatNumber(kind, new com.google.gson.JsonPrimitive(
				kind == ConfigEntry.Kind.INTEGER ? (Number) (int) value : (Number) value));
	}

	static Component presetName(CoreConfigPresets.Preset preset) {
		return Component.translatableWithFallback("jojo_ripples.config.preset." + preset.id(), preset.name());
	}

	static List<Component> forPreset(CoreConfigPresets.Preset preset, boolean editable) {
		List<Component> lines = new ArrayList<>();
		lines.add(presetName(preset).copy().withStyle(ChatFormatting.BOLD));
		String description = Language.getInstance().getOrDefault("jojo_ripples.config.preset." + preset.id() + ".desc", "");
		if (!description.isBlank()) {
			lines.add(Component.literal(description));
		}
		lines.add(Component.translatable(editable ? TEXT + "preset_shows_first" : TEXT + "preset_read_only"));
		return lines;
	}

	static List<Component> forPresetDefaults(boolean editable) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("jojo_ripples.config.preset.defaults").withStyle(ChatFormatting.BOLD));
		lines.add(Component.translatable("jojo_ripples.config.preset.defaults.desc"));
		lines.add(Component.translatable(editable ? TEXT + "preset_shows_first" : TEXT + "preset_read_only"));
		return lines;
	}
}
