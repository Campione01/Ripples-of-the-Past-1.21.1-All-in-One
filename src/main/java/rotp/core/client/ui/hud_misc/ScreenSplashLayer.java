package rotp.core.client.ui.hud_misc;

import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;

import rotp.core.client.ClientTimeStopHandler;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.config.client.ClientModSettings;
import rotp.core.core.JojoMod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Draws the splashes as the lowest GUI layer above the camera overlays: after the whole level pass, so a shader pack and
 * the time stop shader never touch it, the same in first and third person, and under the crosshair and all of the HUD.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class ScreenSplashLayer {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/hud/screen_splash.png");
	private static final ScreenSplash.Stack SPLASHES = new ScreenSplash.Stack();
	private static final Random SEEDS = new Random();

	private ScreenSplashLayer() {}

	@SubscribeEvent
	public static void addLayer(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, JojoMod.resLoc("screen_splash"), ScreenSplashLayer::render);
	}

	public static void add(ScreenSplash.Kind kind, float strength, int tint) {
		Minecraft mc = Minecraft.getInstance();
		// the view is someone else's eyes (a Stand under manual control, a soul): the user's face is not what is seen
		if (mc.player == null || mc.getCameraEntity() != mc.player) {
			return;
		}
		SPLASHES.add(ClientModSettings.getSettingsReadOnly().screenSplash, () -> new ScreenSplash(kind, strength, tint, SEEDS.nextLong()));
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (SPLASHES.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
			SPLASHES.clear();
			return;
		}
		SPLASHES.tick(ClientModSettings.getSettingsReadOnly().screenSplash, isFrozen(mc));
	}

	private static boolean isFrozen(Minecraft mc) {
		return mc.isPaused() || ClientTimeStopHandler.isEntityVisuallyFrozen(mc.player);
	}

	private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (SPLASHES.isEmpty() || mc.options.hideGui || mc.player == null) {
			return;
		}
		float partialTick = isFrozen(mc) ? 0 : ClientUtil.partialTick(deltaTracker, true);
		List<ScreenSplash.Quad> quads = SPLASHES.frame(partialTick, guiGraphics.guiWidth(), guiGraphics.guiHeight());
		if (quads.isEmpty()) {
			return;
		}

		guiGraphics.flush();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, TEXTURE);
		Matrix4f matrix = guiGraphics.pose().last().pose();
		float texture = ScreenSplash.TEXTURE_SIZE;
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for (ScreenSplash.Quad quad : quads) {
			float u0 = quad.u0() / texture;
			float u1 = quad.u1() / texture;
			float v0 = quad.v0() / texture;
			float v1 = quad.v1() / texture;
			buffer.addVertex(matrix, quad.x0(), quad.y0(), 0).setUv(u0, v0).setColor(quad.color());
			buffer.addVertex(matrix, quad.x0(), quad.y1(), 0).setUv(u0, v1).setColor(quad.color());
			buffer.addVertex(matrix, quad.x1(), quad.y1(), 0).setUv(u1, v1).setColor(quad.color());
			buffer.addVertex(matrix, quad.x1(), quad.y0(), 0).setUv(u1, v0).setColor(quad.color());
		}
		BufferUploader.drawWithShader(buffer.buildOrThrow());
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
	}
}
