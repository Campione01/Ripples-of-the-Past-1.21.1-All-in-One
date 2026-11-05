package rotp.addons.stickyfingers.client;

import java.io.IOException;

import org.slf4j.Logger;

import rotp.core.api.client.render.ClientRenderCompatibility;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;

import rotp.addons.stickyfingers.StickyFingersAddon;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * 1.16 StickyFingersZipperRenderType.endPortalNoFog: the end-portal
 * layers over the end sky, with the zipper's own void texture and purple
 * layer colours (shaders/core/zipper_void.fsh).
 */
public final class StickyFingersZipperVoidRenderType extends RenderType {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER =
            StickyFingersAddon.id("zipper_void");
    private static final ResourceLocation END_SKY =
            ResourceLocation.withDefaultNamespace(
                    "textures/environment/end_sky.png");
    private static final ResourceLocation VOID_TEXTURE =
            StickyFingersAddon.id(
                    "textures/zipper/effect/zipper_void.png");

    private static ShaderInstance voidShader;

    private static final RenderType ZIPPER_VOID = create(
            "rotp_stfn:zipper_void",
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(
                            new ShaderStateShard(() -> voidShader))
                    .setTextureState(
                            RenderStateShard.MultiTextureStateShard
                                    .builder()
                                    .add(END_SKY, false, false)
                                    .add(VOID_TEXTURE, false, false)
                                    .build())
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private StickyFingersZipperVoidRenderType(
            String name,
            VertexFormat format,
            VertexFormat.Mode mode,
            int bufferSize,
            boolean affectsCrumbling,
            boolean sortOnUpload,
            Runnable setupState,
            Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling,
                sortOnUpload, setupState, clearState);
    }

    static void registerShader(RegisterShadersEvent event) {
        voidShader = null;
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            SHADER,
                            DefaultVertexFormat.POSITION),
                    loaded -> voidShader = loaded);
        }
        catch (IOException | RuntimeException exception) {
            LOGGER.error(
                    "Unable to load the Sticky Fingers zipper void "
                            + "shader; open zippers use the end portal.",
                    exception);
        }
    }

    // Shader packs replace core shaders; the vanilla end portal is the
    // one they are known to draw.
    static RenderType zipperVoid() {
        return voidShader == null
                        || ClientRenderCompatibility.snapshot()
                                .irisShaderPackInUse()
                ? RenderType.endPortal()
                : ZIPPER_VOID;
    }
}
