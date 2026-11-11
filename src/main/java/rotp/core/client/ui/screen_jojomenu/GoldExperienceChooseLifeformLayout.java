package rotp.core.client.ui.screen_jojomenu;

/**
 * Where Choose Lifeform's paper window puts its parts on a screen of a given size. The window narrows to 180 wide; a
 * short one drops the line under the title, and a narrow one gives the footer's buttons two rows.
 */
public record GoldExperienceChooseLifeformLayout(int x, int y, int width, int height, boolean summaryLine,
        int titleY, int searchY, int filterY, int listTop, int listBottom, boolean twoFooterRows, int footerTop,
        int buttonsY, int lastButtonsY) {
    public static final int MAX_WIDTH = 340;
    public static final int MIN_WIDTH = 180;
    public static final int MAX_HEIGHT = 300;
    public static final int CONTROL = 18;
    public static final int MARGIN = 9;
    public static final int GAP = 4;
    // lower than this, the window drops the "Selected:" line under its title and says it beside the title
    public static final int SUMMARY_LINE_FROM = 190;
    // narrower than this, the footer's four buttons take two rows
    public static final int ONE_FOOTER_ROW_FROM = 300;
    public static final int LIST_ROW = 26;
    public static final int TILE_WIDTH = 52;
    public static final int TILE_HEIGHT = 50;

    public static GoldExperienceChooseLifeformLayout forScreen(int screenWidth, int screenHeight) {
        int width = Math.max(Math.min(MIN_WIDTH, screenWidth - 8), Math.min(MAX_WIDTH, screenWidth - 16));
        int height = Math.min(MAX_HEIGHT, screenHeight - 8);
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        boolean summaryLine = height >= SUMMARY_LINE_FROM;
        int titleY = y + 7;
        int searchY = y + (summaryLine ? 32 : 20);
        int filterY = searchY + CONTROL + GAP;
        int listTop = filterY + CONTROL + 6;
        boolean twoFooterRows = width < ONE_FOOTER_ROW_FROM;
        int footer = twoFooterRows ? 2 * CONTROL + GAP + 10 : CONTROL + 10;
        int footerTop = y + height - footer;
        int buttonsY = footerTop + 5;
        int lastButtonsY = twoFooterRows ? buttonsY + CONTROL + GAP : buttonsY;
        return new GoldExperienceChooseLifeformLayout(x, y, width, height, summaryLine, titleY, searchY, filterY,
                listTop, footerTop - 2, twoFooterRows, footerTop, buttonsY, lastButtonsY);
    }

    public int left() {
        return x + MARGIN;
    }

    public int right() {
        return x + width - MARGIN;
    }

    public int inner() {
        return width - 2 * MARGIN;
    }

    public int summaryY() {
        return titleY + 12;
    }

    /** Tiles in one row of the grid, for a list whose rows are this wide. */
    public static int columns(int rowWidth) {
        return Math.max(1, rowWidth / TILE_WIDTH);
    }
}
