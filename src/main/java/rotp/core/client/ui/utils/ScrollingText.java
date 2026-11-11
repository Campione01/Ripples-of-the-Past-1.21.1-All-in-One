package rotp.core.client.ui.utils;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

public class ScrollingText {
	public int x;
	public int y;
	public int width;
	public int height;
	public Scrolling scrolling;
	protected List<FormattedCharSequence> textLines;
	public int lineHeight = 9;
	public int xOffset = 4;
	public int yOffset = 3;
	/** The text's drawn size; below 1 where TextBoxFit shrank it to show whole. */
	public float textScale = 1.0F;

	public ScrollingText(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.scrolling = new Scrolling(height);
	}
	
	public void setText(List<FormattedCharSequence> textLines) {
		setText(textLines, 1.0F);
	}

	/** Lines split at the box's text width divided by the scale they are drawn at. */
	public void setText(List<FormattedCharSequence> textLines, float textScale) {
		this.textLines = textLines;
		this.textScale = textScale;
		scrolling.setContentsHeight(textLines != null
				? yOffset + (int) Math.ceil(textLines.size() * lineHeight * textScale) + yOffset : 0);
	}

	public void draw(int xOffset, int yOffset, GuiGraphics guiGraphics, Font font, int color, boolean dropShadow) {
		if (textLines != null) {
			scrolling.pushOffsetScissor(guiGraphics, y, x, x + width);
			if (textScale != 1.0F) {
				guiGraphics.pose().pushPose();
				guiGraphics.pose().translate(x + xOffset, y + yOffset, 0);
				guiGraphics.pose().scale(textScale, textScale, 1);
				for (int i = 0; i < textLines.size(); i++) {
					guiGraphics.drawString(font, textLines.get(i), 0, lineHeight * i, color, dropShadow);
				}
				guiGraphics.pose().popPose();
			}
			else {
				for (int i = 0; i < textLines.size(); i++) {
					guiGraphics.drawString(font, textLines.get(i), x + xOffset, y + yOffset + lineHeight * i, color, dropShadow);
				}
			}
			scrolling.pop(guiGraphics);
		}
	}
	
	public void drawSmallScrollBar(GuiGraphics guiGraphics) {
		if (scrolling.hasScrolling()) {
			int[] bounds = scrolling.getScrollBarBounds(-2, 6);
			if (bounds != null) {
				int x = this.x + this.width - 3;
				int y = this.y + bounds[0];
				int y2 = this.y + bounds[1];
				guiGraphics.fill(x, y, x + 1, y2, 0x80000000);
			}
		}
	}
	
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    	if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
    		scrolling.scroll(scrollY);
    		return true;
    	}
    	return false;
    }
	
}
