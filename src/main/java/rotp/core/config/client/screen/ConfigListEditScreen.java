package rotp.core.config.client.screen;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import rotp.core.config.client.screen.ConfigControls.GlyphButton;
import rotp.core.config.client.screen.ConfigControls.PaperAction;
import rotp.core.config.client.screen.ConfigControls.PaperField;
import rotp.core.config.client.screen.PaperUi.Glyph;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.client.ConfigStore;
import rotp.core.config.editor.client.ConfigWidgetRules;

/** Edits a list setting as a whole: nothing leaves this card until Done, and leaving with changes asks first. */
final class ConfigListEditScreen extends PaperDialog {
	private static final String TEXT = "jojo_ripples.config.screen.";

	private final ConfigStore store;
	private final String key;
	private ConfigEntry entry;
	private final List<String> draft = new ArrayList<>();
	private @Nullable Component failure;
	private boolean waitingForReply;
	private boolean askingToDiscard;
	private @Nullable EditList list;
	private @Nullable JsonArray sent;

	ConfigListEditScreen(ModConfigScreen parent, ConfigStore store, ConfigEntry entry) {
		super(parent, PaperUi.label(entry));
		this.store = store;
		this.key = entry.key();
		this.entry = entry;
		if (entry.value().isJsonArray()) {
			for (JsonElement item : entry.value().getAsJsonArray()) {
				draft.add(isNumeric() ? ConfigWidgetRules.formatNumber(entry.elementKind(), item) : item.getAsString());
			}
		}
	}

	private boolean isNumeric() {
		return entry.elementKind() == Kind.INTEGER || entry.elementKind() == Kind.DECIMAL;
	}

	private Kind itemKind() {
		return entry.elementKind() != null ? entry.elementKind() : Kind.STRING;
	}

	private boolean canEdit() {
		return store.access() == ConfigStore.Access.EDITABLE;
	}

	private boolean fixedSize() {
		return ConfigWidgetRules.isByDifficulty(entry);
	}

	private @Nullable JsonElement parse(String text) {
		JsonElement parsed = ConfigWidgetRules.parseScalar(itemKind(), entry.bounds(), text);
		if (parsed != null && ConfigWidgetRules.listItemsAreIds(entry) && ResourceLocation.tryParse(text.trim()) == null) {
			return null;
		}
		return parsed;
	}

	// a row nobody has typed in yet is not a mistake; Done still refuses it
	private boolean looksValid(String text) {
		return text.isBlank() || parse(text) != null;
	}

	private @Nullable JsonArray draftAsJson() {
		JsonArray array = new JsonArray(draft.size());
		for (String text : draft) {
			JsonElement parsed = parse(text);
			if (parsed == null) {
				return null;
			}
			array.add(parsed);
		}
		return array;
	}

	private boolean isDirty() {
		JsonArray array = draftAsJson();
		return array == null || !array.equals(entry.value());
	}

	private boolean sizeAllows(int size) {
		ConfigEntry.Bounds bounds = entry.listSize();
		return bounds == null || bounds.contains(size);
	}

	@Override
	protected void init() {
		super.init();
		int rows = Math.max(4, Math.min(draft.size() + 1, 10));
		placeCard(Math.max(240, Math.min(340, width - 40)), 72 + rows * PaperUi.ROW_HEIGHT);
		double scroll = list != null ? list.getScrollAmount() : 0;
		int listY = cardY + 23;
		int footerY = cardY + cardHeight - 24;
		list = addRenderableWidget(new EditList(minecraft, cardX + 3, listY, cardWidth - 6, Math.max(PaperUi.ROW_HEIGHT, footerY - 20 - listY)));
		for (int i = 0; i < draft.size(); i++) {
			list.addRow(new EditRow(i));
		}
		list.setScrollAmount(scroll);

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
		PaperAction add = addRenderableWidget(new PaperAction(cardX + 10, footerY, third, PaperUi.CONTROL_HEIGHT,
				Component.translatable(TEXT + "list.add"), button -> addRow()));
		add.active = !fixedSize() && sizeAllows(draft.size() + 1);
		PaperAction save = addRenderableWidget(new PaperAction(cardX + 10 + third + gap, footerY, third, PaperUi.CONTROL_HEIGHT,
				CommonComponents.GUI_DONE, button -> save()));
		save.active = !waitingForReply;
		addRenderableWidget(new PaperAction(cardX + 10 + (third + gap) * 2, footerY, third, PaperUi.CONTROL_HEIGHT,
				CommonComponents.GUI_CANCEL, button -> onClose()));
	}

	private void addRow() {
		draft.add(isNumeric() ? ConfigWidgetRules.formatNumber(entry.elementKind(), lastOrZero()) : "");
		rebuildWidgets();
		if (list != null) {
			list.setScrollAmount(list.getMaxScroll());
			// the player pressed Add to type: the keys go to the new row
			setFocused(list);
			list.focusLastRow();
		}
	}

	private JsonElement lastOrZero() {
		if (entry.value().isJsonArray() && !entry.value().getAsJsonArray().isEmpty()) {
			var array = entry.value().getAsJsonArray();
			return array.get(array.size() - 1);
		}
		return new JsonPrimitive(0);
	}

	private void removeRow(int index) {
		draft.remove(index);
		rebuildWidgets();
	}

	private void save() {
		JsonArray array = draftAsJson();
		if (array == null) {
			failure = Component.translatable(TEXT + "status.invalid_value");
			return;
		}
		if (array.equals(entry.value())) {
			super.onClose();
			return;
		}
		sent = array;
		failure = null;
		if (store.set(key, array)) {
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
			if (sent != null && sent.equals(entry.value())) {
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
		if (canEdit() && isDirty()) {
			askingToDiscard = true;
			rebuildWidgets();
			return;
		}
		super.onClose();
	}

	@Override
	protected void renderCard(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		heading(graphics, cardY + 8);
		if (draft.isEmpty()) {
			centered(graphics, Component.translatable(TEXT + "list.empty").getString(), cardY + cardHeight / 2 - 14, PaperUi.INK_FAINT);
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
		else if (entry.bounds() != null && isNumeric()) {
			centered(graphics, ConfigTooltips.rangeOf(entry.elementKind(), entry.bounds()).getString(), messageY, PaperUi.INK_SOFT);
		}
	}

	private static final class EditList extends PaperList<EditRow> {
		EditList(Minecraft minecraft, int x, int y, int width, int height) {
			super(minecraft, x, y, width, height, PaperUi.ROW_HEIGHT);
		}

		void addRow(EditRow row) {
			addEntry(row);
		}

		void focusLastRow() {
			if (!children().isEmpty()) {
				EditRow row = children().get(children().size() - 1);
				setFocused(row);
				row.setFocused(row.field);
			}
		}
	}

	private final class EditRow extends ContainerObjectSelectionList.Entry<EditRow> {
		private final int index;
		private final PaperField field;
		private final GlyphButton remove;

		EditRow(int index) {
			this.index = index;
			field = new PaperField(font, title, itemKind());
			field.setValue(draft.get(index));
			field.setResponder(text -> {
				draft.set(index, text);
				field.showValid(looksValid(text));
				failure = null;
			});
			field.showValid(looksValid(draft.get(index)));
			if (ConfigWidgetRules.listItemsAreIds(entry)) {
				// the id's form, as the /stand give command takes it: not text to translate
				field.setHint(Component.literal("jojo_ripples:star_platinum").withColor(PaperUi.INK_FAINT & 0xFFFFFF));
			}
			field.setEditable(canEdit());
			field.showEditable(canEdit());
			remove = new GlyphButton(Glyph.MINUS, Component.translatable(TEXT + "list.remove"), PaperUi.ERROR, () -> removeRow(index));
			remove.visible = canEdit() && !fixedSize() && sizeAllows(draft.size() - 1);
		}

		@Override
		public void render(GuiGraphics graphics, int rowIndex, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovering, float partialTick) {
			String difficulty = ConfigWidgetRules.listRowLabel(entry, index);
			Component label = difficulty != null ? Component.translatable(difficulty) : Component.literal(Integer.toString(index + 1));
			int labelWidth = difficulty != null ? 66 : 20;
			String shown = PaperUi.oneLine(font, label.getString(), labelWidth);
			PaperUi.text(graphics, font, Component.literal(shown), left + 2, top + 5, PaperUi.INK_SOFT);
			int removeX = left + width - 4 - 14;
			remove.setPosition(removeX, top + 2);
			remove.render(graphics, mouseX, mouseY, partialTick);
			int fieldX = left + labelWidth + 6;
			field.place(fieldX, top, removeX - 4 - fieldX, PaperUi.CONTROL_HEIGHT);
			field.render(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.<AbstractWidget>of(field, remove);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.<AbstractWidget>of(field, remove);
		}
	}
}
