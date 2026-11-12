package rotp.core.mechanics.jojopose.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import rotp.core.core.JojoMod;
import rotp.core.mechanics.jojopose.ClJojoPosePacket;
import rotp.core.mechanics.jojopose.JojoPoseAction;
import rotp.core.mechanics.jojopose.JojoPoseSets.PoseRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The pose list beside the chat (official ClientJojoPoseChatUI): a column at the right edge of the chat screen with the
 * poses the worn character may strike, scrolled with the mouse wheel; and the pose key (JojoPoseKey), which opens the
 * same list on its own screen, or ends the pose being held.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class JojoPoseChatUI {
	private static final int GAP = 2;
	// the chat's input box along the bottom
	private static final int CHAT_INPUT_HEIGHT = 16;
	private static final Map<Screen, Column> COLUMNS = new WeakHashMap<>();

	private JojoPoseChatUI() {}

	@SubscribeEvent
	public static void addPoseList(ScreenEvent.Init.Post event) {
		Screen screen = event.getScreen();
		Minecraft mc = Minecraft.getInstance();
		COLUMNS.remove(screen);
		if (screen.getClass() != ChatScreen.class || mc.player == null) {
			return;
		}
		List<PoseRef> poses = ClientJojoPoseLoader.posesFor(mc.player);
		if (poses.isEmpty()) {
			return;
		}
		ChatScreen chat = (ChatScreen) screen;
		Column column = new Column(screen, poses, () -> {
			sendTypedLine(chat);
			if (mc.screen == chat) {
				mc.setScreen(null);
			}
		});
		column.widgets.forEach(event::addListener);
		COLUMNS.put(screen, column);
	}

	/** As the official ChatScreenMixin: striking a pose sends what was typed, as Enter would, so the line is not lost. */
	private static void sendTypedLine(ChatScreen chat) {
		for (var child : chat.children()) {
			if (child instanceof EditBox input) {
				chat.handleChatInput(input.getValue(), true);
				return;
			}
		}
	}

	@SubscribeEvent
	public static void scrollPoseList(ScreenEvent.MouseScrolled.Pre event) {
		Column column = COLUMNS.get(event.getScreen());
		if (column != null && column.isMouseOver(event.getMouseX(), event.getMouseY())) {
			column.scroll(event.getScrollDeltaY());
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onPoseKey(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		while (JojoPoseKey.KEY.consumeClick()) {
			if (mc.player == null) {
				continue;
			}
			if (JojoPoseAction.getPose(mc.player) != null) {
				PacketDistributor.sendToServer(ClJojoPosePacket.stop());
			}
			else if (mc.screen == null) {
				mc.setScreen(new JojoPoseScreen());
			}
		}
	}

	/** The poses stacked up from the chat's input box; as many as fit show, the wheel moves through the rest. */
	private static final class Column {
		final Screen screen;
		final List<JojoPoseWidget> widgets = new ArrayList<>();
		final int shown;
		int first;

		Column(Screen screen, List<PoseRef> poses, Runnable afterPress) {
			this.screen = screen;
			for (PoseRef pose : poses) {
				widgets.add(new JojoPoseWidget(0, 0, pose, afterPress));
			}
			int room = (screen.height - CHAT_INPUT_HEIGHT) / (JojoPoseWidget.SIZE + GAP);
			this.shown = Mth.clamp(room, 1, widgets.size());
			layout();
		}

		int x() {
			return screen.width - JojoPoseWidget.SIZE - GAP;
		}

		int top() {
			return screen.height - CHAT_INPUT_HEIGHT - shown * (JojoPoseWidget.SIZE + GAP);
		}

		boolean isMouseOver(double mouseX, double mouseY) {
			return mouseX >= x() && mouseX < x() + JojoPoseWidget.SIZE
					&& mouseY >= top() && mouseY < screen.height - CHAT_INPUT_HEIGHT;
		}

		void scroll(double delta) {
			int moved = Mth.clamp(first - (int) Math.signum(delta), 0, widgets.size() - shown);
			if (moved != first) {
				first = moved;
				layout();
			}
		}

		void layout() {
			for (int i = 0; i < widgets.size(); i++) {
				JojoPoseWidget widget = widgets.get(i);
				int row = i - first;
				widget.visible = row >= 0 && row < shown;
				widget.active = widget.visible;
				widget.setPosition(x(), top() + Math.max(row, 0) * (JojoPoseWidget.SIZE + GAP));
			}
		}
	}
}
