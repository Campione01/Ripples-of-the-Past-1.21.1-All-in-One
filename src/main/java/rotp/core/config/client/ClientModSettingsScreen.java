package rotp.core.config.client;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.client.ui.screen_widgets.ButtonInLayout;
import rotp.core.config.client.screen.ModConfigScreen;
import rotp.core.core.JojoMod;
import rotp.core.util.reflection.ClientReflection;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** The J button on vanilla's Options screen, where players have always found the mod's settings. */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class ClientModSettingsScreen {

	private ClientModSettingsScreen() {}

	public static ResourceLocation toIconPath(String fileName) {
		return JojoMod.resLoc("textures/gui/sprites/settings/" + fileName + ".png");
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void addToScreen(ScreenEvent.Init.Post event) {
		Screen screen = event.getScreen();
		if (screen instanceof OptionsScreen optionsScreen) {
			List<AbstractWidget> otherModdedButtons = event.getListenersList().stream()
					.filter(b -> b instanceof AbstractWidget)
					.map(b -> (AbstractWidget) b)
					.toList();

			Component tooltip = Component.translatable("jojo_ripples.config.screen.title");
			Button settingsButton = Button.builder(Component.literal("J"),
					__ -> optionsScreen.getMinecraft().setScreen(new ModConfigScreen(optionsScreen, ModConfigScreen.Page.CLIENT)))
					.bounds(-1, -1, 20, 20)
					.tooltip(Tooltip.create(tooltip))
					.build();
			Layout fuckTheseAbstractions = new ButtonInLayout(settingsButton, button -> {
				int[] buttonPos = findPosForButton(optionsScreen, otherModdedButtons, button);
				button.setX(buttonPos[0]);
				button.setY(buttonPos[1]);
			});
			ClientReflection.getLayout(optionsScreen).addToContents(fuckTheseAbstractions);
			fuckTheseAbstractions.arrangeElements();
			event.addListener(settingsButton);
		}
	}

    private static int[] findPosForButton(Screen optionsScreen, List<AbstractWidget> otherModdedButtons, AbstractWidget except) {
		final int minY = 87;
		final int maxY = minY + 100;

		final int minX1 = 0;
		final int maxX1 = optionsScreen.width / 2 - 155 - 20 - 5;
		final int minX2 = optionsScreen.width / 2 + 160;
		final int maxX2 = optionsScreen.width - 20;

		final int minX3 = optionsScreen.width / 2 - 155;
		final int maxX3 = minX3 + 290;
		final int y3 = maxY + 24;

		int[] buttonPos = null;

		// try placing the button to the right side
		for (int x = minX2; x <= maxX2 && buttonPos == null; x += 25) {
			int y = maxY;
			if (ModList.get().isLoaded("essential")) y -= 24; // for fuck's sake
			for (; y >= minY && buttonPos == null; y -= 24) {
				buttonPos = noOverlapPos(otherModdedButtons, except, x, y);
			}
		}
		// ...or to the left side
		if (buttonPos == null) {
			for (int x = maxX1; x >= minX1 && buttonPos == null; x -= 25) {
				for (int y = maxY; y >= minY && buttonPos == null; y -= 24) {
					buttonPos = noOverlapPos(otherModdedButtons, except, x, y);
				}
			}
		}
		// ...or below the vanilla options
		if (buttonPos == null) {
			for (int x = minX3; x <= maxX3 && buttonPos == null; x += 29) {
				buttonPos = noOverlapPos(otherModdedButtons, except, x, y3);
			}
		}
		// ...how many new buttons are there?? fuck it, just put it at the "Done" button
		if (buttonPos == null) {
			buttonPos = new int[] { optionsScreen.width / 2 + 110, optionsScreen.height - 26 };
		}

		return buttonPos;
    }

	@Nullable
	private static int[] noOverlapPos(List<AbstractWidget> buttonsList, AbstractWidget except, int x, int y) {
		int x2 = x + 20;
		int y2 = y + 20;
		return buttonsList.stream().filter(button -> button != except).anyMatch(button -> {
			return button.getX() < x2 && button.getX() + button.getWidth() > x && button.getY() < y2 && button.getY() + button.getHeight() > y;
		}) ? null : new int[] { x, y };
	}

}
