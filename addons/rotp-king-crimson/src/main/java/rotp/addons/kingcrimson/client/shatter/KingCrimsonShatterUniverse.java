package rotp.addons.kingcrimson.client.shatter;

import java.io.IOException;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;

/**
 * The universe the owner accepted in the ITRP pack, in a program the mod owns: for packs that do not declare the void, painted over
 * their final image where the shell was drawn, and with no shaders, as the sky. It is one fullscreen pass and only runs in an erase.
 */
@OnlyIn(Dist.CLIENT)
public final class KingCrimsonShatterUniverse {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ShaderInstance program;
    private static boolean failureLogged;
    private static final Matrix4f INVERSE = new Matrix4f();

    private KingCrimsonShatterUniverse() {}

    public static void onRegisterShaders(RegisterShadersEvent event) {
        program = null;
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), KingCrimsonAddon.id("time_erase_universe"), DefaultVertexFormat.POSITION),
                    loaded -> program = loaded);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Unable to load King Crimson's universe; the shell's own stars stay.", failure);
        }
    }

    /** Behind everything, with no mask: false when the program did not load, and the old sky stays. */
    public static boolean drawSky(Matrix4fc view, Matrix4fc projection) {
        return draw(view, projection, 0, 0, 0, 1.0F);
    }

    /**
     * After the pack's final pass: only where {@code mask}, the depth before the shell was moved, still holds the marker, and with
     * {@code opacity} below 1 as a layer that much of the way over the pack's image.
     */
    public static boolean paintOverFinal(Matrix4fc view, Matrix4fc projection, int mask, int maskWidth, int maskHeight, float opacity) {
        return mask != 0 && opacity > 0.0F && draw(view, projection, mask, maskWidth, maskHeight, Math.min(opacity, 1.0F));
    }

    private static boolean draw(Matrix4fc view, Matrix4fc projection, int mask, int maskWidth, int maskHeight, float opacity) {
        ShaderInstance shader = program;
        if (shader == null) return false;
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int previousTexture = RenderSystem.getShaderTexture(0);
        try {
            shader.safeGetUniform("InvViewProj").set(INVERSE.set(projection).mul(view).invert());
            shader.safeGetUniform("PixelAngle").set(2.0F / (projection.m11() * viewportHeight()));
            shader.safeGetUniform("MaskSize").set((float) maskWidth, (float) maskHeight);
            shader.safeGetUniform("UseMask").set(mask != 0 ? 1 : 0);
            // A draw fills every sampler from these slots, so setSampler would be overwritten.
            RenderSystem.setShaderTexture(0, mask);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            if (opacity < 1.0F) {
                // The program is generated from the pack's sources and has no opacity of its own, so the blend carries it. The
                // mask's one-pixel soft edge then takes the layer's whole share, for the half second the fade lasts.
                GL14.glBlendColor(0.0F, 0.0F, 0.0F, opacity);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.CONSTANT_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            RenderSystem.setShader(() -> shader);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            builder.addVertex(-1.0F, -1.0F, 0.0F);
            builder.addVertex(3.0F, -1.0F, 0.0F);
            builder.addVertex(-1.0F, 3.0F, 0.0F);
            BufferUploader.drawWithShader(builder.buildOrThrow());
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.warn("King Crimson's universe could not draw; the shell's own stars stay.", failure);
            }
            return false;
        } finally {
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.depthMask(true);
            if (opacity < 1.0F) GL14.glBlendColor(0.0F, 0.0F, 0.0F, 0.0F);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            if (depthTest) RenderSystem.enableDepthTest();
            if (cull) RenderSystem.enableCull();
        }
    }

    private static int viewportHeight() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            return Math.max(1, viewport.get(3));
        }
    }
}
