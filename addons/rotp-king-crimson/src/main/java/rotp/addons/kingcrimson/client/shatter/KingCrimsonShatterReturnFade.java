package rotp.addons.kingcrimson.client.shatter;

import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;

/**
 * The universe fading off the returned world on a pack that does not declare the void. During the erase that pack shades the
 * shell dark and its exposure climbs; when the world came back in one frame it showed overexposed. Here the world comes back at
 * once under the pack, so its exposure starts to follow, while the last frame's void, frozen where it was on the screen, fades out
 * over it.
 */
@OnlyIn(Dist.CLIENT)
final class KingCrimsonShatterReturnFade {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int mask;
    private static int maskWidth;
    private static int maskHeight;
    private static long startedAt;

    private KingCrimsonShatterReturnFade() {}

    /** Takes over the last erase frame's mask; it is deleted when the fade ends or something else begins. */
    static void begin(int lastMask, int width, int height) {
        RenderSystem.assertOnRenderThread();
        close();
        if (lastMask == 0) return;
        mask = lastMask;
        maskWidth = width;
        maskHeight = height;
        startedAt = System.nanoTime();
        LOGGER.info("King Crimson return fade: the universe fades off the returned world in {} s", KingCrimsonShatterPlan.RETURN_FADE_SECONDS);
    }

    /** After the pack's final image, while the fade lasts. */
    static void paint(Matrix4fc view, Matrix4fc projection) {
        if (mask == 0) return;
        float opacity = KingCrimsonShatterPlan.returnOpacity((System.nanoTime() - startedAt) * 1.0E-9F);
        if (opacity <= 0.0F || !KingCrimsonShatterUniverse.paintOverFinal(view, projection, mask, maskWidth, maskHeight, opacity)) {
            close();
        }
    }

    static void close() {
        RenderSystem.assertOnRenderThread();
        if (mask != 0) GL11.glDeleteTextures(mask);
        mask = 0;
    }
}
