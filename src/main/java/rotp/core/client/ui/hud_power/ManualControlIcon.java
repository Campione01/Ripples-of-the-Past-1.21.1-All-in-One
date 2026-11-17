package rotp.core.client.ui.hud_power;

import java.util.List;

import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.client.ui.utils.GuiIcon;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.powersystem.standpower.StandPower;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.FastColor.ARGB32;

/**
 * The remote control ability's icon: the user's face and the Stand's icon, offset and overlapping. Out of control the
 * Stand is in front of the user; under control the user is in front of a faint Stand.
 */
public final class ManualControlIcon {
	public static final int SIZE = 16;
	public static final int FACE_BEHIND_TINT = 0x80FFFFFF;
	public static final int FAINT_STAND_TINT = 0x40FFFFFF;

	private ManualControlIcon() {}

	public enum Head { USER, STAND }

	/** What of a head shows in the icon's square; the UVs are fractions of the head's own picture. */
	public record Layer(Head head, float x, float y, float width, float height,
			float minU, float minV, float maxU, float maxV, int tint) {}

	private static final List<Layer> OUT_OF_CONTROL = List.of(
			layer(Head.USER, 8, 0, SIZE / 2, FACE_BEHIND_TINT),
			layer(Head.STAND, -2, 0, SIZE, BlitFloat.NO_TINT));
	private static final List<Layer> UNDER_CONTROL = List.of(
			layer(Head.STAND, 4, -4, SIZE, FAINT_STAND_TINT),
			layer(Head.USER, 2, 5, SIZE / 2, BlitFloat.NO_TINT));

	/** @return the layers from back to front */
	public static List<Layer> layers(boolean underControl) {
		return underControl ? UNDER_CONTROL : OUT_OF_CONTROL;
	}

	// cut by UV rather than with a scissor, so the cut follows any pose and leaves the scissor stack alone
	private static Layer layer(Head head, float x, float y, float size, int tint) {
		float minX = Math.max(x, 0);
		float minY = Math.max(y, 0);
		float maxX = Math.min(x + size, SIZE);
		float maxY = Math.min(y + size, SIZE);
		return new Layer(head, minX, minY, maxX - minX, maxY - minY,
				(minX - x) / size, (minY - y) / size, (maxX - x) / size, (maxY - y) / size, tint);
	}

	/** @param color the icon's colour from the caller: the unusable look and the HUD's fade stay on every layer */
	public static int layerColor(int color, Layer layer) {
		return ARGB32.multiply(color, layer.tint());
	}

	/**
	 * @return false if there is no player's face or no Stand icon to draw (a mob's power, no Stand yet), so the caller
	 * draws the picture instead
	 */
	public static boolean render(StandPower standPower, GuiGraphics guiGraphics, boolean underControl, float x, float y, int color) {
		GuiIcon standIcon = PowerHud.getStandIcon(standPower);
		if (standIcon == null || !(standPower.getUser() instanceof AbstractClientPlayer player)) {
			return false;
		}
		PoseStack pose = guiGraphics.pose();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		for (Layer layer : layers(underControl)) {
			float layerX = x + layer.x();
			float layerY = y + layer.y();
			int layerColor = layerColor(color, layer);
			if (layer.head() == Head.USER) {
				ClientUtil.renderPlayerFace(pose, layerX, layerY, layer.width(), player, layerColor, false);
			}
			else {
				standIcon.renderPart(pose, layerX, layerY, layer.width(), layer.height(),
						layer.minU(), layer.minV(), layer.maxU(), layer.maxV(), layerColor);
			}
		}
		return true;
	}
}
