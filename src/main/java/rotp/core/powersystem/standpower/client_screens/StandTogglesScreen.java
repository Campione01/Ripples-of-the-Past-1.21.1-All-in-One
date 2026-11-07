package rotp.core.powersystem.standpower.client_screens;

import java.util.EnumMap;
import java.util.Map;

import rotp.core.client.ui.hud_power.StandTogglesHud;
import rotp.core.client.ui.screen_jojomenu.IJojoMenuScreen;
import rotp.core.client.ui.screen_jojomenu.Tab;
import rotp.core.client.ui.screen_jojomenu.TabCategory;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.StandToggle;
import rotp.core.core.JojoMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class StandTogglesScreen extends Screen implements IJojoMenuScreen {
	private static final ResourceLocation WINDOW = JojoMod.resLoc("textures/gui/paper_style/empty3.png");
	private static final int ROW_STEP = 24;

	private final TabCategory category;
	private final Tab tab;
	private final Map<StandToggle, Button> buttons = new EnumMap<>(StandToggle.class);

	public StandTogglesScreen(TabCategory category, Tab tab) {
		super(Component.translatable("jojo_ripples.menu.stand.toggles"));
		this.category = category;
		this.tab = tab;
	}

	@Override
	protected void init() {
		buttons.clear();
		int x = iconX() + StandToggle.ICON_SIZE + 4;
		int y = rowsY();
		int width = getWindowWidth() - 40 - StandToggle.ICON_SIZE - 4;
		for (StandToggle toggle : StandToggle.values()) {
			buttons.put(toggle, addRenderableWidget(Button.builder(message(toggle), button -> {
				ClientModSettings.edit(settings -> toggle.flip(settings.broadcasted), true);
				button.setMessage(message(toggle));
			}).bounds(x, y + toggle.ordinal() * ROW_STEP, width, 20)
					.tooltip(Tooltip.create(Component.translatable(toggle.langKey() + ".tooltip")))
					.build()));
		}
	}

	private int iconX() {
		return getWindowX(this) + 20;
	}

	private int rowsY() {
		return getWindowY(this) + 36;
	}

	private static boolean isOn(StandToggle toggle) {
		return toggle.isOn(ClientModSettings.getSettingsReadOnly().broadcasted);
	}

	private static Component message(StandToggle toggle) {
		return Component.translatable(toggle.langKey(), Component.translatable(isOn(toggle) ? "options.on" : "options.off"));
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		// the menu blur takes in all that is drawn before it: the window goes on top of it, under the controls
		super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), WINDOW,
				getWindowX(this), getWindowY(this), getWindowWidth(), getWindowHeight(), 0,
				0, 0, getWindowWidth(), getWindowHeight(), 256, 256, BlitFloat.NO_TINT);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(font, title, width / 2, getWindowY(this) + 16, 0xFF202020);
		for (StandToggle toggle : StandToggle.values()) {
			int size = StandToggle.ICON_SIZE;
			BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), StandTogglesHud.TEXTURE,
					iconX(), rowsY() + toggle.ordinal() * ROW_STEP + 2, size, size, 0,
					toggle.iconU(), toggle.iconV(isOn(toggle)), size, size, StandToggle.values().length * size, 2 * size,
					BlitFloat.NO_TINT);
		}
		renderTabs(guiGraphics, this);
		renderTabTooltip(guiGraphics, this, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (clickTab(mouseX, mouseY, button, this)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public TabCategory getTabCategory() {
		return category;
	}

	@Override
	public Tab getTab() {
		return tab;
	}
}
