package rotp.core.config.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.config.client.ClientModSettingsScreen;
import rotp.core.config.client.screen.ConfigControls.Control;
import rotp.core.config.client.screen.ConfigControls.GlyphButton;
import rotp.core.config.client.screen.ConfigControls.PaperAction;
import rotp.core.config.client.screen.PaperUi.Glyph;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.CoreConfigPresets;
import rotp.core.config.editor.client.ConfigStore;
import rotp.core.config.editor.client.ConfigWidgetRules;
import rotp.core.config.editor.client.ConfigWidgetRules.Block;
import rotp.core.config.editor.client.ConfigWidgetRules.Group;
import rotp.core.config.editor.client.ConfigWidgetRules.Section;

/** The rows of one settings tab. Rows hold keys, not values: each frame they read what the store has accepted. */
public final class ConfigRowList extends PaperList<ConfigRowList.Row> {
	private static final int REFUSAL_FLASH_MILLIS = 1400;

	private final ConfigScreenHost host;

	public ConfigRowList(Minecraft minecraft, ConfigScreenHost host, int x, int y, int width, int height) {
		super(minecraft, x, y, width, height, PaperUi.ROW_HEIGHT);
		this.host = host;
	}

	public void rebuild(ConfigEntry.Side side, Set<String> collapsed, Set<String> openBlocks) {
		double scroll = getScrollAmount();
		ConfigStore store = host.store();
		List<Row> rows = new ArrayList<>();
		if (side == ConfigEntry.Side.SERVER && !store.entries().isEmpty()) {
			rows.add(new SectionRow(host, "presets", List.of(), collapsed));
			if (!collapsed.contains("presets")) {
				rows.add(new PresetRow(host));
			}
		}
		for (Section section : ConfigWidgetRules.sections(side, store.entries())) {
			List<String> keys = section.entries().stream().map(ConfigEntry::key).toList();
			rows.add(new SectionRow(host, section.id(), keys, collapsed));
			if (!collapsed.contains(section.id())) {
				section.entries().forEach(entry -> rows.add(new SettingRow(host, entry)));
			}
		}
		List<Block> blocks = ConfigWidgetRules.blocks(store.entries(), PaperUi::blockName);
		if (!blocks.isEmpty()) {
			rows.add(new DividerRow(host));
		}
		for (Block block : blocks) {
			// the same add-on's two pages have different owners, and each keeps its own fold
			String id = side + "/" + block.modId();
			rows.add(new SectionRow(host, Component.literal(PaperUi.blockName(block.modId())), block.keys(),
					() -> openBlocks.contains(id), () -> {
						if (!openBlocks.remove(id)) {
							openBlocks.add(id);
						}
					}));
			if (openBlocks.contains(id)) {
				for (Group group : block.groups()) {
					if (block.groups().size() > 1 && !group.path().isEmpty()) {
						rows.add(new GroupRow(host, PaperUi.groupName(block.modId(), group.path())));
					}
					group.entries().forEach(entry -> rows.add(new SettingRow(host, entry)));
				}
			}
		}
		replaceEntries(rows);
		setScrollAmount(scroll);
	}

	public void tickControls(long now) {
		for (Row row : children()) {
			row.tickRow(now);
		}
	}

	/** Sends what is typed or dragged. True when something was. */
	public boolean sendUnsentEdits() {
		boolean any = false;
		for (Row row : children()) {
			any |= row.sendUnsent();
		}
		return any;
	}

	/** Esc: forgets the edit in hand, if there is one, before the screen would close. */
	public boolean dropFocusedEdit() {
		Row focused = getFocused();
		return focused != null && focused.dropUnsent();
	}

	public @Nullable Row hoveredRow() {
		return getHovered();
	}

	public @Nullable Row rowAt(double mouseX, double mouseY) {
		return isMouseOver(mouseX, mouseY) ? getEntryAtPosition(mouseX, mouseY) : null;
	}

	public int topOf(Row row) {
		return getRowTop(children().indexOf(row));
	}

	public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
		void tickRow(long now) {}

		boolean sendUnsent() {
			return false;
		}

		boolean dropUnsent() {
			return false;
		}

		/** What the row's tooltip says, when the pointer rests on it. */
		List<Component> tooltip() {
			return List.of();
		}

		abstract @Nullable AbstractWidget focusedWidget();
	}

	static final class SettingRow extends Row {
		private final ConfigScreenHost host;
		private final String key;
		private final Control control;
		private final GlyphButton reset;
		private final @Nullable ResourceLocation icon;
		private boolean enabled = true;

		SettingRow(ConfigScreenHost host, ConfigEntry entry) {
			this.host = host;
			this.key = entry.key();
			this.control = ConfigControls.forEntry(entry, host.store(), host.font(), () -> {
				ConfigEntry current = host.store().entry(key);
				if (current != null) {
					host.openList(current);
				}
			}, host::typedValueRejected);
			this.reset = new GlyphButton(Glyph.RESET, Component.translatable("jojo_ripples.config.screen.reset"), PaperUi.INK_SOFT,
					() -> host.store().reset(key));
			String iconName = ConfigWidgetRules.iconName(key);
			this.icon = iconName != null ? ClientModSettingsScreen.toIconPath(iconName) : null;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovering, float partialTick) {
			ConfigEntry entry = host.store().entry(key);
			if (entry == null) {
				return;
			}
			Font font = host.font();
			JsonElement pending = host.store().pending(key);
			String needs = ConfigWidgetRules.enabledBy(key);
			ConfigEntry needed = needs != null ? host.store().entry(needs) : null;
			enabled = needed == null || needed.value().getAsBoolean();
			boolean editable = host.editable() && enabled;
			boolean modified = ConfigWidgetRules.isModified(entry);

			int rowTop = top - 1;
			int rowBottom = top + PaperUi.ROW_HEIGHT - 1;
			int rowRight = left + width - 4;
			if (hovering) {
				graphics.fill(left - 2, rowTop, rowRight + 2, rowBottom, PaperUi.HOVER);
			}
			long flash = host.refusalAge(key);
			if (flash >= 0 && flash < REFUSAL_FLASH_MILLIS) {
				int alpha = (int) (0x50 * (1 - flash / (double) REFUSAL_FLASH_MILLIS));
				graphics.fill(left - 2, rowTop, rowRight + 2, rowBottom, alpha << 24 | (PaperUi.ERROR & 0xFFFFFF));
			}
			if (modified) {
				graphics.fill(left - 2, top + 2, left, top + PaperUi.CONTROL_HEIGHT - 2, editable || host.editable() ? PaperUi.ACCENT : PaperUi.INK_FAINT);
			}

			int resetX = rowRight - 14;
			int controlWidth = Math.max(66, Math.min(128, width * 36 / 100));
			int controlX = resetX - 3 - controlWidth;
			control.sync(entry, pending, editable);
			control.place(controlX, top, controlWidth, PaperUi.CONTROL_HEIGHT);
			control.widget().render(graphics, mouseX, mouseY, partialTick);

			reset.visible = modified && editable && pending == null;
			reset.setPosition(resetX, top + 2);
			reset.render(graphics, mouseX, mouseY, partialTick);

			int textX = left + 3;
			if (icon != null) {
				BlitFloat.blit(graphics.pose(), Minecraft.getInstance(), icon, textX, top + 1, 16, 16, 0, 0, 0, 16, 16, 16, 16, BlitFloat.NO_TINT);
				textX += 18;
			}
			boolean restart = entry.restart() != ConfigEntry.Restart.NONE;
			int flagsWidth = (entry.synced() ? 11 : 0) + (restart ? 11 : 0);
			int labelWidth = control.leftEdge() - 6 - flagsWidth - textX;
			int color = !enabled ? PaperUi.INK_FAINT : host.editable() ? PaperUi.INK : PaperUi.INK_SOFT;
			List<FormattedCharSequence> lines = PaperUi.fit(font, PaperUi.label(entry).getString(), labelWidth, 2);
			int textY = lines.size() == 1 ? top + (PaperUi.CONTROL_HEIGHT - 8) / 2 : top - 1;
			int widest = 0;
			for (FormattedCharSequence line : lines) {
				PaperUi.text(graphics, font, line, textX, textY, color);
				widest = Math.max(widest, font.width(line));
				textY += 10;
			}
			// the flags belong to the name, so they sit right after it
			int flagX = textX + widest + 5;
			if (entry.synced()) {
				PaperUi.glyph(graphics, Glyph.SYNC, flagX, top + 4, PaperUi.LINE);
				flagX += 11;
			}
			if (restart) {
				PaperUi.glyph(graphics, Glyph.RESTART, flagX, top + 4, PaperUi.PENDING);
			}
			if (reset.isHovered() && reset.visible) {
				host.showTooltip(List.of(reset.getMessage(), Component.translatable("jojo_ripples.config.screen.default",
						PaperUi.valueText(entry, entry.defaultValue()))));
			}
		}

		@Override
		List<Component> tooltip() {
			ConfigEntry entry = host.store().entry(key);
			return entry == null || reset.isHovered() ? List.of() : ConfigTooltips.forEntry(entry, host.store());
		}

		@Override
		void tickRow(long now) {
			control.tickControl(now);
		}

		@Override
		boolean sendUnsent() {
			if (control.hasUnsentEdit()) {
				control.sendEdit();
				return true;
			}
			return false;
		}

		@Override
		boolean dropUnsent() {
			if (control.hasUnsentEdit()) {
				control.dropEdit();
				return true;
			}
			return false;
		}

		@Override
		@Nullable AbstractWidget focusedWidget() {
			return getFocused() instanceof AbstractWidget widget ? widget : null;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(control.widget(), reset);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(control.widget(), reset);
		}
	}

	static final class SectionRow extends Row {
		private final ConfigScreenHost host;
		private final List<String> keys;
		private final BooleanSupplier open;
		private final HeaderButton header;
		private final GlyphButton reset;

		SectionRow(ConfigScreenHost host, String id, List<String> keys, Set<String> collapsed) {
			this(host, Component.translatableWithFallback(ConfigWidgetRules.SECTION_TEXT + id, ConfigWidgetRules.fallbackName(id)), keys,
					() -> !collapsed.contains(id), () -> {
						if (!collapsed.remove(id)) {
							collapsed.add(id);
						}
					});
		}

		SectionRow(ConfigScreenHost host, Component title, List<String> keys, BooleanSupplier open, Runnable toggle) {
			this.host = host;
			this.keys = keys;
			this.open = open;
			this.header = new HeaderButton(title, () -> {
				toggle.run();
				host.rowsChanged();
			});
			this.reset = new GlyphButton(Glyph.RESET, Component.translatable("jojo_ripples.config.screen.reset_section"), PaperUi.INK_SOFT,
					() -> host.confirmResetKeys(title, keys));
		}

		private long modifiedCount() {
			return keys.stream().map(host.store()::entry).filter(entry -> entry != null && ConfigWidgetRules.isShown(entry)
					&& ConfigWidgetRules.isModified(entry)).count();
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovering, float partialTick) {
			int rowRight = left + width - 4;
			long modified = modifiedCount();
			header.open = open.getAsBoolean();
			header.modified = (int) modified;
			header.rowHovered = hovering;
			header.setPosition(left - 2, top - 1);
			header.setWidth(rowRight - left + 4);
			header.render(graphics, mouseX, mouseY, partialTick);
			reset.visible = modified > 0 && host.editable();
			reset.setPosition(rowRight - 14, top + 3);
			reset.render(graphics, mouseX, mouseY, partialTick);
			if (reset.isHovered() && reset.visible) {
				host.showTooltip(List.of(reset.getMessage()));
			}
		}

		@Override
		@Nullable AbstractWidget focusedWidget() {
			return getFocused() instanceof AbstractWidget widget ? widget : null;
		}

		// the reset comes first: it sits on top of the header and takes its clicks
		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(reset, header);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(header, reset);
		}

		private final class HeaderButton extends AbstractButton {
			private final Runnable press;
			private boolean open;
			private int modified;
			private boolean rowHovered;

			HeaderButton(Component title, Runnable press) {
				super(0, 0, 100, PaperUi.ROW_HEIGHT, title);
				this.press = press;
			}

			@Override
			public void onPress() {
				press.run();
			}

			@Override
			protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
				Font font = host.font();
				int y = getY();
				// the button overlaps the row above by a pixel: only the list's own answer may light it
				if (isHovered() && rowHovered) {
					graphics.fill(getX(), y, getRight(), getBottom(), PaperUi.HOVER);
				}
				if (isFocused()) {
					graphics.renderOutline(getX(), y, width, height, PaperUi.INK);
				}
				PaperUi.glyph(graphics, open ? Glyph.CHEVRON_DOWN : Glyph.CHEVRON_RIGHT, getX() + 3, y + 8, PaperUi.INK_SOFT);
				String title = PaperUi.oneLine(font, getMessage().getString(), width - 50);
				Component shown = Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD);
				PaperUi.text(graphics, font, shown, getX() + 15, y + 9, PaperUi.INK);
				int lineX = getX() + 19 + font.width(shown);
				if (!open && modified > 0) {
					String count = Integer.toString(modified);
					PaperUi.text(graphics, font, Component.literal(count), lineX, y + 9, PaperUi.ACCENT);
					lineX += font.width(count) + 4;
				}
				graphics.fill(lineX, y + 13, getRight() - 18, y + 14, PaperUi.LINE_SOFT);
			}

			@Override
			protected void updateWidgetNarration(NarrationElementOutput output) {
				defaultButtonNarrationText(output);
			}
		}
	}

	/** Where the core's sections end and the add-ons' blocks begin: a word on a line, and nothing to press. */
	static final class DividerRow extends Row {
		private final ConfigScreenHost host;

		DividerRow(ConfigScreenHost host) {
			this.host = host;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovering, float partialTick) {
			Font font = host.font();
			String text = Component.translatableWithFallback("jojo_ripples.config.screen.addons", "Add-ons").getString();
			int right = left + width - 4;
			int textX = left + (right - left - font.width(text)) / 2;
			int lineY = top + 11;
			graphics.fill(left + 2, lineY, textX - 6, lineY + 1, PaperUi.LINE);
			graphics.fill(textX + font.width(text) + 6, lineY, right - 2, lineY + 1, PaperUi.LINE);
			PaperUi.text(graphics, font, Component.literal(text), textX, top + 7, PaperUi.INK_SOFT);
		}

		@Override
		@Nullable AbstractWidget focusedWidget() {
			return null;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}

	/** A group inside an add-on's block: lighter than a section, and it folds nothing. */
	static final class GroupRow extends Row {
		private final ConfigScreenHost host;
		private final String name;

		GroupRow(ConfigScreenHost host, String name) {
			this.host = host;
			this.name = name;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovering, float partialTick) {
			Font font = host.font();
			String shown = PaperUi.oneLine(font, name, width - 12);
			PaperUi.text(graphics, font, Component.literal(shown), left + 3, top + 9, PaperUi.ACCENT);
			graphics.fill(left + 3, top + 19, left + 3 + font.width(shown), top + 20, PaperUi.ACCENT_FILL);
		}

		@Override
		@Nullable AbstractWidget focusedWidget() {
			return null;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}

	static final class PresetRow extends Row {
		private static final int GAP = 3;
		private final ConfigScreenHost host;
		private final List<PaperAction> buttons = new ArrayList<>();
		private final List<CoreConfigPresets.Preset> presets = CoreConfigPresets.entries();
		private final PaperAction defaults;

		PresetRow(ConfigScreenHost host) {
			this.host = host;
			for (CoreConfigPresets.Preset preset : presets) {
				buttons.add(new PaperAction(0, 0, 60, PaperUi.CONTROL_HEIGHT, ConfigTooltips.presetName(preset),
						button -> host.confirmPreset(preset)));
			}
			defaults = new PaperAction(0, 0, 60, PaperUi.CONTROL_HEIGHT, Component.translatable("jojo_ripples.config.preset.defaults"),
					button -> host.confirmPresetDefaults());
			buttons.add(defaults);
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovering, float partialTick) {
			int usable = width - 4 - 2;
			int each = (usable - GAP * (buttons.size() - 1)) / buttons.size();
			int x = left;
			List<ConfigEntry> entries = host.store().entries();
			for (int i = 0; i < buttons.size(); i++) {
				PaperAction button = buttons.get(i);
				button.active = host.editable();
				button.setPosition(x, top);
				button.setWidth(each);
				button.render(graphics, mouseX, mouseY, partialTick);
				if (i < presets.size() && ConfigWidgetRules.holdsValues(presets.get(i).values(), entries)) {
					PaperUi.glyph(graphics, Glyph.CHECK, x + 3, top + 4, button.active ? PaperUi.ON : PaperUi.INK_FAINT);
				}
				x += each + GAP;
			}
		}

		@Override
		List<Component> tooltip() {
			for (int i = 0; i < buttons.size(); i++) {
				if (buttons.get(i).isHovered()) {
					return i < presets.size() ? ConfigTooltips.forPreset(presets.get(i), host.editable())
							: ConfigTooltips.forPresetDefaults(host.editable());
				}
			}
			return List.of();
		}

		@Override
		@Nullable AbstractWidget focusedWidget() {
			return getFocused() instanceof AbstractWidget widget ? widget : null;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return buttons;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return buttons;
		}
	}
}
