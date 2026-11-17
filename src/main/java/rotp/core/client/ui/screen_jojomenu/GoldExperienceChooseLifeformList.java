package rotp.core.client.ui.screen_jojomenu;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.config.client.screen.PaperKit;
import rotp.core.impl.stands.goldexperience.client.LifeformPortrait;
import rotp.core.util.mc.entitysubtype.EntitySubtype;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Choose Lifeform's choices on paper: in the list view a row each, in the grid view rows of tiles, every one with the
 * creature's portrait. A click or Enter chooses one; the screen keeps which is chosen, favourite or new.
 */
final class GoldExperienceChooseLifeformList extends PaperKit.PaperListBase<GoldExperienceChooseLifeformList.Entry> {
    private static final int LIST_PORTRAIT = 22;
    private static final int TILE_PORTRAIT = 30;
    private static final int TILE_BOX_WIDTH = GoldExperienceChooseLifeformLayout.TILE_WIDTH - 4;
    private static final int TILE_BOX_HEIGHT = GoldExperienceChooseLifeformLayout.TILE_HEIGHT - 4;
    private static final int PORTRAIT_BACK = 0xFFE4E7EC;
    // a five-pointed star, seven pixels each way, row by row: the favourite mark
    private static final String[] STAR = {"...#...", "..###..", "#######", ".#####.", "..###..", ".##.##.", ".#...#."};

    private final GoldExperienceChooseLifeformScreen screen;
    private final boolean grid;
    private final int columns;
    private List<String> shownIds = List.of();

    GoldExperienceChooseLifeformList(Minecraft minecraft, GoldExperienceChooseLifeformScreen screen, int x, int y,
            int width, int height, boolean grid) {
        super(minecraft, x, y, width, height, grid ? GoldExperienceChooseLifeformLayout.TILE_HEIGHT
                : GoldExperienceChooseLifeformLayout.LIST_ROW);
        this.screen = screen;
        this.grid = grid;
        this.columns = grid ? GoldExperienceChooseLifeformLayout.columns(getRowWidth()) : 1;
    }

    /** Shows these choices; the one the keyboard is on stays focused if it is still among them. */
    void show(List<EntitySubtype<?>> choices) {
        List<String> ids = choices.stream().map(choice -> choice.getId().toString()).toList();
        if (ids.equals(shownIds)) {
            return;
        }
        shownIds = ids;
        String focusedId = focusedId();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < choices.size(); i += columns) {
            entries.add(new Entry(choices.subList(i, Math.min(i + columns, choices.size()))));
        }
        replaceEntries(entries);
        if (focusedId != null) {
            focus(focusedId, false);
        }
        // only when rows went: setting the scroll stops a glide the wheel started
        if (getScrollAmount() > getMaxScroll()) {
            setScrollAmount(getMaxScroll());
        }
    }

    @Nullable
    private String focusedId() {
        Entry entry = getFocused();
        return entry != null && entry.getFocused() instanceof Cell cell ? cell.id : null;
    }

    /** Puts the keyboard on this choice, and with {@code scroll} brings its row into view. */
    void focus(String id, boolean scroll) {
        for (Entry entry : children()) {
            for (Cell cell : entry.cells) {
                if (cell.id.equals(id)) {
                    entry.setFocused(cell);
                    setFocused(entry);
                    if (scroll) {
                        ensureVisible(entry);
                    }
                    return;
                }
            }
        }
    }

    /** Brings the row of this choice into view without moving the keyboard. */
    void reveal(String id) {
        for (Entry entry : children()) {
            if (entry.cells.stream().anyMatch(cell -> cell.id.equals(id))) {
                ensureVisible(entry);
                return;
            }
        }
    }

    /** The choice under the pointer, if it is over one. */
    @Nullable
    Cell cellAt(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return null;
        }
        // from the position itself: a click can come before the frame that would note the pointer over this row
        Entry entry = getEntryAtPosition(mouseX, mouseY);
        if (entry == null) {
            return null;
        }
        for (Cell cell : entry.cells) {
            if (cell.isMouseOver(mouseX, mouseY)) {
                return cell;
            }
        }
        return null;
    }

    /** The choice the keyboard is on, if any. */
    @Nullable
    Cell focusedCell() {
        Entry entry = getFocused();
        return entry != null && entry.getFocused() instanceof Cell cell ? cell : null;
    }

    int topOf(Cell cell) {
        for (int i = 0; i < children().size(); i++) {
            if (children().get(i).cells.contains(cell)) {
                return getRowTop(i);
            }
        }
        return getY();
    }

    final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final List<Cell> cells = new ArrayList<>();

        private Entry(List<EntitySubtype<?>> choices) {
            choices.forEach(choice -> cells.add(new Cell(choice)));
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = screen.font();
            if (!grid) {
                Cell cell = cells.get(0);
                cell.setRectangle(width + 4, GoldExperienceChooseLifeformLayout.LIST_ROW - 2, left - 2, top - 2);
                renderRow(graphics, font, cell, top, left, width, hovering && cell.isMouseOver(mouseX, mouseY));
                return;
            }
            int offset = Math.max(0, (width - columns * GoldExperienceChooseLifeformLayout.TILE_WIDTH) / 2);
            for (int i = 0; i < cells.size(); i++) {
                Cell cell = cells.get(i);
                int tileX = left + offset + i * GoldExperienceChooseLifeformLayout.TILE_WIDTH + 2;
                cell.setRectangle(TILE_BOX_WIDTH, TILE_BOX_HEIGHT, tileX, top);
                renderTile(graphics, font, cell, tileX, top, hovering && cell.isMouseOver(mouseX, mouseY));
            }
        }

        private void renderRow(GuiGraphics graphics, Font font, Cell cell, int top, int left, int width, boolean hovered) {
            boolean chosen = screen.isChosen(cell.id);
            int rowTop = top - 2;
            int rowBottom = top + GoldExperienceChooseLifeformLayout.LIST_ROW - 4;
            if (chosen) {
                graphics.fill(left - 2, rowTop, left + width + 2, rowBottom, PaperKit.ACCENT_FILL);
            }
            else if (hovered) {
                graphics.fill(left - 2, rowTop, left + width + 2, rowBottom, PaperKit.HOVER);
            }
            int portraitX = left + 1;
            int portraitY = top - 1;
            graphics.fill(portraitX - 1, portraitY - 1, portraitX + LIST_PORTRAIT + 1, portraitY + LIST_PORTRAIT + 1,
                    chosen ? PaperKit.ACCENT : PaperKit.LINE_SOFT);
            graphics.fill(portraitX, portraitY, portraitX + LIST_PORTRAIT, portraitY + LIST_PORTRAIT, PORTRAIT_BACK);
            LifeformPortrait.render(graphics, cell.choice, portraitX, portraitY, LIST_PORTRAIT);

            int right = left + width - 3;
            if (chosen) {
                PaperKit.glyph(graphics, PaperKit.Glyph.CHECK, right - 9, top + 6, PaperKit.ACCENT);
                right -= 13;
            }
            if (screen.isNew(cell.id)) {
                String label = Component.translatable("jojo.ui.lifeform_ui_mode.new").getString();
                label = PaperKit.oneLine(font, label, 60);
                int pill = font.width(label) + 6;
                graphics.fill(right - pill, top + 4, right, top + 15, PaperKit.LINE_SOFT);
                PaperKit.text(graphics, font, Component.literal(label), right - pill + 3, top + 6, PaperKit.ON);
                right -= pill + 4;
            }
            if (screen.isFavorite(cell.id)) {
                star(graphics, right - 8, top + 6, PaperKit.PENDING);
                right -= 12;
            }
            int textX = left + LIST_PORTRAIT + 7;
            int room = right - textX - 2;
            String name = PaperKit.oneLine(font, cell.choice.getDescription().getString(), room);
            PaperKit.text(graphics, font, Component.literal(name), textX, top + 2, chosen ? PaperKit.ACCENT : PaperKit.INK);
            String id = PaperKit.oneLine(font, cell.id, room);
            PaperKit.text(graphics, font, Component.literal(id), textX, top + 12, PaperKit.INK_FAINT);
            if (cell.isFocused()) {
                graphics.renderOutline(left - 2, rowTop, width + 4, rowBottom - rowTop, PaperKit.INK);
            }
        }

        private void renderTile(GuiGraphics graphics, Font font, Cell cell, int x, int top, boolean hovered) {
            boolean chosen = screen.isChosen(cell.id);
            int bottom = top + TILE_BOX_HEIGHT;
            graphics.fill(x, top, x + TILE_BOX_WIDTH, bottom, chosen ? PaperKit.ACCENT : PaperKit.LINE_SOFT);
            graphics.fill(x + 1, top + 1, x + TILE_BOX_WIDTH - 1, bottom - 1, chosen ? PaperKit.ACCENT_FILL : PaperKit.PAPER_LIGHT);
            if (hovered && !chosen) {
                graphics.fill(x + 1, top + 1, x + TILE_BOX_WIDTH - 1, bottom - 1, PaperKit.HOVER);
            }
            int portraitX = x + (TILE_BOX_WIDTH - TILE_PORTRAIT) / 2;
            LifeformPortrait.render(graphics, cell.choice, portraitX, top + 3, TILE_PORTRAIT);
            String name = PaperKit.oneLine(font, cell.choice.getDescription().getString(), TILE_BOX_WIDTH - 4);
            PaperKit.text(graphics, font, Component.literal(name), x + (TILE_BOX_WIDTH - font.width(name)) / 2, top + 35,
                    chosen ? PaperKit.ACCENT : PaperKit.INK);
            // over the portrait, which the inventory drawing puts in front of the page
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 310);
            if (screen.isFavorite(cell.id)) {
                star(graphics, x + TILE_BOX_WIDTH - 9, top + 2, PaperKit.PENDING);
            }
            if (screen.isNew(cell.id)) {
                graphics.fill(x + 3, top + 3, x + 7, top + 7, PaperKit.ON);
            }
            graphics.pose().popPose();
            if (cell.isFocused()) {
                graphics.renderOutline(x - 1, top - 1, TILE_BOX_WIDTH + 2, TILE_BOX_HEIGHT + 2, PaperKit.INK);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return cells;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return cells;
        }
    }

    /** One choice: it only takes the click, the keys and the focus; its row or tile draws it. */
    final class Cell extends AbstractButton {
        final EntitySubtype<?> choice;
        final String id;

        private Cell(EntitySubtype<?> choice) {
            super(0, 0, 10, 10, choice.getDescription());
            this.choice = choice;
            this.id = choice.getId().toString();
        }

        @Override
        public void onPress() {
            screen.choose(id);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static void star(GuiGraphics graphics, int x, int y, int color) {
        for (int row = 0; row < STAR.length; row++) {
            String line = STAR[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) == '#') {
                    graphics.fill(x + column, y + row, x + column + 1, y + row + 1, color);
                }
            }
        }
    }
}
