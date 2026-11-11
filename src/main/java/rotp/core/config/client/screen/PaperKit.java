package rotp.core.config.client.screen;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** The settings screen's paper for the JoJo menu's other pages: its colours, window, glyphs, list and button. */
public final class PaperKit {
	public static final int PAPER_LIGHT = PaperUi.PAPER_LIGHT;
	public static final int PAPER = PaperUi.PAPER;
	public static final int PANEL = PaperUi.PANEL;
	public static final int LINE = PaperUi.LINE;
	public static final int LINE_SOFT = PaperUi.LINE_SOFT;
	public static final int INK = PaperUi.INK;
	public static final int INK_SOFT = PaperUi.INK_SOFT;
	public static final int INK_FAINT = PaperUi.INK_FAINT;
	public static final int HOVER = PaperUi.HOVER;
	public static final int ACCENT = PaperUi.ACCENT;
	public static final int ACCENT_FILL = PaperUi.ACCENT_FILL;
	public static final int ON = PaperUi.ON;
	public static final int ERROR = PaperUi.ERROR;
	public static final int PENDING = PaperUi.PENDING;
	public static final int CONTROL_HEIGHT = PaperUi.CONTROL_HEIGHT;
	public static final ResourceLocation WINDOW = PaperUi.WINDOW;

	public enum Glyph {
		RESET, SYNC, RESTART, LOCK, CHECK, CHEVRON_RIGHT, CHEVRON_DOWN, ARROW, PLUS, MINUS, WARNING;

		private PaperUi.Glyph own() {
			return PaperUi.Glyph.valueOf(name());
		}
	}

	private PaperKit() {}

	public static void glyph(GuiGraphics graphics, Glyph glyph, int x, int y, int color) {
		PaperUi.glyph(graphics, glyph.own(), x, y, color);
	}

	public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
		PaperUi.text(graphics, font, text, x, y, color);
	}

	public static void text(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
		PaperUi.text(graphics, font, text, x, y, color);
	}

	public static String oneLine(Font font, String text, int width) {
		return PaperUi.oneLine(font, text, width);
	}

	public static List<FormattedCharSequence> fit(Font font, String text, int width, int maxLines) {
		return PaperUi.fit(font, text, width, maxLines);
	}

	/** A list on paper, as the settings screen's. */
	public abstract static class PaperListBase<E extends ContainerObjectSelectionList.Entry<E>> extends PaperList<E> {
		protected PaperListBase(Minecraft minecraft, int x, int y, int width, int height, int rowHeight) {
			super(minecraft, x, y, width, height, rowHeight);
		}
	}

	/** The settings screen's paper button: only an outline while it cannot be pressed. */
	public static class Action extends ConfigControls.PaperAction {
		public Action(int x, int y, int width, int height, Component message, Runnable press) {
			super(x, y, width, height, message, button -> press.run());
		}
	}
}
