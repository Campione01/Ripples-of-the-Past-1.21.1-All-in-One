package rotp.core.client.ui.utils;

import java.util.function.IntUnaryOperator;

/**
 * OQ-C08: the skills screen's controls box (31 high) and description box (105 high) show their text whole where a smaller
 * size lets them; only what does not fit at the smallest size scrolls. StandSkillsScreen splits at the width the scale gives.
 * The look of the shrunk text, the ellipsis of a long title and its tooltip are in-game checks.
 */
public final class TextBoxFitSmokeTest {
	private static final int CONTROLS_HEIGHT = 31;
	private static final int DESC_HEIGHT = 105;
	private static final int LINE = 9;
	private static final int PADDING = 3;

	private TextBoxFitSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		// Star Platinum's English controls line: three lines at 101 pixels fit whole as they are
		require(TextBoxFit.scaleToFit(text(250), 101, CONTROLS_HEIGHT, LINE, PADDING) == 1.0F,
				"a three-line controls text is drawn at full size");
		// four lines at 101 pixels are three at 118: drawn at 0.85, all of it shows
		float four = TextBoxFit.scaleToFit(text(340), 101, CONTROLS_HEIGHT, LINE, PADDING);
		require(four == 0.85F && TextBoxFit.fitsWhole(text(340).applyAsInt(TextBoxFit.splitWidth(101, four)), four,
				CONTROLS_HEIGHT, LINE, PADDING), "a four-line controls text is drawn smaller and whole: " + four);
		require(!TextBoxFit.fitsWhole(4, 1.0F, CONTROLS_HEIGHT, LINE, PADDING),
				"four full-size lines do not fit the controls box, which cut the last of them");
		require(TextBoxFit.scaleToFit(text(5000), 101, CONTROLS_HEIGHT, LINE, PADDING) == TextBoxFit.SMALLEST_SCALE,
				"a text too long for any size takes the smallest and scrolls");

		require(TextBoxFit.fitsWhole(11, 1.0F, DESC_HEIGHT, LINE, PADDING) && !TextBoxFit.fitsWhole(12, 1.0F, DESC_HEIGHT, LINE, PADDING),
				"the description box holds eleven full-size lines");
		require(TextBoxFit.scaleToFit(text(111 * 13), 111, DESC_HEIGHT, LINE, PADDING) < 1.0F,
				"a thirteen-line description is drawn smaller");
		require(TextBoxFit.fitsWhole(0, 1.0F, CONTROLS_HEIGHT, LINE, PADDING), "an empty text fits");
	}

	// a text of the given width in pixels, split into full lines at a width
	private static IntUnaryOperator text(int pixels) {
		return width -> (pixels + width - 1) / width;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("TextBoxFitSmokeTest: " + message);
		}
	}
}
