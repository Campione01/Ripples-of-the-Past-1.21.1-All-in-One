package rotp.core.client.ui.hud_misc;

import rotp.core.client.ClientGlobals;
import rotp.core.client.ClientPowerCache;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.config.client.ClientModSettings;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.powersystem.standpower.type.StandTypePersistentData.StandExpSummary;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The standExpBar client setting: with the Stand out, the experience bar's place shows how far the Stand has developed
 * (the skills screen's total, tinted with the skin's colour) and its number the exp there is to spend.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class StandExpBar {
	public static final int WIDTH = 182;
	public static final int HEIGHT = 5;
	private static final ResourceLocation BACKGROUND = JojoMod.resLoc("textures/hud/bars/stand_exp_bar_background.png");
	private static final ResourceLocation PROGRESS = JojoMod.resLoc("textures/hud/bars/stand_exp_bar_progress.png");

	private StandExpBar() {}

	/** A Stand with no exp to earn leaves the vanilla bar, and so does a mode or a vehicle that shows no experience bar. */
	public static boolean replacesVanillaBar(boolean setting, boolean standOut, boolean experienceBarVisible, int totalExp) {
		return setting && standOut && experienceBarVisible && totalExp > 0;
	}

	public static int filledPixels(int spentExp, int exp, int totalExp) {
		if (totalExp <= 0) {
			return 0;
		}
		long developed = (long) spentExp + Math.max(Math.min(exp, totalExp - spentExp), 0);
		return (int) Math.max(Math.min(developed * WIDTH / totalExp, WIDTH), 0);
	}

	@SubscribeEvent
	public static void replaceExperienceBar(RenderGuiLayerEvent.Pre event) {
		ResourceLocation layer = event.getName();
		boolean bar = layer.equals(VanillaGuiLayers.EXPERIENCE_BAR);
		if (!bar && !layer.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || mc.options.hideGui
				|| !ClientModSettings.getSettingsReadOnly().standExpBar) {
			return;
		}
		StandPower power = ClientPowerCache.getPower(PowerClass.STAND);
		StandTypePersistentData data = power != null && power.hasPower() ? power.getCurTypeData() : null;
		if (data == null) {
			return;
		}
		StandExpSummary summary = data.expSummary(power);
		int spent = summary.spent;
		int total = summary.total;
		boolean experienceBarVisible = mc.player.jumpableVehicle() == null && mc.gameMode.hasExperience();
		if (!replacesVanillaBar(true, isStandOut(power), experienceBarVisible, total)) {
			return;
		}
		event.setCanceled(true);
		StandSkin skin = StandSkinsLoader.getCurSkin();
		int color = skin != null ? skin.getColor() : BlitFloat.NO_TINT;
		if (bar) {
			renderBar(event.getGuiGraphics(), mc, filledPixels(spent, data.getExp(), total), color);
		}
		else {
			int left = total - spent;
			Component number = data.getExp() < left ? Component.literal(String.valueOf(data.getExp()))
					: Component.translatable("jojo_ripples.stand_exp.max");
			renderNumber(event.getGuiGraphics(), mc.font, number, color);
		}
	}

	private static boolean isStandOut(StandPower power) {
		if (!power.isSummoned() || ClientGlobals.isPlayerStandFullBodyUnsummoning()) {
			return false;
		}
		StandEntity stand = power.getSummonedStandEntity();
		return stand == null || !stand.isArmsOnlyMode();
	}

	private static void renderBar(GuiGraphics guiGraphics, Minecraft mc, int filled, int color) {
		int x = guiGraphics.guiWidth() / 2 - 91;
		int y = guiGraphics.guiHeight() - 29;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		BlitFloat.blit(guiGraphics.pose(), mc, BACKGROUND, x, y, WIDTH, HEIGHT, 0, BlitFloat.NO_TINT);
		if (filled > 0) {
			BlitFloat.blit(guiGraphics.pose(), mc, PROGRESS, x, y, filled, HEIGHT, 0,
					0, 0, filled, HEIGHT, WIDTH, HEIGHT, color);
		}
	}

	private static void renderNumber(GuiGraphics guiGraphics, Font font, Component number, int color) {
		int x = (guiGraphics.guiWidth() - font.width(number)) / 2;
		int y = guiGraphics.guiHeight() - 35;
		guiGraphics.drawString(font, number, x + 1, y, 0, false);
		guiGraphics.drawString(font, number, x - 1, y, 0, false);
		guiGraphics.drawString(font, number, x, y + 1, 0, false);
		guiGraphics.drawString(font, number, x, y - 1, 0, false);
		guiGraphics.drawString(font, number, x, y, color, false);
	}
}
