package rotp.core.config.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import rotp.core.config.client.screen.ConfigControls.PaperAction;
import rotp.core.config.client.screen.PaperUi.Glyph;
import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.CoreConfigPresets;
import rotp.core.config.editor.client.ConfigEditSession;

/**
 * Shows what would change, and changes it only on the confirm button. For a preset the list comes from the server's
 * preview, which writes nothing; a reset's comes from the values the screen already holds.
 */
final class ConfigDiffScreen extends PaperDialog {
	private static final String TEXT = "jojo_ripples.config.screen.";
	private static final int ROW = 15;

	record Row(Component label, Component before, Component after, ConfigEntry.Restart restart) {
		static Row of(ConfigEntry entry, JsonElement before, JsonElement after, ConfigEntry.Restart restart) {
			return new Row(PaperUi.label(entry), PaperUi.valueText(entry, before), PaperUi.valueText(entry, after), restart);
		}
	}

	private enum State { ASKING, READY, WORKING, FAILED }

	private final Component confirmText;
	private final Consumer<Consumer<ConfigEditResult>> confirm;
	private final Component doneMessage;
	private List<Row> rows = List.of();
	private State state = State.READY;
	private @Nullable Component failure;
	private @Nullable PaperAction confirmButton;
	private boolean started;
	private int idleTicks;
	private @Nullable Runnable onFirstOpen;

	private ConfigDiffScreen(ModConfigScreen parent, Component title, Component confirmText, Component doneMessage,
			Consumer<Consumer<ConfigEditResult>> confirm) {
		super(parent, title);
		this.confirmText = confirmText;
		this.doneMessage = doneMessage;
		this.confirm = confirm;
	}

	static ConfigDiffScreen ofChanges(ModConfigScreen parent, Component title, Component confirmText, List<Row> rows,
			Consumer<Consumer<ConfigEditResult>> confirm) {
		ConfigDiffScreen screen = new ConfigDiffScreen(parent, title, confirmText, Component.translatable(TEXT + "status.reset"), confirm);
		screen.rows = rows;
		return screen;
	}

	static ConfigDiffScreen ofPreset(ModConfigScreen parent, ConfigEditSession session, CoreConfigPresets.Preset preset) {
		Component name = ConfigTooltips.presetName(preset);
		ConfigDiffScreen screen = new ConfigDiffScreen(parent, Component.translatable(TEXT + "preview.title", name),
				Component.translatable(TEXT + "preview.apply"), Component.translatable(TEXT + "status.preset_applied", name),
				done -> session.applyPreset(preset.id(), done));
		screen.state = State.ASKING;
		screen.onFirstOpen = () -> {
			if (!session.previewPreset(preset.id(), result -> screen.previewed(session, result))) {
				screen.fail(Status.FORBIDDEN);
			}
		};
		return screen;
	}

	private void previewed(ConfigEditSession session, ConfigEditResult result) {
		if (result.status() != Status.OK) {
			fail(result.status());
			return;
		}
		List<Row> changes = new ArrayList<>();
		for (ConfigEditResult.Change change : result.changes()) {
			ConfigEntry entry = session.entry(change.key());
			if (entry != null) {
				changes.add(Row.of(entry, change.before(), change.after(), change.restart()));
			}
		}
		rows = changes;
		state = State.READY;
		rebuildWidgets();
	}

	private void fail(Status status) {
		state = State.FAILED;
		failure = Component.translatable(TEXT + "status." + status.name().toLowerCase(java.util.Locale.ROOT));
		rebuildWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		if (!started) {
			started = true;
			if (onFirstOpen != null) {
				onFirstOpen.run();
			}
		}
		// a store that refused the confirm outright never calls back
		if (state == State.WORKING && !parent.store().busy() && ++idleTicks > 2) {
			fail(Status.UNAVAILABLE);
		}
	}

	@Override
	protected void init() {
		super.init();
		int shownRows = Math.max(1, Math.min(rows.size(), 12));
		boolean restart = rows.stream().anyMatch(row -> row.restart() != ConfigEntry.Restart.NONE);
		int wanted = rows.isEmpty() ? 74 : 67 + (restart ? 11 : 0) + shownRows * ROW;
		placeCard(Math.max(260, Math.min(360, width - 40)), wanted);
		int buttonY = cardY + cardHeight - 24;
		if (state == State.READY && !rows.isEmpty()) {
			int listY = cardY + 33;
			int listHeight = buttonY - 6 - listY - (restart ? 11 : 0);
			addRenderableWidget(new DiffList(minecraft, cardX + 3, listY, cardWidth - 6, Math.max(ROW, listHeight), rows));
			int half = (cardWidth - 26) / 2;
			confirmButton = addRenderableWidget(new PaperAction(cardX + 10, buttonY, half, PaperUi.CONTROL_HEIGHT, confirmText, button -> runConfirm()));
			addRenderableWidget(new PaperAction(cardX + 16 + half, buttonY, half, PaperUi.CONTROL_HEIGHT, CommonComponents.GUI_CANCEL, button -> onClose()));
		}
		else {
			int width = 100;
			addRenderableWidget(new PaperAction(cardX + (cardWidth - width) / 2, buttonY, width, PaperUi.CONTROL_HEIGHT,
					state == State.READY ? CommonComponents.GUI_DONE : CommonComponents.GUI_CANCEL, button -> onClose()));
		}
	}

	private void runConfirm() {
		state = State.WORKING;
		if (confirmButton != null) {
			confirmButton.active = false;
		}
		confirm.accept(result -> {
			if (result.status() == Status.OK) {
				parent.setStatus(doneMessage, false);
				minecraft.setScreen(parent);
			}
			else {
				fail(result.status());
			}
		});
	}

	@Override
	public void onClose() {
		if (state != State.WORKING) {
			super.onClose();
		}
	}

	@Override
	protected void renderCard(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		heading(graphics, cardY + 8);
		switch (state) {
			case ASKING -> centered(graphics, Component.translatable(TEXT + "loading").getString(), cardY + 33, PaperUi.INK_SOFT);
			case FAILED -> centered(graphics, failure != null ? failure.getString() : "", cardY + 33, PaperUi.ERROR);
			case WORKING -> centered(graphics, Component.translatable(TEXT + "status.waiting").getString(), cardY + 20, PaperUi.PENDING);
			case READY -> centered(graphics, Component.translatable(TEXT + (rows.isEmpty() ? "preview.none" : "preview.changes")).getString(),
					rows.isEmpty() ? cardY + 33 : cardY + 20, PaperUi.INK_SOFT);
		}
		if (rows.stream().anyMatch(row -> row.restart() != ConfigEntry.Restart.NONE)) {
			centered(graphics, Component.translatable(TEXT + "preview.restart").getString(), cardY + cardHeight - 37, PaperUi.PENDING);
		}
	}

	private static final class DiffList extends PaperList<DiffRow> {
		DiffList(Minecraft minecraft, int x, int y, int width, int height, List<Row> rows) {
			super(minecraft, x, y, width, height, ROW);
			var font = minecraft.font;
			int cap = getRowWidth() * 30 / 100;
			int before = Math.min(cap, rows.stream().mapToInt(row -> font.width(row.before())).max().orElse(0));
			int after = Math.min(cap, rows.stream().mapToInt(row -> font.width(row.after())).max().orElse(0));
			rows.forEach(row -> addEntry(new DiffRow(row, before, after)));
		}
	}

	private static final class DiffRow extends ContainerObjectSelectionList.Entry<DiffRow> {
		private final Row row;
		private final int beforeWidth;
		private final int afterWidth;

		DiffRow(Row row, int beforeWidth, int afterWidth) {
			this.row = row;
			this.beforeWidth = beforeWidth;
			this.afterWidth = afterWidth;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovering, float partialTick) {
			var font = Minecraft.getInstance().font;
			int right = left + width - 8;
			int afterX = right - afterWidth;
			int arrowX = afterX - 12;
			int beforeRight = arrowX - 3;
			int y = top + 2;
			String label = PaperUi.oneLine(font, row.label().getString(), beforeRight - beforeWidth - 8 - left);
			PaperUi.text(graphics, font, Component.literal(label), left + 1, y, PaperUi.INK);
			String before = PaperUi.oneLine(font, row.before().getString(), beforeWidth);
			PaperUi.text(graphics, font, Component.literal(before), beforeRight - font.width(before), y, PaperUi.INK_FAINT);
			PaperUi.glyph(graphics, Glyph.ARROW, arrowX, y - 1, PaperUi.INK_SOFT);
			String after = PaperUi.oneLine(font, row.after().getString(), afterWidth);
			PaperUi.text(graphics, font, Component.literal(after), afterX, y, PaperUi.ACCENT);
			if (index > 0) {
				graphics.fill(left, top - 2, right, top - 1, 0xFFE0E3E9);
			}
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
}
