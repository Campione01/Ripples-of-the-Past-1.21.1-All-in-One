package rotp.core.client.ui.hud_power;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** Stamina still filling up with the Stand's own bar out of sight (the Stand recalled, another power's HUD open). */
public class StaminaMiniBar extends HudElement {

	public StaminaMiniBar(String name, int x0, int y0) {
		super(name, x0, y0, Bars.HORIZONTAL_LENGTH_MINI, Bars.HORIZONTAL_WIDTH_MINI);
	}

	public static boolean shows(boolean usesStamina, boolean creative, boolean fullBarShown, float staminaRatio) {
		return usesStamina && !creative && !fullBarShown && staminaRatio < 1;
	}

	@Override
	protected void initText() {}

	@Override
	public boolean shouldRender() {
		if (hud.forContainerMenu.isTrue()) return false;
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		return standPower != null && shows(standPower.usesStamina(), standPower.isUserCreative(),
				hud.staminaBar.shouldRender(), standPower.getStaminaRatio());
	}

	@Override
	public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		float staminaRatio = standPower.getStaminaRatio(ClientUtil.partialTick(deltaTracker, false));
		Bars.renderHorizontalBarMini(guiGraphics.pose(), getX(), getY(), staminaRatio,
				PowerHud.Stamina.BAR_HORIZONTAL_MINI_FILL, BlitFloat.NO_TINT, 1);
	}

	@Override
	protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {}
}
