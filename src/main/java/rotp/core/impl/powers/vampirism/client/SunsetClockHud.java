package rotp.core.impl.powers.vampirism.client;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.SunsetClock;
import rotp.core.mechanics.JojoDefinitions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** A clock in the hand of someone the sun burns tells the time left to sunset, above the held item's name. */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class SunsetClockHud {
	private static final int COLOR = 0xFFFFB040;

	private SunsetClockHud() {}

	@SubscribeEvent
	public static void renderTimeToSunset(RenderGuiLayerEvent.Post event) {
		if (!event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null || mc.options.hideGui || !level.dimensionType().natural()
				|| !player.isHolding(Items.CLOCK) || !JojoDefinitions.isUndeadOrVampiric(player)) {
			return;
		}
		int ticks = SunsetClock.ticksToSunset(level.getDayTime());
		if (ticks >= 0) {
			GuiGraphics guiGraphics = event.getGuiGraphics();
			Component text = Component.translatable("jojo_ripples.clock.sunset_in",
					StringUtil.formatTickDuration(ticks, level.tickRateManager().tickrate()));
			guiGraphics.drawCenteredString(mc.font, text, guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() - 71, COLOR);
		}
	}
}
