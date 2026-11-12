package rotp.core.client.ui.hud_power;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.input.InputHandler;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.client.ui.utils.ElementTransparency;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.StandToggle;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A toggle's icon, on or off, for two seconds after its key flipped it, or all the time the Stand's controls are shown
 * when the settings say so (the official version's "show in HUD"). Hovered, an icon names its toggle and its state.
 */
public class StandTogglesHud extends HudElement {
	public static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/hud/stand_toggles.png");
	private static final int ICON_STEP = StandToggle.ICON_SIZE + 2;
	private static final int TEXTURE_WIDTH = StandToggle.values().length * StandToggle.ICON_SIZE;
	private static final int TEXTURE_HEIGHT = 2 * StandToggle.ICON_SIZE;

	private final Map<StandToggle, ElementTransparency> shown = new EnumMap<>(StandToggle.class);
	private final List<StandToggle> drawn = new ArrayList<>();

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

	private boolean isAlwaysShown(StandToggle toggle) {
		return toggle.isAlwaysShown(ClientModSettings.getSettingsReadOnly()) && standControlsShown();
	}

	private static boolean standControlsShown() {
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		InputHandler input = InputHandler.getInstance();
		if (standPower == null || !standPower.hasPower() || input == null) {
			return false;
		}
		ClientControlScheme controls = input.getActiveControlScheme();
		return controls != null && controls.hasAbility(ability -> ability.powerClass() == PowerClass.STAND);
	}

	@Override
	public boolean shouldRender() {
		if (hud.forContainerMenu.isTrue()) return false;
		for (StandToggle toggle : StandToggle.values()) {
			ElementTransparency fade = shown.get(toggle);
			if (fade != null && fade.shouldRender() || isAlwaysShown(toggle)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		float partialTick = ClientUtil.partialTick(deltaTracker, false);
		int x = getX();
		drawn.clear();
		for (StandToggle toggle : StandToggle.values()) {
			ElementTransparency fade = shown.get(toggle);
			float visibility = isAlwaysShown(toggle) ? 1.0F
					: fade != null && fade.shouldRender() ? fade.getAlpha(partialTick) : 0.0F;
			if (visibility > 0) {
				boolean on = toggle.isOn(ClientModSettings.getSettingsReadOnly().broadcasted);
				int alpha = Mth.floor(visibility * 255) << 24;
				BlitFloat.blit(guiGraphics.pose(), mc, TEXTURE,
						x, getY(), StandToggle.ICON_SIZE, StandToggle.ICON_SIZE, 0,
						toggle.iconU(), toggle.iconV(on), StandToggle.ICON_SIZE, StandToggle.ICON_SIZE, TEXTURE_WIDTH, TEXTURE_HEIGHT,
						alpha | 0xFFFFFF);
				drawn.add(toggle);
				x += ICON_STEP;
			}
		}
	}

	@Override
	protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {
		StandToggle hovered = null;
		int fromLeft = (int) mouseX - getX();
		if (isHovered && fromLeft >= 0 && fromLeft % ICON_STEP < StandToggle.ICON_SIZE && fromLeft / ICON_STEP < drawn.size()) {
			hovered = drawn.get(fromLeft / ICON_STEP);
		}
		if (hovered != null) {
			boolean on = hovered.isOn(ClientModSettings.getSettingsReadOnly().broadcasted);
			tooltip.set(Tooltip.create(Component.translatable(hovered.langKey(),
					Component.translatable(on ? "options.on" : "options.off")).withStyle(ChatFormatting.BLACK)));
		}
		else {
			tooltip.set(tooltipText);
		}
		super.checkTooltip(mouseX, mouseY, deltaTracker);
	}
}
