package rotp.core.config.client.screen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.util.Mth;

/** A list on paper: no dark backdrop, a thin scroll bar of the window's own greys, and a wheel that glides. */
abstract class PaperList<E extends ContainerObjectSelectionList.Entry<E>> extends ContainerObjectSelectionList<E> {
	private static final int BAR_WIDTH = 4;
	private static final double GLIDE_PER_SECOND = 18;

	private double glideTarget;
	private boolean gliding;
	private boolean gliderMoving;
	private long lastFrame;

	PaperList(Minecraft minecraft, int x, int y, int width, int height, int rowHeight) {
		super(minecraft, width, height, y, rowHeight);
		setX(x);
		centerListVertically = false;
	}

	@Override
	public int getRowWidth() {
		return width - 12;
	}

	@Override
	public int getRowLeft() {
		return getX() + 6;
	}

	@Override
	protected int getScrollbarPosition() {
		return getRight() - 6;
	}

	// the base class draws vanilla's bar when this says so; ours is in renderDecorations, the dragging works the same
	@Override
	protected boolean scrollbarVisible() {
		return false;
	}

	@Override
	protected void renderListBackground(GuiGraphics graphics) {
	}

	@Override
	protected void renderListSeparators(GuiGraphics graphics) {
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double from = gliding ? glideTarget : getScrollAmount();
		glideTarget = Mth.clamp(from - scrollY * itemHeight * 1.5, 0, getMaxScroll());
		gliding = true;
		lastFrame = Util.getMillis();
		return true;
	}

	@Override
	public void setScrollAmount(double scroll) {
		super.setScrollAmount(scroll);
		if (!gliderMoving) {
			gliding = false;
		}
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (gliding) {
			long now = Util.getMillis();
			double blend = 1 - Math.exp(-GLIDE_PER_SECOND * (now - lastFrame) / 1000D);
			lastFrame = now;
			double next = getScrollAmount() + (glideTarget - getScrollAmount()) * blend;
			if (Math.abs(glideTarget - next) < 0.5) {
				next = glideTarget;
				gliding = false;
			}
			gliderMoving = true;
			setScrollAmount(next);
			gliderMoving = false;
		}
		super.renderWidget(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	protected void renderDecorations(GuiGraphics graphics, int mouseX, int mouseY) {
		int maxScroll = getMaxScroll();
		if (getScrollAmount() > 0.5) {
			graphics.fillGradient(getX(), getY(), getRight(), getY() + 5, 0x22000000, 0x00000000);
		}
		if (maxScroll <= 0) {
			return;
		}
		if (getScrollAmount() < maxScroll - 0.5) {
			graphics.fillGradient(getX(), getBottom() - 5, getRight(), getBottom(), 0x00000000, 0x22000000);
		}
		int barX = getScrollbarPosition() + 1;
		int thumb = Mth.clamp(height * height / getMaxPosition(), 18, height - 4);
		int thumbY = getY() + (int) ((height - thumb) * getScrollAmount() / maxScroll);
		boolean over = mouseX >= barX - 1 && mouseX < barX + BAR_WIDTH + 1 && mouseY >= getY() && mouseY < getBottom();
		graphics.fill(barX, getY(), barX + BAR_WIDTH, getBottom(), PaperUi.PANEL);
		graphics.fill(barX, thumbY, barX + BAR_WIDTH, thumbY + thumb, over ? PaperUi.INK_SOFT : PaperUi.LINE);
	}
}
