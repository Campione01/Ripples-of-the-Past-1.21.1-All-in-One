package rotp.addons.novemberrain.client;

import com.mojang.blaze3d.systems.RenderSystem;

import rotp.addons.novemberrain.NovemberRainAddon;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The remake's CutEyelidOverlay: while its player has Cut Eyelid, textures/effect/cut_eyelid.png covers the right half
 * of the screen, drawn after the helmet overlay (1.16 RenderGameOverlayEvent.Post HELMET) and hidden with the GUI.
 */
public final class CutEyelidOverlay implements LayeredDraw.Layer {
    public static final ResourceLocation OVERLAY = NovemberRainAddon.id("textures/effect/cut_eyelid.png");
    // 1.16 AbstractGui.blit(..., uWidth, vHeight, textureHeight 800, textureWidth 256)
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 800;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !player.hasEffect(NovemberRainAddon.CUT_EYELID)) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        graphics.blit(OVERLAY, w / 2, 0, 0.0F, 0.0F, w / 2, h, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
