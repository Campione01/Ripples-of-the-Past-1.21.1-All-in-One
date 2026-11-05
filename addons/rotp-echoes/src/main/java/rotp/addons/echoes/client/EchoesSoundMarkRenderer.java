package rotp.addons.echoes.client;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.block.EchoesSoundMarkBlock;
import rotp.addons.echoes.block.EchoesSoundMarkBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** R772 F11: ACT1's sound on a block face (the master's EchoesSoundMarkRenderer), seen only by Stand users. */
public final class EchoesSoundMarkRenderer implements BlockEntityRenderer<EchoesSoundMarkBlockEntity> {
    private static final ResourceLocation[] TEXTURES = {
            RotpEchoes.id("textures/block/sound.png"),
            RotpEchoes.id("textures/block/sound_mark_1.png"),
            RotpEchoes.id("textures/block/sound_mark_2.png"),
            RotpEchoes.id("textures/block/sound_mark_3.png")
    };

    public EchoesSoundMarkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(EchoesSoundMarkBlockEntity mark, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (mark.sound() == null || !EchoesOnomatopoeiaMarkRenderer.viewerHasStand()) {
            return;
        }
        BlockState state = mark.getBlockState();
        EchoesOnomatopoeiaMarkRenderer.drawOnFace(poseStack, buffer, state.getValue(EchoesSoundMarkBlock.FACING),
                TEXTURES[state.getValue(EchoesSoundMarkBlock.VARIANT) % TEXTURES.length], packedLight);
    }
}
