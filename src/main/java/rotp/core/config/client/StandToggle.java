package rotp.core.config.client;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

import rotp.core.core.JojoMod;

/**
 * What a player lets their Stand do, from the JoJo menu or a key they bind. Every toggle is on by default, which is the
 * game as it plays without them: an off toggle takes something away, and what it takes is read on the server from the
 * settings the client broadcasts ({@link PlayerClientBroadcastedSettings#isStandToggleOn}).
 */
public enum StandToggle {
	BREAK_BLOCKS("break_blocks", settings -> settings.standBreaksBlocks, (settings, on) -> settings.standBreaksBlocks = on,
			settings -> settings.showBreakBlocksToggle),
	PICK_UP_ITEMS("pick_up_items", settings -> settings.standPicksUpItems, (settings, on) -> settings.standPicksUpItems = on,
			settings -> settings.showPickUpItemsToggle),
	BEYOND_EFFECTIVE_RANGE("beyond_effective_range", settings -> settings.standMovesBeyondEffectiveRange,
			(settings, on) -> settings.standMovesBeyondEffectiveRange = on, settings -> settings.showBeyondRangeToggle);

	/** Each icon's side in textures/hud/stand_toggles.png: a column per toggle, the on row above the off row. */
	public static final int ICON_SIZE = 16;

	public final String id;
	private final Predicate<PlayerClientBroadcastedSettings> getter;
	private final BiConsumer<PlayerClientBroadcastedSettings, Boolean> setter;
	private final Predicate<ClientModSettings.Settings> alwaysShown;

	StandToggle(String id, Predicate<PlayerClientBroadcastedSettings> getter, BiConsumer<PlayerClientBroadcastedSettings, Boolean> setter,
			Predicate<ClientModSettings.Settings> alwaysShown) {
		this.id = id;
		this.getter = getter;
		this.setter = setter;
		this.alwaysShown = alwaysShown;
	}

	public boolean isOn(PlayerClientBroadcastedSettings settings) {
		return getter.test(settings);
	}

	/** The official version's "show in HUD": the icon stays while the Stand's controls are shown, not only after its key. */
	public boolean isAlwaysShown(ClientModSettings.Settings settings) {
		return alwaysShown.test(settings);
	}

	public void flip(PlayerClientBroadcastedSettings settings) {
		setter.accept(settings, !isOn(settings));
	}

	public String langKey() {
		return JojoMod.MOD_ID + ".stand_toggles." + id;
	}

	public int iconU() {
		return ordinal() * ICON_SIZE;
	}

	public int iconV(boolean on) {
		return on ? 0 : ICON_SIZE;
	}
}
