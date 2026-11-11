package rotp.core.config.client.screen;

import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import rotp.core.config.client.screen.ConfigControls.PaperAction;
import rotp.core.config.client.screen.ConfigControls.PaperField;
import rotp.core.config.client.screen.PaperUi.Glyph;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.client.ConfigChecklist;
import rotp.core.config.editor.client.ConfigChecklist.Item;
import rotp.core.config.editor.client.ConfigStore;

/**
 * A list whose items the server offers, as one tick box per item with its name and id. Nothing leaves the card until
 * Done; leaving with changes asks first. A long list gets a filter box; a ticked item the server no longer offers is
 * marked so and can be unticked.
 */
final class ConfigChecklistScreen extends PaperDialog {
	private static final String TEXT = "jojo_ripples.config.screen.";
	// a list this long is easier to search than to scroll
	private static final int FILTER_FROM = 9;
	private static final int BOX = 11;

	private final ConfigStore store;
	private final String key;
	private ConfigEntry entry;
	private final ConfigChecklist checklist;
	private String query = "";
	private @Nullable Component failure;
	private boolean waitingForReply;
	private boolean askingToDiscard;
	private @Nullable TickList list;
	private @Nullable JsonArray sent;
	private int restartY = -1;
	private int listY;
	private int listBottom;

	ConfigChecklistScreen(ModConfigScreen parent, ConfigStore store, ConfigEntry entry) {
		super(parent, PaperUi.label(entry));
		this.store = store;
		this.key = entry.key();
		this.entry = entry;
		this.checklist = new ConfigChecklist(entry);
	}

	static String shownName(Item item) {
		return shownName(item.translationKey(), item.name());
	}

	/** The client's text for the key when it has one, else the name the server sent. */
	static String shownName(@Nullable String translationKey, String name) {
		Language language = Language.getInstance();
		return translationKey != null && language.has(translationKey) ? language.getOrDefault(translationKey) : name;
	}

	private boolean canEdit() {
		return store.access() == ConfigStore.Access.EDITABLE;
	}

	private boolean hasFilter() {
		return checklist.items().size() >= FILTER_FROM;
	}

	@Override
	protected void init() {
		super.init();
		int rows = Math.max(3, Math.min(checklist.items().size(), 9));
		int extra = (entry.restart() != ConfigEntry.Restart.NONE ? 13 : 0) + (hasFilter() ? 22 : 0);
		placeCard(Math.max(240, Math.min(360, width - 40)), 72 + extra + rows * PaperUi.ROW_HEIGHT);
		int y = cardY + 22;
		restartY = -1;
		if (entry.restart() != ConfigEntry.Restart.NONE) {
			restartY = y;
			y += 13;
		}
		if (hasFilter()) {
			PaperField filter = new PaperField(font, Component.translatable(TEXT + "checklist.filter"), ConfigEntry.Kind.STRING);
			filter.setHint(Component.translatable(TEXT + "checklist.filter"));
			filter.setValue(query);
			filter.place(cardX + 10, y, cardWidth - 20, PaperUi.CONTROL_HEIGHT);
			filter.setResponder(text -> {
				if (!text.equals(query)) {
					query = text;
					refill(0);
				}
			});
			addRenderableWidget(filter);
			y += 22;
		}
		int footerY = cardY + cardHeight - 24;
		double scroll = list != null ? list.getScrollAmount() : 0;
		listY = y;
		listBottom = footerY - 20;
		list = addRenderableWidget(new TickList(minecraft, cardX + 3, listY, cardWidth - 6, Math.max(PaperUi.ROW_HEIGHT, listBottom - listY)));
		refill(scroll);

		int gap = 6;
		if (askingToDiscard) {
			int half = (cardWidth - 20 - gap) / 2;
			addRenderableWidget(new PaperAction(cardX + 10, footerY, half, PaperUi.CONTROL_HEIGHT,
					Component.translatable(TEXT + "list.keep"), button -> {
						askingToDiscard = false;
						rebuildWidgets();
					}));
			addRenderableWidget(new PaperAction(cardX + 10 + half + gap, footerY, half, PaperUi.CONTROL_HEIGHT,
					Component.translatable(TEXT + "list.discard"), button -> super.onClose()));
			return;
		}
		if (!canEdit()) {
			addRenderableWidget(new PaperAction(cardX + (cardWidth - 100) / 2, footerY, 100, PaperUi.CONTROL_HEIGHT,
					CommonComponents.GUI_DONE, button -> super.onClose()));
			return;
		}
		int third = (cardWidth - 20 - gap * 2) / 3;
		PaperAction reset = addRenderableWidget(new PaperAction(cardX + 10, footerY, third, PaperUi.CONTROL_HEIGHT,
				Component.translatable(TEXT + "card.reset"), button -> {
					checklist.setTo(entry.defaultValue());
					failure = null;
					refill(list != null ? list.getScrollAmount() : 0);
				}));
		reset.active = !waitingForReply;
		PaperAction save = addRenderableWidget(new PaperAction(cardX + 10 + third + gap, footerY, third, PaperUi.CONTROL_HEIGHT,
				CommonComponents.GUI_DONE, button -> save()));
		save.active = !waitingForReply;
		addRenderableWidget(new PaperAction(cardX + 10 + (third + gap) * 2, footerY, third, PaperUi.CONTROL_HEIGHT,
				CommonComponents.GUI_CANCEL, button -> onClose()));
	}

	private void refill(double scroll) {
		if (list == null) {
			return;
		}
		list.fill(checklist.shown(query, ConfigChecklistScreen::shownName).stream().map(TickRow::new).toList());
		list.setScrollAmount(scroll);
	}

	private void save() {
		if (checklist.sameAs(entry.value())) {
			super.onClose();
			return;
		}
		JsonArray value = checklist.value();
		sent = value;
		failure = null;
		if (store.set(key, value)) {
			waitingForReply = true;
			rebuildWidgets();
		}
		else {
			failure = Component.translatable(TEXT + "status.forbidden");
		}
	}

	@Override
	public void tick() {
		super.tick();
		ConfigEntry fresh = store.entry(key);
		if (fresh != null) {
			entry = fresh;
		}
		if (waitingForReply && store.pending(key) == null && !store.busy()) {
			waitingForReply = false;
			if (sent != null && checklist.sameAs(entry.value())) {
				super.onClose();
			}
			else {
				failure = Component.translatable(TEXT + "status.invalid_value");
				rebuildWidgets();
			}
		}
	}

	@Override
	public void onClose() {
		if (waitingForReply) {
			return;
		}
		if (askingToDiscard) {
			askingToDiscard = false;
			rebuildWidgets();
			return;
		}
		if (canEdit() && !checklist.sameAs(entry.value())) {
			askingToDiscard = true;
			rebuildWidgets();
			return;
		}
		super.onClose();
	}

	@Override
	protected void renderCard(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		heading(graphics, cardY + 8);
		if (restartY >= 0) {
			// a value read when the world starts: said on the card itself, not only in the row's tooltip
			String flag = entry.restart() == ConfigEntry.Restart.GAME ? "flag.restart_game" : "flag.restart_world";
			String line = PaperUi.oneLine(font, Component.translatable(TEXT + flag).getString(), cardWidth - 36);
			int lineX = cardX + (cardWidth - font.width(line) - 12) / 2;
			PaperUi.glyph(graphics, Glyph.RESTART, lineX, restartY - 1, PaperUi.PENDING);
			PaperUi.text(graphics, font, Component.literal(line), lineX + 12, restartY, PaperUi.PENDING);
		}
		if (list != null && list.children().isEmpty()) {
			String empty = checklist.items().isEmpty() ? TEXT + "list.empty" : TEXT + "checklist.no_match";
			centered(graphics, Component.translatable(empty).getString(), (listY + listBottom) / 2 - 4, PaperUi.INK_FAINT);
		}
		int lineY = cardY + cardHeight - 30;
		graphics.fill(cardX + 8, lineY, cardX + cardWidth - 8, lineY + 1, PaperUi.LINE_SOFT);
		int messageY = cardY + cardHeight - 41;
		if (askingToDiscard) {
			centered(graphics, Component.translatable(TEXT + "list.discard_question").getString(), messageY, PaperUi.ERROR);
		}
		else if (failure != null) {
			centered(graphics, failure.getString(), messageY, PaperUi.ERROR);
		}
		else if (waitingForReply) {
			centered(graphics, Component.translatable(TEXT + "status.waiting").getString(), messageY, PaperUi.PENDING);
		}
		else {
			centered(graphics, Component.translatable(TEXT + "checklist.summary", checklist.tickedCount(), checklist.offeredCount()).getString(),
					messageY, PaperUi.INK_SOFT);
		}
	}

	private static final class TickList extends PaperList<TickRow> {
		TickList(Minecraft minecraft, int x, int y, int width, int height) {
			super(minecraft, x, y, width, height, PaperUi.ROW_HEIGHT);
		}

		void fill(List<TickRow> rows) {
			replaceEntries(rows);
		}
	}

	private final class TickRow extends ContainerObjectSelectionList.Entry<TickRow> {
		private final TickBox box;

		TickRow(Item item) {
			box = new TickBox(item);
		}

		@Override
		public void render(GuiGraphics graphics, int rowIndex, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovering, float partialTick) {
			box.setPosition(left, top);
			box.setWidth(width);
			box.render(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.<AbstractWidget>of(box);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.<AbstractWidget>of(box);
		}
	}

	/** The whole row is the tick box: a click or Space anywhere on it ticks or unticks its item. */
	private final class TickBox extends AbstractButton {
		private final Item item;

		TickBox(Item item) {
			super(0, 0, 100, PaperUi.ROW_HEIGHT - 1, Component.literal(shownName(item)));
			this.item = item;
		}

		private boolean live() {
			return canEdit() && !waitingForReply;
		}

		@Override
		public void onPress() {
			if (live()) {
				checklist.toggle(item.id());
				failure = null;
			}
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			active = live();
			boolean on = checklist.isTicked(item.id());
			if (isHovered() && active) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), PaperUi.HOVER);
			}
			int boxX = getX() + 4;
			int boxY = getY() + (height - BOX) / 2;
			int edge = isFocused() || isHovered() && active ? PaperUi.INK : active ? PaperUi.INK_SOFT : PaperUi.LINE;
			graphics.fill(boxX, boxY, boxX + BOX, boxY + BOX, edge);
			graphics.fill(boxX + 1, boxY + 1, boxX + BOX - 1, boxY + BOX - 1, on ? (active ? PaperUi.ACCENT : PaperUi.LINE) : PaperUi.PAPER_LIGHT);
			if (on) {
				PaperUi.glyph(graphics, Glyph.CHECK, boxX + 1, boxY + 1, PaperUi.PAPER_LIGHT);
			}
			int textX = boxX + BOX + 6;
			int room = getRight() - 4 - textX;
			var font = Minecraft.getInstance().font;
			String name = PaperUi.oneLine(font, getMessage().getString(), room);
			PaperUi.text(graphics, font, Component.literal(name), textX, getY() + 2, item.offered() ? PaperUi.INK : PaperUi.INK_SOFT);
			if (item.offered()) {
				PaperUi.text(graphics, font, Component.literal(PaperUi.oneLine(font, item.id(), room)), textX, getY() + 12, PaperUi.INK_FAINT);
			}
			else {
				String gone = PaperUi.oneLine(font, Component.translatable(TEXT + "checklist.gone").getString(), room);
				PaperUi.text(graphics, font, Component.literal(gone), textX, getY() + 12, PaperUi.PENDING);
			}
			if (isFocused()) {
				graphics.renderOutline(getX(), getY(), width, height, PaperUi.INK);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, CommonComponents.optionStatus(getMessage(), checklist.isTicked(item.id())));
		}
	}
}
