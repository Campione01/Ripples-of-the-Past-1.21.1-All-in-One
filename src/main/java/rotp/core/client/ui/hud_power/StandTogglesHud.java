package rotp.core.client.ui.hud_power;

import java.util.EnumMap;
import java.util.Map;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.client.ui.utils.ElementTransparency;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.StandToggle;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A toggle's icon, on or off, for two seconds after its key flipped it. */
public class StandTogglesHud extends HudElement {
	public static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/hud/stand_toggles.png");
	private static final int ICON_STEP = StandToggle.ICON_SIZE + 2;
	private static final int TEXTURE_WIDTH = StandToggle.values().length * StandToggle.ICON_SIZE;
	private static final int TEXTURE_HEIGHT = 2 * StandToggle.ICON_SIZE;

	private final Map<StandToggle, ElementTransparency> shown = new EnumMap<>(StandToggle.class);

	public StandTogglesHud(String name, int x0, int y0) {
		super(name, x0, y0, StandToggle.values().length * ICON_STEP, StandToggle.ICON_SIZE);
	}

	/** The key of a toggle was pressed: a player with no Stand has nothing to toggle. */
	public void flipByKey(StandToggle toggle) {
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		if (standPower != null && standPower.hasPower()) {
			ClientModSettings.edit(settings -> toggle.flip(settings.broadcasted), true);
			shown.computeIfAbsent(toggle, __ -> new ElementTransparency()).reset();
		}
	}

	@Override
	public boolean shouldRender() {
		if (hud.forContainerMenu.isTrue()) return false;
		return shown.values().stream().anyMatch(ElementTransparency::shouldRender);
	}

	@Override
	public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		float partialTick = ClientUtil.partialTick(deltaTracker, false);
		int x = getX();
		for (StandToggle toggle : StandToggle.values()) {
			ElementTransparency fade = shown.get(toggle);
			if (fade != null && fade.shouldRender()) {
				boolean on = toggle.isOn(ClientModSettings.getSettingsReadOnly().broadcasted);
				int alpha = Mth.floor(fade.getAlpha(partialTick) * 255) << 24;
				BlitFloat.blit(guiGraphics.pose(), mc, TEXTURE,
						x, getY(), StandToggle.ICON_SIZE, StandToggle.ICON_SIZE, 0,
						toggle.iconU(), toggle.iconV(on), StandToggle.ICON_SIZE, StandToggle.ICON_SIZE, TEXTURE_WIDTH, TEXTURE_HEIGHT,
						alpha | 0xFFFFFF);
				x += ICON_STEP;
			}
		}
	}
}
