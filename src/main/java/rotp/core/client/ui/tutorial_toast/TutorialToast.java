package rotp.core.client.ui.tutorial_toast;

import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A tip in vanilla's tutorial toast, with the official version's bar showing how long it has left. */
public class TutorialToast implements Toast {
	private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/tutorial");
	private static final int TEXT_X = 8;
	private static final int BAR_X = 3;

	private final Component line1;
	private final @Nullable Component line2;
	private final BooleanSupplier stillShown;
	private final TutorialTips.Schedule schedule;

	public TutorialToast(Component line1, @Nullable Component line2, BooleanSupplier stillShown, TutorialTips.Schedule schedule) {
		this.line1 = line1;
		this.line2 = line2;
		this.stillShown = stillShown;
		this.schedule = schedule;
	}

	@Override
	public Toast.Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
		Minecraft mc = toastComponent.getMinecraft();
		guiGraphics.blitSprite(BACKGROUND_SPRITE, 0, 0, width(), height());
		if (line2 == null) {
			guiGraphics.drawString(mc.font, line1, TEXT_X, 12, 0xFF000000, false);
		}
		else {
			guiGraphics.drawString(mc.font, line1, TEXT_X, 7, 0xFF000000, false);
			guiGraphics.drawString(mc.font, line2, TEXT_X, 18, 0xFF000000, false);
		}
		boolean shown = stillShown.getAsBoolean();
		if (shown) {
			int barWidth = width() - 2 * BAR_X;
			int barY = height() - 4;
			guiGraphics.fill(BAR_X, barY, BAR_X + barWidth, barY + 1, -1);
			guiGraphics.fill(BAR_X, barY, BAR_X + Mth.ceil(barWidth * (1 - schedule.progress())), barY + 1, 0xFF000000);
		}
		return shown ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
	}
}
