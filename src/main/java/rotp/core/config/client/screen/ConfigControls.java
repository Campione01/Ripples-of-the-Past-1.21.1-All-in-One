package rotp.core.config.client.screen;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import rotp.core.client.ui.screen_jojomenu.PaperButton;
import rotp.core.config.client.screen.PaperUi.Glyph;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.client.ConfigStore;
import rotp.core.config.editor.client.ConfigWidgetRules;

/** The widgets of a setting's row. Each shows what the store has accepted; what is still on its way shows as waiting. */
final class ConfigControls {
	private static final long SLIDER_SETTLE_MILLIS = 250;

	private ConfigControls() {}

	interface Control {
		/** Every frame: the store's word for this setting, and whether it can be changed right now. */
		void sync(ConfigEntry entry, @Nullable JsonElement pending, boolean editable);

		void place(int x, int y, int width, int height);

		default void tickControl(long now) {}

		/** Typed or dragged and not sent yet. */
		default boolean hasUnsentEdit() {
			return false;
		}

		default void sendEdit() {}

		default void dropEdit() {}

		default AbstractWidget widget() {
			return (AbstractWidget) this;
		}

		default int leftEdge() {
			return widget().getX();
		}
	}

	static Control forEntry(ConfigEntry entry, ConfigStore store, Font font, Runnable openList, Consumer<ConfigEntry> typedWrong) {
		return switch (ConfigWidgetRules.widgetKind(entry)) {
			case TOGGLE -> new ToggleControl(entry.key(), store, font);
			case SLIDER -> new SliderControl(entry, store, font);
			case NUMBER, TEXT -> new FieldControl(entry, store, font, typedWrong);
			case CYCLE -> new CycleControl(entry.key(), store);
			case LIST -> new ListControl(openList);
		};
	}

	static final class ToggleControl extends AbstractButton implements Control {
		private static final int TRACK_WIDTH = 24;
		private static final int TRACK_HEIGHT = 12;
		private final String key;
		private final ConfigStore store;
		private final Font font;
		private boolean on;
		private boolean waiting;

		ToggleControl(String key, ConfigStore store, Font font) {
			super(0, 0, 60, PaperUi.CONTROL_HEIGHT, CommonComponents.EMPTY);
			this.key = key;
			this.store = store;
			this.font = font;
		}

		@Override
		public void sync(ConfigEntry entry, @Nullable JsonElement pending, boolean editable) {
			on = entry.value().getAsBoolean();
			waiting = pending != null;
			active = editable && !waiting;
			setMessage(CommonComponents.optionStatus(PaperUi.label(entry), on));
		}

		@Override
		public void place(int x, int y, int width, int height) {
			// only the switch and its word take the click, not the whole column
			int own = Math.min(width, TRACK_WIDTH + 44);
			setPosition(x + width - own, y);
			setWidth(own);
			setHeight(height);
		}

		@Override
		public void onPress() {
			store.set(key, new JsonPrimitive(!on));
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			int trackX = getRight() - TRACK_WIDTH;
			int trackY = getY() + (height - TRACK_HEIGHT) / 2;
			boolean live = active || waiting;
			int fill = waiting ? PaperUi.PENDING : !live ? PaperUi.LINE_SOFT : on ? PaperUi.ON : PaperUi.LINE;
			int edge = isHoveredOrFocused() && active ? PaperUi.INK : PaperUi.INK_SOFT;
			graphics.fill(trackX, trackY, trackX + TRACK_WIDTH, trackY + TRACK_HEIGHT, edge);
			graphics.fill(trackX + 1, trackY + 1, trackX + TRACK_WIDTH - 1, trackY + TRACK_HEIGHT - 1, fill);
			int knobX = on ? trackX + TRACK_WIDTH - TRACK_HEIGHT : trackX;
			graphics.fill(knobX, trackY, knobX + TRACK_HEIGHT, trackY + TRACK_HEIGHT, edge);
			graphics.fill(knobX + 1, trackY + 1, knobX + TRACK_HEIGHT - 1, trackY + TRACK_HEIGHT - 1, PaperUi.PAPER_LIGHT);
			if (isFocused()) {
				graphics.renderOutline(trackX - 2, trackY - 2, TRACK_WIDTH + 4, TRACK_HEIGHT + 4, PaperUi.INK);
			}
			Component state = Component.translatable(on ? "options.on" : "options.off");
			int color = !live ? PaperUi.INK_FAINT : on ? PaperUi.INK : PaperUi.INK_SOFT;
			String text = PaperUi.oneLine(font, state.getString(), width - TRACK_WIDTH - 6);
			PaperUi.text(graphics, font, Component.literal(text), trackX - 5 - font.width(text), getY() + (height - 8) / 2, color);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	static final class SliderControl extends AbstractSliderButton implements Control {
		private final ConfigStore store;
		private final Font font;
		private ConfigEntry entry;
		private JsonElement shown;
		private boolean waiting;
		private boolean unsent;
		private long lastChange;

		SliderControl(ConfigEntry entry, ConfigStore store, Font font) {
			super(0, 0, 80, PaperUi.CONTROL_HEIGHT, CommonComponents.EMPTY, ConfigWidgetRules.sliderFraction(entry, entry.value()));
			this.entry = entry;
			this.shown = entry.value();
			this.store = store;
			this.font = font;
		}

		@Override
		public void sync(ConfigEntry fresh, @Nullable JsonElement pending, boolean editable) {
			entry = fresh;
			waiting = pending != null;
			active = editable && !waiting;
			if (!unsent) {
				shown = pending != null ? pending : fresh.value();
				value = ConfigWidgetRules.sliderFraction(fresh, shown);
			}
			setMessage(CommonComponents.optionNameValue(PaperUi.label(fresh), Component.literal(text())));
		}

		private String text() {
			return ConfigWidgetRules.sliderText(entry, shown);
		}

		@Override
		public void place(int x, int y, int width, int height) {
			setPosition(x, y);
			setWidth(width);
			setHeight(height);
		}

		@Override
		protected void updateMessage() {
		}

		@Override
		protected void applyValue() {
			shown = ConfigWidgetRules.sliderValue(entry, value);
			unsent = true;
			lastChange = Util.getMillis();
		}

		@Override
		public void tickControl(long now) {
			if (unsent && !Minecraft.getInstance().mouseHandler.isLeftPressed() && now - lastChange >= SLIDER_SETTLE_MILLIS) {
				sendEdit();
			}
		}

		@Override
		public boolean hasUnsentEdit() {
			return unsent;
		}

		@Override
		public void sendEdit() {
			if (unsent) {
				unsent = false;
				if (!shown.equals(entry.value())) {
					store.set(entry.key(), shown);
				}
			}
		}

		@Override
		public void dropEdit() {
			unsent = false;
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX();
			int y = getY();
			boolean live = active || waiting;
			boolean hot = isHoveredOrFocused() && active;
			graphics.fill(x, y, x + width, y + height, hot ? PaperUi.INK_SOFT : PaperUi.LINE);
			graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PaperUi.PAPER_LIGHT);
			int handle = x + 1 + (int) (value * (width - 4));
			graphics.fill(x + 1, y + 1, handle, y + height - 1, !live ? PaperUi.PANEL : waiting ? 0xFFEBD49B : PaperUi.ACCENT_FILL);
			graphics.fill(handle, y + 1, handle + 2, y + height - 1, !live ? PaperUi.LINE : waiting ? PaperUi.PENDING : PaperUi.ACCENT);
			if (isFocused()) {
				graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, PaperUi.INK);
			}
			String text = text();
			Component label = Component.literal(text);
			int textX = x + (width - font.width(text)) / 2;
			int textY = y + (height - 8) / 2;
			// a paper rim keeps a digit or the decimal point readable where the handle crosses the value
			PaperUi.text(graphics, font, label, textX - 1, textY, PaperUi.PAPER_LIGHT);
			PaperUi.text(graphics, font, label, textX + 1, textY, PaperUi.PAPER_LIGHT);
			PaperUi.text(graphics, font, label, textX, textY - 1, PaperUi.PAPER_LIGHT);
			PaperUi.text(graphics, font, label, textX, textY + 1, PaperUi.PAPER_LIGHT);
			PaperUi.text(graphics, font, label, textX, textY, live ? PaperUi.INK : PaperUi.INK_SOFT);
		}
	}

	/** A text box on paper: dark text with no shadow, its box drawn around the area vanilla's EditBox types in. */
	static class PaperField extends EditBox {
		private static final int PAD_X = 4;
		private static final int PAD_Y = 5;
		protected boolean valid = true;
		protected boolean waiting;
		protected boolean editable = true;
		private final Font font;
		private @Nullable Component hint;

		PaperField(Font font, Component name, Kind kind) {
			super(font, 0, 0, 72, 9, name);
			this.font = font;
			setBordered(false);
			setTextShadow(false);
			setTextColor(PaperUi.INK);
			setTextColorUneditable(PaperUi.INK_SOFT);
			setMaxLength(kind == Kind.STRING ? 256 : 32);
			if (kind != Kind.STRING) {
				setFilter(text -> text.matches("[-+0-9.eE]*"));
			}
		}

		public void place(int x, int y, int width, int height) {
			setPosition(x + PAD_X, y + PAD_Y);
			int inner = width - 2 * PAD_X;
			if (inner != getWidth()) {
				setWidth(inner);
				// a text set while the box was narrower stays scrolled to its end
				if (!isFocused()) {
					moveCursorToStart(false);
				}
			}
		}

		public int leftEdge() {
			return getX() - PAD_X;
		}

		void showValid(boolean shown) {
			valid = shown;
		}

		void showEditable(boolean shown) {
			editable = shown;
		}

		@Override
		public void setHint(Component hint) {
			super.setHint(hint);
			this.hint = hint;
		}

		@Override
		protected boolean clicked(double mouseX, double mouseY) {
			return active && visible && mouseX >= getX() - PAD_X && mouseX < getRight() + PAD_X
					&& mouseY >= getY() - PAD_Y && mouseY < getY() - PAD_Y + PaperUi.CONTROL_HEIGHT;
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX() - PAD_X;
			int y = getY() - PAD_Y;
			int boxWidth = width + 2 * PAD_X;
			int edge = !valid ? PaperUi.ERROR : waiting ? PaperUi.PENDING : isFocused() ? PaperUi.INK : editable ? PaperUi.LINE : PaperUi.LINE_SOFT;
			graphics.fill(x, y, x + boxWidth, y + PaperUi.CONTROL_HEIGHT, edge);
			graphics.fill(x + 1, y + 1, x + boxWidth - 1, y + PaperUi.CONTROL_HEIGHT - 1, editable ? PaperUi.PAPER_LIGHT : 0xFFE6E8EC);
			super.renderWidget(graphics, mouseX, mouseY, partialTick);
			// the box drops its hint on focus, and a new row is focused at once: it would never be seen
			if (hint != null && isFocused() && getValue().isEmpty()) {
				PaperUi.text(graphics, font, hint, getX(), getY(), PaperUi.INK_FAINT);
			}
		}
	}

	static final class FieldControl extends PaperField implements Control {
		private final ConfigStore store;
		private final Consumer<ConfigEntry> typedWrong;
		private ConfigEntry entry;
		private String syncedText = "";

		FieldControl(ConfigEntry entry, ConfigStore store, Font font, Consumer<ConfigEntry> typedWrong) {
			super(font, PaperUi.label(entry), entry.kind());
			this.entry = entry;
			this.store = store;
			this.typedWrong = typedWrong;
			setResponder(this::onTyped);
		}

		private void onTyped(String typed) {
			valid = parse(typed) != null || typed.equals(syncedText);
		}

		private @Nullable JsonElement parse(String typed) {
			return ConfigWidgetRules.parseScalar(entry.kind(), entry.bounds(), typed);
		}

		private String format(JsonElement value) {
			return entry.kind() == Kind.STRING ? value.getAsString() : ConfigWidgetRules.formatNumber(entry.kind(), value);
		}

		@Override
		public void sync(ConfigEntry fresh, @Nullable JsonElement pending, boolean canEdit) {
			entry = fresh;
			waiting = pending != null;
			editable = canEdit && !waiting;
			active = canEdit || waiting;
			setEditable(editable);
			String text = format(pending != null ? pending : fresh.value());
			if (!isFocused() && !text.equals(getValue())) {
				syncedText = text;
				setValue(text);
				valid = true;
			}
			syncedText = text;
		}

		@Override
		public boolean hasUnsentEdit() {
			return isFocused() && !getValue().trim().equals(syncedText);
		}

		@Override
		public void sendEdit() {
			String typed = getValue();
			JsonElement parsed = parse(typed);
			if (typed.trim().equals(syncedText)) {
				return;
			}
			if (parsed == null) {
				typedWrong.accept(entry);
				dropEdit();
			}
			else if (!parsed.equals(entry.value())) {
				store.set(entry.key(), parsed);
			}
		}

		@Override
		public void dropEdit() {
			setValue(syncedText);
			valid = true;
		}

		@Override
		public void setFocused(boolean focused) {
			if (isFocused() && !focused) {
				sendEdit();
			}
			super.setFocused(focused);
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (canConsumeInput() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
				// a wrong value stays in the box, red, for the player to correct
				if (parse(getValue()) == null) {
					typedWrong.accept(entry);
				}
				else {
					sendEdit();
				}
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
	}

	static final class CycleControl extends PaperAction implements Control {
		private final String key;
		private final ConfigStore store;
		private ConfigEntry entry;
		private int direction = 1;

		CycleControl(String key, ConfigStore store) {
			super(0, 0, 80, PaperUi.CONTROL_HEIGHT, CommonComponents.EMPTY, button -> {});
			this.key = key;
			this.store = store;
		}

		@Override
		public void sync(ConfigEntry fresh, @Nullable JsonElement pending, boolean editable) {
			entry = fresh;
			active = editable && pending == null && !fresh.choices().isEmpty();
			setMessage(PaperUi.valueText(fresh, fresh.value()));
		}

		@Override
		public void place(int x, int y, int width, int height) {
			setPosition(x, y);
			setWidth(width);
			setHeight(height);
		}

		@Override
		protected boolean isValidClickButton(int button) {
			return button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			direction = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1;
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public void onPress() {
			if (entry != null) {
				store.set(key, new JsonPrimitive(ConfigWidgetRules.nextChoice(entry, entry.value(), direction)));
			}
			direction = 1;
		}

		@Override
		protected boolean showsChevron() {
			return true;
		}
	}

	static final class ListControl extends PaperAction implements Control {
		private final Runnable open;

		ListControl(Runnable open) {
			super(0, 0, 80, PaperUi.CONTROL_HEIGHT, CommonComponents.EMPTY, button -> {});
			this.open = open;
		}

		@Override
		public void sync(ConfigEntry entry, @Nullable JsonElement pending, boolean editable) {
			int size = entry.value().isJsonArray() ? entry.value().getAsJsonArray().size() : 0;
			active = pending == null;
			setMessage(size == 0 ? Component.translatable("jojo_ripples.config.screen.list.empty")
					: Component.translatable("jojo_ripples.config.screen.list.values", size));
		}

		@Override
		public void place(int x, int y, int width, int height) {
			setPosition(x, y);
			setWidth(width);
			setHeight(height);
		}

		@Override
		public void onPress() {
			open.run();
		}

		@Override
		protected boolean showsChevron() {
			return true;
		}
	}

	/** The paper button; when it cannot be pressed it is only an outline, so a read-only page stays quiet to look at. */
	static class PaperAction extends PaperButton {
		private final Consumer<PaperAction> press;

		PaperAction(int x, int y, int width, int height, Component message, Consumer<PaperAction> press) {
			super(x, y, width, height, message, button -> {});
			this.press = press;
		}

		@Override
		public void onPress() {
			press.accept(this);
		}

		protected boolean showsChevron() {
			return false;
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			Font font = Minecraft.getInstance().font;
			if (active) {
				graphics.blitSprite(PaperButton.ENABLED, getX(), getY(), width, height);
				if (isHovered()) {
					graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, PaperUi.PANEL);
				}
				renderScrollingString(graphics, font, 2, PaperUi.INK);
			}
			else {
				graphics.renderOutline(getX(), getY(), width, height, PaperUi.LINE_SOFT);
				renderScrollingString(graphics, font, 2, PaperUi.INK_FAINT);
			}
			if (showsChevron()) {
				PaperUi.glyph(graphics, Glyph.CHEVRON_RIGHT, getRight() - 10, getY() + (height - 9) / 2, active ? PaperUi.INK_SOFT : PaperUi.INK_FAINT);
			}
			if (isFocused()) {
				graphics.renderOutline(getX() - 1, getY() - 1, width + 2, height + 2, PaperUi.INK);
			}
		}

		@Override
		protected void renderScrollingString(GuiGraphics graphics, Font font, int offset, int color) {
			int minX = getX() + offset + 1;
			int maxX = getRight() - offset - 1 - (showsChevron() ? 8 : 0);
			PaperButton.renderScrollingString(graphics, font, getMessage(), (minX + maxX) / 2, minX, getY(), maxX, getBottom(),
					color, false);
		}
	}

	/** A small square button with one glyph. */
	static final class GlyphButton extends AbstractButton {
		private final Glyph glyph;
		private final Runnable press;
		private final int color;

		GlyphButton(Glyph glyph, Component name, int color, Runnable press) {
			super(0, 0, 14, 14, name);
			this.glyph = glyph;
			this.press = press;
			this.color = color;
		}

		@Override
		public void onPress() {
			press.run();
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			if (isHoveredOrFocused() && active) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), PaperUi.PANEL);
			}
			if (isFocused()) {
				graphics.renderOutline(getX(), getY(), width, height, PaperUi.INK);
			}
			PaperUi.glyph(graphics, glyph, getX() + (width - 9) / 2, getY() + (height - 9) / 2, active ? color : PaperUi.INK_FAINT);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
