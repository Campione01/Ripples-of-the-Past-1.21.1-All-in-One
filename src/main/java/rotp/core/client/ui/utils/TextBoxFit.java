package rotp.core.client.ui.utils;

import java.util.function.IntUnaryOperator;

/**
 * A text box of the skills screen shows its text whole where it can: a text with more lines than the box holds is drawn
 * smaller, split again at the wider width that gives, down to {@link #SMALLEST_SCALE}; what still does not fit scrolls.
 * No client classes here, so the smoke test can load it.
 */
public final class TextBoxFit {
	public static final float[] SCALES = { 1.0F, 0.85F, 0.75F };
	public static final float SMALLEST_SCALE = SCALES[SCALES.length - 1];
	// a line's glyphs take 8 of its 9 pixels: the last line needs no blank row under it
	private static final int GLYPH_HEIGHT = 8;

	private TextBoxFit() {}

	/**
	 * The largest scale at which the text, split at {@code width / scale}, shows every line whole in a box of the given
	 * height, its first line {@code padding} below the top.
	 * @param linesAtWidth the number of lines the text splits into at a width
	 */
	public static float scaleToFit(IntUnaryOperator linesAtWidth, int width, int height, int lineHeight, int padding) {
		for (float scale : SCALES) {
			if (fitsWhole(linesAtWidth.applyAsInt(splitWidth(width, scale)), scale, height, lineHeight, padding)) {
				return scale;
			}
		}
		return SMALLEST_SCALE;
	}

	/** The largest scale at which a one-line label of that width fits its room; the smallest when none does. */
	public static float labelScale(int textWidth, int room) {
		for (float scale : SCALES) {
			if (textWidth * scale <= room) {
				return scale;
			}
		}
		return SMALLEST_SCALE;
	}

	public static int splitWidth(int width, float scale) {
		return (int) (width / scale);
	}

	public static boolean fitsWhole(int lines, float scale, int height, int lineHeight, int padding) {
		return lines <= 0 || padding + ((lines - 1) * lineHeight + GLYPH_HEIGHT) * scale <= height;
	}
}
