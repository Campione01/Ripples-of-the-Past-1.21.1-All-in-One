package rotp.core.config.client.screen;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import rotp.core.config.client.screen.ConfigControls.PaperAction;
import rotp.core.config.client.screen.ConfigControls.PaperField;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.client.ConfigStore;
import rotp.core.config.editor.client.ConfigWidgetRules;

/**
 * A colour setting picked from the sixteen dye colours or typed as #RRGGBB, with the colour shown large. Nothing leaves
 * the card until Done; leaving with a change asks first.
 */
final class ConfigColorScreen extends PaperDialog {
	private static final String TEXT = "jojo_ripples.config.screen.";
	private static final int SWATCH = 20;
	private static final int PER_ROW = 8;

	private final ConfigStore store;
	private final String key;
	private ConfigEntry entry;
	private int draft;
	private @Nullable Component failure;
	private boolean waitingForReply;
	private boolean askingToDiscard;
	private @Nullable PaperField hex;
	private int previewY;

	ConfigColorScreen(ModConfigScreen parent, ConfigStore store, ConfigEntry entry) {
		super(parent, PaperUi.label(entry));
		this.store = store;
		this.key = entry.key();
		this.entry = entry;
		this.draft = entry.value().getAsInt() & 0xFFFFFF;
	}

	private boolean canEdit() {
		return store.access() == ConfigStore.Access.EDITABLE;
	}

	private boolean isDirty() {
		return draft != (entry.value().getAsInt() & 0xFFFFFF);
	}

	@Override
	protected void init() {
		super.init();
		placeCard(Math.max(220, Math.min(300, width - 40)), 196);
		previewY = cardY + 22;
		int gridY = previewY + 30;
		int gridWidth = PER_ROW * SWATCH + (PER_ROW - 1) * 4;
		int gridX = cardX + (cardWidth - gridWidth) / 2;
		DyeColor[] dyes = DyeColor.values();
		for (int i = 0; i < dyes.length; i++) {
			int x = gridX + (i % PER_ROW) * (SWATCH + 4);
			int y = gridY + (i / PER_ROW) * (SWATCH + 4);
			addRenderableWidget(new Swatch(x, y, dyes[i]));
		}
		int fieldY = gridY + 2 * (SWATCH + 4) + 4;
		PaperField field = new PaperField(font, Component.translatable(TEXT + "color.hex"), ConfigEntry.Kind.STRING);
		field.setFilter(text -> text.matches("#?[0-9a-fA-F]{0,6}"));
		field.setMaxLength(7);
		field.setValue(hexOf(draft));
		field.place(cardX + (cardWidth - 90) / 2, fieldY, 90, PaperUi.CONTROL_HEIGHT);
		field.setEditable(canEdit() && !waitingForReply);
		field.showEditable(canEdit() && !waitingForReply);
		field.setResponder(text -> {
			JsonElement parsed = ConfigWidgetRules.parseColor(text);
			field.showValid(parsed != null || text.isEmpty());
			if (parsed != null) {
				draft = parsed.getAsInt();
				failure = null;
			}
		});
		hex = addRenderableWidget(field);

		int footerY = cardY + cardHeight - 24;
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
				Component.translatable(TEXT + "reset"), button -> pick(entry.defaultValue().getAsInt() & 0xFFFFFF)));
		reset.active = !waitingForReply;
		PaperAction save = addRenderableWidget(new PaperAction(cardX + 10 + third + gap, footerY, third, PaperUi.CONTROL_HEIGHT,
				CommonComponents.GUI_DONE, button -> save()));
		save.active = !waitingForReply;
		addRenderableWidget(new PaperAction(cardX + 10 + (third + gap) * 2, footerY, third, PaperUi.CONTROL_HEIGHT,
				CommonComponents.GUI_CANCEL, button -> onClose()));
	}

	private static String hexOf(int color) {
		return ConfigWidgetRules.formatColor(new JsonPrimitive(color));
	}

	private void pick(int color) {
		if (!canEdit() || waitingForReply) {
			return;
		}
		draft = color & 0xFFFFFF;
		failure = null;
		if (hex != null) {
			hex.setValue(hexOf(draft));
			hex.showValid(true);
		}
	}

	private void save() {
		if (!isDirty()) {
			super.onClose();
			return;
		}
		if (store.set(key, new JsonPrimitive(draft))) {
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
			if (!isDirty()) {
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
		int left = cardX + 10;
		int right = cardX + cardWidth - 10;
		graphics.fill(left, previewY, right, previewY + 24, PaperUi.INK_SOFT);
		graphics.fill(left + 1, previewY + 1, right - 1, previewY + 23, 0xFF000000 | draft);
		String shown = hexOf(draft);
		// dark ink on a light colour, light ink on a dark one
		int luma = ((draft >> 16 & 0xFF) * 299 + (draft >> 8 & 0xFF) * 587 + (draft & 0xFF) * 114) / 1000;
		PaperUi.text(graphics, font, Component.literal(shown), cardX + (cardWidth - font.width(shown)) / 2, previewY + 8,
				luma > 140 ? PaperUi.INK : PaperUi.PAPER_LIGHT);
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
	}

	/** One dye colour: a click or Enter takes it. */
	private final class Swatch extends AbstractButton {
		private final DyeColor dye;

		Swatch(int x, int y, DyeColor dye) {
			super(x, y, SWATCH, SWATCH, Component.translatable("color.minecraft." + dye.getName()));
			this.dye = dye;
			setTooltip(Tooltip.create(Component.empty().append(getMessage()).append(" " + hexOf(dye.getTextColor()))));
		}

		@Override
		public void onPress() {
			pick(dye.getTextColor());
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			active = canEdit() && !waitingForReply;
			boolean chosen = (dye.getTextColor() & 0xFFFFFF) == draft;
			int edge = isFocused() || isHovered() && active || chosen ? PaperUi.INK : PaperUi.LINE;
			graphics.fill(getX(), getY(), getRight(), getBottom(), edge);
			graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, PaperUi.PAPER_LIGHT);
			graphics.fill(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2, 0xFF000000 | dye.getTextColor());
			if (chosen) {
				graphics.renderOutline(getX() - 2, getY() - 2, width + 4, height + 4, PaperUi.ACCENT);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, Component.translatable("narration.button", getMessage()));
		}
	}
}
