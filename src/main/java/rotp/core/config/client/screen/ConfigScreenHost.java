package rotp.core.config.client.screen;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.CoreConfigPresets;
import rotp.core.config.editor.client.ConfigStore;

/** What the rows ask of the screen they are on. */
public interface ConfigScreenHost {
	ConfigStore store();

	Font font();

	boolean editable();

	void showTooltip(List<Component> lines);

	/** Milliseconds since the store last refused this setting, or -1. */
	long refusalAge(String key);

	void openList(ConfigEntry entry);

	/** A typed value the setting cannot take: it was never sent, and the player is told why. */
	void typedValueRejected(ConfigEntry entry);

	void rowsChanged();

	void confirmResetKeys(Component title, List<String> keys);

	void confirmPreset(CoreConfigPresets.Preset preset);

	void confirmPresetDefaults();
}
