package rotp.core.mechanics.explorer_map_creation.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.core.client.ui.screen_widgets.IconButton;
import rotp.core.client.ui.utils.GuiIcon;
import rotp.core.core.JojoMod;
import rotp.core.mechanics.explorer_map_creation.ClExplorerMapModePacket;
import rotp.core.mechanics.explorer_map_creation.ExplorerMapMenu;

/** Two tabs above the cartography table: vanilla's mode and the explorer map mode. */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class ExplorerMapModeButtons {
	public static final GuiIcon VANILLA_ICON = new GuiIcon(JojoMod.resLoc("textures/gui/cartography_vanilla.png"), 16, 16);
	public static final GuiIcon EXPLORER_ICON = new GuiIcon(JojoMod.resLoc("textures/gui/cartography_explorer.png"), 16, 16);

	private ExplorerMapModeButtons() {}

	@SubscribeEvent
	public static void onCartographyScreenInit(ScreenEvent.Init.Post event) {
		if (event.getScreen().getClass() == CartographyTableScreen.class) {
			for (Button button : create((AbstractContainerScreen<?>) event.getScreen(), false)) {
				event.addListener(button);
			}
		}
	}

	public static Button[] create(AbstractContainerScreen<?> screen, boolean explorerOpen) {
		int x = screen.getGuiLeft();
		int y = screen.getGuiTop() - 20;
		Button vanilla = new IconButton(x, y, 20, 20, VANILLA_ICON,
				button -> PacketDistributor.sendToServer(new ClExplorerMapModePacket(false)), Tooltip.create(ExplorerMapMenu.VANILLA_TITLE));
		Button explorer = new IconButton(x + 20, y, 20, 20, EXPLORER_ICON,
				button -> PacketDistributor.sendToServer(new ClExplorerMapModePacket(true)), Tooltip.create(ExplorerMapMenu.EXPLORER_TITLE));
		vanilla.active = explorerOpen;
		explorer.active = !explorerOpen;
		return new Button[] { vanilla, explorer };
	}
}
