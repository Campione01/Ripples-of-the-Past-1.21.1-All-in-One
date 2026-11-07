package rotp.core.client.ui;

import rotp.core.core.JojoMod;
import rotp.core.init.CreativeTabSections;
import rotp.core.init.CreativeTabSections.Header;
import rotp.core.init.SectionedCreativeTab;
import rotp.core.mixin.client.screen.CreativeScreenScrollAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/** Draws each section's name on the empty row the tab left for it, in the colours of the creative window. */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class CreativeTabSectionLabels {
	private static final int GRID_X = 8;
	private static final int GRID_Y = 17;
	private static final int CELL = 18;
	private static final int VISIBLE_ROWS = 5;
	private static final int WINDOW_GREY = 0xFFC6C6C6;
	private static final int LINE_GREY = 0xFF8B8B8B;

	private CreativeTabSectionLabels() {}

	@SubscribeEvent
	public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
		if (!(event.getContainerScreen() instanceof CreativeModeInventoryScreen screen)
				|| !(CreativeModeInventoryScreen.selectedTab instanceof SectionedCreativeTab tab)) {
			return;
		}
		GuiGraphics graphics = event.getGuiGraphics();
		int firstRow = CreativeTabSections.firstVisibleRow(
				((CreativeScreenScrollAccessor) screen).jojo_ripples$scrollOffs(), screen.getMenu().items.size());
		for (Header<Component> header : tab.getHeaders()) {
			int row = header.row() - firstRow;
			if (row >= 0 && row < VISIBLE_ROWS) {
				int y = GRID_Y + row * CELL;
				int right = GRID_X + CreativeTabSections.ROW_LENGTH * CELL;
				graphics.fill(GRID_X, y, right, y + CELL, WINDOW_GREY);
				graphics.fill(GRID_X, y + CELL - 2, right, y + CELL - 1, LINE_GREY);
				graphics.drawString(Minecraft.getInstance().font, header.name(), GRID_X + 1, y + 6, tab.getLabelColor(), false);
			}
		}
	}
}
