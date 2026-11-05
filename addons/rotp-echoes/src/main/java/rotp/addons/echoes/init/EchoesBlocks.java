package rotp.addons.echoes.init;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.block.EchoesOnomatopoeiaMarkBlock;
import rotp.addons.echoes.block.EchoesOnomatopoeiaMarkBlockEntity;
import rotp.addons.echoes.block.EchoesSoundMarkBlock;
import rotp.addons.echoes.block.EchoesSoundMarkBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** R772 F11: the master's InitBlocks, ACT2's word and ACT1's sound left on a block. */
public final class EchoesBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RotpEchoes.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RotpEchoes.MOD_ID);

    // master OnomatopoeiaMarkBlock: Material.BARRIER, unbreakable, no shape, no collision, no drops
    public static final DeferredBlock<EchoesOnomatopoeiaMarkBlock> ONOMATOPOEIA_MARK = BLOCKS.register(
            "onomatopoeia_mark",
            () -> new EchoesOnomatopoeiaMarkBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3600000.8F)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .noTerrainParticles()
                    .isValidSpawn(Blocks::never)
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredHolder<
            BlockEntityType<?>, BlockEntityType<EchoesOnomatopoeiaMarkBlockEntity>> ONOMATOPOEIA_MARK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "onomatopoeia_mark",
                    () -> BlockEntityType.Builder.of(
                            EchoesOnomatopoeiaMarkBlockEntity::new, ONOMATOPOEIA_MARK.get()).build(null));

    // master EchoesSoundMarkBlock: as the word's mark, but its cell can be aimed at (to take it off)
    public static final DeferredBlock<EchoesSoundMarkBlock> SOUND_MARK = BLOCKS.register(
            "sound_mark",
            () -> new EchoesSoundMarkBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3600000.8F)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .noTerrainParticles()
                    .isValidSpawn(Blocks::never)
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredHolder<
            BlockEntityType<?>, BlockEntityType<EchoesSoundMarkBlockEntity>> SOUND_MARK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "sound_mark",
                    () -> BlockEntityType.Builder.of(
                            EchoesSoundMarkBlockEntity::new, SOUND_MARK.get()).build(null));

    private EchoesBlocks() {}
}
