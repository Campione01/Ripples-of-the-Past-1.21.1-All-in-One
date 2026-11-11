package rotp.core.config.client.screen;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A paper card over the dimmed settings screen. The screen behind keeps its stores running. */
abstract class PaperDialog extends Screen {
	protected final ModConfigScreen parent;
	protected int cardX;
	protected int cardY;
	protected int cardWidth;
	protected int cardHeight;

	PaperDialog(ModConfigScreen parent, Component title) {
		super(title);
		this.parent = parent;
	}

	protected void placeCard(int wantedWidth, int wantedHeight) {
		cardWidth = Math.min(wantedWidth, width - 16);
		cardHeight = Math.min(wantedHeight, height - 8);
		cardX = (width - cardWidth) / 2;
		cardY = (height - cardHeight) / 2;
	}

	@Override
	protected void init() {
		parent.resize(minecraft, width, height);
	}

	// a card rebuilt for its next state (waiting, asking to discard) keeps the place in its list, as the screen does
	@Override
	protected void setInitialFocus() {
		if (!focusedOnce) {
			focusedOnce = true;
			super.setInitialFocus();
		}
	}

	private boolean focusedOnce;

	// a button that rebuilds the card from its own press is gone when the click hands it the focus: Enter must not press it again
	@Override
	public void setFocused(@Nullable GuiEventListener listener) {
		if (listener == null || children().contains(listener)) {
			super.setFocused(listener);
		}
	}

	@Override
	public void tick() {
		parent.stepStores(Util.getMillis());
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return parent.isPauseScreen();
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		parent.renderBehindDialog(graphics, partialTick);
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 500);
		graphics.fill(0, 0, width, height, 0x66101018);
		graphics.blitSprite(PaperUi.WINDOW, cardX, cardY, cardWidth, cardHeight);
		graphics.pose().popPose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 500);
		renderCardBehindWidgets(graphics, mouseX, mouseY, partialTick);
		for (var renderable : renderables) {
			renderable.render(graphics, mouseX, mouseY, partialTick);
		}
		renderCard(graphics, mouseX, mouseY, partialTick);
		graphics.pose().popPose();
	}

	protected void renderCardBehindWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	protected void renderCard(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	protected void centered(GuiGraphics graphics, String text, int y, int color) {
		String shown = PaperUi.oneLine(font, text, cardWidth - 20);
		PaperUi.text(graphics, font, Component.literal(shown), cardX + (cardWidth - font.width(shown)) / 2, y, color);
	}

	protected void heading(GuiGraphics graphics, int y) {
		String shown = PaperUi.oneLine(font, title.getString(), cardWidth - 24);
		Component bold = Component.literal(shown).withStyle(ChatFormatting.BOLD);
		PaperUi.text(graphics, font, bold, cardX + (cardWidth - font.width(bold)) / 2, y, PaperUi.INK);
	}
}
