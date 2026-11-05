package rotp.addons.stickyfingers.client;

import java.util.List;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.blockentity.StickyFingersZipperBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;

/**
 * The zipper block takes the place of the zipped block, which 1.16 left
 * standing: the chunk draws that block's own model (with its tint, light
 * and occlusion) and the block-entity renderer adds the zipper on top.
 */
public final class StickyFingersZipperBlockModel
        extends BakedModelWrapper<BakedModel> {
    public StickyFingersZipperBlockModel(BakedModel emptyModel) {
        super(emptyModel);
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos position,
            BlockState state,
            ModelData modelData) {
        if (modelData.get(StickyFingersZipperBlockEntity.SHOWN_BLOCK)
                != null) {
            return modelData;
        }
        BlockState shown = shownBlock(level, position);
        return shown == null
                ? modelData
                : modelData.derive()
                        .with(StickyFingersZipperBlockEntity.SHOWN_BLOCK,
                                shown)
                        .build();
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData data,
            @Nullable RenderType renderType) {
        BlockState shown =
                data.get(StickyFingersZipperBlockEntity.SHOWN_BLOCK);
        return shown == null
                ? List.of()
                : model(shown).getQuads(
                        shown, side, random, ModelData.EMPTY, renderType);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(
            BlockState state, RandomSource random, ModelData data) {
        BlockState shown =
                data.get(StickyFingersZipperBlockEntity.SHOWN_BLOCK);
        return shown == null
                ? ChunkRenderTypeSet.none()
                : model(shown).getRenderTypes(
                        shown, random, ModelData.EMPTY);
    }

    @Override
    public TriState useAmbientOcclusion(
            BlockState state, ModelData data, RenderType renderType) {
        BlockState shown =
                data.get(StickyFingersZipperBlockEntity.SHOWN_BLOCK);
        return shown == null
                ? TriState.DEFAULT
                : model(shown).useAmbientOcclusion(
                        shown, ModelData.EMPTY, renderType);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState shown =
                data.get(StickyFingersZipperBlockEntity.SHOWN_BLOCK);
        return shown == null
                ? super.getParticleIcon(data)
                : model(shown).getParticleIcon(ModelData.EMPTY);
    }

    // Tint of the zipped block (grass, leaves), read where the chunk
    // asks for the zipper's own colour.
    public static int tint(
            BlockState state,
            @Nullable BlockAndTintGetter level,
            @Nullable BlockPos position,
            int tintIndex) {
        if (level == null || position == null) {
            return -1;
        }
        BlockState shown = shownBlock(level, position);
        return shown == null
                ? -1
                : Minecraft.getInstance().getBlockColors().getColor(
                        shown, level, position, tintIndex);
    }

    @Nullable
    private static BlockState shownBlock(
            BlockAndTintGetter level, BlockPos position) {
        return level.getBlockEntity(position)
                        instanceof StickyFingersZipperBlockEntity zipper
                ? zipper.getShownBlock()
                : null;
    }

    private static BakedModel model(BlockState shown) {
        return Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModel(shown);
    }
}
