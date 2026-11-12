package rotp.core.mechanics.jojopose.client;

import javax.annotation.Nullable;

import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimatedEntity;
import rotp.core.core.JojoMod;
import rotp.core.mechanics.jojopose.ClJojoPosePacket;
import rotp.core.mechanics.jojopose.JojoPoseSets.PoseRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** One pose of the pose list: the player's own model in the pose; a click strikes it (official JojoPoseWidget). */
public class JojoPoseWidget extends AbstractWidget {
	public static final int SIZE = 64;
	private static final ResourceLocation SLOT = JojoMod.resLoc("widget/pose_slot");
	private static final ResourceLocation SELECTION = JojoMod.resLoc("widget/pose_slot_selection");

	private final PoseRef pose;
	private final Runnable afterPress;

	public JojoPoseWidget(int x, int y, PoseRef pose, Runnable afterPress) {
		super(x, y, SIZE, SIZE, Component.literal(pose.pose()));
		this.pose = pose;
		this.afterPress = afterPress;
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.blitSprite(SLOT, getX(), getY(), getWidth(), getHeight());
		renderPosedPlayer(guiGraphics, getX(), getY(), getWidth(), getHeight(), ClientJojoPoseLoader.heldPose(pose));
		if (isHoveredOrFocused()) {
			guiGraphics.blitSprite(SELECTION, getX(), getY(), getWidth(), getHeight());
		}
	}

	/** Draws the local player as the inventory does, with the pose put on its model for this one draw. */
	public static void renderPosedPlayer(GuiGraphics guiGraphics, int x, int y, int width, int height, @Nullable AnimFramePose pose) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || pose == null) {
			return;
		}
		AnimatedEntity animated = (AnimatedEntity) player;
		AnimFramePose worldPose = animated.jojo_ripples$getModelPose(AnimatedEntity.PoseType.FINAL);
		AnimFramePose kept = worldPose != null ? worldPose.deepCopy() : null;
		animated.jojo_ripples$setModelPose(AnimatedEntity.PoseType.FINAL, pose);
		try {
			float centerX = x + width / 2.0F;
			float centerY = y + height / 2.0F;
			InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, x + 2, y + 2, x + width - 2, y + height - 2,
					height * 2 / 5, 0.0625F, centerX, centerY, player);
		}
		finally {
			animated.jojo_ripples$setModelPose(AnimatedEntity.PoseType.FINAL, kept);
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		PacketDistributor.sendToServer(ClJojoPosePacket.start(pose.set(), pose.pose()));
		afterPress.run();
	}

	@Override
	public void playDownSound(SoundManager handler) {}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		defaultButtonNarrationText(narrationElementOutput);
	}
}
