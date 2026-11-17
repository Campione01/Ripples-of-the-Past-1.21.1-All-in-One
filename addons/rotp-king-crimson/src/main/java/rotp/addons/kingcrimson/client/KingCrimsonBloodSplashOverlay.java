package rotp.addons.kingcrimson.client;

import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import rotp.core.client.ClientTimeStopHandler;
import rotp.core.client.ui.hud_misc.ScreenSplash;
import rotp.core.core.JojoMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Blood Throw on the screen of the player it landed on: the core's blood splash at full strength, which holds for
 * 1.8 s and is wiped away over the next 0.6 s (the owner's "遮蔽屏幕持續兩秒"). The core shows its own splashes only to a
 * player who turned them on; this one hit is the move's whole effect on a player, so it has its own stack and shows
 * whatever that setting says. Drawn where the core draws its splashes: above the camera overlays, under the crosshair
 * and the HUD, after the level pass, so no shader pack touches it.
 */
public final class KingCrimsonBloodSplashOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/hud/screen_splash.png");
    private static final ScreenSplash.Stack SPLASHES = new ScreenSplash.Stack();
    private static final Random SEEDS = new Random();

    public static void splash() {
        SPLASHES.add(true, () -> new ScreenSplash(ScreenSplash.Kind.BLOOD, 1.0F, ScreenSplash.BLOOD_TINT,
                SEEDS.nextLong()));
    }

    public static void tick() {
        if (SPLASHES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()) {
            SPLASHES.clear();
            return;
        }
        SPLASHES.tick(true, isFrozen(minecraft));
    }

    public static void clear() {
        SPLASHES.clear();
    }

    // As the core's: a splash neither ages nor lands while its viewer's own time is stopped or the game is paused.
    private static boolean isFrozen(Minecraft minecraft) {
        return minecraft.isPaused() || ClientTimeStopHandler.isEntityVisuallyFrozen(minecraft.player);
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (SPLASHES.isEmpty() || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        float partialTick = isFrozen(minecraft) ? 0.0F : deltaTracker.getGameTimeDeltaPartialTick(true);
        List<ScreenSplash.Quad> quads = SPLASHES.frame(partialTick, graphics.guiWidth(), graphics.guiHeight());
        if (quads.isEmpty()) {
            return;
        }
        graphics.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        Matrix4f matrix = graphics.pose().last().pose();
        float texture = ScreenSplash.TEXTURE_SIZE;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        for (ScreenSplash.Quad quad : quads) {
            float u0 = quad.u0() / texture;
            float u1 = quad.u1() / texture;
            float v0 = quad.v0() / texture;
            float v1 = quad.v1() / texture;
            buffer.addVertex(matrix, quad.x0(), quad.y0(), 0.0F).setUv(u0, v0).setColor(quad.color());
            buffer.addVertex(matrix, quad.x0(), quad.y1(), 0.0F).setUv(u0, v1).setColor(quad.color());
            buffer.addVertex(matrix, quad.x1(), quad.y1(), 0.0F).setUv(u1, v1).setColor(quad.color());
            buffer.addVertex(matrix, quad.x1(), quad.y0(), 0.0F).setUv(u1, v0).setColor(quad.color());
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
