package rotp.core.mechanics.jojopose.client;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.mechanics.jojopose.JojoPoseSets.PoseRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The pose list on its own screen, opened by the pose key: the same poses as beside the chat, in rows. */
public class JojoPoseScreen extends Screen {
	private static final int GAP = 2;
	private static final int TOP = 24;
	private final List<JojoPoseWidget> widgets = new ArrayList<>();
	private int columns;
	private int rowsShown;
	private int firstRow;

	public JojoPoseScreen() {
		super(Component.translatable(JojoMod.MOD_ID + ".screen.jojo_poses"));
	}

	@Override
	protected void init() {
		widgets.clear();
		if (minecraft.player != null) {
			for (PoseRef pose : ClientJojoPoseLoader.posesFor(minecraft.player)) {
				widgets.add(addRenderableWidget(new JojoPoseWidget(0, 0, pose, this::onClose)));
			}
		}
		int cell = JojoPoseWidget.SIZE + GAP;
		columns = Mth.clamp((width - 2 * GAP) / cell, 1, Math.max(widgets.size(), 1));
		int rows = (widgets.size() + columns - 1) / columns;
		rowsShown = Mth.clamp((height - TOP - GAP) / cell, 1, Math.max(rows, 1));
		firstRow = Mth.clamp(firstRow, 0, Math.max(rows - rowsShown, 0));
		layout();
	}

	private void layout() {
		int cell = JojoPoseWidget.SIZE + GAP;
		int left = (width - columns * cell + GAP) / 2;
		for (int i = 0; i < widgets.size(); i++) {
			JojoPoseWidget widget = widgets.get(i);
			int row = i / columns - firstRow;
			widget.visible = row >= 0 && row < rowsShown;
			widget.active = widget.visible;
			widget.setPosition(left + (i % columns) * cell, TOP + Math.max(row, 0) * cell);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int rows = (widgets.size() + columns - 1) / columns;
		int moved = Mth.clamp(firstRow - (int) Math.signum(scrollY), 0, Math.max(rows - rowsShown, 0));
		if (moved != firstRow) {
			firstRow = moved;
			layout();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		if (widgets.isEmpty()) {
			guiGraphics.drawCenteredString(font, Component.translatable(JojoMod.MOD_ID + ".screen.jojo_poses.none"),
					width / 2, height / 2, 0xA0A0A0);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
